param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskTools = Join-Path $env:USERPROFILE '.cache\demos-android'
if (-not $env:JAVA_HOME) {
    $taskJdk = Get-ChildItem -LiteralPath (Join-Path $taskTools 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
if (-not $env:ANDROID_HOME -and (Test-Path -LiteralPath (Join-Path $taskTools 'sdk'))) { $env:ANDROID_HOME = Join-Path $taskTools 'sdk' }
if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME to an Android SDK containing platform 36 and build-tools 35.0.0.' }
Push-Location -LiteralPath $taskRoot
try {
    & (Join-Path $taskRoot 'gradlew.bat') @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
