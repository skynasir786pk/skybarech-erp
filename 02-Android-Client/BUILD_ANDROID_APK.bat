@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title SkyBarech ERP Android APK Builder

where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo ERROR: Windows PowerShell is not available.
  pause
  exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0BUILD_ANDROID_APK.ps1"
set "BUILD_EXIT=%ERRORLEVEL%"

if not "%BUILD_EXIT%"=="0" (
  echo.
  echo BUILD FAILED. The log file is inside BUILD_LOGS.
  pause
  exit /b %BUILD_EXIT%
)

echo.
echo BUILD COMPLETED SUCCESSFULLY.
pause
exit /b 0
