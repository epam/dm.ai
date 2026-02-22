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
        "Write comprehensive summary to output/response.md"
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

### NEW: Using `cliPrompt` Field (Recommended)

The `cliPrompt` field separates CLI prompts from commands for cleaner configurations and supports multiple input types:

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
        "./agents/instructions/development/implementation_instructions.md"
      ],
      "knownInfo": "Project uses Java 23, Spring Boot, and React",
      "formattingRules": "./agents/instructions/development/formatting_rules.md"
    },
    "cliPrompt": "Implement the ticket. Read context from 'input' folder, write results to 'output' folder.",
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh"
    ],
    "skipAIProcessing": true,
    "outputType": "none",
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/developTicketAndCreatePR.js",
    "inputJql": "key = PROJ-123"
  }
}
```

**Benefits of `cliPrompt` field:**
- ✅ **Cleaner JSON**: Prompts separated from command strings
- ✅ **Multiple Input Types**: Supports plain text, local file paths, Confluence URLs, and GitHub URLs
- ✅ **Reusability**: Same prompt file/URL across multiple configurations
- ✅ **Automatic Processing**: Uses InstructionProcessor for content fetching
- ✅ **Shell Escaping**: Prevents injection by escaping special characters

**Supported Input Types:**

```json
// Plain text
"cliPrompt": "Implement the ticket from input/ folder"

// Local file path
"cliPrompt": "./agents/prompts/development_prompt.md"

// Confluence URL
"cliPrompt": "https://company.atlassian.net/wiki/spaces/DEV/pages/123/CLI+Prompt+Template"

// GitHub URL (public, or private with SOURCE_GITHUB_TOKEN)
"cliPrompt": "https://github.com/your-org/playbook/blob/main/prompts/implement.md"
```

**How it works:**
1. Teammate processes `cliPrompt` (fetches Confluence content or reads file if needed)
2. Creates temporary file with prompt content (UTF-8 encoded)
3. Passes file path as quoted parameter to each CLI command
4. Example: `./script.sh` becomes `./script.sh "/tmp/dmtools_cli_prompt_12345.txt"`
5. CLI script reads prompt from file (see examples below)
6. Temporary file auto-deletes on JVM exit

**Benefits:**
- ✅ **Cross-platform**: Works on Windows (cmd.exe, PowerShell), Linux, macOS
- ✅ **No escaping issues**: Special characters (`"`, `$`, `` ` ``, `\`) handled correctly
- ✅ **No length limits**: Long prompts (10K+ chars) work without shell limitations
- ✅ **Backward compatible**: Scripts can check if parameter is a file

### ⚠️ CRITICAL Parameters

| Parameter | Type | Default | Why? |
|-----------|------|---------|------|
| `skipAIProcessing` | Boolean | `false` | **CRITICAL**: CLI agent generates the response, not DMtools AI |
| `outputType` | String | - | Usually `none` since post-action handles output, or `field`/`comment` for direct updates |
| `cliPrompt` | String | - | **NEW v1.7.130+**: Prompt for CLI agent (plain text, local file path, Confluence URL, or GitHub URL) |
| `cliCommands` | Array | - | Scripts that run CLI agents |
| `requireCliOutputFile` | Boolean | `true` | **NEW v1.7.133+**: Require `output/response.md` before updating fields (prevents data loss) |
| `cleanupInputFolder` | Boolean | `true` | **NEW v1.7.133+**: Cleanup `input/[TICKET-KEY]/` folder after execution |
| `postJSAction` | String | - | JS file path - processes CLI output files |

### 🛡️ CLI Output Safety (v1.7.133+)

**Problem**: CLI commands can fail or not produce expected output, potentially overwriting critical fields with error messages.

**Solution**: Two safety parameters protect against data loss:

#### `requireCliOutputFile` (default: `true`)

Controls whether `output/response.md` must exist before processing output.

**Strict Mode** (recommended - default):
```json
{
  "requireCliOutputFile": true,  // Default - safe mode
  "skipAIProcessing": true,
  "outputType": "field",
  "fieldName": "Description"
}
```

**Behavior**:
- ✅ If `output/response.md` exists → Process normally (update field/post comment/create ticket)
- ❌ If `output/response.md` missing → **Skip field update**, post error comment instead
- **Prevents data loss** - won't overwrite fields with error messages

**Permissive Mode** (use with caution):
```json
{
  "requireCliOutputFile": false,  // Permissive mode
  "skipAIProcessing": true,
  "outputType": "field"
}
```

**Behavior**:
- Uses command stdout/stderr as fallback if `response.md` missing
- Less safe - may update fields with error messages
- Backwards compatible with pre-v1.7.133 behavior

#### `cleanupInputFolder` (default: `true`)

Controls whether temporary input context folders are cleaned up.

**Cleanup Enabled** (recommended - default):
```json
{
  "cleanupInputFolder": true  // Default - saves disk space
}
```

**Behavior**:
- Automatically deletes `input/[TICKET-KEY]/` folder after processing
- Removes temporary `request.md` and downloaded attachments

**Cleanup Disabled** (for debugging):
```json
{
  "cleanupInputFolder": false  // Keep for debugging
}
```

**Behavior**:
- Keeps `input/[TICKET-KEY]/` folder for manual inspection
- Useful for debugging CLI issues
- Check `request.md` and attachments: `cat input/PROJ-123/request.md`
- **Must manually cleanup**: `rm -rf input/PROJ-123`

#### Production-Safe Configuration Example

```json
{
  "name": "Teammate",
  "params": {
    "cliPrompt": "Implement the ticket from input/ folder",
    "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
    "skipAIProcessing": true,
    "requireCliOutputFile": true,   // Strict mode (default)
    "cleanupInputFolder": true,     // Cleanup (default)
    "outputType": "field",
    "fieldName": "Description",
    "operationType": "Replace",
    "initiator": "automation"
  }
}
```

**What happens on failure**:
- CLI command fails or doesn't create `output/response.md`
- Field "Description" is **NOT overwritten**
- Error comment posted: `@automation, ⚠️ CLI command execution issue: ...`
- Original field value remains intact ✅

#### Debug Mode Configuration Example

```json
{
  "name": "Teammate",
  "params": {
    "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
    "skipAIProcessing": true,
    "cleanupInputFolder": false,    // Keep input for debugging
    "outputType": "comment"
  }
}
```

**What happens**:
- Input folder kept at `input/PROJ-123/`
- Can inspect: `cat input/PROJ-123/request.md`
- Can check attachments: `ls -la input/PROJ-123/`
- **Remember to cleanup**: `rm -rf input/PROJ-123`

### Migration: Inline Prompt → `cliPrompt` Field

**Before** (inline prompt in command):
```json
{
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh \"**IMPORTANT** implementation details and development is not part of the task. Main request is in 'input' folder, read all files there and do what is requested following instructions from input. Request.md file contains json with all details.\""
  ]
}
```

**After** (separate `cliPrompt` field):
```json
{
  "cliPrompt": "**IMPORTANT** implementation details and development is not part of the task. Main request is in 'input' folder, read all files there and do what is requested following instructions from input. Request.md file contains json with all details.",
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh"
  ]
}
```

Both execute identically, but the second approach is:
- ✅ Easier to read and maintain
- ✅ Supports file paths and Confluence URLs
- ✅ Reusable across configurations
- ✅ 100% backwards compatible (existing configs still work)

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

**POSIX Shell script** (`cicd/scripts/run-cursor-agent.sh`):
```bash
#!/bin/bash
# Run cursor-agent with prompt from file

# Read prompt from file (passed as first argument)
PROMPT_FILE="$1"

# Check if argument is a file (backward compatibility)
if [ -f "$PROMPT_FILE" ]; then
    # New approach: Read from file
    PROMPT=$(cat "$PROMPT_FILE")
else
    # Old approach: Use argument as prompt directly
    PROMPT="$1"
fi

# cursor-agent has access to full workspace via .cursor/workspace.json
cursor-agent "$PROMPT"

# Output is written to output/response.md by cursor-agent
```

**Windows cmd.exe script** (`cicd\scripts\run-cursor-agent.bat`):
```batch
@echo off
REM Run cursor-agent with prompt from file

set PROMPT_FILE=%~1

REM Check if file exists (backward compatibility)
if exist "%PROMPT_FILE%" (
    REM New approach: Read from file
    set /p PROMPT=<"%PROMPT_FILE%"
) else (
    REM Old approach: Use argument as prompt
    set PROMPT=%~1
)

REM Run cursor-agent
cursor-agent "%PROMPT%"
```

**Windows PowerShell script** (`cicd/scripts/run-cursor-agent.ps1`):
```powershell
# Run cursor-agent with prompt from file

param(
    [string]$PromptFileOrText
)

# Check if argument is a file (backward compatibility)
if (Test-Path $PromptFileOrText -PathType Leaf) {
    # New approach: Read from file
    $Prompt = Get-Content $PromptFileOrText -Raw
} else {
    # Old approach: Use argument as prompt
    $Prompt = $PromptFileOrText
}

# Run cursor-agent
cursor-agent $Prompt
```

**Configuration (old pattern - inline prompt):**
```json
{
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh \"Read requirements from input/request.md. Implement the feature with tests. Write summary to output/response.md.\""
  ],
  "skipAIProcessing": true
}
```

**Configuration (NEW - cliPrompt field):**
```json
{
  "cliPrompt": "Read requirements from input/request.md. Implement the feature with tests. Write summary to output/response.md.",
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh"
  ],
  "skipAIProcessing": true
}
```

**Configuration (NEW - with file path):**
```json
{
  "cliPrompt": "./agents/prompts/cursor_implementation_prompt.md",
  "cliCommands": [
    "./cicd/scripts/run-cursor-agent.sh"
  ],
  "skipAIProcessing": true
}
```

### Claude CLI

**Configuration (old pattern):**
```json
{
  "cliCommands": [
    "claude --input input/request.md --output output/response.md --prompt 'Implement the ticket requirements'"
  ],
  "skipAIProcessing": true
}
```

**Configuration (NEW - cliPrompt field):**
```json
{
  "cliPrompt": "Implement the ticket requirements",
  "cliCommands": [
    "claude --input input/request.md --output output/response.md --prompt"
  ],
  "skipAIProcessing": true
}
```

### GitHub Copilot CLI

**Configuration (old pattern):**
```json
{
  "cliCommands": [
    "gh copilot suggest 'Implement the ticket from input/ folder' > output/response.md"
  ],
  "skipAIProcessing": true
}
```

**Configuration (NEW - cliPrompt field):**
```json
{
  "cliPrompt": "Implement the ticket from input/ folder. Write results to output/response.md",
  "cliCommands": [
    "gh copilot suggest > output/response.md"
  ],
  "skipAIProcessing": true
}
```

### Gemini CLI (ai CLI)

**Configuration (old pattern):**
```json
{
  "cliCommands": [
    "ai 'Implement the ticket from input/ folder' --model gemini-2.0-flash > output/response.md"
  ],
  "skipAIProcessing": true
}
```

**Configuration (NEW - cliPrompt field):**
```json
{
  "cliPrompt": "./agents/prompts/gemini_implementation_prompt.md",
  "cliCommands": [
    "ai --model gemini-2.0-flash > output/response.md"
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

**NEW: Using `cliPrompt` field (Recommended)**
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
    "cliPrompt": "User request is in 'input' folder. Implement the ticket requirements including code, tests, and documentation. Compile and run tests. Write comprehensive summary to output/response.md. DO NOT create branches or push.",
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh"
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

**Or use file-based prompt for reusability:**
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
        "./agents/instructions/development/implementation_instructions.md"
      ],
      "knownInfo": "Project: Spring Boot 3.x, Java 23, PostgreSQL"
    },
    "cliPrompt": "./agents/prompts/story_development_prompt.md",
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh"
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

### 1. Story Implementation (NEW: with cliPrompt)
```json
{
  "cliPrompt": "Implement story from input/",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/developTicketAndCreatePR.js"
}
```

### 2. Bug Fixing (NEW: with file-based prompt)
```json
{
  "cliPrompt": "./agents/prompts/bug_fixing_prompt.md",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/fixBugAndCreatePR.js"
}
```

### 3. Test Generation (NEW: with Confluence URL)
```json
{
  "cliPrompt": "https://company.atlassian.net/wiki/spaces/DEV/pages/123/Test+Generation+Guidelines",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/addTestsAndCommit.js"
}
```

### 4. Prompt from Shared GitHub Playbook
```json
{
  "cliPrompt": "https://github.com/your-org/playbook/blob/main/prompts/implement.md",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/developTicketAndCreatePR.js"
}
```

### 5. Documentation Generation
```json
{
  "cliPrompt": "Generate documentation",
  "cliCommands": ["claude --input input/ --output output/ --prompt"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/updateDocumentation.js"
}
```

### 5. Story Description (Without Code Access)
```json
{
  "cliPrompt": "Write story description based on input/",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
  "skipAIProcessing": true,
  "postJSAction": "agents/js/assignForReview.js"
}
```

**Note**: All examples above use the new `cliPrompt` field. Old inline pattern still works for backwards compatibility.

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

### Output Not Generated (NEW: v1.7.133+ Safety)

**Problem**: No `output/response.md` file created, field not updated

**Cause**: `requireCliOutputFile: true` (strict mode - default) prevents field updates without output file

**Solution 1** - Fix CLI command to create output file:
```bash
# Ensure CLI creates output/response.md
"Write results to output/response.md"  # Add to CLI prompt
```

**Solution 2** - Debug with input folder inspection:
```json
{
  "cleanupInputFolder": false  // Keep input for debugging
}
```

Then check:
```bash
cat input/PROJ-123/request.md
ls -la input/PROJ-123/
```

**Solution 3** - Use permissive mode (not recommended):
```json
{
  "requireCliOutputFile": false  // Use command output as fallback
}
```

### Field Updated with Error Message (Pre-v1.7.133)

**Problem**: Field contains error message like "CLI Execution Error: ..."

**Cause**: Old behavior (pre-v1.7.133) allowed error messages in field updates

**Solution**: Update to v1.7.133+ with strict mode (default):
```json
{
  "requireCliOutputFile": true  // Default - prevents this issue
}
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
- ✅ **NEW**: Clean configuration with `cliPrompt` field supporting plain text, files, Confluence URLs, and GitHub URLs

**Key Pattern**: Input folder → CLI agent → Output folder → Post-action processing

## NEW Feature: `cliPrompt` Field

### Overview

The `cliPrompt` field (introduced in v1.7.130+) separates CLI prompts from commands for cleaner configurations.

### Features

- **Multiple Input Types**: Plain text, local file paths, Confluence URLs, GitHub URLs
- **Automatic Processing**: Uses InstructionProcessor for content fetching
- **Shell Escaping**: Prevents injection by escaping `\`, `"`, `$`, `` ` ``
- **Backwards Compatible**: Existing inline prompts still work

### Quick Reference

| Input Type | Example |
|------------|---------|
| **Plain text** | `"cliPrompt": "Implement from input/"` |
| **File path** | `"cliPrompt": "./agents/prompts/dev.md"` |
| **Confluence URL** | `"cliPrompt": "https://company.atlassian.net/wiki/..."` |
| **GitHub URL** | `"cliPrompt": "https://github.com/org/repo/blob/main/prompts/dev.md"` |

### Runtime Behavior

1. Teammate processes `cliPrompt` (fetches Confluence/file content if needed)
2. Escapes shell special characters
3. Appends as quoted parameter to each CLI command

**Example**: `./script.sh` becomes `./script.sh "/tmp/dmtools_cli_prompt_12345.txt"`
