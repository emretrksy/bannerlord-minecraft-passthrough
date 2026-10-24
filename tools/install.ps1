param(
	[string]$BannerlordDir = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord",
	[string]$ReShadeDll = "",
	[string]$MinecraftModsDir = ""
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"
if (-not (Test-Path $bin)) { throw "Bannerlord not found at $BannerlordDir (pass -BannerlordDir)" }

$module = Join-Path $repo "build\Modules\MinecraftPassthrough"
$addon = Join-Path $repo ".tools\build-compositor\Release\MCPassthrough.addon64"
if (-not $ReShadeDll) { $ReShadeDll = Join-Path $repo ".tools\reshade-bin\ReShade64.dll" }
foreach ($path in @($module, $addon, $ReShadeDll)) {
	if (-not (Test-Path $path)) { throw "Missing $path - build it first (see README)" }
}

$moduleTarget = Join-Path $BannerlordDir "Modules\MinecraftPassthrough"
New-Item -ItemType Directory -Force $moduleTarget | Out-Null
Copy-Item -Recurse -Force "$module\*" $moduleTarget
Write-Host "Module    -> $moduleTarget"

$dxgi = Join-Path $bin "dxgi.dll"
$marker = Join-Path $bin "MCPassthrough.installed"
if ((Test-Path $dxgi) -and -not (Test-Path $marker)) {
	Write-Warning "$dxgi already exists and was not installed by this script; leaving it alone. It must be ReShade with add-on support."
} else {
	Copy-Item -Force $ReShadeDll $dxgi
	Set-Content -Encoding ascii $marker "dxgi.dll was installed by bannerlord-minecraft-passthrough"
	Write-Host "ReShade   -> $dxgi"
}

Copy-Item -Force $addon (Join-Path $bin "MCPassthrough.addon64")
$shaders = Join-Path $bin "reshade-shaders\Shaders"
New-Item -ItemType Directory -Force $shaders | Out-Null
Copy-Item -Force (Join-Path $repo "compositor\shaders\MCPassthrough.fx") $shaders
Write-Host "Add-on    -> $bin"

$ini = Join-Path $bin "ReShade.ini"
if (-not (Test-Path $ini)) {
	Set-Content -Encoding ascii $ini @"
[GENERAL]
EffectSearchPaths=.\reshade-shaders\Shaders\**
TextureSearchPaths=.\reshade-shaders\Textures\**
PresetPath=.\MCPassthroughPreset.ini

[OVERLAY]
TutorialProgress=4

[MCPassthrough]
PoseLag=0
"@
	Set-Content -Encoding ascii (Join-Path $bin "MCPassthroughPreset.ini") @"
Techniques=MCPassthrough@MCPassthrough.fx
TechniqueSorting=MCPassthrough@MCPassthrough.fx
"@
	Write-Host "Config    -> $ini"
} else {
	Write-Host "ReShade.ini exists; enable the 'Minecraft Passthrough' technique in the ReShade overlay (Home key)."
}

if ($MinecraftModsDir) {
	$jar = Get-ChildItem (Join-Path $repo "minecraft-mod\build\libs") -Filter "blpassthrough-*.jar" | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1
	if (-not $jar) { throw "Build the Minecraft mod first (minecraft-mod: gradlew build)" }
	New-Item -ItemType Directory -Force $MinecraftModsDir | Out-Null
	Copy-Item -Force $jar.FullName $MinecraftModsDir
	Write-Host "Minecraft -> $MinecraftModsDir\$($jar.Name)"
}

Write-Host "Done. Enable 'Minecraft Passthrough' in the Bannerlord launcher's mod list."
