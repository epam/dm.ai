package com.github.istin.dmtools.common.config;

import com.github.istin.dmtools.common.utils.PropertyReader;
import java.util.Map;

/**
 * Implementation of ApplicationConfiguration that delegates to PropertyReader.
 * This is the default implementation used by the application.
 */
public class PropertyReaderConfiguration implements ApplicationConfiguration {
    
    private final PropertyReader propertyReader;
    
    /**
     * Creates a new PropertyReaderConfiguration with a new PropertyReader instance
     */
    public PropertyReaderConfiguration() {
        this.propertyReader = new PropertyReader();
    }
    
    /**
     * Creates a new PropertyReaderConfiguration with the provided PropertyReader instance
     * @param propertyReader The PropertyReader instance to use
     */
    public PropertyReaderConfiguration(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }
    
    @Override
    public void setConfigFile(String resourcePath) {
        PropertyReader.setConfigFile(resourcePath);
    }
    
    @Override
    public String getValue(String propertyKey) {
        return propertyReader.getValue(propertyKey);
    }
    
    @Override
    public String getValue(String propertyKey, String defaultValue) {
        return propertyReader.getValue(propertyKey, defaultValue);
    }
    
    @Override
    public Map<String, String> getAllProperties() {
        return propertyReader.getAllProperties();
    }
    
    @Override
    public String getJiraLoginPassToken() {
        return propertyReader.getJiraLoginPassToken();
    }
    
    @Override
    public String getJiraEmail() {
        return propertyReader.getJiraEmail();
    }
    
    @Override
    public String getJiraApiToken() {
        return propertyReader.getJiraApiToken();
    }
    
    @Override
    public String getJiraBasePath() {
        return propertyReader.getJiraBasePath();
    }
    
    @Override
    public String getJiraAuthType() {
        return propertyReader.getJiraAuthType();
    }
    
    @Override
    public boolean isJiraWaitBeforePerform() {
        return propertyReader.isJiraWaitBeforePerform();
    }
    
    @Override
    public boolean isJiraLoggingEnabled() {
        return propertyReader.isJiraLoggingEnabled();
    }
    
    @Override
    public boolean isJiraClearCache() {
        return propertyReader.isJiraClearCache();
    }
    
    @Override
    public String getJiraExtraFieldsProject() {
        return propertyReader.getJiraExtraFieldsProject();
    }
    
    @Override
    public String[] getJiraExtraFields() {
        return propertyReader.getJiraExtraFields();
    }
    
    @Override
    public String getDialBathPath() {
        return propertyReader.getDialBathPath();
    }
    
    @Override
    public String getDialApiKey() {
        return propertyReader.getDialIApiKey();
    }
    
    @Override
    public String getDialModel() {
        return propertyReader.getDialModel();
    }
    
    @Override
    public String getCodeAIModel() {
        return propertyReader.getCodeAIModel();
    }
    
    @Override
    public String getTestAIModel() {
        return propertyReader.getTestAIModel();
    }
    
    @Override
    public int getAiRetryAmount() {
        return propertyReader.getAiRetryAmount();
    }
    
    @Override
    public long getAiRetryDelayStep() {
        return propertyReader.getAiRetryDelayStep();
    }
    
    @Override
    public int getPromptChunkTokenLimit() {
        return propertyReader.getPromptChunkTokenLimit();
    }
    
    @Override
    public long getPromptChunkMaxSingleFileSize() {
        return propertyReader.getPromptChunkMaxSingleFileSize();
    }
    
    @Override
    public long getPromptChunkMaxTotalFilesSize() {
        return propertyReader.getPromptChunkMaxTotalFilesSize();
    }
    
    @Override
    public int getPromptChunkMaxFiles() {
        return propertyReader.getPromptChunkMaxFiles();
    }
    
    @Override
    public String getGeminiApiKey() {
        return propertyReader.getGeminiApiKey();
    }
    
    @Override
    public String getGeminiDefaultModel() {
        return propertyReader.getGeminiDefaultModel();
    }
    
    @Override
    public String getGeminiBasePath() {
        return propertyReader.getGeminiBasePath();
    }
    
    @Override
    public String getJsScriptPath() {
        return propertyReader.getJsScriptPath();
    }
    
    @Override
    public String getJsScriptContent() {
        return propertyReader.getJsScriptContent();
    }
    
    @Override
    public String getJsClientName() {
        return propertyReader.getJsClientName();
    }
    
    @Override
    public String getJsDefaultModel() {
        return propertyReader.getJsDefaultModel();
    }
    
    @Override
    public String getJsBasePath() {
        return propertyReader.getJsBasePath();
    }
    
    @Override
    public String[] getJsSecretsKeys() {
        return propertyReader.getJsSecretsKeys();
    }
    
    @Override
    public String getGithubToken() {
        return propertyReader.getGithubToken();
    }
    
    @Override
    public String getGithubWorkspace() {
        return propertyReader.getGithubWorkspace();
    }
    
    @Override
    public String getGithubRepository() {
        return propertyReader.getGithubRepository();
    }
    
    @Override
    public String getGithubBranch() {
        return propertyReader.getGithubBranch();
    }
    
    @Override
    public String getGithubBasePath() {
        return propertyReader.getGithubBasePath();
    }
    
    @Override
    public String getGitLabToken() {
        return propertyReader.getGitLabToken();
    }
    
    @Override
    public String getGitLabWorkspace() {
        return propertyReader.getGitLabWorkspace();
    }
    
    @Override
    public String getGitLabRepository() {
        return propertyReader.getGitLabRepository();
    }
    
    @Override
    public String getGitLabBranch() {
        return propertyReader.getGitLabBranch();
    }
    
    @Override
    public String getGitLabBasePath() {
        return propertyReader.getGitLabBasePath();
    }
    
    @Override
    public String getBitbucketToken() {
        return propertyReader.getBitbucketToken();
    }
    
    @Override
    public String getBitbucketApiVersion() {
        return propertyReader.getBitbucketApiVersion();
    }
    
    @Override
    public String getBitbucketWorkspace() {
        return propertyReader.getBitbucketWorkspace();
    }
    
    @Override
    public String getBitbucketRepository() {
        return propertyReader.getBitbucketRepository();
    }
    
    @Override
    public String getBitbucketBranch() {
        return propertyReader.getBitbucketBranch();
    }
    
    @Override
    public String getBitbucketBasePath() {
        return propertyReader.getBitbucketBasePath();
    }
    
    @Override
    public boolean isReadPullRequestDiff() {
        return propertyReader.isReadPullRequestDiff();
    }
    
    @Override
    public String getConfluenceBasePath() {
        return propertyReader.getConfluenceBasePath();
    }
    
    @Override
    public String getConfluenceLoginPassToken() {
        return propertyReader.getConfluenceLoginPassToken();
    }
    
    @Override
    public String getConfluenceEmail() {
        return propertyReader.getConfluenceEmail();
    }
    
    @Override
    public String getConfluenceApiToken() {
        return propertyReader.getConfluenceApiToken();
    }
    
    @Override
    public String getConfluenceAuthType() {
        return propertyReader.getConfluenceAuthType();
    }
    
    @Override
    public String getConfluenceGraphQLPath() {
        return propertyReader.getConfluenceGraphQLPath();
    }
    
    @Override
    public String getConfluenceDefaultSpace() {
        return propertyReader.getConfluenceDefaultSpace();
    }
    
    @Override
    public Long getSleepTimeRequest() {
        return propertyReader.getSleepTimeRequest();
    }
    
    @Override
    public String getRallyToken() {
        return propertyReader.getRallyToken();
    }
    
    @Override
    public String getRallyPath() {
        return propertyReader.getRallyPath();
    }
    
    @Override
    public String getFigmaBasePath() {
        return propertyReader.getFigmaBasePath();
    }
    
    @Override
    public String getFigmaApiKey() {
        return propertyReader.getFigmaApiKey();
    }
    
    @Override
    public Integer getDefaultTicketWeightIfNoSPs() {
        return propertyReader.getDefaultTicketWeightIfNoSPs();
    }
    
    @Override
    public Double getLinesOfCodeDivider() {
        return propertyReader.getLinesOfCodeDivider();
    }
    
    @Override
    public Double getTimeSpentOnDivider() {
        return propertyReader.getTimeSpentOnDivider();
    }
    
    @Override
    public Double getTicketFieldsChangedDivider(String fieldName) {
        return propertyReader.getTicketFieldsChangedDivider(fieldName);
    }
    
}