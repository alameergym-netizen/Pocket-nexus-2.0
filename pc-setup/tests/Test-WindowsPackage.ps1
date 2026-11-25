param([Parameter(Mandatory = $true)][string]$Archive)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}

function Expect-Rejected([scriptblock]$Run, [string]$Message) {
    try { & $Run | Out-Null }
    catch {
        if ($_.Exception.Message -like "*$Message*") { return }
        throw
    }
    throw "FAILED: expected rejection mentioning $Message"
}

function New-ModifiedArchive([string]$Path, [string]$Name, [bool]$Replace) {
    [IO.File]::Copy($source, $Path)
    $zip = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Update)
    try {
        if ($Replace) { $zip.GetEntry($Name).Delete() }
        $file = $zip.CreateEntry($Name).Open()
        try { $file.WriteByte(97) }
        finally { $file.Dispose() }
    } finally { $zip.Dispose() }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

$candidate = Join-Path (Split-Path $PSScriptRoot) 'Stage-NexusHostDev.ps1'
$source = (Resolve-Path -LiteralPath $Archive).ProviderPath
$digest = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('nexus-package-test-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
try {
    $verified = & $candidate -Archive $source -ExpectedSha256 $digest
    Check ($verified.Result -eq 'VERIFIED_DEVELOPMENT_ARCHIVE' -and $verified.FileCount -gt 6 -and
        $null -eq $verified.StagedPath) 'verified mode makes no deployment'

    $stageRoot = Join-Path $fixture 'side by side'
    $staged = & $candidate -Archive $source -ExpectedSha256 $digest -StageRoot $stageRoot
    Check ((Test-Path -LiteralPath (Join-Path $staged.StagedPath 'PocketNexus-PC.bat')) -and
        (Test-Path -LiteralPath (Join-Path $staged.StagedPath 'playnite/extension.yaml'))) 'verified archive stages with nested files'
    $sentinel = Join-Path $staged.StagedPath 'existing.txt'
    [IO.File]::WriteAllText($sentinel, 'preserve me')
    Expect-Rejected { & $candidate -Archive $source -ExpectedSha256 $digest -StageRoot $stageRoot } 'destination already exists'
    Check ([IO.File]::ReadAllText($sentinel) -eq 'preserve me') 'repeat staging preserves the existing directory'

    $blocked = Join-Path $fixture 'blocked'
    Expect-Rejected { & $candidate -Archive $source -ExpectedSha256 ('0' * 64) -StageRoot $blocked } 'SHA-256 does not match'
    $bad = Join-Path $fixture 'bad-file.zip'
    $badHash = New-ModifiedArchive $bad 'README.txt' $true
    Expect-Rejected { & $candidate -Archive $bad -ExpectedSha256 $badHash -StageRoot $blocked } 'file hash does not match'
    $badPath = Join-Path $fixture 'bad-path.zip'
    $badPathHash = New-ModifiedArchive $badPath '../escape.txt' $false
    Expect-Rejected { & $candidate -Archive $badPath -ExpectedSha256 $badPathHash -StageRoot $blocked } 'unsupported path'
    $duplicate = Join-Path $fixture 'duplicate.zip'
    $duplicateHash = New-ModifiedArchive $duplicate 'readme.txt' $false
    Expect-Rejected { & $candidate -Archive $duplicate -ExpectedSha256 $duplicateHash -StageRoot $blocked } 'duplicate path'
    Check (-not (Test-Path -LiteralPath $blocked)) 'invalid archives never create a staging destination'

    $installer = Join-Path (Split-Path $PSScriptRoot) 'Install-NexusHostDev.ps1'
    $installRoot = Join-Path $fixture 'Installed versions'
    $programsRoot = Join-Path $fixture 'Start Menu Programs'
    $arguments = @{ ExpectedSha256 = $digest; InstallRoot = $installRoot; ProgramsRoot = $programsRoot }
    $installed = & $installer @arguments -Archive $source
    Check ($installed.Result -eq 'INSTALLED_DEVELOPMENT_VERSION' -and
        (Test-Path -LiteralPath $installed.Shortcut -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $installed.Directory 'PocketNexus-PC.ps1'))) 'per-user installation registers a development shortcut'
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($installed.Shortcut)
    Check ($shortcut.Arguments.EndsWith(' -Background') -and
        $shortcut.Arguments.Contains('"' + $installed.Directory + '\PocketNexus-PC.ps1"')) 'shortcut launches the verified script in background mode with a quoted path'
    $second = & $installer @arguments -Archive $source
    Check ($second.Directory -eq $installed.Directory -and $second.Shortcut -eq $installed.Shortcut) 'same archive installs idempotently'

    $added = Join-Path $installed.Directory 'personal.txt'
    [IO.File]::WriteAllText($added, 'preserve unmanaged files')
    Expect-Rejected { & $installer @arguments -Remove } 'modified or unrecognized item'
    Check ((Test-Path -LiteralPath $installed.Shortcut) -and [IO.File]::ReadAllText($added) -eq 'preserve unmanaged files') 'removal preserves a modified installation and its shortcut'
    [IO.File]::Delete($added)

    $originalLink = [IO.File]::ReadAllBytes($installed.Shortcut)
    $shortcut.TargetPath = Join-Path $env:SystemRoot 'System32\notepad.exe'
    $shortcut.Save()
    Expect-Rejected { & $installer @arguments -Remove } 'already in use'
    Check ((Test-Path -LiteralPath $installed.Directory) -and
        $shell.CreateShortcut($installed.Shortcut).TargetPath -eq $shortcut.TargetPath) 'removal refuses to touch a different shortcut'
    [IO.File]::WriteAllBytes($installed.Shortcut, $originalLink)

    $removed = & $installer @arguments -Remove
    Check ($removed.Result -eq 'REMOVED_DEVELOPMENT_VERSION' -and
        -not (Test-Path -LiteralPath $installed.Directory) -and
        -not (Test-Path -LiteralPath $installed.Shortcut) -and
        [IO.File]::ReadAllText($sentinel) -eq 'preserve me') 'removal only deletes the verified version and its own shortcut'
} finally { [IO.Directory]::Delete($fixture, $true) }
