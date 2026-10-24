$ErrorActionPreference = "Stop"
$module = $PSScriptRoot
$bannerlord = Split-Path -Parent (Split-Path -Parent $module)
$bin = Join-Path $bannerlord "bin\Win64_Shipping_Client"
$Host.UI.RawUI.WindowTitle = "Bannerlord x Minecraft"
Add-Type -AssemblyName PresentationFramework

function Fail([string]$message) {
	[System.Windows.MessageBox]::Show($message, "Bannerlord x Minecraft") | Out-Null
	exit 1
}

function Find-Minecraft {
	Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
}

function Start-Launcher {
	foreach ($exe in @("${env:ProgramFiles(x86)}\Minecraft Launcher\MinecraftLauncher.exe", "$env:ProgramFiles\Minecraft Launcher\MinecraftLauncher.exe")) {
		if (Test-Path $exe) {
			Start-Process $exe
			return $true
		}
	}
	$app = Get-StartApps -ErrorAction SilentlyContinue | Where-Object { $_.Name -eq "Minecraft Launcher" } | Select-Object -First 1
	if ($app) {
		Start-Process "shell:AppsFolder\$($app.AppID)"
		return $true
	}
	return $false
}

function Get-Modules {
	$official = @("Native", "SandBoxCore", "BirthAndDeath", "CustomBattle", "Sandbox", "StoryMode", "NavalDLC")
	$selected = $official | Where-Object { Test-Path (Join-Path $bannerlord "Modules\$_\SubModule.xml") }
	return "_MODULES_*" + ((@($selected) + "MinecraftPassthrough") -join "*") + "*_MODULES_"
}

$version = ""
try { $version = ([xml](Get-Content -Raw (Join-Path $bin "Version.xml"))).Version.Singleplayer.Value } catch {}
if ($version -and $version -ne "v1.4.8") {
	Fail "This mod needs Bannerlord v1.4.8, but $version is installed. In Steam: Bannerlord > Properties > Betas > None, let it update, then try again.`n`nBu mod Bannerlord v1.4.8 istiyor, sende $version kurulu. Steam'de: Bannerlord > Ozellikler > Betalar > Yok, guncellemesini bekle, sonra tekrar dene."
}

if (Get-Process Bannerlord -ErrorAction SilentlyContinue) {
	Fail "Bannerlord is already running. Close it, then use this shortcut again.`n`nBannerlord zaten acik. Kapatip bu kisayolu tekrar kullan."
}

if (-not (Find-Minecraft)) {
	if (-not (Start-Launcher)) {
		Fail "The Minecraft Launcher was not found. Install it from minecraft.net, then try again.`n`nMinecraft Launcher bulunamadi. minecraft.net'ten kurup tekrar dene."
	}
	Write-Host ""
	Write-Host "  In the Minecraft Launcher, pick the 'Bannerlord x Minecraft' profile and press PLAY."
	Write-Host "  Bannerlord starts by itself once Minecraft is open."
	Write-Host ""
	Write-Host "  Minecraft Launcher'da 'Bannerlord x Minecraft' profilini secip OYNA'ya bas."
	Write-Host "  Minecraft acilinca Bannerlord kendiliginden baslar."
	Write-Host ""
	for ($i = 0; $i -lt 900 -and -not (Find-Minecraft); $i++) { Start-Sleep 1 }
	if (-not (Find-Minecraft)) {
		Fail "Minecraft did not start within 15 minutes.`n`nMinecraft 15 dakika icinde acilmadi."
	}
	Write-Host "  Minecraft is open. Starting Bannerlord... / Minecraft acik. Bannerlord baslatiliyor..."
	Start-Sleep 5
}

Start-Process -FilePath (Join-Path $bin "Bannerlord.exe") -WorkingDirectory $bin -ArgumentList "/singleplayer $(Get-Modules)"
