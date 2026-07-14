# DMTools Core Properties Configuration

This document describes the configuration properties used in the DMTools Core module.

## Example Configuration Files

### Development Configuration (`config.properties`)
```properties
# Source Code Integration
SOURCE_GITHUB_TOKEN=${GITHUB_TOKEN}
SOURCE_GITHUB_WORKSPACE=your-org
SOURCE_GITHUB_REPOSITORY=your-repo
SOURCE_GITHUB_BRANCH=main
SOURCE_GITHUB_BASE_PATH=https://api.github.com

# Issue Tracking
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64(${account:JIRA_TOKEN})
JIRA_AUTH_TYPE=Bearer
JIRA_WAIT_BEFORE_PERFORM=true
JIRA_LOGGING_ENABLED=true
JIRA_CLEAR_CACHE=true
JIRA_EXTRA_FIELDS=summary,description,status
JIRA_EXTRA_FIELDS_PROJECT=customfield_10001,customfield_10002

# AI Integration
OPEN_AI_BATH_PATH=https://api.openai.com/v1
OPEN_AI_API_KEY=${OPENAI_KEY}
OPEN_AI_MODEL=gpt-4
CODE_AI_MODEL=gpt-4
TEST_AI_MODEL=gpt-4

# System Configuration
SLEEP_TIME_REQUEST=300
LINES_OF_CODE_DIVIDER=1.0
TIME_SPENT_ON_DIVIDER=1.0
TICKET_FIELDS_CHANGED_DIVIDER=1.0
IS_READ_PULL_REQUEST_DIFF=true

# AI Retry Configuration
AI_RETRY_AMOUNT=3
AI_RETRY_DELAY_STEP=20000
```

### Production Configuration (`config.prod.properties`)
```properties
# Source Code Integration
SOURCE_GITHUB_TOKEN=${GITHUB_TOKEN}
SOURCE_GITHUB_WORKSPACE=${GITHUB_WORKSPACE}
SOURCE_GITHUB_REPOSITORY=${GITHUB_REPOSITORY}
SOURCE_GITHUB_BRANCH=${GITHUB_BRANCH}
SOURCE_GITHUB_BASE_PATH=${GITHUB_BASE_PATH}

# Issue Tracking
JIRA_BASE_PATH=${JIRA_BASE_PATH}
JIRA_LOGIN_PASS_TOKEN=${JIRA_TOKEN}
JIRA_AUTH_TYPE=Bearer
JIRA_WAIT_BEFORE_PERFORM=true
JIRA_LOGGING_ENABLED=false
JIRA_CLEAR_CACHE=false
JIRA_EXTRA_FIELDS=${JIRA_EXTRA_FIELDS}
JIRA_EXTRA_FIELDS_PROJECT=${JIRA_EXTRA_FIELDS_PROJECT}

# AI Integration
OPEN_AI_BATH_PATH=${OPENAI_BASE_PATH}
OPEN_AI_API_KEY=${OPENAI_KEY}
OPEN_AI_MODEL=${OPENAI_MODEL}
CODE_AI_MODEL=${CODE_AI_MODEL}
TEST_AI_MODEL=${TEST_AI_MODEL}

# System Configuration
SLEEP_TIME_REQUEST=${SLEEP_TIME_REQUEST}
LINES_OF_CODE_DIVIDER=${LINES_OF_CODE_DIVIDER}
TIME_SPENT_ON_DIVIDER=${TIME_SPENT_ON_DIVIDER}
TICKET_FIELDS_CHANGED_DIVIDER=${TICKET_FIELDS_CHANGED_DIVIDER}
IS_READ_PULL_REQUEST_DIFF=${IS_READ_PULL_REQUEST_DIFF}

# AI Retry Configuration
AI_RETRY_AMOUNT=${AI_RETRY_AMOUNT}
AI_RETRY_DELAY_STEP=${AI_RETRY_DELAY_STEP}
```

## Required Environment Variables

For production deployment, set these environment variables:

```bash
# GitHub Integration
export GITHUB_TOKEN="your-github-token"
export GITHUB_WORKSPACE="your-org"
export GITHUB_REPOSITORY="your-repo"
export GITHUB_BRANCH="main"
export GITHUB_BASE_PATH="https://api.github.com"

# JIRA Integration
export JIRA_BASE_PATH="https://your-company.atlassian.net"
export JIRA_TOKEN="your-jira-token"
export JIRA_EXTRA_FIELDS="summary,description,status"
export JIRA_EXTRA_FIELDS_PROJECT="customfield_10001,customfield_10002"

# OpenAI Integration
export OPENAI_BASE_PATH="https://api.openai.com/v1"
export OPENAI_KEY="your-openai-key"
export OPENAI_MODEL="gpt-4"
export CODE_AI_MODEL="gpt-4"
export TEST_AI_MODEL="gpt-4"

# System Configuration
export SLEEP_TIME_REQUEST="300"
export LINES_OF_CODE_DIVIDER="1.0"
export TIME_SPENT_ON_DIVIDER="1.0"
export TICKET_FIELDS_CHANGED_DIVIDER="1.0"
export IS_READ_PULL_REQUEST_DIFF="true"

# AI Retry Configuration
export AI_RETRY_AMOUNT="3"
export AI_RETRY_DELAY_STEP="20000"
```

## Source Code Integration Properties

### GitHub Integration
- `SOURCE_GITHUB_TOKEN`: GitHub Personal Access Token
  - How to get: Go to GitHub Settings -> Developer Settings -> Personal Access Tokens -> Generate New Token
  - Required scopes: `repo`, `read:user`, `read:org`
- `SOURCE_GITHUB_WORKSPACE`: GitHub organization or username
- `SOURCE_GITHUB_REPOSITORY`: Repository name
- `SOURCE_GITHUB_BRANCH`: Default branch name
- `SOURCE_GITHUB_BASE_PATH`: GitHub API base URL (default: https://api.github.com)

### Bitbucket Integration
- `BITBUCKET_TOKEN`: Bitbucket App Password
  - How to get: Bitbucket Settings -> App Passwords -> Create App Password
  - Required permissions: Repository Read, Pull Request Read
- `BITBUCKET_WORKSPACE`: Bitbucket workspace name
- `BITBUCKET_REPOSITORY`: Repository name
- `BITBUCKET_BRANCH`: Default branch name
- `BITBUCKET_BASE_PATH`: Bitbucket API base URL
- `BITBUCKET_API_VERSION`: API version (e.g., "2.0")

### GitLab Integration
- `GITLAB_TOKEN`: GitLab Personal Access Token
  - How to get: GitLab -> Settings -> Access Tokens -> Create Personal Access Token
  - Required scopes: `read_api`, `read_repository`
- `GITLAB_WORKSPACE`: GitLab group or username
- `GITLAB_REPOSITORY`: Repository name
- `GITLAB_BRANCH`: Default branch name
- `GITLAB_BASE_PATH`: GitLab API base URL

## Issue Tracking Integration

### JIRA Integration
- `JIRA_LOGIN_PASS_TOKEN`: JIRA API Token
  - How to get: Atlassian Account Settings -> Security -> Create and manage API tokens
- `JIRA_BASE_PATH`: JIRA instance URL
- `JIRA_AUTH_TYPE`: Authentication type (e.g., "Bearer")
- `JIRA_WAIT_BEFORE_PERFORM`: Boolean flag for request throttling
- `JIRA_LOGGING_ENABLED`: Boolean flag for request logging
- `JIRA_CLEAR_CACHE`: Boolean flag for cache management
- `JIRA_EXTRA_FIELDS`: Comma-separated list of additional fields to fetch
- `JIRA_EXTRA_FIELDS_PROJECT`: Project-specific extra fields (defaults to `TS` when unset)
- `JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES`: Rename `customfield_XXXX` keys to their human-readable names (defaults to `true` when unset)

### Confluence Integration
- `CONFLUENCE_LOGIN_PASS_TOKEN`: Confluence API Token
  - How to get: Atlassian Account Settings -> Security -> Create and manage API tokens
- `CONFLUENCE_BASE_PATH`: Confluence instance URL
- `CONFLUENCE_GRAPHQL_PATH`: GraphQL API endpoint
- `CONFLUENCE_DEFAULT_SPACE`: Default space key

## AI Integration

### OpenAI Integration
- `OPEN_AI_API_KEY`: OpenAI API Key
  - How to get: OpenAI Platform -> API Keys -> Create New Secret Key
- `OPEN_AI_BATH_PATH`: OpenAI API base URL
- `OPEN_AI_MODEL`: Default model name
- `CODE_AI_MODEL`: Model for code-related tasks
- `TEST_AI_MODEL`: Model for test generation

### Gemini Integration
- `GEMINI_API_KEY`: Google AI Studio API Key
  - How to get: Google AI Studio -> Get API Key
- `GEMINI_DEFAULT_MODEL`: Default model name
- `GEMINI_BASE_PATH`: API base URL

## System Configuration

### Performance Settings
- `SLEEP_TIME_REQUEST`: Delay between requests in milliseconds (default: 300)
- `LINES_OF_CODE_DIVIDER`: Divisor for code metrics calculations
- `TIME_SPENT_ON_DIVIDER`: Divisor for time calculations
- `TICKET_FIELDS_CHANGED_DIVIDER`: Divisor for ticket field changes
- `IS_READ_PULL_REQUEST_DIFF`: Boolean flag for PR diff reading

### AI Retry Configuration
- `AI_RETRY_AMOUNT`: Number of retry attempts (default: 3)
- `AI_RETRY_DELAY_STEP`: Delay between retries in milliseconds (default: 20000)

## Firebase Integration
- `FIREBASE_PROJECT_ID`: Firebase project identifier
  - How to get: Firebase Console -> Project Settings -> General -> Project ID
- `FIREBASE_SERVICE_ACCOUNT_JSON_AUTH`: Firebase service account credentials
  - How to get: Firebase Console -> Project Settings -> Service Accounts -> Generate New Private Key

## Figma Integration
- `FIGMA_TOKEN`: Figma Personal Access Token
  - How to get: Figma Account Settings -> Personal Access Tokens -> Generate New Token
- `FIGMA_BASE_PATH`: Figma API base URL

## Usage Notes

1. All sensitive properties (tokens, keys, credentials) should be stored in environment variables or a secure configuration management system.
2. Never commit property files containing sensitive information to version control.
3. Use `application-local.properties` for local development overrides.
4. The system will first check for properties in the configuration file, then fall back to environment variables. 