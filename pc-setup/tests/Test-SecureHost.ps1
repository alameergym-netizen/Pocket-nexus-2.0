$ErrorActionPreference = "Stop"
$source = Join-Path (Split-Path -Parent $PSScriptRoot) "NexusSecureHost.cs"
$checks = Join-Path $PSScriptRoot "SecureHostChecks.cs"
Add-Type -Path @($source, $checks)

$directory = Join-Path ([IO.Path]::GetTempPath()) ("nexus-secure-test-" + [Guid]::NewGuid().ToString("N"))
$certificate = $null
try {
    New-Item -ItemType Directory -Path $directory | Out-Null
    $certificate = New-SelfSignedCertificate -Subject "CN=Pocket Nexus Host Status Test" -Type SSLServerAuthentication -CertStoreLocation "Cert:\CurrentUser\My" -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddDays(1)
    $results = [PocketNexus.SecureHostChecks]::Run($certificate, (Join-Path $directory "devices.txt"))
    $results | ForEach-Object { Write-Host "  ok  $_" }
    Write-Host "SECURE HOST PASS $($results.Length)"
} finally {
    if ($certificate) {
        Remove-Item -LiteralPath "Cert:\CurrentUser\My\$($certificate.Thumbprint)" -DeleteKey
    }
    Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
}
