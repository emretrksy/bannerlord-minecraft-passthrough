param(
	[string]$BannerlordDir = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord"
)
$ErrorActionPreference = "Stop"
$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $BannerlordDir "Modules\MinecraftPassthrough")
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $bin "MCPassthrough.addon64")
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $bin "reshade-shaders\Shaders\MCPassthrough.fx")
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $bin "MCPassthroughPreset.ini")

$marker = Join-Path $bin "MCPassthrough.installed"
if (Test-Path $marker) {
	foreach ($name in @("dxgi.dll", "ReShade.ini", "ReShade.log", "MCPassthrough.installed")) {
		Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $bin $name)
	}
	$shaders = Join-Path $bin "reshade-shaders"
	if ((Test-Path $shaders) -and -not (Get-ChildItem -Recurse -File $shaders)) { Remove-Item -Recurse -Force $shaders }
}
Write-Host "Removed the passthrough files from $BannerlordDir"
