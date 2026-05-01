# DMtools Agent Skill

Universal AI assistant skill for DMtools - works with Cursor, Claude, Codex, and any [Agent Skills](https://agentskills.io) compatible system.

## 🚀 Quick Install

**IMPORTANT**: This installer works with **project-level directories only**. Run it from your project root directory.

### One-Command Installation (Recommended)

```bash
# Run from your project root directory
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash
```

The installer will:
1. Detect project-level skill directories (.cursor/skills, .claude/skills, .codex/skills)
2. Download the latest DMtools skill
3. **Automatically install to ALL detected locations** (when piped)
4. Install only in your current project (no global installation)

**Example output:**
```
Found skill directories:
  1. .cursor/skills
  2. .claude/skills

Non-interactive mode detected, installing to all detected locations
✓ Installed to .cursor/skills/dmtools
✓ Installed to .claude/skills/dmtools
```

### Advanced Installation Options

```bash
# Interactive mode: choose specific location
bash install.sh

# Install to specific location only
INSTALL_LOCATION=1 bash install.sh    # First location (.cursor)
INSTALL_LOCATION=2 bash install.sh    # Second location (.claude)

# Install to all locations (explicit)
bash install.sh --all

# Install only selected focused skills
DMTOOLS_SKILLS=jira,github bash install.sh
bash install.sh --skills jira,github

# Show help
bash install.sh --help
```

## Install Only the Skills You Need

Use focused packages when you want dedicated slash commands such as `/dmtools-jira` or `/dmtools-github` instead of the full `/dmtools` bundle.

| Skill | Slash command | Latest package | Latest tarball |
|------|------|------|------|
| Full DMtools | `/dmtools` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-skill.tar.gz` |
| Jira | `/dmtools-jira` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-jira-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-jira-skill.tar.gz` |
| GitHub | `/dmtools-github` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-github-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-github-skill.tar.gz` |
| Azure DevOps | `/dmtools-ado` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-ado-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-ado-skill.tar.gz` |
| TestRail | `/dmtools-testrail` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-testrail-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-testrail-skill.tar.gz` |

### Installer Selection Examples

```bash
# Install only Jira
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills jira

# Install Jira + GitHub together
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash -s -- --skills jira,github

# Local installer usage
bash install.sh --skills ado,testrail
```

```powershell
# PowerShell installer usage
$env:DMTOOLS_SKILLS = "jira,github"
irm https://github.com/epam/dm.ai/releases/latest/download/skill-install.ps1 | iex
```

## 📦 Manual Installation

1. Download the latest `dmtools-skill-v<release>.zip` from [Releases](https://github.com/epam/dm.ai/releases/latest)
2. Extract to one of these **project-level directories** in your project root:

| Location | Platform | Description |
|----------|----------|-------------|
| `.cursor/skills/` | Cursor | Project-specific skill for Cursor IDE |
| `.claude/skills/` | Claude | Project-specific skill for Claude Desktop |
| `.codex/skills/` | Codex | Project-specific skill for Codex |

**Note**: The installer no longer supports global (user-level) installation. Each project should have its own skill installation to ensure version compatibility.

## 🎯 What's Included

This skill provides comprehensive DMtools knowledge:

- **Installation & Configuration** - Setup guides for all platforms
- **67+ MCP Tools** - Complete reference with examples
- **JavaScript Agents** - Development with GraalJS and MCP tools
- **Test Generation** - Xray, Cucumber, and automated testing
- **Integrations** - Jira, Azure DevOps, Figma, Confluence, Teams
- **AI Providers** - Gemini, OpenAI, Claude, DIAL, Ollama

## 💬 Usage

Once installed, the skill is available in your AI assistant:

### Cursor
- Type `/dmtools` to invoke explicitly
- Or mention DMtools and the agent will use the skill automatically
- View in: Settings → Rules → Agent Decides

### Claude
- Type `/dmtools` in chat
- Or ask about DMtools naturally

### Any AI Assistant
- The skill loads when relevant to your question
- Ask about installation, configuration, or development

## 📚 Example Questions

- "How do I install DMtools?"
- "Help me configure Jira integration for DMtools"
- "Show me how to create a JavaScript agent with MCP tools"
- "Generate test cases from user story PROJ-123"
- "Set up Gemini AI provider for DMtools"
- "Create a teammate configuration for test generation"

## 🏗️ Skill Structure

```
dmtools/
├── SKILL.md              # Main skill definition (Agent Skills standard)
└── references/           # Detailed documentation
    ├── installation/     # Setup guides
    ├── configuration/    # Integration configs
    ├── agents/          # Agent development
    ├── test-generation/ # Test automation
    └── examples/        # Code examples
```

## 🔄 Updating

To update to the latest version, run from your project root directory:

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash
```

**Note**: This will update the skill in your current project's skill directories.

## 🤝 Compatibility

This skill follows the [Agent Skills](https://agentskills.io) open standard and works with:
- ✅ Cursor IDE
- ✅ Claude Desktop
- ✅ Codex
- ✅ Any Agent Skills compatible system

## 📝 License

Apache License 2.0

## 🔗 Resources

- **DMtools Repository**: [github.com/epam/dm.ai](https://github.com/epam/dm.ai)
- **Agent Skills Standard**: [agentskills.io](https://agentskills.io)
- **Issues & Support**: [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)

---

*This skill enables AI assistants to provide comprehensive DMtools guidance and code generation.*
