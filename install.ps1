# DMTools CLI Installation Script for Windows PowerShell
# Usage: Invoke-RestMethod -Uri 'https://github.com/IstiN/dmtools/releases/latest/download/install.ps1' | Invoke-Expression
# Or: irm https://github.com/IstiN/dmtools/releases/latest/download/install.ps1 | iex (PowerShell 5.1+)
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
        $response = Invoke-WebRequest -Uri "https://github.com/$REPO/releases/latest" -MaximumRedirection 0 -ErrorAction SilentlyContinue
        $location = $response.Headers.Location
        if ($location -match 'tag/([^/]+)') {
            return $matches[1]
        }
    } catch {
        # Ignore redirect errors
    }
    
    Write-Error-Message "Failed to get latest version from GitHub. Please check your network connection."
}

# Download file
function Download-File {
    param(
        [string]$Url,
        [string]$Output,
        [string]$Description
    )
    
    Write-Progress-Message "Downloading $Description..."
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Output -UseBasicParsing
    } catch {
        Write-Error-Message "Failed to download $Description : $_"
    }
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

# Check and install Java
function Check-Java {
    Write-Progress-Message "Checking Java installation..."
    
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        Write-Warn "Java not found. Please install Java 23 or later:"
        Write-Host "  - Download from: https://adoptium.net/" -ForegroundColor Cyan
        Write-Host "  - Or use Chocolatey: choco install temurin23jdk" -ForegroundColor Cyan
        Write-Error-Message "Java 23 is required but not installed."
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
    Download-File -Url $scriptUrl -Output $tempScript -Description "DMTools shell script"
    
    # Create Windows batch wrapper
    $batchContent = @"
@echo off
setlocal
set JAVA_HOME=%JAVA_HOME%
if "%JAVA_HOME%"=="" (
    for /f "tokens=*" %%i in ('where java') do set JAVA_EXE=%%i
) else (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
if not exist "%JAVA_EXE%" set JAVA_EXE=java
"%JAVA_EXE%" -jar "$JAR_PATH" %*
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
    Write-Host "  1. Restart your PowerShell or run: `$env:Path = [System.Environment]::GetEnvironmentVariable('Path','User')"
    Write-Host "  2. Run: dmtools list"
    Write-Host "  3. Set up your integrations with environment variables:"
    Write-Host "     `$env:DMTOOLS_INTEGRATIONS='jira,confluence,figma'"
    Write-Host "     `$env:JIRA_EMAIL='your-email@domain.com'"
    Write-Host "     `$env:JIRA_API_TOKEN='your-jira-api-token'"
    Write-Host "     `$env:JIRA_BASE_PATH='https://your-domain.atlassian.net'"
    Write-Host ""
    Write-Host "For more information, visit: https://github.com/$REPO"
}

# Main installation function
function Main {
    Write-Info "🚀 Installing DMTools CLI..."
    
    # Check prerequisites
    Check-Java
    
    # Get latest version
    $version = Get-LatestVersion
    Write-Info "Latest version: $version"
    
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
