#!/bin/bash

# DMtools Agent Skill Installer
# Works with Cursor, Claude, Codex, and any Agent Skills compatible system
# Installs to project-level directories (.cursor/skills, .claude/skills, .codex/skills)
# and to global ~/.claude/skills if ~/.claude exists (Claude Code / GitHub Copilot CLI)
#
# Usage:
#   curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash
#   # When piped (non-interactive): installs to ALL detected locations automatically
#
#   DMTOOLS_SKILLS=jira,github bash install.sh      # Install focused skills only
#   INSTALL_LOCATION=1 bash install.sh              # Install to first detected location only
#   bash install.sh --all                           # Install to all detected locations
#   bash install.sh --skill jira                    # Select one package explicitly
#   bash install.sh --skills=jira,github            # Allowed alias for multi-skill selection
#   bash install.sh --all-skills                    # Select every supported package
#   bash install.sh --skills=jira,unknown           # Fails and lists invalid names
#   bash install.sh --skills=jira,unknown --skip-unknown  # Warns and keeps valid skills
#   bash install.sh                             # Interactive mode: ask user to choose

set -e

INSTALL_ALL=false
SELECTED_SKILLS="${DMTOOLS_SKILLS:-}"
SKILLS_SOURCE="default"
INSTALL_ALL_SKILLS=false
SKIP_UNKNOWN_SKILLS=false
POSITIONAL_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --all|-a)
            INSTALL_ALL=true
            shift
            ;;
        --all-skills)
            SELECTED_SKILLS="all"
            INSTALL_ALL_SKILLS=true
            SKILLS_SOURCE="cli"
            shift
            ;;
        --skill)
            if [ -z "${2:-}" ]; then
                echo "Missing value for $1" >&2
                exit 1
            fi
            if [ -n "$SELECTED_SKILLS" ]; then
                SELECTED_SKILLS="${SELECTED_SKILLS},$2"
            else
                SELECTED_SKILLS="$2"
            fi
            SKILLS_SOURCE="cli"
            shift 2
            ;;
        --skill=*)
            if [ -n "$SELECTED_SKILLS" ]; then
                SELECTED_SKILLS="${SELECTED_SKILLS},${1#--skill=}"
            else
                SELECTED_SKILLS="${1#--skill=}"
            fi
            SKILLS_SOURCE="cli"
            shift
            ;;
        --skills|-s)
            if [ -z "${2:-}" ]; then
                echo "Missing value for $1" >&2
                exit 1
            fi
            SELECTED_SKILLS="$2"
            SKILLS_SOURCE="cli"
            shift 2
            ;;
        --skills=*)
            SELECTED_SKILLS="${1#--skills=}"
            SKILLS_SOURCE="cli"
            shift
            ;;
        --skip-unknown)
            SKIP_UNKNOWN_SKILLS=true
            shift
            ;;
        --help|-h)
            echo "DMtools Agent Skill Installer"
            echo ""
            echo "Usage:"
            echo "  $0 [options]"
            echo ""
            echo "Options:"
            echo "  --all, -a         Install to all detected locations"
            echo "  --skill <name>    Select a single skill package"
            echo "  --skills=<csv>    Allowed alias for comma-separated packages"
            echo "  --all-skills      Select all supported skill packages"
            echo "  --skip-unknown    Warn and continue when unknown skill names are supplied"
            echo "  --help, -h        Show this help message"
            echo ""
            echo "Environment Variables:"
            echo "  INSTALL_LOCATION   Set to number (1,2,3...) to select specific location"
            echo "  DMTOOLS_SKILLS     Comma-separated package list for non-interactive installs"
            echo ""
            echo "Examples:"
            echo "  curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash"
            echo "    → Non-interactive mode: installs to ALL detected locations automatically"
            echo ""
            echo "  curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skill jira"
            echo "    → Install only /dmtools-jira"
            echo ""
            echo "  curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills=jira,github"
            echo "    → Install only /dmtools-jira and /dmtools-github"
            echo ""
            echo "  bash install.sh"
            echo "    → Interactive mode: shows menu to choose location"
            echo ""
            echo "  INSTALL_LOCATION=1 bash install.sh"
            echo "    → Install to first location only"
            echo ""
            echo "  bash install.sh --all"
            echo "    → Install to all locations"
            echo ""
            echo "  bash install.sh --all-skills"
            echo "    → Select every supported skill package"
            echo ""
            echo "  bash install.sh --skills=jira,unknown"
            echo "    → Fails with a non-zero exit and lists the invalid names"
            echo ""
            echo "  bash install.sh --skills=jira,unknown --skip-unknown"
            echo "    → Downgrades invalid skill names to warnings and installs Jira"
            echo ""
            echo "Note: Installs to project-level and global (~/.claude/skills) directories"
            echo "      Run this command from your project root directory."
            exit 0
            ;;
        *)
            POSITIONAL_ARGS+=("$1")
            shift
            ;;
    esac
done
set -- "${POSITIONAL_ARGS[@]}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

GITHUB_REPO="epam/dm.ai"
TEMP_DIR=$(mktemp -d)
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

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

skill_asset_name() {
    case "$1" in
        dmtools) echo "dmtools-skill.zip" ;;
        jira) echo "dmtools-jira-skill.zip" ;;
        github) echo "dmtools-github-skill.zip" ;;
        ado) echo "dmtools-ado-skill.zip" ;;
        testrail) echo "dmtools-testrail-skill.zip" ;;
        *)
            print_error "Unsupported skill package: $1"
            return 1
            ;;
    esac
}

skill_install_name() {
    case "$1" in
        dmtools) echo "dmtools" ;;
        jira) echo "dmtools-jira" ;;
        github) echo "dmtools-github" ;;
        ado) echo "dmtools-ado" ;;
        testrail) echo "dmtools-testrail" ;;
        *)
            print_error "Unsupported skill package: $1"
            return 1
            ;;
    esac
}

skill_command_name() {
    case "$1" in
        dmtools) echo "/dmtools" ;;
        jira) echo "/dmtools-jira" ;;
        github) echo "/dmtools-github" ;;
        ado) echo "/dmtools-ado" ;;
        testrail) echo "/dmtools-testrail" ;;
        *)
            print_error "Unsupported skill package: $1"
            return 1
            ;;
    esac
}

skill_endpoint_path() {
    case "$1" in
        dmtools|jira|github|ado|testrail)
            echo "/dmtools/$1"
            ;;
        *)
            print_error "Unsupported skill package: $1"
            return 1
            ;;
    esac
}

normalize_skills() {
    local raw="${1-}"
    local normalized=()
    local invalid=()
    local include_all=false
    IFS=',' read -r -a requested <<< "$raw"
    for skill in "${requested[@]}"; do
        local trimmed
        trimmed=$(echo "$skill" | tr '[:upper:]' '[:lower:]' | xargs)
        [ -z "$trimmed" ] && continue
        case "$trimmed" in
            all)
                include_all=true
                ;;
            dmtools|jira|github|ado|testrail)
                normalized+=("$trimmed")
                ;;
            *)
                invalid+=("$trimmed")
                ;;
        esac
    done
    if [ "$include_all" = true ] || [ "$INSTALL_ALL_SKILLS" = true ]; then
        normalized=(dmtools jira github ado testrail)
    fi
    if [ ${#invalid[@]} -gt 0 ]; then
        local invalid_csv
        invalid_csv=$(IFS=,; echo "${invalid[*]}")
        if [ "$SKIP_UNKNOWN_SKILLS" = true ]; then
            print_info "Warning: Skipping unknown skills: $invalid_csv"
        else
            if [ ${#normalized[@]} -eq 0 ]; then
                print_error "No valid skills selected. Unknown skills: $invalid_csv. Allowed skills: dmtools,jira,github,ado,testrail"
            else
                print_error "Unknown skills: $invalid_csv. Use --skip-unknown to continue."
            fi
            return 1
        fi
    fi
    printf '%s\n' "${normalized[@]}"
}

join_by_comma() {
    local IFS=","
    printf '%s' "$*"
}

json_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

json_string_array() {
    local values=("$@")
    local json="["
    local first=true
    local value
    local escaped

    for value in "${values[@]}"; do
        escaped=$(json_escape "$value")
        if [ "$first" = false ]; then
            json+=", "
        fi
        json+="\"$escaped\""
        first=false
    done

    json+="]"
    printf '%s' "$json"
}

array_contains() {
    local needle="$1"
    shift
    local item
    for item in "$@"; do
        if [ "$item" = "$needle" ]; then
            return 0
        fi
    done
    return 1
}

remove_deselected_skills() {
    local target_dir="$1"
    shift
    local selected_skills=("$@")
    local known_skill

    for known_skill in dmtools jira github ado testrail; do
        if array_contains "$known_skill" "${selected_skills[@]}"; then
            continue
        fi

        local install_name
        install_name=$(skill_install_name "$known_skill") || return 1
        if [ -d "$target_dir/$install_name" ]; then
            rm -rf "$target_dir/$install_name"
            print_info "Removed deselected skill: $target_dir/$install_name"
        fi
    done
}

write_installed_skills_metadata() {
    local target_dir="$1"
    shift
    local selected_skills=("$@")
    local active_commands=()
    local skill_key

    for skill_key in "${selected_skills[@]}"; do
        active_commands+=("$(skill_command_name "$skill_key")")
    done

    local metadata_version
    metadata_version=$(resolve_metadata_version "$target_dir")
    local escaped_version
    escaped_version=$(json_escape "$metadata_version")
    local installed_skills_json
    local active_commands_json
    installed_skills_json=$(json_string_array "${selected_skills[@]}")
    active_commands_json=$(json_string_array "${active_commands[@]}")

    cat > "$target_dir/installed-skills.json" <<EOF
{
  "version": "$escaped_version",
  "installed_skills": $installed_skills_json,
  "active_commands": $active_commands_json
}
EOF

    print_success "Updated $target_dir/installed-skills.json"
}

write_endpoints_metadata() {
    local target_dir="$1"
    shift
    local selected_skills=("$@")
    local metadata_version
    metadata_version=$(resolve_metadata_version "$target_dir")
    local escaped_version
    escaped_version=$(json_escape "$metadata_version")
    local endpoints_json="["
    local first=true
    local skill_key

    for skill_key in "${selected_skills[@]}"; do
        local escaped_skill
        local escaped_endpoint
        escaped_skill=$(json_escape "$skill_key")
        escaped_endpoint=$(json_escape "$(skill_endpoint_path "$skill_key")")
        if [ "$first" = false ]; then
            endpoints_json+=", "
        fi
        endpoints_json+="{\"name\":\"$escaped_skill\",\"path\":\"$escaped_endpoint\"}"
        first=false
    done

    endpoints_json+="]"

    cat > "$target_dir/endpoints.json" <<EOF
{
  "version": "$escaped_version",
  "endpoints": $endpoints_json
}
EOF

    print_success "Updated $target_dir/endpoints.json"
}

read_metadata_version() {
    local metadata_path="$1"
    [ -s "$metadata_path" ] || return 1

    local version
    version=$(sed -n 's/^[[:space:]]*"version":[[:space:]]*"\([^"]*\)".*/\1/p' "$metadata_path" | head -n 1)
    [ -n "$version" ] || return 1
    printf '%s' "$version"
}

resolve_metadata_version() {
    local target_dir="$1"
    local version=""
    local version_file="$REPO_ROOT/gradle.properties"

    if version=$(read_metadata_version "$target_dir/endpoints.json"); then
        printf '%s' "$version"
        return 0
    fi

    if version=$(read_metadata_version "$target_dir/installed-skills.json"); then
        printf '%s' "$version"
        return 0
    fi

    if [ -f "$version_file" ]; then
        version=$(sed -n 's/^version=\(.*\)$/\1/p' "$version_file" | head -n 1 | tr -d '[:space:]')
        if [ -n "$version" ]; then
            case "$version" in
                v*) ;;
                *) version="v$version" ;;
            esac
            printf '%s' "$version"
            return 0
        fi
    fi

    printf '%s' "latest"
}

detect_skill_dirs() {
    local found_dirs=()

    if [ -d ".cursor/skills" ] || [ -d ".cursor" ]; then
        found_dirs+=(".cursor/skills")
    fi
    if [ -d ".claude/skills" ] || [ -d ".claude" ]; then
        found_dirs+=(".claude/skills")
    fi
    if [ -d ".codex/skills" ] || [ -d ".codex" ]; then
        found_dirs+=(".codex/skills")
    fi

    local home_claude="$HOME/.claude"
    if [ -d "$home_claude/skills" ] || [ -d "$home_claude" ]; then
        local already_added=false
        for d in "${found_dirs[@]}"; do
            if [ "$d" = ".claude/skills" ]; then
                already_added=true
                break
            fi
        done
        if [ "$already_added" = false ]; then
            found_dirs+=("$home_claude/skills")
        fi
    fi

    if [ ${#found_dirs[@]} -eq 0 ]; then
        found_dirs+=(".cursor/skills")
    fi

    echo "${found_dirs[@]}"
}

download_skill() {
    local skill_key="$1"
    local asset_name
    asset_name=$(skill_asset_name "$skill_key") || return 1
    local asset_path="$TEMP_DIR/$asset_name"
    local extract_dir="$TEMP_DIR/$skill_key"

    print_info "Downloading $(skill_install_name "$skill_key") package..."

    local release_url="https://github.com/$GITHUB_REPO/releases/latest/download/$asset_name"
    if curl -L -f -o "$asset_path" "$release_url" 2>/dev/null; then
        print_success "Downloaded $asset_name"
    elif [ "$skill_key" = "dmtools" ]; then
        local fallback_url="https://github.com/$GITHUB_REPO/archive/refs/heads/main.zip"
        print_info "Falling back to repository archive for dmtools..."
        if curl -L -f -o "$asset_path" "$fallback_url" 2>/dev/null; then
            print_success "Downloaded dmtools from repository"
        else
            print_error "Failed to download $asset_name"
            return 1
        fi
    else
        print_error "Failed to download $asset_name"
        print_info "Focused skill packages are published from release assets only."
        return 1
    fi

    print_info "Extracting $asset_name..."
    mkdir -p "$extract_dir"
    unzip -q "$asset_path" -d "$extract_dir"

    local skill_source=""
    if [ -f "$extract_dir/SKILL.md" ]; then
        skill_source="$extract_dir"
    elif [ -f "$extract_dir/dmtools-main/dmtools-ai-docs/SKILL.md" ]; then
        skill_source="$extract_dir/dmtools-main/dmtools-ai-docs"
    elif [ -f "$extract_dir/dmtools-ai-docs/SKILL.md" ]; then
        skill_source="$extract_dir/dmtools-ai-docs"
    else
        print_error "SKILL.md not found in $asset_name"
        print_info "Contents of extract dir:"
        ls -la "$extract_dir" >&2 | head -10
        return 1
    fi

    echo "$skill_source"
}

install_to_directory() {
    local skill_source="$1"
    local target_dir="$2"
    local skill_name="$3"

    mkdir -p "$target_dir"

    if [ -d "$target_dir/$skill_name" ]; then
        print_info "Removing old version..."
        rm -rf "$target_dir/$skill_name"
    fi

    mkdir -p "$target_dir/$skill_name"

    for item in "$skill_source"/*; do
        local item_name
        item_name=$(basename "$item")
        if [ "$item_name" = "install.sh" ] || [ "$item_name" = "skill-install.ps1" ] || [[ "$item_name" == *.zip ]] || [[ "$item_name" == *.tar.gz ]]; then
            continue
        fi
        cp -r "$item" "$target_dir/$skill_name/"
    done

    print_success "Installed to $target_dir/$skill_name"
}

main() {
    print_header

    if [ "$SKILLS_SOURCE" != "cli" ]; then
        if [ "${DMTOOLS_SKILLS+x}" = x ]; then
            SKILLS_SOURCE="env"
        else
            SKILLS_SOURCE="default"
        fi
    fi

    local requested_skills=()
    local normalized_skills_output
    normalized_skills_output=$(normalize_skills "$SELECTED_SKILLS") || return 1
    while IFS= read -r skill_key; do
        [ -n "$skill_key" ] && requested_skills+=("$skill_key")
    done <<< "$normalized_skills_output"
    if [ ${#requested_skills[@]} -eq 0 ] && [ "$SKILLS_SOURCE" = "default" ]; then
        requested_skills=("dmtools")
    fi

    if [ "$INSTALL_ALL_SKILLS" = true ]; then
        print_info "Installing all skills (source: $SKILLS_SOURCE)"
    fi
    local effective_skills_display
    effective_skills_display=$(join_by_comma "${requested_skills[@]}")
    if [ -z "$effective_skills_display" ]; then
        effective_skills_display="<none>"
    fi
    print_info "Effective skills: $effective_skills_display (source: $SKILLS_SOURCE)"

    print_info "Detecting skill directories..."
    local dirs
    dirs=($(detect_skill_dirs))

    if [ ${#dirs[@]} -eq 0 ]; then
        print_error "No skill directories found"
        exit 1
    fi

    echo "" >&2
    echo "Found skill directories:" >&2
    for i in "${!dirs[@]}"; do
        echo "  $((i+1)). ${dirs[$i]}" >&2
    done
    echo "" >&2
    echo "Selected skill packages:" >&2
    if [ ${#requested_skills[@]} -eq 0 ]; then
        echo "  - <none>" >&2
    else
        for skill_key in "${requested_skills[@]}"; do
            echo "  - $(skill_install_name "$skill_key") ($(skill_command_name "$skill_key"))" >&2
        done
    fi

    echo "" >&2
    local choice=""
    local selected_dir=""

    if [ ${#dirs[@]} -eq 1 ]; then
        selected_dir="${dirs[0]}"
        echo "Installing to: $selected_dir" >&2
    else
        if [ "$INSTALL_ALL" = true ] || [ "${INSTALL_LOCATION}" = "all" ] || [ "${INSTALL_LOCATION}" = "ALL" ]; then
            choice="all"
        elif [ -n "${INSTALL_LOCATION}" ]; then
            choice="${INSTALL_LOCATION}"
        elif [ ! -t 0 ]; then
            print_info "Non-interactive mode detected, installing to all detected locations"
            choice="all"
        else
            echo "Where would you like to install? (Enter number or 'all' for all locations)" >&2
            read -r choice
        fi

        if [ "$choice" = "all" ] || [ "$choice" = "ALL" ]; then
            echo "Installing to all locations..." >&2
            selected_dir="multiple locations"
        else
            local index=$((choice - 1))
            if [ $index -ge 0 ] && [ $index -lt ${#dirs[@]} ]; then
                selected_dir="${dirs[$index]}"
                mkdir -p "$selected_dir"
            else
                print_error "Invalid choice: $choice"
                exit 1
            fi
        fi
    fi

    local target_dirs=()
    if [ "$selected_dir" = "multiple locations" ]; then
        target_dirs=("${dirs[@]}")
    else
        target_dirs=("$selected_dir")
    fi

    local installed_commands=()
    for skill_key in "${requested_skills[@]}"; do
        local skill_source
        skill_source=$(download_skill "$skill_key")
        if [ -z "$skill_source" ]; then
            print_error "Failed to download $(skill_install_name "$skill_key")"
            exit 1
        fi
        local install_name
        install_name=$(skill_install_name "$skill_key")
        installed_commands+=("$(skill_command_name "$skill_key")")
        for dir in "${target_dirs[@]}"; do
            install_to_directory "$skill_source" "$dir" "$install_name"
        done
    done

    for dir in "${target_dirs[@]}"; do
        remove_deselected_skills "$dir" "${requested_skills[@]}"
        write_installed_skills_metadata "$dir" "${requested_skills[@]}"
        write_endpoints_metadata "$dir" "${requested_skills[@]}"
    done

    rm -rf "$TEMP_DIR"

    echo "" >&2
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}" >&2
    echo -e "${GREEN}        DMtools Skill Installed Successfully!       ${NC}" >&2
    echo -e "${GREEN}════════════════════════════════════════════════════${NC}" >&2
    echo "" >&2
    if [ ${#installed_commands[@]} -eq 0 ]; then
        echo "All DMtools skills were removed from the selected install location(s)." >&2
    else
        echo "The selected DMtools skills are now available in your AI assistant!" >&2
    fi
    echo "" >&2
    echo -e "${CYAN}You can now:${NC}" >&2
    if [ ${#installed_commands[@]} -eq 0 ]; then
        echo "  • Re-run the installer with DMTOOLS_SKILLS=<skill list> to install a focused package again" >&2
    else
        for command_name in "${installed_commands[@]}"; do
            echo "  • Type $command_name in chat to invoke the skill" >&2
        done
        echo "  • Ask about the installed DMtools areas and the assistant will use the matching skill automatically" >&2
    fi
    echo "" >&2
    echo -e "${BLUE}Example questions:${NC}" >&2
    if [ ${#installed_commands[@]} -eq 0 ]; then
        echo "  • How do I reinstall only the Jira DMtools skill?" >&2
        echo "  • Which DMtools skills are available to install?" >&2
    else
        echo "  • How do I install DMtools?" >&2
        echo "  • Help me configure Jira integration" >&2
        echo "  • Review GitHub pull requests with DMtools" >&2
        echo "  • Generate test cases from user story PROJ-123" >&2
    fi
    echo "" >&2

    if [ ${#installed_commands[@]} -eq 0 ]; then
        :
    elif [[ "$selected_dir" == *"cursor"* ]]; then
        echo -e "${YELLOW}For Cursor:${NC}" >&2
        echo "  • Open Cursor Settings (Cmd+Shift+J or Ctrl+Shift+J)" >&2
        echo "  • Navigate to Rules → Agent Decides" >&2
        echo "  • You should see the selected dmtools skills in the skills list" >&2
    elif [[ "$selected_dir" == *"claude"* ]]; then
        echo -e "${YELLOW}For Claude:${NC}" >&2
        echo "  • The selected skills are available in your Claude desktop app" >&2
        echo "  • Type the installed /dmtools* command or mention the matching integration in your questions" >&2
    fi

    echo "" >&2
    echo "For more information: https://github.com/epam/dm.ai" >&2
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    case "${1:-install}" in
        install)
            main
            ;;
        --help|-h)
            echo "DMtools Agent Skill Installer"
            echo ""
            echo "Usage: $0 [install] [--all] [--skill <name>] [--skills=<name,name>] [--all-skills] [--skip-unknown]"
            echo ""
            echo "This script installs the DMtools skill for AI assistants that"
            echo "support the Agent Skills standard (Cursor, Claude, Codex, etc.)"
            echo ""
            echo "The installer will:"
            echo "  1. Detect skill directories (.cursor, .claude, .codex, ~/.claude)"
            echo "  2. Download the selected DMtools skill package(s)"
            echo "  3. Install to ALL detected locations (when piped) or ask you to choose"
            echo ""
            echo "Behavior:"
            echo "  - Piped (curl | bash): Installs to ALL detected locations automatically"
            echo "  - Interactive: Shows menu to choose specific location(s)"
            echo "  - Focused installs: pass --skill jira or --skills=jira,github"
            echo "  - Invalid skill names cause a non-zero exit and list the invalid names"
            echo "  - --skip-unknown downgrades invalid skill names to warnings"
            echo ""
            echo "      Run from your project root directory."
            echo ""
            echo "Learn more: https://agentskills.io"
            ;;
        *)
            print_error "Unknown command: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
fi
