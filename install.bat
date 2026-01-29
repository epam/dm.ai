@echo off
REM DMTools CLI Installation Script for Windows
REM Works in cmd.exe and automatically uses PowerShell
REM Usage: curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
REM For specific version: set DMTOOLS_VERSION=v1.7.120 && curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/v1.7.120/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"

setlocal

echo ============================================
echo DMTools CLI Installer for Windows
echo ============================================
echo.

REM Detect version from environment or process
set DETECTED_VERSION=%DMTOOLS_VERSION%
if "%DETECTED_VERSION%"=="" (
    REM Try to detect version from parent process command line
    REM This is a simplified detection - in most cases users will need to set DMTOOLS_VERSION
    set DETECTED_VERSION=latest
)

REM Construct installer URL
if "%DETECTED_VERSION%"=="latest" (
    set INSTALLER_URL=https://github.com/IstiN/dmtools/releases/latest/download/install.ps1
    echo Using latest version...
) else (
    set INSTALLER_URL=https://github.com/IstiN/dmtools/releases/download/%DETECTED_VERSION%/install.ps1
    echo Using version: %DETECTED_VERSION%
)

echo Installer URL: %INSTALLER_URL%
echo.

REM Check if PowerShell is available - prefer regular PowerShell first
where powershell >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Found Windows PowerShell
    echo Downloading and running installer...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-RestMethod -Uri '%INSTALLER_URL%' -UseBasicParsing | Invoke-Expression } catch { Write-Host 'Error: Failed to download or execute installer' -ForegroundColor Red; Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }"
    goto :end
)

REM Try PowerShell Core as fallback
where pwsh >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Found PowerShell Core (pwsh)
    echo Downloading and running installer...
    pwsh -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-RestMethod -Uri '%INSTALLER_URL%' -UseBasicParsing | Invoke-Expression } catch { Write-Host 'Error: Failed to download or execute installer' -ForegroundColor Red; Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }"
    goto :end
)

REM PowerShell not found
echo Error: PowerShell not found!
echo.
echo DMTools requires PowerShell to install on Windows.
echo.
echo Please install PowerShell:
echo   - Windows 10/11: PowerShell is pre-installed
echo   - Older Windows: Download from https://aka.ms/powershell
echo.
echo Or use Git Bash:
echo   curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install ^| bash
echo.
pause
exit /b 1

:end
echo.
echo Installation complete!
echo.
pause
endlocal
