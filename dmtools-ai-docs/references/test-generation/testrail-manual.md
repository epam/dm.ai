# TestRail Manual Test Generation Guide

## 🎯 Overview

DMtools integrates with TestRail to automatically generate manual test cases from user stories (Jira/ADO). The `TestCasesGenerator` job creates test cases directly in TestRail, links them to source tickets via the `refs` field, and supports three creation modes: `simple`, `detailed`, and `steps`.

## 🔧 Configuration

### Prerequisites

1. **TestRail instance**: Accessible at `TESTRAIL_BASE_PATH`
2. **API Key**: Generated in TestRail → My Settings → API Keys
3. **Project**: At least one TestRail project to store test cases

### Environment Setup

```bash
# dmtools.env
TESTRAIL_BASE_PATH=https://your-company.testrail.io
TESTRAIL_USERNAME=your-email@company.com
TESTRAIL_API_KEY=your_api_key_from_my_settings
TESTRAIL_PROJECT=My Project        # Optional default project

# Source tracker (Jira)
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64_encoded_email:token
```

## 🚀 Generating Test Cases

### Run with Configuration File

```bash
# Steps template (recommended)
dmtools run agents/testrail_test_cases_generator.json

# Text template (detailed mode)
dmtools run agents/testrail_test_cases_generator_detailed.json
```

### Configuration File — Steps Mode (recommended)

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (PROJ-123)",
    "outputType": "creation",
    "customTestCasesTracker": {
      "type": "testrail",
      "params": {
        "projectNames": ["My Project"],
        "creationMode": "steps",
        "typeName": "Functional",
        "labelNames": ["ai_generated", "regression"]
      }
    },
    "testCasesPriorities": "Critical, High, Medium, Low",
    "testCasesCustomFields": ["custom_preconds", "custom_steps_json"],
    "customFieldsRules": "custom_preconds: Write preconditions as plain text (e.g. 'User has a valid account. App is installed.'). custom_steps_json: Write test steps as a JSON array where each object has 'content' (the action to perform) and 'expected' (the expected result for that step). Example: [{\"content\":\"Open the app and navigate to Sign-in screen\",\"expected\":\"Sign-in screen is displayed with header 'Sign in.'\"}]",
    "isFindRelated": true,
    "isLinkRelated": true,
    "isGenerateNew": true,
    "isConvertToJiraMarkdown": false
  }
}
```

### Configuration File — Steps Mode with Project ID (large TestRail instances)

Use `projectIds` when the project is beyond the first page of results (> 250 projects in the instance) or when you want to avoid name-resolution overhead. Both `projectNames` and `projectIds` are optional — use one or the other (or both for multi-project searches).

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (PROJ-123)",
    "outputType": "creation",
    "customTestCasesTracker": {
      "type": "testrail",
      "params": {
        "projectIds": [42],
        "creationMode": "steps",
        "typeName": "Functional",
        "labelNames": ["ai_generated"]
      }
    },
    "testCasesPriorities": "Critical, High, Medium, Low",
    "testCasesCustomFields": ["custom_preconds", "custom_steps_json"],
    "isFindRelated": true,
    "isLinkRelated": true,
    "isGenerateNew": true,
    "isConvertToJiraMarkdown": false
  }
}
```

To find the project ID:
```bash
dmtools testrail_get_projects
# Look for "id" in the output for your project
```

You can also mix both — `projectNames` and `projectIds` are merged when searching for existing/related cases:

```json
"params": {
  "projectNames": ["Legacy Project"],
  "projectIds": [42, 55]
}
```

For creation, `targetProjectId` takes precedence over `targetProject` (name):

```json
"params": {
  "targetProjectId": 42,
  "projectIds": [42, 55]
}
```

### Configuration File — Detailed Mode

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (PROJ-123)",
    "outputType": "creation",
    "customTestCasesTracker": {
      "type": "testrail",
      "params": {
        "projectNames": ["My Project"],
        "creationMode": "detailed",
        "typeName": "Functional",
        "labelNames": ["ai_generated"]
      }
    },
    "testCasesPriorities": "Critical, High, Medium, Low",
    "testCasesCustomFields": ["custom_preconds", "custom_steps", "custom_expected"],
    "customFieldsRules": "custom_preconds: Write preconditions as plain text. custom_steps: Write test steps as plain numbered text (e.g. '1. Open the app\n2. Navigate to Sign-in screen'). custom_expected: Write expected results as plain text corresponding to each step.",
    "isFindRelated": true,
    "isLinkRelated": true,
    "isGenerateNew": true,
    "isConvertToJiraMarkdown": false
  }
}
```

## 📋 Creation Modes

| Mode | Template | Fields | Best for |
|------|----------|--------|----------|
| `simple` | Text (id=1) | `preconditions` only | Quick basic tests |
| `detailed` | Text (id=1) | `preconditions` + `steps` (text) + `expected` (text) | Numbered text steps |
| `steps` | Steps (id=2) | `preconditions` + `steps_json` (JSON array) | Structured step-by-step tests |

### Steps Mode — JSON Format

```json
[
  {
    "content": "Open the app and navigate to the Sign-in screen",
    "expected": "Sign-in screen is displayed with header 'Sign in.'"
  },
  {
    "content": "Enter valid email address in the Email field",
    "expected": "Email is accepted without error"
  },
  {
    "content": "Enter valid password and tap Sign In",
    "expected": "User is authenticated and redirected to the home screen"
  }
]
```

### Detailed Mode — Text Format

```
custom_steps:
1. Open the app and navigate to the Sign-in screen
2. Enter valid email address
3. Enter valid password and tap Sign In

custom_expected:
1. Sign-in screen is displayed
2. Email field accepts input without error
3. User is authenticated and redirected to home screen
```

## 🏷️ Labels and Types

Use **human-readable names** — DMtools resolves them to IDs automatically:

```json
{
  "typeName": "Functional",
  "labelNames": ["ai_generated", "regression", "smoke"]
}
```

To discover available values:

```bash
# List all case types
dmtools testrail_get_case_types

# List all labels in a project
dmtools testrail_get_labels "My Project"
```

## 🔗 Linking to Source Stories

Test cases are linked to source Jira/ADO tickets via the TestRail `refs` field automatically. To find all test cases for a story:

```bash
dmtools testrail_get_cases_by_refs PROJ-123 "My Project"
```

```javascript
// JavaScript agent
const linkedCases = testrail_get_cases_by_refs("PROJ-123", "My Project");
for (const tc of linkedCases) {
    console.log(tc.key + ": " + tc.title);
}
```

## 📚 Using Existing TestRail Cases as AI Examples

The `examples` parameter uses the same `ql()` prefix as Jira — when a `customTestCasesTracker` is configured, the query is routed to the TestRail adapter instead of Jira. For TestRail, the value inside `ql(...)` is a **label name**.

### Syntax

```
"examples": "ql(ai_example)"   // all TestRail cases tagged with label 'ai_example'
```

### Example Config

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (PROJ-456)",
    "outputType": "creation",
    "customTestCasesTracker": {
      "type": "testrail",
      "params": {
        "projectNames": ["My Project"],
        "creationMode": "steps"
      }
    },
    "testCasesCustomFields": ["custom_preconds", "custom_steps_json"],
    "customFieldsRules": "custom_preconds: Write preconditions as plain text. custom_steps_json: Write test steps as a JSON array where each object has 'content' and 'expected'.",
    "examples": "ql(ai_example)",
    "isFindRelated": true,
    "isLinkRelated": true,
    "isGenerateNew": true,
    "isConvertToJiraMarkdown": false
  }
}
```

This fetches all TestRail cases in the configured projects that are tagged with the label `ai_example`, extracts their `custom_preconds` and `custom_steps_json` fields (automatically mapping from TestRail's internal `custom_steps_separated`), and provides them to the AI as formatting examples.

To tag cases as examples in TestRail, add an `ai_example` label to them:

```bash
# List available labels
dmtools testrail_get_labels "My Project"

# After creating the label in TestRail UI, update a case via MCP tool
dmtools testrail_update_case 42 '{"label_ids": [<ai_example_label_id>]}'
```

### Field Mapping

| Config key | TestRail internal field | Conversion |
|---|---|---|
| `custom_steps_json` | `custom_steps_separated` (JSONArray) | Each step's `content`/`expected` extracted into JSON string |
| Other fields | Same name | Copied directly |

## 🗑️ Cleanup

```bash
# Delete a single test case (numeric ID, no 'C' prefix)
dmtools testrail_delete_case 42

# Find and delete all AI-generated cases for a story
const cases = testrail_get_cases_by_refs("PROJ-123", "My Project");
for (const tc of cases) {
    const numericId = tc.key.replace("C", "");
    testrail_delete_case(numericId);
}
```

## 🔍 Configuration Parameters Reference

| Parameter | Description |
|-----------|-------------|
| `customTestCasesTracker.type` | Must be `"testrail"` |
| `customTestCasesTracker.params.projectNames` | Array of TestRail project names to read/search existing cases |
| `customTestCasesTracker.params.creationMode` | `"simple"` / `"detailed"` / `"steps"` |
| `customTestCasesTracker.params.typeName` | Case type name (e.g. `"Functional"`, `"Automated"`) |
| `customTestCasesTracker.params.labelNames` | Array of label names to apply (e.g. `["ai_generated", "smoke"]`) |
| `customTestCasesTracker.params.targetProject` | Override creation project (defaults to first in `projectNames`) |
| `testCasesCustomFields` | Fields AI should populate (e.g. `["custom_preconds", "custom_steps_json"]`) |
| `customFieldsRules` | Instructions for AI on how to fill each custom field |
| `isFindRelated` | Find and link existing related test cases |
| `isLinkRelated` | Link found related cases to the source story |
| `isGenerateNew` | Generate new test cases for uncovered acceptance criteria |
| `isConvertToJiraMarkdown` | Set to `false` for TestRail (handles its own formatting) |

## 🧜 Mermaid Index Snapshot Lookup

For repositories with large `existingTestCasesJql` result sets, you can pre-build compact Mermaid-diagram snapshots of the existing test cases and store them in a separate directory or repository. `TestCasesGenerator` will then use those snapshots instead of full ticket text during the relevant-test-case search, significantly reducing token usage.

### How it works

1. Run `mermaid_index_generate` (or the equivalent job) against the same JQL that produces your existing test cases.
2. Point `TestCasesGenerator` at the snapshot root with `mermaidIndexStoragePath`.
3. During the relevant-test search, each existing test case is represented by its Mermaid snapshot if one exists; otherwise the full ticket text is used.
4. Verification (`RelatedTestCaseAgent`) and generation (`TestCaseGeneratorAgent`) keep using the full ticket text by default. Set `useMermaidSnapshotForGeneration` to `true` to use snapshots for generation as well.

### Snapshot directory layout

The layout must match the output produced by `MermaidIndex` for Jira:

```
{mermaidIndexStoragePath}/
  jira/
    {projectKey}/
      {ticketKey}/
        {title}.mmd
```

Example: `snapshots/jira/PROJ/PROJ-123/Login test.mmd`.

### Configuration

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (PROJ-123)",
    "outputType": "creation",
    "existingTestCasesJql": "project = PROJ AND issuetype = 'Test Case'",
    "mermaidIndexStoragePath": "./mermaid-snapshots",
    "mermaidIndexIntegration": "jira",
    "useMermaidSnapshotForGeneration": false,
    "isFindRelated": true,
    "isGenerateNew": true
  }
}
```

### Generating snapshots

```bash
# Generate snapshots for the same JQL used by TestCasesGenerator
dmtools mermaid_index_generate \
  --integration jira \
  --storage_path ./mermaid-snapshots \
  --include_patterns "project = PROJ AND issuetype = 'Test Case'"
```

Or run it as a job and commit `./mermaid-snapshots` to a separate repository that your CI checks out next to the main workspace.

## 🐛 Troubleshooting

### "Project not found"

```bash
# Verify project name (exact, case-sensitive)
dmtools testrail_get_projects
```

### Steps not showing (only 1 step with description)

AI may not have populated `custom_steps_json`. Ensure `customFieldsRules` clearly describes the expected JSON format with an example. The adapter also checks `custom_steps_separated` as a fallback key name.

### Labels or type not applied

Verify names exist in your TestRail project:
```bash
dmtools testrail_get_case_types
dmtools testrail_get_labels "My Project"
```

### Test cases created in wrong project

Set `targetProject` explicitly in adapter params:
```json
{
  "projectNames": ["Source Project"],
  "targetProject": "Target Project",
  "creationMode": "steps"
}
```

---

*Next: [Xray Manual Tests](xray-manual.md) | [TestRail MCP Tools](../mcp-tools/testrail-tools.md)*
