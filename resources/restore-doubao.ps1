# Watcher: when the Doubao window disappears (closed, to tray) OR is minimized,
# restore Shan to its saved geometry. Exits after restoring once.
# Trace log: %TEMP%\shan-watch.log
$save = "$env:TEMP\shan-layout.txt"
$log  = "$env:TEMP\shan-watch.log"
if (-not (Test-Path $save)) { exit 0 }

"watch start " + (Get-Date -Format "HH:mm:ss") | Add-Content $log

Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public class W3{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);[DllImport("user32.dll")]public static extern bool IsIconic(IntPtr h);}'
[W3]::SetProcessDPIAware() | Out-Null

$iconicStreak = 0
for (;;) {
    Start-Sleep -Milliseconds 900
    if (-not (Test-Path $save)) { exit 0 }   # another watcher already restored

    $sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $sp) { Remove-Item $save -ErrorAction SilentlyContinue; "shan gone, cancel " + (Get-Date -Format "HH:mm:ss") | Add-Content $log; exit 0 }

    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1

    $trigger = ""
    if (-not $dp) {
        $trigger = "closed"
    } elseif ([W3]::IsIconic($dp.MainWindowHandle)) {
        $iconicStreak++
        if ($iconicStreak -ge 2) { $trigger = "minimized" }   # 两连判, 防抖
    } else {
        $iconicStreak = 0
    }

    if ($trigger -ne "") {
        $p = (Get-Content $save) -split '\|'
        [W3]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null   # un-minimize first
        [W3]::MoveWindow($sp.MainWindowHandle, [int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3], $true) | Out-Null
        [W3]::SetForegroundWindow($sp.MainWindowHandle) | Out-Null
        Remove-Item $save -ErrorAction SilentlyContinue
        "restored ($trigger) to " + $p[0] + "," + $p[1] + " at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log
        exit 0
    }
}
