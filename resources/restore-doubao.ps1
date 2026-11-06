# Watcher: when the Doubao window disappears (closed or to tray),
# restore Shan to its saved geometry. Exits after restoring once.
# Leaves a trace in %TEMP%\shan-watch.log for debugging.
$save = "$env:TEMP\shan-layout.txt"
$log  = "$env:TEMP\shan-watch.log"
if (-not (Test-Path $save)) { exit 0 }

"watch start " + (Get-Date -Format "HH:mm:ss") | Add-Content $log

Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public class W3{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);}'
[W3]::SetProcessDPIAware() | Out-Null

for (;;) {
    Start-Sleep -Milliseconds 900
    if (-not (Test-Path $save)) { exit 0 }   # another watcher already restored

    $sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $sp) { Remove-Item $save -ErrorAction SilentlyContinue; "shan gone, cancel " + (Get-Date -Format "HH:mm:ss") | Add-Content $log; exit 0 }

    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $dp) {
        $p = (Get-Content $save) -split '\|'
        [W3]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null   # un-minimize first
        [W3]::MoveWindow($sp.MainWindowHandle, [int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3], $true) | Out-Null
        [W3]::SetForegroundWindow($sp.MainWindowHandle) | Out-Null
        Remove-Item $save -ErrorAction SilentlyContinue
        "restored to " + $p[0] + "," + $p[1] + " at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log
        exit 0
    }
}
