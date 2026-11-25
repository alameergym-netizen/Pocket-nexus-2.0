# Shared runner for the bounded local Ludusavi save helpers.
param(
    [Parameter(Mandatory = $true)][string]$Ludusavi,
    [Parameter(Mandatory = $true)][ValidateSet('Backup', 'Restore')][string]$Action,
    [Parameter(Mandatory = $true)][string]$Game,
    [string]$ConfigDirectory,
    [string]$BackupDirectory,
    [switch]$WriteBackup
)
$ErrorActionPreference = 'Stop'

function Quote-WindowsArgument([string]$Argument) {
    # Double backslashes before an embedded or closing quote for Windows argv parsing.
    $escaped = [regex]::Replace($Argument, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Read-LudusaviOutput([Diagnostics.Process]$Process, [Diagnostics.Stopwatch]$Clock, [int]$Timeout) {
    $text = New-Object Text.StringBuilder
    $buffer = New-Object char[] 8192
    while ($true) {
        $pending = $Process.StandardOutput.ReadAsync($buffer, 0, $buffer.Length)
        $remaining = [int][Math]::Max(0, $Timeout - $Clock.ElapsedMilliseconds)
        if (-not $pending.Wait($remaining)) { throw 'Ludusavi operation timed out.' }
        $count = $pending.Result
        if ($count -eq 0) { return $text.ToString() }
        if ($text.Length + $count -gt 4MB) { throw 'Ludusavi operation exceeds the output limit.' }
        [void]$text.Append($buffer, 0, $count)
    }
}

if ([string]::IsNullOrWhiteSpace($Game) -or $Game.Length -gt 256 -or $Game -match '[\p{Cc}]') {
    throw 'Enter one Ludusavi game name without control characters.'
}
$exe = Get-Item -LiteralPath $Ludusavi
if ($exe -isnot [IO.FileInfo] -or $exe.Extension -ne '.exe') { throw 'Choose the local Ludusavi executable.' }
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = $exe.FullName
$start.WorkingDirectory = $exe.DirectoryName
$command = @('--no-manifest-update')
if ($ConfigDirectory) {
    $config = Get-Item -LiteralPath $ConfigDirectory
    if (-not $config.PSIsContainer) { throw 'Choose an existing Ludusavi configuration folder.' }
    $command += @('--config', $config.FullName)
}
$command += $Action.ToLowerInvariant()
if (-not $WriteBackup) { $command += '--preview' }
$command += @('--api', '--no-cloud-sync')
if ($WriteBackup) {
    if ($Action -ne 'Backup' -or -not $BackupDirectory) { throw 'Choose a separate backup destination.' }
    $stage = Get-Item -LiteralPath $BackupDirectory -Force
    if (-not $stage.PSIsContainer -or ($stage.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        $stage.Name -cnotmatch '^\.staging-NexusSave-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$' -or
        @(Get-ChildItem -LiteralPath $stage.FullName -Force).Count -ne 0) {
        throw 'A backup write requires a new empty staging folder.'
    }
    $command += @('--force', '--format', 'zip')
}
if ($BackupDirectory) { $command += @('--path', $BackupDirectory) }
$command += @('--', $Game)
$start.Arguments = ($command | ForEach-Object { Quote-WindowsArgument $_ }) -join ' '
$timeout = if ($WriteBackup) { 180000 } else { 45000 }
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.StandardOutputEncoding = New-Object Text.UTF8Encoding($false, $true)
$process = New-Object Diagnostics.Process
$process.StartInfo = $start
$started = $false
try {
    $started = $process.Start()
    if (-not $started) { throw 'Could not start Ludusavi.' }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    [void]$process.StandardError.BaseStream.CopyToAsync([IO.Stream]::Null)
    $process.StandardInput.Close()
    $output = Read-LudusaviOutput $process $clock $timeout
    $remaining = [int][Math]::Max(0, $timeout - $clock.ElapsedMilliseconds)
    if (-not $process.WaitForExit($remaining)) { throw 'Ludusavi operation timed out.' }
    try { $report = ConvertFrom-Json -InputObject $output -ErrorAction Stop }
    catch { throw 'The executable did not return a supported Ludusavi report.' }
    if ($report.errors.unknownGames) { throw 'Ludusavi could not find the selected game.' }
    if ($process.ExitCode -ne 0) { throw "Ludusavi could not complete this operation (exit $($process.ExitCode))." }
    if ($report.errors -or $report.games -isnot [pscustomobject]) {
        throw 'The executable returned an incomplete report.'
    }
    $games = @($report.games.PSObject.Properties)
    if ($games.Count -ne 1 -or $games[0].Name -cne $Game) {
        throw 'The report did not resolve the exact game name. Use the name shown in Ludusavi.'
    }
    $selected = $games[0].Value
    $states = @('New', 'Different', 'Removed', 'Same', 'Unknown')
    if ($selected.change -cnotin $states -or $selected.files -isnot [pscustomobject] -or
        $selected.registry -isnot [pscustomobject] -or $selected.decision -isnot [string]) {
        throw 'The executable returned an unsupported game report.'
    }
    return $selected
} finally {
    try {
        if ($started -and -not $process.HasExited) { $process.Kill() }
    } catch [InvalidOperationException] {
        # The process can exit between the status check and Kill.
    } finally { $process.Dispose() }
}
