@echo off
cd /d "%~dp0"
echo ============================================
echo  LWJGL 3D Engine - Clean
echo ============================================
echo.

echo Removing out\ ...
if exist "out" (
    rmdir /s /q "out"
    if exist "out" (
        echo [ERROR] Could not delete out\. Close any Explorer window
        echo         showing this folder and try again.
        pause
        exit /b 1
    )
    echo   OK
) else (
    echo   nothing to remove
)

echo Removing leftover sources.txt ...
if exist "sources.txt" (
    del "sources.txt" >nul 2>nul
    echo   OK
) else (
    echo   nothing to remove
)

echo Removing temp build dir ...
if exist "%TEMP%\lwjgl3d_build" (
    rmdir /s /q "%TEMP%\lwjgl3d_build" >nul 2>nul
    echo   OK
) else (
    echo   nothing to remove
)

echo.
echo ============================================
echo  Clean OK. Now run build.bat, then run.bat.
echo ============================================
pause
exit /b 0
