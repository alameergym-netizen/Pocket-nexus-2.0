param($Owner)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
if (-not ('PocketNexus.NexusAudioOutput' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'NexusAudioOutput.cs')
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Audio output | Nexus Host'
$form.StartPosition = 'CenterParent'
$form.ClientSize = New-Object System.Drawing.Size(565, 320)
$form.MinimumSize = New-Object System.Drawing.Size(440, 255)
$form.AutoScaleMode = 'Font'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$details = New-Object System.Windows.Forms.TextBox
$details.Multiline = $true
$details.ReadOnly = $true
$details.Dock = 'Fill'
$details.ScrollBars = 'Vertical'
$details.AccessibleName = 'Observed Windows default audio outputs'
$details.TabStop = $true
$refresh = New-Object System.Windows.Forms.Button
$refresh.Text = 'Refresh audio outputs'
$settings = New-Object System.Windows.Forms.Button
$settings.Text = 'Open Windows Sound settings'
$settings.add_Click({
    try { Start-Process 'ms-settings:sound' }
    catch { $details.Text = 'Windows could not open Sound settings.' }
}.GetNewClosure())
$close = New-Object System.Windows.Forms.Button
$close.Text = 'Close'
$close.DialogResult = 'Cancel'
$form.CancelButton = $close
$buttons = New-Object System.Windows.Forms.FlowLayoutPanel
$buttons.Dock = 'Bottom'
$buttons.AutoSize = $true
$buttons.WrapContents = $true
foreach ($button in @($refresh, $settings, $close)) {
    $button.AutoSize = $true
    $buttons.Controls.Add($button)
}
$form.Controls.Add($details)
$form.Controls.Add($buttons)
if (-not [System.Windows.Forms.SystemInformation]::HighContrast) {
    $form.BackColor = [System.Drawing.Color]::FromArgb(17, 24, 36)
    $form.ForeColor = [System.Drawing.Color]::WhiteSmoke
    $details.BackColor = [System.Drawing.Color]::FromArgb(29, 40, 57)
    $details.ForeColor = [System.Drawing.Color]::WhiteSmoke
    foreach ($button in $buttons.Controls) {
        $button.FlatStyle = 'Flat'
        $button.BackColor = [System.Drawing.Color]::FromArgb(50, 72, 103)
        $button.ForeColor = [System.Drawing.Color]::WhiteSmoke
    }
}

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 120
$clock = New-Object Diagnostics.Stopwatch
$state = @{ Task = $null }
$refresh.add_Click({
    $details.Text = 'Reading Windows audio outputs...'
    $refresh.Enabled = $false
    $state.Task = [PocketNexus.NexusAudioOutput]::Sample()
    $clock.Restart()
    $timer.Start()
}.GetNewClosure())
$timer.add_Tick({
    if (-not $state.Task.IsCompleted -and $clock.ElapsedMilliseconds -lt 8000) { return }
    $timer.Stop()
    try {
        if (-not $state.Task.IsCompleted) { throw 'Windows Core Audio did not respond.' }
        $blocks = foreach ($sample in $state.Task.Result) {
            $name = if ($sample.Name) { $sample.Name } elseif ($sample.HasEndpoint) { 'Unnamed endpoint' } else { 'Unavailable' }
            $volume = if ($null -eq $sample.Volume) { 'Unavailable' } else { [string]$sample.Volume + '%' }
            $mute = if ($null -eq $sample.Muted) { 'Unavailable' } elseif ($sample.Muted) { 'Muted' } else { 'Unmuted' }
            "$($sample.Role)`r`n$name`r`nVolume: $volume   Mute: $mute"
        }
        $details.Text = 'Checked ' + (Get-Date -Format 'T') + "`r`n`r`n" + ($blocks -join "`r`n`r`n") +
            "`r`n`r`nIndividual Windows apps can use different outputs."
    } catch {
        $details.Text = 'Windows audio outputs are unavailable. Open Sound settings or try refreshing.'
    } finally {
        $state.Task = $null
        $refresh.Enabled = $true
    }
}.GetNewClosure())
$form.Add_Shown({ $refresh.PerformClick() }.GetNewClosure())
try {
    if ($Owner) { [void]$form.ShowDialog($Owner) } else { [void]$form.ShowDialog() }
} finally {
    $timer.Dispose()
    $form.Dispose()
}
