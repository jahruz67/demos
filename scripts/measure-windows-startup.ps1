param(
    [string]$Executable = "$PSScriptRoot\..\desktop\build\compose\binaries\main\app\Demos\Demos.exe",
    [ValidateRange(1, 20)][int]$Runs = 3,
    [string]$JvmOptions = '',
    [ValidateRange(0, 60000)][int]$MaxStartupMs = 0,
    [string]$ClassList
)
$ErrorActionPreference = 'Stop'
$executablePath = (Resolve-Path -LiteralPath $Executable).Path
if ($ClassList -and $Runs -ne 1) { throw 'Record a startup class list with -Runs 1.' }

for ($run = 1; $run -le $Runs; $run++) {
    $tracePath = Join-Path ([IO.Path]::GetTempPath()) ("demos-startup-" + [Guid]::NewGuid() + '.csv')
    $classListPath = "$tracePath.classlist"
    $startInfo = [Diagnostics.ProcessStartInfo]::new($executablePath)
    $startInfo.WorkingDirectory = Split-Path -Parent $executablePath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.EnvironmentVariables['DEMOS_STARTUP_TRACE'] = $tracePath
    if ($JvmOptions) { $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = $JvmOptions }
    if ($ClassList) {
        $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = "$JvmOptions `"-XX:DumpLoadedClassList=$classListPath`""
    }
    $process = $null
    $rendered = $false
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
        $rendered = $true
        $firstFrame = ($trace -match '^first-frame,')[0] -split ','
        $elapsed = [long]$firstFrame[1] - $startedAt
        $milestones = $trace | ForEach-Object {
            $stage, $timestamp = $_ -split ','
            "$stage=$([long]$timestamp - $startedAt)"
        }
        [pscustomobject]@{ Run = $run; LaunchToFrameMs = $elapsed; MilestonesMs = $milestones -join '; ' }
        if ($MaxStartupMs -gt 0 -and $elapsed -ge $MaxStartupMs) {
            throw "Cold process startup took $elapsed ms; expected less than $MaxStartupMs ms (run $run)."
        }
    } finally {
        if ($process) {
            if (-not $process.HasExited) {
                $process.Refresh()
                if (-not $process.CloseMainWindow() -or -not $process.WaitForExit(5000)) { $process.Kill() }
            }
            $process.Dispose()
        }
        Remove-Item -LiteralPath $tracePath -Force -ErrorAction SilentlyContinue
        if ($ClassList -and $rendered -and (Test-Path -LiteralPath $classListPath)) {
            Get-Content -LiteralPath $classListPath |
                Where-Object { $_ -match '^(java|javax|sun|com/sun|jdk)/' } |
                Select-Object -Unique | Set-Content -LiteralPath $ClassList -Encoding ascii
        }
        Remove-Item -LiteralPath $classListPath -Force -ErrorAction SilentlyContinue
    }
}
