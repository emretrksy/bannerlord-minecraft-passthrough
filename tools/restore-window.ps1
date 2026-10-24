param([Parameter(Mandatory = $true)][string]$Process)
Add-Type @"
using System; using System.Runtime.InteropServices;
public static class RestoreWin {
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr h, int cmd);
}
"@
foreach ($p in Get-Process -Name $Process -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }) {
  if ([RestoreWin]::IsIconic($p.MainWindowHandle)) {
    [RestoreWin]::ShowWindowAsync($p.MainWindowHandle, 4) | Out-Null
    "restored $($p.Id)"
  }
}
