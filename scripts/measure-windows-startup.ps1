param(
    [string]$Executable = "$PSScriptRoot\..\desktop\build\compose\binaries\main\app\Demos\Demos.exe",
    [ValidateRange(1, 20)][int]$Runs = 3,
    [string]$JvmOptions = ''
)
$ErrorActionPreference = 'Stop'
$executablePath = (Resolve-Path -LiteralPath $Executable).Path

for ($run = 1; $run -le $Runs; $run++) {
    $tracePath = Join-Path ([IO.Path]::GetTempPath()) ("demos-startup-" + [Guid]::NewGuid() + '.csv')
    $startInfo = [Diagnostics.ProcessStartInfo]::new($executablePath)
    $startInfo.WorkingDirectory = Split-Path -Parent $executablePath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.EnvironmentVariables['DEMOS_STARTUP_TRACE'] = $tracePath
    if ($JvmOptions) { $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = $JvmOptions }
    $process = $null
    try {
        $startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $process = [Diagnostics.Process]::Start($startInfo)
        $trace = @()
        while ($timer.Elapsed.TotalSeconds -lt 30 -and -not $process.HasExited) {
            if (Test-Path -LiteralPath $tracePath) {
                $trace = @(Get-Content -LiteralPath $tracePath)
                if ($trace -match '^first-frame,') { break }
            }
            Start-Sleep -Milliseconds 10
        }
        if (-not ($trace -match '^first-frame,')) { throw "Startup did not render a frame within 30 seconds (exit: $($process.HasExited))." }
        $firstFrame = ($trace -match '^first-frame,')[0] -split ','
        $elapsed = [long]$firstFrame[1] - $startedAt
        $milestones = $trace | ForEach-Object {
            $stage, $timestamp = $_ -split ','
            "$stage=$([long]$timestamp - $startedAt)"
        }
        [pscustomobject]@{ Run = $run; LaunchToFrameMs = $elapsed; MilestonesMs = $milestones -join '; ' }
    } finally {
        if ($process) {
            if (-not $process.HasExited) {
                $process.Refresh()
                if (-not $process.CloseMainWindow() -or -not $process.WaitForExit(5000)) { $process.Kill() }
            }
            $process.Dispose()
        }
        Remove-Item -LiteralPath $tracePath -Force -ErrorAction SilentlyContinue
    }
}
