@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "PROJECT_ROOT=%~dp0"
title SkyBarech ERP Complete Build

echo ============================================================
echo   SkyBarech ERP - Build Desktop and Android
echo ============================================================
echo.
echo First the Windows setup will be built, then the Android APK.
echo.

call "%PROJECT_ROOT%01-Desktop-ERP\BUILD_WINDOWS_INSTALLER.bat"
if errorlevel 1 goto :failed

call "%PROJECT_ROOT%02-Android-Client\BUILD_ANDROID_APK.bat"
if errorlevel 1 goto :failed

echo.
echo ============================================================
echo   BOTH BUILDS COMPLETED SUCCESSFULLY
echo ============================================================
echo Desktop: Open 01-Desktop-ERP\dist for the versioned setup file.
echo Android: Open 02-Android-Client\APK_OUTPUT for the generated APK.
pause
exit /b 0

:failed
echo.
echo One of the builds failed. Open that client's BUILD_LOGS folder.
pause
exit /b 1
