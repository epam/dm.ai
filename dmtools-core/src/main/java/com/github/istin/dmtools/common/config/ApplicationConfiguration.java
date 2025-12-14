package com.github.istin.dmtools.common.config;

/**
 * Main configuration interface that combines all specific configuration interfaces.
 * This is the interface that should be used by most components.
 */
public interface ApplicationConfiguration extends 
    Configuration,
    JiraConfiguration,
    AIConfiguration,
    JSAIConfiguration,
    SourceControlConfiguration,
    ConfluenceConfiguration,
    MiscConfiguration,
    GitHubConfiguration,
    GitLabConfiguration,
    BitbucketConfiguration,
    RallyConfiguration,
    FigmaConfiguration,
    MetricsConfiguration,
    OllamaConfiguration,
    AnthropicConfiguration,
    BedrockConfiguration,
    TrackerConfiguration {
    
    /**
     * Sets the configuration file path to use
     * @param resourcePath The path to the configuration file
     */
    void setConfigFile(String resourcePath);
} 