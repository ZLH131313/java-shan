# Shan x Doubao side-by-side layout (ASCII only for PS5.1 encoding safety)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -Namespace W -Name U -MemberDefinition '[DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n); [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool repaint);'
[W.U]::SetProcessDPIAware() | Out-Null

# launch Doubao if not running, OR if it is running but hiding in tray (no window handle):
# starting the exe again signals the single instance to show its window.
$exe = "$env:LOCALAPPDATA\Doubao\Application\Doubao.exe"
$dp = Get-Process Doubao -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $dp) {
    Start-Process $exe
    Start-Sleep -Milliseconds 1500
}

# wait up to 12s for a Doubao window (fresh launch takes a moment);
# if process exists but still windowless (tray), launch once more to nudge it
for ($i = 0; $i -lt 30; $i++) {
    $dp = Get-Process Doubao -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($dp) { break }
    if ($i -eq 4) { Start-Process $exe }
    Start-Sleep -Milliseconds 400
}
if (-not $dp) { exit 1 }

# our app window
$sp = Get-Process JavaShan -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1

$area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$half = [int]($area.Width / 2)

if ($sp) {
    [W.U]::ShowWindow($sp.MainWindowHandle, 9) | Out-Null   # SW_RESTORE
    [W.U]::MoveWindow($sp.MainWindowHandle, $area.X, $area.Y, $half - 6, $area.Height, $true) | Out-Null
}
[W.U]::ShowWindow($dp.MainWindowHandle, 9) | Out-Null
[W.U]::MoveWindow($dp.MainWindowHandle, $area.X + $half + 6, $area.Y, $half - 12, $area.Height, $true) | Out-Null
[W.U]::SetForegroundWindow($dp.MainWindowHandle) | Out-Null
