# Windows native resources

Before changing native library paths or packaging tasks, read the native resource packaging invariant in `../docs/WINDOWS.md`.

- Keep `nativeResourceDirectory` in `build.gradle.kts` as the shared setting for staging, verification, and the launcher.
- Compose collects `appResources/windows/native/`, not top-level `appResources/native/`.
- `prepareWindowsResources` must precede `prepareAppResources`.
- Preserve the guard on Compose's collected DLL and the independent EXE installation/hash guard in `.github/workflows/android-release.yml`. Do not weaken them to check only staging or bypass failures by removing guards.
- A repeated build may hide missing resources left by an earlier build. Do not infer that packaging is correct from existing build artifacts.
