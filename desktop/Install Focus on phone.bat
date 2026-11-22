@echo off
title Focus - install on the phone from this laptop
echo.
echo  Install Focus on your phone from this laptop
echo  --------------------------------------------
echo  Use this when the phone says "App blocked to protect your device".
echo  Installing from a computer is not blocked by that check.
echo.
echo  Before you go on:
echo   1. Phone: Settings ^> About phone ^> tap "OS version" 7 times.
echo   2. Phone: Settings ^> Additional settings ^> Developer options:
echo      turn on "USB debugging", "Install via USB" and "USB debugging (Security settings)".
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
echo  Downloading the newest Focus from GitHub ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://github.com/AbuelgasimMohamedSaeed/Focus/releases/latest/download/Focus.apk' -OutFile '%~dp0Focus.apk'"
if not exist "%~dp0Focus.apk" (
  echo  Could not download Focus. Check the internet connection and run this again.
  pause
  exit /b 1
)
echo.
echo  Waiting for the phone... If the phone asks "Allow USB debugging?", tap Allow.
"%ADB%" wait-for-device
echo  Installing (keeps your Focus data if Focus is already on the phone) ...
"%ADB%" install -r "%~dp0Focus.apk"
if errorlevel 1 (
  echo.
  echo  It did not install. Check the phone screen: Xiaomi may ask you to confirm the install.
  echo  Also check "Install via USB" is on in Developer options, then run this again.
  pause
  exit /b 1
)
rem the gray screen permission, while we are connected (harmless if already given)
"%ADB%" shell pm grant io.github.abuelgasimmohamedsaeed.focus android.permission.WRITE_SECURE_SETTINGS >nul 2>nul
echo.
echo  Done. Focus is installed on the phone.
echo  From now on, update it with the Update button inside Focus.
echo  You can turn USB debugging off again if you like.
echo.
pause
