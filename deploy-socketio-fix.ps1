#!/usr/bin/env powershell

param(
    [Parameter(Mandatory=$true)]
    [string]$Host,

    [Parameter(Mandatory=$true)]
    [string]$User,

    [Parameter(Mandatory=$false)]
    [string]$KeyPath,

    [Parameter(Mandatory=$false)]
    [SecureString]$Password,

    [Parameter(Mandatory=$false)]
    [string]$ProjectRoot = "C:\Users\leona\Desktop\FreeTime_project\FreeTime",

    [Parameter(Mandatory=$false)]
    [switch]$SkipBackup,

    [Parameter(Mandatory=$false)]
    [switch]$NoRestart
)

$Green = [System.ConsoleColor]::Green
$Red = [System.ConsoleColor]::Red
$Yellow = [System.ConsoleColor]::Yellow
$Cyan = [System.ConsoleColor]::Cyan

function Write-Success {
    Write-Host " $($args[0])" -ForegroundColor $Green
}

function Write-Error {
    Write-Host " $($args[0])" -ForegroundColor $Red
}

function Write-Warning {
    Write-Host " $($args[0])" -ForegroundColor $Yellow
}

function Write-Info {
    Write-Host "ℹ $($args[0])" -ForegroundColor $Cyan
}

if ([string]::IsNullOrEmpty($KeyPath) -and $null -eq $Password) {
    Write-Error "Either -KeyPath or -Password must be provided"
    exit 1
}

if ($null -ne $Password) {
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($Password)
    $PlainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringUni($ptr)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeCoTaskMemUnicode($ptr)
}

if (-not (Test-Path "$ProjectRoot\SecureChatApp")) {
    Write-Error "Project not found at $ProjectRoot"
    exit 1
}

$MasterServerPath = "$ProjectRoot\SecureChatApp\master-server"
$SocketIOServerFile = "$MasterServerPath\websocket\socket-io-server.js"
$ApiServerFile = "$MasterServerPath\api\master-server-api.js"

if (-not (Test-Path $SocketIOServerFile)) {
    Write-Error "Socket.IO server file not found: $SocketIOServerFile"
    exit 1
}

if (-not (Test-Path $ApiServerFile)) {
    Write-Error "API server file not found: $ApiServerFile"
    exit 1
}

Write-Info "================================"
Write-Info "FreeTime Master-Server Deployment"
Write-Info "Socket.IO Connection Fix"
Write-Info "================================"
Write-Info ""

$SCPBaseCmd = "scp"
if ($KeyPath) {
    Write-Info "Using SSH key: $KeyPath"
    $SCPBaseCmd = "scp -i `"$KeyPath`""
} else {
    Write-Warning "Using password authentication (less secure)"
}

$SSHDest = "$($User)@$($Host)"

Write-Info ""
Write-Info "DEPLOYMENT PLAN:"
Write-Info "1. Backup current master-server on Debian"
Write-Info "2. Upload updated socket-io-server.js"
Write-Info "3. Upload updated master-server-api.js"
Write-Info "4. Restart services"
Write-Info ""

$Confirm = Read-Host "Continue with deployment? (y/n)"
if ($Confirm -ne 'y' -and $Confirm -ne 'Y') {
    Write-Info "Deployment cancelled"
    exit 0
}

Write-Info ""
Write-Info "Starting deployment..."
Write-Info ""

if (-not $SkipBackup) {
    Write-Info "Step 1: Backing up current master-server on Debian..."
    $BackupCmd = "cd /root && tar -czf master-server-backup-`$(date +%Y%m%d-%H%M%S).tar.gz master-server/ && echo 'Backup complete'"

    if ($KeyPath) {
        $Result = ssh -i "$KeyPath" "$SSHDest" $BackupCmd 2>&1
    } else {
        $Result = ssh "$SSHDest" $BackupCmd 2>&1
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Backup created on Debian"
    } else {
        Write-Warning "Backup may have failed: $Result"
    }
} else {
    Write-Info "Skipping backup (--SkipBackup flag set)"
}

Write-Info ""

Write-Info "Step 2: Uploading socket-io-server.js..."
$Cmd = "& $SCPBaseCmd `"$SocketIOServerFile`" `"$($SSHDest):/root/master-server/websocket/`""
Write-Debug "Running: $Cmd"

Invoke-Expression $Cmd
if ($LASTEXITCODE -eq 0) {
    Write-Success "socket-io-server.js uploaded"
} else {
    Write-Error "Failed to upload socket-io-server.js"
    exit 1
}

Write-Info ""

Write-Info "Step 3: Uploading master-server-api.js..."
$Cmd = "& $SCPBaseCmd `"$ApiServerFile`" `"$($SSHDest):/root/master-server/api/`""
Write-Debug "Running: $Cmd"

Invoke-Expression $Cmd
if ($LASTEXITCODE -eq 0) {
    Write-Success "master-server-api.js uploaded"
} else {
    Write-Error "Failed to upload master-server-api.js"
    exit 1
}

Write-Info ""

if (-not $NoRestart) {
    Write-Info "Step 4: Restarting services on Debian..."

    $RestartCmd = "cd /root/master-server && pkill -f 'node.*master-server-api.js' ; sleep 2 ; ./start-all.sh"

    Write-Info "Sending restart command (this may take 10-20 seconds)..."

    if ($KeyPath) {
        $Result = ssh -i "$KeyPath" "$SSHDest" $RestartCmd 2>&1
    } else {
        $Result = ssh "$SSHDest" $RestartCmd 2>&1
    }

    Write-Info "Restart output:"
    Write-Info $Result

    Write-Info ""
    Write-Info "Services should be restarting... giving them 5 seconds to start"
    Start-Sleep -Seconds 5
} else {
    Write-Info "Skipping service restart (--NoRestart flag set)"
    Write-Warning "Remember to manually restart the services on Debian!"
}

Write-Info ""

Write-Info "Step 5: Verifying deployment..."

$VerifyCmd = "netstat -tlnp 2>/dev/null | grep 443 && echo 'OK'"

if ($KeyPath) {
    $Result = ssh -i "$KeyPath" "$SSHDest" $VerifyCmd 2>&1
} else {
    $Result = ssh "$SSHDest" $VerifyCmd 2>&1
}

if ($Result -match "LISTEN") {
    Write-Success "Master-server is listening on port 443"
} else {
    Write-Warning "Could not verify port 443 is listening"
    Write-Info "Check Debian server logs manually: ssh $SSHDest 'tail -f /root/master-server/logs/api-server.log'"
}

Write-Info ""
Write-Success "================================"
Write-Success "Deployment Complete!"
Write-Success "================================"
Write-Info ""
Write-Info "Next steps:"
Write-Info "1. Install new APK on Android emulator/device"
Write-Info "2. Monitor logcat for Socket.IO connection:"
Write-Info " adb logcat | grep -E 'SocketIOManager|CONNECTED'"
Write-Info "3. Test incoming call notification"
Write-Info "4. Test message delivery"
Write-Info ""
Write-Info "To monitor Debian logs:"
Write-Info " ssh -i `"$KeyPath`" $SSHDest 'tail -f /root/master-server/logs/api-server.log | grep Socket.IO'"
Write-Info ""
Write-Info "Full deployment guide: $ProjectRoot\DEPLOY_SOCKET_IO_FIX.md"
