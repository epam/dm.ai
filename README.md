# DMTools
Delivery Management Tools

[![codecov](https://codecov.io/gh/IstiN/dmtools/branch/main/graph/badge.svg)](https://codecov.io/gh/IstiN/dmtools) [![](https://jitpack.io/v/IstiN/dmtools.svg)](https://jitpack.io/#IstiN/dmtools)

---

## Simple Run
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
OPEN_AI_BATH_PATH=https://api.openai.com/v1
OPEN_AI_API_KEY=your_openai_key
OPEN_AI_MODEL=gpt-4
```

#### How to Get OpenAI Token:
1. Go to the [OpenAI Platform](https://platform.openai.com).
2. Navigate to the **API Keys** section.
3. Create a new secret key.
4. Copy the key and add it to the configuration:
   ```properties
   OPEN_AI_API_KEY=[token]
   ```

---

### CurlAI Configuration
```properties
CURL_AI_BATH_PATH=https://your-ai-service.com
CURL_AI_AUTH=Bearer your_token
CURL_AI_MODEL=your-model
CURL_AI_RESPONSE_JSON_PATH=response.text
CURL_AI_URL_TEMPLATE=/v1/chat/completions
CURL_AI_BODY_TEMPLATE={"model":"%s","messages":[{"role":"user","content":"%s"}]}
```

---

### AI Models Configuration
```properties
CODE_AI_MODEL=gpt-4
TEST_AI_MODEL=gpt-4
```

---

### AppCenter Configuration
```properties
APP_CENTER_TOKEN=your_appcenter_token
APP_CENTER_ORGANIZATION=your-org
```

#### How to Get AppCenter Token:
1. Log in to AppCenter.
2. Navigate to **Account Settings > API Tokens**.
3. Generate a new API token.
4. Copy the token and add it to the configuration.

---

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