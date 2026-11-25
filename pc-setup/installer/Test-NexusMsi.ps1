param(
    [Parameter(Mandatory = $true)][string]$Older,
    [Parameter(Mandatory = $true)][string]$Newer,
    [Parameter(Mandatory = $true)][string]$LogDirectory
)
$ErrorActionPreference = 'Stop'

function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}

function Invoke-Installer([string]$Operation, [string]$Package, [string]$Label, [int]$Expected = 0) {
    $packagePath = (Resolve-Path -LiteralPath $Package).ProviderPath
    $log = Join-Path $LogDirectory ($Label + '.log')
    $options = @($Operation, ('"' + $packagePath + '"'), '/qn', '/norestart', '/L*V', ('"' + $log + '"'))
    $process = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32\msiexec.exe') -ArgumentList $options -Wait -PassThru
    Check ($process.ExitCode -eq $Expected) ($Label + ' Windows Installer exit ' + $process.ExitCode)
}

$install = Join-Path $env:LOCALAPPDATA 'PocketNexus\NexusHost-msi-dev'
$preserved = Join-Path $env:LOCALAPPDATA ('ArtemisPocket\nexus-msi-ci-' + [Guid]::NewGuid().ToString('N'))
$linkPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Pocket Nexus Host Development\Pocket Nexus Host Development.lnk'
$registry = 'HKCU:\Software\Pocket Nexus\Host MSI Development'
$Arp = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*'

function Installed-Version {
    return @(Get-ItemProperty -Path $Arp -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -eq 'Pocket Nexus Host Development' }).DisplayVersion
}

if ([IO.Directory]::Exists($install) -or [IO.File]::Exists($linkPath) -or (Test-Path -LiteralPath $registry)) {
    throw 'Run the MSI lifecycle fixture in an isolated Windows user profile.'
}
[void][IO.Directory]::CreateDirectory($LogDirectory)
[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($preserved))
[IO.File]::WriteAllText($preserved, 'keep legacy companion data')
$newerInstalled = $false
$olderInstalled = $false
try {
    Invoke-Installer '/i' $Older 'install'
    $olderInstalled = $true
    Check ([IO.File]::Exists((Join-Path $install 'PocketNexus-PC.ps1')) -and [IO.File]::Exists($linkPath) -and
        (Test-Path -LiteralPath $registry)) 'MSI registers the version and Start menu shortcut for the current user'
    $firstVersion = @(Installed-Version)
    Check ($firstVersion.Count -eq 1) 'one development version is registered'
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($linkPath)
    $expectedTarget = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $expectedScript = Join-Path $install 'PocketNexus-PC.ps1'
    Check ($link.TargetPath -eq $expectedTarget) ("the shortcut uses Windows PowerShell ($($link.TargetPath))")
    Check ($link.WorkingDirectory.TrimEnd([char]92) -eq $install) ("the shortcut works from the installation directory ($($link.WorkingDirectory))")
    Check ($link.Arguments -match '^-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "([^"]+)" -Background$' -and
        $Matches[1] -eq $expectedScript) ("the shortcut launches the installed script on demand ($($link.Arguments))")

    $manifest = Get-Content -LiteralPath (Join-Path $install 'SHA256-MANIFEST.json') -Raw | ConvertFrom-Json
    foreach ($item in $manifest.files.PSObject.Properties) {
        $installedFile = Join-Path $install $item.Name
        Check ((Get-FileHash -LiteralPath $installedFile -Algorithm SHA256).Hash -eq $item.Value) ('installed digest for ' + $item.Name)
    }

    Invoke-Installer '/i' $Newer 'upgrade'
    $olderInstalled = $false
    $newerInstalled = $true
    $secondVersion = @(Installed-Version)
    Check ($secondVersion.Count -eq 1 -and [version]$secondVersion[0] -gt [version]$firstVersion[0]) 'upgrade replaces the registered development version'
    Invoke-Installer '/i' $Older 'blocked-downgrade' 1603
    Check (@(Installed-Version).Count -eq 1 -and @(Installed-Version)[0] -eq $secondVersion[0]) 'an older MSI leaves the newer version installed'

    Invoke-Installer '/x' $Newer 'uninstall'
    $newerInstalled = $false
    Check (-not [IO.File]::Exists($linkPath) -and -not [IO.File]::Exists($expectedScript) -and
        @(Installed-Version).Count -eq 0) 'uninstall removes the registered version and its shortcut'
    Check ([IO.File]::ReadAllText($preserved) -eq 'keep legacy companion data') 'install, upgrade, and removal preserve legacy user data'
} finally {
    try {
        if ($newerInstalled) { Invoke-Installer '/x' $Newer 'cleanup-newer' }
        if ($olderInstalled) { Invoke-Installer '/x' $Older 'cleanup-older' }
    } finally { [IO.File]::Delete($preserved) }
}
