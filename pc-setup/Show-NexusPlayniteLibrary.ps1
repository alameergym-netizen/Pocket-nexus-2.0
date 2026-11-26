param($Owner, [string]$DataDirectory = (Join-Path $env:LOCALAPPDATA 'ArtemisPocket'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
if (-not ('PocketNexus.NexusPlayniteCatalog' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'NexusPlayniteCatalog.cs') -ErrorAction Stop
}
$path = Join-Path $DataDirectory 'playnite-catalog.tsv'
$form = New-Object System.Windows.Forms.Form
$form.Text = 'Playnite Library | Nexus Host'
$form.StartPosition = 'CenterParent'
$form.ClientSize = New-Object System.Drawing.Size(760, 500)
$form.MinimumSize = New-Object System.Drawing.Size(600, 420)
$form.AutoScaleMode = 'Font'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$layout = New-Object System.Windows.Forms.TableLayoutPanel
$layout.Dock = 'Fill'
$layout.Padding = New-Object System.Windows.Forms.Padding(16)
$layout.ColumnCount = 1
$layout.RowCount = 5
foreach ($height in @(64, 34)) {
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, $height)))
}
[void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Percent, 100)))
foreach ($height in @(56, 70)) {
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, $height)))
}
$intro = New-Object System.Windows.Forms.Label
$intro.Dock = 'Fill'
$intro.Text = 'Export visible games in Playnite > Extensions > Pocket Nexus. Refresh reads that manual snapshot; installation state may have changed since export.'
$layout.Controls.Add($intro, 0, 0)
$search = New-Object System.Windows.Forms.TextBox
$search.Dock = 'Fill'
$search.AccessibleName = 'Filter exported games by name'
$search.MaxLength = 200
$layout.Controls.Add($search, 0, 1)
$list = New-Object System.Windows.Forms.ListView
$list.Dock = 'Fill'
$list.View = 'Details'
$list.FullRowSelect = $true
$list.MultiSelect = $false
$list.HideSelection = $false
$list.AccessibleName = 'Manually exported Playnite games'
[void]$list.Columns.Add('Game', 440)
[void]$list.Columns.Add('State at export', 210)
$layout.Controls.Add($list, 0, 2)
$actions = New-Object System.Windows.Forms.FlowLayoutPanel
$actions.Dock = 'Fill'
$actions.WrapContents = $true
$layout.Controls.Add($actions, 0, 3)
$status = New-Object System.Windows.Forms.TextBox
$status.Dock = 'Fill'
$status.Multiline = $true
$status.ReadOnly = $true
$status.AccessibleName = 'Playnite library status'
$status.ScrollBars = 'Vertical'
$layout.Controls.Add($status, 0, 4)
$buttons = @{}
foreach ($label in @('Refresh', 'Open details', 'Start in Playnite', 'Close')) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $label
    $button.AutoSize = $true
    $actions.Controls.Add($button)
    $buttons[$label] = $button
}
$form.Controls.Add($layout)
$form.CancelButton = $buttons['Close']
$state = @{ Snapshot = $null; Task = $null; Kind = ''; Deadline = $null; TimedOut = $false }
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 120
$updateActions = {
    $entry = if ($list.SelectedItems.Count) { $list.SelectedItems[0].Tag } else { $null }
    $buttons['Refresh'].Enabled = $null -eq $state.Task
    $buttons['Open details'].Enabled = $null -eq $state.Task -and $null -ne $entry
    $buttons['Start in Playnite'].Enabled = $null -eq $state.Task -and $null -ne $entry -and $entry.Installed
}.GetNewClosure()
$render = {
    $selected = if ($list.SelectedItems.Count) { $list.SelectedItems[0].Tag.Id } else { [Guid]::Empty }
    $list.BeginUpdate()
    try {
        $list.Items.Clear()
        if ($state.Snapshot) {
            foreach ($entry in $state.Snapshot.Entries) {
                if ($search.Text -and $entry.Name.IndexOf($search.Text, [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
                $item = New-Object System.Windows.Forms.ListViewItem($entry.Name)
                [void]$item.SubItems.Add($(if ($entry.Installed) { 'Installed at export' } else { 'Not installed at export' }))
                $item.Tag = $entry
                [void]$list.Items.Add($item)
                if ($entry.Id -eq $selected) { $item.Selected = $true }
            }
        }
    } finally { $list.EndUpdate() }
    & $updateActions
}.GetNewClosure()
$startTask = {
    param($Task, [string]$Kind)
    $state.Task = $Task
    $state.Kind = $Kind
    $state.Deadline = [DateTime]::UtcNow.AddSeconds(8)
    $state.TimedOut = $false
    $status.Text = if ($Kind -eq 'Read') { 'Reading the local Playnite export...' } else { 'Checking the current export and sending the selected request...' }
    & $updateActions
    $timer.Start()
}.GetNewClosure()
$refresh = {
    if ($state.Task) { return }
    $state.Snapshot = $null
    & $render
    & $startTask ([PocketNexus.NexusPlayniteCatalog]::ReadAsync($path)) 'Read'
}.GetNewClosure()
$buttons['Refresh'].add_Click($refresh)
$search.add_TextChanged($render)
$list.add_SelectedIndexChanged($updateActions)
foreach ($kind in @('showgame', 'start')) {
    $label = if ($kind -eq 'start') { 'Start in Playnite' } else { 'Open details' }
    $buttons[$label].add_Click({
        if ($state.Task -or -not $state.Snapshot -or -not $list.SelectedItems.Count) { return }
        $entry = $list.SelectedItems[0].Tag
        & $startTask ([PocketNexus.NexusPlayniteCatalog]::OpenAsync($path, $state.Snapshot.Sha256, $entry.Id.ToString('D'), $kind)) 'Open'
    }.GetNewClosure())
}
$buttons['Close'].add_Click({ $form.Close() }.GetNewClosure())
$timer.add_Tick({
    if (-not $state.Task) { $timer.Stop(); return }
    if (-not $state.Task.IsCompleted) {
        if (-not $state.TimedOut -and [DateTime]::UtcNow -gt $state.Deadline) {
            $state.TimedOut = $true
            $state.Snapshot = $null
            & $render
            $status.Text = 'The local operation is taking too long. Its result will be ignored. You can close this window.'
        }
        return
    }
    $timer.Stop()
    try {
        if ($state.Task.IsFaulted) { throw $state.Task.Exception.GetBaseException() }
        if (-not $state.TimedOut) {
            if ($state.Kind -eq 'Read') {
                $state.Snapshot = $state.Task.Result
                & $render
                $handler = if ([PocketNexus.NexusPlayniteCatalog]::IsProtocolRegistered()) { 'Playnite URI handler registered.' } else { 'Playnite URI handler unavailable; open Playnite to enable URI support.' }
                $status.Text = "$($state.Snapshot.Entries.Count) games in the manual export. $handler`r`nOnly the selected entry is sent to Playnite."
            } else { $status.Text = $state.Task.Result }
        }
    } catch {
        $state.Snapshot = $null
        & $render
        $status.Text = if ($_.Exception.GetBaseException() -is [IO.FileNotFoundException] -or $_.Exception.GetBaseException() -is [IO.DirectoryNotFoundException]) {
            'No export found. In Playnite, use Extensions > Pocket Nexus > Export visible games to Nexus Host, then Refresh.'
        } else { $_.Exception.GetBaseException().Message }
    } finally {
        $state.Task = $null
        & $updateActions
    }
}.GetNewClosure())
$form.add_Shown($refresh)
try {
    if ($Owner) { [void]$form.ShowDialog($Owner) } else { [void]$form.ShowDialog() }
} finally { $timer.Stop(); $timer.Dispose(); $form.Dispose() }
