package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class PropertyReader {

	private static final Logger logger = LogManager.getLogger(PropertyReader.class);

	private static String PATH_TO_CONFIG_FILE = "/config.properties";

	private static final int DEFAULT_AI_RETRY_AMOUNT = 3;
	private static final long DEFAULT_AI_RETRY_DELAY_STEP = 20000L;
	public static final String DEFAULT_JSAI_CLIENT_NAME = "JSAIClientFromProperties";
	private static final String DEFAULT_GEMINI_MODEL = "gemini-2.0-flash";
	private static final String DEFAULT_GEMINI_BASE_PATH = "https://generativelanguage.googleapis.com/v1beta/models";

	public static void setConfigFile(String resourcePath) {
		PATH_TO_CONFIG_FILE = resourcePath;
	}


	static Properties prop;
	private static Properties envFileProps;
	private static Path projectRoot = null;

	/**
	 * Finds the project root by walking up the directory tree looking for Gradle project markers.
	 * Caches the result to avoid repeated filesystem operations.
	 * @return Path to the project root, or user.dir as fallback
	 */
	private static Path findProjectRoot() {
		if (projectRoot != null) {
			return projectRoot;
		}
		
		Path current = Paths.get(System.getProperty("user.dir"));
		while (current != null) {
			if (Files.exists(current.resolve("settings.gradle")) || 
				Files.exists(current.resolve("settings.gradle.kts"))) {
				projectRoot = current;
				logger.debug("Detected project root at: {}", projectRoot);
				return projectRoot;
			}
			current = current.getParent();
		}
		
		// Fallback to user.dir if no Gradle project markers found
		projectRoot = Paths.get(System.getProperty("user.dir"));
		logger.debug("No Gradle project markers found, using user.dir as project root: {}", projectRoot);
		return projectRoot;
	}

	/**
	 * Loads properties from dmtools.env file.
	 * First tries project root, then falls back to current working directory.
	 * This is called lazily on first access.
	 */
	private static void loadEnvFileProperties() {
		if (envFileProps != null) {
			return; // Already loaded
		}
		
		envFileProps = new Properties();
		
		// Priority 1: Try to load from project root directory
		Path root = findProjectRoot();
		Path envFileAtRoot = root.resolve("dmtools.env");
		if (Files.exists(envFileAtRoot) && Files.isRegularFile(envFileAtRoot)) {
			try {
				Map<String, String> envVars = CommandLineUtils.loadEnvironmentFromFile(envFileAtRoot.toString());
				if (!envVars.isEmpty()) {
					envVars.forEach(envFileProps::setProperty);
					logger.debug("Loaded {} properties from dmtools.env at project root: {}", envVars.size(), envFileAtRoot);
					return;
				}
			} catch (Exception e) {
				logger.warn("Failed to load dmtools.env from {}: {}", envFileAtRoot, e.getMessage());
			}
		}
		
		// Priority 2: Fall back to current working directory (if different from project root)
		String currentDir = System.getProperty("user.dir");
		if (currentDir != null && !currentDir.equals(root.toString())) {
			Path envFile = Paths.get(currentDir, "dmtools.env");
			if (Files.exists(envFile) && Files.isRegularFile(envFile)) {
				try {
					Map<String, String> envVars = CommandLineUtils.loadEnvironmentFromFile(envFile.toString());
					if (!envVars.isEmpty()) {
						envVars.forEach(envFileProps::setProperty);
						logger.debug("Loaded {} properties from dmtools.env at working directory: {}", envVars.size(), envFile);
						return;
					}
				} catch (Exception e) {
					logger.warn("Failed to load dmtools.env from {}: {}", envFile, e.getMessage());
				}
			}
		}
		
		logger.debug("dmtools.env not found in project root ({}) or working directory ({})", root, currentDir);
	}

	public String getValue(String propertyKey) {
		if (prop == null) {
			prop = new Properties();
			InputStream input = null;
			boolean loadedFromFile = false;
			
			// Priority 1: Try to load from root project's src/main/resources/config.properties
			try {
				Path root = findProjectRoot();
				Path configFile = root.resolve("src/main/resources/config.properties");
				if (Files.exists(configFile) && Files.isRegularFile(configFile)) {
					input = Files.newInputStream(configFile);
					prop.load(input);
					loadedFromFile = true;
					logger.debug("Loaded config.properties from root project: {}", configFile);
				}
			} catch (IOException e) {
				logger.debug("Could not load config.properties from root project: {}", e.getMessage());
			} finally {
				if (input != null) {
					try {
						input.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					input = null;
				}
			}
			
			// Priority 2: Fall back to dmtools-core embedded resource (classpath)
			if (!loadedFromFile) {
				try {
					input = getClass().getResourceAsStream(PATH_TO_CONFIG_FILE);
					if (input != null) {
						prop.load(input);
						logger.debug("Loaded config.properties from classpath resource: {}", PATH_TO_CONFIG_FILE);
					}
				} catch (IOException e) {
					logger.warn("Could not load config.properties from classpath: {}", e.getMessage());
				} finally {
					try {
						if (input != null) {
							input.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Priority 1: Resource file (config.properties)
		String property = prop.getProperty(propertyKey);
		if (property != null && !property.isEmpty()) {
			return property;
		}
		
		// Priority 2: dmtools.env file
		loadEnvFileProperties();
		property = envFileProps.getProperty(propertyKey);
		if (property != null && !property.isEmpty()) {
			return property;
		}
		
		// Priority 3: System environment variables
		return System.getenv(propertyKey);
	}

	public String getValue(String propertyKey, String defaultValue) {
		String value = getValue(propertyKey);
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		return value;
	}

	public String getJiraLoginPassToken() {
		// Priority 1: Use separate email and API token if both are available
		String email = getJiraEmail();
		String apiToken = getJiraApiToken();
		
		if (email != null && !email.trim().isEmpty() && 
			apiToken != null && !apiToken.trim().isEmpty()) {
			// Automatically combine email:token and base64 encode
			String credentials = email.trim() + ":" + apiToken.trim();
			return Base64.getEncoder().encodeToString(credentials.getBytes());
		}
		
		// Priority 2: Fall back to existing base64-encoded token
		return getValue("JIRA_LOGIN_PASS_TOKEN");
	}
	
	public String getJiraEmail() {
		return getValue("JIRA_EMAIL");
	}
	
	public String getJiraApiToken() {
		return getValue("JIRA_API_TOKEN");
	}

	public String getJiraBasePath() {
		return getValue("JIRA_BASE_PATH");
	}

	public String getJiraAuthType() {
		return getValue("JIRA_AUTH_TYPE");
	}

	public boolean isJiraWaitBeforePerform() {
		String value = getValue("JIRA_WAIT_BEFORE_PERFORM");
		if (value == null) {
			return false;
		}
		return Boolean.parseBoolean(value);
	}

	public boolean isJiraLoggingEnabled() {
		String value = getValue("JIRA_LOGGING_ENABLED");
		if (value == null) {
			return false;
		}
		return Boolean.parseBoolean(value);
	}

	public boolean isJiraClearCache() {
		String value = getValue("JIRA_CLEAR_CACHE");
		if (value == null) {
			return false;
		}
		return Boolean.parseBoolean(value);
	}

	public String getJiraExtraFieldsProject() {
		return getValue("JIRA_EXTRA_FIELDS_PROJECT");
	}

    public int getJiraMaxSearchResults() {
        String jiraMaxSearchResults = getValue("JIRA_MAX_SEARCH_RESULTS");
        if (jiraMaxSearchResults == null || jiraMaxSearchResults.trim().isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(jiraMaxSearchResults.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid JIRA_MAX_SEARCH_RESULTS value: " + jiraMaxSearchResults + ", using default -1");
            return -1;
        }
    }

	public String[] getJiraExtraFields() {
		String value = getValue("JIRA_EXTRA_FIELDS");
		if (value == null) {
			return null;
		}
		return value.split(",");
	}

	public String getXrayClientId() {
		return getValue("XRAY_CLIENT_ID");
	}

	public String getXrayClientSecret() {
		return getValue("XRAY_CLIENT_SECRET");
	}

	public String getXrayBasePath() {
		return getValue("XRAY_BASE_PATH");
	}

	public Long getSleepTimeRequest() {
		String value = getValue("SLEEP_TIME_REQUEST");
		if (value == null || value.isEmpty()) {
			return 300L;
		}
		return Long.parseLong(value);
	}


	public String getRallyToken() {
		return getValue("RALLY_TOKEN");
	}

	public String getRallyPath() {
		return getValue("RALLY_PATH");
	}

	// Azure DevOps (ADO) configuration methods
	public String getAdoOrganization() {
		return getValue("ADO_ORGANIZATION");
	}

	public String getAdoProject() {
		return getValue("ADO_PROJECT");
	}

	public String getAdoPatToken() {
		return getValue("ADO_PAT_TOKEN");
	}

	public String getAdoBasePath() {
		String value = getValue("ADO_BASE_PATH");
		if (value == null || value.isEmpty()) {
			return "https://dev.azure.com";
		}
		return value;
	}

	public String getBitbucketToken() {
		return getValue("BITBUCKET_TOKEN");
	}

	public String getBitbucketApiVersion() {
		return getValue("BITBUCKET_API_VERSION");
	}

	public String getBitbucketWorkspace() {
		return getValue("BITBUCKET_WORKSPACE");
	}

	public String getBitbucketRepository() {
		return getValue("BITBUCKET_REPOSITORY");
	}

	public String getBitbucketBranch() {
		return getValue("BITBUCKET_BRANCH");
	}

	public String getBitbucketBasePath() {
		return getValue("BITBUCKET_BASE_PATH");
	}

	public String getGithubToken() {
		return getValue("SOURCE_GITHUB_TOKEN");
	}

	public String getGithubWorkspace() {
		return getValue("SOURCE_GITHUB_WORKSPACE");
	}

	public String getGithubRepository() {
		return getValue("SOURCE_GITHUB_REPOSITORY");
	}

	public String getGithubBranch() {
		return getValue("SOURCE_GITHUB_BRANCH");
	}

	public String getGithubBasePath() {
		return getValue("SOURCE_GITHUB_BASE_PATH");
	}

	public String getGitLabToken() {
		return getValue("GITLAB_TOKEN");
	}

	public String getGitLabWorkspace() {
		return getValue("GITLAB_WORKSPACE");
	}

	public String getGitLabRepository() {
		return getValue("GITLAB_REPOSITORY");
	}

	public String getGitLabBranch() {
		return getValue("GITLAB_BRANCH");
	}

	public String getGitLabBasePath() {
		return getValue("GITLAB_BASE_PATH");
	}

	public String getConfluenceBasePath() {
		return getValue("CONFLUENCE_BASE_PATH");
	}

	public String getConfluenceLoginPassToken() {
		// Priority 1: Use separate email and API token if both are available
		String email = getConfluenceEmail();
		String apiToken = getConfluenceApiToken();
		String authType = getConfluenceAuthType();
		
		if (email != null && !email.trim().isEmpty() && 
			apiToken != null && !apiToken.trim().isEmpty()) {
			
			// For Bearer auth, use token directly without email combination
			if ("Bearer".equalsIgnoreCase(authType)) {
				return apiToken.trim();
			}
			
			// For Basic auth (default), combine email:token and base64 encode
			String credentials = email.trim() + ":" + apiToken.trim();
			return Base64.getEncoder().encodeToString(credentials.getBytes());
		}
		
		// Priority 2: Fall back to existing base64-encoded token
		return getValue("CONFLUENCE_LOGIN_PASS_TOKEN");
	}
	
	public String getConfluenceEmail() {
		return getValue("CONFLUENCE_EMAIL");
	}
	
	public String getConfluenceApiToken() {
		return getValue("CONFLUENCE_API_TOKEN");
	}
	
	public String getConfluenceAuthType() {
		String authType = getValue("CONFLUENCE_AUTH_TYPE");
		// Default to Basic if not specified
		return authType != null ? authType : "Basic";
	}

	public String getConfluenceGraphQLPath() {
		return getValue("CONFLUENCE_GRAPHQL_PATH");
	}

	public String getConfluenceDefaultSpace() {
		return getValue("CONFLUENCE_DEFAULT_SPACE");
	}

	public String getDialBathPath() {
		return getValue("DIAL_BATH_PATH");
	}

	public String getDialIApiKey() {
		return getValue("DIAL_API_KEY");
	}

	public String getDialModel() {
		return getValue("DIAL_MODEL");
	}

	public String getCodeAIModel() {
		return getValue("CODE_AI_MODEL");
	}

	public String getTestAIModel() {
		return getValue("TEST_AI_MODEL");
	}

	public String getFigmaBasePath() {
		return getValue("FIGMA_BASE_PATH");
	}

	public String getFigmaApiKey() {
		return getValue("FIGMA_TOKEN");
	}

	public Integer getDefaultTicketWeightIfNoSPs() {
		String value = getValue("DEFAULT_TICKET_WEIGHT_IF_NO_SP");
		if (value == null || value.isEmpty()) {
			return -1;
		}
		return Integer.parseInt(value);
	}

	public Double getLinesOfCodeDivider() {
		String value = getValue("LINES_OF_CODE_DIVIDER");
		if (value == null || value.isEmpty()) {
			return 1d;
		}
		return Double.parseDouble(value);
	}

	public Double getTimeSpentOnDivider() {
		String value = getValue("TIME_SPENT_ON_DIVIDER");
		if (value == null || value.isEmpty()) {
			return 1d;
		}
		return Double.parseDouble(value);
	}

	public Double getTicketFieldsChangedDivider(String fieldName) {
		String value = getValue("TICKET_FIELDS_CHANGED_DIVIDER_"+ fieldName.toUpperCase());
		if (value == null) {
			String defaultValue = getValue("TICKET_FIELDS_CHANGED_DIVIDER_DEFAULT");
			if (defaultValue != null && !defaultValue.isEmpty()) {
				return Double.parseDouble(defaultValue);
			}
			return 1d;
		}
		return Double.parseDouble(value);
	}


	public boolean isReadPullRequestDiff() {
		String value = getValue("IS_READ_PULL_REQUEST_DIFF");
		if (value == null) {
			return true;
		}
		return Boolean.parseBoolean(value);
	}

	/**
	 * Gets the number of retry attempts for AI operations
	 * @return number of retries, default is 3
	 */
	public int getAiRetryAmount() {
		String value = getValue("AI_RETRY_AMOUNT");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_AI_RETRY_AMOUNT;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return DEFAULT_AI_RETRY_AMOUNT;
		}
	}

	/**
	 * Gets the delay step in milliseconds between retry attempts
	 * The actual delay will be multiplied by the attempt number
	 * @return delay step in milliseconds, default is 20000 (20 seconds)
	 */
	public long getAiRetryDelayStep() {
		String value = getValue("AI_RETRY_DELAY_STEP");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_AI_RETRY_DELAY_STEP;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return DEFAULT_AI_RETRY_DELAY_STEP;
		}
	}

	// Default values (matching the ones from PromptPreparation)
	private static final int DEFAULT_PROMPT_CHUNK_TOKEN_LIMIT = 4000;
	private static final long DEFAULT_PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE = 4 * 1024 * 1024; // 5MB
	private static final long DEFAULT_PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE = 4 * 1024 * 1024; // 5MB
	private static final int DEFAULT_PROMPT_CHUNK_MAX_FILES = 10;

	/**
	 * Gets the maximum token limit for AI model
	 * @return token limit, default is 4000
	 */
	public int getPromptChunkTokenLimit() {
		String value = getValue("PROMPT_CHUNK_TOKEN_LIMIT");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_PROMPT_CHUNK_TOKEN_LIMIT;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return DEFAULT_PROMPT_CHUNK_TOKEN_LIMIT;
		}
	}

	/**
	 * Gets the maximum size in bytes for a single file
	 * @return maximum file size in bytes, default is 5MB
	 */
	public long getPromptChunkMaxSingleFileSize() {
		String value = getValue("PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE;
		}
		try {
			// Convert MB to bytes
			return Long.parseLong(value) * 1024 * 1024;
		} catch (NumberFormatException e) {
			return DEFAULT_PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE;
		}
	}

	/**
	 * Gets the maximum total size in bytes for all files in a chunk
	 * @return maximum total files size in bytes, default is 5MB
	 */
	public long getPromptChunkMaxTotalFilesSize() {
		String value = getValue("PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE_MB");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE;
		}
		try {
			// Convert MB to bytes
			return Long.parseLong(value) * 1024 * 1024;
		} catch (NumberFormatException e) {
			return DEFAULT_PROMPT_CHUNK_MAX_TOTAL_FILES_SIZE;
		}
	}

	/**
	 * Gets the maximum number of files allowed per chunk
	 * @return maximum files per chunk, default is 10
	 */
	public int getPromptChunkMaxFiles() {
		String value = getValue("PROMPT_CHUNK_MAX_FILES");
		if (value == null || value.trim().isEmpty()) {
			return DEFAULT_PROMPT_CHUNK_MAX_FILES;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return DEFAULT_PROMPT_CHUNK_MAX_FILES;
		}
	}

	// JSAIClient specific properties
	public String getJsScriptPath() {
		return getValue("JSAI_SCRIPT_PATH");
	}

	public String getJsScriptContent() {
		return getValue("JSAI_SCRIPT_CONTENT");
	}

	public String getJsClientName() {
		String value = getValue("JSAI_CLIENT_NAME");
		return (value == null || value.trim().isEmpty()) ? DEFAULT_JSAI_CLIENT_NAME : value;
	}

	public String getJsDefaultModel() {
		return getValue("JSAI_DEFAULT_MODEL");
	}

	public String getJsBasePath() {
		return getValue("JSAI_BASE_PATH");
	}

	public String[] getJsSecretsKeys() {
		String value = getValue("JSAI_SECRETS_KEYS");
		if (value == null || value.trim().isEmpty()) {
			return null; 
		}
		return value.split(",");
	}

	public Map<String, String> getAllProperties() {
		Properties props = loadProperties();
		return props.stringPropertyNames().stream()
				.collect(Collectors.toMap(name -> name, props::getProperty));
	}

	private Properties loadProperties() {
		Properties props = new Properties();
		InputStream input = null;
		try {
			input = getClass().getResourceAsStream(PATH_TO_CONFIG_FILE);
			if (input != null) {
				props.load(input);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Property file not found");
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return props;
	}

	public static final String DIAL_MODEL = "DIAL_MODEL";
	public static final String CODE_AI_MODEL = "CODE_AI_MODEL";
	public static final String TEST_AI_MODEL = "TEST_AI_MODEL";
	public static final String GEMINI_API_KEY = "GEMINI_API_KEY";
	public static final String GEMINI_DEFAULT_MODEL_KEY = "GEMINI_DEFAULT_MODEL";
	public static final String GEMINI_BASE_PATH_KEY = "GEMINI_BASE_PATH";
	public static final String OLLAMA_BASE_PATH = "OLLAMA_BASE_PATH";
	public static final String OLLAMA_MODEL = "OLLAMA_MODEL";
	public static final String OLLAMA_NUM_CTX = "OLLAMA_NUM_CTX";
	public static final String OLLAMA_NUM_PREDICT = "OLLAMA_NUM_PREDICT";
	public static final String ANTHROPIC_BASE_PATH = "ANTHROPIC_BASE_PATH";
	public static final String ANTHROPIC_MODEL = "ANTHROPIC_MODEL";
	public static final String ANTHROPIC_MAX_TOKENS = "ANTHROPIC_MAX_TOKENS";
	public static final String BEDROCK_BASE_PATH = "BEDROCK_BASE_PATH";
	public static final String BEDROCK_REGION = "BEDROCK_REGION";
	public static final String BEDROCK_MODEL_ID = "BEDROCK_MODEL_ID";
	public static final String BEDROCK_BEARER_TOKEN = "BEDROCK_BEARER_TOKEN";
	public static final String AWS_BEARER_TOKEN_BEDROCK = "AWS_BEARER_TOKEN_BEDROCK";
	public static final String BEDROCK_ACCESS_KEY_ID = "BEDROCK_ACCESS_KEY_ID";
	public static final String BEDROCK_SECRET_ACCESS_KEY = "BEDROCK_SECRET_ACCESS_KEY";
	public static final String BEDROCK_SESSION_TOKEN = "BEDROCK_SESSION_TOKEN";
	public static final String BEDROCK_MAX_TOKENS = "BEDROCK_MAX_TOKENS";
	public static final String BEDROCK_TEMPERATURE = "BEDROCK_TEMPERATURE";
	public static final String DEFAULT_LLM = "DEFAULT_LLM";
	public static final String DEFAULT_TRACKER = "DEFAULT_TRACKER";
	public static final String IMAGE_MAX_DIMENSION = "IMAGE_MAX_DIMENSION";
	public static final String IMAGE_JPEG_QUALITY = "IMAGE_JPEG_QUALITY";

	public String getGeminiApiKey() {
		return getValue(GEMINI_API_KEY);
	}

	public String getGeminiDefaultModel() {
		return getValue(GEMINI_DEFAULT_MODEL_KEY, DEFAULT_GEMINI_MODEL);
	}

	public String getGeminiBasePath() {
		return getValue(GEMINI_BASE_PATH_KEY, DEFAULT_GEMINI_BASE_PATH);
	}

	// Microsoft Teams configuration
	public String getTeamsClientId() {
		return getValue("TEAMS_CLIENT_ID");
	}

	public String getTeamsTenantId() {
		return getValue("TEAMS_TENANT_ID", "common");
	}

	public String getTeamsScopes() {
		return getValue("TEAMS_SCOPES", 
			"User.Read Chat.Read ChatMessage.Read Mail.Read " +
			"Team.ReadBasic.All Channel.ReadBasic.All " +
			"openid profile email offline_access");
	}

	public String getTeamsAuthMethod() {
		return getValue("TEAMS_AUTH_METHOD", "device");
	}

	public int getTeamsAuthPort() {
		String value = getValue("TEAMS_AUTH_PORT");
		if (value == null || value.trim().isEmpty()) {
			return 8080;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 8080;
		}
	}

	public String getTeamsTokenCachePath() {
		return getValue("TEAMS_TOKEN_CACHE_PATH", "./teams.token");
	}

	public String getTeamsRefreshToken() {
		return getValue("TEAMS_REFRESH_TOKEN");
	}

	// Microsoft SharePoint configuration (reuses Teams auth, adds Files.Read)
	public String getSharePointScopes() {
		// SharePoint needs Files.Read.All or Files.ReadWrite.All in addition to Teams scopes
		return getValue("SHAREPOINT_SCOPES", 
			"User.Read Chat.Read ChatMessage.Read Mail.Read " +
			"Team.ReadBasic.All Channel.ReadBasic.All " +
			"Files.Read.All Sites.Read.All " +
			"openid profile email offline_access");
	}

	// Ollama configuration
	public String getOllamaBasePath() {
		return getValue(OLLAMA_BASE_PATH, "http://localhost:11434");
	}

	public String getOllamaModel() {
		return getValue(OLLAMA_MODEL);
	}

	public int getOllamaNumCtx() {
		String value = getValue(OLLAMA_NUM_CTX);
		if (value == null || value.trim().isEmpty()) {
			return 16384;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			logger.warn("Invalid OLLAMA_NUM_CTX value: {}, using default 16384", value);
			return 16384;
		}
	}

	public int getOllamaNumPredict() {
		String value = getValue(OLLAMA_NUM_PREDICT);
		if (value == null || value.trim().isEmpty()) {
			return -1;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			logger.warn("Invalid OLLAMA_NUM_PREDICT value: {}, using default -1", value);
			return -1;
		}
	}

	public String getOllamaCustomHeaderNames() {
		return getValue("OLLAMA_CUSTOM_HEADER_NAMES");
	}

	public String getOllamaCustomHeaderValues() {
		return getValue("OLLAMA_CUSTOM_HEADER_VALUES");
	}

	// Anthropic configuration
	public String getAnthropicBasePath() {
		return getValue(ANTHROPIC_BASE_PATH, "https://api.anthropic.com/v1/messages");
	}

	public String getAnthropicModel() {
		return getValue(ANTHROPIC_MODEL);
	}

	public int getAnthropicMaxTokens() {
		String value = getValue(ANTHROPIC_MAX_TOKENS);
		if (value == null || value.trim().isEmpty()) {
			return 4096;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			logger.warn("Invalid ANTHROPIC_MAX_TOKENS value: {}, using default 4096", value);
			return 4096;
		}
	}

	public String getAnthropicCustomHeaderNames() {
		return getValue("ANTHROPIC_CUSTOM_HEADER_NAMES");
	}

	public String getAnthropicCustomHeaderValues() {
		return getValue("ANTHROPIC_CUSTOM_HEADER_VALUES");
	}

	// Bedrock configuration
	public String getBedrockBasePath() {
		String basePath = getValue(BEDROCK_BASE_PATH);
		String region = getBedrockRegion();
		if (basePath != null && !basePath.trim().isEmpty()) {
			return basePath;
		}
		if (region != null && !region.trim().isEmpty()) {
			return "https://bedrock-runtime." + region + ".amazonaws.com";
		}
		return null;
	}

	public String getBedrockRegion() {
		return getValue(BEDROCK_REGION);
	}

	public String getBedrockModelId() {
		return getValue(BEDROCK_MODEL_ID);
	}

	public String getBedrockBearerToken() {
		// Check AWS_BEARER_TOKEN_BEDROCK first (alternative name), then fall back to BEDROCK_BEARER_TOKEN
		String token = getValue(AWS_BEARER_TOKEN_BEDROCK);
		if (token != null && !token.trim().isEmpty() && !token.startsWith("$")) {
			return token;
		}
		return getValue(BEDROCK_BEARER_TOKEN);
	}

	public String getBedrockAccessKeyId() {
		return getValue(BEDROCK_ACCESS_KEY_ID);
	}

	public String getBedrockSecretAccessKey() {
		return getValue(BEDROCK_SECRET_ACCESS_KEY);
	}

	public String getBedrockSessionToken() {
		return getValue(BEDROCK_SESSION_TOKEN);
	}

	public int getBedrockMaxTokens() {
		String value = getValue(BEDROCK_MAX_TOKENS);
		if (value == null || value.trim().isEmpty()) {
			return 4096;
		}
		try {
			int maxTokens = Integer.parseInt(value.trim());
			// Validate minimum value
			if (maxTokens < 1) {
				logger.warn("Invalid BEDROCK_MAX_TOKENS value: {}, using default 4096", value);
				return 4096;
			}
			return maxTokens;
		} catch (NumberFormatException e) {
			logger.warn("Invalid BEDROCK_MAX_TOKENS value: {}, using default 4096", value);
			return 4096;
		}
	}

	public double getBedrockTemperature() {
		String value = getValue(BEDROCK_TEMPERATURE);
		if (value == null || value.trim().isEmpty()) {
			return 1.0;
		}
		try {
			double temperature = Double.parseDouble(value.trim());
			// Validate range 0.0-1.0
			if (temperature < 0.0) {
				logger.warn("Invalid BEDROCK_TEMPERATURE value: {}, using default 1.0", value);
				return 1.0;
			}
			if (temperature > 1.0) {
				logger.warn("BEDROCK_TEMPERATURE value {} exceeds maximum 1.0, using 1.0", value);
				return 1.0;
			}
			return temperature;
		} catch (NumberFormatException e) {
			logger.warn("Invalid BEDROCK_TEMPERATURE value: {}, using default 1.0", value);
			return 1.0;
		}
	}

	public String getDefaultLLM() {
		return getValue(DEFAULT_LLM);
	}

	public String getDefaultTracker() {
		return getValue(DEFAULT_TRACKER);
	}

	public int getImageMaxDimension() {
		return Integer.parseInt(getValue(IMAGE_MAX_DIMENSION, "8000"));
	}

	public float getImageJpegQuality() {
		return Float.parseFloat(getValue(IMAGE_JPEG_QUALITY, "0.9"));
	}

}