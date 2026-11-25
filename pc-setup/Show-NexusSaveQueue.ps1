param($Owner, [string]$Ludusavi, [string]$VaultDirectory, [string]$Game, [string]$ConfigDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Backup queue | Nexus Host'
$form.StartPosition = 'CenterParent'
$form.ClientSize = New-Object System.Drawing.Size(680, 580)
$form.MinimumSize = New-Object System.Drawing.Size(560, 480)
$form.AutoScaleMode = 'Font'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$layout = New-Object System.Windows.Forms.TableLayoutPanel
$layout.Dock = 'Fill'
$layout.Padding = New-Object System.Windows.Forms.Padding(14)
$layout.ColumnCount = 3
$layout.RowCount = 7
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::AutoSize)))
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 100)))
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::AutoSize)))
foreach ($height in @(32, 32, 26, 110, 44)) {
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, $height)))
}
[void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Percent, 100)))
[void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, 65)))
$fields = @()
$pickers = @()
foreach ($name in @('Ludusavi executable', 'Local vault folder')) {
    $index = $fields.Count
    $label = New-Object System.Windows.Forms.Label
    $label.Text = $name
    $label.AutoSize = $true
    $label.Anchor = 'Left'
    $field = New-Object System.Windows.Forms.TextBox
    $field.Dock = 'Fill'
    $field.ReadOnly = $true
    $field.AccessibleName = $name
    $field.Text = if ($index -eq 0) { $Ludusavi } else { $VaultDirectory }
    $pick = New-Object System.Windows.Forms.Button
    $pick.Text = 'Browse...'
    $pick.AutoSize = $true
    $pick.AccessibleName = 'Browse ' + $name
    $isExecutable = $index -eq 0
    $pick.add_Click({
        if ($isExecutable) {
            $dialog = New-Object System.Windows.Forms.OpenFileDialog
            $dialog.Filter = 'Ludusavi executable (*.exe)|*.exe'
            $dialog.FileName = 'ludusavi.exe'
        } else {
            $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
            $dialog.Description = 'Choose the local Save Vault folder.'
        }
        try {
            if ($dialog.ShowDialog($form) -eq 'OK') {
                $field.Text = if ($isExecutable) { $dialog.FileName } else { $dialog.SelectedPath }
            }
        } finally { $dialog.Dispose() }
    }.GetNewClosure())
    $layout.Controls.Add($label, 0, $index)
    $layout.Controls.Add($field, 1, $index)
    $layout.Controls.Add($pick, 2, $index)
    $fields += $field
    $pickers += $pick
}
$hint = New-Object System.Windows.Forms.Label
$hint.Text = 'Exact Ludusavi game names, one per line. Close these games before backing up.'
$hint.AutoSize = $true
$layout.Controls.Add($hint, 0, 2)
$layout.SetColumnSpan($hint, 3)
$names = New-Object System.Windows.Forms.TextBox
$names.Multiline = $true
$names.ScrollBars = 'Vertical'
$names.Dock = 'Fill'
$names.MaxLength = 8256
$names.AccessibleName = 'Games to back up, one exact name per line'
$names.Text = $Game
$layout.Controls.Add($names, 0, 3)
$layout.SetColumnSpan($names, 3)
$buttons = New-Object System.Windows.Forms.FlowLayoutPanel
$buttons.Dock = 'Fill'
$buttons.WrapContents = $true
$start = New-Object System.Windows.Forms.Button
$start.Text = 'Start backup queue'
$stop = New-Object System.Windows.Forms.Button
$stop.Text = 'Stop after current game'
$stop.Enabled = $false
$close = New-Object System.Windows.Forms.Button
$close.Text = 'Close'
$close.DialogResult = 'Cancel'
$form.CancelButton = $close
foreach ($button in @($start, $stop, $close)) {
    $button.AutoSize = $true
    $buttons.Controls.Add($button)
}
$layout.Controls.Add($buttons, 0, 4)
$layout.SetColumnSpan($buttons, 3)
$list = New-Object System.Windows.Forms.ListView
$list.View = 'Details'
$list.Dock = 'Fill'
$list.FullRowSelect = $true
$list.AccessibleName = 'Per-game backup results'
[void]$list.Columns.Add('Game', 280)
[void]$list.Columns.Add('Result', 290)
$layout.Controls.Add($list, 0, 5)
$layout.SetColumnSpan($list, 3)
$status = New-Object System.Windows.Forms.TextBox
$status.Multiline = $true
$status.ReadOnly = $true
$status.Dock = 'Fill'
$status.ScrollBars = 'Vertical'
$status.AccessibleName = 'Backup queue progress'
$status.Text = 'Up to 32 games. Each verified snapshot is retained separately in your vault. Live saves are left in place.'
$layout.Controls.Add($status, 0, 6)
$layout.SetColumnSpan($status, 3)
$form.Controls.Add($layout)
if (-not [System.Windows.Forms.SystemInformation]::HighContrast) {
    $form.BackColor = [System.Drawing.Color]::FromArgb(17, 24, 36)
    $form.ForeColor = [System.Drawing.Color]::WhiteSmoke
    foreach ($control in @($names, $list, $status) + $fields + @($start, $stop, $close) + $pickers) {
        $control.BackColor = [System.Drawing.Color]::FromArgb(29, 40, 57)
        $control.ForeColor = [System.Drawing.Color]::WhiteSmoke
    }
}

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 120
$state = @{ Worker = $null; Job = $null; Running = $false; Stop = $false; Close = $false;
    Index = 0; Games = @(); Completed = 0; Failed = 0; Executable = ''; Vault = '' }
$backup = Join-Path $PSScriptRoot 'Save-NexusGameBackup.ps1'
$start.add_Click({
    if ($state.Running) { return }
    $games = New-Object 'System.Collections.Generic.List[string]'
    $unique = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($line in ($names.Text -split '\r?\n')) {
        $name = $line.Trim()
        if (-not $name) { continue }
        if ($name.Length -gt 256 -or $name -match '[\p{Cc}]') {
            $status.Text = 'Use exact names of up to 256 characters, without tabs or control characters.'
            return
        }
        if ($unique.Add($name)) { $games.Add($name) }
    }
    if ($games.Count -lt 1 -or $games.Count -gt 32) { $status.Text = 'Enter between 1 and 32 different game names.'; return }
    if (-not $fields[0].Text -or -not $fields[1].Text) { $status.Text = 'Choose Ludusavi and a local vault folder.'; return }
    $state.Games = $games.ToArray()
    $state.Executable = $fields[0].Text
    $state.Vault = $fields[1].Text
    $state.Index = 0
    $state.Completed = 0
    $state.Failed = 0
    $state.Stop = $false
    $state.Close = $false
    $state.Running = $true
    $list.Items.Clear()
    foreach ($name in $state.Games) {
        $row = New-Object System.Windows.Forms.ListViewItem($name)
        [void]$row.SubItems.Add('Waiting')
        [void]$list.Items.Add($row)
    }
    foreach ($control in @($names, $start) + $fields + $pickers) { $control.Enabled = $false }
    $stop.Enabled = $true
    $status.Text = 'Starting the backup queue...'
    $timer.Start()
}.GetNewClosure())
$stop.add_Click({
    $state.Stop = $true
    $stop.Enabled = $false
    $status.Text = 'Stopping after the current game. Verified snapshots are retained.'
}.GetNewClosure())
$timer.add_Tick({
    if ($state.Worker) {
        if (-not $state.Job.IsCompleted) { return }
        $row = $list.Items[$state.Index]
        try {
            $result = @($state.Worker.EndInvoke($state.Job))
            if ($state.Worker.HadErrors) { throw $state.Worker.Streams.Error[0].Exception }
            if ($result.Count -ne 1 -or $result[0].Result -cne 'CREATED_LOCAL_SAVE_BACKUP') { throw 'The backup returned no verified snapshot.' }
            $row.SubItems[1].Text = 'Verified snapshot created'
            $row.ToolTipText = $result[0].Directory
            $state.Completed++
        } catch {
            $row.SubItems[1].Text = 'Failed'
            $row.ToolTipText = $_.Exception.GetBaseException().Message
            $status.Text = $row.Text + ': ' + $row.ToolTipText
            $state.Failed++
        } finally {
            $state.Worker.Dispose()
            $state.Worker = $null
            $state.Job = $null
            $state.Index++
        }
    }
    if ($state.Stop -or $state.Index -ge $state.Games.Count) {
        $timer.Stop()
        for ($i = $state.Index; $i -lt $state.Games.Count; $i++) { $list.Items[$i].SubItems[1].Text = 'Not started' }
        $state.Running = $false
        $stop.Enabled = $false
        foreach ($control in @($names, $start) + $fields + $pickers) { $control.Enabled = $true }
        $status.Text = "Queue finished. Verified: $($state.Completed)   Failed: $($state.Failed)   Not started: $($state.Games.Count - $state.Index)"
        if ($state.Close) { $form.Close() }
        return
    }
    $worker = [System.Management.Automation.PowerShell]::Create()
    $arguments = @{ Ludusavi = $state.Executable; Game = $state.Games[$state.Index]; VaultDirectory = $state.Vault }
    if ($ConfigDirectory) { $arguments.ConfigDirectory = $ConfigDirectory }
    try {
        [void]$worker.AddCommand($backup).AddParameters($arguments)
        $state.Job = $worker.BeginInvoke()
        $state.Worker = $worker
        $list.Items[$state.Index].SubItems[1].Text = 'Backing up and verifying'
        $status.Text = "Game $($state.Index + 1) of $($state.Games.Count): $($state.Games[$state.Index])"
    } catch {
        $worker.Dispose()
        $list.Items[$state.Index].SubItems[1].Text = 'Failed'
        $list.Items[$state.Index].ToolTipText = $_.Exception.GetBaseException().Message
        $state.Failed++
        $state.Index++
    }
}.GetNewClosure())
$list.ShowItemToolTips = $true
$list.add_SelectedIndexChanged({
    if ($list.SelectedItems.Count -and $list.SelectedItems[0].ToolTipText) { $status.Text = $list.SelectedItems[0].ToolTipText }
}.GetNewClosure())
$form.add_FormClosing({
    param($sender, $event)
    if ($state.Running) {
        $event.Cancel = $true
        $state.Close = $true
        $stop.PerformClick()
    }
}.GetNewClosure())
try {
    if ($Owner) { [void]$form.ShowDialog($Owner) } else { [void]$form.ShowDialog() }
} finally {
    $timer.Dispose()
    $form.Dispose()
}
