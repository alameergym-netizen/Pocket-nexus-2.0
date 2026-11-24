# Owner-local release build. No installation, publication or upload.
# Requires trusted JDK 17, Android SDK (platform 36/NDK 27.0.12077973), git, Gradle wrapper.
param(
    [Parameter(Mandatory=$true)][string]$KeyStore,
    [string]$Alias = "nexus-release",
    [int]$VersionCode,
    [switch]$InitializeKey,
    [string]$OutputDirectory = "$env:LOCALAPPDATA\PocketNexusRelease"
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "NexusReleaseLedger.ps1")
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$key = [IO.Path]::GetFullPath($KeyStore)
$out = [IO.Path]::GetFullPath($OutputDirectory)
function Assert-OutsideRepository([string]$Path) {
    if ($Path.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
        $Path.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Keep signing material and output outside the source repository."
    }
}
Assert-OutsideRepository $key
Assert-OutsideRepository $out
if ($Alias -notmatch '^[a-zA-Z0-9._-]{1,64}$') { throw "Invalid alias." }
if (-not (Get-Command keytool.exe -ErrorAction SilentlyContinue)) { throw "JDK keytool.exe not found." }
if ($InitializeKey) {
    if (Test-Path -LiteralPath $key) { throw "Existing keystore will never be overwritten." }
    $parent = Split-Path -Parent $key
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw "Create your private key directory first." }
    Write-Host "Create a private signing key on this PC. Choose a strong password. Keep an encrypted offline backup."
    & keytool.exe -genkeypair -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -keystore $key -dname "CN=Pocket Nexus Owner"
    if ($LASTEXITCODE -ne 0) { throw "Key generation failed." }
    Write-Host "Key created locally. Keep it private. No APK was built."
    exit 0
}
if ($VersionCode -lt 20000 -or $VersionCode -gt 2100000000) { throw "Specify an explicit increasing -VersionCode." }
if (-not (Test-Path -LiteralPath $key -PathType Leaf)) { throw "No existing key. Use -InitializeKey only for the first key creation." }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "Set ANDROID_HOME to the trusted Android SDK." }
$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and (Test-Path (Join-Path $_.FullName "apksigner.bat")) } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw "Android SDK build tools missing." }
$signer = Join-Path $buildTools.FullName "apksigner.bat"
$aapt = Join-Path $buildTools.FullName "aapt.exe"
if (-not (Test-Path -LiteralPath $aapt)) { throw "aapt.exe missing." }
$destination = Join-Path $out ("PocketNexus-" + $VersionCode + ".apk")
if ((Test-Path -LiteralPath $destination) -or (Test-Path -LiteralPath ($destination + ".json"))) { throw "Output already exists. Never silently replace a release." }
$names = @("NEXUS_KEYSTORE","NEXUS_KEY_ALIAS","NEXUS_STORE_PASSWORD","NEXUS_KEY_PASSWORD","NEXUS_VERSION_CODE","GITHUB_SHA","SOURCE_DATE_EPOCH")
$old = @{}
foreach ($n in $names) { $old[$n] = [Environment]::GetEnvironmentVariable($n, "Process") }
$secret = Read-Host "Keystore password (local only)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$cert = Join-Path ([IO.Path]::GetTempPath()) ("nexus-cert-" + [guid]::NewGuid() + ".der")
$ledgerLock = $null
Push-Location $root
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:NEXUS_KEYSTORE = $key
    $env:NEXUS_KEY_ALIAS = $Alias
    $env:NEXUS_STORE_PASSWORD = $password
    $env:NEXUS_KEY_PASSWORD = $password # PKCS12 uses the same password for key and store.
    $env:NEXUS_VERSION_CODE = [string]$VersionCode
    $sha = (& git rev-parse HEAD | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[a-f0-9]{40}$') { throw "Git revision unavailable." }
    if (@(& git status --porcelain --untracked-files=normal).Count -ne 0) { throw "Build from a clean checked-out source tree." }
    $env:GITHUB_SHA = $sha
    $epoch = (& git show -s --format=%ct HEAD | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or $epoch -notmatch "^[0-9]+$") { throw "Git commit time unavailable." }
    $env:SOURCE_DATE_EPOCH = $epoch
    & keytool.exe -exportcert -alias $Alias -keystore $key -storepass:env NEXUS_STORE_PASSWORD -file $cert
    if ($LASTEXITCODE -ne 0) { throw "Could not verify owner certificate." }
    $expected = (Get-FileHash -Algorithm SHA256 -LiteralPath $cert).Hash.ToLowerInvariant()
    $ledgerPath = $key + ".release-ledger.json"
    $ledgerLock = Open-NexusReleaseLock $ledgerPath
    Assert-NexusReleaseVersion $ledgerPath $expected $VersionCode
    & .\gradlew.bat --no-daemon -PpocketAbis=arm64-v8a :app:assembleNonRoot_gameRelease
    if ($LASTEXITCODE -ne 0) { throw "Release build failed." }
    $apks = @(Get-ChildItem "app\build\outputs\apk\nonRoot_game\release" -Filter *.apk -File)
    if ($apks.Count -ne 1) { throw "Expected exactly one release APK." }
    $apk = $apks[0].FullName
    $certs = @(& $signer verify --print-certs $apk)
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
    $digests = @($certs | ForEach-Object {
        if ($_ -match '^Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$') { $Matches[1].ToLowerInvariant() }
    } | Sort-Object -Unique)
    if ($digests.Count -ne 1 -or $digests[0] -cne $expected) { throw "APK signer does not match owner key." }
    $badging = @(& $aapt dump badging $apk)
    if ($LASTEXITCODE -ne 0 -or -not ($badging | Where-Object { $_ -match ("^package: name='com\.pocketnexus\.console' versionCode='" + $VersionCode + "'") })) {
        throw "APK identity/version verification failed."
    }
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    # Reserve this version before exposing the APK. An interrupted delivery consumes the code safely.
    Save-NexusReleaseVersion $ledgerPath $expected $VersionCode
    [IO.File]::Copy($apk, $destination, $false)
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()
    [ordered]@{ package="com.pocketnexus.console"; versionCode=$VersionCode; commit=$sha;
        certificateSha256=$expected; apkSha256=$hash; abi="arm64-v8a" } |
        ConvertTo-Json | Set-Content -LiteralPath ($destination + ".json") -Encoding UTF8
    Write-Host "Verified owner-signed APK: $destination"
    Write-Host "Nothing installed or uploaded. For upgrades, use Test-NexusUpgrade.ps1 first."
} finally {
    if ($null -ne $ledgerLock) { $ledgerLock.Dispose() }
    foreach ($n in $names) { [Environment]::SetEnvironmentVariable($n, $old[$n], "Process") }
    $password = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secret.Dispose()
    if (Test-Path -LiteralPath $cert) { Remove-Item -LiteralPath $cert -Force }
    Pop-Location
}
