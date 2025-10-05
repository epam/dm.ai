# DMTools Configuration System

This package contains the core components of the DMTools application, including a flexible configuration system for various tools and services.

## Overview

The configuration system allows for granular configuration of different tools, making it easy to:

1. Configure tools separately (GitHub, Bitbucket, Figma, etc.)
2. Use different configurations for different components
3. Test components with different configurations
4. Maintain isolated configurations for different users or environments

## Key Components

### Configuration Interfaces

The configuration system consists of:

- `ApplicationConfiguration`: Main interface that combines all specific configuration interfaces
- `Configuration`: Base interface for all configuration types
- Source Control Configurations:
  - `SourceControlConfiguration`: Interface for source control settings
  - `GitHubConfiguration`: Interface for GitHub-specific settings
  - `GitLabConfiguration`: Interface for GitLab-specific settings
  - `BitbucketConfiguration`: Interface for Bitbucket-specific settings
- Tool-specific Configurations:
  - `FigmaConfiguration`: Interface for Figma settings
  - `FirebaseConfiguration`: Interface for Firebase settings
  - `RallyConfiguration`: Interface for Rally settings
  - `MetricsConfiguration`: Interface for metrics settings

## Usage Examples

### Using Configuration

```java
// Create a custom configuration
ApplicationConfiguration customConfig = new InMemoryConfiguration();
customConfig.setProperty("GITHUB_TOKEN", "my-token");
customConfig.setProperty("OPEN_AI_MODEL", "gpt-4");

// Access configuration values
String githubToken = customConfig.getGithubToken();
String openAiModel = customConfig.getValue("OPEN_AI_MODEL");
```

## Benefits of the Configuration System

1. **Modularity**: Each tool or service has its own configuration interface
2. **Type Safety**: Configuration interfaces provide type-safe access to settings
3. **Separation of Concerns**: Configuration is separated from business logic
4. **Flexibility**: Different components can use different configurations
5. **Extensibility**: New configuration interfaces can be added as needed 