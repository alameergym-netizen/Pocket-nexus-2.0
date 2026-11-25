param([Parameter(Mandatory = $true)][string]$Screenshot)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing, System.IO.Compression.FileSystem
$base = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('nexus-save-queue-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 80
$clock = [Diagnostics.Stopwatch]::StartNew()
$state = @{ Phase = 0; Failure = $null; Done = $false }
try {
    $download = Join-Path $fixture 'ludusavi.zip'
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -OutFile $download -Uri 'https://github.com/mtkennerly/ludusavi/releases/download/v0.31.0/ludusavi-v0.31.0-win64.zip'
    if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne 'f47a8ad8c708f01d2eb124704973beffab205e292f5287a10fc4a101f8d68706') { throw 'The Ludusavi archive digest changed.' }
    $binary = Join-Path $fixture 'Ludusavi app'
    [IO.Compression.ZipFile]::ExtractToDirectory($download, $binary)
    $exe = @(Get-ChildItem -LiteralPath $binary -Recurse -Filter 'ludusavi.exe')
    if ($exe.Count -ne 1) { throw 'Expected one official Ludusavi executable.' }
    $config = Join-Path $fixture 'Config'
    $vault = Join-Path $fixture 'Vault'
    [void][IO.Directory]::CreateDirectory($config)
    [void][IO.Directory]::CreateDirectory($vault)
    $live = @((Join-Path $fixture 'first save.dat'), (Join-Path $fixture 'second save.dat'))
    [IO.File]::WriteAllBytes($live[0], [byte[]]@(7, 10, 100, 255))
    [IO.File]::WriteAllBytes($live[1], [byte[]]@(9, 22, 31))
    $before = @($live | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash })
    $yaml = "customGames:`n"
    for ($i = 0; $i -lt 2; $i++) { $yaml += "  - name: Queue Game $($i+1)`n    files:`n      - $($live[$i].Replace('\', '/'))`n" }
    $utf8 = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $config 'config.yaml'), $yaml, $utf8)
    [IO.File]::WriteAllText((Join-Path $config 'manifest.yaml'), '{}', $utf8)
    $clock.Restart()
    $timer.add_Tick({
        $form = [System.Windows.Forms.Application]::OpenForms | Where-Object Text -eq 'Backup queue | Nexus Host' | Select-Object -First 1
        if (-not $form) { return }
        try {
            if ($clock.ElapsedMilliseconds -gt 90000) { throw "Save queue timed out at step $($state.Phase)." }
            $layout = $form.Controls[0]
            $names = $layout.GetControlFromPosition(0, 3)
            $buttons = $layout.GetControlFromPosition(0, 4)
            $list = $layout.GetControlFromPosition(0, 5)
            $status = $layout.GetControlFromPosition(0, 6)
            $start = $buttons.Controls | Where-Object Text -eq 'Start backup queue'
            $stop = $buttons.Controls | Where-Object Text -eq 'Stop after current game'
            switch ($state.Phase) {
                0 {
                    $names.Text = ((1..33 | ForEach-Object { 'Game ' + $_ }) -join "`r`n")
                    $start.PerformClick()
                    if ($status.Text -notmatch 'between 1 and 32' -or @(Get-ChildItem -LiteralPath $vault -Force).Count) { throw 'An oversized queue must be rejected before backup.' }
                    $names.Text = "Queue Game 1`r`nMissing Queue Game`r`nQueue Game 1`r`nQueue Game 2"
                    $start.PerformClick()
                    if ($list.Items.Count -ne 3 -or $names.Enabled) { throw 'The queue did not deduplicate and freeze its selected games.' }
                    $state.Phase = 1
                }
                1 {
                    if (-not $start.Enabled) { return }
                    if ($list.Items[0].SubItems[1].Text -ne 'Verified snapshot created' -or
                        $list.Items[1].SubItems[1].Text -ne 'Failed' -or
                        $list.Items[2].SubItems[1].Text -ne 'Verified snapshot created') { throw 'A failed game must be reported while later games still back up.' }
                    $snapshots = @(Get-ChildItem -LiteralPath $vault -Directory)
                    if ($snapshots.Count -ne 2 -or @($snapshots | Where-Object Name -like '.staging-*').Count) { throw 'The mixed-result queue published unexpected snapshots or staging data.' }
                    foreach ($snapshot in $snapshots) {
                        $receipt = Get-Content -LiteralPath (Join-Path $snapshot.FullName 'NEXUS-SAVE-SHA256.json') -Raw | ConvertFrom-Json
                        foreach ($file in $receipt.files.PSObject.Properties) {
                            if ((Get-FileHash -LiteralPath (Join-Path $snapshot.FullName $file.Name) -Algorithm SHA256).Hash -ne $file.Value) { throw 'The queued snapshot failed receipt readback.' }
                        }
                    }
                    $output = [IO.Path]::GetFullPath($Screenshot)
                    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($output))
                    $image = New-Object System.Drawing.Bitmap($form.Width, $form.Height)
                    try {
                        $form.DrawToBitmap($image, (New-Object System.Drawing.Rectangle(0, 0, $form.Width, $form.Height)))
                        $image.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
                    } finally { $image.Dispose() }
                    $names.Text = "Queue Game 1`r`nQueue Game 2"
                    $start.PerformClick()
                    $state.Phase = 2
                }
                2 {
                    if ($list.Items[0].SubItems[1].Text -ne 'Backing up and verifying') { return }
                    $stop.PerformClick()
                    $state.Phase = 3
                }
                3 {
                    if (-not $start.Enabled) { return }
                    if ($list.Items[0].SubItems[1].Text -ne 'Verified snapshot created' -or
                        $list.Items[1].SubItems[1].Text -ne 'Not started' -or
                        @(Get-ChildItem -LiteralPath $vault -Directory).Count -ne 3) { throw 'Stop must finish the current transaction and leave the next game unstarted.' }
                    for ($i = 0; $i -lt 2; $i++) {
                        if ((Get-FileHash -LiteralPath $live[$i] -Algorithm SHA256).Hash -ne $before[$i]) { throw 'The live fixture save changed.' }
                    }
                    $state.Done = $true
                    $timer.Stop()
                    $form.Close()
                }
            }
        } catch {
            $state.Failure = $_.Exception.Message
            $timer.Stop()
            $form.Close()
        }
    }.GetNewClosure())
    $timer.Start()
    & (Join-Path $base 'Show-NexusSaveQueue.ps1') -Ludusavi $exe[0].FullName -VaultDirectory $vault -ConfigDirectory $config
    if (-not $state.Done) { throw $state.Failure }
    Write-Host 'PASS: the real Ludusavi Windows queue validates, deduplicates, retains verified snapshots, continues after failure and stops after the current game without changing live saves.'
} finally {
    $timer.Dispose()
    [IO.Directory]::Delete($fixture, $true)
}
