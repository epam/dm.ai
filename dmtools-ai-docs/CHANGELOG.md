## [skill-v1.0.23] - 2026-02-15

### Added

- **Cross-platform CLI Prompt Passing via Temporary File** - Fixed Windows compatibility issue with `cliPrompt` parameter
  - **Problem**: Previous implementation used POSIX-style shell escaping (`\"`, `\$`, `\``) which doesn't work on Windows cmd.exe
  - **Solution**: Prompt content is written to temporary file, file path passed as parameter to CLI commands
  - **Benefits**:
    - ✅ Works on Windows (cmd.exe, PowerShell), Linux, macOS
    - ✅ No escaping issues with special characters (`"`, `$`, `` ` ``, `\`)
    - ✅ No length limits (supports 10K+ char prompts)
    - ✅ Backward compatible (scripts can detect if parameter is file)
  - **CLI Script Update Required**: Scripts should read prompt from file (see examples in cli-integration.md)
    ```bash
    # POSIX example
    if [ -f "$1" ]; then PROMPT=$(cat "$1"); else PROMPT="$1"; fi
    ```
    ```batch
    REM Windows cmd.exe example
    if exist "%~1" (set /p PROMPT=<"%~1") else (set PROMPT=%~1)
    ```
  - Updated documentation with examples for POSIX shell, Windows cmd.exe, and PowerShell
- **CLI Output Safety Parameters for Teammate** - Two new safety parameters protect against data loss when CLI commands fail
  - **`requireCliOutputFile`** (boolean, default: `true`) - Strict mode requires `outputs/response.md` before updating fields
    - ✅ If output file exists → Process normally (update field/post comment/create ticket)
    - ❌ If output file missing → Skip field update, post error comment instead
    - **Prevents data loss** - won't overwrite critical fields with error messages
    - Permissive mode (`false`) uses command output as fallback (backwards compatible)

  - **`cleanupInputFolder`** (boolean, default: `true`) - Controls cleanup of temporary input context folders
    - Enabled (default): Automatically deletes `input/[TICKET-KEY]/` folder after processing
    - Disabled: Keeps folder for debugging CLI issues (manual cleanup required)

  **Production-Safe Example:**
  ```json
  {
    "skipAIProcessing": true,
    "requireCliOutputFile": true,   // Strict mode (default)
    "cleanupInputFolder": true,     // Cleanup (default)
    "outputType": "field",
    "fieldName": "Description"
  }
  ```

  **Debug Mode Example:**
  ```json
  {
    "skipAIProcessing": true,
    "cleanupInputFolder": false,    // Keep for debugging
    "outputType": "comment"
  }
  ```

### Changed
- Updated Teammate configuration documentation with CLI safety parameters
- Updated CLI Integration guide with safety best practices and troubleshooting

### Documentation
- Added 16 unit tests for CLI safety features (all passing)
- Updated teammate-configs.md with safety parameter examples
- Updated cli-integration.md with production-safe and debug mode examples

## [skill-v1.0.22] - 2026-02-15

### Added
- **`cliPrompt` Field for Teammate Configurations** - New field separates CLI prompts from commands for cleaner, more maintainable configurations
  - **Multiple Input Types**: Supports plain text, local file paths, and Confluence URLs
  - **Automatic Processing**: Uses InstructionProcessor to fetch content from files or Confluence
  - **Shell Escaping**: Automatically escapes special characters (`\`, `"`, `$`, `` ` ``) to prevent injection
  - **Reusability**: Same prompt file or Confluence page can be used across multiple configurations
  - **Backwards Compatible**: Existing configs with inline prompts continue to work without changes

  **Example Migration:**
  ```json
  // Before (inline)
  "cliCommands": ["./script.sh \"Long prompt here...\""]

  // After (separate field)
  "cliPrompt": "Long prompt here...",
  "cliCommands": ["./script.sh"]

  // Or with file
  "cliPrompt": "./agents/prompts/dev_prompt.md",
  "cliCommands": ["./script.sh"]

  // Or with Confluence
  "cliPrompt": "https://company.atlassian.net/wiki/...",
  "cliCommands": ["./script.sh"]
  ```

### Changed
- Updated CLI Integration documentation with `cliPrompt` examples and migration guide
- Updated skill description to mention `cliPrompt` feature

### Documentation
- Updated all CLI integration examples to show new pattern
- Added 17 unit tests for `cliPrompt` functionality (all passing)

## [skill-v1.0.21] - 2026-02-06

### Fixed
- **Installer now installs to ALL detected locations in non-interactive mode** (fixes issue where only .cursor was installed when both .cursor and .claude existed)
- Changed default behavior: `curl | bash` now installs to all detected project-level directories automatically
- Users no longer need to use `INSTALL_LOCATION=all` when piping the installer
- Interactive mode still allows choosing specific location

### Changed
- Updated installer documentation to reflect new automatic multi-location installation behavior
- Clarified usage examples in README and help messages

## [skill-v1.0.20] - 2026-02-06

### Added
- **Agent Best Practices** - New comprehensive guide documenting 14 critical patterns learned from real-world agent development
  - Teammate Job Configuration Pattern (use Teammate, not standalone JS)
  - Template References Pattern (file refs, not inline text)
  - Output File Naming Convention (fixed names for sequential, unique for parallel)
  - Post-Action Responsibilities (field updates + comment posting)
  - OutputType Selection Guide (when to use "none", "field", "comment")
  - Configuration File Naming (human-friendly names for automation)
  - Pre-Action Usage Patterns (WIP marking, validation)
  - Conditional File Creation (only create description.md if needed)
  - CLI Command Structure (delegation to Copilot/Claude/Cursor)
  - Agent ID in Comments (traceability)
  - Attachment Preservation (CRITICAL - preserve media references)
  - Code Reusability (common helpers reduce code by 90%)
  - Correct MCP Tools (jira_assign_ticket_to vs jira_update_field)
  - Skill Description Length (keep short for performance)

### Changed
- **Installation now project-level only**: Installer no longer supports global (user-level) directories
- Updated `install.sh` to detect and install only to project-level directories (.cursor/skills, .claude/skills, .codex/skills)
- Removed global installation paths (~/.cursor/skills, ~/.claude/skills, ~/.codex/skills) from installer
- Default installation location is now .cursor/skills in current directory if no directories found
- Updated documentation to reflect project-level installation approach
- **Shortened skill description** for performance (200+ chars → ~120 chars)
- Simplified description: "DMtools - 96+ MCP tools for Jira, Azure DevOps, Figma, Confluence, Teams. Use for integrations, JavaScript agents, test generation."

### Rationale
- Ensures skill version matches project requirements
- Prevents conflicts between different project versions
- Clearer dependency management per project
- Faster skill loading with shorter description
- Documented critical patterns to prevent common mistakes

## [skill-v1.0.19] - 2026-02-02

### Added
- Comprehensive InstructionsGenerator job documentation with all parameters, use cases, and examples

## [skill-v1.0.18] - 2026-02-02

### Removed
- Removed MAINTAINING.md from user-facing skill (developer documentation should be in separate skill)

# Changelog

All notable changes to the DMtools Agent Skill will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

