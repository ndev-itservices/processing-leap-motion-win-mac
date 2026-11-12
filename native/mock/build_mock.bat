@echo off
rem Builds the fake LeapC (LeapC-mock.dll) used by the bridge tests. Run from a
rem "Developer Command Prompt for VS" (or after vcvars64.bat). Author: Tiago Martins
setlocal
set HERE=%~dp0
if not exist "%HERE%build" mkdir "%HERE%build"
cl /nologo /O2 /W3 /I"%HERE%.." /LD "%HERE%mock_leapc.c" /Fe"%HERE%build\LeapC-mock.dll" /Fo"%HERE%build\\" || exit /b 1
echo built %HERE%build\LeapC-mock.dll
