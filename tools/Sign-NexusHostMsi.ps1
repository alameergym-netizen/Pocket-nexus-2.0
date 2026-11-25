# Signs a separate MSI using a certificate already held in the current Windows user's store.
param(
    [Parameter(Mandatory = $true)][string]$Msi,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9A-Fa-f]{64}$')][string]$ExpectedSha256,
    [Parameter(Mandatory = $true)][string]$SignTool,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9A-Fa-f]{40}$')][string]$CertificateThumbprint,
    [Parameter(Mandatory = $true)][string]$OutputMsi,
    [uri]$TimestampServer
)
$ErrorActionPreference = 'Stop'

$source = Get-Item -LiteralPath $Msi -Force
if ($source -isnot [IO.FileInfo] -or $source.Extension -ne '.msi') { throw 'Select an unsigned Windows installer MSI.' }
$sdkTool = Get-Item -LiteralPath $SignTool -Force
if ($sdkTool -isnot [IO.FileInfo] -or $sdkTool.Name -ne 'signtool.exe') { throw 'Select SignTool from the Windows SDK.' }
if ($TimestampServer -and (-not $TimestampServer.IsAbsoluteUri -or $TimestampServer.Scheme -ne 'https' -or
    $TimestampServer.UserInfo -or $TimestampServer.Fragment)) { throw 'Select an HTTPS RFC 3161 timestamp server.' }
$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputMsi)
if ([IO.Path]::GetExtension($output) -ne '.msi' -or [IO.File]::Exists($output) -or [IO.Directory]::Exists($output)) {
    throw 'Select a new output MSI path.'
}
$folder = [IO.Path]::GetDirectoryName($output)
if (-not [IO.Directory]::Exists($folder)) { throw 'Select an existing output folder.' }
if ((Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash -ne $ExpectedSha256) {
    throw 'The unsigned MSI SHA-256 does not match.'
}
if ((Get-AuthenticodeSignature -LiteralPath $source.FullName).Status -ne 'NotSigned') {
    throw 'The input MSI must be unsigned.'
}
$certificate = Get-Item -LiteralPath (Join-Path 'Cert:\CurrentUser\My' $CertificateThumbprint) -ErrorAction SilentlyContinue
if (-not $certificate -or -not $certificate.HasPrivateKey -or $certificate.NotBefore -gt (Get-Date) -or
    $certificate.NotAfter -le (Get-Date)) { throw 'The selected certificate and its usable private key are required in Current User Personal.' }

$work = Join-Path $folder ('.nexus-msi-sign-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($work)
$staged = Join-Path $work 'signed.msi'
try {
    [IO.File]::Copy($source.FullName, $staged, $false)
    if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $ExpectedSha256) {
        throw 'The MSI changed before signing.'
    }
    $arguments = @('sign', '/s', 'My', '/sha1', $CertificateThumbprint, '/fd', 'SHA256')
    if ($TimestampServer) { $arguments += @('/tr', $TimestampServer.AbsoluteUri, '/td', 'SHA256') }
    $arguments += $staged
    $toolOutput = & $sdkTool.FullName @arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Windows signing failed: ' + ($toolOutput -join [Environment]::NewLine)) }
    $signature = Get-AuthenticodeSignature -LiteralPath $staged
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Thumbprint -ne $CertificateThumbprint -or
        ($TimestampServer -and -not $signature.TimeStamperCertificate)) {
        throw 'Windows could not verify the requested signer and timestamp for the staged MSI.'
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $signerHash = [BitConverter]::ToString($sha.ComputeHash($signature.SignerCertificate.RawData)).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
    $signedHash = (Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::Move($staged, $output)
    return [pscustomobject]@{
        Result = 'SIGNED_MSI_VERIFIED'
        Msi = $output
        Sha256 = $signedHash
        SignerCertificateSha256 = $signerHash
        Timestamped = [bool]$signature.TimeStamperCertificate
    }
} finally {
    if ([IO.File]::Exists($staged)) { [IO.File]::Delete($staged) }
    if ([IO.Directory]::Exists($work)) { [IO.Directory]::Delete($work) }
}
