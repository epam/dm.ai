#!/bin/bash
# DMTools CLI Wrapper
# Usage: ./dmtools.sh [command] [args...]
# Supports: STDIN, heredoc, file input, and inline JSON

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find Java command (bundled or system)
find_java_command() {
    # Check for bundled Java first (installed with dmtools)
    local bundled_java="$HOME/.dmtools/jre/bin/java"
    local bundled_java_exe="$HOME/.dmtools/jre/bin/java.exe"

    # Windows uses .exe extension
    if [ -x "$bundled_java_exe" ]; then
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

    # No Java found
    return 1
}

# Get Java command or error
JAVA_CMD=$(find_java_command 2>/dev/null) || {
    echo "Error: Java 23 is required but not found." >&2
    echo "Please install DMTools first:" >&2
    echo "  curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash" >&2
    exit 1
}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Brand colors (teal/cyan theme)
# Detect terminal capabilities and use appropriate color mode
# macOS Terminal often doesn't support 24-bit colors, so we check TERM_PROGRAM
USE_24BIT=false
if [ -n "$COLORTERM" ] && [[ "$COLORTERM" == *"truecolor"* ]]; then
    USE_24BIT=true
elif [ -n "$TERM_PROGRAM" ] && [[ "$TERM_PROGRAM" != "Apple_Terminal" ]]; then
    # For non-Apple terminals, check if 256color is supported
    if [ -n "$TERM" ] && [[ "$TERM" == *"256color"* ]]; then
        USE_24BIT=true
    fi
fi

if [ "$USE_24BIT" = true ]; then
    TEAL_LIGHT='\033[38;2;102;208;232m'      # #66D0E8 - Light teal
    TEAL_DARK='\033[38;2;51;153;204m'        # #3399CC - Darker blue-green
else
    # Use 256-color palette for better compatibility, or fallback to ANSI
    # For macOS Terminal, use bright cyan which looks good
    TEAL_LIGHT='\033[1;36m'                  # Bright cyan (works well in macOS Terminal)
    TEAL_DARK='\033[0;36m'                   # Cyan
fi
CYAN='\033[0;36m'
CYAN_BRIGHT='\033[1;36m'
WHITE='\033[1;37m'
BOLD='\033[1m'
RESET='\033[0m'

# Helper functions
error() {
    echo -e "${RED}Error: $1${NC}" >&2
    exit 1
}

info() {
    echo -e "${GREEN}Info: $1${NC}" >&2
}

# Execute java command with proper stderr handling
execute_java_command() {
    local java_cmd="$1"
    if [ "$DEBUG" = true ]; then
        # Show stderr - execute command directly
        eval "$java_cmd"
    else
        # Suppress stderr
        eval "$java_cmd 2>/dev/null"
    fi
}

# Load environment variables from various .env files
# Usage: load_env_files [quiet]
#   quiet: if set, don't print info messages
load_env_files() {
    local quiet_mode="${1:-false}"
    local env_files=(
        # Current working directory (highest priority)
        ".env"
        "dmtools.env"
        "dmtools-local.env"
        # Script directory (lower priority)
        "$SCRIPT_DIR/.env"
        "$SCRIPT_DIR/dmtools.env"
        "$SCRIPT_DIR/dmtools-local.env"
    )
    
    for env_file in "${env_files[@]}"; do
        if [ -f "$env_file" ] && [ -r "$env_file" ]; then
            # Check file permissions for security (should not be world-readable for sensitive data)
            if [ -f "$env_file" ]; then
                # Load the environment file, ignoring comments and empty lines
                while IFS= read -r line || [ -n "$line" ]; do
                    # Skip empty lines and comments
                    if [[ -n "$line" && ! "$line" =~ ^[[:space:]]*# ]]; then
                        # Export the variable if it's in KEY=VALUE format
                        if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
                            key="${BASH_REMATCH[1]}"
                            value="${BASH_REMATCH[2]}"
                            # Remove surrounding quotes if present
                            value=$(echo "$value" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")
                            export "$key"="$value"
                        fi
                    fi
                done < "$env_file"
                if [ "$quiet_mode" != "quiet" ]; then
                    info "Loaded environment variables from: $env_file"
                fi
            fi
        fi
    done
}

# Load environment files silently (will be reloaded with messages when command is executed)
load_env_files quiet

# Try to find JAR file in multiple locations
find_jar_file() {
    local jar_file=""
    
    # 1. Check if installed via installer (user's home directory)
    if [ -f "$HOME/.dmtools/dmtools.jar" ]; then
        jar_file="$HOME/.dmtools/dmtools.jar"
    # 2. Check local build directory (development)
    elif ls "$SCRIPT_DIR/build/libs"/*.jar >/dev/null 2>&1; then
        jar_file=$(find "$SCRIPT_DIR/build/libs" -name "*-all.jar" | head -1)
    # 3. Check for any JAR file in the script directory
    elif ls "$SCRIPT_DIR"/*.jar >/dev/null 2>&1; then
        jar_file=$(find "$SCRIPT_DIR" -name "dmtools*.jar" -o -name "*-all.jar" | head -1)
    # 4. Check parent directory build folder (if script is in subdirectory)
    elif ls "$SCRIPT_DIR/../build/libs"/*.jar >/dev/null 2>&1; then
        jar_file=$(find "$SCRIPT_DIR/../build/libs" -name "*-all.jar" | head -1)
    fi
    
    echo "$jar_file"
}

JAR_FILE=$(find_jar_file)

# Display branded banner
show_banner() {
    echo ""
    echo -e "${TEAL_LIGHT}╔══════════════════════════════════════════════════════════════════════════╗${RESET}"
    echo -e "${TEAL_LIGHT}║${RESET}                                                                          ${TEAL_LIGHT}║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ██████╗ ███╗   ███╗${TEAL_DARK}████████╗ ██████╗  ██████╗ ██╗     ███████╗${TEAL_LIGHT}        ║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ██╔══██╗████╗ ████║${TEAL_DARK}╚══██╔══╝██╔═══██╗██╔═══██╗██║     ██╔════╝${TEAL_LIGHT}        ║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ██║  ██║██╔████╔██║${TEAL_DARK}   ██║   ██║   ██║██║   ██║██║     ███████╗${TEAL_LIGHT}        ║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ██║  ██║██║╚██╔╝██║${TEAL_DARK}   ██║   ██║   ██║██║   ██║██║          ██║ ${TEAL_LIGHT}       ║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ██████╔╝██║ ╚═╝ ██║${TEAL_DARK}   ██║   ╚██████╔╝╚██████╔╝███████╗███████║${TEAL_LIGHT}        ║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}    ╚═════╝ ╚═╝     ╚═╝${TEAL_DARK}   ╚═╝    ╚═════╝  ╚═════╝ ╚══════╝╚══════╝${TEAL_LIGHT}        ║${RESET}"
    echo -e "${TEAL_LIGHT}║${RESET}                                                                          ${TEAL_LIGHT}║${RESET}"
    echo -e "${TEAL_LIGHT}║${RESET}                                                                          ${TEAL_LIGHT}║${RESET}"
    echo -e "${TEAL_LIGHT}║${WHITE}   Is it easier to train ${TEAL_DARK}thousands employees${WHITE} to write perfect ${TEAL_DARK}prompts${WHITE},${TEAL_LIGHT}    ║${RESET}"
    echo -e "${TEAL_LIGHT}║${RESET}      or to build a system of ${TEAL_DARK}expert agents${WHITE} for them to use?              ${TEAL_LIGHT}║${RESET}"
    echo -e "${TEAL_LIGHT}╚══════════════════════════════════════════════════════════════════════════╝${RESET}"
    echo ""
}

usage() {
    show_banner
    # Check if JAR file exists before calling JobRunner
    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        error "DMTools JAR file not found. Please install DMTools first:
  curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash

Or if you're developing locally, build the project first:
  ./gradlew build

Note: Java 23 is required for DMTools to run."
    fi
    execute_java_command "\"$JAVA_CMD\" -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp \"$JAR_FILE\" com.github.istin.dmtools.job.JobRunner"
    exit 0
}

# Parse arguments
if [ $# -eq 0 ]; then
    show_banner
    exit 0
fi

# Reload environment files with messages when executing commands
load_env_files

# Initialize variables for argument parsing
ARGS=()
DATA=""
FILE=""
QUIET=true
DEBUG=false
COMMAND=""

# Parse all arguments first to handle flags
while [[ $# -gt 0 ]]; do
    case $1 in
        --data)
            DATA="$2"
            shift 2
            ;;
        --file)
            FILE="$2"
            shift 2
            ;;
        --verbose)
            QUIET=false
            shift
            ;;
        --debug)
            QUIET=false
            DEBUG=true
            shift
            ;;
        --quiet)
            QUIET=true
            shift
            ;;
        *)
            if [ -z "$COMMAND" ]; then
                COMMAND="$1"
            else
                ARGS+=("$1")
            fi
            shift
            ;;
    esac
done

# Validate command exists
if [ -z "$COMMAND" ]; then
    echo "DMTools CLI - Use 'dmtools help' or 'dmtools --help' for usage information"
    exit 0
fi

# Handle special commands
case "$COMMAND" in
    "help"|"-h"|"--help")
        usage
        ;;
    "list")
        if [ ${#ARGS[@]} -gt 0 ]; then
            # List with filter
            execute_java_command "\"$JAVA_CMD\" -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp \"$JAR_FILE\" com.github.istin.dmtools.job.JobRunner mcp list \"${ARGS[0]}\""
        else
            # List all tools
            execute_java_command "\"$JAVA_CMD\" -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp \"$JAR_FILE\" com.github.istin.dmtools.job.JobRunner mcp list"
        fi
        exit 0
        ;;
    "run")
        # Handle new run command with JSON file + optional encoded parameter
        if [ ${#ARGS[@]} -lt 1 ]; then
            error "Run command requires at least one argument: json-file-path"
        fi
        
        JSON_FILE="${ARGS[0]}"
        ENCODED_PARAM="${ARGS[1]:-}"
        
        # Validate file exists and is readable
        if [ ! -f "$JSON_FILE" ]; then
            error "Configuration file not found: $JSON_FILE"
        fi
        
        if [ ! -r "$JSON_FILE" ]; then
            error "Configuration file is not readable: $JSON_FILE"
        fi
        
        # Execute run command with JobRunner
        if [ -n "$ENCODED_PARAM" ]; then
            info "Executing job with file: $JSON_FILE and encoded parameter"
            execute_java_command "\"$JAVA_CMD\" -cp \"$JAR_FILE\" com.github.istin.dmtools.job.JobRunner run \"$JSON_FILE\" \"$ENCODED_PARAM\""
        else
            info "Executing job with file: $JSON_FILE"
            execute_java_command "\"$JAVA_CMD\" -cp \"$JAR_FILE\" com.github.istin.dmtools.job.JobRunner run \"$JSON_FILE\""
        fi
        exit $?
        ;;
esac

# Check if JAR file exists
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    error "DMTools JAR file not found. Please install DMTools first:
  curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash

Or if you're developing locally, build the project first:
  ./gradlew build

Note: Java 23 is required for DMTools to run."
fi

# Determine log configuration based on debug mode
if [ "$DEBUG" = true ]; then
    LOG_CONFIG="log4j2-debug.xml"
else
    LOG_CONFIG="log4j2-cli.xml"
fi

# Check if command starts with - or --, then proxy directly to JobRunner
if [[ "$COMMAND" == -* ]]; then
    # Direct proxy to JobRunner for flags like --version, --help, etc.
    CMD_ARGS=("$COMMAND")
    if [ ${#ARGS[@]} -gt 0 ]; then
        CMD_ARGS+=("${ARGS[@]}")
    fi
    
    # Execute directly without mcp prefix
    if [ "$DEBUG" = true ]; then
        "$JAVA_CMD" -Dlog4j2.configurationFile=classpath:$LOG_CONFIG -Dlog4j.configuration=$LOG_CONFIG --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner "${CMD_ARGS[@]}"
    else
        "$JAVA_CMD" -Dlog4j2.configurationFile=classpath:$LOG_CONFIG -Dlog4j.configuration=$LOG_CONFIG --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner "${CMD_ARGS[@]}" 2>/dev/null
    fi
    exit $?
fi

# Proxy all other commands to JobRunner as MCP tools
# Build command arguments array
CMD_ARGS=("$COMMAND")
if [ ${#ARGS[@]} -gt 0 ]; then
    CMD_ARGS+=("${ARGS[@]}")
fi

# Check if STDIN has data (heredoc/pipe)
if [ ! -t 0 ]; then
    STDIN_DATA=$(cat)
    if [ -n "$STDIN_DATA" ]; then
        [ "$QUIET" = false ] && info "Reading JSON from STDIN (${#STDIN_DATA} characters)"
        CMD_ARGS+=("--stdin-data" "$STDIN_DATA")
    fi
fi

# Add --data or --file flags if provided
if [ -n "$DATA" ]; then
    [ "$QUIET" = false ] && info "Using inline data"
    CMD_ARGS+=("--data" "$DATA")
elif [ -n "$FILE" ]; then
    if [ ! -f "$FILE" ]; then
        error "File not found: $FILE"
    fi
    [ "$QUIET" = false ] && info "Using file data: $FILE"
    FILE_DATA=$(cat "$FILE")
    CMD_ARGS+=("--data" "$FILE_DATA")
fi

# Add verbose/debug flags
if [ "$QUIET" = false ]; then
    CMD_ARGS+=("--verbose")
fi
if [ "$DEBUG" = true ]; then
    CMD_ARGS+=("--debug")
fi

# Execute command via JobRunner as MCP tool
if [ "$DEBUG" = true ]; then
    "$JAVA_CMD" -Dlog4j2.configurationFile=classpath:$LOG_CONFIG -Dlog4j.configuration=$LOG_CONFIG --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "${CMD_ARGS[@]}"
else
    "$JAVA_CMD" -Dlog4j2.configurationFile=classpath:$LOG_CONFIG -Dlog4j.configuration=$LOG_CONFIG --add-opens java.base/java.lang=ALL-UNNAMED -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "${CMD_ARGS[@]}" 2>/dev/null
fi