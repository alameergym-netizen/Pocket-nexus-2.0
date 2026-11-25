# Per-user installation of a verified development package. Run from a trusted checkout.
[CmdletBinding(DefaultParameterSetName = 'Install')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Install')][string]$Archive,
    [Parameter(Mandatory = $true, ParameterSetName = 'Remove')][switch]$Remove,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedSha256,
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'PocketNexus\NexusHost-dev'),
    [string]$ProgramsRoot = [Environment]::GetFolderPath('Programs')
)

$ErrorActionPreference = 'Stop'
$digest = $ExpectedSha256.ToLowerInvariant()
$version = 'NexusHost-dev-' + $digest
$markerName = '.nexus-install.json'
$manifestName = 'SHA256-MANIFEST.json'
$root = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($InstallRoot)
$programs = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ProgramsRoot)
$destination = Join-Path $root $version
$shortcut = Join-Path $programs ('Pocket Nexus Host (Dev ' + $digest.Substring(0, 16) + ').lnk')
$hostScript = Join-Path $destination 'PocketNexus-PC.ps1'
$launcher = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$launchArgs = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $hostScript + '" -Background'

function Assert-PlainItem($Item) {
    if ($Item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'A managed installation path is redirected.'
    }
}

function Assert-PlainPath([string]$Path) {
    $part = $Path
    while ($part) {
        $existing = Get-Item -LiteralPath $part -Force -ErrorAction SilentlyContinue
        if ($existing) { Assert-PlainItem $existing }
        $part = [IO.Path]::GetDirectoryName($part)
    }
}

function Test-ManagedShortcut($Shell) {
    $existing = Get-Item -LiteralPath $shortcut -Force -ErrorAction SilentlyContinue
    if (-not $existing) { return $false }
    Assert-PlainItem $existing
    if ($existing.PSIsContainer) { throw 'The development shortcut name is already in use.' }
    $link = $Shell.CreateShortcut($shortcut)
    if ($link.TargetPath -ne $launcher -or $link.Arguments -cne $launchArgs -or
        $link.WorkingDirectory -ne $destination) {
        throw 'The development shortcut name is already in use.'
    }
    return $true
}

function Get-ManagedTree([string]$Path) {
    $manifestPath = Join-Path $Path $manifestName
    $markerPath = Join-Path $Path $markerName
    foreach ($itemPath in @($Path, $manifestPath, $markerPath)) {
        Assert-PlainItem (Get-Item -LiteralPath $itemPath -Force)
    }
    $stamp = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    $manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
    if ($stamp.archive -cne $digest -or $stamp.manifest -ne $manifestHash) {
        throw 'The installed development receipt or manifest has changed.'
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $hashes = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    $dirs = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $hashes.Add($manifestName, $manifestHash)
    $hashes.Add($markerName, (Get-FileHash -LiteralPath $markerPath -Algorithm SHA256).Hash)
    foreach ($file in $manifest.files.PSObject.Properties) {
        $hashes.Add($file.Name, $file.Value)
        $parent = $file.Name
        while (($slash = $parent.LastIndexOf('/')) -ge 0) {
            $parent = $parent.Substring(0, $slash)
            [void]$dirs.Add($parent)
        }
    }
    $filesFound = [Collections.Generic.List[string]]::new()
    $dirsFound = [Collections.Generic.List[string]]::new()
    function Check-Directory([string]$Folder, [string]$Prefix) {
        foreach ($item in Get-ChildItem -LiteralPath $Folder -Force) {
            Assert-PlainItem $item
            $name = $Prefix + $item.Name
            if ($item.PSIsContainer -and $dirs.Contains($name)) {
                Check-Directory $item.FullName ($name + '/')
                $dirsFound.Add($name)
            } elseif (-not $item.PSIsContainer -and $hashes.ContainsKey($name) -and
                (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash -eq $hashes[$name]) {
                $filesFound.Add($name)
            } else {
                throw 'The installed development folder contains a modified or unrecognized item.'
            }
        }
    }
    Check-Directory $Path ''
    if ($filesFound.Count -ne $hashes.Count -or $dirsFound.Count -ne $dirs.Count) {
        throw 'The installed development folder is incomplete.'
    }
    return [pscustomobject]@{ Files = $filesFound; Directories = $dirsFound }
}

Assert-PlainPath $destination
Assert-PlainPath $programs
$shell = New-Object -ComObject WScript.Shell
$hasShortcut = Test-ManagedShortcut $shell

if ($Remove) {
    $tree = Get-ManagedTree $destination
    $live = Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe' OR Name = 'pwsh.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($hostScript, [StringComparison]::OrdinalIgnoreCase) -ge 0 }
    if ($live) { throw 'Quit this development version from the tray before removing it.' }
    $startup = Join-Path ([Environment]::GetFolderPath('Startup')) 'Pocket Nexus PC.lnk'
    $startupScript = Join-Path $env:LOCALAPPDATA 'ArtemisPocket\PocketNexus-PC.vbs'
    if ([IO.File]::Exists($startup) -and [IO.File]::Exists($startupScript) -and
        [IO.File]::ReadAllText($startupScript).IndexOf($hostScript, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw 'Disable Start with Windows in this version before removing it.'
    }
    $retired = Join-Path $root ('.nexus-retired-' + [Guid]::NewGuid().ToString('N'))
    [IO.Directory]::Move($destination, $retired)
    try {
        if ($hasShortcut) { [IO.File]::Delete($shortcut) }
    } catch {
        [IO.Directory]::Move($retired, $destination)
        throw
    }
    try {
        foreach ($file in $tree.Files) { [IO.File]::Delete((Join-Path $retired $file)) }
        foreach ($dir in $tree.Directories) { [IO.Directory]::Delete((Join-Path $retired $dir)) }
        [IO.Directory]::Delete($retired)
    } catch {
        throw "The Start menu registration was removed. Remaining files are at $retired."
    }
    return [pscustomobject]@{ Result = 'REMOVED_DEVELOPMENT_VERSION'; Sha256 = $digest }
}

$stageTool = Join-Path $PSScriptRoot 'Stage-NexusHostDev.ps1'
if ([IO.Directory]::Exists($destination)) {
    & $stageTool -Archive $Archive -ExpectedSha256 $digest | Out-Null
    Get-ManagedTree $destination | Out-Null
} else {
    $work = Join-Path $root ('.nexus-install-' + [Guid]::NewGuid().ToString('N'))
    try {
        $staged = & $stageTool -Archive $Archive -ExpectedSha256 $digest -StageRoot $work
        $stagedManifest = Join-Path $staged.StagedPath $manifestName
        $receipt = @{ archive = $digest; manifest = (Get-FileHash -LiteralPath $stagedManifest -Algorithm SHA256).Hash }
        $receipt | ConvertTo-Json -Compress | Set-Content -LiteralPath (Join-Path $staged.StagedPath $markerName) -Encoding Ascii
        [IO.Directory]::Move($staged.StagedPath, $destination)
    } finally {
        if ([IO.Directory]::Exists($work)) { [IO.Directory]::Delete($work, $true) }
    }
}
if (-not $hasShortcut) {
    [void][IO.Directory]::CreateDirectory($programs)
    $pendingLink = Join-Path $programs ('.nexus-link-' + [Guid]::NewGuid().ToString('N') + '.lnk')
    try {
        $link = $shell.CreateShortcut($pendingLink)
        $link.TargetPath = $launcher
        $link.Arguments = $launchArgs
        $link.WorkingDirectory = $destination
        $link.Description = 'Start Pocket Nexus Host development version'
        $link.Save()
        [IO.File]::Move($pendingLink, $shortcut)
    } finally {
        if ([IO.File]::Exists($pendingLink)) { [IO.File]::Delete($pendingLink) }
    }
}
return [pscustomobject]@{ Result = 'INSTALLED_DEVELOPMENT_VERSION'; Sha256 = $digest; Directory = $destination; Shortcut = $shortcut }
