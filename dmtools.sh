#!/bin/bash
# DMTools CLI Wrapper
# Usage: ./dmtools.sh [command] [args...]
# Supports: STDIN, heredoc, file input, and inline JSON

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
error() {
    echo -e "${RED}Error: $1${NC}" >&2
    exit 1
}

info() {
    echo -e "${GREEN}Info: $1${NC}" >&2
}

# Load environment variables from various .env files
load_env_files() {
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
                            local key="${BASH_REMATCH[1]}"
                            local value="${BASH_REMATCH[2]}"
                            # Remove surrounding quotes if present
                            value=$(echo "$value" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")
                            export "$key"="$value"
                        fi
                    fi
                done < "$env_file"
                info "Loaded environment variables from: $env_file"
            fi
        fi
    done
}

# Load environment files before proceeding
load_env_files

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

usage() {
    cat << EOF
DMTools CLI Wrapper

Usage:
  $0 list                           # List available MCP tools
  $0 run <json-file>                # Execute job with JSON config file
  $0 run <json-file> <encoded>      # Execute job with file + encoded overrides
  $0 <tool> [args...]              # Execute MCP tool with args
  $0 <tool> --data '{"json"}'      # Execute with inline JSON
  $0 <tool> --file params.json     # Execute with JSON file
  $0 <tool> --verbose              # Execute with debug output
  $0 <tool> <<EOF                  # Execute with heredoc
  {"json": "data"}
  EOF

Examples:
  $0 list
  $0 run job-config.json
  $0 run job-config.json "eyJvdmVycmlkZSI6InZhbHVlIn0="  # base64 encoded
  $0 run job-config.json "%7B%22override%22%3A%22value%22%7D"  # URL encoded
  $0 jira_get_ticket DMC-479 summary,description
  $0 jira_get_ticket --data '{"key": "DMC-479", "fields": ["summary"]}'
  $0 jira_get_ticket <<EOF
  {
    "key": "DMC-479",
    "fields": ["summary", "description"]
  }
  EOF

Environment Variables:
  DMTOOLS_INTEGRATIONS    Comma-separated list of integrations (jira,confluence,figma)
  
Environment Files:
  The script automatically loads environment variables from the following files (in order of precedence):
  1. .env (current directory)
  2. dmtools.env (current directory)  
  3. dmtools-local.env (current directory)
  4. .env (script directory)
  5. dmtools.env (script directory)
  6. dmtools-local.env (script directory)
  
  File format supports:
  - KEY=VALUE pairs
  - Comments (lines starting with #)
  - Quoted values: KEY="value with spaces"
  - Empty lines (ignored)
  
  Common variables:
  - JIRA_BASE_PATH, JIRA_EMAIL, JIRA_API_TOKEN
  - CONFLUENCE_BASE_PATH, CONFLUENCE_API_TOKEN  
  - FIGMA_API_KEY
  - GEMINI_API_KEY, OPEN_AI_API_KEY
  - GITHUB_TOKEN, GITLAB_TOKEN, BITBUCKET_TOKEN

Installation:
  If you don't have DMTools installed, run:
  curl https://github.com/IstiN/dmtools/releases/latest/download/install.sh -fsS | bash

EOF
    exit 0
}

# Parse arguments
if [ $# -eq 0 ]; then
    usage
fi

COMMAND="$1"
shift

# Handle special commands
case "$COMMAND" in
    "help"|"-h"|"--help")
        usage
        ;;
    "list")
        if [ $# -gt 0 ]; then
            # List with filter
            java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp list "$1" 2>/dev/null
        else
            # List all tools
            java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp list 2>/dev/null
        fi
        exit 0
        ;;
    "run")
        # Handle new run command with JSON file + optional encoded parameter
        if [ $# -lt 1 ]; then
            error "Run command requires at least one argument: json-file-path"
        fi
        
        JSON_FILE="$1"
        ENCODED_PARAM="${2:-}"
        
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
            java -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner run "$JSON_FILE" "$ENCODED_PARAM"
        else
            info "Executing job with file: $JSON_FILE"
            java -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner run "$JSON_FILE"
        fi
        exit $?
        ;;
esac

# Check if STDIN has data (heredoc/pipe)
STDIN_DATA=""
if [ ! -t 0 ]; then
    STDIN_DATA=$(cat)
fi

# Parse remaining arguments
ARGS=()
DATA=""
FILE=""
QUIET=true

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
        --quiet)
            QUIET=true
            shift
            ;;
        *)
            ARGS+=("$1")
            shift
            ;;
    esac
done

# Check if JAR file exists
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    error "DMTools JAR file not found. Please install DMTools first:
  curl https://github.com/IstiN/dmtools/releases/latest/download/install.sh -fsS | bash

Or if you're developing locally, build the project first:
  ./gradlew build

Note: Java 23 is required for DMTools to run."
fi

# Determine data source priority: STDIN > --data > --file > args
if [ -n "$STDIN_DATA" ]; then
    [ "$QUIET" = false ] && info "Reading JSON from STDIN (${#STDIN_DATA} characters)"
    [ "$QUIET" = false ] && info "Using STDIN data"
    if [ "$QUIET" = true ]; then
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --stdin-data "$STDIN_DATA" "${ARGS[@]}" 2>/dev/null
    else
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --stdin-data "$STDIN_DATA" "${ARGS[@]}" 2>/dev/null
    fi
elif [ -n "$DATA" ]; then
    [ "$QUIET" = false ] && info "Using inline data"
    if [ "$QUIET" = true ]; then
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --data "$DATA" "${ARGS[@]}" 2>/dev/null
    else
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --data "$DATA" "${ARGS[@]}" 2>/dev/null
    fi
elif [ -n "$FILE" ]; then
    if [ ! -f "$FILE" ]; then
        error "File not found: $FILE"
    fi
    [ "$QUIET" = false ] && info "Using file data: $FILE"
    FILE_DATA=$(cat "$FILE")
    if [ "$QUIET" = true ]; then
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --data "$FILE_DATA" "${ARGS[@]}" 2>/dev/null
    else
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" --data "$FILE_DATA" "${ARGS[@]}" 2>/dev/null
    fi
else
    [ "$QUIET" = false ] && info "Using positional arguments"
    if [ "$QUIET" = true ]; then
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" "${ARGS[@]}" 2>/dev/null
    else
        java -Dlog4j2.configurationFile=classpath:log4j2-cli.xml -Dlog4j.configuration=log4j2-cli.xml --add-opens java.base/sun.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -XX:-PrintWarnings -cp "$JAR_FILE" com.github.istin.dmtools.job.JobRunner mcp "$COMMAND" "${ARGS[@]}" 2>/dev/null
    fi
fi