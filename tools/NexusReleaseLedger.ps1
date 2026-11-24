# Local signing version guard. No key/password handling.
function Open-NexusReleaseLock([string]$Path) {
    # Exclusive OS lock; a leftover lock file after a crash is harmless.
    return [IO.File]::Open(($Path + ".lock"), [IO.FileMode]::OpenOrCreate,
        [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
}
function Assert-NexusReleaseVersion([string]$Path, [string]$Certificate, [int]$Version) {
    if ($Certificate -cnotmatch '^[a-f0-9]{64}$' -or $Version -lt 20000 -or $Version -gt 2100000000) {
        throw "Invalid release identity."
    }
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $state = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -ErrorAction Stop
    if ($null -eq $state -or $state.format -ne 1 -or $state.package -cne "com.pocketnexus.console" -or
        [string]$state.certificateSha256 -cnotmatch '^[a-f0-9]{64}$' -or
        [string]$state.lastVersionCode -notmatch '^[0-9]{5,10}$') {
        throw "Invalid release ledger; restore the trusted backup before building."
    }
    $last = [long]$state.lastVersionCode
    if ($last -lt 20000 -or $last -gt 2100000000) { throw "Invalid ledger version." }
    if ($state.certificateSha256 -cne $Certificate) { throw "Release certificate differs from local ledger." }
    if ($Version -le $last) { throw "Version must be greater than the last reserved release ($last)." }
}
function Save-NexusReleaseVersion([string]$Path, [string]$Certificate, [int]$Version) {
    # Caller holds exclusive lock through the entire build and export.
    Assert-NexusReleaseVersion $Path $Certificate $Version
    $temp = $Path + "." + [guid]::NewGuid().ToString("N") + ".tmp"
    try {
        $json = [ordered]@{ format=1; package="com.pocketnexus.console"; certificateSha256=$Certificate;
            lastVersionCode=$Version } | ConvertTo-Json
        [IO.File]::WriteAllText($temp, $json, [Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $Path) {
            [IO.File]::Replace($temp, $Path, $null)
        } else {
            [IO.File]::Move($temp, $Path)
        }
    } finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force }
    }
}
