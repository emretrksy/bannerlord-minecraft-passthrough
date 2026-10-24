$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$repo = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $repo ".tools"
New-Item -ItemType Directory -Force $tools | Out-Null

if (-not (Test-Path (Join-Path $tools "jdk"))) {
	Write-Host "Downloading JDK 25..."
	$zip = Join-Path $tools "jdk25.zip"
	Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zip -UseBasicParsing
	Expand-Archive $zip (Join-Path $tools "jdk") -Force
	Remove-Item $zip
}

if (-not (Test-Path (Join-Path $tools "reshade-src\include\reshade.hpp"))) {
	Write-Host "Fetching ReShade 6.8.0 add-on headers..."
	git clone -q --depth 1 --branch v6.8.0 --filter=blob:none --sparse https://github.com/crosire/reshade (Join-Path $tools "reshade-src")
	git -C (Join-Path $tools "reshade-src") sparse-checkout set include
}

$dll = Join-Path $tools "reshade-bin\ReShade64.dll"
if (-not (Test-Path $dll)) {
	Write-Host "Downloading ReShade 6.8.0 with add-on support..."
	$setup = Join-Path $tools "ReShade_Setup_Addon.exe"
	Invoke-WebRequest "https://reshade.me/downloads/ReShade_Setup_6.8.0_Addon.exe" -OutFile $setup -UseBasicParsing -UserAgent "Mozilla/5.0"
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($setup)
	try {
		New-Item -ItemType Directory -Force (Split-Path $dll) | Out-Null
		[System.IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry("ReShade64.dll"), $dll, $true)
	} finally {
		$archive.Dispose()
	}
}

Write-Host "Dependencies are in $tools"
