@echo off
setlocal EnableExtensions
title SkyBarech ERP - Gradle Loopback Repair

net session >nul 2>&1
if not "%errorlevel%"=="0" (
  echo.
  echo This repair needs Administrator permission.
  echo Right-click this file and choose Run as administrator.
  echo.
  pause
  exit /b 1
)

echo.
echo Repairing Windows network sockets used by Gradle...
netsh winsock reset
if not "%errorlevel%"=="0" (
  echo.
  echo Winsock reset could not finish. Check Administrator permission.
  pause
  exit /b 1
)

set "ANDROID_JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
if exist "%ANDROID_JAVA%" (
  echo Allowing Android Studio Java through Windows Firewall...
  netsh advfirewall firewall delete rule name="SkyBarech Gradle Android Studio Java" >nul 2>&1
  netsh advfirewall firewall add rule name="SkyBarech Gradle Android Studio Java" dir=in action=allow program="%ANDROID_JAVA%" enable=yes profile=any >nul
  netsh advfirewall firewall add rule name="SkyBarech Gradle Android Studio Java Out" dir=out action=allow program="%ANDROID_JAVA%" enable=yes profile=any >nul
)

for /f "delims=" %%J in ('where java.exe 2^>nul') do (
  echo Allowing configured Java through Windows Firewall...
  netsh advfirewall firewall delete rule name="SkyBarech Gradle System Java" >nul 2>&1
  netsh advfirewall firewall add rule name="SkyBarech Gradle System Java" dir=in action=allow program="%%J" enable=yes profile=any >nul
  netsh advfirewall firewall add rule name="SkyBarech Gradle System Java Out" dir=out action=allow program="%%J" enable=yes profile=any >nul
  goto :javaDone
)
:javaDone

echo.
echo SUCCESS: Restart this PC now, then run BUILD_ANDROID_APK.bat again.
pause
