# DMTools CLI Installation Script for Windows PowerShell
# Usage: Invoke-RestMethod -Uri 'https://github.com/IstiN/dmtools/releases/latest/download/install.ps1' | Invoke-Expression
# Or: irm https://github.com/IstiN/dmtools/releases/latest/download/install.ps1 | iex (PowerShell 5.1+)
# For specific version: $env:DMTOOLS_VERSION="v1.7.126"; irm https://raw.githubusercontent.com/IstiN/dmtools/v1.7.126/install.ps1 | iex
# Requirements: Java 23 (will attempt automatic installation)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Info {
    Write-Host $args -ForegroundColor Green
}

function Write-Progress-Message {
    Write-Host $args -ForegroundColor Blue
}

function Write-Warn {
    Write-Host "Warning: $args" -ForegroundColor Yellow
}

function Write-Error-Message {
    Write-Host "Error: $args" -ForegroundColor Red
    exit 1
}

# Configuration
$REPO = "IstiN/dmtools"
$INSTALL_DIR = "$env:USERPROFILE\.dmtools"
$BIN_DIR = "$INSTALL_DIR\bin"
$JAR_PATH = "$INSTALL_DIR\dmtools.jar"
$SCRIPT_PATH = "$BIN_DIR\dmtools.cmd"

# Get latest release version
function Get-LatestVersion {
    Write-Progress-Message "Fetching latest release information..."
    
    try {
        $response = Invoke-RestMethod -Uri "https://api.github.com/repos/$REPO/releases/latest" -TimeoutSec 30
        $version = $response.tag_name
        if ($version) {
            return $version
        }
    } catch {
        Write-Warn "GitHub API failed, trying alternative method..."
    }
    
    try {
        $response = Invoke-WebRequest -Uri "https://github.com/$REPO/releases/latest" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
        $location = $response.Headers.Location
        if ($location -match 'tag/([^/]+)') {
            return $matches[1]
        }
    } catch {
        # Ignore redirect errors
    }
    
    Write-Error-Message "Failed to get latest version from GitHub. Please check your network connection."
}

# Validate downloaded file is not HTML (404 error page)
function Test-NotHtml {
    param(
        [string]$FilePath,
        [string]$Description
    )
    
    if (-not (Test-Path $FilePath)) {
        return $false
    }
    
    try {
        $content = Get-Content -Path $FilePath -TotalCount 1 -ErrorAction SilentlyContinue
        if ($content -match '<!DOCTYPE|<html|<body') {
            return $false
        }
        
        # Check if file is empty
        $fileInfo = Get-Item -Path $FilePath -ErrorAction SilentlyContinue
        if ($fileInfo.Length -eq 0) {
            return $false
        }
        
        return $true
    } catch {
        return $false
    }
}

# Download file with validation
function Download-File {
    param(
        [string]$Url,
        [string]$Output,
        [string]$Description,
        [switch]$Validate
    )
    
    Write-Progress-Message "Downloading $Description..."
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Output -UseBasicParsing
        
        if ($Validate) {
            if (-not (Test-NotHtml -FilePath $Output -Description $Description)) {
                Write-Warn "Downloaded file appears to be HTML (likely 404 error page). Removing invalid file."
                Remove-Item -Path $Output -Force -ErrorAction SilentlyContinue
                return $false
            }
        }
        
        return $true
    } catch {
        Write-Error-Message "Failed to download $Description from $Url : $_"
    }
}

# Download dmtools.sh from repository if release asset is missing
function Download-ScriptFromRepo {
    param([string]$Version)
    
    Write-Progress-Message "dmtools.sh not found in release assets, downloading from repository..."
    
    $scriptUrl = "https://raw.githubusercontent.com/$REPO/main/dmtools.sh"
    $tempScript = "$env:TEMP\dmtools.sh"
    
    if (Download-File -Url $scriptUrl -Output $tempScript -Description "DMTools shell script (from repository)" -Validate) {
        # Validate it's actually a shell script
        $firstLine = Get-Content -Path $tempScript -TotalCount 1 -ErrorAction SilentlyContinue
        if ($firstLine -match '^#!/bin/bash') {
            return $true
        } else {
            Write-Warn "Downloaded file doesn't appear to be a valid shell script."
            Remove-Item -Path $tempScript -Force -ErrorAction SilentlyContinue
            return $false
        }
    }
    
    return $false
}

# Create installation directory
function Create-InstallDir {
    Write-Progress-Message "Creating installation directory..."
    if (-not (Test-Path $INSTALL_DIR)) {
        New-Item -ItemType Directory -Path $INSTALL_DIR -Force | Out-Null
    }
    if (-not (Test-Path $BIN_DIR)) {
        New-Item -ItemType Directory -Path $BIN_DIR -Force | Out-Null
    }
}

# Detect Windows architecture
function Get-WindowsArchitecture {
    $arch = $env:PROCESSOR_ARCHITECTURE
    if ($arch -eq "AMD64" -or $arch -eq "x86_64") {
        return "x64"
    } elseif ($arch -eq "ARM64") {
        return "arm64"
    } else {
        return "x64" # Default to x64
    }
}

# Download and install Java 23 locally
function Install-LocalJava {
    Write-Info "Java not found. Attempting to install Java 23 locally..."

    $jreDir = "$INSTALL_DIR\jre"
    $arch = Get-WindowsArchitecture

    # Determine download URL based on architecture
    if ($arch -eq "arm64") {
        $javaUrl = "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_aarch64_windows_hotspot_23.0.1_11.zip"
        $javaFilename = "openjdk-jre-windows-arm64.zip"
    } else {
        $javaUrl = "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_x64_windows_hotspot_23.0.1_11.zip"
        $javaFilename = "openjdk-jre-windows-x64.zip"
    }

    Write-Progress-Message "Downloading Java 23 JRE for Windows ($arch)..."
    Write-Progress-Message "This may take a few minutes (~40MB download)..."

    $tempFile = "$env:TEMP\$javaFilename"

    try {
        # Download Java
        Invoke-WebRequest -Uri $javaUrl -OutFile $tempFile -UseBasicParsing

        # Create JRE directory
        if (Test-Path $jreDir) {
            Write-Progress-Message "Cleaning existing JRE directory..."
            Remove-Item -Path $jreDir -Recurse -Force
        }
        New-Item -ItemType Directory -Path $jreDir -Force | Out-Null

        # Extract Java
        Write-Progress-Message "Extracting Java..."
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($tempFile, $jreDir)

        # Find the extracted directory (it's usually named like jdk-23.0.1+11-jre)
        $extractedDir = Get-ChildItem -Path $jreDir -Directory | Select-Object -First 1
        if ($extractedDir) {
            # Move contents up one level
            $extractedPath = $extractedDir.FullName
            Get-ChildItem -Path $extractedPath -Force | ForEach-Object {
                Move-Item -Path $_.FullName -Destination $jreDir -Force
            }
            Remove-Item -Path $extractedPath -Force
        }

        # Clean up temp file
        Remove-Item -Path $tempFile -Force -ErrorAction SilentlyContinue

        # Verify installation
        $javaExe = "$jreDir\bin\java.exe"
        if (Test-Path $javaExe) {
            Write-Info "✅ Java 23 JRE installed successfully to $jreDir"

            # Set DMTOOLS_JAVA_HOME for this session
            $env:DMTOOLS_JAVA_HOME = $jreDir

            return $true
        } else {
            Write-Error-Message "Java installation failed - java.exe not found at $javaExe"
            return $false
        }
    } catch {
        Write-Error-Message "Failed to download or install Java: $_"
        Remove-Item -Path $tempFile -Force -ErrorAction SilentlyContinue
        return $false
    }
}

# Check and install Java
function Check-Java {
    Write-Progress-Message "Checking Java installation..."

    # First check system Java
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        # Check for bundled Java
        $bundledJava = "$INSTALL_DIR\jre\bin\java.exe"
        if (Test-Path $bundledJava) {
            Write-Info "Using bundled Java from $INSTALL_DIR\jre"
            $env:DMTOOLS_JAVA_HOME = "$INSTALL_DIR\jre"
            return
        }

        # Try to install Java locally
        if (Install-LocalJava) {
            return
        }

        # If automatic installation fails, show manual installation instructions
        Write-Warn "Automatic Java installation failed. Please install Java 23 manually:"
        Write-Host "  - Download from: https://adoptium.net/" -ForegroundColor Cyan
        Write-Host "  - Or use Chocolatey: choco install temurin23" -ForegroundColor Cyan
        Write-Host "  - Or use Scoop: scoop install temurin23-jre" -ForegroundColor Cyan
        Write-Error-Message "Java 23 is required but could not be installed automatically."
    }
    
    # Get Java version - suppress errors as java -version writes to stderr
    # PowerShell treats stderr output as errors, so we need to capture it properly
    $javaVersionOutput = $null
    $oldErrorAction = $ErrorActionPreference
    try {
        # Temporarily suppress errors to capture stderr output
        $ErrorActionPreference = 'SilentlyContinue'
        # Capture both stdout and stderr, java -version writes to stderr
        $allOutput = java -version 2>&1
        if ($allOutput) {
            # Get the first line which contains version info
            $javaVersionOutput = $allOutput | Select-Object -First 1
            # Convert to string if it's an ErrorRecord
            if ($javaVersionOutput -is [System.Management.Automation.ErrorRecord]) {
                $javaVersionOutput = $javaVersionOutput.ToString()
            }
        }
    } catch {
        # If that fails, try alternative method
        $javaVersionOutput = $null
    } finally {
        $ErrorActionPreference = $oldErrorAction
    }
    
    if ($javaVersionOutput) {
        $versionMatch = $javaVersionOutput -match 'version "(\d+)'
        if ($versionMatch) {
            $majorVersion = [int]$matches[1]
            if ($majorVersion -lt 23) {
                Write-Error-Message "Java $majorVersion is too old. DMTools requires Java 23."
            }
            Write-Info "Java version detected: $javaVersionOutput"
        } else {
            Write-Warn "Could not determine Java version, but Java is installed."
        }
    } else {
        Write-Warn "Could not determine Java version, but Java is installed."
    }
}

# Download DMTools JAR and script
function Download-DMTools {
    param([string]$Version)
    
    $jarUrl = "https://github.com/$REPO/releases/download/$Version/dmtools-$Version-all.jar"
    $scriptUrl = "https://github.com/$REPO/releases/download/$Version/dmtools.sh"
    
    # Download JAR
    Download-File -Url $jarUrl -Output $JAR_PATH -Description "DMTools JAR"
    
    # Download shell script and create Windows wrapper
    $tempScript = "$env:TEMP\dmtools.sh"
    
    # Try release asset first, fallback to repository
    if (-not (Download-File -Url $scriptUrl -Output $tempScript -Description "DMTools shell script" -Validate)) {
        Write-Warn "dmtools.sh not found in release assets (this is normal if not included in release)."
        if (-not (Download-ScriptFromRepo -Version $Version)) {
            Write-Error-Message "Failed to download dmtools.sh from both release assets and repository.

Please ensure dmtools.sh is included in the GitHub release, or download it manually from:
  https://raw.githubusercontent.com/$REPO/main/dmtools.sh"
        }
    }
    
    # Create Windows batch wrapper that mimics dmtools.sh behavior
    $batchContent = @"
@echo off
setlocal enabledelayedexpansion

REM Check for bundled Java first
set DMTOOLS_DIR=%USERPROFILE%\.dmtools
if exist "%DMTOOLS_DIR%\jre\bin\java.exe" (
    set JAVA_EXE=%DMTOOLS_DIR%\jre\bin\java.exe
    goto :java_found
)

REM Check JAVA_HOME
set JAVA_HOME=%JAVA_HOME%
if "%JAVA_HOME%"=="" (
    for /f "tokens=*" %%i in ('where java 2^>nul') do set JAVA_EXE=%%i
) else (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
if not exist "%JAVA_EXE%" set JAVA_EXE=java

:java_found

REM Check for --debug flag
set DEBUG_MODE=0
for %%a in (%*) do (
    if "%%a"=="--debug" set DEBUG_MODE=1
)

REM Set log4j config based on debug mode (same as dmtools.sh)
if %DEBUG_MODE%==1 (
    set LOG_CONFIG=log4j2-debug.xml
) else (
    set LOG_CONFIG=log4j2-cli.xml
)

REM Handle special commands that don't need 'mcp' prefix
if "%1"=="--version" goto :direct
if "%1"=="-v" goto :direct
if "%1"=="--help" goto :direct
if "%1"=="-h" goto :direct
if "%1"=="--list-jobs" goto :direct
if "%1"=="run" goto :direct
if "%1"=="mcp" goto :direct

REM For all other commands, prepend 'mcp' to route through MCP handler
if %DEBUG_MODE%==1 (
    "%JAVA_EXE%" -Dlog4j2.configurationFile=classpath:%LOG_CONFIG% -Dlog4j.configuration=%LOG_CONFIG% --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -jar "$JAR_PATH" mcp %*
) else (
    "%JAVA_EXE%" -Dlog4j2.configurationFile=classpath:%LOG_CONFIG% -Dlog4j.configuration=%LOG_CONFIG% --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -jar "$JAR_PATH" mcp %* 2>nul
)
goto :end

:direct
if %DEBUG_MODE%==1 (
    "%JAVA_EXE%" -Dlog4j2.configurationFile=classpath:%LOG_CONFIG% -Dlog4j.configuration=%LOG_CONFIG% --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -jar "$JAR_PATH" %*
) else (
    "%JAVA_EXE%" -Dlog4j2.configurationFile=classpath:%LOG_CONFIG% -Dlog4j.configuration=%LOG_CONFIG% --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -jar "$JAR_PATH" %* 2>nul
)

:end
endlocal
"@
    Set-Content -Path $SCRIPT_PATH -Value $batchContent
}

# Update PATH
function Update-Path {
    Write-Progress-Message "Updating PATH..."
    
    $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($currentPath -notlike "*$BIN_DIR*") {
        $newPath = "$BIN_DIR;$currentPath"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        $env:Path = "$BIN_DIR;$env:Path"
        Write-Info "Added $BIN_DIR to PATH"
    } else {
        Write-Warn "$BIN_DIR already in PATH"
    }
}

# Verify installation
function Verify-Installation {
    Write-Progress-Message "Verifying installation..."
    
    if (-not (Test-Path $JAR_PATH)) {
        Write-Error-Message "JAR file not found at $JAR_PATH"
    }
    if (-not (Test-Path $SCRIPT_PATH)) {
        Write-Error-Message "Script file not found at $SCRIPT_PATH"
    }
    
    Write-Info "DMTools CLI installed successfully!"
}

# Print post-installation instructions
function Print-Instructions {
    Write-Host ""
    Write-Info "🎉 DMTools CLI installation completed!"
    Write-Host ""
    Write-Host "To get started:"
    Write-Host "  1. Restart your terminal (PowerShell or CMD) to reload PATH"
    Write-Host "     Or in PowerShell: `$env:Path = [System.Environment]::GetEnvironmentVariable('Path','User')"
    Write-Host "     Or in CMD: set PATH=%PATH%;$BIN_DIR"
    Write-Host "  2. Run: dmtools list"
    Write-Host "  3. Set up your integrations with environment variables:"
    Write-Host "     `$env:DMTOOLS_INTEGRATIONS='jira,confluence,figma'"
    Write-Host "     `$env:JIRA_EMAIL='your-email@domain.com'"
    Write-Host "     `$env:JIRA_API_TOKEN='your-jira-api-token'"
    Write-Host "     `$env:JIRA_BASE_PATH='https://your-domain.atlassian.net'"
    Write-Host ""
    Write-Host "For more information, visit: https://github.com/$REPO"
}

# Get version to install (from environment or latest)
function Get-Version {
    # Check for DMTOOLS_VERSION environment variable
    if ($env:DMTOOLS_VERSION) {
        $version = $env:DMTOOLS_VERSION
        # Ensure version has 'v' prefix
        if (-not $version.StartsWith('v')) {
            $version = "v$version"
        }
        Write-Info "Using specified version: $version"
        return $version
    }

    # Default to latest
    return Get-LatestVersion
}

# Main installation function
function Main {
    Write-Info "🚀 Installing DMTools CLI..."

    # Check prerequisites
    Check-Java

    # Get version to install
    $version = Get-Version
    Write-Info "Installing version: $version"
    
    # Create directories
    Create-InstallDir
    
    # Download DMTools
    Download-DMTools -Version $version
    
    # Update PATH
    Update-Path
    
    # Verify installation
    Verify-Installation
    
    # Print instructions
    Print-Instructions
}

# Run main function
Main
