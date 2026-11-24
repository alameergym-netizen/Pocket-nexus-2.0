# Verifies a development ZIP and optionally stages it beside any existing installation.
param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedSha256,
    [string]$StageRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Test-PortablePath([string]$Name) {
    if ($Name.Length -gt 120) { return $false }
    $parts = $Name.Split('/')
    if ($parts.Length -gt 4) { return $false }
    foreach ($part in $parts) {
        if ($part -notmatch '^[a-zA-Z0-9_-]+(?:\.[a-zA-Z0-9_-]+)*$' -or
            $part -match '^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\.|$)') { return $false }
    }
    return $true
}

function Read-Entry($Entry) {
    if ($Entry.Length -gt 8MB) { throw 'A package file is too large.' }
    $inputStream = $Entry.Open()
    $buffer = New-Object byte[] 32768
    $output = New-Object IO.MemoryStream
    try {
        while (($count = $inputStream.Read($buffer, 0, $buffer.Length)) -ne 0) {
            if ($output.Length + $count -gt $Entry.Length) { throw 'A package file exceeds its declared length.' }
            $output.Write($buffer, 0, $count)
        }
        if ($output.Length -ne $Entry.Length) { throw 'A package file is incomplete.' }
        return ,$output.ToArray()
    } finally {
        $inputStream.Dispose()
        $output.Dispose()
    }
}

function Read-VerifiedPackage([string]$Path, [string]$ExpectedHash) {
    $source = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        if ($source.Length -gt 32MB) { throw 'The Windows development archive exceeds the size limit.' }
        $hash = (Get-FileHash -InputStream $source -Algorithm SHA256).Hash
        if ($hash -ne $ExpectedHash) { throw 'The ZIP SHA-256 does not match the expected digest.' }
        $source.Position = 0
        $zip = [IO.Compression.ZipArchive]::new($source, [IO.Compression.ZipArchiveMode]::Read, $true)
        try {
            if ($zip.Entries.Count -gt 64) { throw 'The Windows development archive has too many entries.' }
            $entries = [Collections.Generic.Dictionary[string,byte[]]]::new([StringComparer]::OrdinalIgnoreCase)
            $total = 0L
            foreach ($entry in $zip.Entries) {
                if (-not (Test-PortablePath $entry.FullName)) { throw 'The Windows development archive contains an unsupported path.' }
                if ($entries.ContainsKey($entry.FullName)) { throw 'The package contains a duplicate path.' }
                $total += $entry.Length
                if ($total -gt 32MB) { throw 'The unpacked Windows development archive exceeds the size limit.' }
                $entries.Add($entry.FullName, (Read-Entry $entry))
            }
        } finally { $zip.Dispose() }
    } finally { $source.Dispose() }

    $manifestName = 'SHA256-MANIFEST.json'
    foreach ($required in @($manifestName, 'PocketNexus-PC.bat', 'PocketNexus-PC.ps1', 'NexusSecureHost.cs', 'NexusVibepollo.cs')) {
        if (-not $entries.ContainsKey($required)) { throw "The development archive is missing $required." }
    }
    $utf8 = New-Object Text.UTF8Encoding($false, $true)
    $manifest = $utf8.GetString($entries[$manifestName]) | ConvertFrom-Json
    if ($manifest.format -ne 1 -or $manifest.channel -cne 'development' -or
        $manifest.files -isnot [pscustomobject]) {
        throw 'The package manifest does not describe this development archive.'
    }
    $records = @($manifest.files.PSObject.Properties)
    if ($records.Count -ne ($entries.Count - 1)) { throw 'The package manifest is incomplete.' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        foreach ($file in $records) {
            if ($file.Name -eq $manifestName -or -not $entries.ContainsKey($file.Name) -or
                $file.Value -isnot [string] -or $file.Value -notmatch '^[0-9a-f]{64}$') {
                throw 'The package manifest contains an invalid file record.'
            }
            $parent = $file.Name
            while (($end = $parent.LastIndexOf('/')) -ge 0) {
                $parent = $parent.Substring(0, $end)
                if ($entries.ContainsKey($parent)) { throw 'A package file conflicts with a directory.' }
            }
            $actual = [BitConverter]::ToString($sha.ComputeHash($entries[$file.Name])).Replace('-', '')
            if ($actual -ne $file.Value) { throw "The file hash does not match for $($file.Name)." }
        }
    } finally { $sha.Dispose() }
    return ,$entries
}

$location = (Resolve-Path -LiteralPath $Archive).ProviderPath
$files = Read-VerifiedPackage $location $ExpectedSha256
$stagedPath = $null
if ($StageRoot) {
    $root = [IO.Path]::GetFullPath($ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($StageRoot))
    [void][IO.Directory]::CreateDirectory($root)
    $dest = Join-Path $root ('NexusHost-dev-' + $ExpectedSha256.Substring(0, 16).ToLowerInvariant())
    if ([IO.Directory]::Exists($dest) -or [IO.File]::Exists($dest)) { throw 'This staging destination already exists.' }
    $stage = Join-Path $root ('.nexus-stage-' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($stage)
    try {
        foreach ($name in $files.Keys) {
            $filePath = Join-Path $stage $name
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($filePath))
            [IO.File]::WriteAllBytes($filePath, $files[$name])
        }
        [IO.Directory]::Move($stage, $dest)
        $stagedPath = $dest
    } finally {
        if ([IO.Directory]::Exists($stage)) { [IO.Directory]::Delete($stage, $true) }
    }
}

[pscustomobject]@{
    Result = 'VERIFIED_DEVELOPMENT_ARCHIVE'
    Sha256 = $ExpectedSha256.ToLowerInvariant()
    FileCount = $files.Count - 1
    StagedPath = $stagedPath
}
