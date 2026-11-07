# Watcher phase 1: when the Doubao window disappears (closed, to tray) OR is
# minimized, restore Shan to its saved geometry.
# Phase 2: make sure Doubao itself does NOT keep the tiled right-half position.
# Doubao (Electron) remembers window bounds and reopens there; it also ignores
# external moves while minimized. So we wait for Doubao to be VISIBLE (user
# reopens/un-minimizes it, or it relaunches), then MoveWindow it back to its
# pre-tile position (or centered) exactly once. Trace: %TEMP%\shan-watch.log
$save = "$env:TEMP\shan-layout.txt"
$dsave = "$env:TEMP\shan-doubao.txt"
$log  = "$env:TEMP\shan-watch.log"
if (-not (Test-Path $save)) { exit 0 }

"watch start " + (Get-Date -Format "HH:mm:ss") | Add-Content $log

Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public struct RECT{public int L,T,R,B;} public struct POINT{public int X,Y;} public struct WINDOWPLACEMENT{public int length,flags,showCmd;public POINT ptMin,ptMax;public RECT rcNormal;} public class W3{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);[DllImport("user32.dll")]public static extern bool GetWindowRect(IntPtr h,out RECT r);[DllImport("user32.dll")]public static extern bool IsIconic(IntPtr h);[DllImport("user32.dll")]public static extern bool GetWindowPlacement(IntPtr h,out WINDOWPLACEMENT p);}'
[W3]::SetProcessDPIAware() | Out-Null

# target geometry for Doubao: its saved pre-tile position, else centered.
# never "restore" it to the tiled right-half spot or a maximized/near-fullscreen
# rect (stale or state-not-position saves are rejected).
function Get-DoubaoTarget {
    $wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    $half = [int]($wa.Width / 2)
    if (Test-Path $dsave) {
        $p = (Get-Content $dsave) -split '\|'
        if ($p.Count -eq 4) {
            $tiled = [Math]::Abs([int]$p[0] - ($wa.X + $half + 6)) -lt 60 -and
                     [Math]::Abs([int]$p[2] - ($half - 12)) -lt 80
            if (-not $tiled -and [int]$p[2] -gt 300 -and [int]$p[2] -lt ($wa.Width * 0.92)) {
                return @([int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3])
            }
        }
    }
    $w = [int][Math]::Min(1600, [Math]::Max(1000, $wa.Width * 0.55))
    $h = [int][Math]::Min(1100, [Math]::Max(700, $wa.Height * 0.75))
    return @([int](($wa.Width - $w) / 2), [int]($wa.Y + ($wa.Height - $h) / 2), $w, $h)
}

# move a VISIBLE Doubao window and verify it stuck. NB: only moves made while
# the window is visible are honored by Doubao (Electron re-asserts its own
# bounds otherwise), so callers must ensure the window is not minimized.
function Set-DoubaoPos([IntPtr]$h) {
    $t = Get-DoubaoTarget
    for ($k = 0; $k -lt 3; $k++) {
        [W3]::MoveWindow($h, $t[0], $t[1], $t[2], $t[3], $true) | Out-Null
        Start-Sleep -Milliseconds 400
        $r = New-Object RECT
        [W3]::GetWindowRect($h, [ref]$r) | Out-Null
        if ([Math]::Abs($r.L - $t[0]) -lt 20 -and [Math]::Abs($r.T - $t[1]) -lt 20) { return $true }
    }
    return $false
}

# ---- Phase 1: Doubao closed / to tray / minimized -> restore Shan ----
$iconicStreak = 0
for (;;) {
    Start-Sleep -Milliseconds 250
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
        Start-Sleep -Milliseconds 150; $dp2 = Get-Process Doubao -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1; if ($dp2 -and [W3]::IsIconic($dp2.MainWindowHandle)) { $trigger = "minimized" }   # 快速复核防抖
    } else {
        $iconicStreak = 0
    }

    if ($trigger -ne "") {
        $p = (Get-Content $save) -split '\|'
        [W3]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null   # un-minimize first
        [W3]::MoveWindow($sp.MainWindowHandle, [int]$p[0], [int]$p[1], [int]$p[2], [int]$p[3], $true) | Out-Null
        [W3]::SetForegroundWindow($sp.MainWindowHandle) | Out-Null
        Remove-Item $save -ErrorAction SilentlyContinue
        "restored ($trigger), waiting to fix doubao position at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log
        break   # go to phase 2
    }
}

# ---- Phase 2: Doubao kept the tiled bounds (Electron remembers on exit). ----
# Wait until the Doubao window is VISIBLE again (relaunch or un-minimize),
# then move it back exactly once. Bounded ~10 minutes.
$deadline = (Get-Date).AddMinutes(10)
for (;;) {
    if ((Get-Date) -gt $deadline) {
        Remove-Item $dsave -ErrorAction SilentlyContinue
        "phase2 timeout, give up at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log
        exit 0
    }
    Start-Sleep -Milliseconds 250
    if (Test-Path $save) { "phase2 cancelled (new session) at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log; exit 0 }

    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($dp -and -not [W3]::IsIconic($dp.MainWindowHandle)) {
        Start-Sleep -Milliseconds 1200   # let the app finish restoring its saved bounds
        $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
              Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
        if ($dp -and -not [W3]::IsIconic($dp.MainWindowHandle) -and -not (Test-Path $save)) {
            Set-DoubaoPos $dp.MainWindowHandle | Out-Null
            # one delayed re-check: the app may apply its own saved bounds a
            # couple of seconds after launch, overriding our earlier move
            Start-Sleep -Milliseconds 2000
            $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
                  Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
            if ($dp -and -not [W3]::IsIconic($dp.MainWindowHandle)) {
                $t = Get-DoubaoTarget
                $r = New-Object RECT
                [W3]::GetWindowRect($dp.MainWindowHandle, [ref]$r) | Out-Null
                if ([Math]::Abs($r.L - $t[0]) -gt 40 -or [Math]::Abs($r.T - $t[1]) -gt 40) {
                    [W3]::MoveWindow($dp.MainWindowHandle, $t[0], $t[1], $t[2], $t[3], $true) | Out-Null
                }
            }
            Remove-Item $dsave -ErrorAction SilentlyContinue
            "phase2 doubao repositioned at " + (Get-Date -Format "HH:mm:ss") | Add-Content $log
            exit 0
        }
    }
}
