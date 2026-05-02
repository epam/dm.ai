# DMTools
Delivery Management Tools

[![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=latest%20version)](https://github.com/epam/dm.ai/releases/latest) [![codecov](https://codecov.io/gh/epam/dm.ai/branch/main/graph/badge.svg)](https://codecov.io/gh/epam/dm.ai) [![](https://jitpack.io/v/epam/dm.ai.svg)](https://jitpack.io/#epam/dm.ai)

> 🚀 **Latest DMTools release: [v1.7.179](https://github.com/epam/dm.ai/releases/tag/v1.7.179)** — installer assets are published under `https://github.com/epam/dm.ai/releases/latest/download/`.

---

## 📚 Documentation

**Complete documentation is available in [dmtools-ai-docs/](dmtools-ai-docs/):**

### Quick Links
- 🚀 **[Getting Started](dmtools-ai-docs/references/installation/README.md)** - Install, configure, and run your first commands
  - [Installation Guide](dmtools-ai-docs/references/installation/README.md)
  - [Configuration Guide](dmtools-ai-docs/references/configuration/README.md)
  - [Examples](dmtools-ai-docs/references/examples/)

- 💻 **[CLI Usage](dmtools-ai-docs/references/mcp-tools/README.md)** - Command-line interface guide
  - [MCP Tools Reference](dmtools-ai-docs/references/mcp-tools/README.md) - 67+ built-in tools

- ⚙️ **[Jobs (JobRunner)](dmtools-ai-docs/references/jobs/README.md)** - 20+ automation jobs
  - [Job Reference](dmtools-ai-docs/references/jobs/README.md)
  - [Workflows](dmtools-ai-docs/references/workflows/)
  - [Reporting](dmtools-ai-docs/references/reporting/)

- 🤖 **[AI Teammate Workflows](dmtools-ai-docs/references/workflows/github-actions-teammate.md)** - GitHub Actions automation
  - [GitHub Actions Teammate](dmtools-ai-docs/references/workflows/github-actions-teammate.md)
  - [Examples](dmtools-ai-docs/references/examples/)
  - [Agent Skill Guide](dmtools-ai-docs/SKILL.md)

- 🔌 **[Integrations](dmtools-ai-docs/references/configuration/integrations/)** - Connect to Jira, Confluence, Figma, GitHub, etc.

- 📖 **[Complete Documentation Index](dmtools-ai-docs/README.md)**

---

## Quick Start

### Installation

**Latest Version:** ![Latest Release](https://img.shields.io/github/v/release/epam/dm.ai?label=)

**macOS / Linux / Git Bash:**
```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install | bash
```

**Windows (cmd.exe, PowerShell, Windows Terminal):**
```cmd
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
```

**Verify installation:**
```bash
dmtools --version
dmtools list
```

See the [installation guide](dmtools-ai-docs/references/installation/README.md) and [troubleshooting guide](dmtools-ai-docs/references/installation/troubleshooting.md) for details.

### Upgrading from legacy installs

1. Back up `~/.dmtools` before changing anything so you can restore the current wrapper, JAR, and local state if the migration goes wrong.
2. Re-run the installer from `https://github.com/epam/dm.ai/releases/latest/download/install.sh` (or `install.bat` / `install.ps1` on Windows) to replace any `IstiN/dmtools` or raw GitHub bootstrap paths with the EPAM release asset path.
3. Preserve or merge your existing `dmtools.env`, `dmtools-local.env`, and any other local configuration instead of overwriting them during the reinstall.
4. If `which dmtools` or `Get-Command dmtools` resolves outside `~/.dmtools/bin`, remove stale aliases, wrapper scripts, and outdated PATH entries from your shell profile or CI bootstrap steps.
5. Verify the migrated install with `dmtools --version` and `dmtools list`, and refresh CI cache keys that still reference legacy install URLs.
6. If the migration fails, roll back by restoring the backup copy of `~/.dmtools` and re-enabling the previous wrapper or PATH entry.

   Tagged reinstall example for rollback or migration validation:

   **macOS / Linux / Git Bash:**
   ```bash
   curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install | bash -s -- v1.7.179
   ```

   **Windows:**
   ```cmd
   set DMTOOLS_VERSION=v1.7.179 && curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
   ```

### Deprecated compatibility shims

- `CodeGenerator` is deprecated and kept only as a compatibility shim for one release. Invocations now log a warning and return a no-op response; migrate affected automation before `v1.8.0`.

### Configuration

```bash
# Create configuration file
cat > dmtools.env << EOF
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_EMAIL=your-email@company.com
JIRA_API_TOKEN=your-jira-api-token
GEMINI_API_KEY=your-gemini-api-key
EOF

# Secure the file
chmod 600 dmtools.env
```

### Your First Command

```bash
# Get a Jira ticket
dmtools jira_get_ticket YOUR-123

# Search tickets
dmtools jira_search_by_jql "project = PROJ AND status = Open" "summary,status"

# List all available tools
dmtools list
```

See the [examples reference](dmtools-ai-docs/references/examples/) for more usage patterns.

---

## Simple Run (Legacy)
1. Download the release from: [DMTools Releases](https://github.com/epam/dm.ai/releases)
2. Set environment variables.
3. Run the command:
   ```bash
   java -cp dmtools.jar com.github.istin.dmtools.job.UrlEncodedJobTrigger "$JOB_PARAMS"
   ```
   `JOB_PARAMS` is a Base64-encoded payload accepted by `UrlEncodedJobTrigger`.

---

## Build JAR
To build the JAR file, run:
```bash
gradle shadowJar
```

---

## 🔐 OAuth2 Authentication Setup

DMTools includes a **web application with OAuth2 authentication** supporting Google, Microsoft, and GitHub login.

### Quick Setup
For complete OAuth2 setup instructions including:
- ✅ **Google, Microsoft, GitHub** OAuth provider configuration  
- ✅ **Production deployment** on Google Cloud Run
- ✅ **GitHub Actions** with secrets management
- ✅ **Security configuration** and troubleshooting

📖 **OAuth2 deployment notes are maintained with the server configuration in this repository.**

### Live Application
- **Production**: https://ai-native.cloud
- **API Documentation**: https://ai-native.cloud/swagger-ui/index.html

---

## Configuration

### Jira Configuration
```properties
JIRA_BASE_PATH=https://jira.company.com
JIRA_LOGIN_PASS_TOKEN=base64(email:token)
JIRA_AUTH_TYPE=Bearer
JIRA_WAIT_BEFORE_PERFORM=true
JIRA_LOGGING_ENABLED=true
JIRA_CLEAR_CACHE=true
JIRA_EXTRA_FIELDS_PROJECT=customfield_10001,customfield_10002
JIRA_EXTRA_FIELDS=summary,description,status
```

#### How to Get Jira Token:
1. Go to **Jira > Profile Settings > Security**.
2. Under "API Token", click **Create and manage API tokens**.
3. Click **Create API token** and give it a name (e.g., `dm_tools`).
4. Copy the generated token.
5. Convert `email:token` to base64 format.
6. Add the following to your configuration:
   ```properties
   JIRA_LOGIN_PASS_TOKEN=base64(email:token)
   JIRA_AUTH_TYPE=Bearer
   ```

---

### Rally Configuration
```properties
RALLY_TOKEN=your_rally_token
RALLY_PATH=https://rally1.rallydev.com
```

#### How to Get Rally Token:
1. Log in to Rally.
2. Navigate to **User Profile > API Keys**.
3. Generate a new API key.
4. Copy the token and add it to the configuration.

---

### Bitbucket Configuration
```properties
BITBUCKET_TOKEN=Bearer your_token
BITBUCKET_API_VERSION=V2
BITBUCKET_WORKSPACE=your-workspace
BITBUCKET_REPOSITORY=your-repo
BITBUCKET_BRANCH=main
BITBUCKET_BASE_PATH=https://api.bitbucket.org
```

#### How to Get Bitbucket Token:
1. Go to **Bitbucket Settings > App passwords**.
2. Click **Create app password**.
3. Select the required permissions (e.g., read/write access).
4. Copy the generated token.
5. Add the following to your configuration:
   ```properties
   BITBUCKET_TOKEN=Bearer [token]
   ```

---

### GitHub Configuration
```properties
SOURCE_GITHUB_TOKEN=your_github_token
SOURCE_GITHUB_WORKSPACE=your-org
SOURCE_GITHUB_REPOSITORY=your-repo
SOURCE_GITHUB_BRANCH=main
SOURCE_GITHUB_BASE_PATH=https://api.github.com
```

#### How to Get GitHub Token:
1. Go to **GitHub Settings > Developer settings > Personal access tokens**.
2. Generate a new token (classic).
3. Select the required scopes (e.g., repo access).
4. Copy the generated token.
5. Add the following to your configuration:
   ```properties
   SOURCE_GITHUB_TOKEN=[token]
   ```

---

### GitLab Configuration
```properties
GITLAB_TOKEN=your_gitlab_token
GITLAB_WORKSPACE=your-workspace
GITLAB_REPOSITORY=your-repo
GITLAB_BRANCH=main
GITLAB_BASE_PATH=https://gitlab.com/api/v4
```

#### How to Get GitLab Token:
1. Go to **GitLab > User Settings > Access Tokens**.
2. Create a new personal access token.
3. Select the required scopes (e.g., API access).
4. Copy the generated token and add it to the configuration.

---

### Confluence Configuration
```properties
CONFLUENCE_BASE_PATH=https://confluence.company.com
CONFLUENCE_LOGIN_PASS_TOKEN=base64(email:token)
CONFLUENCE_GRAPHQL_PATH=/graphql
CONFLUENCE_DEFAULT_SPACE=TEAM
```

#### How to Get Confluence Token:
1. Go to **Confluence > Profile > Settings > Password**.
2. Create an API token.
3. Convert `email:token` to base64 format.
4. Add the following to your configuration:
   ```properties
   CONFLUENCE_LOGIN_PASS_TOKEN=base64(email:token)
   ```

---

### AI Configuration
```properties
DIAL_BATH_PATH=https://api.dial.com/v1
DIAL_API_KEY=your_dial_key
DIAL_MODEL=gpt-4
```

#### How to Get DIAL Token:
1. Go to the [DIAL Platform](https://platform.openai.com).
2. Request your API key.
3. Create a new secret key.
4. Copy the key and add it to the configuration:
   ```properties
   DIAL_API_KEY=[token]
   ```

---

### AI Models Configuration
```properties
CODE_AI_MODEL=gpt-4
TEST_AI_MODEL=gpt-4
```

****---

### Figma Configuration
```properties
FIGMA_BASE_PATH=https://api.figma.com/v1
FIGMA_TOKEN=your_figma_token
```

#### How to Get Figma Token:
1. Log in to Figma.
2. Go to **Account Settings > Personal Access Tokens**.
3. Generate a new token.
4. Copy the token and add it to the configuration.

---

### Firebase Configuration
```properties
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_SERVICE_ACCOUNT_JSON_AUTH={"type":"service_account",...}
```

#### How to Get Firebase Credentials:
1. Go to the [Firebase Console](https://console.firebase.google.com).
2. Navigate to **Project Settings > Service Accounts**.
3. Generate a new private key.
4. Download the JSON file and use it in the configuration.

---

## Notes
- Replace all placeholder values (e.g., `your_token`, `your-org`) with actual values.
- **Never commit sensitive tokens to version control.** Use environment variables or secure vaults instead.

### Prompt Preparation Config
```properties
# Prompt Chunk Configurations
PROMPT_CHUNK_TOKEN_LIMIT=4000
PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB=4
PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB=4
PROMPT_CHUNK_MAX_FILES=10
```
