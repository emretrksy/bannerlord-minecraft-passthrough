param(
	[string]$BannerlordDir = "",
	[string]$MinecraftDir = ""
)
$ErrorActionPreference = "Stop"
$profileId = "bannerlord-passthrough"
$utf8 = New-Object System.Text.UTF8Encoding $false

if (-not $BannerlordDir) {
	$default = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord"
	if (Test-Path $default) { $BannerlordDir = $default }
	else { $BannerlordDir = Read-Host "Bannerlord folder (the folder that has 'bin' and 'Modules')" }
}
$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"
if (Get-Process Bannerlord -ErrorAction SilentlyContinue) { throw "Close Bannerlord first." }

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

Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath("Desktop")) "Bannerlord x Minecraft.lnk")

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA ".minecraft" }
$mods = Join-Path $MinecraftDir "bannerlord-passthrough\mods"
if (Test-Path $mods) { Get-ChildItem $mods -Filter "blpassthrough-*.jar" | Remove-Item -Force }

$profiles = Join-Path $MinecraftDir "launcher_profiles.json"
if (Test-Path $profiles) {
	if (Get-Process MinecraftLauncher, Minecraft -ErrorAction SilentlyContinue) {
		Write-Warning "Close the Minecraft Launcher and run Uninstall.bat again to remove the 'Bannerlord x Minecraft' profile."
	} else {
		$launcher = Get-Content -Raw -Encoding UTF8 $profiles | ConvertFrom-Json
		if ($launcher.profiles -and $launcher.profiles.PSObject.Properties[$profileId]) {
			$launcher.profiles.PSObject.Properties.Remove($profileId)
			[System.IO.File]::WriteAllText($profiles, ($launcher | ConvertTo-Json -Depth 32), $utf8)
			Write-Host "Removed the 'Bannerlord x Minecraft' Minecraft profile"
		}
	}
}
Write-Host "Steve's Minecraft folder is kept: $MinecraftDir\bannerlord-passthrough"
