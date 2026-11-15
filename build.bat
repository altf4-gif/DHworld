@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "DHROOT="
if exist ".\DHsim-main\src\main\java\com\example\engine\Window.java" set "DHROOT=."
if not defined DHROOT if exist "..\DHsim-main\src\main\java\com\example\engine\Window.java" set "DHROOT=.."
if not defined DHROOT if exist "..\..\DHsim-main\src\main\java\com\example\engine\Window.java" set "DHROOT=..\.."
if not defined DHROOT (
    echo [ERROR] DHsim-main not found.
    pause & exit /b 1
)

set "ENG=%DHROOT%\DHsim-main\src\main\java\com\example\engine"
set "LIB=%DHROOT%\DHsim-main\lib"
set "MRH=%DHROOT%\MrH"
set "SRC=src\main\java\com\example\engine"
if not exist "%LIB%" mkdir "%LIB%"

if exist "%LIB%\lwjgl-3.4.3.jar" goto :libs_ok
where curl >nul 2>nul || (echo [ERROR] curl not found. Run DHsim-main\setup.bat once. & pause & exit /b 1)
echo [libs] downloading ...
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
if not exist "%LIB%\lwjgl-3.4.3.jar" (echo [ERROR] download failed. & pause & exit /b 1)

:libs_ok
echo [copy] Camera ShaderProgram Texture UiRenderer UiTexture TextRenderer
for %%f in (Camera ShaderProgram Texture UiRenderer UiTexture TextRenderer) do (
    copy /y "%ENG%\%%f.java" "%SRC%\" >nul
)

if not exist "mesh\MrH" mkdir "mesh\MrH"
if not exist "mesh\MrH\rig.txt" (
    if exist "%MRH%\rig.txt" copy /y "%MRH%\rig.txt" "mesh\MrH\rig.txt" >nul
)

set "PROJECT=%~dp0"
if "%PROJECT:~-1%"=="\" set "PROJECT=%PROJECT:~0,-1%"
set "BUILD=%TEMP%\mrhgame_build"
if exist "%BUILD%" rmdir /s /q "%BUILD%" >nul 2>nul
mkdir "%BUILD%"
xcopy "src" "%BUILD%\src\" /E /I /Q /Y >nul
xcopy "%LIB%" "%BUILD%\lib\" /E /I /Q /Y >nul
pushd "%BUILD%"
dir /s /b "src\*.java" > sources.txt
javac -encoding UTF-8 -cp "lib\*" -d out @sources.txt
set "ERR=%ERRORLEVEL%"
popd
if not "%ERR%"=="0" (echo [ERROR] javac failed. & pause & exit /b 1)
if exist "out" rmdir /s /q "out" >nul 2>nul
xcopy "%BUILD%\out" "out\" /E /I /Q /Y >nul
rmdir /s /q "%BUILD%" >nul 2>nul
echo.
echo Build OK. Run run.bat
pause
exit /b 0

:dl
set "REL=%~1" & set "NAME=%~2"
if exist "%LIB%\%NAME%" (echo   [skip] %NAME% & exit /b 0)
echo   [get ] %NAME%
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 20 -o "%LIB%\%NAME%.tmp" "%URL%/%REL%"
if not errorlevel 1 (move /y "%LIB%\%NAME%.tmp" "%LIB%\%NAME%" >nul & exit /b 0)
if exist "%LIB%\%NAME%.tmp" del "%LIB%\%NAME%.tmp" >nul 2>nul
echo   [try2] %NAME% (mirror)
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 20 -o "%LIB%\%NAME%.tmp" "%MIRROR%/%REL%"
if not errorlevel 1 (move /y "%LIB%\%NAME%.tmp" "%LIB%\%NAME%" >nul & exit /b 0)
if exist "%LIB%\%NAME%.tmp" del "%LIB%\%NAME%.tmp" >nul 2>nul
echo   [FAIL] %NAME%
exit /b 0
