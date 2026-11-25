$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$history = Join-Path (Split-Path $PSScriptRoot) 'Get-NexusSaveVaultHistory.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('nexus-recover-test-' + [Guid]::NewGuid().ToString('N'))
$game = 'Recovery Fixture'
$vault = Join-Path $root 'Vault'
$destination = Join-Path $root 'Recovery'
[void][IO.Directory]::CreateDirectory($destination)

function Check($Condition, [string]$Description) {
    if (-not $Condition) { throw "FAILED: $Description" }
    Write-Host "PASS: $Description"
}

function Write-Snapshot([string]$Id, [System.Collections.IDictionary]$Entries) {
    $snapshotRoot = Join-Path $vault $Id
    $gameRoot = Join-Path $snapshotRoot $game
    [void][IO.Directory]::CreateDirectory($gameRoot)
    $archive = Join-Path $gameRoot 'backup.zip'
    $zip = [IO.Compression.ZipFile]::Open($archive, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($path in $Entries.Keys) {
            $stream = $zip.CreateEntry($path).Open()
            try { $stream.Write([byte[]]$Entries[$path], 0, $Entries[$path].Length) } finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    [IO.File]::WriteAllText((Join-Path $gameRoot 'mapping.yaml'), 'fixture')
    $files = [ordered]@{}
    foreach ($name in @('backup.zip', 'mapping.yaml')) {
        $files[$game + '/' + $name] = (Get-FileHash -LiteralPath (Join-Path $gameRoot $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    @{ format = 1; game = $game; files = $files } | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath (Join-Path $snapshotRoot 'NEXUS-SAVE-SHA256.json') -Encoding UTF8
    return $archive
}

function Expect-FailedRecovery([string]$Id, [string]$ErrorPattern, [string]$Directory = $destination) {
    try {
        & $history -VaultDirectory $vault -Game $game -VerifySnapshot $Id -RecoveryDirectory $Directory | Out-Null
        throw 'FAILED: the recovery should have been rejected.'
    } catch {
        if ($_.Exception.Message -notlike $ErrorPattern) { throw }
    }
}

try {
    $id = 'NexusSave-20261125T103000Z-295ecc1e83ab'
    $rootSave = 'drive-C/Player/My Save.sav'
    $unicode = 'drive-C/Player/slots-' + [char]0x00E9 + '.dat'
    $archive = Write-Snapshot $id ([ordered]@{ $rootSave = @(31, 2, 0, 255); $unicode = @(1, 122); 'registry.reg' = @(41) })
    $original = Join-Path $destination 'My Save.sav'
    [IO.File]::WriteAllBytes($original, [byte[]]@(5, 7))
    $recoverParams = @{ VaultDirectory = $vault; Game = $game; VerifySnapshot = $id; RecoveryDirectory = $destination }
    $first = & $history @recoverParams
    $second = & $history @recoverParams
    $saved = Join-Path $first.Directory $rootSave
    Check ($first.Files -eq 2 -and $first.Bytes -eq 6 -and $first.RegistrySkipped) 'two archive files recover and the registry stays in the snapshot'
    Check (([IO.File]::ReadAllBytes($saved) -join ',') -eq '31,2,0,255' -and [IO.File]::Exists((Join-Path $first.Directory $unicode))) 'nested recovered bytes and Unicode filename match'
    Check ($first.Directory -ne $second.Directory -and ([IO.File]::ReadAllBytes($original) -join ',') -eq '5,7') 'repeat recovery keeps both copies and protects an existing save'
    $receipt = Get-Content -LiteralPath (Join-Path $first.Directory 'NEXUS-RECOVERY.json') -Raw | ConvertFrom-Json
    $entry = @($receipt.Files | Where-Object Path -ceq $rootSave)
    Check ($receipt.Snapshot -ceq $id -and $entry.Count -eq 1 -and $entry[0].SHA256 -eq (Get-FileHash -LiteralPath $saved).Hash) 'receipt matches the selected snapshot and recovered bytes'
    Expect-FailedRecovery $id '*outside the Save Vault*' $vault
    [IO.File]::AppendAllText($archive, 'changed fixture')
    Expect-FailedRecovery $id '*changed since the backup*'

    $badId = 'NexusSave-20261125T103100Z-295ecc1e83ab'
    [void](Write-Snapshot $badId ([ordered]@{ $rootSave = @(11); 'drive-C/../outside.sav' = @(12) }))
    Expect-FailedRecovery $badId '*unsafe or unsupported recovery path*'
    $conflictId = 'NexusSave-20261125T103200Z-295ecc1e83ab'
    $conflict = New-Object Collections.Specialized.OrderedDictionary ([StringComparer]::Ordinal)
    $conflict.Add('drive-C/Save.dat', @(1))
    $conflict.Add('drive-C/SAVE.dat', @(2))
    [void](Write-Snapshot $conflictId $conflict)
    Expect-FailedRecovery $conflictId '*conflicting recovery paths*'
    Check (@(Get-ChildItem -LiteralPath $destination -Directory -Force).Count -eq 2 -and
        -not [IO.File]::Exists((Join-Path $destination 'outside.sav'))) 'rejected recoveries publish no partial folder or escaped file'
} finally {
    [IO.Directory]::Delete($root, $true)
}
