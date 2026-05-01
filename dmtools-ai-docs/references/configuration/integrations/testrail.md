# TestRail Configuration Guide

## 🎯 Overview

DMtools provides 15 MCP tools for TestRail integration, enabling automated test case creation, requirement linking, and test management directly from Jira/ADO stories.

If you only need the TestRail-focused AI assistant package, install `/dmtools-testrail` with the instructions in [../../installation/README.md#install-only-the-skills-you-need](../../installation/README.md#install-only-the-skills-you-need).

## 🔑 API Key Generation

### Step 1: Create TestRail API Key

1. Log in to your TestRail instance
2. Go to **My Settings** (top-right user menu)
3. Open the **API Keys** tab
4. Click **"Add Key"**, give it a name (e.g. "DMtools Integration")
5. Copy the key immediately

### Step 2: Configure DMtools

Add to your `dmtools.env`:

```bash
# TestRail Configuration
TESTRAIL_BASE_PATH=https://yourcompany.testrail.io
TESTRAIL_USERNAME=your-email@company.com
TESTRAIL_API_KEY=your_api_key
```

## 🔧 Configuration Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `TESTRAIL_BASE_PATH` | Your TestRail instance URL | `https://yourcompany.testrail.io` |
| `TESTRAIL_USERNAME` | Your TestRail login email | `user@company.com` |
| `TESTRAIL_API_KEY` | API key from My Settings | `abc123...` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TESTRAIL_PROJECT` | Default project name for operations | None |
| `TESTRAIL_LOGGING_ENABLED` | Enable debug request logging | `false` |

## 🧪 Testing Your Configuration

```bash
# List all projects (basic connection test)
dmtools testrail_get_projects

# Get all cases in a project
dmtools testrail_get_all_cases "My Project"

# Find cases linked to a Jira story
dmtools testrail_get_cases_by_refs PROJ-123 "My Project"
```

## 🚀 Generating Test Cases from Jira Stories

The `TestCasesGenerator` job reads stories from Jira and creates test cases in TestRail.

### Steps Template (recommended)

```bash
# Create agents/testrail_test_cases_generator.json with your config
dmtools run agents/testrail_test_cases_generator.json
```

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
        "labelNames": ["ai_generated"]
      }
    },
    "testCasesPriorities": "Critical, High, Medium, Low",
    "testCasesCustomFields": ["custom_preconds", "custom_steps_json"],
    "customFieldsRules": "custom_preconds: Write preconditions as plain text. custom_steps_json: Write test steps as a JSON array where each object has 'content' (the action to perform) and 'expected' (the expected result for that step). Example: [{\"content\":\"Open the app\",\"expected\":\"App opens successfully\"}]",
    "isFindRelated": true,
    "isLinkRelated": true,
    "isGenerateNew": true,
    "isConvertToJiraMarkdown": false
  }
}
```

### Using Project IDs (large TestRail instances)

If your TestRail instance has more than 250 projects and the target project doesn't appear at the top of the list, use `projectIds` instead of (or alongside) `projectNames` to bypass name-based resolution entirely:

```json
"customTestCasesTracker": {
  "type": "testrail",
  "params": {
    "projectIds": [42],
    "targetProjectId": 42,
    "creationMode": "steps",
    "typeName": "Functional",
    "labelNames": ["ai_generated"]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `projectNames` | `string[]` | Project names to search when finding/linking cases. Optional if `projectIds` is set. |
| `projectIds` | `number[]` | Project IDs — bypasses name resolution. Optional if `projectNames` is set. |
| `targetProject` | `string` | Name of the project where new cases are created. Defaults to first `projectNames` entry. |
| `targetProjectId` | `number` | ID of the project where new cases are created. Takes precedence over `targetProject`. |

To find a project ID:
```bash
dmtools testrail_get_projects
```

**Full guide**: [../../test-generation/testrail-manual.md](../../test-generation/testrail-manual.md)

## 📋 Available TestRail MCP Tools

**Complete reference**: [../../mcp-tools/testrail-tools.md](../../mcp-tools/testrail-tools.md) — all 15 TestRail tools with parameters.

### Quick Examples

```bash
# Get all projects
dmtools testrail_get_projects

# Get test cases linked to a story
dmtools testrail_get_cases_by_refs PROJ-123 "My Project"

# Get available case types
dmtools testrail_get_case_types

# Get labels for a project
dmtools testrail_get_labels "My Project"

# Delete a test case (numeric ID without 'C' prefix)
dmtools testrail_delete_case 42
```

### JavaScript Agent Access

```javascript
// Direct MCP tool access in agents
const projects = testrail_get_projects();
const cases = testrail_get_cases_by_refs("PROJ-123", "My Project");
const caseTypes = testrail_get_case_types();
testrail_link_to_requirement("42", "PROJ-123");
```

## 🐛 Troubleshooting

### Authentication Failed (401)

```bash
# Verify credentials
# Check TESTRAIL_USERNAME is your login email (not display name)
# Regenerate API key in TestRail My Settings → API Keys
```

### Project Not Found

```bash
# List all projects to get exact names (case-sensitive)
dmtools testrail_get_projects
```

### Enable Debug Logging

```bash
# dmtools.env
TESTRAIL_LOGGING_ENABLED=true

# Re-run your command — all API requests/responses will be logged
```

## 🔒 Security Best Practices

```bash
# Never commit credentials
echo "dmtools.env" >> .gitignore

# Use environment variables in CI/CD
export TESTRAIL_API_KEY=${{ secrets.TESTRAIL_API_KEY }}
```

---

*Next: [TestRail Test Generation Guide](../../test-generation/testrail-manual.md) | [TestRail MCP Tools](../../mcp-tools/testrail-tools.md) | [Jira Setup](jira.md)*
