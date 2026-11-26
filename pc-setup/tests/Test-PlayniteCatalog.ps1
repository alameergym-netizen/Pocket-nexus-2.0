$ErrorActionPreference = 'Stop'
Add-Type -Path (Join-Path (Split-Path -Parent $PSScriptRoot) 'NexusPlayniteCatalog.cs')
$directory = Join-Path ([IO.Path]::GetTempPath()) ('nexus-playnite-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$path = Join-Path $directory 'playnite-catalog.tsv'
$encoding = New-Object Text.UTF8Encoding($false, $true)
$id = '10000000-0000-0000-0000-000000000001'
$other = '10000000-0000-0000-0000-000000000002'
function Write-Export([string[]]$Rows, [string]$Trailer = '') {
    if (-not $Trailer) { $Trailer = 'END ' + $Rows.Count }
    [IO.File]::WriteAllLines($path, (@('NEXUS-PLAYNITE 1') + $Rows + @($Trailer)), $encoding)
}
function Check($Condition, [string]$Name) {
    if (-not $Condition) { throw "FAILED: $Name" }
    Write-Host "PASS: $Name"
}
function Reject([scriptblock]$Run, [string]$Name) {
    $rejected = $false
    try { & $Run | Out-Null } catch { $rejected = $true }
    Check $rejected $Name
}
try {
    Write-Export @("$id`t1`tInstalled game", "$other`t0`tAvailable game")
    $snapshot = [PocketNexus.NexusPlayniteCatalog]::Read($path)
    Check ($snapshot.Entries.Count -eq 2 -and $snapshot.Entries[0].Installed -and -not $snapshot.Entries[1].Installed) 'manual installed states and stable IDs survive parsing'
    $digest = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    Check ($snapshot.Sha256 -ceq $digest) 'snapshot digest describes the complete bytes'
    Check ([PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, $id, 'start') -ceq "playnite://playnite/start/$id") 'installed entry generates only the documented GUID start URI'
    Check ([PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, $other, 'showgame') -ceq "playnite://playnite/showgame/$other") 'uninstalled entry can open its exact details'
    Reject { [PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, $other, 'start') } 'uninstalled entry cannot start'
    Reject { [PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, $id, 'shutdown') } 'other commands cannot be dispatched'
    Reject { [PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, "$id/1", 'start') } 'arguments cannot be injected into an ID'
    Reject { [PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, '10000000-0000-0000-0000-000000000003', 'start') } 'unknown IDs cannot start'
    Write-Export @("$id`t1`tChanged name")
    Reject { [PocketNexus.NexusPlayniteCatalog]::PrepareLaunchUri($path, $digest, $id, 'start') } 'changed valid snapshot must be refreshed before dispatch'
    Write-Export @("$id`t1`tOne", "$id`t0`tDuplicate")
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'duplicate game IDs reject the whole export'
    Write-Export @("$id`t1`tOne") 'END 2'
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'trailer count must match'
    Write-Export @("$id`t1`tOne", 'END 1', 'unexpected trailing row') 'END 1'
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'rows after the trailer are rejected'
    foreach ($row in @("$id`t2`tInvalid state", ("$id`t1`tBad" + [char]1 + 'name'), ("$id`t1`t" + ('x' * 201)), "00000000-0000-0000-0000-000000000000`t1`tEmpty identity")) {
        Write-Export @($row)
        Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'invalid state, name or identity is rejected'
    }
    [IO.File]::WriteAllBytes($path, [byte[]]@(255, 254, 255))
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'invalid UTF-8 is rejected'
    $file = [IO.File]::Create($path)
    try { $file.SetLength(4MB + 1) } finally { $file.Dispose() }
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'oversized export is bounded'
    Write-Export @()
    Check ([PocketNexus.NexusPlayniteCatalog]::Read($path).Entries.Count -eq 0) 'empty export revokes previous entries'
    $rows = for ($i = 1; $i -le 5001; $i++) { [Guid]::NewGuid().ToString('D') + "`t1`tGame $i" }
    Write-Export $rows
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'entry count is bounded'
    [IO.File]::Delete($path)
    Reject { [PocketNexus.NexusPlayniteCatalog]::Read($path) } 'removed export cannot retain old launch authority'
} finally { [IO.Directory]::Delete($directory, $true) }
