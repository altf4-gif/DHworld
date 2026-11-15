@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist "out\com\example\engine\Main.class" (
    echo [ERROR] Not built. Run build.bat first.
    pause
    exit /b 1
)

REM ---------- locate DHsim-main\lib (same auto-detect as build.bat) ----------
set "DHROOT="
if exist "..\DHsim-main\lib\lwjgl-3.4.3.jar" set "DHROOT=.."
if not defined DHROOT if exist ".\DHsim-main\lib\lwjgl-3.4.3.jar" set "DHROOT=."
if not defined DHROOT if exist "..\..\DHsim-main\lib\lwjgl-3.4.3.jar" set "DHROOT=..\.."

if not defined DHROOT (
    echo [ERROR] Cannot find DHsim-main\lib.
    echo         Run build.bat first, or put MrHgame next to DHsim-main.
    pause
    exit /b 1
)

java -cp "out;%DHROOT%\DHsim-main\lib\*" com.example.engine.Main
pause
