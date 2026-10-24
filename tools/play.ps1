param(
	[string]$BannerlordDir = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord"
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$mod = Join-Path $repo "minecraft-mod"
$log = Join-Path $repo ".tools\minecraft.log"
$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"
Add-Type -AssemblyName PresentationFramework

function Fail([string]$message) {
	[System.Windows.MessageBox]::Show($message, "Bannerlord x Minecraft") | Out-Null
	exit 1
}

if (-not (Test-Path (Join-Path $BannerlordDir "Modules\MinecraftPassthrough"))) {
	& (Join-Path $PSScriptRoot "install.ps1") -BannerlordDir $BannerlordDir | Out-Null
}
if (-not (Test-Path "$mod\build\loom-cache\argFiles\runClient")) {
	Fail "The Minecraft mod has not been built yet. See README: Building."
}
if (-not (Get-Content -Raw "$mod\build\loom-cache\argFiles\runClient").Replace('" "', ' ').Contains("$mod\build")) {
	Fail "The Minecraft mod's launch files point to an old folder. Run 'gradlew runClient' once in $mod, then try again."
}

function Start-Minecraft {
	$java = (Get-ChildItem (Join-Path $repo ".tools\jdk") -Directory | Select-Object -First 1).FullName + "\bin\java.exe"
	$javaArgs = @(
		"--enable-native-access=ALL-UNNAMED", "-XX:ErrorFile=$repo\.tools\hs_err_%p.log", "-XX:TieredStopAtLevel=1",
		"-Xmx1500M", "-Dmax.bg.threads=4", "-Dblpassthrough.preload=true",
		"-Dfabric.dli.config=$mod\.gradle\loom-cache\launch.cfg", "-Dfabric.dli.env=client",
		"-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient",
		"@$mod\build\loom-cache\argFiles\runClient", "net.fabricmc.devlaunchinjector.Main"
	) | ForEach-Object { if ($_ -match ' ') { '"' + $_ + '"' } else { $_ } }
	$process = Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory "$mod\run" -WindowStyle Hidden `
		-RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru

	for ($i = 0; $i -lt 180; $i++) {
		Start-Sleep 1
		& (Join-Path $PSScriptRoot "restore-window.ps1") -Process java | Out-Null
		if ($process.HasExited) {
			return $null
		}
		if ((Test-Path $log) -and (Select-String -Path $log -Pattern "Sound engine started" -Quiet)) {
			return $process
		}
	}
	return $null
}

$minecraft = Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
if (-not $minecraft) {
	$minecraft = Start-Minecraft
	if (-not $minecraft) {
		Fail "Minecraft did not start. Its log is in $log"
	}
}

if (-not (Get-Process Bannerlord -ErrorAction SilentlyContinue)) {
	Start-Process -FilePath (Join-Path $bin "Bannerlord.exe") -WorkingDirectory $bin `
		-ArgumentList '/singleplayer _MODULES_*Native*SandBoxCore*CustomBattle*SandBox*StoryMode*MinecraftPassthrough*_MODULES_' | Out-Null
}

$restarts = 0
while (Get-Process Bannerlord -ErrorAction SilentlyContinue) {
	if ($minecraft.HasExited -and $restarts -lt 5) {
		$restarts++
		$minecraft = Start-Minecraft
		if (-not $minecraft) {
			Fail "Minecraft closed and could not be restarted. Its log is in $log"
		}
	}
	& (Join-Path $PSScriptRoot "restore-window.ps1") -Process java | Out-Null
	Start-Sleep 2
}
