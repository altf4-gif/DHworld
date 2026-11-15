@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo  LWJGL 3D Engine - Setup
echo ============================================
echo.

REM ---------- 1. Проверка JDK ----------
echo [1/4] Checking Java...
where java >nul 2>nul
if errorlevel 1 (
    echo Java not found.
    echo Trying to install Microsoft OpenJDK 21 via winget...
    where winget >nul 2>nul
    if errorlevel 1 (
        echo.
        echo [ERROR] winget not found. Install JDK 21 manually:
        echo   https://learn.microsoft.com/java/openjdk/download
        pause
        exit /b 1
    )
    winget install --id Microsoft.OpenJDK.21 -e --accept-source-agreements --accept-package-agreements
    if errorlevel 1 (
        echo [ERROR] winget install failed. Install JDK 21 manually.
        pause
        exit /b 1
    )
    echo.
    echo JDK installed. CLOSE this window and run setup.bat again
    echo so PATH is refreshed.
    pause
    exit /b 0
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JVER=%%v
echo Found Java: %JVER%

REM ---------- 2. Проверка curl ----------
echo.
echo [2/4] Checking curl...
where curl >nul 2>nul
if errorlevel 1 (
    echo [ERROR] curl not found. Windows 10 1803+ includes curl.
    pause
    exit /b 1
)
echo curl OK.

REM ---------- 3. Скачивание библиотек ----------
echo.
echo [3/4] Downloading libraries to lib\ ...
if not exist "lib" mkdir "lib"

set "LWJGL=3.4.3"
set "JOML=1.10.8"

set "PRIMARY=https://repo1.maven.org/maven2"
set "FALLBACK=https://maven.aliyun.com/repository/central"

set "FAIL=0"

call :fetch "org/lwjgl/lwjgl/%LWJGL%/lwjgl-%LWJGL%.jar"                                    "lwjgl-%LWJGL%.jar"
call :fetch "org/lwjgl/lwjgl/%LWJGL%/lwjgl-%LWJGL%-natives-windows.jar"                    "lwjgl-%LWJGL%-natives-windows.jar"
call :fetch "org/lwjgl/lwjgl-glfw/%LWJGL%/lwjgl-glfw-%LWJGL%.jar"                           "lwjgl-glfw-%LWJGL%.jar"
call :fetch "org/lwjgl/lwjgl-glfw/%LWJGL%/lwjgl-glfw-%LWJGL%-natives-windows.jar"           "lwjgl-glfw-%LWJGL%-natives-windows.jar"
call :fetch "org/lwjgl/lwjgl-opengl/%LWJGL%/lwjgl-opengl-%LWJGL%.jar"                       "lwjgl-opengl-%LWJGL%.jar"
call :fetch "org/lwjgl/lwjgl-opengl/%LWJGL%/lwjgl-opengl-%LWJGL%-natives-windows.jar"       "lwjgl-opengl-%LWJGL%-natives-windows.jar"
call :fetch "org/lwjgl/lwjgl-stb/%LWJGL%/lwjgl-stb-%LWJGL%.jar"                             "lwjgl-stb-%LWJGL%.jar"
call :fetch "org/lwjgl/lwjgl-stb/%LWJGL%/lwjgl-stb-%LWJGL%-natives-windows.jar"             "lwjgl-stb-%LWJGL%-natives-windows.jar"
call :fetch "org/joml/joml/%JOML%/joml-%JOML%.jar"                                          "joml-%JOML%.jar"

REM ---------- 3b. JLayer (mp3 playback) ----------
echo.
echo [3b/4] JLayer (mp3 playback)...
set "JL_OK="

if exist "lib\jlayer-1.0.1.4.jar" (
    echo   [skip] jlayer-1.0.1.4.jar ^(already present^)
    set "JL_OK=1"
)
if defined JL_OK goto :jlayer_done

if exist "lib\jlayer-1.0.1.jar" (
    echo   [skip] jlayer-1.0.1.jar ^(already present^)
    set "JL_OK=1"
)
if defined JL_OK goto :jlayer_done

call :jl_try "https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :jlayer_done

call :jl_try "https://repo.maven.apache.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :jlayer_done

call :jl_try "https://maven.aliyun.com/repository/central/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar" "jlayer-1.0.1.4.jar"
if defined JL_OK goto :jlayer_done

call :jl_try "https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar" "jlayer-1.0.1.jar"

:jlayer_done
if not defined JL_OK (
    echo   [WARN] JLayer could not be downloaded.
    echo          The game will still BUILD and RUN, just silently.
    echo          Fix later with:  download-jlayer.bat
)

REM ---------- 4. Проверка результата ----------
echo.
echo [4/4] Verifying downloaded files...
set "COUNT=0"
set "MISSING="
for %%F in (
    "lwjgl-%LWJGL%.jar"
    "lwjgl-%LWJGL%-natives-windows.jar"
    "lwjgl-glfw-%LWJGL%.jar"
    "lwjgl-glfw-%LWJGL%-natives-windows.jar"
    "lwjgl-opengl-%LWJGL%.jar"
    "lwjgl-opengl-%LWJGL%-natives-windows.jar"
    "lwjgl-stb-%LWJGL%.jar"
    "lwjgl-stb-%LWJGL%-natives-windows.jar"
    "joml-%JOML%.jar"
) do (
    set "F=%%~F"
    if exist "lib\!F!" (
        for %%S in ("lib\!F!") do set "SZ=%%~zS"
        if !SZ! LSS 5000 (
            echo   [BAD ] !F! ^(!SZ! bytes, too small^)
            set /a FAIL+=1
        ) else (
            echo   [ OK ] !F! ^(!SZ! bytes^)
            set /a COUNT+=1
        )
    ) else (
        echo   [MISS] !F!
        set "MISSING=!MISSING! !F!"
        set /a FAIL+=1
    )
)

echo.
if not "%FAIL%"=="0" (
    echo ============================================
    echo  [ERROR] %FAIL% file^(s^) failed to download.
    echo.
    echo  Possible causes:
    echo    - Firewall / antivirus blocking maven mirrors
    echo    - Corporate proxy required
    echo    - No internet access
    echo.
    echo  Manual fallback:
    echo    Download the %COUNT% missing files listed above from
    echo    https://repo1.maven.org/maven2/  and put them in lib\
    echo ============================================
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Setup complete: %COUNT% files in lib\
echo  Next: run build.bat, then run.bat
echo ============================================
pause
exit /b 0

REM ------------------------------------------------------------------
REM  :fetch <relative-path> <filename>
REM  Tries PRIMARY, then FALLBACK mirror.
REM ------------------------------------------------------------------
:fetch
set "REL=%~1"
set "NAME=%~2"
set "DEST=lib\%NAME%"

if exist "%DEST%" (
    for %%S in ("%DEST%") do (
        if %%~zS GEQ 5000 (
            echo   [skip] %NAME% ^(already present^)
            exit /b 0
        )
    )
    echo   [re  ] %NAME% ^(existing file too small^)
    del "%DEST%" >nul 2>nul
)

echo   [get ] %NAME%
curl -L --fail --silent --show-error --retry 3 --retry-delay 2 --connect-timeout 15 -o "%DEST%.tmp" "%PRIMARY%/%REL%"
if not errorlevel 1 (
    move /y "%DEST%.tmp" "%DEST%" >nul
    exit /b 0
)

echo   [try2] %NAME% from fallback mirror...
curl -L --fail --silent --show-error --retry 3 --retry-delay 2 --connect-timeout 15 -o "%DEST%.tmp" "%FALLBACK%/%REL%"
if not errorlevel 1 (
    move /y "%DEST%.tmp" "%DEST%" >nul
    exit /b 0
)

if exist "%DEST%.tmp" del "%DEST%.tmp" >nul 2>nul
echo   [FAIL] %NAME%
exit /b 0

REM ------------------------------------------------------------------
REM  :jl_try <url> <filename>
REM  Sets JL_OK when the jar was downloaded and is not a 404 stub.
REM ------------------------------------------------------------------
:jl_try
set "JURL=%~1"
set "JNAME=%~2"

echo   [get ] %JNAME%
curl -L --fail --silent --show-error --retry 2 --retry-delay 1 --connect-timeout 15 -o "lib\jl.tmp" "%JURL%"
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

move /y "lib\jl.tmp" "lib\%JNAME%" >nul
set "JL_OK=1"
echo   [ ok ] %JNAME% ^(%JSZ% bytes^)
exit /b 0
