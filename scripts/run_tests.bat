@echo off
rem Runs the test suite on Windows: pure Java API tests + JNI bridge test against the mock LeapC.
rem Needs: JAVA_HOME (JDK 17+), build\classes and build\test-classes (see build.sh / CI), and
rem native\build\LeapJava.dll + native\mock\build\LeapC-mock.dll (build_native.bat / build_mock.bat).
rem Author: Tiago Martins
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"
echo ### ApiTest (synthetic frames, no native code)
"%JAVA_HOME%\bin\java" -cp "build\classes;build\test-classes" com.leapmotion.leap.ApiTest || exit /b 1
echo.
echo ### BridgeTest (LeapJava native bridge + mock LeapC)
"%JAVA_HOME%\bin\java" -Dleap.leapc.path="%ROOT%\native\mock\build\LeapC-mock.dll" -Djava.library.path="%ROOT%\native\build" -cp "build\classes;build\test-classes" BridgeTest || exit /b 1
