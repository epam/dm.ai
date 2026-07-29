# VERTEX MCP Tools

**Total Tools**: 2

## Quick Reference

```bash
# List all vertex tools
dmtools list | jq '.tools[] | select(.name | startswith("vertex_"))'

# Example usage
dmtools vertex_ai_gemini_chat [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for vertex tools
const result = vertex_ai_gemini_chat(...);
const result = vertex_ai_gemini_chat_with_files(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `vertex_ai_gemini_chat` | Send a text message to Google Vertex AI Gemini (service account auth) | `message` (string, **required**) |
| `vertex_ai_gemini_chat_with_files` | Send message with file attachments to Vertex AI Gemini. Supports images, documents, and other file types. | `message` (string, **required**)<br>`filePaths` (array, **required**) |

## Detailed Parameter Information

### `vertex_ai_gemini_chat`

Send a text message to Google Vertex AI Gemini (service account auth)

**Parameters:**

- **`message`** (string) 🔴 Required
  - Text message

**Example:**
```bash
dmtools vertex_ai_gemini_chat "value"
```

```javascript
// In JavaScript agent
const result = vertex_ai_gemini_chat("message");
```

---

### `vertex_ai_gemini_chat_with_files`

Send message with file attachments to Vertex AI Gemini. Supports images, documents, and other file types.

**Parameters:**

- **`message`** (string) 🔴 Required
  - Text message to send to Vertex AI Gemini
  - Example: `What is in this image? Please analyze the document content.`

- **`filePaths`** (array) 🔴 Required
  - Array of file paths to attach to the message
  - Example: `['/path/to/image.png', '/path/to/document.pdf']`

**Example:**
```bash
dmtools vertex_ai_gemini_chat_with_files "value" "value"
```

```javascript
// In JavaScript agent
const result = vertex_ai_gemini_chat_with_files("message", "filePaths");
```

---

