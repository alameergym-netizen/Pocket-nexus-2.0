function Get-NexusSnapshotPath {
    Join-Path $env:LOCALAPPDATA 'ArtemisPocket\playnite-catalog.tsv'
}

function Write-NexusSnapshot($Games, [string]$Path) {
    $rows = New-Object 'System.Collections.Generic.List[string]'
    $seen = New-Object 'System.Collections.Generic.HashSet[Guid]'
    foreach ($game in $Games) {
        if ($game.Hidden -or $game.Id -eq [Guid]::Empty) { continue }
        $name = ([string]$game.Name -replace '[\p{Cc}]', ' ').Trim()
        if (-not $name) { continue }
        if ($name.Length -gt 200) {
            $length = if ([char]::IsHighSurrogate($name[199])) { 199 } else { 200 }
            $name = $name.Substring(0, $length).TrimEnd()
        }
        if (-not $seen.Add($game.Id)) { continue }
        if ($rows.Count -ge 5000) { throw 'The export exceeds 5,000 visible games. The previous export was kept.' }
        $installed = if ($game.IsInstalled) { '1' } else { '0' }
        $rows.Add(($game.Id.ToString('D') + "`t" + $installed + "`t" + $name))
    }

    $directory = [IO.Path]::GetDirectoryName($Path)
    [void][IO.Directory]::CreateDirectory($directory)
    $temporary = Join-Path $directory ([Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $encoding = New-Object Text.UTF8Encoding($false, $true)
        $lines = @('NEXUS-PLAYNITE 1') + $rows.ToArray() + @('END ' + $rows.Count)
        [IO.File]::WriteAllLines($temporary, [string[]]$lines, $encoding)
        if ([IO.File]::Exists($Path)) {
            [IO.File]::Replace($temporary, $Path, [System.Management.Automation.Language.NullString]::Value)
        } else {
            [IO.File]::Move($temporary, $Path)
        }
    } finally {
        [IO.File]::Delete($temporary)
    }
    return $rows.Count
}

function Export-NexusPlayniteCatalog {
    $answer = $PlayniteApi.Dialogs.ShowMessage(
        'Share visible game names and installation status with devices paired for the Nexus Host game list?',
        'Pocket Nexus catalog', [System.Windows.MessageBoxButton]::YesNo)
    if ($answer -ne [System.Windows.MessageBoxResult]::Yes) { return }
    try {
        $count = Write-NexusSnapshot $PlayniteApi.Database.Games (Get-NexusSnapshotPath)
        [void]$PlayniteApi.Dialogs.ShowMessage("Exported $count games. Refresh the secure game list on your handheld.", 'Pocket Nexus catalog')
    } catch {
        [void]$PlayniteApi.Dialogs.ShowMessage("Could not export the catalog. " + $_.Exception.Message, 'Pocket Nexus catalog')
    }
}

function Remove-NexusPlayniteCatalog {
    try {
        $path = Get-NexusSnapshotPath
        if ([IO.File]::Exists($path)) { [IO.File]::Delete($path) }
        [void]$PlayniteApi.Dialogs.ShowMessage('The Pocket Nexus export was removed.', 'Pocket Nexus catalog')
    } catch {
        [void]$PlayniteApi.Dialogs.ShowMessage('Could not remove the Pocket Nexus export.', 'Pocket Nexus catalog')
    }
}

function GetMainMenuItems {
    param($menuArgs)
    $export = New-Object Playnite.SDK.Plugins.ScriptMainMenuItem
    $export.Description = 'Export visible games to Nexus Host'
    $export.MenuSection = '@Pocket Nexus'
    $export.FunctionName = 'Export-NexusPlayniteCatalog'
    $remove = New-Object Playnite.SDK.Plugins.ScriptMainMenuItem
    $remove.Description = 'Remove Nexus Host export'
    $remove.MenuSection = '@Pocket Nexus'
    $remove.FunctionName = 'Remove-NexusPlayniteCatalog'
    return @($export, $remove)
}

Export-ModuleMember -Function GetMainMenuItems, Export-NexusPlayniteCatalog, Remove-NexusPlayniteCatalog
