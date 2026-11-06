# Watcher: when the Doubao window disappears (closed or to tray),
# restore Shan to its saved geometry. Exits after restoring once.
$save = "$env:TEMP\shan-layout.txt"
if (-not (Test-Path $save)) { exit 0 }

Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public class W3{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);}'
[W3]::SetProcessDPIAware() | Out-Null

for (;;) {
    Start-Sleep -Milliseconds 900
    $sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $sp) { Remove-Item $save -ErrorAction SilentlyContinue; exit 0 }   # Shan closed

    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $dp) {
        $p = (Get-Content $save) -split '\|'
        [W3]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null   # 先解除最小化再移回
        [W3]::MoveWindow($sp.MainWindowHandle, [int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3], $true) | Out-Null
        [W3]::SetForegroundWindow($sp.MainWindowHandle) | Out-Null
        Remove-Item $save -ErrorAction SilentlyContinue
        exit 0
    }
}
