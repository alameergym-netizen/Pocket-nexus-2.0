$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}

$candidate = Join-Path (Split-Path $PSScriptRoot) 'Save-NexusGameBackup.ps1'
$history = Join-Path (Split-Path $PSScriptRoot) 'Get-NexusSaveVaultHistory.ps1'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('nexus-real-save-test-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
try {
    $download = Join-Path $fixture 'ludusavi.zip'
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -OutFile $download -Uri 'https://github.com/mtkennerly/ludusavi/releases/download/v0.31.0/ludusavi-v0.31.0-win64.zip'
    Check ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -eq 'f47a8ad8c708f01d2eb124704973beffab205e292f5287a10fc4a101f8d68706') 'pinned Ludusavi executable archive digest'
    $binary = Join-Path $fixture 'Ludusavi app'
    [IO.Compression.ZipFile]::ExtractToDirectory($download, $binary)
    $executable = @(Get-ChildItem -LiteralPath $binary -Recurse -Filter 'ludusavi.exe')
    Check ($executable.Count -eq 1) 'one Ludusavi executable in the official archive'
    $vault = Join-Path $fixture 'Save vault'
    $config = Join-Path $fixture 'Local config'
    [void][IO.Directory]::CreateDirectory($vault)
    [void][IO.Directory]::CreateDirectory($config)
    $game = 'Nexus Fixture'
    $save = Join-Path $fixture 'private sample save.dat'
    $data = [byte[]]@(0, 73, 1, 128, 255)
    [IO.File]::WriteAllBytes($save, $data)
    $utf8 = New-Object Text.UTF8Encoding($false)
    $savePath = $save.Replace('\', '/')
    [IO.File]::WriteAllText((Join-Path $config 'config.yaml'), "customGames:`n  - name: Nexus Fixture`n    files:`n      - $savePath`n", $utf8)
    [IO.File]::WriteAllText((Join-Path $config 'manifest.yaml'), '{}', $utf8)
    $params = @{ Ludusavi = $executable[0].FullName; VaultDirectory = $vault; ConfigDirectory = $config }
    $first = & $candidate @params -Game $game
    Check ($first.Result -eq 'CREATED_LOCAL_SAVE_BACKUP' -and $first.Files -eq 1 -and $first.RegistryKeys -eq 0 -and
        ($first | ConvertTo-Json) -notmatch 'private sample save') 'real binary creates a snapshot and verifies its restore preview without reporting live paths'
    $archive = @(Get-ChildItem -LiteralPath $first.Directory -Recurse -Filter '*.zip')
    Check ($archive.Count -eq 1) 'a single game ZIP was published'
    $snapshot = [IO.Compression.ZipFile]::OpenRead($archive[0].FullName)
    try {
        $entry = @($snapshot.Entries | Where-Object { $_.Name -eq 'private sample save.dat' })
        Check ($entry.Count -eq 1 -and $entry[0].Length -eq $data.Length) 'the actual Ludusavi ZIP contains the expected payload'
        $stream = $entry[0].Open()
        $read = New-Object IO.MemoryStream
        try {
            $stream.CopyTo($read)
            Check ([Convert]::ToBase64String($read.ToArray()) -ceq [Convert]::ToBase64String($data)) 'saved bytes match the live fixture'
        } finally { $stream.Dispose(); $read.Dispose() }
    } finally { $snapshot.Dispose() }
    $receipt = Get-Content -LiteralPath (Join-Path $first.Directory 'NEXUS-SAVE-SHA256.json') -Raw | ConvertFrom-Json
    $hashes = @($receipt.files.PSObject.Properties)
    Check ($receipt.game -ceq $game -and $hashes.Count -eq 2) 'the SHA-256 receipt covers both Ludusavi output files'
    foreach ($item in $hashes) {
        Check ((Get-FileHash -LiteralPath (Join-Path $first.Directory $item.Name) -Algorithm SHA256).Hash -eq
            $item.Value) 'the recorded SHA-256 matches the final snapshot'
    }

    $changed = [byte[]]@(5, 6, 7)
    [IO.File]::WriteAllBytes($save, $changed)
    $second = & $candidate @params -Game $game
    Check ($first.Directory -ne $second.Directory -and [IO.Directory]::Exists($first.Directory) -and
        ([IO.File]::ReadAllBytes($save) -join ',') -eq ($changed -join ',')) 'a second backup preserves the first snapshot and current game data'
    $timeline = @(& $history -VaultDirectory $vault -Game $game)
    Check ($timeline.Count -eq 2 -and -not $timeline[0].Verified -and -not $timeline[1].Verified) 'the timeline finds both snapshots with inspection still available'
    $snapshotId = Split-Path -Leaf $first.Directory
    $verified = & $history -VaultDirectory $vault -Game $game -VerifySnapshot $snapshotId
    Check ($verified.Verified -and $verified.Checksums.Count -eq 2) 'an explicit selection verifies both files against the receipt'
    $preview = & $history -VaultDirectory $vault -Game $game -VerifySnapshot $snapshotId -Ludusavi $executable[0].FullName -ConfigDirectory $config
    Check ($preview.Operation -ceq 'Restore preview' -and $preview.Files -eq 1 -and
        ([IO.File]::ReadAllBytes($save) -join ',') -eq ($changed -join ',')) 'the verified historical snapshot has a real read-only restore preview'
    [IO.File]::AppendAllText($archive[0].FullName, 'tamper')
    try {
        & $history -VaultDirectory $vault -Game $game -VerifySnapshot $snapshotId | Out-Null
        throw 'FAILED: modified snapshot passed verification'
    } catch { if ($_.Exception.Message -notlike '*changed since the backup*') { throw } }
    try {
        & $candidate @params -Game 'Missing Nexus Game' | Out-Null
        throw 'FAILED: unknown game did not fail'
    } catch { if ($_.Exception.Message -notlike '*could not find*') { throw } }
    Check (@(Get-ChildItem -LiteralPath $vault -Force).Count -eq 2) 'failure leaves no unfinished or additional backup'
} finally { [IO.Directory]::Delete($fixture, $true) }
