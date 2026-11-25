# Create a separate Ludusavi backup for one exact game in a user-selected local vault.
param(
    [Parameter(Mandatory = $true)][string]$Ludusavi,
    [Parameter(Mandatory = $true)][string]$Game,
    [Parameter(Mandatory = $true)][string]$VaultDirectory,
    [string]$ConfigDirectory
)
$ErrorActionPreference = 'Stop'

function Assert-OrdinaryItem($Item, [bool]$Directory) {
    if ($Item -isnot [IO.FileSystemInfo] -or ($Item -is [IO.DirectoryInfo]) -ne $Directory -or
        ($Item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'The selected location or backup entry is not an ordinary file system item.'
    }
}

function Get-Selection($Report, [bool]$MustMatchLive) {
    if ($Report.decision -cne 'Processed') { throw 'Ludusavi did not process the selected game.' }
    $files = @($Report.files.PSObject.Properties | Where-Object { -not $_.Value.ignored })
    $registry = @($Report.registry.PSObject.Properties | Where-Object { -not $_.Value.ignored })
    if ($files.Count + $registry.Count -eq 0) { throw 'Ludusavi found no save data for the selected game.' }
    foreach ($item in ($files + $registry)) {
        if ($item.Value.failed -or $item.Value.error -or ($MustMatchLive -and $item.Value.change -cne 'Same')) {
            throw 'Ludusavi could not verify the save data. Close the game before retrying.'
        }
    }
    return [pscustomobject]@{ Files = $files; Registry = $registry }
}

function Confirm-SameSelection($Written, $Readback) {
    if ($Written.Files.Count -ne $Readback.Files.Count -or $Written.Registry.Count -ne $Readback.Registry.Count) {
        throw 'The backup and restore preview cover different save items.'
    }
    foreach ($original in $Written.Files) {
        $found = $Readback.Files | Where-Object { $_.Name -ceq $original.Name -and $_.Value.bytes -eq $original.Value.bytes }
        if (-not $found) { throw 'A saved file could not be read back at its recorded size.' }
    }
    foreach ($original in $Written.Registry) {
        if ($original.Name -cnotin $Readback.Registry.Name) { throw 'A saved registry key could not be read back.' }
    }
}

$vault = Get-Item -LiteralPath $VaultDirectory -Force
Assert-OrdinaryItem $vault $true
for ($parent = $vault.Parent; $parent; $parent = $parent.Parent) { Assert-OrdinaryItem $parent $true }
$label = 'NexusSave-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$staging = Join-Path $vault.FullName ('.staging-' + $label)
$destination = Join-Path $vault.FullName $label
$runner = Join-Path $PSScriptRoot 'Invoke-NexusLudusavi.ps1'
$arguments = @{ Ludusavi = $Ludusavi; Game = $Game; BackupDirectory = $staging }
if ($ConfigDirectory) { $arguments.ConfigDirectory = $ConfigDirectory }

[void][IO.Directory]::CreateDirectory($staging)
try {
    $written = Get-Selection (& $runner @arguments -Action Backup -WriteBackup) $false
    $readback = Get-Selection (& $runner @arguments -Action Restore) $true
    Confirm-SameSelection $written $readback

    # A single game in a new vault has one folder, one mapping and one ZIP.
    # Hash the physical outputs and retain their digests with the snapshot.
    $children = @(Get-ChildItem -LiteralPath $staging -Force)
    if ($children.Count -ne 1) { throw 'Ludusavi did not produce a single-game backup.' }
    Assert-OrdinaryItem $children[0] $true
    $backupFiles = @(Get-ChildItem -LiteralPath $children[0].FullName -Force)
    if ($backupFiles.Count -ne 2 -or 'mapping.yaml' -cnotin $backupFiles.Name -or
        @($backupFiles | Where-Object { $_.Extension -ceq '.zip' }).Count -ne 1) {
        throw 'The backup does not contain one mapping and one ZIP archive.'
    }
    $hashes = [ordered]@{}
    foreach ($file in $backupFiles) {
        Assert-OrdinaryItem $file $false
        if ($file.Length -eq 0) { throw 'Ludusavi returned an empty backup file.' }
        $hashes[($children[0].Name + '/' + $file.Name)] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    @{
        format = 1
        game = $Game
        files = $hashes
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $staging 'NEXUS-SAVE-SHA256.json') -Encoding Utf8
    [IO.Directory]::Move($staging, $destination)
    return [pscustomobject]@{
        Result = 'CREATED_LOCAL_SAVE_BACKUP'
        Game = $Game
        Files = $written.Files.Count
        RegistryKeys = $written.Registry.Count
        Directory = $destination
    }
} finally {
    if ([IO.Directory]::Exists($staging)) { [IO.Directory]::Delete($staging, $true) }
}
