#!/usr/bin/env powershell

param(
    [string]$DeviceId = "emulator-5554",
    [string]$BackendHost = "chat.example.com",
    [string]$BuildType = "debug" # debug or release
)

Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║ FreeTime Complete Deployment & Test Script ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/4] Building Debug APK..." -ForegroundColor Yellow
Write-Host "────────────────────────────────────────────────────────────────" -ForegroundColor Gray

try {
    $buildResult = & .\gradlew clean assembleDebug 2>&1
    if ($buildResult -match "BUILD SUCCESSFUL") {
        Write-Host " Build successful" -ForegroundColor Green
    } else {
        Write-Host " Build failed" -ForegroundColor Red
        $buildResult | Select-Object -Last 20
        exit 1
    }
} catch {
    Write-Host " Build error: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

Write-Host "[2/4] Installing APK on emulator ($DeviceId)..." -ForegroundColor Yellow
Write-Host "────────────────────────────────────────────────────────────────" -ForegroundColor Gray

$apkPath = "app\build\outputs\apk\dev\debug\app-dev-debug.apk"

if (!(Test-Path $apkPath)) {
    Write-Host " APK not found at: $apkPath" -ForegroundColor Red
    exit 1
}

try {
    $adbPath = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    if (!(Test-Path $adbPath)) {
        $adbPath = "adb" # Use system PATH
    }

    $installResult = & $adbPath -s $DeviceId install -r $apkPath 2>&1

    if ($installResult -match "Success" -or $installResult[-1] -match "Success") {
        Write-Host " APK installed successfully" -ForegroundColor Green
    } else {
        Write-Host " Installation failed" -ForegroundColor Red
        $installResult | Select-Object -Last 10
        exit 1
    }
} catch {
    Write-Host " ADB error: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

Write-Host "[3/4] Checking backend connectivity..." -ForegroundColor Yellow
Write-Host "────────────────────────────────────────────────────────────────" -ForegroundColor Gray

try {
    $connection = New-Object System.Net.Sockets.TcpClient
    $connection.Connect($BackendHost, 443)

    if ($connection.Connected) {
        Write-Host " Backend port 443 is reachable" -ForegroundColor Green
        $connection.Close()
    } else {
        Write-Host " Backend port 443 is not responding" -ForegroundColor Yellow
        Write-Host " Make sure backend is running on Debian:" -ForegroundColor Yellow
        Write-Host " cd ~/master-server && sudo npm start" -ForegroundColor Yellow
    }
} catch {
    Write-Host " Cannot connect to backend: $_" -ForegroundColor Yellow
    Write-Host " Make sure backend is running on Debian:" -ForegroundColor Yellow
    Write-Host " cd ~/master-server && sudo npm start" -ForegroundColor Yellow
}

Write-Host ""

Write-Host "[4/4] Launching app and monitoring logs..." -ForegroundColor Yellow
Write-Host "────────────────────────────────────────────────────────────────" -ForegroundColor Gray

try {
    $adbPath = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    if (!(Test-Path $adbPath)) {
        $adbPath = "adb"
    }

    & $adbPath -s $DeviceId shell am start -n com.freetime.app.debug/com.freetime.app.MainActivity
    Write-Host " App launched" -ForegroundColor Green

    Start-Sleep -Seconds 3

    Write-Host ""
    Write-Host " Monitoring logs (Ctrl+C to stop)..." -ForegroundColor Yellow
    Write-Host "Looking for: SocketIO_Diagnostics, FREETIME_HOME, API_SERVICE errors" -ForegroundColor Gray
    Write-Host ""

    & $adbPath -s $DeviceId logcat | Select-String "SocketIO_Diagnostics|FREETIME_HOME|API_SERVICE|FREETIME_CHAT|Online|Offline|Connected|connection|error" -ErrorAction SilentlyContinue

} catch {
    Write-Host " Error: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host " Deployment Complete" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Check app shows 'Online' status" -ForegroundColor Gray
Write-Host "2. Try sending a message" -ForegroundColor Gray
Write-Host "3. Test invite links in group settings" -ForegroundColor Gray
Write-Host "4. Monitor backend logs on Debian" -ForegroundColor Gray
Write-Host ""
