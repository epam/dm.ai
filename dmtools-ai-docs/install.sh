#!/bin/bash

# DMtools Agent Skill Installer
# Works with Cursor, Claude, Codex, and any Agent Skills compatible system
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash
#   INSTALL_LOCATION=all bash install.sh        # Install to all locations
#   INSTALL_LOCATION=1 bash install.sh          # Install to first location
#   bash install.sh --all                       # Install to all locations

set -e

# Parse command line arguments
INSTALL_ALL=false
for arg in "$@"; do
    case $arg in
        --all|-a)
            INSTALL_ALL=true
            shift
            ;;
        --help|-h)
            echo "DMtools Agent Skill Installer"
            echo ""
            echo "Usage:"
            echo "  $0 [options]"
            echo ""
            echo "Options:"
            echo "  --all, -a     Install to all detected locations"
            echo "  --help, -h    Show this help message"
            echo ""
            echo "Environment Variables:"
            echo "  INSTALL_LOCATION  Set to 'all' or number (1,2,3...) to auto-select location"
            echo ""
            echo "Examples:"
            echo "  curl -fsSL https://github.com/IstiN/dmtools/releases/latest/download/install.sh | bash"
            echo "  INSTALL_LOCATION=all bash install.sh"
            echo "  INSTALL_LOCATION=1 bash install.sh"
            exit 0
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
SKILL_NAME="dmtools"
GITHUB_REPO="IstiN/dmtools"
TEMP_DIR=$(mktemp -d)

# Skill directories to check (in order of preference)
SKILL_DIRS=(
    ".cursor/skills"
    ".claude/skills"
    ".codex/skills"
    "$HOME/.cursor/skills"
    "$HOME/.claude/skills"
    "$HOME/.codex/skills"
)

# Functions
print_header() {
    echo "" >&2
    echo -e "${CYAN}╔════════════════════════════════════════════╗${NC}" >&2
    echo -e "${CYAN}║      DMtools Agent Skill Installer        ║${NC}" >&2
    echo -e "${CYAN}╚════════════════════════════════════════════╝${NC}" >&2
    echo "" >&2
}

print_success() {
    echo -e "${GREEN}✓${NC} $1" >&2
}

print_error() {
    echo -e "${RED}✗${NC} $1" >&2
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1" >&2
}

# Detect available skill directories
detect_skill_dirs() {
    local found_dirs=()

    # Check project-level directories
    if [ -d ".cursor/skills" ] || [ -d ".cursor" ]; then
        found_dirs+=(".cursor/skills")
    fi
    if [ -d ".claude/skills" ] || [ -d ".claude" ]; then
        found_dirs+=(".claude/skills")
    fi
    if [ -d ".codex/skills" ] || [ -d ".codex" ]; then
        found_dirs+=(".codex/skills")
    fi

    # Check user-level directories
    if [ -d "$HOME/.cursor" ] || command -v cursor &> /dev/null; then
        found_dirs+=("$HOME/.cursor/skills")
    fi
    if [ -d "$HOME/.claude" ] || command -v claude &> /dev/null; then
        found_dirs+=("$HOME/.claude/skills")
    fi
    if [ -d "$HOME/.codex" ]; then
        found_dirs+=("$HOME/.codex/skills")
    fi

    # If no specific directories found, suggest defaults
    if [ ${#found_dirs[@]} -eq 0 ]; then
        # If in a project directory (has .git), suggest project-level
        if [ -d ".git" ]; then
            found_dirs+=(".cursor/skills")
        else
            # Otherwise suggest user-level
            found_dirs+=("$HOME/.cursor/skills")
        fi
    fi

    # Return found directories via stdout (not stderr!)
    echo "${found_dirs[@]}"
}

# Download skill package
download_skill() {
    print_info "Downloading DMtools skill..."

    # Get latest release download URL from GitHub API
    local API_URL="https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    local DOWNLOAD_URL=$(curl -s "$API_URL" | grep "browser_download_url.*\.zip" | head -1 | cut -d '"' -f 4)
    local FALLBACK_URL="https://github.com/$GITHUB_REPO/archive/refs/heads/main.zip"

    # Try latest release first
    if [ -n "$DOWNLOAD_URL" ] && curl -L -f -o "$TEMP_DIR/dmtools-skill.zip" "$DOWNLOAD_URL" 2>/dev/null; then
        print_success "Downloaded latest release"
    else
        # Fallback to main branch
        print_info "Downloading from main branch..."
        if curl -L -f -o "$TEMP_DIR/dmtools-skill.zip" "$FALLBACK_URL" 2>/dev/null; then
            print_success "Downloaded from repository"
        else
            print_error "Failed to download skill"
            return 1
        fi
    fi

    # Extract
    print_info "Extracting skill package..."
    unzip -q "$TEMP_DIR/dmtools-skill.zip" -d "$TEMP_DIR"

    # Find the skill directory
    local SKILL_SOURCE=""
    if [ -f "$TEMP_DIR/SKILL.md" ]; then
        # Direct extraction (from release ZIP)
        SKILL_SOURCE="$TEMP_DIR"
    elif [ -f "$TEMP_DIR/dmtools-main/dmtools-ai-docs/SKILL.md" ]; then
        # From GitHub main branch archive
        SKILL_SOURCE="$TEMP_DIR/dmtools-main/dmtools-ai-docs"
    elif [ -f "$TEMP_DIR/dmtools-ai-docs/SKILL.md" ]; then
        # Legacy: direct dmtools-ai-docs folder
        SKILL_SOURCE="$TEMP_DIR/dmtools-ai-docs"
    else
        # Debug: show what we found
        print_error "SKILL.md not found in package"
        print_info "Contents of temp dir:"
        ls -la "$TEMP_DIR" >&2 | head -10
        return 1
    fi

    # Return the skill source path via stdout (not stderr!)
    echo "$SKILL_SOURCE"
}

# Install skill to a directory
install_to_directory() {
    local SKILL_SOURCE="$1"
    local TARGET_DIR="$2"

    # Create target directory
    mkdir -p "$TARGET_DIR"

    # Remove old version if exists
    if [ -d "$TARGET_DIR/$SKILL_NAME" ]; then
        print_info "Removing old version..."
        rm -rf "$TARGET_DIR/$SKILL_NAME"
    fi

    # Copy skill
    cp -r "$SKILL_SOURCE" "$TARGET_DIR/$SKILL_NAME"
    print_success "Installed to $TARGET_DIR/$SKILL_NAME"
}

# Main installation
main() {
    print_header

    # Download skill
    local SKILL_SOURCE=$(download_skill)
    if [ -z "$SKILL_SOURCE" ]; then
        print_error "Failed to download skill"
        exit 1
    fi

    # Detect available directories
    print_info "Detecting skill directories..."
    local DIRS=($(detect_skill_dirs))

    if [ ${#DIRS[@]} -eq 0 ]; then
        print_error "No skill directories found"
        exit 1
    fi

    # Show found directories
    echo "" >&2
    echo "Found skill directories:" >&2
    for i in "${!DIRS[@]}"; do
        echo "  $((i+1)). ${DIRS[$i]}" >&2
    done

    # Determine installation choice
    echo "" >&2
    local CHOICE=""

    if [ ${#DIRS[@]} -eq 1 ]; then
        # Only one option, use it
        CHOICE="1"
        local SELECTED_DIR="${DIRS[0]}"
        echo "Installing to: $SELECTED_DIR" >&2
    else
        # Multiple options - check for non-interactive mode
        if [ "$INSTALL_ALL" = true ] || [ "${INSTALL_LOCATION}" = "all" ] || [ "${INSTALL_LOCATION}" = "ALL" ]; then
            CHOICE="all"
        elif [ -n "${INSTALL_LOCATION}" ]; then
            # Use environment variable
            CHOICE="${INSTALL_LOCATION}"
        elif [ ! -t 0 ]; then
            # Non-interactive (piped input) - default to first location
            print_info "Non-interactive mode detected, installing to first location"
            CHOICE="1"
        else
            # Interactive mode - ask user
            echo "Where would you like to install? (Enter number or 'all' for all locations)" >&2
            read -r CHOICE
        fi

        if [ "$CHOICE" = "all" ] || [ "$CHOICE" = "ALL" ]; then
            # Install to all directories
            echo "Installing to all locations..." >&2
            for DIR in "${DIRS[@]}"; do
                install_to_directory "$SKILL_SOURCE" "$DIR"
            done
            SELECTED_DIR="multiple locations"
        else
            # Install to selected directory
            local INDEX=$((CHOICE - 1))
            if [ $INDEX -ge 0 ] && [ $INDEX -lt ${#DIRS[@]} ]; then
                SELECTED_DIR="${DIRS[$INDEX]}"
                install_to_directory "$SKILL_SOURCE" "$SELECTED_DIR"
            else
                print_error "Invalid choice: $CHOICE"
                exit 1
            fi
        fi
    fi

    # If single directory selected and it doesn't exist, create it
    if [ "$SELECTED_DIR" != "multiple locations" ] && [ ! -d "$SELECTED_DIR" ]; then
        mkdir -p "$SELECTED_DIR"
    fi

    # Install to single directory if not "all"
    if [ "$SELECTED_DIR" != "multiple locations" ]; then
        install_to_directory "$SKILL_SOURCE" "$SELECTED_DIR"
    fi

    # Cleanup
    rm -rf "$TEMP_DIR"

    # Success message
    echo "" >&2
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}" >&2
    echo -e "${GREEN}        DMtools Skill Installed Successfully!       ${NC}" >&2
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}" >&2
    echo "" >&2
    echo "The DMtools skill is now available in your AI assistant!" >&2
    echo "" >&2
    echo -e "${CYAN}You can now:${NC}" >&2
    echo "  • Type /dmtools in chat to invoke the skill" >&2
    echo "  • Ask about DMtools and the assistant will use the skill automatically" >&2
    echo "" >&2
    echo -e "${BLUE}Example questions:${NC}" >&2
    echo "  • How do I install DMtools?" >&2
    echo "  • Help me configure Jira integration" >&2
    echo "  • Show me how to create JavaScript agents" >&2
    echo "  • Generate test cases from user story PROJ-123" >&2
    echo "" >&2

    # Platform-specific instructions
    if [[ "$SELECTED_DIR" == *"cursor"* ]]; then
        echo -e "${YELLOW}For Cursor:${NC}" >&2
        echo "  • Open Cursor Settings (Cmd+Shift+J or Ctrl+Shift+J)" >&2
        echo "  • Navigate to Rules → Agent Decides" >&2
        echo "  • You should see 'dmtools' in the skills list" >&2
    elif [[ "$SELECTED_DIR" == *"claude"* ]]; then
        echo -e "${YELLOW}For Claude:${NC}" >&2
        echo "  • The skill is available in your Claude desktop app" >&2
        echo "  • Type /dmtools or mention DMtools in your questions" >&2
    fi

    echo "" >&2
    echo "For more information: https://github.com/IstiN/dmtools" >&2
}

# Handle arguments
case "${1:-install}" in
    install)
        main
        ;;
    --help|-h)
        echo "DMtools Agent Skill Installer"
        echo ""
        echo "Usage: $0 [install|--help]"
        echo ""
        echo "This script installs the DMtools skill for AI assistants that"
        echo "support the Agent Skills standard (Cursor, Claude, Codex, etc.)"
        echo ""
        echo "The installer will:"
        echo "  1. Detect available skill directories"
        echo "  2. Download the latest DMtools skill"
        echo "  3. Install to your chosen location(s)"
        echo ""
        echo "Learn more: https://agentskills.io"
        ;;
    *)
        print_error "Unknown command: $1"
        echo "Use --help for usage information"
        exit 1
        ;;
esac