# GitHub Actions: AI Teammate Workflow

This workflow demonstrates how to run DMtools Teammate job in GitHub Actions for automated ticket processing.

---

## ⚠️ CRITICAL: Understanding the "name" Field

**Before configuring this workflow, you MUST understand:**

```json
{
  "name": "Teammate"  // ← This is the Java class name (NOT customizable)
}
```

The `"name"` field is a **technical identifier** that maps to Java code:

```
"name": "Teammate" → new Teammate() (Java instantiation)
```

**Rules:**
- ✅ **MUST** be exactly `"Teammate"` (case-sensitive)
- ❌ **CANNOT** be "AI Teammate" or "My Teammate" or "teammate"
- ❌ **CANNOT** be changed or customized

**Why?** This is how DMtools finds the correct Java class to run. Wrong name = `Error: Unknown job`

**See**: [JSON Configuration Rules](../configuration/json-config-rules.md) for complete explanation.

---

## Overview

The AI Teammate workflow allows you to:
- Trigger Teammate job on-demand via `workflow_dispatch`
- Process Jira tickets with AI assistance
- Use encoded configuration for dynamic parameters
- Cache DMtools installation for faster runs
- Integrate with multiple services (Jira, Confluence, Figma, AWS Bedrock)

## Workflow File

**Location**: `.github/workflows/ai-teammate.yml`

```yaml
name: AI Teammate

on:
  workflow_dispatch:
    inputs:
      config_file:
        description: 'Path to config'
        required: true
      encoded_config:
        description: 'Encoded or JSON Agent Config'
        required: false

permissions:
  contents: write
  pull-requests: write
  actions: read

jobs:
  ai-teammate-agent:
    runs-on: ubuntu-latest
    concurrency:
      group: ai-teammate-agent-${{ inputs.config_file }}
      cancel-in-progress: false

    steps:
    - name: Checkout repository
      uses: actions/checkout@v4

    - name: Setup Java Environment
      uses: ./.github/actions/setup-java-only
      with:
        cache-key-suffix: '-ai-teammate'

    - name: Cache DMTools
      id: cache-dmtools
      uses: actions/cache@v4
      with:
        path: ~/.dmtools
        key: dmtools-v1.7.127-${{ runner.os }}

    - name: Install DMTools CLI
      if: steps.cache-dmtools.outputs.cache-hit != 'true'
      run: |
        curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/v1.7.127/install.sh | DMTOOLS_VERSION=v1.7.127 bash

    - name: Add DMTools to PATH
      run: |
        echo "$HOME/.dmtools/bin" >> $GITHUB_PATH

    - name: Verify DMTools Installation
      run: |
        dmtools || echo "dmtools verification failed"

    - name: Run AI Teammate
      env:
        AGENT_DISABLE_WATCHDOG: "1"
        PATH: "/home/runner/.local/bin:/home/runner/.dmtools/bin:/bin:/usr/bin:$PATH"
        # Jira Configuration
        JIRA_EMAIL: ${{ secrets.JIRA_EMAIL }}
        JIRA_API_TOKEN: ${{ secrets.JIRA_API_TOKEN }}
        JIRA_BASE_PATH: ${{ vars.JIRA_BASE_PATH }}
        JIRA_AUTH_TYPE: ${{ vars.JIRA_AUTH_TYPE }}
        JIRA_CLEAR_CACHE: ${{ vars.JIRA_CLEAR_CACHE }}
        JIRA_EXTRA_FIELDS: ${{ vars.JIRA_EXTRA_FIELDS }}
        JIRA_EXTRA_FIELDS_PROJECT: ${{ vars.JIRA_EXTRA_FIELDS_PROJECT }}
        JIRA_LOGGING_ENABLED: ${{ vars.JIRA_LOGGING_ENABLED }}
        JIRA_WAIT_BEFORE_PERFORM: ${{ vars.JIRA_WAIT_BEFORE_PERFORM }}
        JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES: ${{ vars.JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES }}

        # Confluence Configuration
        CONFLUENCE_EMAIL: ${{ secrets.JIRA_EMAIL }}
        CONFLUENCE_API_TOKEN: ${{ secrets.JIRA_API_TOKEN }}
        CONFLUENCE_BASE_PATH: ${{ vars.CONFLUENCE_BASE_PATH }}
        CONFLUENCE_DEFAULT_SPACE: ${{ vars.CONFLUENCE_DEFAULT_SPACE }}
        CONFLUENCE_GRAPHQL_PATH: ${{ vars.CONFLUENCE_GRAPHQL_PATH }}
        CONFLUENCE_AUTH_TYPE: ${{ vars.CONFLUENCE_AUTH_TYPE }}

        # AWS Bedrock Configuration
        BEDROCK_REGION: ${{ vars.BEDROCK_REGION }}
        BEDROCK_MODEL_ID: ${{ vars.BEDROCK_MODEL_ID }}
        BEDROCK_ACCESS_KEY_ID: ${{ secrets.BEDROCK_ACCESS_KEY_ID }}
        BEDROCK_SECRET_ACCESS_KEY: ${{ secrets.BEDROCK_SECRET_ACCESS_KEY }}
        BEDROCK_BASE_PATH: ${{ vars.BEDROCK_BASE_PATH }}
        BEDROCK_MAX_TOKENS: ${{ vars.BEDROCK_MAX_TOKENS }}
        BEDROCK_TEMPERATURE: ${{ vars.BEDROCK_TEMPERATURE }}
        DEFAULT_LLM: ${{ vars.DEFAULT_LLM }}

        # DMTools Integration Settings
        DMTOOLS_INTEGRATIONS: "jira,confluence,figma,ai,cli,file"
        DEFAULT_TRACKER: ${{ vars.DEFAULT_TRACKER }}

        PROMPT_CHUNK_TOKEN_LIMIT: ${{ vars.PROMPT_CHUNK_TOKEN_LIMIT }}

        # Figma Configuration
        FIGMA_TOKEN: ${{ secrets.FIGMA_TOKEN }}
        FIGMA_BASE_PATH: ${{ vars.FIGMA_BASE_PATH }}
        ENCODED_CONFIG: ${{ inputs.encoded_config }}

        # GitHub Authentication for PR creation
        PAT_TOKEN: ${{ secrets.PAT_GITHUB_TOKEN }}
        GH_TOKEN: ${{ secrets.PAT_GITHUB_TOKEN }}

      run: |
        echo "Using configuration: ${{ inputs.config_file }}"
        if [ -n "${ENCODED_CONFIG}" ]; then
          echo "Encoded config received (raw):"
          printf '%s\n' "${ENCODED_CONFIG}"
          dmtools run "${{ inputs.config_file }}" "${ENCODED_CONFIG}"
        else
          echo "No encoded config provided."
          dmtools run "${{ inputs.config_file }}"
        fi
```

## Configuration

### 1. Required Secrets

Configure these in **Settings → Secrets and variables → Actions → Repository secrets**:

| Secret | Description | Example |
|--------|-------------|---------|
| `JIRA_EMAIL` | Jira account email | `user@company.com` |
| `JIRA_API_TOKEN` | Jira API token | Generate at https://id.atlassian.com/manage-profile/security/api-tokens |
| `BEDROCK_ACCESS_KEY_ID` | AWS access key for Bedrock | From AWS IAM |
| `BEDROCK_SECRET_ACCESS_KEY` | AWS secret key for Bedrock | From AWS IAM |
| `FIGMA_TOKEN` | Figma personal access token | Generate in Figma settings |
| `PAT_GITHUB_TOKEN` | GitHub PAT with repo access | For PR creation |

### 2. Required Variables

Configure these in **Settings → Secrets and variables → Actions → Variables**:

| Variable | Description | Example |
|----------|-------------|---------|
| `JIRA_BASE_PATH` | Jira instance URL | `https://company.atlassian.net` |
| `JIRA_AUTH_TYPE` | Authentication type | `Basic` |
| `CONFLUENCE_BASE_PATH` | Confluence URL | `https://company.atlassian.net/wiki` |
| `CONFLUENCE_DEFAULT_SPACE` | Default Confluence space | `TEAM` |
| `BEDROCK_REGION` | AWS region | `us-east-1` |
| `BEDROCK_MODEL_ID` | Bedrock model | `anthropic.claude-3-5-sonnet-20241022-v2:0` |
| `DEFAULT_LLM` | Default AI provider | `bedrock` |
| `DEFAULT_TRACKER` | Default tracker | `jira` |
| `PROMPT_CHUNK_TOKEN_LIMIT` | Token limit per chunk | `4000` |

### 3. Teammate Configuration File

Create configuration in your repository (e.g., `agents/teammate_config.json`):

**CRITICAL**: The `"name"` field must be exactly `"Teammate"` - this matches the Java Job class name and cannot be changed. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "Teammate",
  "params": {
    "metadata": {
      "contextId": "story_analysis"
    },
    "agentParams": {
      "aiRole": "Senior Software Architect",
      "instructions": [
        "./agents/instructions/analysis/requirements_analysis.md",
        "./agents/instructions/common/error_handling.md",
        "Analyze the ticket requirements",
        "Identify technical risks",
        "Suggest implementation approach"
      ],
      "formattingRules": "Return structured analysis with sections: Requirements, Risks, Approach",
      "knownInfo": "Project uses microservices architecture with Spring Boot and React"
    },
    "inputJql": "project = PROJ AND status = 'To Do' AND labels = 'needs-analysis'",
    "outputType": "comment",
    "initiator": "github-actions-bot",
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/notifyAnalysisComplete.js"
  }
}
```

## Usage

### Trigger Workflow Manually

1. Go to **Actions** tab in GitHub
2. Select **AI Teammate** workflow
3. Click **Run workflow**
4. Fill in parameters:
   - **config_file**: `agents/teammate_config.json`
   - **encoded_config**: (optional) JSON string to override config

### Trigger via API

```bash
curl -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  https://api.github.com/repos/OWNER/REPO/actions/workflows/ai-teammate.yml/dispatches \
  -d '{
    "ref": "main",
    "inputs": {
      "config_file": "agents/teammate_config.json"
    }
  }'
```

### Trigger with Encoded Config

```bash
curl -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  https://api.github.com/repos/OWNER/REPO/actions/workflows/ai-teammate.yml/dispatches \
  -d '{
    "ref": "main",
    "inputs": {
      "config_file": "agents/teammate_config.json",
      "encoded_config": "{\"params\":{\"inputJql\":\"key = PROJ-123\"}}"
    }
  }'
```

## Features

### 1. DMtools Caching

The workflow caches the DMtools installation to speed up subsequent runs:

```yaml
- name: Cache DMTools
  id: cache-dmtools
  uses: actions/cache@v4
  with:
    path: ~/.dmtools
    key: dmtools-v1.7.127-${{ runner.os }}
```

### 2. Concurrency Control

Prevents multiple runs for the same config file:

```yaml
concurrency:
  group: ai-teammate-agent-${{ inputs.config_file }}
  cancel-in-progress: false
```

### 3. Dynamic Configuration

Use `encoded_config` to override or extend the base configuration at runtime:

```bash
# Base config from file
dmtools run agents/teammate_config.json

# With override
dmtools run agents/teammate_config.json '{"params":{"inputJql":"key = PROJ-456"}}'
```

## Advanced Examples

### Schedule-Based Execution

Add a schedule trigger to run daily:

```yaml
on:
  workflow_dispatch:
    # ... inputs as above
  schedule:
    - cron: '0 9 * * 1-5'  # Run at 9 AM on weekdays
```

### Multiple Teammate Configurations

Create a matrix strategy for different teams:

```yaml
jobs:
  ai-teammate-agent:
    strategy:
      matrix:
        config:
          - agents/backend_teammate.json
          - agents/frontend_teammate.json
          - agents/qa_teammate.json
    steps:
      # ... use ${{ matrix.config }} for config_file
```

### Integration with Jira Webhooks

Trigger workflow from Jira using webhook + GitHub API:

1. Create webhook in Jira
2. Set up middleware to call GitHub Actions API
3. Pass ticket key in `encoded_config`

## Troubleshooting

### DMtools Installation Fails

Check Java installation:
```yaml
- name: Debug Java
  run: |
    java -version
    which java
    echo $JAVA_HOME
```

### Configuration Not Found

Verify file path:
```yaml
- name: Check Config File
  run: |
    ls -la agents/
    cat agents/teammate_config.json
```

### API Authentication Errors

Test credentials:
```yaml
- name: Test Jira Connection
  run: |
    dmtools jira_get_ticket PROJ-1
```

## Best Practices

1. **Version Pinning**: Use specific DMtools version in cache key and install script
2. **Secret Rotation**: Regularly rotate API tokens and secrets
3. **Config Validation**: Use JSON schema validation for config files
4. **Error Notifications**: Add Slack/email notifications on failure
5. **Audit Logging**: Log all ticket modifications for compliance

## Related Documentation

- [Teammate Job Reference](../jobs/README.md#teammate)
- [Teammate Configuration Guide](../agents/teammate-configs.md)
- [DMtools Installation](../installation/README.md)
- [Jira Configuration](../configuration/integrations/jira.md)
- [AWS Bedrock Configuration](../configuration/ai-providers/bedrock-claude.md)
