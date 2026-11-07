# Install the official system engine used by the Inoagent desktop client.
# Shows the normal Windows installer and UAC. Run once before first VPN connection.
$ErrorActionPreference = 'Stop'
$release = Invoke-RestMethod 'https://api.github.com/repos/amnezia-vpn/amneziawg-windows-client/releases/latest'
$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } elseif ([Environment]::Is64BitOperatingSystem) { 'amd64' } else { 'x86' }
$asset = $release.assets | Where-Object { $_.name -match "^amneziawg-$arch-.*\.msi$" } | Select-Object -First 1
if (-not $asset) { throw 'Official installer not found for this architecture' }
if ($asset.digest -notmatch '^sha256:[a-fA-F0-9]{64}$') { throw 'No SHA-256 published by GitHub. Download and verify the official installer manually.' }
$folder = Join-Path $env:TEMP ('inoagent-runtime-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $folder | Out-Null
$installer = Join-Path $folder 'amneziawg.msi'
try {
    Invoke-WebRequest $asset.browser_download_url -OutFile $installer
    $expected = $asset.digest.Substring(7)
    if ((Get-FileHash $installer -Algorithm SHA256).Hash -ne $expected) { throw 'Installer checksum mismatch' }
    $process = Start-Process msiexec.exe -ArgumentList @('/i', ('"' + $installer + '"')) -Verb RunAs -Wait -PassThru
    if ($process.ExitCode -notin @(0,3010)) { throw "Installation failed: $($process.ExitCode)" }
} finally { Remove-Item $folder -Recurse -Force }
