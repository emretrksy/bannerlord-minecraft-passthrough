param(
	[string]$BannerlordDir = "",
	[string]$MinecraftDir = ""
)
$ErrorActionPreference = "Stop"
$package = $PSScriptRoot
$profileId = "bannerlord-passthrough"
$profileName = "Bannerlord x Minecraft"
$javaArgs = "-Xmx2G -Dmax.bg.threads=4 -XX:TieredStopAtLevel=1 -Dblpassthrough.preload=true"
$utf8 = New-Object System.Text.UTF8Encoding $false

function Find-Bannerlord {
	$roots = @()
	foreach ($key in @("HKCU:\Software\Valve\Steam", "HKLM:\SOFTWARE\WOW6432Node\Valve\Steam")) {
		try {
			$item = Get-ItemProperty $key -ErrorAction Stop
			if ($item.SteamPath) { $roots += $item.SteamPath }
			if ($item.InstallPath) { $roots += $item.InstallPath }
		} catch {}
	}
	$libraries = @()
	foreach ($root in $roots) {
		$libraries += $root
		$vdf = Join-Path $root "steamapps\libraryfolders.vdf"
		if (Test-Path $vdf) {
			foreach ($match in [regex]::Matches((Get-Content -Raw $vdf), '"path"\s+"([^"]+)"')) {
				$libraries += $match.Groups[1].Value.Replace("\\", "\")
			}
		}
	}
	foreach ($library in ($libraries | Select-Object -Unique)) {
		$dir = Join-Path $library "steamapps\common\Mount & Blade II Bannerlord"
		if (Test-Path (Join-Path $dir "bin\Win64_Shipping_Client\Bannerlord.exe")) { return $dir }
	}
	return $null
}

Write-Host "Bannerlord x Minecraft Passthrough - installer"
Write-Host ""

if (-not $BannerlordDir) { $BannerlordDir = Find-Bannerlord }
if (-not $BannerlordDir) {
	$BannerlordDir = Read-Host "Bannerlord folder not found. Paste its path (the folder that has 'bin' and 'Modules')"
}
$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"
if (-not (Test-Path (Join-Path $bin "Bannerlord.exe"))) { throw "Bannerlord.exe not found under $BannerlordDir (the Steam version is needed)" }
if (Get-Process Bannerlord -ErrorAction SilentlyContinue) { throw "Close Bannerlord first." }
$version = ""
try { $version = ([xml](Get-Content -Raw (Join-Path $bin "Version.xml"))).Version.Singleplayer.Value } catch {}
if ($version -and $version -ne "v1.4.8") {
	throw "This mod needs Bannerlord v1.4.8, but $version is installed. In Steam: Bannerlord > Properties > Betas > None, let it update, then run Install.bat again. / Bu mod Bannerlord v1.4.8 istiyor, sende $version kurulu. Steam'de: Bannerlord > Ozellikler > Betalar > Yok, guncellemesini bekle, sonra Install.bat'i tekrar calistir."
}

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA ".minecraft" }
$profiles = Join-Path $MinecraftDir "launcher_profiles.json"
if (-not (Test-Path $profiles)) {
	throw "The Minecraft Launcher has not been set up on this PC yet. Open it once, sign in, close it, then run Install.bat again."
}
if (Get-Process MinecraftLauncher, Minecraft -ErrorAction SilentlyContinue) {
	throw "Close the Minecraft Launcher first (it would overwrite the new profile when it exits)."
}

Write-Host "Bannerlord: $BannerlordDir"
Write-Host "Minecraft:  $MinecraftDir"
Write-Host ""

$sourceBin = Join-Path $package "Bannerlord\bin\Win64_Shipping_Client"
$moduleTarget = Join-Path $BannerlordDir "Modules\MinecraftPassthrough"
New-Item -ItemType Directory -Force $moduleTarget | Out-Null
Copy-Item -Recurse -Force (Join-Path $package "Bannerlord\Modules\MinecraftPassthrough\*") $moduleTarget
Copy-Item -Force (Join-Path $package "Play.ps1") $moduleTarget
Write-Host "Module    -> $moduleTarget"

$dxgi = Join-Path $bin "dxgi.dll"
$marker = Join-Path $bin "MCPassthrough.installed"
if ((Test-Path $dxgi) -and -not (Test-Path $marker)) {
	Write-Warning "$dxgi already exists and was not put there by this installer; it is left alone. It must be ReShade 6.x WITH add-on support, or Steve will not be drawn."
} else {
	Copy-Item -Force (Join-Path $sourceBin "dxgi.dll") $dxgi
	Set-Content -Encoding ascii $marker "dxgi.dll was installed by Bannerlord x Minecraft Passthrough"
	Write-Host "ReShade   -> $dxgi"
}

Copy-Item -Force (Join-Path $sourceBin "MCPassthrough.addon64") $bin
$shaders = Join-Path $bin "reshade-shaders\Shaders"
New-Item -ItemType Directory -Force $shaders | Out-Null
Copy-Item -Force (Join-Path $sourceBin "reshade-shaders\Shaders\MCPassthrough.fx") $shaders
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
	Write-Host "ReShade.ini exists: in game, press Home and tick the 'MCPassthrough' technique once."
}

$versionJson = Get-ChildItem (Join-Path $package "Minecraft") -Filter "fabric-loader-*.json" | Select-Object -First 1
$versionId = $versionJson.BaseName
$versionDir = Join-Path $MinecraftDir "versions\$versionId"
New-Item -ItemType Directory -Force $versionDir | Out-Null
Copy-Item -Force $versionJson.FullName (Join-Path $versionDir "$versionId.json")
Write-Host "Fabric    -> $versionDir"

$gameDir = Join-Path $MinecraftDir "bannerlord-passthrough"
$mods = Join-Path $gameDir "mods"
New-Item -ItemType Directory -Force $mods | Out-Null
Get-ChildItem $mods -Filter "blpassthrough-*.jar" | Remove-Item -Force
$jar = Get-ChildItem (Join-Path $package "Minecraft\mods") -Filter "*.jar" | Select-Object -First 1
Copy-Item -Force $jar.FullName $mods
Write-Host "Mod       -> $mods\$($jar.Name)"

Copy-Item -Force $profiles "$profiles.before-passthrough"
$launcher = Get-Content -Raw -Encoding UTF8 $profiles | ConvertFrom-Json
if (-not $launcher.profiles) {
	$launcher | Add-Member -Force -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{})
}
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$entry = [pscustomobject]@{
	name = $profileName
	type = "custom"
	icon = "Grass"
	lastVersionId = $versionId
	gameDir = $gameDir
	javaArgs = $javaArgs
	created = $now
	lastUsed = $now
}
$launcher.profiles | Add-Member -Force -NotePropertyName $profileId -NotePropertyValue $entry
[System.IO.File]::WriteAllText($profiles, ($launcher | ConvertTo-Json -Depth 32), $utf8)
Write-Host "Profile   -> '$profileName' in the Minecraft Launcher"

$shell = New-Object -ComObject WScript.Shell
$shortcut = Join-Path ([Environment]::GetFolderPath("Desktop")) "Bannerlord x Minecraft.lnk"
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$link.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $moduleTarget 'Play.ps1')`""
$link.WorkingDirectory = $moduleTarget
$link.IconLocation = (Join-Path $bin "Bannerlord.exe") + ",0"
$link.Description = "Start Minecraft and Bannerlord with the Minecraft Passthrough module"
$link.Save()
Write-Host "Shortcut  -> $shortcut"

Write-Host ""
Write-Host "Done. Start the game with the 'Bannerlord x Minecraft' shortcut on your desktop."
Write-Host "Kurulum tamam. Oyunu masaustundeki 'Bannerlord x Minecraft' kisayoluyla baslat."
