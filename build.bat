@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM ================================================================
REM  MrHgame build — auto-locates DHsim-main
REM  Works from either:
REM    DHworld\MrHgame\build.bat   (normal — parent has DHsim-main)
REM    DHworld\build.bat           (extracted to root — .\DHsim-main)
REM ================================================================

REM ---------- 1. locate DHsim-main ----------
set "DHROOT="
if exist "..\DHsim-main\src\main\java\com\example\engine\Window.java" (
    set "DHROOT=.."
    goto :dh_found
)
if exist ".\DHsim-main\src\main\java\com\example\engine\Window.java" (
    set "DHROOT=."
    goto :dh_found
)
if exist "..\..\DHsim-main\src\main\java\com\example\engine\Window.java" (
    set "DHROOT=..\.."
    goto :dh_found
)

echo.
echo [ERROR] DHsim-main not found near this build.bat.
echo.
echo   Looked in:
echo     %CD%\..\DHsim-main\src\main\java\com\example\engine\Window.java
echo     %CD%\DHsim-main\src\main\java\com\example\engine\Window.java
echo     %CD%\..\..\DHsim-main\src\main\java\com\example\engine\Window.java
echo.
echo   Fix: the MrHgame folder must live NEXT TO DHsim-main and MrH
echo        inside the same DHworld folder. Then run build.bat again.
echo.
pause
exit /b 1

:dh_found
echo [paths] DHROOT = %DHROOT%
set "ENG=%DHROOT%\DHsim-main\src\main\java\com\example\engine"
set "LIB=%DHROOT%\DHsim-main\lib"
set "MRH=%DHROOT%\MrH"
set "SRC=src\main\java\com\example\engine"

if not exist "%LIB%" mkdir "%LIB%"

REM ---------- 2. ensure LWJGL + JOML are present ----------
if exist "%LIB%\lwjgl-3.4.3.jar" goto :libs_ok

where curl >nul 2>nul
if errorlevel 1 (
    echo.
    echo [ERROR] curl not found. Windows 10 1803+ has curl built-in.
    echo         Run DHsim-main\setup.bat once to fetch libs there,
    echo         or install curl and retry.
    echo.
    pause
    exit /b 1
)

echo [libs] downloading LWJGL 3.4.3 + JOML 1.10.8 into "%LIB%" ...
set "URL=https://repo1.maven.org/maven2"
set "MIRROR=https://maven.aliyun.com/repository/central"
set "LV=3.4.3"
set "JOML=1.10.8"

call :dl "org/lwjgl/lwjgl/%LV%/lwjgl-%LV%.jar" "lwjgl-%LV%.jar"
call :dl "org/lwjgl/lwjgl/%LV%/lwjgl-%LV%-natives-windows.jar" "lwjgl-%LV%-natives-windows.jar"
call :dl "org/lwjgl/lwjgl-glfw/%LV%/lwjgl-glfw-%LV%.jar" "lwjgl-glfw-%LV%.jar"
call :dl "org/lwjgl/lwjgl-glfw/%LV%/lwjgl-glfw-%LV%-natives-windows.jar" "lwjgl-glfw-%LV%-natives-windows.jar"
call :dl "org/lwjgl/lwjgl-opengl/%LV%/lwjgl-opengl-%LV%.jar" "lwjgl-opengl-%LV%.jar"
call :dl "org/lwjgl/lwjgl-opengl/%LV%/lwjgl-opengl-%LV%-natives-windows.jar" "lwjgl-opengl-%LV%-natives-windows.jar"
call :dl "org/lwjgl/lwjgl-stb/%LV%/lwjgl-stb-%LV%.jar" "lwjgl-stb-%LV%.jar"
call :dl "org/lwjgl/lwjgl-stb/%LV%/lwjgl-stb-%LV%-natives-windows.jar" "lwjgl-stb-%LV%-natives-windows.jar"
call :dl "org/joml/joml/%JOML%/joml-%JOML%.jar" "joml-%JOML%.jar"

if not exist "%LIB%\lwjgl-3.4.3.jar" (
    echo.
    echo [ERROR] Could not download LWJGL. Internet/proxy/firewall?
    echo         Alternative: run DHsim-main\setup.bat once.
    echo.
    pause
    exit /b 1
)

:libs_ok
REM ---------- 3. copy engine .java files ----------
echo [copy] Window Camera ShaderProgram Texture RiggedModel
for %%f in (Window Camera ShaderProgram Texture RiggedModel) do (
    copy /y "%ENG%\%%f.java" "%SRC%\" >nul
)

REM ---------- 4. copy rig.txt if missing ----------
if not exist "mesh\MrH" mkdir "mesh\MrH"
if not exist "mesh\MrH\rig.txt" (
    if exist "%MRH%\rig.txt" copy /y "%MRH%\rig.txt" "mesh\MrH\rig.txt" >nul
)

REM ---------- 5. compile in temp folder (Cyrillic-safe, like DHsim-main) ----------
set "PROJECT=%~dp0"
if "%PROJECT:~-1%"=="\" set "PROJECT=%PROJECT:~0,-1%"
set "BUILD=%TEMP%\mrhgame_build"

if exist "%BUILD%" rmdir /s /q "%BUILD%" >nul 2>nul
mkdir "%BUILD%"
if errorlevel 1 (
    echo [ERROR] Cannot create %BUILD%
    pause
    exit /b 1
)

echo [temp] mirroring src + lib to %BUILD%
xcopy "src" "%BUILD%\src\" /E /I /Q /Y >nul
if errorlevel 1 (
    echo [ERROR] xcopy src failed
    pause
    exit /b 1
)
xcopy "%LIB%" "%BUILD%\lib\" /E /I /Q /Y >nul
if errorlevel 1 (
    echo [ERROR] xcopy lib failed
    pause
    exit /b 1
)

pushd "%BUILD%"
dir /s /b "src\*.java" > sources.txt
javac -encoding UTF-8 -cp "lib\*" -d out @sources.txt
set "ERR=%ERRORLEVEL%"
popd

if not "%ERR%"=="0" (
    echo.
    echo [ERROR] javac failed ^(see messages above^).
    echo.
    pause
    exit /b 1
)

echo [temp] copying compiled classes back to %PROJECT%\out
if exist "out" rmdir /s /q "out" >nul 2>nul
xcopy "%BUILD%\out" "out\" /E /I /Q /Y >nul
rmdir /s /q "%BUILD%" >nul 2>nul

echo.
echo Build OK. Run run.bat
pause
exit /b 0

REM ==================================================================
REM  :dl <relative-maven-path> <filename>  — skip if present
REM ==================================================================
:dl
set "REL=%~1"
set "NAME=%~2"
if exist "%LIB%\%NAME%" (
    echo   [skip] %NAME%
    exit /b 0
)
echo   [get ] %NAME%
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 20 -o "%LIB%\%NAME%.tmp" "%URL%/%REL%"
if not errorlevel 1 (
    move /y "%LIB%\%NAME%.tmp" "%LIB%\%NAME%" >nul
    exit /b 0
)
if exist "%LIB%\%NAME%.tmp" del "%LIB%\%NAME%.tmp" >nul 2>nul
echo   [try2] %NAME% (mirror)
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 20 -o "%LIB%\%NAME%.tmp" "%MIRROR%/%REL%"
if not errorlevel 1 (
    move /y "%LIB%\%NAME%.tmp" "%LIB%\%NAME%" >nul
    exit /b 0
)
if exist "%LIB%\%NAME%.tmp" del "%LIB%\%NAME%.tmp" >nul 2>nul
echo   [FAIL] %NAME%
exit /b 0
