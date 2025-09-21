#!/bin/bash
# DMTools CLI Installation Script
# Usage: curl https://github.com/IstiN/dmtools/releases/latest/download/install.sh -fsS | bash
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
    
    case "$(uname -s)" in
        Darwin*) os="darwin" ;;
        Linux*) os="linux" ;;
        CYGWIN*|MINGW*|MSYS*) os="windows" ;;
        *) error "Unsupported operating system: $(uname -s)" ;;
    esac
    
    case "$(uname -m)" in
        x86_64|amd64) arch="amd64" ;;
        arm64|aarch64) arch="arm64" ;;
        *) error "Unsupported architecture: $(uname -m)" ;;
    esac
    
    echo "${os}_${arch}"
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

# Download file with progress
download_file() {
    local url="$1"
    local output="$2"
    local desc="$3"
    
    progress "Downloading $desc..."
    
    if command -v curl >/dev/null 2>&1; then
        curl -L --progress-bar "$url" -o "$output" || error "Failed to download $desc"
    elif command -v wget >/dev/null 2>&1; then
        wget --progress=bar "$url" -O "$output" || error "Failed to download $desc"
    else
        error "Neither curl nor wget is available. Please install one of them."
    fi
}

# Create installation directory
create_install_dir() {
    progress "Creating installation directory..."
    mkdir -p "$INSTALL_DIR"
    mkdir -p "$BIN_DIR"
}

# Check and install Java
check_java() {
    progress "Checking Java installation..."
    
    # Check if Java is available
    if ! command -v java >/dev/null 2>&1; then
        if [ -n "${GITHUB_ACTIONS:-}" ]; then
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
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
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
    
    # Verify Java version
    local java_version
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    local java_major_version
    java_major_version=$(echo "$java_version" | cut -d'.' -f1)
    
    info "Java version detected: $java_version"
    
    # Check if Java version is sufficient (Java 23+ required)
    if [ "$java_major_version" -lt 23 ]; then
        error "Java $java_version is too old. DMTools requires Java 23."
    fi
}

# Download DMTools JAR and script
download_dmtools() {
    local version="$1"
    local jar_url="https://github.com/${REPO}/releases/download/${version}/dmtools-${version}-all.jar"
    local script_url="https://github.com/${REPO}/releases/download/${version}/dmtools.sh"
    
    # Download JAR
    download_file "$jar_url" "$JAR_PATH" "DMTools JAR"
    
    # Download shell script
    download_file "$script_url" "$SCRIPT_PATH" "DMTools shell script"
    
    # Make script executable
    chmod +x "$SCRIPT_PATH"
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
        if [ -f "$config" ]; then
            # Check if PATH is already added
            if ! grep -q "$BIN_DIR" "$config" 2>/dev/null; then
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
    echo "  ✓ Java $(java -version 2>&1 | head -n 1 | cut -d'"' -f2) detected"
    echo ""
    echo "For more information, visit: https://github.com/${REPO}"
}

# Main installation function
main() {
    info "🚀 Installing DMTools CLI..."
    
    # Check prerequisites
    check_java
    
    # Get latest version
    local version
    version=$(get_latest_version)
    info "Latest version: $version"
    
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