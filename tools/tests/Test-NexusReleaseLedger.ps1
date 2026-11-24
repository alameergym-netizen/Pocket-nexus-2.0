$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "../NexusReleaseLedger.ps1")
function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAIL: $Label" }
    Write-Host "PASS: $Label"
}
function Reject([scriptblock]$Action, [string]$Label) {
    $failed = $false
    try { & $Action } catch { $failed = $true }
    Check $failed $Label
}
$dir = Join-Path ([IO.Path]::GetTempPath()) ("nexus-ledger-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $dir | Out-Null
$p = Join-Path $dir "fixture.release-ledger.json"
$a = "a" * 64
$b = "b" * 64
$lock = $null
try {
    $lock = Open-NexusReleaseLock $p
    Reject { $second = Open-NexusReleaseLock $p; $second.Dispose() } "exclusive lock"
    Assert-NexusReleaseVersion $p $a 20001
    Save-NexusReleaseVersion $p $a 20001
    Check ((Get-Content $p -Raw | ConvertFrom-Json).lastVersionCode -eq 20001) "first reservation"
    Reject { Assert-NexusReleaseVersion $p $a 20001 } "same version rejected"
    Reject { Assert-NexusReleaseVersion $p $a 20000 } "older version rejected"
    Reject { Assert-NexusReleaseVersion $p $b 20002 } "signer replacement rejected"
    Save-NexusReleaseVersion $p $a 20002
    Check ((Get-Content $p -Raw | ConvertFrom-Json).lastVersionCode -eq 20002) "atomic newer reservation"
    $lock.Dispose(); $lock = Open-NexusReleaseLock $p
    Reject { Assert-NexusReleaseVersion $p $a 20002 } "reservation survives reopen"
    Set-Content $p -Value '{"format":1,"lastVersionCode":"invalid"}'
    Reject { Assert-NexusReleaseVersion $p $a 20003 } "malformed ledger fails closed"
    Reject { Assert-NexusReleaseVersion $p $a 0 } "invalid input rejected"
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
    Remove-Item -LiteralPath $dir -Recurse -Force
}
