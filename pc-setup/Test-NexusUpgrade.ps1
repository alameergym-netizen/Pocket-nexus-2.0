# Read-only APK upgrade preflight. Does not install, uninstall, clear data or change device settings.
# Requires adb, aapt and apksigner from a trusted Android SDK installation.
param(
    [string]$Apk,
    [string]$Adb = "adb",
    [string]$Aapt = "aapt",
    [string]$ApkSigner = "apksigner.bat",
    [string]$Serial,
    [switch]$SelfTest
)
$ErrorActionPreference = "Stop"
function Read-ApkIdentity([string[]]$Badging, [string[]]$Certs) {
    $line = @($Badging | Where-Object { $_ -match "^package: " })
    if ($line.Count -ne 1 -or $line[0] -notmatch "^package: name='([^']+)' versionCode='([0-9]+)'") {
        throw "Could not determine APK package/version."
    }
    $pkg = $Matches[1]; $version = [long]$Matches[2]
    $digests = @($Certs | ForEach-Object {
        if ($_ -match '^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$') {
            $Matches[1].ToLowerInvariant()
        }
    } | Sort-Object -Unique)
    if ($digests.Count -eq 0) { throw "No verified signing certificate found." }
    return [pscustomobject]@{ Package = $pkg; Version = $version; Signers = ($digests -join ",") }
}
function Compare-Upgrade($Installed, $Candidate) {
    if ($Installed.Package -ne $Candidate.Package) { return "DIFFERENT_PACKAGE" }
    if ($Installed.Signers -cne $Candidate.Signers) { return "SIGNER_MISMATCH" }
    if ($Candidate.Version -le $Installed.Version) { return "NOT_NEWER" }
    return "COMPATIBLE_IDENTITY"
}
function Assert-Expected($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}
if ($SelfTest) {
    $sig = "Signer #1 certificate SHA-256 digest: " + ("a" * 64)
    $a = Read-ApkIdentity @("package: name='com.pocketnexus.console.debug' versionCode='20001'") @($sig)
    $b = Read-ApkIdentity @("package: name='com.pocketnexus.console.debug' versionCode='20002'") @($sig)
    Assert-Expected ((Compare-Upgrade $a $b) -eq "COMPATIBLE_IDENTITY") "same package and signer, newer version"
    Assert-Expected ((Compare-Upgrade $b $a) -eq "NOT_NEWER") "downgrade rejected"
    Assert-Expected ((Compare-Upgrade $a $a) -eq "NOT_NEWER") "same version rejected"
    $c = Read-ApkIdentity @("package: name='com.pocketnexus.console' versionCode='20002'") @($sig)
    Assert-Expected ((Compare-Upgrade $a $c) -eq "DIFFERENT_PACKAGE") "release and debug identities distinct"
    $d = Read-ApkIdentity @("package: name='com.pocketnexus.console.debug' versionCode='20002'") @("Signer #1 certificate SHA-256 digest: " + ("b" * 64))
    Assert-Expected ((Compare-Upgrade $a $d) -eq "SIGNER_MISMATCH") "different signer rejected"
    $rejected = $false
    try { Read-ApkIdentity @("package: name='com.pocketnexus.console' versionCode='20002'") @("missing signer") | Out-Null } catch { $rejected = $true }
    Assert-Expected $rejected "missing signer rejected"
    exit 0
}
function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    $result = @(& $Tool @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "External verification command failed (exit $LASTEXITCODE)." }
    return $result
}
function Inspect-Apk([string]$Path) {
    $badging = @(Invoke-Checked $Aapt @("dump", "badging", $Path))
    $certs = @(Invoke-Checked $ApkSigner @("verify", "--print-certs", $Path))
    return Read-ApkIdentity $badging $certs
}
if (-not $Apk) { throw "Specify -Apk and trusted SDK tools. Use -SelfTest for offline policy tests." }
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$candidate = Inspect-Apk $apkPath
if ($candidate.Package -notin @("com.pocketnexus.console", "com.pocketnexus.console.debug", "com.pocketnexus.console.preview")) {
    throw "Not a recognized Pocket Nexus 2.0 package."
}
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }
$state = @(Invoke-Checked $Adb ($adbArgs + @("get-state")))
if (($state -join "").Trim() -ne "device") { throw "Connect and authorize exactly one device, or specify -Serial." }
$package = $candidate.Package
$paths = @(Invoke-Checked $Adb ($adbArgs + @("shell", "pm", "path", $package)))
$base = @($paths | Where-Object { $_ -match '^package:.+/base\.apk\s*$' })
if ($base.Count -ne 1) {
    throw "Installed base APK not confirmed. This tool checks existing upgrades, not fresh installations."
}
$remote = ($base[0] -replace '^package:', '').Trim()
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("nexus-apk-check-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    $local = Join-Path $temp "base.apk"
    Invoke-Checked $Adb ($adbArgs + @("pull", $remote, $local)) | Out-Null
    $installed = Inspect-Apk $local
    $result = Compare-Upgrade $installed $candidate
    Write-Host ("Package: " + $package)
    Write-Host ("Installed version code: " + $installed.Version)
    Write-Host ("Candidate version code: " + $candidate.Version)
    Write-Host ("Result: " + $result)
    if ($result -ne "COMPATIBLE_IDENTITY") {
        Write-Host "Do not uninstall the installed app. Keep a backup and obtain a compatible build."
        exit 2
    }
    Write-Host "Package, current signer and version agree. Back up settings; this is not proof of data migration or installation success."
} finally {
    Remove-Item -LiteralPath $temp -Recurse -Force
}
