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
Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;
using System.Text;

public static class NexusMsiRegistration {
    [DllImport("msi.dll", CharSet = CharSet.Unicode, EntryPoint = "MsiGetProductInfoExW")]
    public static extern uint CurrentUserProduct(string product, string userSid, uint context,
        string property, StringBuilder value, ref uint length);
}
'@

function Get-InstalledVersion([string]$ProductCode) {
    $result = New-Object Text.StringBuilder 64
    $length = [uint32]$result.Capacity
    # MSIINSTALLCONTEXT_USERUNMANAGED checks the package in the current user's context.
    $status = [NexusMsiRegistration]::CurrentUserProduct($ProductCode, $null, 2, 'VersionString', $result, [ref]$length)
    if ($status -eq 1605) { return $null }
    if ($status -ne 0) { throw "Windows Installer could not query the product registration (error $status)." }
    return [version]($result.ToString())
}

function Installed-Product([string]$Label) {
    $log = Join-Path $LogDirectory ($Label + '.log')
    $entry = Select-String -LiteralPath $log -Pattern '^Property\(S\): ProductCode = (\{[A-F0-9-]+\})$' | Select-Object -Last 1
    if (-not $entry) { throw 'Windows Installer did not log the package product code.' }
    $code = $entry.Matches[0].Groups[1].Value
    return [pscustomobject]@{ Code = $code; Version = Get-InstalledVersion $code }
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
    $first = Installed-Product 'install'
    Check ($null -ne $first.Version) 'the installed product is registered for the current user'
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
    $second = Installed-Product 'upgrade'
    Check ($second.Code -ne $first.Code -and $second.Version -gt $first.Version -and
        $null -eq (Get-InstalledVersion $first.Code)) ("upgrade replaces the registered development version $($first.Version) with $($second.Version)")
    Invoke-Installer '/i' $Older 'blocked-downgrade' 1603
    Check ((Get-InstalledVersion $second.Code) -eq $second.Version -and
        $null -eq (Get-InstalledVersion $first.Code)) 'an older MSI leaves the newer version installed'

    Invoke-Installer '/x' $Newer 'uninstall'
    $newerInstalled = $false
    Check (-not [IO.File]::Exists($linkPath) -and -not [IO.File]::Exists($expectedScript) -and
        $null -eq (Get-InstalledVersion $second.Code)) 'uninstall removes the registered version and its shortcut'
    Check ([IO.File]::ReadAllText($preserved) -eq 'keep legacy companion data') 'install, upgrade, and removal preserve legacy user data'
} finally {
    try {
        if ($newerInstalled) { Invoke-Installer '/x' $Newer 'cleanup-newer' }
        if ($olderInstalled) { Invoke-Installer '/x' $Older 'cleanup-older' }
    } finally { [IO.File]::Delete($preserved) }
}
