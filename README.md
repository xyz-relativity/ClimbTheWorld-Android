# ClimbTheWorld-Android

Rock climbing frontend for OpenStreetMap.

* Install it with [Obtainium or ObtainX](#with-obtainium-or-obtainx) to get updates automatically,
  or download the APK from the [latest release](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases/latest).
* Support us on Patreon: https://www.patreon.com/climb_the_world?fan_landing=true

## Install

ClimbTheWorld is published as an APK on its
[GitHub releases](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases), in two
builds:

* `ClimbTheWorld-Release.apk`, the default build, with what needs libraries that are not free
  software. So far, that is Google's
  [ARCore](https://play.google.com/store/apps/details?id=com.google.ar.core), on the phones that
  support it: the A.R. view tracks the camera with it, steadier than with the sensors, and can be
  aligned on mapped routes once close to them, and the node editor can place nodes in A.R. **Use ARCore** in the
  settings turns it off in the A.R. view.
* `ClimbTheWorld-foss-Release.apk`, the same without them, all free software.

Either installs over the other, keeping your downloads and changes.

### With Obtainium or ObtainX

[Obtainium](https://github.com/ImranR98/Obtainium) and its fork
[ObtainX](https://github.com/bikram-agarwal/ObtainX) install apps straight from their GitHub
releases, and let you know when a new version is out. The steps are the same in both.

1. Install Obtainium (from [GitHub](https://github.com/ImranR98/Obtainium/releases),
   [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/dev.imranr.obtainium) or
   [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/)) or ObtainX (from
   [GitHub](https://github.com/bikram-agarwal/ObtainX/releases/latest) or
   [F-Droid](https://f-droid.org/packages/dev.bikram.obtainx/)).
2. On the phone, open the link of the build you want:
   [default build](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.climbtheworld.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fxyz-relativity%2FClimbTheWorld-Android%22%2C%22author%22%3A%22xyz-relativity%22%2C%22name%22%3A%22ClimbTheWorld%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22ClimbTheWorld-Release%5C%22%7D%22%7D)
   or
   [free software build](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.climbtheworld.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fxyz-relativity%2FClimbTheWorld-Android%22%2C%22author%22%3A%22xyz-relativity%22%2C%22name%22%3A%22ClimbTheWorld%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22foss%5C%22%7D%22%7D).
   It opens Obtainium or ObtainX with ClimbTheWorld ready to import, its updates kept to that
   build. Confirm the import.

   Or add it yourself: open **Add app**, enter
   `https://github.com/xyz-relativity/ClimbTheWorld-Android` as the **App source URL**, and pick
   the build in **Filter links by regular expression** (**Filter APKs by regular expression** in
   older versions): `ClimbTheWorld-Release` for the default one, or `foss` for the free software
   one. Keep the other options as they are, and tap **Add**.
3. Tap **Install** on the app's page, and allow Obtainium or ObtainX to install apps when Android
   asks.

From then on, Obtainium or ObtainX checks for new releases in the background and notifies you of
them, to install with **Update**.

If you added ClimbTheWorld before there were two builds, set the same filter in its options on
its page, so that updates keep to the build you want: `foss` keeps the free software one you had.

### By hand

Download `ClimbTheWorld-Release.apk`, or `ClimbTheWorld-foss-Release.apk`, from the
[latest release](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases/latest), open it
on the phone, and allow the browser or file manager to install apps when Android asks.

New versions then have to be downloaded and installed the same way.

## Screenshots

### Augmented reality

Point the camera at the rock to see the crags and routes from OpenStreetMap where they are.

| <img src="pics/ar-cliff-from-approach.png" width="120" alt="A.R. view of a cliff from the approach"> | <img src="pics/ar-cliff-from-road.png" width="120" alt="A.R. view of the same cliff from the road"> | <img src="pics/ar-crag-routes.png" width="120" alt="A.R. view close to a crag"> | <img src="pics/ar-forest-terrain.png" width="120" alt="A.R. view in the forest with the virtual terrain"> | <img src="pics/ar-forest-no-horizon.png" width="120" alt="A.R. view in the forest with the horizon off"> |
|:---:|:---:|:---:|:---:|:---:|
| From the approach | From the road | Close to a crag | Virtual terrain | No horizon |
