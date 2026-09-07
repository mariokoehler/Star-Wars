@echo off
rem StarWars client updater (design.md 3.11).
rem
rem Downloads the latest client release from GitHub and replaces this install
rem in place, without touching connection-config.json (or anything else this
rem script doesn't explicitly own) at the top level of the install folder.
rem
rem Only ever shells out to powershell.exe (Windows PowerShell 5.1, bundled
rem with Windows 11) - never pwsh.exe (PowerShell 7+, a separate install we
rem can't assume players have). Every PowerShell snippet below must stay
rem 5.1-compatible (no ternary/?? /?. operators, etc).
setlocal EnableDelayedExpansion

set "REPO_OWNER=mariokoehler"
set "REPO_NAME=Star-Wars"
set "ASSET_NAME=StarWars-Client.zip"
set "APP_EXE=StarWars.exe"

if "%~1"=="--relaunched" goto :relaunched

rem --- Step 1: self-relaunch from %TEMP%. -----------------------------------
rem This file is one of the things about to be replaced, so a copy of it -
rem not this running instance - has to be the one that does the actual work.
set "INSTALL_DIR=%~dp0"
set "TEMP_COPY=%TEMP%\StarWars-update-%RANDOM%.cmd"
copy /y "%~f0" "%TEMP_COPY%" >nul
start "" cmd /c ""%TEMP_COPY%" --relaunched "%INSTALL_DIR%""
exit /b 0

:relaunched
set "INSTALL_DIR=%~2"
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"

echo StarWars updater
echo Install directory: %INSTALL_DIR%
echo.

rem --- Step 2: refuse to update while the game is running. ------------------
rem A running JVM holds app\*.jar and runtime\bin\*.dll open, so nothing
rem below could be replaced anyway - better to say so than fail halfway.
tasklist /fi "imagename eq %APP_EXE%" 2>nul | find /i "%APP_EXE%" >nul
if not errorlevel 1 (
    echo StarWars is currently running. Please close it, then run update.cmd again.
    pause
    exit /b 1
)

rem --- Clean up any stale leftovers from a previous, interrupted update. -----
if exist "%INSTALL_DIR%\app.old" rd /s /q "%INSTALL_DIR%\app.old" 2>nul
if exist "%INSTALL_DIR%\runtime.old" rd /s /q "%INSTALL_DIR%\runtime.old" 2>nul
if exist "%INSTALL_DIR%\%APP_EXE%.old" del /f /q "%INSTALL_DIR%\%APP_EXE%.old" 2>nul

rem --- Step 3+4: download + extract via PowerShell 5.1. ----------------------
set "STAGE_DIR=%TEMP%\StarWars-update-stage-%RANDOM%"
set "ZIP_PATH=%STAGE_DIR%\%ASSET_NAME%"
set "EXTRACT_DIR=%STAGE_DIR%\extracted"
set "DOWNLOAD_URL=https://github.com/%REPO_OWNER%/%REPO_NAME%/releases/latest/download/%ASSET_NAME%"

mkdir "%STAGE_DIR%" 2>nul

echo Downloading the latest version...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ZIP_PATH%'"
if errorlevel 1 (
    echo Download failed. No files were changed.
    goto :cleanup_fail
)

echo Extracting...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%EXTRACT_DIR%' -Force"
if errorlevel 1 (
    echo Extraction failed. No files were changed.
    goto :cleanup_fail
)

rem --- Step 5: verify before touching the real install. ----------------------
set "NEW_ROOT=%EXTRACT_DIR%\StarWars"
if not exist "%NEW_ROOT%\%APP_EXE%" (
    echo Downloaded update looks incomplete - %APP_EXE% not found. No files were changed.
    goto :cleanup_fail
)

rem --- Step 6: staged swap - rename old aside, move new in, delete old only --
rem --- on full success; roll back if anything fails partway through. --------
echo Installing update...

set "SWAP_OK=1"
call :safe_move "%INSTALL_DIR%\app" "%INSTALL_DIR%\app.old"
if errorlevel 1 set "SWAP_OK=0"
call :safe_move "%INSTALL_DIR%\runtime" "%INSTALL_DIR%\runtime.old"
if errorlevel 1 set "SWAP_OK=0"
call :safe_move "%INSTALL_DIR%\%APP_EXE%" "%INSTALL_DIR%\%APP_EXE%.old"
if errorlevel 1 set "SWAP_OK=0"

if "%SWAP_OK%"=="1" (
    call :safe_move "%NEW_ROOT%\app" "%INSTALL_DIR%\app"
    if errorlevel 1 set "SWAP_OK=0"
    call :safe_move "%NEW_ROOT%\runtime" "%INSTALL_DIR%\runtime"
    if errorlevel 1 set "SWAP_OK=0"
    call :safe_move "%NEW_ROOT%\%APP_EXE%" "%INSTALL_DIR%\%APP_EXE%"
    if errorlevel 1 set "SWAP_OK=0"
    call :safe_move "%NEW_ROOT%\update.cmd" "%INSTALL_DIR%\update.cmd"
    if errorlevel 1 set "SWAP_OK=0"
)

if "%SWAP_OK%"=="1" (
    echo Update installed. Cleaning up...
    if exist "%INSTALL_DIR%\app.old" rd /s /q "%INSTALL_DIR%\app.old" 2>nul
    if exist "%INSTALL_DIR%\runtime.old" rd /s /q "%INSTALL_DIR%\runtime.old" 2>nul
    if exist "%INSTALL_DIR%\%APP_EXE%.old" del /f /q "%INSTALL_DIR%\%APP_EXE%.old" 2>nul
    rd /s /q "%STAGE_DIR%" 2>nul
    echo Done. Starting StarWars...
    start "" "%INSTALL_DIR%\%APP_EXE%"
    exit /b 0
) else (
    echo Update failed partway through - rolling back...
    call :rollback
    rd /s /q "%STAGE_DIR%" 2>nul
    echo Rolled back to the previous version. No harm done.
    pause
    exit /b 1
)

:cleanup_fail
if exist "%STAGE_DIR%" rd /s /q "%STAGE_DIR%" 2>nul
pause
exit /b 1

rem --- Subroutines. -----------------------------------------------------------

rem :rollback - moves any *.old items still present back into place.
:rollback
if exist "%INSTALL_DIR%\app.old" (
    if exist "%INSTALL_DIR%\app" rd /s /q "%INSTALL_DIR%\app" 2>nul
    move /y "%INSTALL_DIR%\app.old" "%INSTALL_DIR%\app" >nul
)
if exist "%INSTALL_DIR%\runtime.old" (
    if exist "%INSTALL_DIR%\runtime" rd /s /q "%INSTALL_DIR%\runtime" 2>nul
    move /y "%INSTALL_DIR%\runtime.old" "%INSTALL_DIR%\runtime" >nul
)
if exist "%INSTALL_DIR%\%APP_EXE%.old" (
    if exist "%INSTALL_DIR%\%APP_EXE%" del /f /q "%INSTALL_DIR%\%APP_EXE%" 2>nul
    move /y "%INSTALL_DIR%\%APP_EXE%.old" "%INSTALL_DIR%\%APP_EXE%" >nul
)
exit /b 0

rem :safe_move <source> <dest> - moves a file or directory, retrying a few
rem times in case AV/indexer is briefly holding a lock. Sets errorlevel 1
rem (via the final exit /b) if the source still exists after all retries.
:safe_move
set "_tries=0"
:safe_move_retry
move /y "%~1" "%~2" >nul 2>&1
if exist "%~1" (
    set /a _tries+=1
    if !_tries! LSS 5 (
        timeout /t 1 /nobreak >nul
        goto :safe_move_retry
    )
    exit /b 1
)
exit /b 0
