@echo off
setlocal
cd /d "%~dp0"

if not exist "out\com\example\engine\Main.class" (
    echo [ERROR] Project not built. Run build.bat first.
    pause
    exit /b 1
)

echo Starting LWJGL 3D Engine demo...
java -cp "out;lib/*" com.example.engine.Main

if errorlevel 1 (
    echo.
    echo [ERROR] Java process exited with error code %errorlevel%.
    pause
)
