$ErrorActionPreference = "Stop"
$source = Join-Path (Split-Path -Parent $PSScriptRoot) "NexusSecureHost.cs"
$checks = Join-Path $PSScriptRoot "SecureHostChecks.cs"
$vibepollo = Join-Path (Split-Path -Parent $PSScriptRoot) "NexusVibepollo.cs"
$vibepolloChecks = Join-Path $PSScriptRoot "VibepolloStatusChecks.cs"
Add-Type -Path @($source, $checks, $vibepollo, $vibepolloChecks) -ReferencedAssemblies @("System.dll", "System.Core.dll", "System.Runtime.Serialization.dll", "System.Security.dll", "System.Xml.dll")

$directory = Join-Path ([IO.Path]::GetTempPath()) ("nexus-secure-test-" + [Guid]::NewGuid().ToString("N"))
$certificate = $null
try {
    New-Item -ItemType Directory -Path $directory | Out-Null
    $certificate = New-SelfSignedCertificate -Subject "CN=Pocket Nexus Host Status Test" -Type SSLServerAuthentication -CertStoreLocation "Cert:\CurrentUser\My" -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddDays(1)
    $results = [PocketNexus.SecureHostChecks]::Run($certificate, (Join-Path $directory "devices.txt"))
    $results | ForEach-Object { Write-Host "  ok  $_" }
    Write-Host "SECURE HOST PASS $($results.Length)"
    $results = [PocketNexus.VibepolloStatusChecks]::Run($certificate, $directory)
    $results | ForEach-Object { Write-Host "  ok  $_" }
    Write-Host "VIBEPOLLO STATUS PASS $($results.Length)"
} finally {
    if ($certificate) {
        Remove-Item -LiteralPath "Cert:\CurrentUser\My\$($certificate.Thumbprint)" -DeleteKey
    }
    Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
}
