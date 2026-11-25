# List game snapshots or verify the exact selected copy from a local Nexus vault.
param(
    [Parameter(Mandatory = $true)][string]$VaultDirectory,
    [Parameter(Mandatory = $true)][string]$Game,
    [string]$VerifySnapshot,
    [string]$Ludusavi,
    [string]$ConfigDirectory,
    [string]$RecoveryDirectory
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

function New-NexusRecoveryCopy($SnapshotPath, $ArchivePath, $ExpectedHash, $Destination) {
    Add-Type -AssemblyName System.IO.Compression
    $target = Get-OrdinaryItem $Destination $true
    for ($parent = $target.Parent; $parent; $parent = $parent.Parent) {
        [void](Get-OrdinaryItem $parent.FullName $true)
    }
    $separator = [IO.Path]::DirectorySeparatorChar
    $prefix = $target.FullName.TrimEnd($separator) + $separator
    $vaultPrefix = (Split-Path -Parent $SnapshotPath).TrimEnd($separator) + $separator
    if ($prefix.StartsWith($vaultPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Choose a recovery folder outside the Save Vault.'
    }

    $id = [Guid]::NewGuid().ToString('N')
    $stage = Join-Path $target.FullName ('.nexus-recovery-' + $id)
    $final = Join-Path $target.FullName ('Nexus-Recovered-' + $id.Substring(0, 12))
    $archive = $null
    $zip = $null
    $published = $false
    try {
        [void](Get-OrdinaryItem $ArchivePath $false)
        $archive = [IO.File]::Open($ArchivePath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ((Get-FileHash -InputStream $archive -Algorithm SHA256).Hash -ine $ExpectedHash) {
            throw 'A snapshot file has changed since the backup was created.'
        }
        $archive.Position = 0
        $zip = New-Object IO.Compression.ZipArchive($archive, [IO.Compression.ZipArchiveMode]::Read, $true)
        $files = New-Object 'System.Collections.Generic.List[psobject]'
        $names = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
        $bytes = [long]0
        $skippedRegistry = $false
        if ($zip.Entries.Count -gt 15000) { throw 'This archive exceeds the 15,000-entry recovery limit.' }
        foreach ($entry in $zip.Entries) {
            $path = $entry.FullName
            if ($path -cin @('registry.reg', 'registry.yaml')) { $skippedRegistry = $true; continue }
            $parts = $path.TrimEnd('/').Split('/')
            if ($parts[0] -cnotmatch '^drive-.+$' -or $path.Length -gt 1920 -or
                    @($parts | Where-Object {
                        $_.Length -lt 1 -or $_.Length -gt 255 -or $_ -match '[\:*?"<>|\p{Cc}]|[. ]$' -or
                        $_ -match '^(CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|COM[0-9\u00B9\u00B2\u00B3]|LPT[0-9\u00B9\u00B2\u00B3])(\.|$)'
                    }).Count -ne 0) {
                throw 'The snapshot contains an unsafe or unsupported recovery path.'
            }
            if ($path.EndsWith('/', [StringComparison]::Ordinal)) { continue }
            if ($parts.Length -lt 2 -or -not $names.Add($path)) { throw 'The snapshot contains conflicting recovery paths.' }
            $bytes += $entry.Length
            if ($entry.Length -lt 0 -or $bytes -gt 8GB) { throw 'This archive exceeds the 8 GiB recovery limit.' }
            $files.Add([pscustomobject]@{ Entry = $entry; Relative = $parts -join [IO.Path]::DirectorySeparatorChar })
        }
        if ($files.Count -eq 0) { throw 'This snapshot contains no regular save files to copy.' }

        [void][IO.Directory]::CreateDirectory($stage)
        $manifest = New-Object 'System.Collections.Generic.List[psobject]'
        $buffer = New-Object byte[] (128KB)
        foreach ($file in $files) {
            $output = Join-Path $stage $file.Relative
            [void][IO.Directory]::CreateDirectory((Split-Path -Parent $output))
            $inputStream = $file.Entry.Open()
            $outputStream = $null
            $sha256 = [Security.Cryptography.SHA256]::Create()
            try {
                $outputStream = [IO.File]::Open($output, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
                $copied = [long]0
                while (($count = $inputStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                    $copied += $count
                    if ($copied -gt $file.Entry.Length) { throw 'A recovered file exceeded its recorded size.' }
                    $outputStream.Write($buffer, 0, $count)
                    [void]$sha256.TransformBlock($buffer, 0, $count, $buffer, 0)
                }
                if ($copied -ne $file.Entry.Length) { throw 'A recovered file did not match its recorded size.' }
                [void]$sha256.TransformFinalBlock([byte[]]@(), 0, 0)
                $expected = ([BitConverter]::ToString($sha256.Hash)).Replace('-', '')
            } finally {
                if ($outputStream) { $outputStream.Dispose() }
                $inputStream.Dispose()
                $sha256.Dispose()
            }
            $actual = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
            if ($actual -ine $expected) { throw 'A recovered file failed SHA-256 readback.' }
            $manifest.Add([pscustomobject]@{ Path = $file.Entry.FullName; Bytes = $copied; SHA256 = $actual.ToLowerInvariant() })
        }
        [pscustomobject]@{ Format = 1; Snapshot = (Split-Path -Leaf $SnapshotPath); Game = $Game;
            ArchiveSHA256 = $ExpectedHash; RegistrySkipped = $skippedRegistry; Files = $manifest.ToArray() } |
            ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage 'NEXUS-RECOVERY.json') -Encoding UTF8
        [IO.Directory]::Move($stage, $final)
        $published = $true
        return [pscustomobject]@{ Directory = $final; Files = $files.Count; Bytes = $bytes; RegistrySkipped = $skippedRegistry }
    } finally {
        if ($zip) { $zip.Dispose() }
        if ($archive) { $archive.Dispose() }
        if (-not $published -and [IO.Directory]::Exists($stage)) { [IO.Directory]::Delete($stage, $true) }
    }
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
    $archivePath = $null
    $archiveDigest = $null
    foreach ($digest in $digests) {
        $parts = $digest.Name.Split('/')
        if ($parts.Length -ne 2 -or $digest.Value -cnotmatch '^[a-f0-9]{64}$' -or
                @($parts | Where-Object { $_ -eq '' -or $_ -match '[\\:*?"<>|\p{Cc}]|[. ]$' }).Count -ne 0) {
            throw 'The save receipt contains an invalid relative path or digest.'
        }
        if ($gameFolder -and $parts[0] -cne $gameFolder) { throw 'The receipt spans more than one backup folder.' }
        $gameFolder = $parts[0]
        $mappingFound = $mappingFound -or $parts[1] -ceq 'mapping.yaml'
        $isArchive = $parts[1].EndsWith('.zip', [StringComparison]::Ordinal)
        $archiveFound = $archiveFound -or $isArchive
        if ($Verify) {
            $folder = Get-OrdinaryItem (Join-Path $Directory.FullName $gameFolder) $true
            $file = Get-OrdinaryItem (Join-Path $folder.FullName $parts[1]) $false
            if ($isArchive) {
                $archivePath = $file.FullName
                $archiveDigest = $digest.Value
                if ($RecoveryDirectory) { continue }
            }
            if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ine $digest.Value) {
                throw 'A snapshot file has changed since the backup was created.'
            }
        }
    }
    if (-not $mappingFound -or -not $archiveFound) { throw 'A mapping and a ZIP archive are required.' }
    if ($Verify -and $RecoveryDirectory) {
        return New-NexusRecoveryCopy $Directory.FullName $archivePath $archiveDigest $RecoveryDirectory
    }
    return [pscustomobject]@{
        Snapshot = $Directory.Name
        Game = $Game
        CreatedUtc = $timestamp
        Verified = $Verify
        Checksums = @($digests | ForEach-Object { $_.Name.Split('/')[1] + '  ' + $_.Value })
    }
}

if ($RecoveryDirectory -and (-not $VerifySnapshot -or $Ludusavi)) {
    throw 'Select one verified snapshot to recover to a separate folder.'
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
