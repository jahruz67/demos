# Automatic Android releases

Every push to a branch runs `.github/workflows/android-release.yml`, builds a signed universal APK, and publishes it as a GitHub Release. Each build gets an increasing Android `versionCode` (starting at 1001 so it can update the current version 1 app) and its own `build-N` release tag. The application ID remains `dev.demos.graphing`; installs signed with the same release key can update one another without uninstalling.

The APK currently in `dist/` is signed with Android's debug key, which differs from this new release key. Android will require a one-time uninstall when moving from that debug build to the first release APK. After the first release is installed, later releases signed by this persistent key can be installed as updates without uninstalling.

## One-time GitHub setup

The release key is generated once and must remain the same for every build. Keep the `.jks` file and its passwords private and backed up. Never commit the key or passwords. In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add these four secrets:

| Secret name | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the generated JKS file (PowerShell command below) |
| `ANDROID_KEYSTORE_PASSWORD` | Contents of the local `release-password.txt` file |
| `ANDROID_KEY_ALIAS` | `demos-release` |
| `ANDROID_KEY_PASSWORD` | Same value as `ANDROID_KEYSTORE_PASSWORD` |

The key files live outside this checkout under `%USERPROFILE%\.cache\demos-android\signing`. To copy the base64 value to the clipboard in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.cache\demos-android\signing\release.keystore")) | Set-Clipboard
```

Then paste it as the `ANDROID_KEYSTORE_BASE64` secret. Read the password to paste into both password secrets with:

```powershell
Get-Content "$env:USERPROFILE\.cache\demos-android\signing\release-password.txt" -Raw
```

After the workflow file and app changes are pushed to GitHub and the four secrets are set, each subsequent branch commit will produce a release. The APK can be downloaded from the repository's **Releases** page. GitHub Actions must be enabled, and the workflow needs permission to write repository contents/releases.
