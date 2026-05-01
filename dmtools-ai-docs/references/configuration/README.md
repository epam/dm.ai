# DMtools Configuration Guide

## 📋 Configuration Overview

DMtools uses a hierarchical configuration system with multiple sources. Configuration is loaded in priority order (highest to lowest):

1. **Environment Variables** - Set in your shell or system
2. **dmtools.env** - Local configuration file (current directory)
3. **dmtools-local.env** - User-specific configuration
4. **application.properties** - Server configuration (Spring Boot)

## 🔧 Configuration Methods

### Method 1: Environment Variables (Recommended for CI/CD)

```bash
# Export in your shell
export JIRA_BASE_PATH=https://company.atlassian.net
export JIRA_LOGIN_PASS_TOKEN=$(echo -n "email@company.com:token" | base64)
export GEMINI_API_KEY=your-api-key

# Run DMtools
dmtools list
```

### Method 2: dmtools.env File (Recommended for Development)

Create a `dmtools.env` file in your project directory:

```bash
# dmtools.env
JIRA_BASE_PATH=https://company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64_encoded_credentials
JIRA_AUTH_TYPE=Bearer

# AI Provider (choose one)
GEMINI_API_KEY=your-gemini-api-key
#OPEN_AI_API_KEY=sk-...
#DIAL_API_KEY=...

# Azure DevOps
ADO_BASE_PATH=https://dev.azure.com/organization
ADO_PAT=your-personal-access-token
ADO_PROJECT=YourProject

# GitHub
SOURCE_GITHUB_TOKEN=ghp_...
SOURCE_REPO_PATH=/path/to/local/repo

# Figma
FIGMA_ACCESS_TOKEN=your-figma-token
```

### Method 3: dmtools-local.env (User-specific)

For personal settings that shouldn't be committed:

```bash
# ~/.dmtools/dmtools-local.env
# Personal tokens and keys
JIRA_LOGIN_PASS_TOKEN=your_personal_token
GEMINI_API_KEY=your_personal_key
```

## 🔑 Essential Configuration Variables

### Core Settings

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `PROMPT_CHUNK_TOKEN_LIMIT` | Max tokens per AI prompt chunk | No | `4000` |
| `PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB` | Max file size for context | No | `4` |
| `DMTOOLS_DEBUG` | Enable debug logging | No | `true` |

### Integration Credentials

| Integration | Required Variables | Optional Variables |
|-------------|-------------------|-------------------|
| **Jira** | `JIRA_BASE_PATH`, `JIRA_LOGIN_PASS_TOKEN` | `JIRA_AUTH_TYPE`, `JIRA_FIELDS_MAPPING` |
| **Azure DevOps** | `ADO_BASE_PATH`, `ADO_PAT`, `ADO_PROJECT` | `ADO_AREA_PATH`, `ADO_ITERATION_PATH` |
| **Figma** | `FIGMA_ACCESS_TOKEN` | `FIGMA_FILE_KEY` |
| **Confluence** | `CONFLUENCE_BASE_PATH`, `CONFLUENCE_LOGIN_PASS_TOKEN` | `CONFLUENCE_SPACE_KEY` |
| **Teams** | `TEAMS_TENANT_ID`, `TEAMS_CLIENT_ID`, `TEAMS_CLIENT_SECRET` | `TEAMS_CHANNEL_ID` |
| **GitHub** | `SOURCE_GITHUB_TOKEN` | `SOURCE_REPO_PATH`, `SOURCE_ORG` |
| **GitLab** | `GITLAB_TOKEN`, `GITLAB_URL` | `GITLAB_PROJECT_ID` |

### AI Provider Configuration

Configure at least one AI provider:

| Provider | Required Variables | Optional Variables | Notes |
|----------|-------------------|-------------------|-------|
| **Gemini** | `GEMINI_API_KEY` | `GEMINI_MODEL` | Free tier available |
| **OpenAI** | `OPEN_AI_API_KEY` | `OPEN_AI_MODEL`, `OPEN_AI_ORG_ID` | GPT-4o, GPT-4-turbo |
| **Claude (Bedrock)** | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | `AWS_REGION`, `BEDROCK_MODEL` | Via AWS |
| **DIAL** | `DIAL_API_KEY`, `DIAL_URL` | `DIAL_MODEL` | Enterprise gateway |
| **Ollama** | `OLLAMA_BASE_URL` | `OLLAMA_MODEL` | Local models |

## 📝 Configuration File Examples

### Minimal Configuration (Jira + Gemini)

```bash
# dmtools.env - Minimal setup
JIRA_BASE_PATH=https://mycompany.atlassian.net
JIRA_LOGIN_PASS_TOKEN=<your-base64-encoded-email:api-token>
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
```

### Full Development Configuration

```bash
# dmtools.env - Complete development setup
# Jira Configuration
JIRA_BASE_PATH=https://mycompany.atlassian.net
JIRA_LOGIN_PASS_TOKEN=<your-base64-encoded-email:api-token>
JIRA_AUTH_TYPE=Bearer
JIRA_FIELDS_MAPPING=customfield_10001:StoryPoints,customfield_10002:Epic

# Azure DevOps
ADO_BASE_PATH=https://dev.azure.com/myorg
ADO_PAT=YOUR_ADO_PERSONAL_ACCESS_TOKEN
ADO_PROJECT=MyProject
ADO_AREA_PATH=MyProject\\Backend
ADO_ITERATION_PATH=MyProject\\Sprint 23

# AI Providers (multiple configured, runtime selection)
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
GEMINI_MODEL=gemini-2.0-flash
OPEN_AI_API_KEY=YOUR_OPENAI_API_KEY_HERE
OPEN_AI_MODEL=gpt-4o

# Source Control
SOURCE_GITHUB_TOKEN=YOUR_GITHUB_TOKEN_HERE
SOURCE_REPO_PATH=/Users/me/projects/myapp
SOURCE_ORG=mycompany

# Figma
FIGMA_ACCESS_TOKEN=YOUR_FIGMA_ACCESS_TOKEN_HERE
FIGMA_FILE_KEY=ABC123XYZ

# Confluence
CONFLUENCE_BASE_PATH=https://mycompany.atlassian.net/wiki
CONFLUENCE_LOGIN_PASS_TOKEN=<your-base64-encoded-email:api-token>
CONFLUENCE_SPACE_KEY=DOCS

# Performance Tuning
PROMPT_CHUNK_TOKEN_LIMIT=4000
PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB=4
```

### CI/CD Configuration

```yaml
# .github/workflows/dmtools.yml
env:
  JIRA_BASE_PATH: ${{ secrets.JIRA_BASE_PATH }}
  JIRA_LOGIN_PASS_TOKEN: ${{ secrets.JIRA_TOKEN }}
  GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
```

## 🔐 Security Best Practices

### 1. Never Commit Credentials

```bash
# .gitignore
dmtools.env
dmtools-local.env
*.env
.env*
```

### 2. Use Base64 Encoding for Jira

```bash
# Encode credentials
echo -n "email@company.com:api_token" | base64
# Output: <your-base64-encoded-email:api-token>

# Set in configuration
JIRA_LOGIN_PASS_TOKEN=<your-base64-encoded-email:api-token>
```

### 3. Rotate Tokens Regularly

- API tokens should be rotated every 90 days
- Use separate tokens for dev/staging/production
- Revoke unused tokens immediately

### 4. Use Environment-Specific Files

```bash
dmtools.env.development
dmtools.env.staging
dmtools.env.production
```

### 5. Secure Storage Options

For production environments, consider:
- AWS Secrets Manager
- HashiCorp Vault
- Azure Key Vault
- Kubernetes Secrets

## 🧪 Testing Configuration

### Verify Jira Connection

```bash
# Test Jira access
dmtools jira_get_ticket PROJ-123

# Search tickets
dmtools jira_search_by_jql "project = PROJ AND status = Open"
```

### Verify AI Provider

```bash
# Test Gemini
dmtools gemini_ai_chat "Hello, are you working?"

# Test OpenAI
dmtools openai_ai_chat "Hello, are you working?"
```

### Verify Azure DevOps

```bash
# Get work item
dmtools ado_get_work_item 12345

# List iterations
dmtools ado_get_iterations
```

### List All Configured Tools

```bash
# Show all available MCP tools
dmtools list

# Should display 67+ tools if all integrations are configured
```

## 🔄 Configuration Precedence Examples

```bash
# Scenario 1: Environment variable overrides file
export GEMINI_API_KEY=env-key
# dmtools.env contains: GEMINI_API_KEY=file-key
# Result: env-key is used

# Scenario 2: dmtools.env overrides dmtools-local.env
# dmtools.env: JIRA_BASE_PATH=https://prod.atlassian.net
# dmtools-local.env: JIRA_BASE_PATH=https://dev.atlassian.net
# Result: https://prod.atlassian.net is used

# Scenario 3: Check active configuration
dmtools --show-config  # Shows resolved configuration
```

## 📊 Configuration for Different Workflows

### Test Case Generation Setup

```bash
# Required for test generation
JIRA_BASE_PATH=https://company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=...
GEMINI_API_KEY=...  # Or other AI provider

# Optional for enhanced features
JIRA_XRAY_CLIENT_ID=...  # For Xray integration
CONFLUENCE_BASE_PATH=...  # For documentation
```

### Code Generation Setup

```bash
# Required
SOURCE_GITHUB_TOKEN=...
SOURCE_REPO_PATH=/path/to/repo
OPEN_AI_API_KEY=...  # GPT-4 recommended for code

# Optional
SONAR_TOKEN=...  # For code quality checks
```

### Documentation Generation Setup

```bash
# Required
CONFLUENCE_BASE_PATH=...
CONFLUENCE_LOGIN_PASS_TOKEN=...
CONFLUENCE_SPACE_KEY=DOCS

# AI provider for content generation
GEMINI_API_KEY=...
```

## 🚀 Next Steps

1. **Choose your integrations** - Start with Jira and one AI provider
2. **Create dmtools.env** - Copy examples above and add your credentials
3. **Test connections** - Run test commands to verify setup
4. **Explore specific guides**:
   - [Jira Configuration](integrations/jira.md)
   - [Gemini AI Setup](ai-providers/gemini.md)
   - [Azure DevOps Setup](integrations/ado.md)
   - [CLI Output Formats](cli-output-formats.md)

## 🆘 Troubleshooting

Common configuration issues:

| Issue | Solution |
|-------|----------|
| "Authentication failed" | Check token encoding, especially for Jira base64 |
| "API rate limit" | Add delays or use different API keys |
| "Connection refused" | Verify BASE_PATH URLs and network access |
| "Invalid API key" | Regenerate token from provider dashboard |

---

*Need help? Check integration-specific guides or report issues at [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)*
