param(
	[string]$BannerlordDir = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord",
	[switch]$NoAutostart
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$mod = Join-Path $repo "minecraft-mod"
$log = Join-Path $repo ".tools\runClient.log"

Stop-Process -Name Bannerlord -Force -ErrorAction SilentlyContinue
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Stop-Process -Force
for ($i = 0; $i -lt 60 -and (Get-Process Bannerlord -ErrorAction SilentlyContinue); $i++) { Start-Sleep 1 }
Start-Sleep 3
& (Join-Path $PSScriptRoot "install.ps1") -BannerlordDir $BannerlordDir | Out-Null

$java = (Get-ChildItem (Join-Path $repo ".tools\jdk") -Directory | Select-Object -First 1).FullName + "\bin\java.exe"
$javaArgs = @(
	"--enable-native-access=ALL-UNNAMED", "-XX:ErrorFile=$repo\.tools\hs_err_%p.log", "-Xmx1500M", "-Dmax.bg.threads=4", "-Dblpassthrough.debug=true", "-Dblpassthrough.devtest=true",
	"-Dfabric.dli.config=$mod\.gradle\loom-cache\launch.cfg", "-Dfabric.dli.env=client",
	"-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient",
	"@$mod\build\loom-cache\argFiles\runClient", "net.fabricmc.devlaunchinjector.Main"
) | ForEach-Object { if ($_ -match ' ') { '"' + $_ + '"' } else { $_ } }
Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory "$mod\run" -RedirectStandardOutput $log -RedirectStandardError "$log.err" | Out-Null

for ($i = 0; $i -lt 120; $i++) {
	Start-Sleep 1
	& (Join-Path $PSScriptRoot "restore-window.ps1") -Process java | Out-Null
	if ((Test-Path $log) -and (Select-String -Path $log -Pattern "Sound engine started" -Quiet)) { break }
}
Start-Sleep 10

$bin = Join-Path $BannerlordDir "bin\Win64_Shipping_Client"
if (-not $NoAutostart) { $env:BLMC_AUTOSTART = "1" }
$p = Start-Process -FilePath (Join-Path $bin "Bannerlord.exe") -WorkingDirectory $bin -PassThru `
	-ArgumentList '/singleplayer _MODULES_*Native*SandBoxCore*CustomBattle*SandBox*StoryMode*MinecraftPassthrough*_MODULES_'
"bannerlord pid $($p.Id)"
