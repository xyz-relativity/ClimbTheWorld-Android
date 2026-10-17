# ClimbTheWorld-Android

Rock climbing frontend for OpenStreetMap.

* Install it with [Obtainium or ObtainX](#with-obtainium-or-obtainx) to get updates automatically,
  or download the APK from the [latest release](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases/latest).
* Support us on Patreon: https://www.patreon.com/climb_the_world?fan_landing=true

## Install

ClimbTheWorld is published as APKs on its
[GitHub releases](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases).

### With Obtainium or ObtainX

[Obtainium](https://github.com/ImranR98/Obtainium) and its fork
[ObtainX](https://github.com/bikram-agarwal/ObtainX) install apps straight from their GitHub
releases, and let you know when a new version is out. The steps are the same in both.

1. Install Obtainium (from [GitHub](https://github.com/ImranR98/Obtainium/releases),
   [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/dev.imranr.obtainium) or
   [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/)) or ObtainX (from
   [GitHub](https://github.com/bikram-agarwal/ObtainX/releases/latest) or
   [F-Droid](https://f-droid.org/packages/dev.bikram.obtainx/)).
2. On the phone, open
   [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/xyz-relativity/ClimbTheWorld-Android):
   it opens the **Add app** screen with ClimbTheWorld filled in. Or open **Add app** yourself, and
   enter `https://github.com/xyz-relativity/ClimbTheWorld-Android` as the **App source URL**.
3. Tap **Add**, keeping the default options: *Attempt to filter APKs by CPU architecture if
   possible* picks the APK built for the phone out of the ones each release carries.
4. Tap **Install** on the app's page, and allow Obtainium or ObtainX to install apps when Android
   asks.

From then on, Obtainium or ObtainX checks for new releases in the background and notifies you of
them, to install with **Update**. If it ever asks you to **Pick an APK**, take the one for the
phone's CPU type (`arm64-v8a` on most phones), or `universal`.

### By hand

Download one of the APKs of the
[latest release](https://github.com/xyz-relativity/ClimbTheWorld-Android/releases/latest), open it
on the phone, and allow the browser or file manager to install apps when Android asks:

* `ClimbTheWorld-Release-arm64-v8a.apk` for most phones;
* `ClimbTheWorld-Release-armeabi-v7a.apk` for older, 32-bit phones;
* `ClimbTheWorld-Release-x86_64.apk` and `ClimbTheWorld-Release-x86.apk` for emulators and Intel
  devices;
* `ClimbTheWorld-Release-universal.apk` works on all of them, but is larger.

New versions then have to be downloaded and installed the same way.

## Screenshots

### Augmented reality

Point the camera at the rock to see the crags and routes from OpenStreetMap where they are.

| <img src="pics/ar-cliff-from-approach.png" width="120" alt="A.R. view of a cliff from the approach"> | <img src="pics/ar-cliff-from-road.png" width="120" alt="A.R. view of the same cliff from the road"> | <img src="pics/ar-crag-routes.png" width="120" alt="A.R. view close to a crag"> | <img src="pics/ar-forest-terrain.png" width="120" alt="A.R. view in the forest with the virtual terrain"> | <img src="pics/ar-forest-no-horizon.png" width="120" alt="A.R. view in the forest with the horizon off"> |
|:---:|:---:|:---:|:---:|:---:|
| From the approach | From the road | Close to a crag | Virtual terrain | No horizon |
