# Google Gemini AI Configuration

## 🎯 Overview

Google Gemini provides powerful AI capabilities with a **generous free tier** (15 requests/minute), making it ideal for getting started with DMtools. Gemini 2.0 Flash offers excellent performance for most tasks.

## 🔑 Quick Setup

### Step 1: Get API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Click **"Get API Key"**
3. Select or create a Google Cloud project
4. Click **"Create API key in existing project"**
5. Copy your API key (starts with `AIzaSy...`)

### Step 2: Configure DMtools

Add to your `dmtools.env`:

```bash
# Gemini Configuration
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
GEMINI_MODEL=gemini-2.0-flash-exp  # Optional, defaults to gemini-2.0-flash-exp
```

### Step 3: Test Configuration

```bash
# Test Gemini connection
dmtools gemini_ai_chat "Hello, please confirm you're working"

# Expected response:
# "Hello! Yes, I'm working properly. I'm Gemini, ready to help you with your tasks."
```

## 🎭 Available Models

### Gemini 2.0 Flash (Recommended)
```bash
GEMINI_MODEL=gemini-2.0-flash-exp
# - Fastest response time
# - Best for most DMtools tasks
# - 1M token context window
# - Free tier: 15 RPM, 1M TPM, 1500 RPD
```

### Gemini 1.5 Pro
```bash
GEMINI_MODEL=gemini-1.5-pro-002
# - More capable for complex tasks
# - 2M token context window
# - Free tier: 2 RPM, 32K TPM, 50 RPD
# - Use for large code analysis
```

### Gemini 1.5 Flash
```bash
GEMINI_MODEL=gemini-1.5-flash-002
# - Previous generation flash model
# - Good balance of speed and capability
# - Free tier: 15 RPM, 1M TPM, 1500 RPD
```

## 🔧 Advanced Configuration

### Rate Limit Handling

```bash
# dmtools.env
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
GEMINI_MODEL=gemini-2.0-flash-exp
GEMINI_RETRY_ATTEMPTS=3          # Retry on rate limit
GEMINI_RETRY_DELAY_MS=2000       # Wait 2 seconds between retries
```

### Multiple API Keys (Load Balancing)

```bash
# For higher throughput, rotate between keys
GEMINI_API_KEY_1=YOUR_FIRST_GEMINI_API_KEY
GEMINI_API_KEY_2=YOUR_SECOND_GEMINI_API_KEY
GEMINI_API_KEY_3=YOUR_THIRD_GEMINI_API_KEY
GEMINI_LOAD_BALANCE=true
```

### Context Window Management

```bash
# Control context size for large operations
PROMPT_CHUNK_TOKEN_LIMIT=4000    # Max tokens per chunk
PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB=4  # Max file size
```

## 💡 Usage Examples

### Example 1: Test Case Generation

```bash
# Generate test cases from Jira story
dmtools TestCasesGenerator --inputJql "key = PROJ-123"

# Uses Gemini to analyze story and create test scenarios
```

### Example 2: Code Analysis

```javascript
// agents/js/codeReview.js
function action(params) {
    const code = file_read(params.filePath);

    const analysis = gemini_ai_chat(`
        Analyze this code for:
        1. Security vulnerabilities
        2. Performance issues
        3. Code quality

        Code:
        ${code}
    `);

    return JSON.parse(analysis);
}
```

### Example 3: Documentation Generation

```json
// agents/doc_generator.json
{
  "name": "DocumentationGenerator",
  "params": {
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "aiRole": "You are a technical documentation expert",
    "instructions": "Generate comprehensive API documentation"
  }
}
```

### Example 4: Direct AI Chat

```bash
# Interactive chat
dmtools gemini_ai_chat "Explain the SOLID principles with examples"

# From file
echo "Review this SQL query for optimization" > prompt.txt
dmtools gemini_ai_chat --file prompt.txt

# With context
dmtools gemini_ai_chat "Analyze ticket PROJ-123" --context "$(dmtools jira_get_ticket PROJ-123)"
```

## 🚀 Optimizing for Gemini

### 1. Prompt Engineering

```javascript
// Good prompt structure for Gemini
const prompt = `
Role: You are an expert QA engineer.

Context: ${ticketDescription}

Task: Generate comprehensive test cases.

Requirements:
1. Cover positive and negative scenarios
2. Include edge cases
3. Follow Given-When-Then format

Output Format: JSON array with test cases
`;
```

### 2. Token Optimization

```bash
# Monitor token usage
dmtools --debug gemini_ai_chat "test prompt"
# Shows: Tokens used: input=50, output=200

# Reduce context for faster responses
PROMPT_CHUNK_TOKEN_LIMIT=2000  # Smaller chunks
```


## 🐛 Troubleshooting

### API Key Issues

```bash
# Error: "API key not valid"

# Verify key format
echo $GEMINI_API_KEY
# Should start with AIzaSy...

# Test directly with curl
curl -X POST "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Hello"}]}]}'
```


### Context Length Exceeded

```bash
# Error: "Request too large"

# Reduce context size
PROMPT_CHUNK_TOKEN_LIMIT=2000
PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB=2

# Or use model with larger context
GEMINI_MODEL=gemini-1.5-pro-002  # 2M tokens
```

### Timeout Issues

```bash
# Error: "Read timed out"

# Increase timeout
export GEMINI_TIMEOUT_SECONDS=60

# Or use streaming for long responses
GEMINI_STREAM_RESPONSE=true
```
## 🔒 Security Best Practices

### 1. Secure API Key Storage

```bash
# Never commit API keys
echo "*.env" >> .gitignore

# Use environment variables in CI/CD
export GEMINI_API_KEY=${{ secrets.GEMINI_API_KEY }}
```

### 2. Restrict API Key Usage

In [Google Cloud Console](https://console.cloud.google.com/apis/credentials):
1. Select your API key
2. Click "Edit API key"
3. Under "API restrictions", select "Restrict key"
4. Choose "Gemini API" only
5. Add IP restrictions if needed

### 3. Monitor Usage

```bash
# Check daily usage
curl "https://generativelanguage.googleapis.com/v1/models?key=$GEMINI_API_KEY"

# Set up alerts in Google Cloud Console for unusual activity
```

## 🎯 Best Practices

### 1. Start with Free Tier
- Perfect for development and testing
- 15 requests/minute is sufficient for most workflows
- No credit card required

### 2. Use Flash for Speed
- gemini-2.0-flash-exp is 2-3x faster
- Ideal for iterative development
- Handles structured output well

### 3. Cache Responses
```javascript
// Cache AI responses to avoid repeated calls
const cache = {};
function getCachedResponse(prompt) {
    const key = hash(prompt);
    if (cache[key]) return cache[key];

    const response = gemini_ai_chat(prompt);
    cache[key] = response;
    return response;
}
```

### 4. Batch Operations
```javascript
// Process multiple items in one request
const items = ["item1", "item2", "item3"];
const response = gemini_ai_chat(`
    Process these items:
    ${items.join('\n')}

    Return as JSON array.
`);
```

## 📚 Integration with DMtools Jobs

### Configure for Test Generation
```json
{
  "name": "TestCasesGenerator",
  "params": {
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "inputJql": "sprint in openSprints()"
  }
}
```

## 🔗 Useful Resources

- [Google AI Studio](https://aistudio.google.com/) - API key management and testing
- [Gemini API Docs](https://ai.google.dev/gemini-api/docs) - Official documentation
- [Pricing Calculator](https://ai.google.dev/pricing) - Estimate costs for paid tier
- [Model Comparison](https://ai.google.dev/gemini-api/docs/models/gemini) - Detailed model capabilities


*Next: [OpenAI Configuration](openai.md) | [Azure DevOps Setup](../integrations/ado.md)*