# Jira Configuration Guide

## 🎯 Overview

DMtools provides 52 MCP tools for Jira integration, enabling automated ticket management, test case generation, and workflow automation.

## 🔑 API Token Generation

### Step 1: Create Atlassian API Token

1. Go to [Atlassian Account Settings](https://id.atlassian.com/manage-profile/security/api-tokens)
2. Click **"Create API token"**
3. Give it a descriptive name: "DMtools Integration"
4. Copy the token immediately (shown only once)

### Step 2: Encode Credentials

```bash
# Format: email:token
echo -n "your.email@company.com:ATATT3xFfGF0..." | base64

# Example output:
# eW91ci5lbWFpbEBjb21wYW55LmNvbTpBVEFUVDN4RmZHRjA...
```

### Step 3: Configure DMtools

Add to your `dmtools.env`:

```bash
# Jira Configuration
JIRA_BASE_PATH=https://yourcompany.atlassian.net
JIRA_LOGIN_PASS_TOKEN=eW91ci5lbWFpbEBjb21wYW55LmNvbTpBVEFUVDN4RmZHRjA...
JIRA_AUTH_TYPE=Basic
```

## 🔧 Configuration Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JIRA_BASE_PATH` | Your Jira instance URL | `https://company.atlassian.net` |
| `JIRA_LOGIN_PASS_TOKEN` | Base64 encoded email:token | `base64(email:token)` |

### Optional Variables

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `JIRA_AUTH_TYPE` | Authentication method | `Basic` or `Bearer` | `Basic` |
| `JIRA_CLOUD` | Set to any value to use cloud-specific APIs | `true` | Not set |
| `JIRA_FIELDS_MAPPING` | Custom field mappings | `customfield_10001:StoryPoints` | None |
| `JIRA_DEFAULT_PROJECT` | Default project key | `PROJ` | None |
| `JIRA_DEFAULT_ISSUE_TYPE` | Default issue type | `Story` | `Task` |
| `DEFAULT_TRACKER` | Default tracker implementation | `jira_xray` | Depends on setup |
| `XRAY_BASE_PATH` | Xray API base path | `https://xray.cloud.getxray.app/api/v2` | None |
| `XRAY_CLIENT_ID` | Xray client ID for test management | `E5E7...` | None |
| `XRAY_CLIENT_SECRET` | Xray client secret | `8a6b...` | None |
| `XRAY_ENRICHMENT_ENABLED_BY_DEFAULT` | Enable Xray search enrichment by default | `true` or `false` | `true` |

### Xray-Specific Notes

If you use Xray-backed test generation, set `DEFAULT_TRACKER=jira_xray`.

With `TestCasesGenerator` and `"outputType": "creation"`, DMtools creates Xray tests automatically through `XrayClient`.

Xray enrichment for search results is enabled by default, including searches used to load existing or related test cases. This preserves current client behavior, so existing configs do not need extra fields or logic changes.

Set `XRAY_ENRICHMENT_ENABLED_BY_DEFAULT=false` only if you explicitly want the faster non-enriched search path.

## 🎯 Custom Fields Configuration

### Identifying Custom Fields

```bash
# Get all fields for a ticket
dmtools jira_get_ticket PROJ-123

# Look for customfield_XXXXX in the response
# Common custom fields:
# - customfield_10001: Story Points
# - customfield_10002: Epic Link
# - customfield_10020: Sprint
```

### Mapping Custom Fields

```bash
# dmtools.env
JIRA_FIELDS_MAPPING=customfield_10001:StoryPoints,customfield_10002:EpicLink,customfield_10020:Sprint

# This allows you to use friendly names in configs:
# Instead of: "customfield_10001": 5
# You can use: "StoryPoints": 5
```

### Handling Duplicate Fields

Jira often has duplicate field definitions. DMtools handles this automatically:

```bash
# If you see warnings about duplicate fields:
# WARNING: Duplicate field 'Epic Link' found
# DMtools will use the first occurrence

# To explicitly choose a field:
JIRA_PREFERRED_FIELDS=customfield_10002:EpicLink
```

## 📋 Available Jira MCP Tools

**Complete Jira tools**: See [../../mcp-tools/jira-tools.md](../../mcp-tools/jira-tools.md) for all 52 Jira MCP tools with detailed parameters.

**All MCP tools**: See [../../mcp-tools/README.md](../../mcp-tools/README.md) for complete reference across all integrations.

### Quick Examples

```bash
# Get ticket details
dmtools jira_get_ticket PROJ-123

# Search with JQL
dmtools jira_search_by_jql "project = PROJ AND status = Open"

# Post comment
dmtools jira_post_comment PROJ-123 "Review completed"

# Assign ticket
dmtools jira_assign_ticket_to PROJ-123 <account-id>

# List all available Jira tools
dmtools list | grep jira
```

### JavaScript Agent Access

All Jira MCP tools are directly accessible as functions in JavaScript agents:

```javascript
// Direct MCP tool access in agents
const ticket = jira_get_ticket("PROJ-123");
const comments = jira_get_comments("PROJ-123");
jira_post_comment("PROJ-123", "Processed by agent");
```

## 🔍 Common JQL Queries

```bash
# Open tickets in current sprint
dmtools jira_search_by_jql "sprint in openSprints() AND status != Done"

# My assigned tickets
dmtools jira_search_by_jql "assignee = currentUser() AND status != Done"

# Recently updated
dmtools jira_search_by_jql "updated >= -7d ORDER BY updated DESC"

# Bugs in project
dmtools jira_search_by_jql "project = PROJ AND issuetype = Bug AND status = Open"

# Epic and its stories
dmtools jira_search_by_jql "'Epic Link' = PROJ-100"

# High priority items
dmtools jira_search_by_jql "priority in (Highest, High) AND status != Done"
```

## 🧪 Testing Your Configuration

### 1. Test Basic Connection

```bash
# Get server info
dmtools jira_get_ticket PROJ-1

# If successful, you'll see ticket details
# If failed, check error message for auth issues
```

### 2. Test Search

```bash
# Simple search
dmtools jira_search_by_jql "project = PROJ"

# Should return list of tickets
```

### 3. Test Write Operations

```bash
# Add a comment (safe test)
dmtools jira_post_comment PROJ-123 "Test comment from DMtools"

# Check it appeared in Jira UI
```

## 💡 Usage Examples

### Example 1: Generate Test Cases from Story

```bash
# Configure AI provider (if not already done)
export GEMINI_API_KEY=your-key

# Generate test cases
dmtools run agents/xray_test_cases_generator.json

# Or with specific ticket
dmtools TestCasesGenerator --inputJql "key = PROJ-123"
```

### Example 2: Bulk Create Stories

```json
// stories.json
{
  "stories": [
    {
      "summary": "Implement user login",
      "description": "As a user, I want to log in...",
      "storyPoints": 5,
      "priority": "High"
    },
    {
      "summary": "Add password reset",
      "description": "As a user, I want to reset...",
      "storyPoints": 3,
      "priority": "Medium"
    }
  ]
}
```

```bash
dmtools jira_bulk_create_stories PROJ --data @stories.json
```

### Example 3: Workflow Automation

```javascript
// agents/js/jiraWorkflow.js
function action(params) {
    // Get all open bugs
    const bugs = jira_search_by_jql("project = PROJ AND issuetype = Bug AND status = Open");

    for (const bug of bugs) {
        // Add label
        jira_update_labels(bug.key, "needs-review");

        // Assign to QA lead
        jira_assign_ticket(bug.key, "qa.lead@company.com");

        // Add comment
        jira_post_comment(bug.key, "Assigned for review in current sprint");
    }

    return { processed: bugs.length };
}
```

## 🔒 Security Best Practices

### 1. Token Security

```bash
# Never commit tokens
echo "dmtools.env" >> .gitignore

# Use environment variables in CI/CD
export JIRA_LOGIN_PASS_TOKEN=${{ secrets.JIRA_TOKEN }}
```

### 2. Restrict Token Permissions

In Atlassian admin:
1. Create service account: `dmtools@company.com`
2. Grant minimal permissions:
   - Browse projects
   - Create issues
   - Edit issues (if needed)
   - Add comments

### 3. Rotate Tokens Regularly

```bash
# Every 90 days:
# 1. Generate new token in Atlassian
# 2. Encode new credentials
echo -n "email:new_token" | base64
# 3. Update dmtools.env
# 4. Test connection
dmtools jira_get_ticket PROJ-1
# 5. Revoke old token in Atlassian
```

## 🐛 Troubleshooting

### Authentication Failed

```bash
# Error: 401 Unauthorized

# Check encoding:
echo -n "your.email@company.com:token" | base64
# Make sure no newline at the end

# Try Basic auth:
JIRA_AUTH_TYPE=Basic dmtools jira_get_ticket PROJ-1
```

### SSL Certificate Issues

```bash
# Error: PKIX path building failed

# For self-signed certificates:
export JAVA_OPTS="-Dcom.sun.net.ssl.checkRevocation=false"

# Or add certificate to Java truststore
```

### Rate Limiting

```bash
# Error: 429 Too Many Requests

# Add delays in scripts:
sleep 1  # Between API calls

# Or use different API tokens for parallel operations
```

### Field Not Found

```bash
# Error: Field 'customfield_10001' does not exist

# List all fields:
dmtools jira_get_fields

# Find correct field ID and update mapping
```

## 📚 Advanced Configuration

### Multiple Jira Instances

```bash
# Production
JIRA_PROD_BASE_PATH=https://prod.atlassian.net
JIRA_PROD_TOKEN=...

# Staging
JIRA_STAGE_BASE_PATH=https://stage.atlassian.net
JIRA_STAGE_TOKEN=...

# Switch via environment variable:
JIRA_ENV=PROD dmtools jira_get_ticket PROJ-123
```

### Proxy Configuration

```bash
# HTTP proxy
export HTTP_PROXY=http://proxy.company.com:8080
export HTTPS_PROXY=http://proxy.company.com:8080

# With authentication
export HTTP_PROXY=http://user:pass@proxy.company.com:8080
```

## 🔗 Useful Resources

- [Atlassian API Documentation](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)
- [JQL Reference](https://support.atlassian.com/jira-software-cloud/docs/advanced-search-reference-jql-fields/)
- [API Token Management](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/)
- [Xray Documentation](https://docs.getxray.app/display/XRAYDC/REST+API)

---

*Next: [Configure AI Provider](../ai-providers/gemini.md) | [Azure DevOps Setup](ado.md)*
