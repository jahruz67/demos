# Demos

Demos is an Android graphing calculator built with Kotlin and Jetpack Compose. It combines an editable math worksheet, 2D and 3D graphing, and symbolic calculation through the native Giac computer algebra system.

## Features

- Math expression editing with exact and approximate results.
- 2D and 3D plots with configurable bounds, axes, grids, and appearance.
- Variable sliders with animation controls.
- Data tables, regression fitting, and CSV, TSV, and JSON import/export.
- Saved graphs, document import/export, and undo/redo.
- Graph export as PNG or PDF.
- Calculator, graph, and split layouts, plus degree/radian and complex-number settings.

The native CAS runs on-device in a separate Android process. Normal app builds use the bundled Giac AAR; rebuilding native libraries is not required.

## Requirements

- JDK 17.
- Android SDK with platform 36 and Build Tools 35.0.0, matching the repository's build script and CI setup.
- An Android 8.0 (API 26) or newer device or emulator with OpenGL ES 3.0 support.

The project includes a Gradle 8.13 wrapper and supports `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, with both ABI-specific and universal APKs.

## Build locally

Set `JAVA_HOME` to your JDK 17 installation and `ANDROID_HOME` to your Android SDK directory. Run these commands from the repository root.

On Windows, build a debug APK with:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```sh
./gradlew assembleDebug
```

Debug APKs are written to `app/build/outputs/apk/debug/`. Choose the APK matching your device's ABI or use the universal APK.

The Windows helper can also build using the configured environment or tools under `%USERPROFILE%\.cache\demos-android`:

```powershell
.\scripts\build.ps1 -Tasks assembleDebug
```

Running the helper without `-Tasks` builds the debug APK and runs unit tests.

## Tests

Run JVM and Robolectric tests on Windows with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run instrumentation tests, including native Giac integration tests, with a connected device or running emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

On macOS or Linux, use `./gradlew` with the same task names.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/dev/demos/graphing/` | App entry points and calculator state management |
| `app/src/main/java/dev/demos/graphing/ui/` | Compose screens, math editor, worksheet, and controls |
| `app/src/main/java/dev/demos/graphing/plot/` | Graph rendering, geometry, and gestures |
| `app/src/main/java/dev/demos/graphing/math/` | Numeric evaluation, syntax, regression, and statistics |
| `app/src/main/java/dev/demos/graphing/cas/` | Giac adapter and process-backed CAS service |
| `app/src/main/java/dev/demos/graphing/data/` | Graph storage and table/image file handling |
| `app/src/test/` | JVM and Robolectric tests |
| `app/src/androidTest/` | Device and emulator tests |
| `app/libs/` | Bundled native Giac AAR |
| `third_party/giac/` | Native dependency sources and provenance |

## Releases and third-party notices

The GitHub Actions workflow builds a signed universal APK and a Windows MSI installer and publishes both on GitHub Releases on pushes to any branch. The installer runs offline and includes the native Giac symbolic engine. See [Windows desktop build and release](docs/WINDOWS.md) and [Android release setup](docs/ANDROID_RELEASES.md).

See [native Giac provenance and source](third_party/giac/README.md) for bundled dependency versions, corresponding source archives, and redistribution notices. License texts and attribution are included in the app under `assets/cas`.
