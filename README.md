# Velocity GPS

A native Android GPS speedometer built with Kotlin, Jetpack Compose, and Material 3.

## Included

- Live GPS speed using Google Play services Fused Location Provider
- Digital, analog, and hybrid speedometer styles
- Spring-driven analog needle and progress animations
- Rolling/slide-and-scale speed number animation (not a fade-only transition)
- Slide + scale transitions between Speed and Settings screens
- Animated Material bottom navigation
- km/h, mph, and knots
- Trip distance, elapsed time, average speed, maximum speed, and compass heading
- GPS accuracy status
- Responsive / Balanced / Smooth GPS filtering
- Drift suppression at walking/stationary GPS noise levels
- Trip distance jump filtering for bad fixes
- 180 / 240 / 300 km/h analog dial ranges
- System / Light / Dark themes
- Material You dynamic color on Android 12+
- Keep-screen-awake option
- Persistent settings using SharedPreferences
- No background-location permission and no internet requirement for speed readings

## Requirements

- Android Studio with Android SDK 37 installed
- Android SDK Build Tools 36.0.0 or newer
- JDK 17+
- Gradle 9.6.x

The project uses Android Gradle Plugin 9.4.0, Kotlin Compose Compiler plugin 2.4.20, Compose BOM 2026.08.00, and play-services-location 21.4.0.

## Build

The easiest route is to open the `VelocityGPS` folder in Android Studio, let Gradle sync, then run the `app` configuration.

For command-line builds with Gradle 9.6 installed:

```bash
gradle :app:assembleDebug
```

If you want a normal Gradle wrapper in the project, generate it once:

```bash
gradle wrapper --gradle-version 9.6.0
./gradlew :app:assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GPS behavior

Velocity requests fine/coarse location only. Tracking starts while the activity is visible and stops when it is no longer visible. Raw GPS speed is low-pass filtered according to the selected smoothing mode. Very small speeds are treated as stationary to reduce GPS drift. Trip distance ignores poor-accuracy location pairs and implausible jumps.

## GitHub Actions APK builds

The repository includes `.github/workflows/build-debug.yml`.

- Every push to `main` builds `app-debug.apk` in GitHub Actions.
- You can also run it manually from **Actions → Build Debug APK → Run workflow**.
- Each successful run creates a GitHub **prerelease** tagged `debug-<run number>`.
- The release contains `VelocityGPS-debug.apk` plus its SHA-256 checksum.
- The same files are also retained as a GitHub Actions artifact for 30 days.

The workflow uses JDK 17 and Gradle 9.6.0, matching Android Gradle Plugin 9.4.0 compatibility requirements. It does not need a signing secret because it builds the standard Android debug variant.
