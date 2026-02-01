#!/bin/bash
# DMTools CLI Installation Script
# Usage:
#   Latest version: curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash
#   Specific version: curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/v1.7.126/install.sh | bash -s v1.7.126
# Requirements: Java 23 (will attempt automatic installation on macOS/Linux)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REPO="IstiN/dmtools"
INSTALL_DIR="$HOME/.dmtools"
BIN_DIR="$INSTALL_DIR/bin"
JAR_PATH="$INSTALL_DIR/dmtools.jar"
SCRIPT_PATH="$BIN_DIR/dmtools"

# Helper functions
error() {
    echo -e "${RED}Error: $1${NC}" >&2
    exit 1
}

info() {
    echo -e "${GREEN}$1${NC}"
}

warn() {
    echo -e "${YELLOW}Warning: $1${NC}"
}

progress() {
    echo -e "${BLUE}$1${NC}"
}

# Detect platform
detect_platform() {
    local os=""
    local arch=""
    
    # Check for Windows first (Git Bash, WSL, Cygwin, MSYS)
    if [[ -n "$WINDIR" ]] || [[ -n "$MSYSTEM" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ "$(uname -s)" == *"MINGW"* ]] || [[ "$(uname -s)" == *"MSYS"* ]] || [[ "$(uname -s)" == *"CYGWIN"* ]]; then
        os="windows"
    else
        case "$(uname -s)" in
            Darwin*) os="darwin" ;;
            Linux*) os="linux" ;;
            *) error "Unsupported operating system: $(uname -s)" ;;
        esac
    fi
    
    case "$(uname -m)" in
        x86_64|amd64) arch="amd64" ;;
        arm64|aarch64) arch="arm64" ;;
        *) error "Unsupported architecture: $(uname -m)" ;;
    esac
    
    echo "${os}_${arch}"
}

# Detect version from script URL or parameters
detect_version() {
    local version=""
    
    # Method 1: Check for DMTOOLS_VERSION environment variable (highest priority)
    if [ -n "${DMTOOLS_VERSION:-}" ]; then
        version="$DMTOOLS_VERSION"
        # Ensure version has 'v' prefix if it doesn't already
        if [[ ! "$version" =~ ^v ]]; then
            version="v${version}"
        fi
        echo "$version"
        return 0
    fi
    
    # Method 2: Check for command-line argument
    if [ $# -gt 0 ] && [ -n "$1" ]; then
        version="$1"
        # Ensure version has 'v' prefix if it doesn't already
        if [[ ! "$version" =~ ^v ]]; then
            version="v${version}"
        fi
        echo "$version"
        return 0
    fi
    
    # Method 3: Try to detect from script filename (if saved as install-v1.7.126.sh)
    if [ -f "${BASH_SOURCE[0]}" ]; then
        local script_name
        script_name=$(basename "${BASH_SOURCE[0]}")
        local filename_version
        filename_version=$(echo "$script_name" | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1)
        if [ -n "$filename_version" ]; then
            echo "$filename_version"
            return 0
        fi
    fi
    
    # Method 4: Try to detect from SCRIPT_URL environment variable (can be set before curl)
    if [ -n "${SCRIPT_URL:-}" ]; then
        local url_version
        url_version=$(echo "$SCRIPT_URL" | grep -oE '/v[0-9]+\.[0-9]+\.[0-9]+/' | head -1 | sed 's/\///g')
        if [ -n "$url_version" ]; then
            echo "$url_version"
            return 0
        fi
    fi
    
    # Method 5: Try to detect from parent process command line (when piped from curl)
    # This checks the parent process (usually bash running the pipe) for curl commands
    local parent_cmd
    if command -v ps >/dev/null 2>&1; then
        # Get parent process ID
        local ppid=${PPID:-}
        if [ -n "$ppid" ]; then
            # Try to get the command line of parent process (works on Linux)
            if parent_cmd=$(ps -p "$ppid" -o args= 2>/dev/null | head -1); then
                # Extract version from curl URL in parent command
                local detected_version
                detected_version=$(echo "$parent_cmd" | grep -oE 'github\.com/[^/]+/[^/]+/(v[0-9]+\.[0-9]+\.[0-9]+)/' | head -1 | sed -E 's/.*\/(v[0-9]+\.[0-9]+\.[0-9]+)\/.*/\1/')
                if [ -n "$detected_version" ]; then
                    echo "$detected_version"
                    return 0
                fi
            fi
        fi
    fi
    
    # Method 6: Try to detect from script's own source (if script was saved to a file)
    # This works when the script is downloaded and saved, then executed
    if [ -f "${BASH_SOURCE[0]}" ]; then
        local script_path="${BASH_SOURCE[0]}"
        # Try to extract version from the script content if it contains a versioned URL
        local script_content
        script_content=$(cat "$script_path" 2>/dev/null || echo "")
        if [ -n "$script_content" ]; then
            # Look for versioned GitHub URL pattern in comments or usage
            local detected_version
            detected_version=$(echo "$script_content" | grep -oE 'github\.com/[^/]+/[^/]+/(v[0-9]+\.[0-9]+\.[0-9]+)/' | head -1 | sed -E 's/.*\/(v[0-9]+\.[0-9]+\.[0-9]+)\/.*/\1/')
            if [ -n "$detected_version" ]; then
                echo "$detected_version"
                return 0
            fi
        fi
    fi
    
    # No version detected, return empty to trigger fallback
    return 1
}

# Get latest release version
get_latest_version() {
    progress "Fetching latest release information..." >&2
    local version
    local api_response
    local curl_exit_code
    
    # Try GitHub API with proper error handling
    api_response=$(curl -s --connect-timeout 10 --max-time 30 --fail "https://api.github.com/repos/${REPO}/releases/latest" 2>&1)
    curl_exit_code=$?
    
    if [ $curl_exit_code -eq 0 ] && [ -n "$api_response" ]; then
        version=$(echo "$api_response" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/' | head -1)
        
        if [ -n "$version" ]; then
            echo "$version"
            return 0
        fi
    fi
    
    # If GitHub API failed, try alternative approach with redirect
    progress "GitHub API failed (exit code: $curl_exit_code), trying redirect method..." >&2
    
    local redirect_response
    redirect_response=$(curl -s --connect-timeout 10 --max-time 30 --fail -I "https://github.com/${REPO}/releases/latest" 2>&1)
    curl_exit_code=$?
    
    if [ $curl_exit_code -eq 0 ] && [ -n "$redirect_response" ]; then
        version=$(echo "$redirect_response" | grep -i "location:" | sed -E 's/.*\/tag\/([^\/\r\n]+).*/\1/' | tr -d '\r\n')
        
        if [ -n "$version" ]; then
            echo "$version"
            return 0
        fi
    fi
    
    # Both methods failed - provide detailed error information
    error "Failed to get latest version from GitHub API and redirect method.
    
Possible causes:
  - Network connectivity issues
  - GitHub API rate limiting
  - Repository access issues
  - curl version incompatibility

Debug information:
  - Last curl exit code: $curl_exit_code
  - API response: ${api_response:-'(empty)'}
  - Redirect response: ${redirect_response:-'(empty)'}
  
Please check your network connection and try again.
If the issue persists, you can manually download from:
https://github.com/${REPO}/releases/latest"
}

# Get version to install (detects from various sources or falls back to latest)
get_version() {
    local version
    
    # Try to detect version from various sources
    if version=$(detect_version "$@"); then
        if [ -n "$version" ]; then
            echo "$version"
            return 0
        fi
    fi
    
    # Fall back to latest version
    get_latest_version
}

# Validate downloaded file is not HTML (404 error page) and is valid
validate_not_html() {
    local file="$1"
    local desc="$2"
    local require_shell="${3:-false}"
    
    # Check if file exists and is readable
    if [ ! -f "$file" ] || [ ! -r "$file" ]; then
        return 1
    fi
    
    # Check if file is empty
    if [ ! -s "$file" ]; then
        return 1
    fi
    
    # Get first line for validation
    local first_line
    first_line=$(head -n 1 "$file" 2>/dev/null || echo "")
    
    # Check if file starts with HTML doctype or common HTML tags
    if echo "$first_line" | grep -qiE "<!DOCTYPE|<html|<body"; then
        return 1
    fi
    
    # If shell script is required, check for shebang
    if [ "$require_shell" = "true" ]; then
        if ! echo "$first_line" | grep -qE "^#!/bin/(bash|sh)"; then
            # Also check for common error messages that might be returned
            if echo "$first_line" | grep -qiE "not found|404|error|page not found"; then
                return 1
            fi
            # Check if file contains shell script indicators
            if ! head -n 5 "$file" 2>/dev/null | grep -qE "^#!/|^#.*bash|^#.*sh|set -|function |\(\)"; then
                return 1
            fi
        fi
    fi
    
    return 0
}

# Download file with progress and validation
download_file() {
    local url="$1"
    local output="$2"
    local desc="$3"
    local validate="${4:-true}"
    local max_retries=3
    local retry_count=0
    
    progress "Downloading $desc..."
    
    while [ $retry_count -lt $max_retries ]; do
        local http_code=0
        local download_success=false
        
        if command -v curl >/dev/null 2>&1; then
            # Use curl with better error handling
            # For large files (like JAR), skip HTTP code check and download directly
            # This avoids double download and handles redirects better
            if curl -L --fail --connect-timeout 30 --max-time 300 "$url" -o "$output" 2>&1 | grep -v "^[[:space:]]*[0-9]"; then
                download_success=true
            else
                local curl_exit_code=$?
                # Map curl exit codes to messages
                case $curl_exit_code in
                    6|7) warn "Network error or timeout when downloading $desc. Retrying..." ;;
                    22) warn "HTTP error (404 or similar) when downloading $desc. Retrying..." ;;
                    23) warn "Write error when saving $desc. Retrying..." ;;
                    28) warn "Transfer timeout when downloading $desc. Retrying..." ;;
                    *) warn "Download failed (curl exit code: $curl_exit_code). Retrying..." ;;
                esac
                rm -f "$output" 2>/dev/null
            fi
        elif command -v wget >/dev/null 2>&1; then
            # Use wget with better error handling
            if wget --progress=bar --tries=1 --timeout=30 "$url" -O "$output" 2>&1; then
                download_success=true
            else
                warn "Download failed. Retrying..."
            fi
        else
            error "Neither curl nor wget is available. Please install one of them."
        fi
        
        if [ "$download_success" = true ]; then
            break
        fi
        
        retry_count=$((retry_count + 1))
        if [ $retry_count -lt $max_retries ]; then
            local wait_time=$((retry_count * 2))
            warn "Waiting ${wait_time}s before retry ($retry_count/$max_retries)..."
            sleep $wait_time
        fi
    done
    
    # Check if download was successful
    if [ ! -f "$output" ] || [ ! -s "$output" ]; then
        error "Failed to download $desc from $url after $max_retries attempts.
        
Possible causes:
  - Network connectivity issues
  - GitHub service temporarily unavailable (503 error)
  - File not found in release (404 error)
  
Please try again later or check: https://github.com/${REPO}/releases/latest"
    fi
    
    # Validate the downloaded file if requested
    if [ "$validate" = "true" ]; then
        local require_shell="false"
        # Check if this is a shell script download
        if [[ "$desc" == *"shell script"* ]] || [[ "$url" == *.sh ]]; then
            require_shell="true"
        fi
        
        if ! validate_not_html "$output" "$desc" "$require_shell"; then
            warn "Downloaded file appears to be invalid (HTML error page or not a valid shell script). Removing invalid file."
            rm -f "$output"
            return 1
        fi
    fi
    
    return 0
}

# Create installation directory
create_install_dir() {
    progress "Creating installation directory..."
    mkdir -p "$INSTALL_DIR"
    mkdir -p "$BIN_DIR"
}

# Check if running on Windows (Git Bash, WSL, Cygwin, MSYS)
is_windows() {
    # Check various Windows indicators
    if [[ -n "$WINDIR" ]] || [[ -n "$MSYSTEM" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        return 0
    fi
    
    # Check uname output
    local uname_s=$(uname -s 2>/dev/null || echo "")
    if [[ "$uname_s" == *"MINGW"* ]] || [[ "$uname_s" == *"MSYS"* ]] || [[ "$uname_s" == *"CYGWIN"* ]]; then
        return 0
    fi
    
    # Check for WSL (Windows Subsystem for Linux)
    if [[ -f /proc/version ]] && grep -qi microsoft /proc/version 2>/dev/null; then
        return 0
    fi
    
    # Check for Windows mount point in WSL
    if [[ -d /mnt/c/Windows ]] || [[ -d /mnt/c/windows ]]; then
        return 0
    fi
    
    return 1
}

# Download and install Java 23 locally
install_local_java() {
    local platform="$1"
    local jre_dir="$INSTALL_DIR/jre"

    # Ensure installation directory exists
    mkdir -p "$INSTALL_DIR"

    progress "Downloading Java 23 JRE for local installation..."

    # Determine download URL based on platform
    local java_url=""
    local java_filename=""

    case "$platform" in
        darwin_amd64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_x64_mac_hotspot_23.0.1_11.tar.gz"
            java_filename="openjdk-jre-macos-x64.tar.gz"
            ;;
        darwin_arm64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_aarch64_mac_hotspot_23.0.1_11.tar.gz"
            java_filename="openjdk-jre-macos-arm64.tar.gz"
            ;;
        linux_amd64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_x64_linux_hotspot_23.0.1_11.tar.gz"
            java_filename="openjdk-jre-linux-x64.tar.gz"
            ;;
        linux_arm64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_aarch64_linux_hotspot_23.0.1_11.tar.gz"
            java_filename="openjdk-jre-linux-arm64.tar.gz"
            ;;
        windows_amd64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_x64_windows_hotspot_23.0.1_11.zip"
            java_filename="openjdk-jre-windows-x64.zip"
            ;;
        windows_arm64)
            java_url="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jre_aarch64_windows_hotspot_23.0.1_11.zip"
            java_filename="openjdk-jre-windows-arm64.zip"
            ;;
        *)
            warn "Local Java installation not supported for platform: $platform"
            return 1
            ;;
    esac

    local java_archive="$INSTALL_DIR/$java_filename"

    # Download JRE
    if ! download_file "$java_url" "$java_archive" "Java 23 JRE" "false"; then
        warn "Failed to download Java 23 JRE"
        return 1
    fi

    # Extract JRE
    progress "Extracting Java 23 JRE..."
    rm -rf "$jre_dir" 2>/dev/null
    mkdir -p "$jre_dir"

    # Handle different archive formats
    if [[ "$java_filename" == *.zip ]]; then
        # Windows ZIP archive - try multiple extraction methods
        local extracted=false

        # Method 1: Try tar (Windows 10+ has tar with zip support)
        if command -v tar >/dev/null 2>&1; then
            if tar -xf "$java_archive" -C "$jre_dir" --strip-components=1 2>/dev/null; then
                extracted=true
            fi
        fi

        # Method 2: Try unzip
        if [ "$extracted" = false ] && command -v unzip >/dev/null 2>&1; then
            local temp_extract="$INSTALL_DIR/jre_temp"
            rm -rf "$temp_extract" 2>/dev/null
            mkdir -p "$temp_extract"

            if unzip -q "$java_archive" -d "$temp_extract" 2>/dev/null; then
                # Find the JRE directory (usually jdk-23.0.1+11-jre or similar)
                local jre_subdir=$(find "$temp_extract" -maxdepth 1 -type d -name "*jre*" -o -name "jdk*" | head -1)
                if [ -n "$jre_subdir" ]; then
                    mv "$jre_subdir"/* "$jre_dir/" 2>/dev/null
                    extracted=true
                fi
                rm -rf "$temp_extract" 2>/dev/null
            fi
        fi

        # Method 3: Try PowerShell Expand-Archive (Git Bash/WSL on Windows)
        if [ "$extracted" = false ] && command -v powershell.exe >/dev/null 2>&1; then
            local win_archive_path=$(cygpath -w "$java_archive" 2>/dev/null || echo "$java_archive")
            local win_jre_dir=$(cygpath -w "$jre_dir" 2>/dev/null || echo "$jre_dir")
            local temp_extract_win="$INSTALL_DIR/jre_temp"
            local win_temp=$(cygpath -w "$temp_extract_win" 2>/dev/null || echo "$temp_extract_win")

            rm -rf "$temp_extract_win" 2>/dev/null
            mkdir -p "$temp_extract_win"

            if powershell.exe -NoProfile -Command "Expand-Archive -Path '$win_archive_path' -DestinationPath '$win_temp' -Force" 2>/dev/null; then
                # Move extracted content to jre_dir
                local jre_subdir=$(find "$temp_extract_win" -maxdepth 1 -type d -name "*jre*" -o -name "jdk*" | head -1)
                if [ -n "$jre_subdir" ]; then
                    mv "$jre_subdir"/* "$jre_dir/" 2>/dev/null
                    extracted=true
                fi
                rm -rf "$temp_extract_win" 2>/dev/null
            fi
        fi

        if [ "$extracted" = false ]; then
            warn "Failed to extract ZIP archive. No suitable extraction tool found."
            rm -f "$java_archive"
            rm -rf "$jre_dir"
            return 1
        fi
    else
        # Unix tar.gz archive
        if ! tar -xzf "$java_archive" -C "$jre_dir" --strip-components=1 2>/dev/null; then
            warn "Failed to extract Java 23 JRE"
            rm -f "$java_archive"
            rm -rf "$jre_dir"
            return 1
        fi
    fi

    info "Java 23 JRE installed locally to $jre_dir"
    rm -f "$java_archive"
    return 0
}

# Get Java command (bundled or system)
get_java_command() {
    # macOS has different JRE structure: Contents/Home/bin/java
    local bundled_java_macos="$INSTALL_DIR/jre/Contents/Home/bin/java"
    local bundled_java="$INSTALL_DIR/jre/bin/java"
    local bundled_java_exe="$INSTALL_DIR/jre/bin/java.exe"

    # Check for bundled Java (order matters: macOS, Windows, Linux)
    if [ -x "$bundled_java_macos" ]; then
        echo "$bundled_java_macos"
        return 0
    elif [ -x "$bundled_java_exe" ]; then
        echo "$bundled_java_exe"
        return 0
    elif [ -x "$bundled_java" ]; then
        echo "$bundled_java"
        return 0
    fi

    # Fall back to system Java
    if command -v java >/dev/null 2>&1; then
        echo "java"
        return 0
    fi

    return 1
}

# Check and install Java
check_java() {
    progress "Checking Java installation..."

    local java_cmd=""
    local needs_local_install=false

    # Check for environment variable to force local Java installation (for testing)
    if [ "${DMTOOLS_FORCE_LOCAL_JAVA:-false}" = "true" ]; then
        progress "DMTOOLS_FORCE_LOCAL_JAVA=true - forcing local Java installation..."
        needs_local_install=true
    elif java_cmd=$(get_java_command 2>/dev/null); then
        # Java found, check version
        local java_version
        java_version=$("$java_cmd" -version 2>&1 | head -n 1 | cut -d'"' -f2)

        # Validate that we got a valid version string
        if [ -z "$java_version" ] || ! echo "$java_version" | grep -qE '^[0-9]+'; then
            progress "Java command found but version could not be determined. Will try local installation..."
            needs_local_install=true
        else
            local java_major_version
            java_major_version=$(echo "$java_version" | cut -d'.' -f1)

            if [ "$java_major_version" -ge 23 ] 2>/dev/null; then
                info "Java version detected: $java_version"
                return 0
            else
                warn "Java $java_version is too old (need 23+). Will try to install locally..."
                needs_local_install=true
            fi
        fi
    else
        progress "No Java found. Will try local installation..."
        needs_local_install=true
    fi

    # Try local installation first
    if [ "$needs_local_install" = true ]; then
        local platform
        platform=$(detect_platform)

        progress "Attempting local Java 23 installation..."
        if install_local_java "$platform"; then
            # Verify the bundled Java
            java_cmd=$(get_java_command)
            local java_version
            java_version=$("$java_cmd" -version 2>&1 | head -n 1 | cut -d'"' -f2)
            info "Using bundled Java version: $java_version"
            return 0
        fi

        warn "Local Java installation failed. Falling back to system installation..."
    fi

    # Fall back to system package manager installation
    if ! command -v java >/dev/null 2>&1; then
        # First check if we're on Windows - don't try to install Java automatically
        if is_windows; then
            error "Java 23 is required but not installed. Please install Java 23 manually on Windows:
  - Download from: https://adoptium.net/
  - Or use Chocolatey: choco install temurin23jdk
  - Or use Windows installer: https://adoptium.net/temurin/releases/?version=23
  
Note: If you're using WSL, you can install Java in WSL using:
  sudo apt-get update && sudo apt-get install -y openjdk-23-jdk"
        elif [ -n "${GITHUB_ACTIONS:-}" ]; then
            error "Java is not available in GitHub Actions. Please set up Java first:
            
steps:
  - name: Set up Java
    uses: actions/setup-java@v4
    with:
      distribution: 'temurin'
      java-version: '23'
  - name: Install DMTools CLI
    run: |
      curl https://github.com/IstiN/dmtools/releases/latest/download/install.sh -fsS | bash"
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            warn "Java not found. Attempting to install via Homebrew..."
            if command -v brew >/dev/null 2>&1; then
                progress "Installing OpenJDK 23 via Homebrew..."
                brew install openjdk@23 || error "Failed to install Java via Homebrew"
                info "Java installed successfully via Homebrew"
            else
                error "Java 23 is required but not installed. Please install Java 23:
  - Via Homebrew: brew install openjdk@23
  - Via Oracle: https://www.oracle.com/java/technologies/downloads/
  - Via Eclipse Temurin: https://adoptium.net/"
            fi
        elif [[ "$OSTYPE" == "linux-gnu"* ]] || [[ "$(uname -s)" == "Linux" ]]; then
            # This is real Linux (not Windows/WSL)
            if command -v apt-get >/dev/null 2>&1; then
                warn "Java not found. Attempting to install via apt..."
                progress "Installing OpenJDK 23..."
                sudo apt-get update && sudo apt-get install -y openjdk-23-jdk || error "Failed to install Java 23 via apt. Please install manually."
                info "Java installed successfully"
            elif command -v yum >/dev/null 2>&1; then
                warn "Java not found. Attempting to install via yum..."
                sudo yum install -y java-23-openjdk-devel || error "Failed to install Java 23 via yum. Please install manually."
                info "Java installed successfully"
            elif command -v dnf >/dev/null 2>&1; then
                warn "Java not found. Attempting to install via dnf..."
                sudo dnf install -y java-23-openjdk-devel || error "Failed to install Java 23 via dnf. Please install manually."
                info "Java installed successfully"
            else
                error "Java 23 is required but not installed. Please install Java 23:
  - Ubuntu/Debian: sudo apt-get install openjdk-23-jdk
  - RHEL/CentOS: sudo yum install java-23-openjdk-devel
  - Fedora: sudo dnf install java-23-openjdk-devel"
            fi
        else
            error "Java 23 is required but not installed. Please install Java 23."
        fi
    fi

    # Final verification after system installation
    if java_cmd=$(get_java_command 2>/dev/null); then
        local java_version
        java_version=$("$java_cmd" -version 2>&1 | head -n 1 | cut -d'"' -f2)
        local java_major_version
        java_major_version=$(echo "$java_version" | cut -d'.' -f1)

        info "Java version detected: $java_version"

        if [ "$java_major_version" -lt 23 ] 2>/dev/null; then
            error "Java $java_version is too old. DMTools requires Java 23."
        fi
    else
        error "Java installation failed. Please install Java 23 manually."
    fi
}

# Get asset download URL from GitHub API (more reliable than redirect URLs)
get_asset_url_from_api() {
    local version="$1"
    local asset_name="$2"
    
    # Ensure version has 'v' prefix for API call
    local tag_for_api="$version"
    if [[ ! "$tag_for_api" =~ ^v ]]; then
        tag_for_api="v${version}"
    fi
    
    local api_url="https://api.github.com/repos/${REPO}/releases/tags/${tag_for_api}"
    
    progress "Getting asset URL from GitHub API..." >&2
    
    local release_info
    release_info=$(curl -s --connect-timeout 10 --max-time 30 "$api_url" 2>/dev/null)
    
    if [ -n "$release_info" ] && ! echo "$release_info" | grep -q '"message":"Not Found"'; then
        # Extract browser_download_url for the asset (works without jq)
        local download_url
        download_url=$(echo "$release_info" | grep -o "\"browser_download_url\":\"[^\"]*${asset_name}[^\"]*\"" | head -1 | sed 's/.*"browser_download_url":"\([^"]*\)".*/\1/')
        
        if [ -n "$download_url" ] && [ "$download_url" != "null" ]; then
            echo "$download_url"
            return 0
        fi
    fi
    
    return 1
}

# Download dmtools.sh from repository if release asset is missing
download_script_from_repo() {
    local version="$1"
    local script_url="https://raw.githubusercontent.com/${REPO}/main/dmtools.sh"
    
    progress "dmtools.sh not found in release assets, downloading from repository..."
    
    if download_file "$script_url" "$SCRIPT_PATH" "DMTools shell script (from repository)" "true"; then
        # Validate it's actually a shell script
        if ! head -n 1 "$SCRIPT_PATH" 2>/dev/null | grep -q "^#!/bin/bash"; then
            warn "Downloaded file doesn't appear to be a valid shell script. Trying alternative source..."
            rm -f "$SCRIPT_PATH"
            return 1
        fi
        return 0
    fi
    
    return 1
}

# Download DMTools JAR and script
download_dmtools() {
    local version="$1"
    local jar_url="https://github.com/${REPO}/releases/download/${version}/dmtools-${version}-all.jar"
    local script_url="https://github.com/${REPO}/releases/download/${version}/dmtools.sh"
    
    # Download JAR
    download_file "$jar_url" "$JAR_PATH" "DMTools JAR"
    
    # Download shell script - try multiple methods
    # Method 1: Try redirect-based URL (standard GitHub release URL)
    if download_file "$script_url" "$SCRIPT_PATH" "DMTools shell script" "true"; then
        # Success with redirect URL
        chmod +x "$SCRIPT_PATH"
        return 0
    fi
    
    # Method 2: Try GitHub API to get direct asset URL (avoids expired blob URLs)
    warn "Redirect-based download failed, trying GitHub API for direct asset URL..."
    local api_asset_url
    api_asset_url=$(get_asset_url_from_api "$version" "dmtools.sh")
    
    if [ -n "$api_asset_url" ]; then
        if download_file "$api_asset_url" "$SCRIPT_PATH" "DMTools shell script (from API)" "true"; then
            chmod +x "$SCRIPT_PATH"
            return 0
        fi
    fi
    
    # Method 3: Fallback to repository main branch
    warn "Release asset download failed, trying repository main branch..."
    if download_script_from_repo "$version"; then
        chmod +x "$SCRIPT_PATH"
        return 0
    fi
    
    # All methods failed
    error "Failed to download dmtools.sh from all available sources:
  1. GitHub release redirect URL: $script_url
  2. GitHub API asset URL: ${api_asset_url:-'(not available)'}
  3. Repository main branch: https://raw.githubusercontent.com/${REPO}/main/dmtools.sh
  
Possible causes:
  - Network connectivity issues
  - GitHub service temporarily unavailable (503 error)
  - File not found in release (404 error)
  
Please try again later or download manually from:
  https://raw.githubusercontent.com/${REPO}/main/dmtools.sh
  
And place it at: $SCRIPT_PATH"
}

# Update shell configuration
update_shell_config() {
    progress "Updating shell configuration..."
    
    local shell_configs=()
    
    # Detect shell and add appropriate config files
    case "$SHELL" in
        */bash)
            [ -f "$HOME/.bashrc" ] && shell_configs+=("$HOME/.bashrc")
            [ -f "$HOME/.bash_profile" ] && shell_configs+=("$HOME/.bash_profile")
            ;;
        */zsh)
            [ -f "$HOME/.zshrc" ] && shell_configs+=("$HOME/.zshrc")
            ;;
        */fish)
            mkdir -p "$HOME/.config/fish/conf.d"
            shell_configs+=("$HOME/.config/fish/conf.d/dmtools.fish")
            ;;
    esac
    
    # Add generic profile files if they exist
    [ -f "$HOME/.profile" ] && shell_configs+=("$HOME/.profile")
    
    local path_export="export PATH=\"$BIN_DIR:\$PATH\""

    for config in "${shell_configs[@]}"; do
        # For fish, file may not exist yet - create it
        # For other shells, only update if file exists
        if [ -f "$config" ] || [[ "$config" == *".fish" ]]; then
            # Check if PATH is already added
            if ! grep -q "$BIN_DIR" "$config" 2>/dev/null; then
                # Ensure parent directory exists
                local config_dir=$(dirname "$config")
                mkdir -p "$config_dir"

                # Add PATH configuration
                echo "" >> "$config"
                echo "# Added by DMTools installer" >> "$config"
                if [[ "$config" == *".fish" ]]; then
                    echo "set -gx PATH $BIN_DIR \$PATH" >> "$config"
                else
                    echo "$path_export" >> "$config"
                fi
                info "Updated $config"
            else
                warn "$BIN_DIR already in PATH in $config"
            fi
        fi
    done
}

# Verify installation
verify_installation() {
    progress "Verifying installation..."
    
    # Check if files exist
    [ -f "$JAR_PATH" ] || error "JAR file not found at $JAR_PATH"
    [ -f "$SCRIPT_PATH" ] || error "Script file not found at $SCRIPT_PATH"
    [ -x "$SCRIPT_PATH" ] || error "Script file is not executable at $SCRIPT_PATH"
    
    # Test the installation
    if "$SCRIPT_PATH" list >/dev/null 2>&1; then
        info "DMTools CLI installed successfully!"
    else
        warn "Installation completed but dmtools command test failed. You may need to restart your shell."
    fi
}

# Print post-installation instructions
print_instructions() {
    echo ""
    info "🎉 DMTools CLI installation completed!"
    echo ""
    echo "To get started:"
    echo "  1. Restart your shell or run: source ~/.zshrc (or ~/.bashrc)"
    echo "  2. Run: dmtools list"
    echo "  3. Set up your integrations with environment variables:"
    echo "     export DMTOOLS_INTEGRATIONS=jira,confluence,figma"
    echo "     export JIRA_EMAIL=your-email@domain.com"
    echo "     export JIRA_API_TOKEN=your-jira-api-token"
    echo "     export JIRA_BASE_PATH=https://your-domain.atlassian.net"
    echo ""
    echo "System Requirements:"
    if java_cmd=$(get_java_command 2>/dev/null); then
        local java_version=$("$java_cmd" -version 2>&1 | head -n 1 | cut -d'"' -f2)
        echo "  ✓ Java $java_version detected"
    else
        echo "  ⚠ Java not found (restart shell or source config file)"
    fi
    echo ""
    echo "For more information, visit: https://github.com/${REPO}"
}

# Main installation function
main() {
    info "🚀 Installing DMTools CLI..."
    
    # Check prerequisites
    check_java
    
    # Get version to install (detects from URL/args/env or falls back to latest)
    local version
    local version_source="latest"
    
    # Check if version was explicitly provided via DMTOOLS_VERSION env var
    if [ -n "${DMTOOLS_VERSION:-}" ]; then
        version="$DMTOOLS_VERSION"
        # Ensure version has 'v' prefix
        if [[ ! "$version" =~ ^v ]]; then
            version="v${version}"
        fi
        version_source="specified"
        info "Using specified version from DMTOOLS_VERSION env: $version"
    elif [ $# -gt 0 ] && [ -n "$1" ]; then
        # Version provided as command line argument
        version="$1"
        # Ensure version has 'v' prefix
        if [[ ! "$version" =~ ^v ]]; then
            version="v${version}"
        fi
        version_source="specified"
        info "Using specified version from argument: $version"
    else
        # Try to detect from script source
        if version=$(detect_version "$@"); then
            if [ -n "$version" ]; then
                version_source="detected from URL"
                info "Detected version from URL: $version"
            else
                version=$(get_latest_version)
            fi
        else
            version=$(get_latest_version)
        fi
    fi
    
    # Display appropriate message based on version source
    if [ "$version_source" = "specified" ] || [ "$version_source" = "detected from URL" ]; then
        info "Installing version: $version"
    else
        info "Latest version: $version"
    fi
    
    # Create directories
    create_install_dir
    
    # Download DMTools
    download_dmtools "$version"
    
    # Update shell configuration
    update_shell_config
    
    # Verify installation
    verify_installation
    
    # Print instructions
    print_instructions
}

# Run main function
main "$@"