# BITBUCKET MCP Tools

**Total Tools**: 1

## Quick Reference

```bash
# List all bitbucket tools
dmtools list | jq '.tools[] | select(.name | startswith("bitbucket_"))'

# Example usage
dmtools bitbucket_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for bitbucket tools
const result = bitbucket_test(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `bitbucket_test` | Test Bitbucket connectivity by fetching the current user's profile | None |

## Detailed Parameter Information

### `bitbucket_test`

Test Bitbucket connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools bitbucket_test
```

```javascript
// In JavaScript agent
const result = bitbucket_test();
```

---

