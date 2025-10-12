# Ollama AI Client Integration

## Overview

This document describes the Ollama AI client integration that has been added to dmtools-core. The implementation follows the same patterns as the existing Dial and Gemini AI clients and provides full support for Ollama's OpenAI-compatible API endpoints.

## Features

- **OpenAI-Compatible API**: Uses Ollama's `/v1/chat/completions` endpoint for maximum compatibility
- **Image Support**: Full support for base64-encoded images using vision models (e.g., llava)
- **Configurable Context Window**: Supports custom `num_ctx` parameter (default: 16384 tokens)
- **Configurable Output Tokens**: Supports custom `num_predict` parameter (default: -1 for unlimited)
- **Multi-turn Conversations**: Full support for conversation history with Message objects
- **DEFAULT_LLM Selector**: New property to explicitly choose which LLM provider to use

## Configuration Properties

Add the following properties to your `config.properties` or environment variables:

```properties
# Ollama Base Path (default: http://localhost:11434)
OLLAMA_BASE_PATH=http://localhost:11434

# Ollama Model Name (required for auto-detection)
OLLAMA_MODEL=llama3

# Context Window Size in tokens (default: 16384)
OLLAMA_NUM_CTX=16384

# Maximum prediction tokens (default: -1 for unlimited)
OLLAMA_NUM_PREDICT=-1

# Default LLM Provider Selection (optional: "ollama", "dial", "gemini")
DEFAULT_LLM=ollama
```

## Default LLM Provider Selection

The new `DEFAULT_LLM` property allows you to explicitly choose which LLM provider to use when multiple are configured:

- **DEFAULT_LLM=ollama**: Forces Ollama client initialization
- **DEFAULT_LLM=dial**: Forces Dial client initialization
- **DEFAULT_LLM=gemini**: Forces Gemini client initialization

If `DEFAULT_LLM` is not set, the system auto-detects available providers in this order:
1. Ollama (if `OLLAMA_MODEL` is set)
2. Dial (if `DIAL_API_KEY` is set)
3. Gemini (if `GEMINI_API_KEY` is set)
4. JSAIClient (if `JSAI_SCRIPT_PATH` is set)
5. Fallback to BasicDialAI

## Usage Examples

### Basic Usage

```java
// Using default constructor (reads from config.properties)
BasicOllamaAI ollama = new BasicOllamaAI();
String response = ollama.chat("Hello, Ollama!");
```

### With Configuration

```java
ApplicationConfiguration config = new PropertyReaderConfiguration();
ConversationObserver observer = new ConversationObserver();
BasicOllamaAI ollama = new BasicOllamaAI(observer, config);

String response = ollama.chat("What is the capital of France?");
```

### With Images (Vision Models)

```java
BasicOllamaAI ollama = new BasicOllamaAI();
File imageFile = new File("path/to/image.png");

// Note: Requires a vision model like llava
String response = ollama.chat("llava", "Describe this image", imageFile);
```

### Multi-turn Conversations

```java
BasicOllamaAI ollama = new BasicOllamaAI();

Message msg1 = new Message("user", "My name is Alice.", null);
Message msg2 = new Message("user", "What is my name?", null);

String response = ollama.chat(msg1, msg2);
// Expected: "Your name is Alice."
```

## API Endpoints

The client uses Ollama's OpenAI-compatible endpoints:

- **POST** `/v1/chat/completions` - Chat completions with message history

### Request Format

```json
{
  "model": "llama3",
  "temperature": 0.1,
  "messages": [
    {
      "role": "user",
      "content": "Hello!"
    }
  ],
  "max_tokens": -1,
  "options": {
    "num_ctx": 16384
  }
}
```

### Image Request Format

```json
{
  "model": "llava",
  "temperature": 0.1,
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "What is in this image?"
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/png;base64,iVBORw0KG..."
          }
        }
      ]
    }
  ],
  "options": {
    "num_ctx": 16384
  }
}
```

## Implementation Details

### Files Created

1. **OllamaConfiguration.java** - Configuration interface for Ollama settings
2. **OllamaAIClient.java** - Main client implementation extending AbstractRestClient
3. **BasicOllamaAI.java** - Convenience wrapper with property-based initialization
4. **OllamaAIClientIntegrationTest.java** - Integration tests

### Files Modified

1. **PropertyReader.java** - Added Ollama and DEFAULT_LLM property methods
2. **AIConfiguration.java** - Added OllamaConfiguration interface
3. **AIModelConfiguration.java** - Added getDefaultLLM() method
4. **PropertyReaderConfiguration.java** - Added Ollama property delegations
5. **InMemoryConfiguration.java** - Added Ollama property implementations
6. **AIComponentsModule.java** - Added DEFAULT_LLM logic and Ollama initialization

### Key Design Decisions

1. **OpenAI-Compatible Endpoint**: Uses `/v1/chat/completions` instead of native `/api/chat` for better compatibility
2. **Temperature Fixed at 0.1**: Following the same pattern as DialAIClient for consistent behavior
3. **No Authentication**: Ollama typically runs locally without authentication
4. **Same Response Model**: Reuses `AIResponse` and `Choice` classes from Dial client (OpenAI format)
5. **Configurable Context**: Both `num_ctx` and `num_predict` are configurable via properties

## Integration Tests

Integration tests are located in:
```
dmtools-core/src/integrationTest/java/com/github/istin/dmtools/ai/ollama/OllamaAIClientIntegrationTest.java
```

### Running Tests

To run the integration tests, ensure:
1. Ollama server is running locally (`ollama serve`)
2. A model is pulled (e.g., `ollama pull llama3`)
3. For image tests, a vision model is required (`ollama pull llava`)
4. `OLLAMA_MODEL` is configured in `config.properties`

```bash
./gradlew integrationTest --tests OllamaAIClientIntegrationTest
```

## Troubleshooting

### Connection Refused

**Problem**: `Connection refused` error when connecting to Ollama.

**Solution**: Ensure Ollama server is running:
```bash
ollama serve
```

### Model Not Found

**Problem**: Error about model not being available.

**Solution**: Pull the required model:
```bash
ollama pull llama3
```

### Image Processing Fails

**Problem**: Image-related requests fail or return errors.

**Solution**: Ensure you're using a vision-capable model:
```bash
ollama pull llava
```

Then use the vision model explicitly:
```java
ollama.chat("llava", "Describe this image", imageFile);
```

### Context Window Too Large

**Problem**: Requests fail with context window errors.

**Solution**: Reduce `OLLAMA_NUM_CTX` in your configuration:
```properties
OLLAMA_NUM_CTX=4096
```

## MCP Tool Integration

The Ollama client includes MCP (Model Context Protocol) tool integration via the `@MCPTool` annotation:

```java
@MCPTool(
    name = "ollama_ai_chat",
    description = "Send a text message to Ollama AI and get response",
    integration = "ai"
)
public String chat(String message)
```

This allows the Ollama client to be used in MCP-enabled workflows and agent systems.

## Future Enhancements

Potential future improvements:
1. Streaming response support
2. Function calling support (if Ollama adds it)
3. Additional parameter configuration (top_p, top_k, etc.)
4. Model listing and management through the API
5. Support for embeddings endpoint

## References

- [Ollama Documentation](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Ollama OpenAI Compatibility](https://github.com/ollama/ollama/blob/main/docs/openai.md)
- DialAIClient implementation (reference implementation)

