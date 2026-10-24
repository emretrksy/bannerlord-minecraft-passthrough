param(
	[string]$BannerlordDir = "C:\Program Files (x86)\Steam\steamapps\common\Mount & Blade II Bannerlord",
	[string]$Destination = [Environment]::GetFolderPath("Desktop")
)
$ErrorActionPreference = "Stop"
$play = Join-Path $PSScriptRoot "play.ps1"
$shortcut = Join-Path $Destination "Bannerlord x Minecraft.lnk"

$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$link.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$play`" -BannerlordDir `"$BannerlordDir`""
$link.WorkingDirectory = Split-Path -Parent $PSScriptRoot
$link.IconLocation = (Join-Path $BannerlordDir "bin\Win64_Shipping_Client\Bannerlord.exe") + ",0"
$link.Description = "Start Minecraft with the passthrough mod, then Bannerlord with Minecraft Passthrough"
$link.Save()
Write-Host "Shortcut created: $shortcut"
