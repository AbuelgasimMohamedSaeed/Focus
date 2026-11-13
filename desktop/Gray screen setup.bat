@echo off
title Focus - gray screen setup
echo.
echo  Focus: gray screen setup (one time only)
echo  ----------------------------------------
echo  This lets Focus on your phone turn the screen black and white.
echo.
echo  Before you go on:
echo   1. Phone: Settings ^> About phone ^> tap "OS version" 7 times.
echo   2. Phone: Settings ^> Additional settings ^> Developer options:
echo      turn on "USB debugging" and "USB debugging (Security settings)".
echo   3. Plug the phone into this laptop with a USB cable.
echo.
pause

set "ADB="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if defined ADB goto haveadb
echo.
echo  Downloading Google's Android platform tools (about 7 MB) from dl.google.com ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile \"$env:TEMP\focus-platform-tools.zip\"; Expand-Archive -Force \"$env:TEMP\focus-platform-tools.zip\" '%~dp0.'"
if not exist "%~dp0platform-tools\adb.exe" (
  echo  The download did not work. Check the internet connection and run this again.
  pause
  exit /b 1
)
set "ADB=%~dp0platform-tools\adb.exe"

:haveadb
echo.
echo  Waiting for the phone... If the phone asks "Allow USB debugging?", tap Allow.
"%ADB%" wait-for-device
"%ADB%" shell pm grant io.github.abuelgasimmohamedsaeed.focus android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 (
  echo.
  echo  It did not work. Check that Focus is installed and up to date, the phone is unlocked,
  echo  and BOTH "USB debugging" and "USB debugging (Security settings)" are on. Then run this again.
) else (
  echo.
  echo  Done. Open Focus on the phone: App Time ^> Gray screen now works.
  echo  You can turn USB debugging off again if you like.
)
echo.
pause
