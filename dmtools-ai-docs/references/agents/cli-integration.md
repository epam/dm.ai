# CLI Agent Integration with Teammate

**→ See also: [Agent Best Practices](best-practices.md) for critical patterns and common mistakes to avoid**

## Overview

Teammate supports integration with external CLI agents (Cursor, Claude, Copilot, Codex, Gemini CLI, etc.) for advanced code generation and analysis tasks that require full workspace context.

## ⚠️ CRITICAL: When to Use CLI Integration

**Use CLI commands with `skipAIProcessing: true` when:**
- You need **full codebase context** (entire workspace, not just selected files)
- The task requires **code generation** with access to project structure
- You want to leverage **specialized CLI agents** (cursor-agent, claude CLI, etc.)
- The task is **code-heavy** and benefits from IDE-aware tools

**How it works:**
1. Teammate prepares context in `input` folder
2. CLI agent reads context and processes with full workspace access
3. CLI agent writes results to `output` folder
4. Post-action JavaScript processes files from `output` folder

## Architecture: Input → CLI → Output Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ TEAMMATE                                                        │
│                                                                 │
│ 1. Prepare Context                                             │
│    ├─ Load ticket data from Jira/ADO                          │
│    ├─ Process agent params (role, instructions, etc.)         │
│    ├─ Gather linked tickets, comments, attachments            │
│    └─ Create input/ folder with:                              │
│         ├─ request.md (ticket JSON + params)                  │
│         └─ [other context files]                              │
│                                                                 │
│ 2. Execute CLI Commands                                        │
│    └─ Run: cursor-agent / claude / copilot / gemini          │
│         ├─ CLI reads: input/request.md                        │
│         ├─ CLI accesses: full workspace/codebase             │
│         └─ CLI writes: output/response.md                     │
│                                                                 │
│ 3. Skip AI Processing (skipAIProcessing: true)                │
│    └─ Use CLI output as final response                        │
│                                                                 │
│ 4. Post-Process Output                                         │
│    └─ JavaScript post-action reads output/ files              │
│         ├─ Parse generated code                               │
│         ├─ Create pull request                                │
│         ├─ Update Jira ticket                                 │
│         └─ Post comments with summary                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Configuration Pattern

### Basic CLI Integration Configuration

```json
{
  "name": "Teammate",
  "params": {
    "metadata": {
      "contextId": "code_generation"
    },
    "agentParams": {
      "aiRole": "Senior Software Engineer",
      "instructions": [
        "./agents/instructions/development/implementation_instructions.md",
        "**IMPORTANT** Read all files from 'input' folder for context",
        "Implement the ticket requirements with code, tests, and documentation",
        "Write comprehensive summary to outputs/response.md"
      ],
      "knownInfo": "Project uses Java 23, Spring Boot, and React",
      "formattingRules": "./agents/instructions/development/formatting_rules.md"
    },
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh \"Implement the ticket. Read context from 'input' folder, write results to 'output' folder.\""
    ],
    "skipAIProcessing": true,
    "outputType": "none",
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/developTicketAndCreatePR.js",
    "inputJql": "key = PROJ-123"
  }
}
```

### ⚠️ CRITICAL Parameters

| Parameter | Value | Why? |
|-----------|-------|------|
| `skipAIProcessing` | **`true`** | CLI agent generates the response, not DMtools AI |
| `outputType` | `none` or `field` | Usually `none` since post-action handles output |
| `cliCommands` | Array of commands | Scripts that run CLI agents |
| `postJSAction` | JS file path | Processes CLI output files |

## Input Folder Structure

Teammate automatically creates `input/` folder with context:

```
input/
├── request.md           # Main context file
│   ├── Ticket data (JSON)
│   ├── Agent parameters
│   ├── Instructions
│   └── Known info
└── [additional files]   # Optional: design files, specs, etc.
```

**request.md format:**
```json
{
  "ticket": {
    "key": "PROJ-123",
    "summary": "Implement user authentication",
    "description": "Add JWT-based authentication...",
    "fields": { ... }
  },
  "agentParams": {
    "aiRole": "Senior Software Engineer",
    "instructions": [...],
    "knownInfo": "..."
  }
}
```

## CLI Agent Instructions Pattern

**Template for CLI agent prompts:**

```bash
"Read context from 'input' folder - contains ticket details and requirements.
Implement the ticket with code, tests, and documentation.
Write summary to output/response.md explaining your approach.
DO NOT create branches or push - focus on implementation only."
```

**Key points:**
- ✅ Explicitly tell CLI to read from `input/`
- ✅ Explicitly tell CLI to write to `output/`
- ✅ Specify what to implement (code, tests, docs)
- ✅ Specify what NOT to do (push, create PRs, etc.)

## CLI Agent Examples

### Cursor Agent (cursor-agent)

**Shell script** (`cicd/scripts/run-cursor-agent.sh`):
```bash
#!/bin/bash
# Run cursor-agent with workspace access

PROMPT="$1"

# cursor-agent has access to full workspace via .cursor/workspace.json
cursor-agent "$PROMPT"

# Output is written to output/response.md by cursor-agent
```

**Configuration:**
```json
{
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh \"Read requirements from input/request.md. Implement the feature with tests. Write summary to output/response.md.\""
  ],
  "skipAIProcessing": true
}
```

### Claude CLI

**Configuration:**
```json
{
  "cliCommands": [
    "claude --input input/request.md --output output/response.md --prompt 'Implement the ticket requirements'"
  ],
  "skipAIProcessing": true
}
```

### GitHub Copilot CLI

**Configuration:**
```json
{
  "cliCommands": [
    "gh copilot suggest \"$(cat input/request.md)\" > output/response.md"
  ],
  "skipAIProcessing": true
}
```

### Gemini CLI (ai CLI)

**Configuration:**
```json
{
  "cliCommands": [
    "ai \"Implement: $(cat input/request.md)\" --model gemini-2.0-flash > output/response.md"
  ],
  "skipAIProcessing": true
}
```

## Output Folder Structure

CLI agents should write results to `output/` folder:

```
output/
├── response.md          # Main summary/response (REQUIRED)
├── code/                # Generated code files
│   ├── UserAuth.java
│   └── UserAuthTest.java
├── changes.diff         # Git diff of changes
└── summary.json         # Structured output (optional)
```

**response.md format:**
```markdown
# Implementation Summary

## Changes Made
- Added JWT authentication service
- Created UserAuthController
- Added unit tests

## Files Modified
- src/main/java/auth/UserAuthService.java (created)
- src/test/java/auth/UserAuthServiceTest.java (created)

## Testing
All tests passing:
- UserAuthServiceTest: 8/8 passed
- Integration tests: 3/3 passed

## Next Steps
- Code review required
- Deploy to staging for QA testing
```

## Post-Action JavaScript Processing

**Example** (`agents/js/developTicketAndCreatePR.js`):

```javascript
/**
 * Process CLI agent output and create pull request
 * Runs AFTER CLI agent completes
 */
function action(params) {
    const ticket = params.ticket;
    const response = params.response; // Content from output/response.md

    // 1. Read CLI output files
    const outputFiles = file_read("output/response.md");
    const changesSummary = parseResponse(outputFiles);

    // 2. Check if code was generated
    if (!changesSummary.filesModified || changesSummary.filesModified.length === 0) {
        jira_post_comment(ticket.key, "No code changes generated by CLI agent.");
        return { success: false, reason: "No changes" };
    }

    // 3. Create git branch
    const branchName = `feature/${ticket.key}-${sanitize(ticket.fields.summary)}`;
    cli_execute_command(`git checkout -b ${branchName}`);

    // 4. Stage changes
    cli_execute_command(`git add .`);

    // 5. Create commit
    const commitMessage = `${ticket.key}: ${ticket.fields.summary}\n\n${changesSummary.description}\n\nGenerated by DMtools + cursor-agent`;
    cli_execute_command(`git commit -m "${commitMessage}"`);

    // 6. Push branch
    cli_execute_command(`git push -u origin ${branchName}`);

    // 7. Create pull request
    const prBody = `
## Summary
${changesSummary.description}

## Changes
${changesSummary.filesModified.map(f => `- ${f}`).join('\n')}

## Testing
${changesSummary.testResults}

Jira: ${ticket.key}
Generated by: DMtools + cursor-agent
    `;

    cli_execute_command(`gh pr create --title "${ticket.key}: ${ticket.fields.summary}" --body "${prBody}" --base main`);

    // 8. Update Jira ticket
    jira_post_comment(ticket.key,
        `Pull request created: [View PR](${prUrl})\n\nCLI Agent Summary:\n${response}`
    );

    return {
        success: true,
        branch: branchName,
        pr: prUrl
    };
}

function parseResponse(content) {
    // Parse CLI agent output
    return {
        description: extractSection(content, "Changes Made"),
        filesModified: extractList(content, "Files Modified"),
        testResults: extractSection(content, "Testing")
    };
}
```

## Complete Example: Story Development

### Configuration (`agents/story_development.json`)

```json
{
  "name": "Teammate",
  "params": {
    "metadata": {
      "contextId": "story_development"
    },
    "agentParams": {
      "aiRole": "Senior Java Software Engineer",
      "instructions": [
        "./agents/instructions/development/implementation_instructions.md",
        "./agents/instructions/common/code_quality.md",
        "Read ticket requirements from input/request.md",
        "Implement with code, unit tests, and documentation",
        "Compile and run tests before finishing",
        "Write detailed summary to output/response.md"
      ],
      "knownInfo": "Project: Spring Boot 3.x, Java 23, PostgreSQL",
      "formattingRules": "./agents/instructions/development/formatting_rules.md",
      "fewShots": "./agents/instructions/development/few_shots.md"
    },
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh \"User request is in 'input' folder. Implement the ticket requirements including code, tests, and documentation. Compile and run tests. Write comprehensive summary to output/response.md. DO NOT create branches or push.\""
    ],
    "skipAIProcessing": true,
    "outputType": "none",
    "ticketContextDepth": 1,
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/developTicketAndCreatePR.js",
    "inputJql": "project = PROJ AND status = 'To Do' AND labels = 'ready-for-dev'"
  }
}
```

### Pre-Action (`agents/js/checkWipLabel.js`)

```javascript
/**
 * Check if ticket has WIP label - skip if yes
 */
function action(params) {
    const ticket = params.ticket;
    const labels = ticket.fields.labels || [];

    if (labels.includes('WIP') || labels.includes('in-progress')) {
        console.log(`Ticket ${ticket.key} has WIP label - skipping`);
        return false; // Skip this ticket
    }

    // Add WIP label to prevent concurrent processing
    jira_update_labels(ticket.key, [...labels, 'WIP']);

    return true; // Continue processing
}
```

### Post-Action (`agents/js/developTicketAndCreatePR.js`)

```javascript
/**
 * Create PR from CLI agent output
 */
function action(params) {
    const ticket = params.ticket;
    const response = params.response; // CLI output

    try {
        // Parse CLI response
        const summary = parseCliOutput(response);

        // Create branch and PR
        const branchName = `feature/${ticket.key}`;
        cli_execute_command(`git checkout -b ${branchName}`);
        cli_execute_command(`git add .`);
        cli_execute_command(`git commit -m "${ticket.key}: ${ticket.fields.summary}\n\n${summary.changes}"`);
        cli_execute_command(`git push -u origin ${branchName}`);

        const prUrl = createPullRequest(ticket, summary);

        // Update Jira
        jira_transition_issue(ticket.key, "In Review");
        jira_post_comment(ticket.key,
            `Implementation completed!\n\nPR: ${prUrl}\n\nSummary:\n${summary.description}`
        );
        jira_remove_label(ticket.key, "WIP");
        jira_add_label(ticket.key, "in-review");

        return { success: true, pr: prUrl };

    } catch (error) {
        // Handle errors
        jira_post_comment(ticket.key,
            `Error during development: ${error.message}`
        );
        jira_remove_label(ticket.key, "WIP");
        jira_add_label(ticket.key, "failed");

        return { success: false, error: error.message };
    }
}
```

## Use Cases

### 1. Story Implementation
```json
{
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh \"Implement story from input/\""],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/developTicketAndCreatePR.js"
}
```

### 2. Bug Fixing
```json
{
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh \"Fix bug described in input/\""],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/fixBugAndCreatePR.js"
}
```

### 3. Test Generation
```json
{
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh \"Generate tests for code in input/\""],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/addTestsAndCommit.js"
}
```

### 4. Documentation Generation
```json
{
  "cliCommands": ["claude --input input/ --output output/ --prompt 'Generate documentation'"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/updateDocumentation.js"
}
```

### 5. Story Description (Without Code Access)
```json
{
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh \"Write story description based on input/\""],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/assignForReview.js"
}
```

## Best Practices

### ✅ DO

1. **Always use `skipAIProcessing: true`** when CLI agent generates the response
2. **Explicitly instruct CLI** where to read input and write output
3. **Validate CLI output** in post-action before proceeding
4. **Handle errors gracefully** in post-action JavaScript
5. **Clean up temporary files** in post-action
6. **Use WIP labels** to prevent concurrent processing
7. **Test CLI commands** locally before deploying

### ❌ DON'T

1. **Don't use AI processing** when CLI agent does the work (`skipAIProcessing: false`)
2. **Don't assume CLI output format** - always parse and validate
3. **Don't let CLI agents push** - handle git operations in post-action
4. **Don't skip error handling** - CLI commands can fail
5. **Don't hardcode paths** - use relative paths from project root
6. **Don't commit without tests** - verify tests pass in post-action

## Troubleshooting

### CLI Agent Not Finding Input

**Problem**: CLI agent can't find `input/` folder

**Solution**: CLI commands run from project root - use relative paths:
```bash
# ✅ Correct
"Read from input/request.md"

# ❌ Wrong
"Read from /absolute/path/input/request.md"
```

### Output Not Generated

**Problem**: No files in `output/` folder

**Solution**: Check CLI agent instructions explicitly mention output:
```bash
"Write results to output/response.md"
```

### Post-Action Fails

**Problem**: Post-action JavaScript can't process output

**Solution**: Validate output exists and has expected format:
```javascript
function action(params) {
    const response = params.response;

    if (!response || response.trim().length === 0) {
        console.error("No CLI output received");
        return { success: false, reason: "No output" };
    }

    // Continue processing...
}
```

### AI Processing Still Runs

**Problem**: DMtools AI runs even with CLI agent

**Solution**: Ensure `skipAIProcessing: true` in configuration:
```json
{
  "skipAIProcessing": true  // CRITICAL
}
```

## Related Documentation

- [Teammate Job Reference](../jobs/README.md#teammate)
- [JavaScript Agents](javascript-agents.md)
- [Teammate Configuration Guide](teammate-configs.md)
- [GitHub Actions Workflow](../workflows/github-actions-teammate.md)

## Summary

CLI Integration with Teammate enables:
- ✅ Full workspace/codebase context access
- ✅ Specialized CLI agent capabilities (Cursor, Claude, Copilot, etc.)
- ✅ Complex code generation and analysis tasks
- ✅ Automated PR creation and ticket updates
- ✅ Flexible post-processing with JavaScript

**Key Pattern**: Input folder → CLI agent → Output folder → Post-action processing
