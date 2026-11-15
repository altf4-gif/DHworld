@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo  LWJGL 3D Engine - Build
echo ============================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found. Run setup.bat first.
    pause
    exit /b 1
)

if not exist "lib" (
    echo [ERROR] lib\ not found. Run setup.bat first.
    pause
    exit /b 1
)

REM Sanity check: are there any jars in lib?
set "JARCOUNT=0"
for %%F in ("lib\*.jar") do set /a JARCOUNT+=1
if "%JARCOUNT%"=="0" (
    echo [ERROR] lib\ contains no .jar files. Run setup.bat again.
    pause
    exit /b 1
)
echo Found %JARCOUNT% jar(s) in lib\.

REM ------------------------------------------------------------------
REM  Since the project path contains non-ASCII characters
REM  (C:\Users\Влад\...), javac cannot read @sources.txt with the
REM  default encoding. Workaround: mirror src + lib into a pure-ASCII
REM  temp folder, compile there, then copy the classes back.
REM ------------------------------------------------------------------
set "PROJECT=%~dp0"
if "%PROJECT:~-1%"=="\" set "PROJECT=%PROJECT:~0,-1%"

set "BUILD=%TEMP%\lwjgl3d_build"
echo.
echo Using temp build dir: %BUILD%

if exist "%BUILD%" rmdir /s /q "%BUILD%" >nul 2>nul
mkdir "%BUILD%" || (echo [ERROR] Cannot create %BUILD% & pause & exit /b 1)

echo Copying sources and libraries...
xcopy "src" "%BUILD%\src\" /E /I /Q /Y >nul || (echo [ERROR] xcopy src failed & pause & exit /b 1)
xcopy "lib" "%BUILD%\lib\" /E /I /Q /Y >nul || (echo [ERROR] xcopy lib failed & pause & exit /b 1)

pushd "%BUILD%"

dir /s /b "src\main\java\*.java" > sources.txt
if not exist "sources.txt" (
    echo [ERROR] No .java files found.
    popd
    pause
    exit /b 1
)

echo.
echo Files to compile:
type sources.txt
echo.

echo Compiling with javac...
javac -encoding UTF-8 -cp "lib\*" -d "out" @sources.txt
set ERR=%ERRORLEVEL%

if not "%ERR%"=="0" (
    echo.
    echo [ERROR] Compilation failed. See messages above.
    popd
    pause
    exit /b 1
)

popd

echo.
echo Copying compiled classes back to project\out ...
if exist "out" rmdir /s /q "out" >nul 2>nul
xcopy "%BUILD%\out" "out\" /E /I /Q /Y >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy classes back.
    pause
    exit /b 1
)

rmdir /s /q "%BUILD%" >nul 2>nul

echo.
echo ============================================
echo  Build OK. Run run.bat to start the demo.
echo ============================================
pause
exit /b 0
