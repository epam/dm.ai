# AI Teammate Configuration Guide

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
| `aiRole` | String | System prompt/role | `"You are a senior developer"` |
| `instructions` | String | Task instructions | `"Analyze and optimize this code"` |
| `formattingRules` | String | Output format rules | `"Return as JSON with keys: title, description"` |
| `fewShots` | Array | Example input/outputs | See examples below |
| `temperature` | Float | Creativity (0-1) | `0.7` |
| `maxTokens` | Integer | Max response tokens | `2000` |

### JavaScript Actions

| Parameter | Type | Description | When Executed |
|-----------|------|-------------|---------------|
| `preprocessJSAction` | String | JS file path | Before AI processing |
| `postprocessJSAction` | String | JS file path | After AI processing |
| `skipJSAction` | String | JS file path | To determine skip logic |
| `validateJSAction` | String | JS file path | To validate AI output |

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