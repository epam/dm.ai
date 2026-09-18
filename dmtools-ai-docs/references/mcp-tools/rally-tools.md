# RALLY MCP Tools

**Total Tools**: 1

## Quick Reference

```bash
# List all rally tools
dmtools list | jq '.tools[] | select(.name | startswith("rally_"))'

# Example usage
dmtools rally_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for rally tools
const result = rally_test(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `rally_test` | Test Rally connectivity by fetching the current user's profile | None |

## Detailed Parameter Information

### `rally_test`

Test Rally connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools rally_test
```

```javascript
// In JavaScript agent
const result = rally_test();
```

---

