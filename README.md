# Demos

Demos is an Android and Windows graphing calculator built with Kotlin and Compose. Both apps use the same responsive calculator UI, native 2D and 3D graph rendering, and symbolic calculation through the native Giac computer algebra system.

## Features

- Math expression editing with exact and approximate results.
- 2D and 3D plots with configurable bounds, axes, grids, and appearance.
- Variable sliders with animation controls.
- Data tables, regression fitting, and CSV, TSV, and JSON import/export.
- Saved graphs, document import/export, and undo/redo.
- Graph export as PNG or PDF.
- Calculator, graph, and split layouts, plus degree/radian and complex-number settings.

The native CAS runs on-device in a separate Android process. Normal app builds use the bundled Giac AAR; rebuilding native libraries is not required. Symbolic calculation and both graph viewports work offline. The 3D viewport compiles the actual GeoGebra engine used by the `geographer` reference application. Its original geometry, camera, controller, shader pipeline, picking and math layout run through native OpenGL ES on Android and JOGL on desktop; Compose supplies the reference's floating controls. See [native 3D implementation and verification](docs/NATIVE_3D.md).

## Requirements

- JDK 17.
- Android SDK with platform 36 and Build Tools 35.0.0, matching the repository's build script and CI setup.
- An Android 8.0 (API 26) or newer device or emulator.

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
| `shared/src/main/kotlin/dev/demos/graphing/` | Shared calculator controller and application content |
| `shared/src/main/kotlin/dev/demos/graphing/ui/` | Shared Compose screens, math editor, worksheet, and controls |
| `shared/src/main/kotlin/dev/demos/graphing/plot/` | Shared native 2D/3D rendering, geometry, camera, settings, and gestures |
| `shared/src/main/kotlin/dev/demos/graphing/math/` | Shared numeric evaluation, syntax, regression, and statistics |
| `shared/src/main/kotlin/dev/demos/graphing/cas/` | Shared Giac evaluation and syntax |
| `shared/src/main/kotlin/dev/demos/graphing/data/` | Shared graph storage and table file handling |
| `shared/src/main/java/` | Shared Giac JNI declarations |
| `geogebra/` | Pinned GeoGebra Java engine build and native graphics/input/CAS bindings |
| `app/src/main/java/` | Android lifecycle, native services, file pickers, clipboard, and image adapters |
| `desktop/src/main/kotlin/` | Windows window host, native services, file pickers, clipboard, and image adapters |
| `app/src/test/` | JVM and Robolectric tests |
| `app/src/androidTest/` | Device and emulator tests |
| `app/libs/` | Bundled native Giac AAR |
| `third_party/giac/` | Native dependency sources and provenance |
| `third_party/geogebra/` | Exact engine source archive, reference wrapper snapshot, hashes and provenance |

Both platform builds compile the shared sources directly and render `CalculatorApp`. Make calculator and graph integration changes in `shared/` so they apply to phones, tablets, and Windows together. Layout adapts to available space; native file dialogs and other operating-system services remain in the platform hosts. The 3D engine renders into a native OpenGL framebuffer displayed by Compose. It requires no browser, JavaScript runtime or network connection.

## Releases and third-party notices

The GitHub Actions workflow builds a signed universal APK and a Windows EXE installer and publishes only those two files on GitHub Releases on pushes to any branch. The installer runs offline and includes the native Giac symbolic engine and license notices. Release notes link to the exact source revision. See [Windows desktop build and release](docs/WINDOWS.md) and [Android release setup](docs/ANDROID_RELEASES.md).

See [native Giac provenance and source](third_party/giac/README.md) for bundled dependency versions, corresponding source archives, and redistribution notices. License texts and attribution are included in the app under `assets/cas`.

See [GeoGebra source provenance and licenses](third_party/geogebra/README.md) for the 3D engine, ReTeX math fonts, viewport UI fonts and icons. Preserve the upstream per-file notices when redistributing the source or binaries.
