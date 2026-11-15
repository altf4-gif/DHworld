@echo off
cd /d "%~dp0"
echo Contents of lib\:
echo ----------------------------------------
if not exist "lib" (
    echo [lib\ does not exist]
    pause
    exit /b 1
)
dir /a-d "lib\*.jar" 2>nul | findstr /i ".jar" >nul
if errorlevel 1 (
    echo [lib\ has no .jar files]
    echo Run setup.bat to download them.
    pause
    exit /b 1
)
for %%F in ("lib\*.jar") do (
    for %%S in ("%%~F") do echo   %%~nxF  ^(%%~zS bytes^)
)
echo ----------------------------------------
pause
