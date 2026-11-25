# List game snapshots or verify the exact selected copy from a local Nexus vault.
param(
    [Parameter(Mandatory = $true)][string]$VaultDirectory,
    [Parameter(Mandatory = $true)][string]$Game,
    [string]$VerifySnapshot,
    [string]$Ludusavi,
    [string]$ConfigDirectory
)
$ErrorActionPreference = 'Stop'
$snapshotPattern = '^NexusSave-([0-9]{8}T[0-9]{6}Z)-[0-9a-f]{12}$'

function Get-OrdinaryItem([string]$Path, [bool]$Directory) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item -isnot [IO.FileSystemInfo] -or ($item -is [IO.DirectoryInfo]) -ne $Directory -or
            ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The selected vault entry is not a regular Windows file system item.'
    }
    return $item
}

function Read-NexusSnapshot($Directory, [bool]$Verify) {
    if ($Directory.Name -cnotmatch $snapshotPattern) { throw 'Choose a Nexus Save Vault snapshot.' }
    $timestamp = [DateTime]::ParseExact($Matches[1], 'yyyyMMddTHHmmssZ', [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal)
    $receipt = Get-OrdinaryItem (Join-Path $Directory.FullName 'NEXUS-SAVE-SHA256.json') $false
    if ($receipt.Length -gt 64KB) { throw 'The save receipt is too large.' }
    $metadata = Get-Content -LiteralPath $receipt.FullName -Raw | ConvertFrom-Json
    if ($metadata.format -ne 1 -or $metadata.game -cne $Game -or $metadata.files -isnot [pscustomobject]) {
        throw 'The selected snapshot does not have a matching Save Vault receipt.'
    }
    $digests = @($metadata.files.PSObject.Properties)
    if ($digests.Count -ne 2) { throw 'The save receipt is incomplete.' }

    $gameFolder = $null
    $mappingFound = $false
    $archiveFound = $false
    foreach ($digest in $digests) {
        $parts = $digest.Name.Split('/')
        if ($parts.Length -ne 2 -or $digest.Value -cnotmatch '^[a-f0-9]{64}$' -or
                @($parts | Where-Object { $_ -eq '' -or $_ -match '[\\:*?"<>|\p{Cc}]|[. ]$' }).Count -ne 0) {
            throw 'The save receipt contains an invalid relative path or digest.'
        }
        if ($gameFolder -and $parts[0] -cne $gameFolder) { throw 'The receipt spans more than one backup folder.' }
        $gameFolder = $parts[0]
        $mappingFound = $mappingFound -or $parts[1] -ceq 'mapping.yaml'
        $archiveFound = $archiveFound -or $parts[1].EndsWith('.zip', [StringComparison]::Ordinal)
        if ($Verify) {
            $folder = Get-OrdinaryItem (Join-Path $Directory.FullName $gameFolder) $true
            $file = Get-OrdinaryItem (Join-Path $folder.FullName $parts[1]) $false
            if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ine $digest.Value) {
                throw 'A snapshot file has changed since the backup was created.'
            }
        }
    }
    if (-not $mappingFound -or -not $archiveFound) { throw 'A mapping and a ZIP archive are required.' }
    return [pscustomobject]@{
        Snapshot = $Directory.Name
        Game = $Game
        CreatedUtc = $timestamp
        Verified = $Verify
        Checksums = @($digests | ForEach-Object { $_.Value })
    }
}

$vault = Get-OrdinaryItem $VaultDirectory $true
for ($parent = $vault.Parent; $parent; $parent = $parent.Parent) {
    [void](Get-OrdinaryItem $parent.FullName $true)
}
if ($VerifySnapshot) {
    if ($VerifySnapshot -cnotmatch $snapshotPattern) { throw 'Choose a Nexus Save Vault snapshot.' }
    $directory = Get-OrdinaryItem (Join-Path $vault.FullName $VerifySnapshot) $true
    $verified = Read-NexusSnapshot $directory $true
    if ($Ludusavi) {
        & (Join-Path $PSScriptRoot 'Get-NexusSavePreview.ps1') -Action Restore -Game $Game -Ludusavi $Ludusavi `
            -ConfigDirectory $ConfigDirectory -BackupDirectory $directory.FullName
    } else { $verified }
} else {
    Get-ChildItem -LiteralPath $vault.FullName -Directory -Filter 'NexusSave-*' |
        Where-Object { $_.Name -cmatch $snapshotPattern -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) } |
        Sort-Object Name -Descending | ForEach-Object {
            try { Read-NexusSnapshot $_ $false } catch { }
        }
}
