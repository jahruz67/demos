# Automatic Android releases

Every push to a branch runs `.github/workflows/android-release.yml`, builds a signed universal APK and a Windows installer, and publishes both on the same GitHub Release. Each build gets an increasing Android `versionCode` (starting at 1001 so it can update the current version 1 app) and its own `build-N` release tag. The application ID remains `dev.demos.graphing`; installs signed with the same release key can update one another without uninstalling. Windows packaging and native Giac source details are in [WINDOWS.md](WINDOWS.md).

The APK currently in `dist/` is signed with Android's debug key, which differs from this new release key. Android will require a one-time uninstall when moving from that debug build to the first release APK. After the first release is installed, later releases signed by this persistent key can be installed as updates without uninstalling.

CI passes `-PuniversalApkOnly=true` to build one APK containing all supported ABIs, avoiding four additional ABI APKs. Local builds keep the ABI splits by default. Gradle caching is enabled for both platforms; superseded runs on the same branch are cancelled.

## Release signing

The workflow signs releases with the checked-in `release.keystore`, using alias `demos-release` and password `android` for both the keystore and key. No GitHub signing secrets are needed. Keep this keystore unchanged so future APKs can update existing installs. Anyone with repository access can use this key to sign app updates. GitHub Actions must be enabled, and the workflow needs permission to write repository contents/releases.
