# Shan x Doubao side-by-side layout + auto-restore watcher (ASCII only)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public struct RECT{public int L,T,R,B;} public struct POINT{public int X,Y;} public struct WINDOWPLACEMENT{public int length,flags,showCmd;public POINT ptMin,ptMax;public RECT rcNormal;} public class W2{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);[DllImport("user32.dll")]public static extern bool GetWindowRect(IntPtr h,out RECT r);[DllImport("user32.dll")]public static extern bool IsIconic(IntPtr h);[DllImport("user32.dll")]public static extern bool GetWindowPlacement(IntPtr h,out WINDOWPLACEMENT p);}'
[W2]::SetProcessDPIAware() | Out-Null

$exe = "$env:LOCALAPPDATA\Doubao\Application\Doubao.exe"
$save = "$env:TEMP\shan-layout.txt"
$dsave = "$env:TEMP\shan-doubao.txt"

# Shan must be running. Claim the session BEFORE launching Doubao, so a leftover
# phase-2 watcher from the previous session sees the file and cancels (it would
# otherwise fight this layout by dragging Doubao back to its old position).
$sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $sp) { exit 2 }

# save current Shan geometry (keep the TRUE original if already tiled).
# if Shan is minimized its rect is garbage(-32000): un-minimize first, then measure.
if (-not (Test-Path $save)) {
    if ([W2]::IsIconic($sp.MainWindowHandle)) {
        [W2]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null
        Start-Sleep -Milliseconds 400
    }
    $r = New-Object RECT
    [W2]::GetWindowRect($sp.MainWindowHandle, [ref]$r) | Out-Null
    if ($r.L -gt -5000 -and $r.T -gt -5000 -and ($r.R - $r.L) -gt 300) {
        "$($r.L)|$($r.T)|$($r.R - $r.L)|$($r.B - $r.T)" | Set-Content $save -Encoding ASCII
    }
}

# launch Doubao if not running / windowless in tray: start exe to nudge window
$dp = Get-Process Doubao -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $dp) {
    Start-Process $exe
    Start-Sleep -Milliseconds 1500
}
for ($i = 0; $i -lt 30; $i++) {
    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($dp) { break }
    if ($i -eq 4) { Start-Process $exe }
    Start-Sleep -Milliseconds 400
}
if (-not $dp) { exit 1 }

# save Doubao's pre-tile geometry (keep the TRUE original if already tiled).
# GetWindowPlacement gives the true restore geometry even while minimized.
# reject: the right-half tile spot itself, maximized state (showCmd=3) and
# near-fullscreen rects (that is a state, not a position).
if (-not (Test-Path $dsave)) {
    $wp = New-Object WINDOWPLACEMENT
    $wp.length = [System.Runtime.InteropServices.Marshal]::SizeOf($wp)
    [W2]::GetWindowPlacement($dp.MainWindowHandle, [ref]$wp) | Out-Null
    $qL = $wp.rcNormal.L; $qT = $wp.rcNormal.T
    $qW = $wp.rcNormal.R - $wp.rcNormal.L; $qH = $wp.rcNormal.B - $wp.rcNormal.T
    $wa = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    $whalf = [int]($wa.Width / 2)
    $tiled = [Math]::Abs($qL - ($wa.X + $whalf + 6)) -lt 60 -and
             [Math]::Abs($qW - ($whalf - 12)) -lt 80
    if (-not $tiled -and $qL -gt -5000 -and $qW -gt 300 -and
        $qW -lt ($wa.Width * 0.92) -and $wp.showCmd -ne 3) {
        "$qL|$qT|$qW|$qH" | Set-Content $dsave -Encoding ASCII
    }
}

# arrange: Shan left half, Doubao right half
$area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$half = [int]($area.Width / 2)
[W2]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null
[W2]::MoveWindow($sp.MainWindowHandle, $area.X, $area.Y, $half - 6, $area.Height, $true) | Out-Null
# SW_SHOWNORMAL(1): un-minimize AND un-maximize Doubao, else MoveWindow cannot
# tile a maximized window (it just stays fullscreen)
[W2]::ShowWindow($dp.MainWindowHandle, 1) | Out-Null
Start-Sleep -Milliseconds 300
[W2]::MoveWindow($dp.MainWindowHandle, $area.X + $half + 6, $area.Y, $half - 12, $area.Height, $true) | Out-Null
[W2]::SetForegroundWindow($dp.MainWindowHandle) | Out-Null

# detach the restore watcher (survives this script's exit)
Start-Process powershell -WindowStyle Hidden -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', "$env:TEMP\shan-restore-doubao.ps1"
)
