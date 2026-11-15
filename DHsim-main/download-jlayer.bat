@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo  Downloading JLayer (mp3 playback)
echo ============================================
echo.

if not exist "lib" mkdir "lib"

set "JL_OK="

call :try "https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :done

call :try "https://repo.maven.apache.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :done

call :try "https://maven.aliyun.com/repository/central/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :done

call :try "https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar" "jlayer-1.0.1.jar"
if defined JL_OK goto :done

echo.
echo [ERROR] Could not download JLayer from any mirror.
echo         Download it manually in a browser:
echo           https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar
echo         and save it as:  lib\jlayer-1.0.1.4.jar
echo.
pause
exit /b 1

:done
echo.
echo ============================================
echo  JLayer is in lib\. Now run build.bat, then run.bat
echo ============================================
pause
exit /b 0

REM ------------------------------------------------------------------
REM  :try <url> <filename>
REM  Sets JL_OK if the file was downloaded and looks like a real jar.
REM ------------------------------------------------------------------
:try
set "URL=%~1"
set "NAME=%~2"

if exist "lib\%NAME%" (
    for %%S in ("lib\%NAME%") do (
        if %%~zS GEQ 5000 (
            echo   [skip] %NAME% ^(already present^)
            set "JL_OK=1"
            exit /b 0
        )
    )
)

echo   [get ] %NAME%
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 15 -o "lib\jl.tmp" "%URL%"
if errorlevel 1 (
    if exist "lib\jl.tmp" del "lib\jl.tmp" >nul 2>nul
    exit /b 0
)

for %%S in ("lib\jl.tmp") do set "JSZ=%%~zS"
if not defined JSZ exit /b 0
if %JSZ% LSS 5000 (
    del "lib\jl.tmp" >nul 2>nul
    exit /b 0
)

move /y "lib\jl.tmp" "lib\%NAME%" >nul
set "JL_OK=1"
echo   [ ok ] %NAME% ^(%JSZ% bytes^)
exit /b 0
