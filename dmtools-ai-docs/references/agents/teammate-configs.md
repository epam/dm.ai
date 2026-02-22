# AI Teammate Configuration Guide

**→ See also: [Agent Best Practices](best-practices.md) for critical patterns, common helpers, and lessons learned from real-world development**

## ⚠️ CRITICAL: The "name" Field is Java Class Name

**READ THIS FIRST - The `"name"` field is NOT customizable:**

```json
{
  "name": "TestCasesGenerator"  // ← Exact Java class name (immutable)
}
```

This is a **technical identifier** for Java class instantiation:
- `"TestCasesGenerator"` → `new TestCasesGenerator()`
- `"Teammate"` → `new Teammate()`
- `"Expert"` → `new Expert()`

**Rules:**
- ✅ Use exact class names from [Jobs Reference](../jobs/README.md)
- ❌ Do NOT customize: "My Generator" will fail
- ❌ Do NOT change case: "testcasesgenerator" will fail

See [JSON Configuration Rules](../configuration/json-config-rules.md) for complete explanation.

---

## 🎯 Overview

AI Teammates are JSON-configured workflows that combine AI analysis with pre/post-processing JavaScript agents. They enable complex automation scenarios like test generation, code analysis, and documentation creation.

## 📋 Configuration Structure

### Complete Configuration Schema

```json
{
  "name": "TestCasesGenerator",
  "params": {
    // Input Configuration
    "inputJql": "project = PROJ AND type = Story",
    "inputTicketId": "PROJ-123",
    "inputFile": "/path/to/input.json",

    // AI Configuration
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "aiRole": "You are an expert QA engineer specializing in test case design",
    "instructions": "Generate comprehensive test cases with positive, negative, and edge cases",
    "formattingRules": "Return results as JSON array with specific structure",
    "fewShots": [
      {
        "input": "Login feature",
        "output": "Test cases for login including valid/invalid credentials"
      }
    ],

    // JavaScript Agents
    "preprocessJSAction": "agents/js/validateInput.js",
    "postprocessJSAction": "agents/js/createTickets.js",
    "skipJSAction": "agents/js/checkWipLabel.js",

    // Output Configuration
    "outputType": "field",
    "outputFieldName": "Test Cases",
    "outputFile": "/path/to/output.json",
    "outputJiraProject": "PROJ",
    "outputJiraIssueType": "Test",

    // Processing Options
    "batchSize": 10,
    "maxConcurrent": 5,
    "retryAttempts": 3,
    "skipExisting": true,
    "dryRun": false,

    // Custom Parameters
    "customField1": "value1",
    "customField2": "value2"
  }
}
```

## 🔧 Parameter Reference

### Input Parameters

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `inputJql` | String | JQL query to fetch tickets | `"sprint in openSprints()"` |
| `inputTicketId` | String | Single ticket ID | `"PROJ-123"` |
| `inputFile` | String | Path to input file | `"/data/stories.json"` |
| `inputTickets` | Array | Direct ticket list | `["PROJ-1", "PROJ-2"]` |

### AI Configuration

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `aiProvider` | String | AI provider to use | `"gemini"`, `"openai"`, `"bedrock"` |
| `aiModel` | String | Specific model | `"gemini-2.0-flash-exp"`, `"gpt-4o"` |
| `aiRole` | String | System prompt/role — also supports local file paths and GitHub/Confluence URLs (same as `instructions`) | `"You are a senior developer"`, `"./agents/roles/dev.md"` |
| `instructions` | String/Array | Task instructions — supports plain text, local file paths (`./`, `../`, `/`), Confluence URLs (`https://...atlassian.net/...`), and GitHub URLs (`https://github.com/...`) | `"./agents/prompts/dev.md"`, `"https://github.com/org/repo/blob/main/PROMPT.md"` |
| `formattingRules` | String | Output format rules — same source types as `instructions` | `"Return as JSON with keys: title, description"`, `"./prompts/format.md"` |
| `fewShots` | String/Array | Example input/outputs — same source types as `instructions` | `"./prompts/few_shots.md"` |
| `temperature` | Float | Creativity (0-1) | `0.7` |
| `maxTokens` | Integer | Max response tokens | `2000` |

### JavaScript Actions

| Parameter | Type | Description | When Executed |
|-----------|------|-------------|---------------|
| `preprocessJSAction` | String | JS file path | Before AI processing |
| `postprocessJSAction` | String | JS file path | After AI processing |
| `skipJSAction` | String | JS file path | To determine skip logic |
| `validateJSAction` | String | JS file path | To validate AI output |

### Instruction Sources

The following fields all go through **InstructionProcessor**, which resolves content from four source types:
`instructions`, `formattingRules`, `fewShots`, `cliPrompt`, `aiRole`, `questions`, `tasks`

| Source | Detection | How content is fetched |
|--------|-----------|------------------------|
| **Plain text** | Anything else | Used as-is |
| **Local file** | Starts with `./`, `../`, or `/` | Read from disk relative to working directory |
| **Confluence URL** | `https://...atlassian.net/...` | Fetched via Confluence API (requires `CONFLUENCE_*` env vars) |
| **GitHub URL** | `https://github.com/...` or `https://raw.githubusercontent.com/...` | Fetched via GitHub API (`SOURCE_GITHUB_TOKEN` is optional for public repos, required for private) |

If fetching fails for any reason, the original string is used as fallback — no error is thrown.

**Examples:**

```json
{
  "name": "Teammate",
  "params": {
    "agentParams": {
      "instructions": [
        "https://github.com/your-org/playbook/blob/main/instructions/enhance-sd-api.md",
        "./instructions/common/jira_context.md",
        "https://company.atlassian.net/wiki/spaces/DEV/pages/123/Template",
        "**IMPORTANT** Return JSON format: {\"description\": \"...\"}"
      ],
      "formattingRules": "https://github.com/your-org/playbook/blob/main/formatting/api-ticket.md",
      "fewShots": "./agents/prompts/few_shots.md"
    }
  }
}
```

**Private GitHub repos** require `SOURCE_GITHUB_TOKEN` environment variable:
```bash
SOURCE_GITHUB_TOKEN=ghp_your_token_here
```

### CLI Integration (NEW in v1.7.130+)

| Parameter | Type | Default | Description | Example |
|-----------|------|---------|-------------|---------|
| `cliPrompt` | String | - | Prompt for CLI agent (supports plain text, local file paths, Confluence URLs, GitHub URLs) | `"Implement from input/"`, `"./prompts/dev.md"`, `"https://github.com/org/repo/blob/main/PROMPT.md"` |
| `cliCommands` | Array | - | CLI commands to execute | `["./cicd/scripts/run-cursor-agent.sh"]` |
| `preCliJSAction` | String | - | JS script path executed **after** input folder is created but **before** CLI commands run. Receives `params.inputFolderPath` (absolute path). Use to write extra files into the input folder. Errors in this script are logged but do NOT stop CLI execution. | `"agents/js/extendInputFolder.js"` |
| `skipAIProcessing` | Boolean | `false` | Skip AI processing when using CLI agents | `true` |
| `requireCliOutputFile` | Boolean | `true` | **NEW v1.7.133**: Require `output/response.md` before updating fields (strict mode prevents data loss) | `true` (recommended) |
| `cleanupInputFolder` | Boolean | `true` | **NEW v1.7.133**: Cleanup `input/[TICKET-KEY]/` folder after execution | `false` (for debugging) |

**Example with `cliPrompt` field:**
```json
{
  "name": "Teammate",
  "params": {
    "cliPrompt": "Implement the ticket from input/ folder. Write results to output/.",
    "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
    "skipAIProcessing": true,
    "postJSAction": "agents/js/developTicketAndCreatePR.js"
  }
}
```

**Or use file-based prompt for reusability:**
```json
{
  "cliPrompt": "./agents/prompts/implementation_prompt.md",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"]
}
```

**Or Confluence URL for centralized management:**
```json
{
  "cliPrompt": "https://company.atlassian.net/wiki/spaces/DEV/pages/123/Prompt",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"]
}
```

**Or GitHub URL (public or private with `SOURCE_GITHUB_TOKEN`):**
```json
{
  "cliPrompt": "https://github.com/your-org/your-repo/blob/main/agents/prompts/implement.md",
  "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"]
}
```

**How it works:**
1. Teammate processes `cliPrompt` (fetches GitHub/Confluence/file content if needed)
2. Escapes shell special characters (`\`, `"`, `$`, `` ` ``)
3. Appends as quoted parameter to each CLI command
4. Example: `./script.sh` becomes `./script.sh "Your prompt content"`

See [CLI Integration Guide](cli-integration.md) for complete documentation.

#### CLI Output Safety (v1.7.133+)

**Problem**: When CLI commands fail or don't create `output/response.md`, the system could overwrite critical fields with error messages.

**Solution**: Two new safety parameters control CLI output handling:

##### `requireCliOutputFile` (default: `true`)

**Strict Mode** (recommended for production):
```json
{
  "requireCliOutputFile": true,  // Default - safe mode
  "outputType": "field",
  "fieldName": "Description"
}
```

**Behavior**:
- ✅ If `output/response.md` exists → Process normally (update field/post comment/create ticket)
- ❌ If `output/response.md` missing → **Skip field update**, post error comment instead
- **Protects against data loss** - won't overwrite fields with error messages

**Permissive Mode** (use with caution):
```json
{
  "requireCliOutputFile": false,  // Permissive mode
  "outputType": "field",
  "fieldName": "Notes"
}
```

**Behavior**:
- Uses command stdout/stderr as fallback if `response.md` missing
- Less safe - may update fields with error messages
- Backwards compatible with old behavior

##### `cleanupInputFolder` (default: `true`)

**Cleanup Enabled** (recommended for production):
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
  "cleanupInputFolder": false  // Keep input for inspection
}
```

**Behavior**:
- Keeps `input/[TICKET-KEY]/` folder for manual inspection
- Allows debugging CLI issues by checking `request.md` and attachments
- Requires manual cleanup: `rm -rf input/[TICKET-KEY]`

##### `preCliJSAction`

Runs after the input folder is created (containing `request.md` and ticket attachments) but before CLI commands execute. The script receives `params.inputFolderPath` — the absolute path to the folder.

**Typical use cases**: inject extra reference files (architecture docs, style guides, API specs) that the CLI agent should read as context.

```javascript
// agents/js/extendInputFolder.js
function action(params) {
    const folder = params.inputFolderPath;

    // Fetch architecture guide from Confluence and add to input folder
    const architectureGuide = confluence_content_by_title("Architecture Guide");
    file_write(folder + "/architecture.md", architectureGuide);

    // Add a coding standards file
    const codingStandards = file_read("./docs/coding-standards.md");
    file_write(folder + "/coding-standards.md", codingStandards);
}
```

```json
{
  "name": "Teammate",
  "params": {
    "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
    "preCliJSAction": "agents/js/extendInputFolder.js",
    "skipAIProcessing": true
  }
}
```

**Error handling**: if `preCliJSAction` throws an exception, it is logged as a warning and CLI execution continues normally — the script failure never blocks the main workflow.

**Example: Production-safe CLI configuration:**
```json
{
  "name": "Teammate",
  "params": {
    "cliPrompt": "Generate comprehensive story description",
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

**Example: Debug mode (CLI not working):**
```json
{
  "name": "Teammate",
  "params": {
    "cliCommands": ["./cicd/scripts/run-cursor-agent.sh"],
    "skipAIProcessing": true,
    "cleanupInputFolder": false,    // Keep for debugging
    "outputType": "comment"
  }
}
```

After debugging: `cat input/PROJ-123/request.md` and `ls -la input/PROJ-123/`

### Output Configuration

| Parameter | Type | Description | Options |
|-----------|------|-------------|---------|
| `outputType` | String | Output destination | `"field"`, `"creation"`, `"file"`, `"none"` |
| `outputFieldName` | String | Jira field to update | `"Test Cases"`, `"customfield_10001"` |
| `outputFile` | String | Output file path | `"/results/tests.json"` |
| `outputJiraProject` | String | Project for new tickets | `"PROJ"` |
| `outputJiraIssueType` | String | Type for new tickets | `"Test"`, `"Task"`, `"Bug"` |

## 📚 Real-World Examples

### Example 1: Xray Test Case Generator

From `agents/xray_test_cases_generator.json`:

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "project = TP AND type = Story AND sprint in openSprints()",
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "aiRole": "You are a highly skilled QA engineer specializing in test case design",
    "instructions": "Generate comprehensive manual test cases for the given user story. Include positive, negative, and edge cases.",
    "formattingRules": "Return as JSON array. Each test case must have: priority (Critical/High/Medium/Low), summary (brief title), description (detailed steps), testType (Manual)",
    "outputType": "creation",
    "outputJiraProject": "TP",
    "outputJiraIssueType": "Test",
    "preprocessJSAction": "agents/js/checkWipLabel.js",
    "postprocessJSAction": "agents/js/preprocessXrayTestCases.js",
    "customFields": {
      "testType": "Manual",
      "labels": ["auto-generated", "xray"]
    }
  }
}
```

### Example 2: Cucumber Test Generator

From `agents/cucumber_tests_generator.json`:

```json
{
  "name": "CucumberTestsGenerator",
  "params": {
    "inputJql": "project = PROJ AND type = Story AND 'Cucumber Tests' is EMPTY",
    "aiProvider": "openai",
    "aiModel": "gpt-4o",
    "aiRole": "You are a BDD expert who writes Cucumber scenarios",
    "instructions": "Create Cucumber scenarios using Gherkin syntax. Focus on business behavior, not implementation.",
    "formattingRules": "Use Scenario Outline for data-driven tests. Include Background when there are common preconditions.",
    "fewShots": [
      {
        "input": "User login feature",
        "output": "Feature: User Login\n  Scenario: Successful login\n    Given user is on login page\n    When user enters valid credentials\n    Then user is redirected to dashboard"
      }
    ],
    "outputType": "field",
    "outputFieldName": "Cucumber Tests",
    "validateJSAction": "agents/js/validateGherkin.js"
  }
}
```

### Example 3: Code Review Assistant

```json
{
  "name": "CodeReviewAssistant",
  "params": {
    "inputFile": "/src/main/java/",
    "aiProvider": "gemini",
    "aiModel": "gemini-1.5-pro-002",
    "aiRole": "You are a senior software engineer conducting code reviews",
    "instructions": "Review the code for: security issues, performance problems, code smells, SOLID violations, and suggest improvements",
    "formattingRules": "Structure feedback as: Issues (critical/major/minor), Suggestions, Good Practices observed",
    "outputType": "file",
    "outputFile": "/reports/code-review.md",
    "temperature": 0.3,
    "customRules": {
      "checkSecurity": true,
      "checkPerformance": true,
      "checkStyle": false
    }
  }
}
```

### Example 4: Story Decomposition

From `agents/story_decomposition.json`:

```json
{
  "name": "StoryDecomposer",
  "params": {
    "inputJql": "type = Epic AND status = 'To Do'",
    "aiProvider": "openai",
    "aiModel": "gpt-4o",
    "aiRole": "You are a product owner breaking down epics into user stories",
    "instructions": "Decompose the epic into 3-7 user stories. Each story should be independently deliverable and follow INVEST principles",
    "formattingRules": "Each story needs: title, description (As a... I want... So that...), acceptance criteria, story points (1,2,3,5,8)",
    "outputType": "creation",
    "outputJiraProject": "PROJ",
    "outputJiraIssueType": "Story",
    "postprocessJSAction": "agents/js/linkToEpic.js"
  }
}
```

### Example 5: Documentation Generator

```json
{
  "name": "DocumentationGenerator",
  "params": {
    "inputJql": "type = Story AND fixVersion = '2.0' AND 'Documentation' is EMPTY",
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "aiRole": "You are a technical writer creating user documentation",
    "instructions": "Create user-facing documentation for the implemented feature",
    "formattingRules": "Include: Overview, Prerequisites, Step-by-step Usage, Examples, Troubleshooting, FAQ",
    "outputType": "confluence",
    "confluenceSpace": "DOCS",
    "confluenceParentPage": "User Guide",
    "postprocessJSAction": "agents/js/addScreenshots.js"
  }
}
```

## 🎨 Output Types Explained

### 1. Field Output (`"outputType": "field"`)

Updates a field in the source ticket:

```json
{
  "outputType": "field",
  "outputFieldName": "Test Cases",
  "appendToField": true  // Optional: append instead of replace
}
```

### 2. Creation Output (`"outputType": "creation"`)

Creates new tickets:

```json
{
  "outputType": "creation",
  "outputJiraProject": "PROJ",
  "outputJiraIssueType": "Test",
  "linkToSource": true,  // Link new tickets to source
  "linkType": "Tests"    // Relationship type
}
```

### 3. File Output (`"outputType": "file"`)

Saves to file:

```json
{
  "outputType": "file",
  "outputFile": "/results/output.json",
  "outputFormat": "json"  // json, csv, markdown
}
```

### 4. No Output (`"outputType": "none"`)

For processing without output:

```json
{
  "outputType": "none"  // Just process, don't save
}
```

## 🧩 Few-Shot Learning

Provide examples to improve AI accuracy:

```json
{
  "fewShots": [
    {
      "input": "Login with email and password",
      "output": {
        "title": "Valid login test",
        "steps": [
          "Navigate to login page",
          "Enter valid email",
          "Enter valid password",
          "Click login button"
        ],
        "expected": "User successfully logged in and redirected to dashboard"
      }
    },
    {
      "input": "Password reset functionality",
      "output": {
        "title": "Password reset via email",
        "steps": [
          "Click 'Forgot Password'",
          "Enter registered email",
          "Check email for reset link",
          "Click reset link",
          "Enter new password",
          "Confirm password change"
        ],
        "expected": "Password successfully changed, user can login with new password"
      }
    }
  ]
}
```

## 🔄 Processing Options

### Batch Processing

```json
{
  "batchSize": 10,        // Process 10 tickets at a time
  "maxConcurrent": 5,     // Max 5 parallel AI calls
  "delayBetweenBatches": 2000  // 2 second delay
}
```

### Error Handling

```json
{
  "retryAttempts": 3,     // Retry failed tickets 3 times
  "continueOnError": true,  // Don't stop on single failure
  "errorFile": "/logs/errors.json"  // Log errors
}
```

### Skip Logic

```json
{
  "skipExisting": true,   // Skip if output already exists
  "skipJSAction": "agents/js/shouldSkip.js",  // Custom skip logic
  "skipLabels": ["skip", "manual"]  // Skip tickets with these labels
}
```

## 🚀 Running Configurations

### Execute Configuration

```bash
# Run configuration file
dmtools run agents/xray_test_cases_generator.json

# With override parameters
dmtools run agents/config.json --param inputJql="project = PROJ"

# Dry run (preview without execution)
dmtools run agents/config.json --dry-run

# With debug output
dmtools run agents/config.json --debug
```

### Test Configuration

```bash
# Validate configuration
dmtools validate agents/config.json

# Test with single ticket
dmtools run agents/config.json --param inputTicketId="PROJ-123"

# Test with limited batch
dmtools run agents/config.json --param batchSize=1
```

## 🔧 Advanced Configurations

### Multi-Provider Fallback

```json
{
  "aiProviders": [
    {
      "provider": "gemini",
      "model": "gemini-2.0-flash-exp",
      "maxRetries": 2
    },
    {
      "provider": "openai",
      "model": "gpt-4o",
      "fallback": true
    }
  ]
}
```

### Dynamic Parameters

```json
{
  "params": {
    "inputJql": "${env.SPRINT_JQL}",  // From environment
    "aiModel": "${config.ai.model}",  // From config file
    "outputFile": "/results/${date:yyyy-MM-dd}/tests.json"  // Date template
  }
}
```

### Conditional Processing

```json
{
  "conditions": {
    "processIf": {
      "field": "priority",
      "operator": "in",
      "value": ["High", "Critical"]
    },
    "skipIf": {
      "field": "labels",
      "operator": "contains",
      "value": "no-automation"
    }
  }
}
```

## 📊 Monitoring & Logging

### Progress Tracking

```json
{
  "monitoring": {
    "showProgress": true,
    "progressInterval": 10,  // Log every 10 items
    "summaryReport": true,
    "reportFile": "/reports/execution-summary.json"
  }
}
```

### Detailed Logging

```json
{
  "logging": {
    "level": "DEBUG",  // ERROR, WARN, INFO, DEBUG
    "logFile": "/logs/teammate.log",
    "logAIPrompts": true,
    "logAIResponses": true,
    "logAPIcalls": false
  }
}
```

## 🆘 Common Issues

### Issue: "AI provider not configured"

```json
// Ensure AI provider credentials are set
{
  "aiProvider": "gemini",  // Requires GEMINI_API_KEY env var
  "aiProviderKey": "${env.GEMINI_API_KEY}"  // Or explicit
}
```

### Issue: "Custom field not found"

```json
// Map custom fields correctly
{
  "fieldMapping": {
    "customfield_10001": "StoryPoints",
    "customfield_10002": "TestCases"
  }
}
```

### Issue: "Rate limit exceeded"

```json
// Add rate limiting
{
  "rateLimiting": {
    "maxRequestsPerMinute": 10,
    "delayBetweenRequests": 6000  // 6 seconds
  }
}
```

## 🎯 Best Practices

### 1. Start Simple

Begin with minimal configuration and add complexity:

```json
{
  "name": "SimpleTestGenerator",
  "params": {
    "inputTicketId": "PROJ-123",
    "aiProvider": "gemini",
    "instructions": "Generate 3 test cases",
    "outputType": "none"  // Just test AI response
  }
}
```

### 2. Use Preprocessing

Validate and enrich data before AI:

```json
{
  "preprocessJSAction": "agents/js/enrichTicketData.js"
}
```

### 3. Validate Output

Always validate AI responses:

```json
{
  "validateJSAction": "agents/js/validateOutput.js",
  "validationRules": {
    "required": ["title", "description"],
    "maxLength": { "title": 100 }
  }
}
```

### 4. Handle Errors Gracefully

```json
{
  "errorHandling": {
    "continueOnError": true,
    "logErrors": true,
    "fallbackValue": { "status": "manual-review-needed" }
  }
}
```

---

*Next: [JavaScript Agent Examples](examples/preprocessing.md) | [MCP Tools Usage](examples/mcp-tools-usage.md)*