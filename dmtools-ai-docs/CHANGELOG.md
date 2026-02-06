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

