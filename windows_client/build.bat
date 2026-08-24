@echo off
echo ===================================
echo  FreeTime Windows Build Script
echo ===================================
echo.

python --version >nul 2>&1
if errorlevel 1 (
    echo Python is not installed or not in PATH
    pause
    exit /b 1
)

echo Installing dependencies...
pip install -r requirements.txt
if errorlevel 1 (
    echo Failed to install dependencies
    pause
    exit /b 1
)

echo.
echo Building Windows EXE with PyInstaller...
echo.

pyinstaller ^
    --noconfirm ^
    --onefile ^
    --windowed ^
    --name "FreeTime" ^
    --icon "icon.ico" ^
    --add-data "insta_logo.png;." ^
    --paths "." ^
    --hidden-import PyQt6.sip ^
    --hidden-import websocket ^
    --hidden-import cryptography ^
    --hidden-import pyotp ^
    --hidden-import qrcode ^
    --hidden-import PIL ^
    main.py

if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo ===================================
echo  Build successful!
echo  EXE located at: dist\FreeTime.exe
echo ===================================
echo.
pause
