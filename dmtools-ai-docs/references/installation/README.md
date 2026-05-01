# DMtools Installation Guide

## 🚀 Quick Installation

The fastest way to install DMtools:

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
```

If you only need focused AI assistant skills, see [Install Only the Skills You Need](#install-only-the-skills-you-need).

This script will:
1. ✅ Check for Java 17+ (install if missing)
2. ✅ Download the latest DMtools release
3. ✅ Install to `~/.dmtools/`
4. ✅ Create the `dmtools` command alias
5. ✅ Set up shell integration (bash/zsh)

**⚠️ IMPORTANT**: After installation, you **must** configure `dmtools.env` file. See [Configuration Setup](#-configuration-setup) below.

## 📦 Installation Methods

### Method 1: Automatic Installation (Recommended)

```bash
# Latest stable version
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash

# Specific version
curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install.sh | bash -s -- v1.7.179
```

## Install Only the Skills You Need

Use the focused skill packages when you want integration-specific slash commands in your AI assistant instead of the full `/dmtools` bundle.

| Skill | Slash command | Latest ZIP | Latest TAR |
|------|------|------|------|
| Full DMtools | `/dmtools` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-skill.tar.gz` |
| Jira | `/dmtools-jira` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-jira-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-jira-skill.tar.gz` |
| GitHub | `/dmtools-github` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-github-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-github-skill.tar.gz` |
| Azure DevOps | `/dmtools-ado` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-ado-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-ado-skill.tar.gz` |
| TestRail | `/dmtools-testrail` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-testrail-skill.zip` | `https://github.com/epam/dm.ai/releases/latest/download/dmtools-testrail-skill.tar.gz` |

### Skill Installer Examples

```bash
# Install only Jira
DMTOOLS_SKILLS=jira curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash

# Install Jira + GitHub together
DMTOOLS_SKILLS=jira,github curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/skill-install.sh | bash

# Run the installer locally and pass the package list directly
bash install.sh --skills ado,testrail
```

```powershell
$env:DMTOOLS_SKILLS = "jira,github"
irm https://github.com/epam/dm.ai/releases/latest/download/skill-install.ps1 | iex
```

### Manual Focused Package Installation

1. Download the ZIP or TAR for the skill you need.
2. Extract it into `.cursor/skills/`, `.claude/skills/`, or `.codex/skills/`.
3. The extracted folder name becomes the slash command, for example `dmtools-jira` → `/dmtools-jira`.

### Upgrading from legacy installs

1. Replace any `IstiN/dmtools` or `raw.githubusercontent.com/.../main/install.sh` bootstrap commands with `https://github.com/epam/dm.ai/releases/latest/download/install.sh`.
2. Remove stale aliases or wrapper scripts that bypass `~/.dmtools/bin/dmtools` before reinstalling.
3. If a CI job caches `~/.dmtools`, update the cache key when switching away from legacy install URLs.

### Method 2: Local Development Installation

For developers working on DMtools:

```bash
# Clone the repository
git clone https://github.com/epam/dm.ai.git
cd dm.ai

# Build and install locally
./buildInstallLocal.sh

# This will:
# 1. Build the fat JAR with ./gradlew :dmtools-core:shadowJar
# 2. Copy to ~/.dmtools/dmtools.jar
# 3. Use your locally built version
```

### Method 3: Manual Installation

```bash
# Download the JAR directly
DMTOOLS_VERSION=1.7.179  # Replace with desired version
wget -O ~/.dmtools/dmtools.jar \
  "https://github.com/epam/dm.ai/releases/download/v${DMTOOLS_VERSION}/dmtools-v${DMTOOLS_VERSION}-all.jar"

# Create the wrapper script
cat > ~/bin/dmtools << 'EOF'
#!/bin/bash
java -jar ~/.dmtools/dmtools.jar "$@"
EOF

chmod +x ~/bin/dmtools

# Add to PATH in ~/.bashrc or ~/.zshrc
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

## ☕ Java Installation

DMtools requires Java 17 or higher. The installer handles this automatically, but you can also install manually:

### Automatic Java Installation

The install script will automatically install Java 17 using SDKMAN if Java is missing or outdated:

```bash
# The installer runs these commands internally:
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17-open
sdk default java 17-open
```

### Manual Java Installation

#### macOS
```bash
# Using Homebrew
brew install openjdk@17
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### Linux
```bash
# Using SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17-open

# Or using package manager (Ubuntu/Debian)
sudo apt update
sudo apt install openjdk-17-jdk
```

#### Windows (WSL)
```bash
# In WSL terminal
sudo apt update
sudo apt install openjdk-17-jdk

# Or use SDKMAN as shown above
```

### Verify Java Version
```bash
java -version
# Should show: openjdk version "17" or higher
```

## ✅ Verification

After installation, verify DMtools is working:

```bash
# Check version
dmtools --version
# Output: DMtools version v1.7.179 (or your installed version)

# List all available MCP tools
dmtools list
# Should show 67+ tools organized by category

# Test a simple command
dmtools help
# Shows usage information

# Test MCP tool (if configured)
dmtools cli_execute_command "echo 'DMtools is working!'"
```

## ⚙️ Configuration Setup

**CRITICAL: DMtools requires configuration before use.**

After installation, create a `dmtools.env` file to configure integrations:

### Step 1: Create Configuration File

Create `dmtools.env` in your project directory or home directory:

```bash
# In your project directory
touch dmtools.env

# Or globally in home directory
touch ~/dmtools.env
```

**⚠️ SECURITY**: Never commit `dmtools.env` to git - it contains sensitive API keys and tokens. This file is already in `.gitignore`.

### Step 2: Configure Integrations

Add your integration credentials to `dmtools.env`:

```bash
# Jira Configuration (Required for Jira tools)
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_EMAIL=your-email@company.com
JIRA_API_TOKEN=your-jira-api-token
JIRA_AUTH_TYPE=Basic

# AI Provider (Required for AI features)
# Choose one or more:
GEMINI_API_KEY=your-gemini-api-key          # Free tier available
OPEN_AI_API_KEY=your-openai-api-key        # OpenAI
BEDROCK_ACCESS_KEY_ID=your-aws-key          # AWS Bedrock (Claude)
BEDROCK_SECRET_ACCESS_KEY=your-aws-secret
BEDROCK_REGION=us-east-1
BEDROCK_MODEL_ID=anthropic.claude-3-5-sonnet-20241022-v2:0

# Confluence (Optional)
CONFLUENCE_BASE_PATH=https://your-company.atlassian.net/wiki
CONFLUENCE_EMAIL=your-email@company.com
CONFLUENCE_API_TOKEN=your-confluence-token
CONFLUENCE_DEFAULT_SPACE=TEAM

# Figma (Optional)
FIGMA_TOKEN=your-figma-personal-access-token
FIGMA_BASE_PATH=https://api.figma.com

# Azure DevOps (Optional)
ADO_BASE_PATH=https://dev.azure.com/your-organization
ADO_PAT=your-personal-access-token
ADO_PROJECT=your-project-name

# GitHub (Optional)
SOURCE_GITHUB_TOKEN=your-github-pat

# Configuration
DEFAULT_LLM=gemini
DEFAULT_TRACKER=jira
```

### Step 3: Generate API Tokens

#### Jira API Token
1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Click "Create API token"
3. Give it a name (e.g., "DMtools")
4. Copy the token immediately (it won't be shown again)
5. Add to `dmtools.env`: `JIRA_API_TOKEN=<your-token>`

#### Gemini API Key (Free Tier)
1. Go to https://aistudio.google.com/app/apikey
2. Click "Get API key"
3. Create a new key or use existing
4. Copy the key
5. Add to `dmtools.env`: `GEMINI_API_KEY=<your-key>`

**Note**: Gemini offers free tier with 15 requests/minute - perfect for getting started.

#### OpenAI API Key
1. Go to https://platform.openai.com/api-keys
2. Create new secret key
3. Copy immediately (shown only once)
4. Add to `dmtools.env`: `OPEN_AI_API_KEY=<your-key>`

#### AWS Bedrock (Claude)
1. Create IAM user with Bedrock permissions
2. Generate access key and secret
3. Add to `dmtools.env`:
   ```
   BEDROCK_ACCESS_KEY_ID=<your-key>
   BEDROCK_SECRET_ACCESS_KEY=<your-secret>
   ```

### Step 4: Configuration Hierarchy

DMtools searches for configuration in this order:

1. **Environment variables** (highest priority)
2. **`dmtools.env`** in current directory
3. **`dmtools-local.env`** in current directory
4. **`dmtools.env`** in dmtools.sh script directory
5. **`dmtools-local.env`** in dmtools.sh script directory

**Tip**: Use `dmtools-local.env` for local overrides that won't be committed to git.

### Step 5: Verify Configuration

```bash
# Test Jira connection (if configured)
dmtools jira_get_ticket PROJ-1

# Test AI provider (if configured)
dmtools gemini_ai_chat "Hello, are you working?"

# List all available tools
dmtools list
```

### Configuration Examples

See complete configuration examples:
- [Jira Configuration](../configuration/integrations/jira.md)
- [Gemini AI](../configuration/ai-providers/gemini.md)
- [AWS Bedrock](../configuration/ai-providers/bedrock-claude.md)
- [All Integrations](../configuration/README.md)

## 🗂️ Installation Structure

After installation, you'll have:

```
~/.dmtools/
├── dmtools.jar           # Main DMtools JAR file
├── dmtools.env          # Environment configuration (created by you)
└── logs/                # Execution logs (created on first run)

~/bin/
└── dmtools              # Executable wrapper script

~/.bashrc or ~/.zshrc
└── # PATH and alias additions
```

## 🔧 Shell Integration

The installer adds these to your shell configuration:

```bash
# Added to ~/.bashrc or ~/.zshrc
export PATH="$HOME/bin:$PATH"
alias dmtools='java -jar ~/.dmtools/dmtools.jar'

# For development with local JAR
alias dmtools-dev='java -jar /path/to/your/dmtools-core/build/libs/dmtools-v*-all.jar'
```

## 🐳 Docker Installation (Optional)

For containerized environments:

```dockerfile
FROM openjdk:17-slim

# Install DMtools
RUN apt-get update && apt-get install -y curl bash \
    && curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash \
    && apt-get clean

# Set up environment
ENV PATH="/root/bin:${PATH}"

WORKDIR /workspace

ENTRYPOINT ["dmtools"]
```

Build and run:
```bash
docker build -t dmtools .
docker run -it dmtools --version
```

## 🔄 Updating DMtools

### Update to Latest Version
```bash
# Re-run the installer
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
```

### Update to Specific Version
```bash
curl -fsSL https://github.com/epam/dm.ai/releases/download/v1.7.179/install.sh | bash -s -- v1.7.179
```

### Check Current Version
```bash
dmtools --version
```

## 🗑️ Uninstallation

To completely remove DMtools:

```bash
# Remove DMtools files
rm -rf ~/.dmtools
rm ~/bin/dmtools

# Remove from shell configuration
# Edit ~/.bashrc or ~/.zshrc and remove DMtools-related lines

# Reload shell
source ~/.bashrc  # or ~/.zshrc
```

## 🆘 Common Issues

If you encounter problems, check the [Troubleshooting Guide](troubleshooting.md).

## 📝 Next Steps

After installation:
1. [Configure environment variables](../configuration/README.md)
2. [Set up your first integration](../configuration/integrations/jira.md)
3. [Configure an AI provider](../configuration/ai-providers/gemini.md)
4. [Run your first command](../examples/workflows/)

---

*Need help? Report issues at [github.com/epam/dm.ai/issues](https://github.com/epam/dm.ai/issues)*
