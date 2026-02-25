# DMtools MCP Tools Reference

Complete reference for all MCP tools available in DMtools.

**Total Integrations**: 17
**Total Tools**: 168

*Updated: 2026-02-25*

## Quick Start

```bash
# List all available tools
dmtools list

# List tools for specific integration
dmtools list | jq '.tools[] | select(.name | startswith("jira_"))'

# Execute a tool
dmtools <tool_name> [arguments]
```

## Integrations

| Integration | Tools | Documentation |
|-------------|-------|---------------|
| **ADO** | 14 | [ado-tools.md](ado-tools.md) |
| **ANTHROPIC** | 2 | [anthropic-tools.md](anthropic-tools.md) |
| **BEDROCK** | 2 | [bedrock-tools.md](bedrock-tools.md) |
| **CLI** | 1 | [cli-tools.md](cli-tools.md) |
| **CONFLUENCE** | 17 | [confluence-tools.md](confluence-tools.md) |
| **DIAL** | 2 | [dial-tools.md](dial-tools.md) |
| **FIGMA** | 12 | [figma-tools.md](figma-tools.md) |
| **FILE** | 4 | [file-tools.md](file-tools.md) |
| **GEMINI** | 2 | [gemini-tools.md](gemini-tools.md) |
| **GITHUB** | 16 | [github-tools.md](github-tools.md) |
| **JIRA** | 52 | [jira-tools.md](jira-tools.md) |
| **KB** | 5 | [kb-tools.md](kb-tools.md) |
| **MERMAID** | 3 | [mermaid-tools.md](mermaid-tools.md) |
| **OLLAMA** | 2 | [ollama-tools.md](ollama-tools.md) |
| **OPENAI** | 2 | [openai-tools.md](openai-tools.md) |
| **SHAREPOINT** | 2 | [sharepoint-tools.md](sharepoint-tools.md) |
| **TEAMS** | 30 | [teams-tools.md](teams-tools.md) |

## Usage in JavaScript Agents

All MCP tools are directly accessible as JavaScript functions:

```javascript
// Direct MCP tool access
const ticket = jira_get_ticket('PROJ-123');
const workItem = ado_get_work_item(12345);
const response = gemini_ai_chat('Analyze this');
file_write('output.txt', 'content');
```

## Integration Categories

### Issue Tracking & Source Control

- [JIRA](jira-tools.md) - 52 tools
- [ADO](ado-tools.md) - 14 tools
- [GITHUB](github-tools.md) - 16 tools (pull requests, CI/CD monitoring)

### Communication

- [TEAMS](teams-tools.md) - 30 tools

### Design

- [FIGMA](figma-tools.md) - 12 tools

### Documentation

- [CONFLUENCE](confluence-tools.md) - 17 tools
- [SHAREPOINT](sharepoint-tools.md) - 2 tools

### AI Providers

- [GEMINI](gemini-tools.md) - 2 tools
- [OPENAI](openai-tools.md) - 2 tools
- [ANTHROPIC](anthropic-tools.md) - 2 tools
- [OLLAMA](ollama-tools.md) - 2 tools
- [BEDROCK](bedrock-tools.md) - 2 tools
- [DIAL](dial-tools.md) - 2 tools

### Authentication

- [TEAMS](teams-tools.md) - 30 tools

### File Operations

- [FILE](file-tools.md) - 4 tools

### CLI Operations

- [CLI](cli-tools.md) - 1 tools

### Knowledge Base

- [KB](kb-tools.md) - 5 tools

