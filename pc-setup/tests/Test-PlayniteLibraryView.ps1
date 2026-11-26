param([Parameter(Mandatory = $true)][string]$Screenshot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$directory = Join-Path ([IO.Path]::GetTempPath()) ('nexus-playnite-view-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$path = Join-Path $directory 'playnite-catalog.tsv'
$encoding = New-Object Text.UTF8Encoding($false, $true)
[IO.File]::WriteAllLines($path, @('NEXUS-PLAYNITE 1', "10000000-0000-0000-0000-000000000001`t1`tInstalled fixture", "10000000-0000-0000-0000-000000000002`t0`tAvailable fixture", 'END 2'), $encoding)
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 160
$clock = [Diagnostics.Stopwatch]::StartNew()
$state = @{ Phase = 0; Failure = $null; Done = $false }
$timer.add_Tick({
    $form = [System.Windows.Forms.Application]::OpenForms | Where-Object Text -eq 'Playnite Library | Nexus Host' | Select-Object -First 1
    if (-not $form) { return }
    try {
        if ($clock.ElapsedMilliseconds -gt 25000) { throw "Timed out at Playnite UI step $($state.Phase)." }
        $layout = $form.Controls[0]
        $search = $layout.GetControlFromPosition(0, 1)
        $list = $layout.GetControlFromPosition(0, 2)
        $actions = $layout.GetControlFromPosition(0, 3)
        $status = $layout.GetControlFromPosition(0, 4)
        $refresh = $actions.Controls | Where-Object Text -eq 'Refresh'
        $details = $actions.Controls | Where-Object Text -eq 'Open details'
        $start = $actions.Controls | Where-Object Text -eq 'Start in Playnite'
        if ($state.Phase -eq 0 -and $refresh.Enabled -and $list.Items.Count -eq 2) {
            if ($details.Enabled -or $start.Enabled) { throw 'An unselected row acquired launch authority.' }
            $list.Items[1].Selected = $true
            if (-not $details.Enabled -or $start.Enabled) { throw 'The uninstalled row has incorrect actions.' }
            $search.Text = 'Installed fixture'
            if ($list.Items.Count -ne 1) { throw 'Filtering did not retain exactly the requested game.' }
            $list.Items[0].Selected = $true
            if (-not $start.Enabled) { throw 'The installed row cannot be selected for launch.' }
            $search.Text = ''
            $output = [IO.Path]::GetFullPath($Screenshot)
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($output))
            $bitmap = New-Object System.Drawing.Bitmap($form.Width, $form.Height)
            try {
                $form.DrawToBitmap($bitmap, (New-Object System.Drawing.Rectangle(0, 0, $form.Width, $form.Height)))
                $bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally { $bitmap.Dispose() }
            [IO.File]::WriteAllText($path, 'incomplete export', $encoding)
            $refresh.PerformClick()
            $state.Phase = 1
        } elseif ($state.Phase -eq 1 -and $refresh.Enabled) {
            if ($list.Items.Count -ne 0 -or $details.Enabled -or $start.Enabled -or $status.Text -notmatch 'invalid or incomplete') { throw 'An invalid refresh retained launchable rows.' }
            [IO.File]::Delete($path)
            $refresh.PerformClick()
            $state.Phase = 2
        } elseif ($state.Phase -eq 2 -and $refresh.Enabled) {
            if ($status.Text -notmatch 'No export found' -or $list.Items.Count -ne 0) { throw 'Missing export guidance is absent.' }
            $state.Done = $true
            $timer.Stop()
            ($actions.Controls | Where-Object Text -eq 'Close').PerformClick()
        }
    } catch {
        $state.Failure = $_.Exception.Message
        $timer.Stop()
        $form.Close()
    }
}.GetNewClosure())
$timer.Start()
try {
    & (Join-Path (Split-Path -Parent $PSScriptRoot) 'Show-NexusPlayniteLibrary.ps1') -DataDirectory $directory
    if (-not $state.Done) { throw $state.Failure }
    Write-Host 'PASS: real Windows Playnite library selection, installed gating, filter, invalidation, missing-export recovery and close.'

    # Load the production dashboard function without starting helper/network servers.
    $here = Split-Path -Parent $PSScriptRoot
    $DataDir = $directory
    $script:hostDashboard = $null
    $script:companionOk = $false
    function Get-HostDashboardText { 'Synthetic status for the native dashboard layout check.' }
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $here 'PocketNexus-PC.ps1'), [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw 'Host script has parse errors.' }
    $definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Show-HostDashboard' }, $true)
    Invoke-Expression $definition.Extent.Text
    foreach ($size in @(@(720, 420), @(560, 360))) {
        Show-HostDashboard
        $dashboard = $script:hostDashboard
        try {
            $dashboard.ClientSize = New-Object Drawing.Size($size[0], $size[1])
            [Windows.Forms.Application]::DoEvents()
            $actions = $dashboard.Controls[0].GetControlFromPosition(0, 3)
            if (-not ($actions.Controls | Where-Object Text -eq 'Playnite Library')) { throw 'The dashboard library entry is absent.' }
            foreach ($action in $actions.Controls) {
                if (-not $actions.ClientRectangle.Contains($action.Bounds)) { throw "Dashboard action clipped at $($size[0]): $($action.Text)" }
            }
            $file = Join-Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Screenshot))) "Nexus-Host-$($size[0]).png"
            $bitmap = New-Object Drawing.Bitmap($dashboard.Width, $dashboard.Height)
            try {
                $dashboard.DrawToBitmap($bitmap, (New-Object Drawing.Rectangle(0, 0, $dashboard.Width, $dashboard.Height)))
                $bitmap.Save($file, [Drawing.Imaging.ImageFormat]::Png)
            } finally { $bitmap.Dispose() }
        } finally { $dashboard.Close(); $dashboard.Dispose() }
    }
    Write-Host 'PASS: production dashboard keeps every action visible at wide and compact sizes.'
} finally { $timer.Dispose(); [IO.Directory]::Delete($directory, $true) }
