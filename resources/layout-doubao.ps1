# Shan x Doubao side-by-side layout + auto-restore watcher (ASCII only)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public struct RECT{public int L,T,R,B;} public class W2{[DllImport("user32.dll")]public static extern bool SetProcessDPIAware();[DllImport("user32.dll")]public static extern bool ShowWindow(IntPtr h,int n);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool MoveWindow(IntPtr h,int x,int y,int w,int hh,bool p);[DllImport("user32.dll")]public static extern bool GetWindowRect(IntPtr h,out RECT r);}'
[W2]::SetProcessDPIAware() | Out-Null

$exe = "$env:LOCALAPPDATA\Doubao\Application\Doubao.exe"
$save = "$env:TEMP\shan-layout.txt"

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

$sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $sp) { exit 2 }

# save current Shan geometry (keep the TRUE original if already tiled)
if (-not (Test-Path $save)) {
    $r = New-Object RECT
    [W2]::GetWindowRect($sp.MainWindowHandle, [ref]$r) | Out-Null
    "$($r.L)|$($r.T)|$($r.R - $r.L)|$($r.B - $r.T)" | Set-Content $save -Encoding ASCII
}

# arrange: Shan left half, Doubao right half
$area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$half = [int]($area.Width / 2)
[W2]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null
[W2]::MoveWindow($sp.MainWindowHandle, $area.X, $area.Y, $half - 6, $area.Height, $true) | Out-Null
[W2]::ShowWindow($dp.MainWindowHandle, 9) | Out-Null
[W2]::MoveWindow($dp.MainWindowHandle, $area.X + $half + 6, $area.Y, $half - 12, $area.Height, $true) | Out-Null
[W2]::SetForegroundWindow($dp.MainWindowHandle) | Out-Null

# detach the restore watcher (survives this script's exit)
Start-Process powershell -WindowStyle Hidden -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', "$env:TEMP\shan-restore-doubao.ps1"
)
