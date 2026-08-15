# autoTask 真机冒烟测试脚本（M7 用）
# 用法：.\scripts\smoke-test.ps1  （在项目根目录执行）
# 依赖：设备已开启 USB 调试；SDK platform-tools 位于 .tools/android/platform-tools

$ErrorActionPreference = "Stop"
$adb = "$PSScriptRoot\..\.tools\android\platform-tools\adb.exe"
$apk = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk"
$PKG = "com.autotask.app"

if (-not (Test-Path $apk)) {
    Write-Host "[-] 未找到 debug APK，请先构建：.\gradlew.bat assembleDebug"
    exit 1
}

Write-Host "[*] 等待设备连接..."
& $adb wait-for-device
$devices = & $adb devices
if (($devices | Select-String "device$") -eq $null) {
    Write-Host "[-] 未检测到设备，请检查 USB 调试"
    exit 1
}

Write-Host "[*] 安装 APK..."
& $adb install -r $apk

Write-Host "[*] 启动 App..."
& $adb shell am start -n "$PKG/.MainActivity"
Start-Sleep -Seconds 3

Write-Host "[*] 检查前台服务..."
& $adb shell dumpsys activity services $PKG | Select-String "KeeperService|isForeground"

Write-Host "[*] 检查闹钟注册..."
& $adb shell dumpsys alarm | Select-String $PKG | Select-Object -First 3

Write-Host "[*] 检查进程存活..."
& $adb shell pidof $PKG

Write-Host "[*] 截图保存 smoke.png..."
& $adb exec-out screencap -p > "$PSScriptRoot\..\smoke.png"

Write-Host "[*] 抓取崩溃日志（若有）..."
& $adb logcat -d -s AndroidRuntime:E | Select-Object -Last 20

Write-Host "[+] 冒烟测试完成，截图见 smoke.png"
