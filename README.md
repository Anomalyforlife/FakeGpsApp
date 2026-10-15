# Anomaly GPS

Android app for simulating GPS locations, replaying Google Maps or GPX routes at variable speeds, and recording real trips. Requires Android 8.0 or later. No root or account required.

## Installation

Download `AnomalyGPS.apk` from [Releases](https://github.com/Anomalyforlife/FakeGpsApp/releases), install it and select **Anomaly GPS** under **Developer options → Select mock location app**. Allow precise location and notifications.

Tracks stay on your device and can be exported as GPX. Uninstalling the app deletes local data. Google Maps routes are recalculated with OSRM using Driving or Walking and may differ from Google's routes. The app simulates GPS, not sensors; other apps can detect mock locations.

## Automated builds and releases

Every push to `main` starts the `APK release` workflow: it runs unit tests and Android lint, builds a signed release APK, and publishes the APK and SHA-256 checksum to a GitHub Release tagged `build-<run number>-<attempt>`. You can also run it manually from Actions on `main`.

Before the first run, add the **`ANDROID_KEYSTORE_BASE64`** repository secret under **Settings → Secrets and variables → Actions**, containing the Base64-encoded existing `.signing/debug.keystore`:

```sh
base64 -w 0 .signing/debug.keystore
```

Keep this key private and outside Git. The workflow requires it to preserve the signing identity used by versions 1.1–1.3, allowing updates without uninstalling. It uses the standard Android debug key alias and passwords. The release APK is not debuggable. Version 1.0 used a different signing key.

## Project

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2 and Gradle Wrapper 8.11.1. Source code, resources and tests are in `app/`.

For builds in ScoutSolutions, use only the Executor through `start_build`, monitoring `status`, `list_builds` and `get_build_log`; do not run Gradle in the AI container. The `:app:assembleDebug` task also runs unit tests and lint and preserves the local signing key in `.signing/`.
