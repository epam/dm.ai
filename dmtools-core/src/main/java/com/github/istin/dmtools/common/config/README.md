# DMTools Configuration System

This package contains a modular configuration system for the DMTools application, allowing for granular configuration of different AI providers and services.

## Overview

The configuration system uses a modular approach with specialized interfaces, making it easy to:

1. Configure different AI providers separately (OpenAI, Gemini, etc.)
2. Extend the system with new AI providers without modifying existing code
3. Test components with different configurations
4. Maintain isolated configurations for different services

## Key Components

### Core Configuration Interfaces

The configuration system is built around specialized interfaces:

- **AIConfiguration**: Composite interface that combines all AI-related configurations
- **OpenAIConfiguration**: Settings specific to OpenAI services
- **GeminiConfiguration**: Settings specific to Gemini services
- **AIModelConfiguration**: Settings for AI model selection
- **AIRetryConfiguration**: Settings for AI operation retry policies
- **PromptConfiguration**: Settings for prompt handling and chunking

### Implementation Classes

- **PropertyReaderConfiguration**: Default implementation that reads from property files
- **InMemoryConfiguration**: Implementation for testing and runtime configuration changes

## Usage Examples

```java
// Create a custom configuration
InMemoryConfiguration config = new InMemoryConfiguration();

// Configure OpenAI settings
config.setProperty("OPEN_AI_API_KEY", "your-api-key");
config.setProperty("OPEN_AI_MODEL", "gpt-4");

// Configure Gemini settings
config.setProperty("GEMINI_API_KEY", "your-gemini-api-key");
config.setProperty("GEMINI_DEFAULT_MODEL", "gemini-pro");

// Configure prompt settings
config.setProperty("PROMPT_CHUNK_TOKEN_LIMIT", "4000");
config.setProperty("PROMPT_CHUNK_MAX_FILES", "5");

// Use the configuration
String openAiModel = config.getOpenAIModel();
String geminiModel = config.getGeminiDefaultModel();
int tokenLimit = config.getPromptChunkTokenLimit();
```

## Benefits of the Modular Approach

1. **Separation of Concerns**: Each AI provider has its own configuration interface
2. **Type Safety**: Specialized interfaces provide type-safe access to settings
3. **Extensibility**: New AI providers can be added by creating new interfaces
4. **Maintainability**: Changes to one provider's configuration don't affect others
5. **Testability**: Components can be tested with different configurations

## Adding a New AI Provider

To add a new AI provider:

1. Create a new interface extending `Configuration` (e.g., `AnthropicConfiguration`)
2. Define the provider-specific methods in the interface
3. Update `AIConfiguration` to extend the new interface
4. Implement the new methods in the configuration implementations

This approach allows for seamless integration of new AI providers without disrupting existing code.

## Configuration Interfaces

1. **Modularity**: Each configuration interface is focused on a specific area of functionality.
2. **Extensibility**: New configuration interfaces can be added without modifying existing ones.
3. **Separation of Concerns**: Each configuration interface is responsible for a specific set of configuration options.
4. **Flexibility**: The configuration system can be used with different configuration sources (e.g., properties files, environment variables, etc.).

## Benefits

- **Granular Configuration**: Tools can be configured separately (e.g., GitHub, Bitbucket, Figma).
- **Type Safety**: Configuration options are strongly typed.
- **Documentation**: Each configuration option is documented with JavaDoc comments.
- **Default Values**: Default values can be provided for configuration options. 