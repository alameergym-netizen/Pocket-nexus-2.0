param([Parameter(Mandatory = $true)][string]$Screenshot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

$directory = Join-Path ([IO.Path]::GetTempPath()) ('nexus-vault-view-' + [Guid]::NewGuid().ToString('N'))
$snapshot = 'NexusSave-20261125T103000Z-295ecc1e83aa'
function Add-FixtureSnapshot($Id, $Game) {
    $backup = Join-Path (Join-Path $directory $Id) 'Fixture Saves'
    [void][IO.Directory]::CreateDirectory($backup)
    $receipt = [ordered]@{}
    foreach ($name in @('mapping.yaml', 'backup.zip')) {
        $path = Join-Path $backup $name
        [IO.File]::WriteAllBytes($path, [Text.Encoding]::UTF8.GetBytes($Game + $name))
        $receipt['Fixture Saves/' + $name] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    @{ format = 1; game = $Game; files = $receipt } | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath (Join-Path (Split-Path -Parent $backup) 'NEXUS-SAVE-SHA256.json') -Encoding UTF8
}
$second = 'NexusSave-20261126T110000Z-295ecc1e83bb'
Add-FixtureSnapshot $snapshot 'Fixture Game'
Add-FixtureSnapshot $second 'Second Game'
Add-FixtureSnapshot 'NexusSave-20261126T120000Z-295ecc1e83cc' "Invalid`nName"
$ui = Join-Path (Split-Path -Parent $PSScriptRoot) 'Show-NexusSaveVault.ps1'
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 160
$clock = [Diagnostics.Stopwatch]::StartNew()
$state = @{ Phase = 0; Failure = $null; Done = $false }
$timer.add_Tick({
    $form = [System.Windows.Forms.Application]::OpenForms | Where-Object Text -eq 'Save Vault | Nexus Host' | Select-Object -First 1
    if (-not $form) { return }
    try {
        if ($clock.ElapsedMilliseconds -gt 20000) { throw "Timed out at Save Vault UI step $($state.Phase)." }
        $layout = $form.Controls[0]
        $actions = $layout.GetControlFromPosition(0, 3)
        $history = $layout.GetControlFromPosition(0, 4)
        $details = $layout.GetControlFromPosition(0, 5)
        if ($state.Phase -eq 0) {
            if (-not ($actions.Controls | Where-Object Text -eq 'Recover to folder...')) { throw 'The recovery action is missing.' }
            if (-not ($actions.Controls | Where-Object Text -eq 'Batch backup...')) { throw 'The batch backup entry is missing.' }
            $layout.GetControlFromPosition(1, 2).Text = $directory
            ($actions.Controls | Where-Object Text -eq 'Load history').PerformClick()
            $state.Phase = 1
        } elseif ($state.Phase -eq 1 -and $actions.Enabled) {
            if ($history.Items.Count -ne 2 -or $history.Items[0].Snapshot -cne $second -or
                    $history.Items[1].Snapshot -cne $snapshot -or $history.Items[0].Label -notlike 'Second Game *') {
                throw 'All-games history did not show the two valid, named snapshots in newest-first order.'
            }
            $history.SelectedIndex = 0
            ($actions.Controls | Where-Object Text -eq 'Verify snapshot').PerformClick()
            $state.Phase = 2
        } elseif ($state.Phase -eq 2 -and $details.Text -match 'verified with SHA-256') {
            if ($details.Text -notmatch [regex]::Escape($second)) { throw 'All-games verification used the wrong snapshot.' }
            $layout.GetControlFromPosition(1, 0).Text = 'Fixture Game'
            if ($history.Items.Count -ne 0) { throw 'Changing filters retained a stale selection.' }
            ($actions.Controls | Where-Object Text -eq 'Load history').PerformClick()
            $state.Phase = 3
        } elseif ($state.Phase -eq 3 -and $actions.Enabled) {
            if ($history.Items.Count -ne 1 -or $history.Items[0].Game -cne 'Fixture Game') { throw 'Exact game filter failed.' }
            $history.SelectedIndex = 0
            ($actions.Controls | Where-Object Text -eq 'Verify snapshot').PerformClick()
            $state.Phase = 4
        } elseif ($state.Phase -eq 4 -and $details.Text -match 'verified with SHA-256') {
            if ($details.Text -notmatch [regex]::Escape($snapshot)) { throw 'A different snapshot was verified.' }
            $output = [IO.Path]::GetFullPath($Screenshot)
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($output))
            $bitmap = New-Object System.Drawing.Bitmap($form.Width, $form.Height)
            try {
                $form.DrawToBitmap($bitmap, (New-Object System.Drawing.Rectangle(0, 0, $form.Width, $form.Height)))
                $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally { $bitmap.Dispose() }
            $state.Done = $true
            $timer.Stop()
            $form.Close()
        }
    } catch {
        $state.Failure = $_.Exception.Message
        $timer.Stop()
        $form.Close()
    }
}.GetNewClosure())
$timer.Start()
try {
    & $ui
    if (-not $state.Done) { throw $state.Failure }
    Write-Host 'PASS: Windows Save Vault opens, finds the game snapshot, and verifies its receipt.'
} finally {
    $timer.Dispose()
    [IO.Directory]::Delete($directory, $true)
}
