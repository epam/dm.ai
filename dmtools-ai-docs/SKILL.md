---
name: dmtools
description: Comprehensive documentation and assistance for DMtools - AI-powered development toolkit with 96+ MCP tools for Jira, Azure DevOps, Figma, Confluence, Teams, and test automation. Use when working with DMtools, configuring integrations, developing JavaScript agents, or generating test cases.
license: Apache-2.0
compatibility:
  - Java 23+
  - macOS, Linux, Windows (WSL)
metadata:
  version: 1.0.0
  author: DMtools Team
  repository: https://github.com/IstiN/dmtools
  documentation: https://github.com/IstiN/dmtools
---

# DMtools Development Assistant

Comprehensive knowledge base for DMtools - an AI-powered development toolkit that integrates with multiple platforms and provides 96+ MCP tools for automation.

## When to Use

Use this skill when:
- Installing or configuring DMtools
- Setting up integrations (Jira, Azure DevOps, Figma, Confluence, Teams)
- Configuring AI providers (Gemini, OpenAI, Claude, DIAL, Ollama)
- Developing JavaScript agents with MCP tools
- Generating test cases (Xray, Cucumber)
- Troubleshooting DMtools issues
- Working with dmtools.env configuration
- Creating AI teammate configurations

## Quick Reference

### Installation
```bash
curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash
```

### Basic Configuration
```bash
# dmtools.env
JIRA_BASE_PATH=https://company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64(email:token)
GEMINI_API_KEY=your-api-key
```

### Common Commands
```bash
dmtools list                          # List all 96+ MCP tools
dmtools jira_get_ticket PROJ-123      # Get Jira ticket
dmtools run agents/config.json        # Run configuration
```

## Core Capabilities

### 96+ MCP Tools Available

**Complete Reference**: [references/mcp-tools/README.md](references/mcp-tools/README.md) - Auto-generated from actual DMtools build

Current breakdown:
- **Jira** (52 tools): Ticket management, search, comments, Xray test management
- **Teams** (27 tools): Messages, chats, files, transcripts
- **Figma**: Design extraction, icons, layers, styles
- **Confluence**: Page management, search, content access
- **Teams**: Messages, chats, files, transcripts
- **AI**: Gemini, OpenAI, Claude (Bedrock), DIAL, Ollama
- **File & CLI**: File operations, command execution
- **SharePoint, KB, and more**

**Example tools**:
- `jira_get_ticket`, `jira_search_by_jql`, `jira_xray_create_test`
- `ado_get_work_item`, `ado_move_to_state`, `ado_add_comment`
- `figma_get_layers`, `figma_get_icons`, `figma_download_node_image`
- `teams_send_message`, `teams_messages_since`, `teams_download_file`
- `gemini_ai_chat`, `openai_ai_chat`, `bedrock_ai_chat`

### JavaScript Agent Pattern

All MCP tools are directly accessible as JavaScript functions in agents:

```javascript
function action(params) {
    try {
        // Direct MCP tool access
        const ticket = jira_get_ticket(params.ticketKey);
        const analysis = gemini_ai_chat(`Analyze: ${ticket.fields.description}`);

        // Process and return
        return { success: true, result: analysis };
    } catch (error) {
        return { success: false, error: error.toString() };
    }
}
```

## Detailed Documentation

### Installation & Setup
- [Installation Guide](references/installation/README.md) - Complete setup for all platforms
- [Troubleshooting](references/installation/troubleshooting.md) - Common issues and solutions

### Configuration Guides
- [Configuration Overview](references/configuration/README.md) - Environment variables and hierarchy
- **[JSON Configuration Rules](references/configuration/json-config-rules.md)** - Critical rules for job configurations
- [Jira Setup](references/configuration/integrations/jira.md) - API tokens and 52 tools
- [Azure DevOps](references/configuration/integrations/ado.md) - PAT setup and 23+ tools
- [Gemini AI](references/configuration/ai-providers/gemini.md) - Free tier configuration
- [Other AI Providers](references/configuration/ai-providers/) - OpenAI, Claude, DIAL, Ollama

### Jobs & Workflows
- **[Jobs Reference](references/jobs/README.md)** - Complete guide to all 23 jobs
- **[Teammate](references/jobs/README.md#teammate)** - Flexible AI assistant with custom instructions
- **[Expert](references/jobs/README.md#expert)** - Domain expert Q&A based on project context
- **[TestCasesGenerator](references/jobs/README.md#testcasesgenerator)** - Automated test case generation

### Development
- [JavaScript Agents](references/agents/javascript-agents.md) - GraalJS development with MCP tools
- [Teammate Configs](references/agents/teammate-configs.md) - JSON-based AI workflows
- [Test Generation](references/test-generation/xray-manual.md) - Xray test case creation

### CI/CD Workflows
- [GitHub Actions: AI Teammate](references/workflows/github-actions-teammate.md) - Automated ticket processing in GitHub Actions

## Common Tasks

### Configure Jira Integration
```bash
# 1. Generate API token at https://id.atlassian.com/manage-profile/security/api-tokens
# 2. Encode credentials
echo -n "email@company.com:token" | base64
# 3. Add to dmtools.env
JIRA_BASE_PATH=https://company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64_output_here
```

### Generate Test Cases

**IMPORTANT**: `"name"` must exactly match Job class name. See [JSON Configuration Rules](references/configuration/json-config-rules.md).

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "project = PROJ AND type = Story",
    "testCasesPriorities": "High, Medium, Low",
    "outputType": "creation",
    "testCaseIssueType": "Test",
    "existingTestCasesJql": "project = PROJ AND type = Test",
    "isFindRelated": true,
    "isGenerateNew": true
  }
}
```

### Create JavaScript Agent
```javascript
// agents/js/processTickets.js
function action(params) {
    const tickets = jira_search_by_jql(params.jql);

    for (const ticket of tickets) {
        // Process with AI
        const result = gemini_ai_chat(`Analyze: ${ticket.fields.summary}`);

        // Update ticket
        jira_post_comment(ticket.key, result);
    }

    return { processed: tickets.length };
}
```

## Best Practices

1. **Security**: Never commit credentials - use environment variables
2. **AI Provider**: Start with Gemini (free tier, 15 req/min)
3. **Testing**: Mock external APIs with Mockito
4. **Batch Processing**: Add delays to avoid rate limits
5. **Error Handling**: Always use try-catch in agents

## Troubleshooting Quick Reference

| Issue | Solution |
|-------|----------|
| "Java 23 required" | Run installer again, it auto-installs Java |
| "401 Unauthorized" | Check base64 encoding of Jira credentials |
| "Rate limit exceeded" | Add `sleep(1000)` between API calls |
| "Field not found" | Use `jira_get_fields` to find custom field IDs |

## Architecture Notes

- **Job System**: 20+ specialized jobs for workflows
- **Agent System**: Java and JavaScript agents for AI tasks
- **Configuration**: Hierarchy - env vars > dmtools.env > dmtools-local.env
- **Thread Safety**: JobContext with thread-local storage
- **DI Framework**: Dagger 2 for dependency injection

## Resources

- Repository: https://github.com/IstiN/dmtools
- MCP Tools Reference: https://github.com/IstiN/dmtools/blob/main/docs/README-MCP.md
- Issues: https://github.com/IstiN/dmtools/issues

## Ask Questions

If you need clarification on requirements or implementation details, ask the user for more information before proceeding.