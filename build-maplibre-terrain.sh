#!/bin/bash
# Builds MapLibre Native for Android from its 3D terrain pull request
# (https://github.com/maplibre/maplibre-native/pull/4190, branch feature/terrain-3d) into a local
# Maven repository, ../maplibre-terrain/maven, as org.maplibre.gl:android-sdk:13.8.0-terrain, the
# version the app asks for (app/build.gradle). Nothing publishes that build, so the app on this
# branch only builds where this script has run: run it where the Android SDK is, before building
# the app.
#
# Each run fetches the newest commit of the pull request's branch and builds it unless it is the
# one already built, so running it again brings in the pull request's latest changes. To build
# another commit or branch instead: MLN_REF=<commit or branch> ./build-maplibre-terrain.sh
# Without network, it keeps the build there is.
#
# The first run clones MapLibre Native and its submodules into
# ../maplibre-terrain/maplibre-native and builds its native library for one ABI (below): that can
# take half an hour or more, and a few GB. Later builds redo only what the new commits changed.
# It needs the NDK and CMake versions MapLibre asks for (so far NDK 28.2.13676358 and CMake 3.24 or
# later); Gradle installs them into the SDK if its licences are accepted, else install them with
# the SDK manager.
#
# The renderer is Vulkan, as in the released android-sdk the app used so far (13.7.1). The pull
# request is mostly tested with OpenGL ES: to try that, set RENDERER=opengl here and ask for
# org.maplibre.gl:android-sdk-opengl in app/build.gradle (dependency and repository).
set -eu
cd "$(dirname "$0")"

MLN_REF=${MLN_REF:-feature/terrain-3d}
MLN_VERSION=13.8.0-terrain  # the version app/build.gradle asks for
RENDERER=vulkan             # or opengl, see above
ABI=arm64-v8a

TERRAIN_DIR=$(cd .. && pwd)/maplibre-terrain
MLN=$TERRAIN_DIR/maplibre-native
MAVEN=$TERRAIN_DIR/maven
if [ ! -d "$MLN/.git" ]; then
	git init -q "$MLN"
	git -C "$MLN" remote add origin https://github.com/maplibre/maplibre-native.git
fi
# Only the commit itself, not its history: MapLibre's is large.
if ! git -C "$MLN" fetch --depth 1 origin "$MLN_REF"; then
	if [ -f "$MAVEN/built-from" ]; then
		echo "Could not fetch $MLN_REF: keeping the MapLibre Native build there is" \
			"($(cat "$MAVEN/built-from"))." >&2
		exit 0
	fi
	exit 1
fi
COMMIT=$(git -C "$MLN" rev-parse FETCH_HEAD)
SUMMARY=$(git -C "$MLN" log -1 --date=short --format='%h of %cd, "%s"' FETCH_HEAD)
BUILT="$COMMIT $RENDERER $ABI"
if [ "$(cat "$MAVEN/built-from" 2>/dev/null)" = "$BUILT" ]; then
	echo "MapLibre Native $SUMMARY ($RENDERER, $ABI) is already built in $MAVEN."
	exit 0
fi

echo "Building MapLibre Native $SUMMARY ($RENDERER, $ABI) in $MLN: this takes a while."
git -C "$MLN" checkout -q --force "$COMMIT"
git -C "$MLN" submodule update --init --recursive --depth 1 --jobs 8
# The SDK this app builds with, for MapLibre's build and the test builds it includes
# (render-test/android, test/android), each of which would look for its own local.properties.
SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties)
if [ ! -d "$SDK_DIR" ]; then
	echo "No Android SDK at sdk.dir of local.properties: '$SDK_DIR'" >&2
	exit 1
fi
export ANDROID_HOME=$SDK_DIR
rm -f "$MLN/platform/android/local.properties"
# The version the app asks for.
echo "$MLN_VERSION" > "$MLN/platform/android/VERSION"
# Vulkan is published as android-sdk, OpenGL ES as android-sdk-opengl.
if [ "$RENDERER" = vulkan ]; then PUBLICATION=Default; else PUBLICATION=Opengl; fi
(cd "$MLN/platform/android" && ./gradlew \
	":MapLibreAndroid:publish${PUBLICATION}ReleasePublicationToConfiguredRepository" \
	-Pmaplibre.abis="$ABI" -PpublicationRepositoryUrl="file://$MAVEN")
echo "$BUILT" > "$MAVEN/built-from"
echo "Built MapLibre Native $SUMMARY into $MAVEN as $MLN_VERSION: the app builds against it now."
