# Windows desktop app

The Windows desktop app shares the Android tablet worksheet, calculator, expression engine, graph data, saved documents, and settings. It starts in the tablet split layout and includes native Windows file pickers, clipboard integration, offline symbolic evaluation, 2D and 3D graphs, graph image/PDF export, and a standalone Windows installer.

Android and Windows both compile `shared/src/main/` and render the same `CalculatorApp`, including the keypad, graph controls, and Compose 2D/3D renderers. Mouse and touch input use the same graph implementation. Keep app features in the shared sources; platform hosts provide window/lifecycle handling and native services without excluding or replacing shared UI files.

## Build and run

Use JDK 17, Gradle's wrapper, CMake, and an MSYS2 `CLANG64` environment with the upstream packages `base-devel`, `mingw-w64-clang-x86_64-cc`, `mingw-w64-clang-x86_64-gmp`, `mingw-w64-clang-x86_64-mpfr`, `mingw-w64-clang-x86_64-cmake`, and `mingw-w64-clang-x86_64-make` installed. Build the Windows JNI library from the included, pinned Giac source:

```bash
unzip third_party/giac/giac-70507-source.zip -d /tmp/giac
cd /tmp/giac/giac-19a710aea9d528afa272ea582f3283f242e17af3
CC=clang CXX=clang++ cmake -G "MinGW Makefiles" -B build .
cmake --build build --target javagiac --parallel 2
```

Copy `javagiac.dll` to `desktop/build/native-dist/javagiac.dll` and return to the repository root:

```powershell
Copy-Item <path-to-giac-source>/build/javagiac.dll desktop/build/native-dist/javagiac.dll
.\gradlew.bat :desktop:run
```

Build a signed-free distributable Windows installer with:

```powershell
.\gradlew.bat :desktop:packageExe
```

The EXE installer is written to `desktop/build/compose/binaries/main/exe/`. It includes the native Giac DLL and license notices. Releases attach only the EXE and Android APK; the release notes link to the exact repository revision containing the application and bundled native dependency sources.

## Native resource packaging invariant

`desktop/build.gradle.kts` defines `nativeResourceDirectory` once and supplies it to the launcher. The required pipeline is `native-dist/javagiac.dll` → `appResources/windows/native/javagiac.dll` → Compose's collected `native/javagiac.dll` → the installed `app/resources/native/javagiac.dll`. Compose selects `common/` and `windows/` and removes those prefixes; a top-level `appResources/native/` directory is ignored.

`prepareWindowsResources` must finish before `prepareAppResources`. The staging task uses `Sync` to remove stale files, and `verifyWindowsResources` checks Compose's actual output against the source DLL before packaging or running. The release workflow independently installs the finished EXE on its disposable Windows runner and checks the installed relative path and SHA-256 before upload. Preserve these guards when changing packaging; checking only the staging directory cannot detect this regression. Do not hard-code a second native resource directory in the runtime.

## Startup measurement

The packaged runtime includes a CDS archive and uses two JIT compiler threads to reduce startup contention while retaining full JVM optimization. Windows preferences initialize on the recovery IO thread.

Build the app image and measure three separate process launches:

```powershell
.\gradlew.bat :desktop:createDistributable
.\scripts\measure-windows-startup.ps1
```

The script measures launcher invocation through the first calculator draw returning to the window event queue, then closes each app. It does not clear the OS file cache or measure reboot-cold disk access. Recovery of large saved documents and graph sampling can continue after that first frame. Set `-Executable` to measure another app image or installed executable. Startup tracing is disabled unless `DEMOS_STARTUP_TRACE` names an output file.

## Continuous releases

Every push builds both platforms, with superseded runs on the same branch cancelled. GitHub Releases attach only `Demos-universal.apk` and `Demos-setup.exe`. Gradle dependencies and build outputs are cached. The native DLL cache is keyed by runner OS/architecture, the pinned Giac source, and the workflow; a cache hit skips MSYS2 setup and native compilation. The DLL is cached before installer packaging so a packaging failure does not force recompilation on retry. The Android signing setup is documented in [ANDROID_RELEASES.md](ANDROID_RELEASES.md).
