# DMTools
Delivery Management Tools

[![Latest Release](https://img.shields.io/github/v/release/IstiN/dmtools?label=latest%20version)](https://github.com/IstiN/dmtools/releases/latest) [![codecov](https://codecov.io/gh/IstiN/dmtools/branch/main/graph/badge.svg)](https://codecov.io/gh/IstiN/dmtools) [![](https://jitpack.io/v/IstiN/dmtools.svg)](https://jitpack.io/#IstiN/dmtools)

---

## 📚 Documentation

**Complete documentation is available in the [docs/](docs/) folder:**

### Quick Links
- 🚀 **[Getting Started](docs/getting-started/)** - Install, configure, and run your first commands
  - [Installation Guide](docs/getting-started/installation.md)
  - [Configuration Guide](docs/getting-started/configuration.md)
  - [First Steps](docs/getting-started/first-steps.md)

- 💻 **[CLI Usage](docs/cli-usage/)** - Command-line interface guide
  - [MCP Tools Reference](docs/cli-usage/mcp-tools.md) - 67 built-in tools

- ⚙️ **[Jobs (JobRunner)](docs/jobs/)** - 20 automation jobs
  - [Business Analysis Jobs](docs/jobs/business-analysis/)
  - [Development Jobs](docs/jobs/development/)
  - [QA Jobs](docs/jobs/qa/)
  - [Reporting Jobs](docs/jobs/reports/)

- 🤖 **[AI Teammate Workflows](docs/ai-teammate/)** - GitHub Actions automation
  - [Overview](docs/ai-teammate/README.md)
  - [JavaScript Actions](docs/ai-teammate/javascript-actions.md)
  - [Agent Configuration](docs/ai-teammate/agent-configuration.md)

- 🔌 **[Integrations](docs/integrations/)** - Connect to Jira, Confluence, Figma, GitHub, etc.

- 📖 **[Complete Documentation Index](docs/README.md)**

---

## Quick Start

### Installation

**Latest Version:** ![Latest Release](https://img.shields.io/github/v/release/IstiN/dmtools?label=)

**macOS / Linux / Git Bash:**
```bash
curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install | bash
```

**Windows (cmd.exe, PowerShell, Windows Terminal):**
```cmd
curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
```

**Specific Version (e.g., v1.7.126):**

**macOS / Linux / Git Bash:**
```bash
curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/v1.7.126/install.sh | bash -s v1.7.126
```

**Windows:**
```cmd
set DMTOOLS_VERSION=v1.7.126 && curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/v1.7.126/install.bat -o "%TEMP%\dmtools-install.bat" && "%TEMP%\dmtools-install.bat"
```

**Verify installation:**
```bash
dmtools --version
dmtools list
```

See [INSTALL_INSTRUCTIONS.md](INSTALL_INSTRUCTIONS.md) for detailed installation guide and troubleshooting.

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

See **[First Steps Guide](docs/getting-started/first-steps.md)** for more examples.

---

## Simple Run (Legacy)
1. Download the release from: [DMTools Releases](https://github.com/IstiN/dmtools/releases)
2. Set environment variables.
3. Run the command:
   ```bash
   java -cp dmtools.jar com.github.istin.dmtools.job.UrlEncodedJobTrigger "$JOB_PARAMS"
   ```
   API of base64 encoded `JOB_PARAMS`: [API Description](https://github.com/IstiN/dmtools/blob/main/api_description.md)

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

📖 **See the comprehensive guide**: [OAUTH_SETUP.md](OAUTH_SETUP.md)

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