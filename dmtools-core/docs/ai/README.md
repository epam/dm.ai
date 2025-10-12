# AI Integration Documentation

This directory contains documentation for AI/LLM integrations in dmtools-core.

## Available Integrations

### [Ollama Integration](ollama-integration.md)
Local LLM integration using Ollama's OpenAI-compatible API. Supports text generation, vision models, and multi-turn conversations.

**Key Features:**
- OpenAI-compatible endpoints
- Base64 image support for vision models
- Configurable context window and output tokens
- Local deployment (no API keys required)

### Dial AI Integration
Integration with Dial AI service (previously known as EPAM AI Dial). Documentation to be added.

### Gemini AI Integration
Integration with Google's Gemini AI models. Documentation to be added.

### JavaScript AI Client (JSAIClient)
Flexible JavaScript-based AI client for custom integrations. Documentation to be added.

## Configuration

All AI integrations can be configured through `config.properties` or environment variables. 

### Default LLM Selection

You can explicitly choose which LLM provider to use with the `DEFAULT_LLM` property:

```properties
# Choose default LLM provider: "ollama", "dial", or "gemini"
DEFAULT_LLM=ollama
```

If not set, the system auto-detects available providers in this order:
1. Ollama (if `OLLAMA_MODEL` is set)
2. Dial (if `DIAL_API_KEY` is set)
3. Gemini (if `GEMINI_API_KEY` is set)
4. JSAIClient (if `JSAI_SCRIPT_PATH` is set)
5. Fallback to BasicDialAI

## Common Interfaces

All AI clients implement the `AI` interface from `com.github.istin.dmtools.ai.AI`, which provides:

- `String chat(String message)` - Simple text chat
- `String chat(String model, String message)` - Chat with specific model
- `String chat(String model, String message, File imageFile)` - Chat with image
- `String chat(Message... messages)` - Multi-turn conversation
- `String roleName()` - Get the role name for this provider

## MCP Tool Integration

AI clients can be exposed as MCP (Model Context Protocol) tools using the `@MCPTool` annotation, allowing them to be used in agent-based workflows.

## Testing

Integration tests for AI clients are located in:
```
dmtools-core/src/integrationTest/java/com/github/istin/dmtools/ai/
```

Run AI integration tests with:
```bash
./gradlew integrationTest
```

## Adding New AI Integrations

To add a new AI integration:

1. Create a new package under `com.github.istin.dmtools.ai.yourprovider`
2. Implement the `AI` interface or extend `AbstractRestClient`
3. Create a configuration interface extending the appropriate base configuration
4. Add property readers in `PropertyReader.java`
5. Register in `AIComponentsModule.java` provider method
6. Create integration tests
7. Document here!

## Related Documentation

- [MCP Tools](../mcp/) - Model Context Protocol integration
- [Agent System](../agents/) - AI agent framework
- [Prompt Management](../prompts/) - Prompt template system



