@echo off
rem Double-click installer for Windows 10/11 (64-bit). It:
rem   1. downloads and starts the Ultraleap Hand Tracking installer (the Leap Motion driver), if missing
rem   2. copies the "Leap Motion for Processing" library into your Processing sketchbook
rem   3. opens the test example in Processing
rem Author: Tiago Martins
setlocal EnableDelayedExpansion
title Leap Motion for Processing - installer (Windows)
set "HERE=%~dp0"
for %%I in ("%HERE%..") do set "LIB=%%~fI"
echo ======================================================
echo    Leap Motion for Processing - installer (Windows)
echo ======================================================
echo.
if not exist "%LIB%\library\LeapJava.jar" (
  echo I cannot find the library files next to this installer.
  echo Please unzip LeapMotionForProcessing.zip first and run this file from inside the unzipped folder.
  pause & exit /b 1
)

echo Step 1 of 3 - Leap Motion driver (Ultraleap Hand Tracking)
if exist "%ProgramFiles%\Ultraleap\LeapSDK\lib\x64\LeapC.dll" (
  echo    already installed.
) else (
  set "PKG=%USERPROFILE%\Downloads\tracking-software-windows-6.2.0.exe"
  if not exist "!PKG!" (
    echo    downloading the official Ultraleap installer, about 400 MB...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; (New-Object Net.WebClient).DownloadFile('https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/6.2.0/tracking-software-windows-6.2.0.exe', '!PKG!')"
  )
  if exist "!PKG!" (
    echo    starting the installer. Follow it to the end, then come back here.
    start /wait "" "!PKG!"
  ) else (
    echo    download failed. Get it from https://www.ultraleap.com/downloads/leap-controller/ and run it.
  )
)
echo.

echo Step 2 of 3 - Processing library
set "SKETCHBOOK=%USERPROFILE%\Documents\Processing"
set "DEST=%SKETCHBOOK%\libraries\LeapMotionForProcessing"
if not exist "%SKETCHBOOK%\libraries\old" mkdir "%SKETCHBOOK%\libraries\old"
if exist "%SKETCHBOOK%\libraries\LeapJava" (
  move "%SKETCHBOOK%\libraries\LeapJava" "%SKETCHBOOK%\libraries\old\LeapJava_old" >nul
  echo    moved the old LeapJava library to libraries\old\
)
if exist "%DEST%" rmdir /s /q "%DEST%"
xcopy "%LIB%" "%DEST%\" /E /I /Q /Y >nul
echo    installed in %DEST%
echo.

echo Step 3 of 3 - test
tasklist /FI "IMAGENAME eq LeapSvc.exe" 2>nul | find /I "LeapSvc.exe" >nul && echo    the Leap Motion service is running.
echo    Open Processing: File ^> Examples ^> Contributed Libraries ^> Leap Motion for Processing ^> LM_0_Test,
echo    then press Run. A green dot means the Leap Motion works.
echo.
echo Done.
pause
