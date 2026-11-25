param(
    [Parameter(Mandatory = $true)][string]$Msi,
    [Parameter(Mandatory = $true)][string]$OutputMsi
)
$ErrorActionPreference = 'Stop'

function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FAILED: $Label" }
    Write-Host "PASS: $Label"
}

function Expect-Rejected([scriptblock]$Call, [string]$Message) {
    try { & $Call | Out-Null }
    catch { if ($_.Exception.Message -like "*$Message*") { return }; throw }
    throw "FAILED: expected rejection containing $Message"
}

$tool = Get-ChildItem -Path "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -File |
    Sort-Object { [version]$_.Directory.Parent.Name } -Descending | Select-Object -First 1
if (-not $tool) { throw 'The Windows SDK signing tool is not available.' }
$script = Join-Path (Split-Path $PSScriptRoot) 'Sign-NexusHostMsi.ps1'
$id = [Guid]::NewGuid().ToString('N')
$certificate = New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=Nexus CI only $id" -CertStoreLocation 'Cert:\CurrentUser\My' `
    -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -NotAfter ((Get-Date).AddDays(1))
$root = New-Object Security.Cryptography.X509Certificates.X509Store 'Root', 'CurrentUser'
$trusted = $false
try {
    $root.Open([Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
    $root.Add($certificate)
    $trusted = $true
    $unsignedDigest = (Get-FileHash -LiteralPath $Msi -Algorithm SHA256).Hash
    $options = @{ Msi = $Msi; SignTool = $tool.FullName; CertificateThumbprint = $certificate.Thumbprint; OutputMsi = $OutputMsi }
    Expect-Rejected { & $script @options -ExpectedSha256 ('0' * 64) } 'SHA-256 does not match'
    Check (-not (Test-Path -LiteralPath $OutputMsi)) 'hash mismatch leaves no signed installer'

    $signed = & $script @options -ExpectedSha256 $unsignedDigest
    $signature = Get-AuthenticodeSignature -LiteralPath $signed.Msi
    Check ($signed.Result -eq 'SIGNED_MSI_VERIFIED' -and $signature.Status -eq 'Valid' -and
        $signature.SignerCertificate.Thumbprint -eq $certificate.Thumbprint -and $signed.SignerCertificateSha256.Length -eq 64) 'a disposable publisher signs and verifies the installer'
    Check ((Get-FileHash -LiteralPath $Msi -Algorithm SHA256).Hash -eq $unsignedDigest -and
        (Get-FileHash -LiteralPath $signed.Msi -Algorithm SHA256).Hash -eq $signed.Sha256) 'the signed copy has its own digest and preserves the original'
    Expect-Rejected { & $script @options -ExpectedSha256 $unsignedDigest } 'new output MSI path'
    Check ((Get-FileHash -LiteralPath $signed.Msi -Algorithm SHA256).Hash -eq $signed.Sha256) 'an existing signed MSI stays intact'
} finally {
    try { if ($trusted) { $root.Remove($certificate) } }
    finally {
        $root.Close()
        Remove-Item -LiteralPath (Join-Path 'Cert:\CurrentUser\My' $certificate.Thumbprint) -DeleteKey -ErrorAction Stop
    }
}
