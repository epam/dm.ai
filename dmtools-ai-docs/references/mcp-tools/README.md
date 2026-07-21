# DMtools MCP Tools Reference

Complete reference for all MCP tools available in DMtools.

**Total Integrations**: 20
**Total Tools**: 289

*Auto-generated from `dmtools list` on: 2026-07-21 17:23:05*

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
| **ADO** | 38 | [ado-tools.md](ado-tools.md) |
| **ANTHROPIC** | 3 | [anthropic-tools.md](anthropic-tools.md) |
| **BEDROCK** | 2 | [bedrock-tools.md](bedrock-tools.md) |
| **CLI** | 1 | [cli-tools.md](cli-tools.md) |
| **CONFLUENCE** | 19 | [confluence-tools.md](confluence-tools.md) |
| **DIAL** | 2 | [dial-tools.md](dial-tools.md) |
| **FIGMA** | 19 | [figma-tools.md](figma-tools.md) |
| **FILE** | 5 | [file-tools.md](file-tools.md) |
| **GEMINI** | 2 | [gemini-tools.md](gemini-tools.md) |
| **GITHUB** | 35 | [github-tools.md](github-tools.md) |
| **GITLAB** | 30 | [gitlab-tools.md](gitlab-tools.md) |
| **JIRA** | 69 | [jira-tools.md](jira-tools.md) |
| **KB** | 5 | [kb-tools.md](kb-tools.md) |
| **MERMAID** | 3 | [mermaid-tools.md](mermaid-tools.md) |
| **OLLAMA** | 2 | [ollama-tools.md](ollama-tools.md) |
| **OPENAI** | 2 | [openai-tools.md](openai-tools.md) |
| **SHAREPOINT** | 2 | [sharepoint-tools.md](sharepoint-tools.md) |
| **TEAMS** | 31 | [teams-tools.md](teams-tools.md) |
| **TESTRAIL** | 17 | [testrail-tools.md](testrail-tools.md) |
| **VERTEX** | 2 | [vertex-tools.md](vertex-tools.md) |

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

### Issue Tracking

- [JIRA](jira-tools.md) - 69 tools
- [ADO](ado-tools.md) - 38 tools

### Communication

- [TEAMS](teams-tools.md) - 31 tools

### Design

- [FIGMA](figma-tools.md) - 19 tools

### Documentation

- [CONFLUENCE](confluence-tools.md) - 19 tools
- [SHAREPOINT](sharepoint-tools.md) - 2 tools

### AI Providers

- [GEMINI](gemini-tools.md) - 2 tools
- [OPENAI](openai-tools.md) - 2 tools
- [ANTHROPIC](anthropic-tools.md) - 3 tools
- [OLLAMA](ollama-tools.md) - 2 tools
- [BEDROCK](bedrock-tools.md) - 2 tools
- [DIAL](dial-tools.md) - 2 tools

### Authentication

- [TEAMS](teams-tools.md) - 31 tools

### File Operations

- [FILE](file-tools.md) - 5 tools

### CLI Operations

- [CLI](cli-tools.md) - 1 tools

### Knowledge Base

- [KB](kb-tools.md) - 5 tools

