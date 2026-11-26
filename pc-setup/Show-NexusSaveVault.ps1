param($Owner)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Save Vault | Nexus Host'
$form.StartPosition = 'CenterParent'
$form.ClientSize = New-Object System.Drawing.Size(720, 444)
$form.MinimumSize = New-Object System.Drawing.Size(560, 380)
$form.AutoScaleMode = 'Font'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$layout = New-Object System.Windows.Forms.TableLayoutPanel
$layout.Dock = 'Fill'
$layout.Padding = New-Object System.Windows.Forms.Padding(14)
$layout.ColumnCount = 3
$layout.RowCount = 6
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::AutoSize)))
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 100)))
[void]$layout.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::AutoSize)))
for ($i = 0; $i -lt 4; $i++) {
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::AutoSize)))
}
[void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Percent, 52)))
[void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Percent, 48)))

$fields = @()
$browsers = @()
foreach ($name in @('Exact game name', 'Ludusavi executable', 'Local vault folder')) {
    $i = $fields.Count
    $label = New-Object System.Windows.Forms.Label
    $label.Text = $name
    $label.AutoSize = $true
    $label.Anchor = 'Left'
    $field = New-Object System.Windows.Forms.TextBox
    $field.Dock = 'Fill'
    $field.AccessibleName = $name
    $field.MaxLength = if ($i -eq 0) { 256 } else { 4096 }
    $field.ReadOnly = $i -ne 0
    $layout.Controls.Add($label, 0, $i)
    $layout.Controls.Add($field, 1, $i)
    $fields += $field
    if ($i -eq 0) { continue }
    $pick = New-Object System.Windows.Forms.Button
    $pick.Text = 'Browse...'
    $pick.AutoSize = $true
    $pick.AccessibleName = 'Browse ' + $name
    $chooseFile = $i -eq 1
    $pick.add_Click({
        if ($chooseFile) {
            $dialog = New-Object System.Windows.Forms.OpenFileDialog
            $dialog.Filter = 'Ludusavi executable (*.exe)|*.exe'
            $dialog.FileName = 'ludusavi.exe'
        } else {
            $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
            $dialog.Description = 'Choose a local Nexus Save Vault folder.'
        }
        try {
            if ($dialog.ShowDialog($form) -eq 'OK') {
                $field.Text = if ($chooseFile) { $dialog.FileName } else { $dialog.SelectedPath }
            }
        } finally { $dialog.Dispose() }
    }.GetNewClosure())
    $layout.Controls.Add($pick, 2, $i)
    $browsers += $pick
}

$history = New-Object System.Windows.Forms.ListBox
$history.Dock = 'Fill'
$history.HorizontalScrollbar = $true
$history.AccessibleName = 'Save snapshots'
$history.DisplayMember = 'Label'
$layout.Controls.Add($history, 0, 4)
$layout.SetColumnSpan($history, 3)
$details = New-Object System.Windows.Forms.TextBox
$details.Multiline = $true
$details.ReadOnly = $true
$details.Dock = 'Fill'
$details.ScrollBars = 'Vertical'
$details.AccessibleName = 'Save Vault operation result'
$details.Text = 'Choose a vault and select Load history to see all snapshots. Enter an exact game name to filter.'
$layout.Controls.Add($details, 0, 5)
$layout.SetColumnSpan($details, 3)

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 150
$state = @{ Worker = $null; Job = $null; Operation = $null; CloseWhenReady = $false }
$paths = @{
    'Preview' = Join-Path $PSScriptRoot 'Get-NexusSavePreview.ps1'
    'Backup' = Join-Path $PSScriptRoot 'Save-NexusGameBackup.ps1'
    'History' = Join-Path $PSScriptRoot 'Get-NexusSaveVaultHistory.ps1'
    'Verify' = Join-Path $PSScriptRoot 'Get-NexusSaveVaultHistory.ps1'
    'RestorePreview' = Join-Path $PSScriptRoot 'Get-NexusSaveVaultHistory.ps1'
    'RecoverCopy' = Join-Path $PSScriptRoot 'Get-NexusSaveVaultHistory.ps1'
}
$actions = New-Object System.Windows.Forms.FlowLayoutPanel
$actions.Dock = 'Fill'
$actions.AutoSize = $true
$actions.WrapContents = $true
$layout.Controls.Add($actions, 0, 3)
$layout.SetColumnSpan($actions, 3)
foreach ($action in @(
    @{ Label = 'Preview backup'; Kind = 'Preview' }, @{ Label = 'Create backup'; Kind = 'Backup' },
    @{ Label = 'Load history'; Kind = 'History' }, @{ Label = 'Verify snapshot'; Kind = 'Verify' },
    @{ Label = 'Preview restore'; Kind = 'RestorePreview' }, @{ Label = 'Recover to folder...'; Kind = 'RecoverCopy' }
)) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $action.Label
    $button.AutoSize = $true
    $kind = $action.Kind
    $button.add_Click({
        if ($state.Worker) { return }
        $game = $fields[0].Text.Trim()
        $exe = $fields[1].Text
        $vault = $fields[2].Text
        if ($kind -in @('Preview', 'Backup') -and -not $game) { $details.Text = 'Enter the exact game name as shown in Ludusavi.'; return }
        if ($kind -in @('Preview', 'Backup', 'RestorePreview') -and -not $exe) { $details.Text = 'Choose the Ludusavi executable.'; return }
        if ($kind -ne 'Preview' -and -not $vault) { $details.Text = 'Choose a local vault folder.'; return }
        $arguments = @{ Game = $game }
        switch ($kind) {
            'Preview' { $arguments.Ludusavi = $exe; $arguments.Action = 'Backup' }
            'Backup' { $arguments.Ludusavi = $exe; $arguments.VaultDirectory = $vault }
            'History' {
                $history.Items.Clear()
                $arguments.VaultDirectory = $vault
                if (-not $game) {
                    $arguments.Remove('Game')
                    $arguments.AllGames = $true
                }
            }
            { $_ -in @('Verify', 'RestorePreview', 'RecoverCopy') } {
                if (-not $history.SelectedItem) { $details.Text = 'Select a snapshot from the history first.'; return }
                $arguments.VaultDirectory = $vault
                $arguments.Game = $history.SelectedItem.Game
                $arguments.VerifySnapshot = $history.SelectedItem.Snapshot
                if ($kind -eq 'RestorePreview') { $arguments.Ludusavi = $exe }
                if ($kind -eq 'RecoverCopy') {
                    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
                    $dialog.Description = 'Choose a folder for a separate copy of the selected snapshot. Nexus creates a new subfolder for the recovered files.'
                    try {
                        if ($dialog.ShowDialog($form) -ne 'OK') { return }
                        $arguments.RecoveryDirectory = $dialog.SelectedPath
                    } finally { $dialog.Dispose() }
                }
            }
        }
        $worker = [System.Management.Automation.PowerShell]::Create()
        try {
            [void]$worker.AddCommand($paths[$kind]).AddParameters($arguments)
            $state.Job = $worker.BeginInvoke()
            $state.Worker = $worker
            $state.Operation = $kind
            $details.Text = 'Working...'
            $actions.Enabled = $false
            foreach ($control in ($fields + $browsers)) { $control.Enabled = $false }
            $timer.Start()
        } catch {
            $worker.Dispose()
            $details.Text = $_.Exception.GetBaseException().Message
        }
    }.GetNewClosure())
    $actions.Controls.Add($button)
}
$queue = New-Object System.Windows.Forms.Button
$queue.Text = 'Batch backup...'
$queue.AutoSize = $true
$queue.add_Click({
    if ($state.Worker) { return }
    & (Join-Path $PSScriptRoot 'Show-NexusSaveQueue.ps1') -Owner $form `
        -Game $fields[0].Text -Ludusavi $fields[1].Text -VaultDirectory $fields[2].Text
}.GetNewClosure())
$actions.Controls.Add($queue)

$timer.add_Tick({
    if (-not $state.Job.IsCompleted) { return }
    $timer.Stop()
    try {
        $result = @($state.Worker.EndInvoke($state.Job))
        if ($state.Worker.HadErrors) { throw $state.Worker.Streams.Error[0].Exception }
        switch ($state.Operation) {
            'Preview' { $details.Text = "Game: $($result.Game)`r`nFiles: $($result.Files)   Registry keys: $($result.RegistryKeys)`r`nLudusavi result: $($result.Decision) / $($result.Change)" }
            'Backup' { $details.Text = "Verified backup created`r`n$($result.Directory)`r`nFiles: $($result.Files)   Registry keys: $($result.RegistryKeys)`r`nSelect Load history to see the new snapshot." }
            'History' {
                $history.BeginUpdate()
                try {
                    foreach ($entry in $result) {
                        [void]$history.Items.Add([pscustomobject]@{
                            Snapshot = $entry.Snapshot
                            Game = $entry.Game
                            Label = $entry.Game + '   ' + $entry.CreatedUtc.ToLocalTime().ToString('G') + '   ' + $entry.Snapshot
                        })
                    }
                } finally { $history.EndUpdate() }
                $details.Text = if ($result.Count) { "$($result.Count) matching snapshots. Select one to verify its files." } else { 'No matching snapshots in this vault.' }
            }
            'Verify' { $details.Text = "Both snapshot files verified with SHA-256`r`n$($result.Snapshot)`r`n" + ($result.Checksums -join "`r`n") }
            'RecoverCopy' {
                $registry = if ($result.RegistrySkipped) { "`r`nRegistry entries remain in the snapshot." } else { '' }
                $details.Text = "Recovered $($result.Files) files with SHA-256 readback`r`n$($result.Directory)`r`nNEXUS-RECOVERY.json lists each recovered file and its checksum.$registry"
            }
            'RestorePreview' { $details.Text = "Restore preview for $($result.Game)`r`nFiles: $($result.Files)   Registry keys: $($result.RegistryKeys)`r`nDifferent or missing files: $($result.DifferentOrRemovedFiles)`r`nDifferent or missing registry keys: $($result.DifferentOrRemovedRegistryKeys)`r`nLudusavi result: $($result.Decision) / $($result.Change)" }
        }
    } catch {
        $details.Text = $_.Exception.GetBaseException().Message
    } finally {
        $state.Worker.Dispose()
        $state.Worker = $null
        $state.Job = $null
        $actions.Enabled = $true
        foreach ($control in ($fields + $browsers)) { $control.Enabled = $true }
        if ($state.CloseWhenReady) { $form.Close() }
    }
}.GetNewClosure())
foreach ($field in @($fields[0], $fields[2])) {
    $field.add_TextChanged({ $history.Items.Clear() }.GetNewClosure())
}
$form.add_FormClosing({
    param($sender, $event)
    if ($state.Worker) {
        $event.Cancel = $true
        if (-not $state.CloseWhenReady) {
            $state.CloseWhenReady = $true
            $details.Text = 'Stopping the current operation...'
            if (-not $state.Job.IsCompleted) {
                try { [void]$state.Worker.BeginStop($null, $null) } catch { }
            }
        }
    }
}.GetNewClosure())
$form.Controls.Add($layout)
if (-not [System.Windows.Forms.SystemInformation]::HighContrast) {
    $form.BackColor = [System.Drawing.Color]::FromArgb(17, 24, 36)
    $form.ForeColor = [System.Drawing.Color]::WhiteSmoke
    foreach ($control in ($fields + @($history, $details))) {
        $control.BackColor = [System.Drawing.Color]::FromArgb(29, 40, 57)
        $control.ForeColor = [System.Drawing.Color]::WhiteSmoke
    }
    foreach ($button in ($browsers + @($actions.Controls))) {
        $button.FlatStyle = 'Flat'
        $button.BackColor = [System.Drawing.Color]::FromArgb(50, 72, 103)
        $button.ForeColor = [System.Drawing.Color]::WhiteSmoke
    }
}
try {
    if ($Owner) { [void]$form.ShowDialog($Owner) } else { [void]$form.ShowDialog() }
} finally {
    $timer.Dispose()
    $form.Dispose()
}
