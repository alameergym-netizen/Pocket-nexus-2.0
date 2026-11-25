$ErrorActionPreference = "Stop"
Add-Type -Path (Join-Path (Split-Path -Parent $PSScriptRoot) "NexusGpuTelemetry.cs")

function Assert-Telemetry($value, [string]$message) {
    if (-not $value) { throw $message }
}

# Synthetic NVIDIA CSV with separate adapter counters and unsupported fields.
$fixture = @'
0, NVIDIA GeForce RTX 4060, 0, 412, 8192, 48, 6
1, "NVIDIA Example, Test Adapter", 82, [N/A], 16384, [Not Supported], N/A
1, Duplicate adapter, 13, 314, 514, 66, 12
'@
$summary = [PocketNexus.NexusGpuTelemetry]::Format($fixture, $true)
Assert-Telemetry ($summary -match 'RTX 4060' -and $summary -match 'GPU utilization\s+0%') "A real zero must remain a reading"
Assert-Telemetry ($summary -match '412 MiB / 8192 MiB' -and $summary -match 'Temperature\s+48' -and $summary -match 'Video encoder\s+6%') "Readings must keep units and adapter ownership"
Assert-Telemetry ($summary -match 'NVIDIA Example, Test Adapter' -and $summary -match 'Unavailable / 16384 MiB') "Missing values must not hide the available fields"
Assert-Telemetry ($summary -notmatch 'Duplicate adapter') "One entry per adapter"
$fallback = [PocketNexus.NexusGpuTelemetry]::Format('0, GPU, 5, 11000, 8192, 300', $false)
Assert-Telemetry ($fallback -match 'GPU utilization\s+5%' -and $fallback -match 'Video memory\s+Unavailable / Unavailable') "Impossible memory readings must be unavailable"
Assert-Telemetry ($fallback -match 'Temperature\s+Unavailable' -and $fallback -match 'Video encoder\s+Unavailable') "Unsupported counters must be unavailable"
Assert-Telemetry ([PocketNexus.NexusGpuTelemetry]::Format(('x' * 16385), $true) -eq 'No NVIDIA GPU readings available.') "Oversized output must be rejected"

# Use the installed-driver path on this runner, which may have no NVIDIA hardware.
$live = [PocketNexus.NexusGpuTelemetry]::Sample()
Assert-Telemetry ($live.Wait(7000) -and -not [string]::IsNullOrWhiteSpace($live.Result)) "Runtime probe must produce a bounded response"
Write-Host "GPU TELEMETRY PASS: synthetic counter validation and local driver probe"
