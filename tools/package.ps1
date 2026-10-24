param(
	[switch]$NoBuild
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

$version = ([xml](Get-Content -Raw (Join-Path $repo "bannerlord-mod\Module\SubModule.xml"))).Module.Version.value
if (-not $version) { throw "No version in bannerlord-mod\Module\SubModule.xml" }

if (-not $NoBuild) {
	dotnet build (Join-Path $repo "bannerlord-mod") -c Release
	if ($LASTEXITCODE -ne 0) { throw "Bannerlord module build failed" }
	cmake --build (Join-Path $repo ".tools\build-compositor") --config Release
	if ($LASTEXITCODE -ne 0) { throw "Compositor build failed" }
	$env:JAVA_HOME = (Resolve-Path (Join-Path $repo ".tools\jdk\jdk-25*")).Path
	Push-Location (Join-Path $repo "minecraft-mod")
	try {
		.\gradlew.bat build
		if ($LASTEXITCODE -ne 0) { throw "Minecraft mod build failed" }
	} finally {
		Pop-Location
	}
}

$module = Join-Path $repo "build\Modules\MinecraftPassthrough"
$addon = Join-Path $repo ".tools\build-compositor\Release\MCPassthrough.addon64"
$reshade = Join-Path $repo ".tools\reshade-bin\ReShade64.dll"
$jar = Get-ChildItem (Join-Path $repo "minecraft-mod\build\libs") -Filter "blpassthrough-*.jar" | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1
foreach ($path in @($module, $addon, $reshade)) {
	if (-not (Test-Path $path)) { throw "Missing $path - build it first (see README)" }
}
if (-not $jar) { throw "Missing the Minecraft mod jar - build it first (see README)" }

$name = "MinecraftPassthrough-$version"
$dist = Join-Path $repo "dist"
$stage = Join-Path $dist $name
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
$bin = Join-Path $stage "Bannerlord\bin\Win64_Shipping_Client"
New-Item -ItemType Directory -Force (Join-Path $stage "Bannerlord\Modules\MinecraftPassthrough"), (Join-Path $bin "reshade-shaders\Shaders"), (Join-Path $stage "Minecraft\mods") | Out-Null

Copy-Item -Recurse -Force "$module\*" (Join-Path $stage "Bannerlord\Modules\MinecraftPassthrough")
Copy-Item -Force $reshade (Join-Path $bin "dxgi.dll")
Copy-Item -Force $addon $bin
Copy-Item -Force (Join-Path $repo "compositor\shaders\MCPassthrough.fx") (Join-Path $bin "reshade-shaders\Shaders")
Copy-Item -Force $jar.FullName (Join-Path $stage "Minecraft\mods")
$gradle = Get-Content (Join-Path $repo "minecraft-mod\gradle.properties") | ConvertFrom-StringData
$fabricId = "fabric-loader-$($gradle.loader_version)-$($gradle.minecraft_version)"
Invoke-WebRequest -UseBasicParsing "https://meta.fabricmc.net/v2/versions/loader/$($gradle.minecraft_version)/$($gradle.loader_version)/profile/json" `
	-OutFile (Join-Path $stage "Minecraft\$fabricId.json")
foreach ($file in @("Install.bat", "Install.ps1", "Uninstall.bat", "Uninstall.ps1", "Play.ps1", "README.txt", "THIRD-PARTY-NOTICES.txt")) {
	Copy-Item -Force (Join-Path $PSScriptRoot "release\$file") $stage
}
Copy-Item -Force (Join-Path $repo "LICENSE") (Join-Path $stage "LICENSE.txt")

$zip = Join-Path $dist "$name.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $stage -DestinationPath $zip
Write-Host "Package -> $zip"
