param([Parameter(Mandatory = $true)][string]$Screenshot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$base = Split-Path -Parent $PSScriptRoot
Add-Type -Path (Join-Path $base 'NexusAudioOutput.cs')
$task = [PocketNexus.NexusAudioOutput]::Sample()
if (-not $task.Wait(12000)) { throw 'The Core Audio query did not return.' }
$readings = $task.Result
if ($readings.Length -ne 2 -or $readings[0].Role -ne 'Default media output' -or
        $readings[1].Role -ne 'Default communications output') { throw 'Both Windows audio roles must be represented.' }
foreach ($reading in $readings) {
    if ($null -ne $reading.Volume -and ($reading.Volume -lt 0 -or $reading.Volume -gt 100)) { throw 'Invalid endpoint volume.' }
    if (-not $reading.HasEndpoint -and ($null -ne $reading.Name -or $null -ne $reading.Volume -or $null -ne $reading.Muted)) {
        throw 'A missing endpoint reported values.'
    }
    Write-Host "$($reading.Role) endpoint available: $($reading.HasEndpoint)"
}

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 120
$clock = [Diagnostics.Stopwatch]::StartNew()
$state = @{ Refreshed = $false; Complete = $false; Failure = $null }
$timer.add_Tick({
    $form = [System.Windows.Forms.Application]::OpenForms | Where-Object Text -eq 'Audio output | Nexus Host' | Select-Object -First 1
    if (-not $form) { return }
    try {
        if ($clock.ElapsedMilliseconds -gt 22000) { throw 'The Windows Audio output panel did not refresh.' }
        $details = $form.Controls | Where-Object AccessibleName -eq 'Observed Windows default audio outputs'
        $buttons = $form.Controls | Where-Object { $_ -is [System.Windows.Forms.FlowLayoutPanel] }
        $refresh = $buttons.Controls | Where-Object Text -eq 'Refresh audio outputs'
        if (-not ($buttons.Controls | Where-Object Text -eq 'Open Windows Sound settings')) { throw 'Sound settings entry is missing.' }
        if ($details.Text -notmatch '(?s)Checked .*Default media output.*Default communications output') { return }
        if (-not $state.Refreshed) {
            $state.Refreshed = $true
            $refresh.PerformClick()
            return
        }
        if (-not $refresh.Enabled) { return }
        $path = [IO.Path]::GetFullPath($Screenshot)
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))
        $bitmap = New-Object System.Drawing.Bitmap($form.Width, $form.Height)
        try {
            $form.DrawToBitmap($bitmap, (New-Object System.Drawing.Rectangle(0, 0, $form.Width, $form.Height)))
            $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $bitmap.Dispose() }
        $state.Complete = $true
        $timer.Stop()
        $form.Close()
    } catch {
        $state.Failure = $_.Exception.Message
        $timer.Stop()
        $form.Close()
    }
}.GetNewClosure())
$timer.Start()
try {
    & (Join-Path $base 'Show-NexusAudioOutput.ps1')
    if (-not $state.Complete) { throw $state.Failure }
    Write-Host 'PASS: Nexus Host reads and refreshes Windows audio-role observations.'
} finally { $timer.Dispose() }
