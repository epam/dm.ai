@echo off
REM DMTools CLI Installation Script for Windows
REM Works in cmd.exe and automatically uses PowerShell
REM Usage: download this file and run: install.bat

setlocal

echo ============================================
echo DMTools CLI Installer for Windows
echo ============================================
echo.

REM Check if PowerShell is available
where pwsh >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Found PowerShell Core (pwsh)
    echo Downloading and running installer...
    pwsh -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-RestMethod -Uri 'https://github.com/IstiN/dmtools/releases/latest/download/install.ps1' -UseBasicParsing | Invoke-Expression } catch { Write-Host 'Error: Failed to download or execute installer' -ForegroundColor Red; Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }"
    goto :end
)

where powershell >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Found Windows PowerShell
    echo Downloading and running installer...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-RestMethod -Uri 'https://github.com/IstiN/dmtools/releases/latest/download/install.ps1' -UseBasicParsing | Invoke-Expression } catch { Write-Host 'Error: Failed to download or execute installer' -ForegroundColor Red; Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }"
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
