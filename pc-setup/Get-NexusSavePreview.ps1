# Runs the supported Ludusavi preview command for one expressly selected game.
param(
    [Parameter(Mandatory = $true)][string]$Ludusavi,
    [Parameter(Mandatory = $true)][ValidateSet('Backup', 'Restore')][string]$Action,
    [Parameter(Mandatory = $true)][string]$Game
)
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'Invoke-NexusLudusavi.ps1'
$selected = & $runner -Ludusavi $Ludusavi -Action $Action -Game $Game
$files = @($selected.files.PSObject.Properties | Where-Object { -not $_.Value.ignored })
$registry = @($selected.registry.PSObject.Properties | Where-Object { -not $_.Value.ignored })
$changedFiles = @($files | Where-Object { $_.Value.change -eq 'Different' -or $_.Value.change -eq 'Removed' })
$changedRegistry = @($registry | Where-Object { $_.Value.change -eq 'Different' -or $_.Value.change -eq 'Removed' })
[pscustomobject]@{
    Operation = "$Action preview"
    Game = $Game
    Decision = $selected.decision
    Change = $selected.change
    Files = $files.Count
    RegistryKeys = $registry.Count
    DifferentOrRemovedFiles = $changedFiles.Count
    DifferentOrRemovedRegistryKeys = $changedRegistry.Count
}
