# Runs the supported Ludusavi preview command for one expressly selected game.
param(
    [Parameter(Mandatory = $true)][string]$Ludusavi,
    [Parameter(Mandatory = $true)][ValidateSet('Backup', 'Restore')][string]$Action,
    [Parameter(Mandatory = $true)][string]$Game
)
$ErrorActionPreference = 'Stop'

function Read-PreviewOutput([Diagnostics.Process]$Process, [Diagnostics.Stopwatch]$Clock) {
    $text = New-Object Text.StringBuilder
    $buffer = New-Object char[] 8192
    while ($true) {
        $pending = $Process.StandardOutput.ReadAsync($buffer, 0, $buffer.Length)
        $remaining = [int][Math]::Max(0, 45000 - $Clock.ElapsedMilliseconds)
        if (-not $pending.Wait($remaining)) { throw 'Ludusavi preview timed out.' }
        $count = $pending.Result
        if ($count -eq 0) { return $text.ToString() }
        if ($text.Length + $count -gt 4MB) { throw 'Ludusavi preview exceeds the output limit.' }
        [void]$text.Append($buffer, 0, $count)
    }
}

function Convert-Preview([string]$Output, [int]$ExitCode) {
    try { $report = ConvertFrom-Json -InputObject $Output -ErrorAction Stop }
    catch { throw 'The executable did not return a supported Ludusavi preview.' }
    if ($report.errors.unknownGames) { throw 'Ludusavi could not find the selected game.' }
    if ($ExitCode -ne 0 -or $report.errors -or $report.games -isnot [pscustomobject]) {
        throw 'Ludusavi could not complete this preview.'
    }
    $games = @($report.games.PSObject.Properties)
    if ($games.Count -ne 1 -or $games[0].Name -cne $Game) {
        throw 'The preview did not resolve the exact game name. Use the name shown in Ludusavi.'
    }
    $selected = $games[0].Value
    $states = @('New', 'Different', 'Removed', 'Same', 'Unknown')
    if ($selected.change -cnotin $states -or $selected.files -isnot [pscustomobject] -or
        $selected.registry -isnot [pscustomobject] -or $selected.decision -isnot [string]) {
        throw 'The executable returned an unsupported game preview.'
    }
    $files = @($selected.files.PSObject.Properties | Where-Object { -not $_.Value.ignored })
    $registry = @($selected.registry.PSObject.Properties | Where-Object { -not $_.Value.ignored })
    $changedFiles = @($files | Where-Object { $_.Value.change -eq 'Different' -or $_.Value.change -eq 'Removed' })
    $changedRegistry = @($registry | Where-Object { $_.Value.change -eq 'Different' -or $_.Value.change -eq 'Removed' })
    [pscustomobject]@{
        Operation = "$Action preview"
        Game = $Game
        Decision = $selected.decision
        Change = $selected.change
        Files = $files.Count
        RegistryKeys = $registry.Count
        DifferentOrRemovedFiles = $changedFiles.Count
        DifferentOrRemovedRegistryKeys = $changedRegistry.Count
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
$start.Arguments = '--no-manifest-update ' + $Action.ToLowerInvariant() + ' --preview --api --no-cloud-sync'
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
    $wire = [Text.Encoding]::UTF8.GetBytes($Game + "`n")
    $process.StandardInput.BaseStream.Write($wire, 0, $wire.Length)
    $process.StandardInput.Close()
    $output = Read-PreviewOutput $process $clock
    $remaining = [int][Math]::Max(0, 45000 - $clock.ElapsedMilliseconds)
    if (-not $process.WaitForExit($remaining)) { throw 'Ludusavi preview timed out.' }
    Convert-Preview $output $process.ExitCode
} finally {
    try {
        if ($started -and -not $process.HasExited) { $process.Kill() }
    } catch [InvalidOperationException] {
        # The process can exit between the status check and Kill.
    } finally { $process.Dispose() }
}
