# AI Agent Development Best Practices

Critical patterns and lessons learned from real-world DMtools agent development.

## 🎯 Core Patterns

### 1. Teammate Job Configuration Pattern

**CRITICAL: Use Teammate job for AI-powered agents, not standalone JavaScript agents.**

When creating BA agents or similar AI-powered workflows:
- Use **"Teammate"** job (not standalone JS agents)
- Structure: Pre-Action → CLI Command (delegate to AI) → Post-Action
- Set `skipAIProcessing: true` when using CLI commands
- Set `outputType: "none"` when post-action handles all output

```json
{
  "name": "Teammate",
  "params": {
    "metadata": {
      "agentId": "story_description",
      "contextId": "ba-workflow"
    },
    "agentParams": {
      "instructions": [
        "./agents/templates/story-template.md",
        "./agents/instructions/common/jira-markdown.md",
        "./agents/instructions/common/preserve_attachments.md"
      ],
      "cliCommands": [
        "./scripts/run-agent.sh \"Read files from input/ and follow instructions. Write to output/description.md and output/response.md\""
      ],
      "skipAIProcessing": true,
      "outputType": "none"
    },
    "preJSAction": "agents/mark-wip-pre.js",
    "postJSAction": "agents/story-description-post.js"
  }
}
```

### 2. Template References Pattern

**Use file references in instructions, not inline text.**

❌ **Wrong**:
```json
{
  "instructions": "Write user stories following this format: As a [role], I want [feature]..."
}
```

✅ **Correct**:
```json
{
  "instructions": [
    "./agents/templates/story-template.md",
    "./agents/templates/jira-markdown.md",
    "./agents/instructions/common/response_output.md"
  ]
}
```

### 3. Output File Naming Convention

**Sequential agents** (one at a time):
- Use fixed names: `output/description.md`, `output/response.md`
- Overwrite on each run
- Post-action reads and processes

**Parallel agents** (multiple concurrent):
- Use unique names: `output/{TICKET_KEY}-description.md`
- Preserve history for all tickets

```javascript
// Sequential agent (standard)
const description = file_read("output/description.md");

// Parallel agent
const ticketKey = params.ticket.key;
const description = file_read(`output/${ticketKey}-description.md`);
```

### 4. Post-Action Responsibilities

**Standard post-action for description-writing agents must:**

1. Check if `output/description.md` exists
2. If exists: Update ticket field with content
3. Read `output/response.md`
4. Post response as comment with `[agentId]` prefix
5. Handle missing files gracefully

```javascript
// Example: See agents/common/agentHelpers.js
const { handleDescriptionUpdate } = require('./common/agentHelpers');

function action(params) {
    try {
        return handleDescriptionUpdate(params, 'Description');
    } catch (error) {
        return createErrorResult(error);
    }
}
```

### 5. OutputType Selection Guide

Choose the right `outputType` based on post-action behavior:

| OutputType | When to Use |
|------------|-------------|
| `"none"` | Post-action handles all output (field updates, comments) - **Recommended for CLI delegation** |
| `"field"` | Teammate should directly update a field (no post-action) |
| `"comment"` | Teammate should directly post comment (no post-action) |
| `"file"` | Writing to file system only, no Jira interaction |

**For CLI-delegated agents with post-actions**: Always use `"none"`

### 6. Configuration File Naming Convention

**For automated workflows** that reference config by name:
- Use human-friendly names: `"BA Description.json"`, `"BA Questions.json"`
- Use Title Case with spaces
- Name matches the agent's display name

**For internal/manual configs**:
- kebab-case is acceptable: `"sync-jira.json"`

### 7. Pre-Action Usage Patterns

**Typical pre-action uses:**
1. Mark ticket as WIP (add label) - prevents double processing
2. Validate prerequisites (ticket state, required fields)
3. Lock resources for concurrent safety

**Usually NOT needed for** (rare cases):
- Context preparation (Teammate handles this)
- Project detection (can be done in prompt)
- Simple validation

```javascript
// Common pre-action: Mark as WIP
function action(params) {
    const ticketKey = params.ticket.key;

    // Check if already being processed
    if (jira_has_label(ticketKey, 'WIP')) {
        return { skip: true, reason: 'Already in progress' };
    }

    // Mark as WIP to prevent concurrent processing
    jira_add_label(ticketKey, 'WIP');

    return { success: true };
}
```

### 8. Conditional File Creation

**Only create description.md if changes are needed, otherwise only response.md**

Add to instructions:
```markdown
**Conditional Output**:
- If the current description already follows the template and requires no changes:
  - Do NOT create output/description.md
  - Create only output/response.md explaining that no changes were needed
  - Mention what is already correct in the description
```

Post-action must check:
```javascript
if (fileExists("output/description.md")) {
    // Update field
    jira_update_field(ticketKey, "Description", description);
} else {
    // Skip field update, only post comment
}
```

### 9. CLI Command Structure

**GitHub Copilot CLI / Claude Code / Cursor CLI Delegation**

Use `cliCommands` with wrapper script:

```json
{
  "cliCommands": [
    "./scripts/run-agent.sh \"Read files from 'input' folder and follow instructions. Write description to output/description.md and summary to output/response.md\""
  ],
  "skipAIProcessing": true,
  "outputType": "none"
}
```

The `run-agent.sh` wrapper:
- Loads `dmtools.env`
- Executes: `npx @github/copilot --allow-all-tools --model claude-sonnet-4.5 -p "prompt"`
- Or: `claude-code -p "prompt"`

### 10. Agent ID in Comments

**Always include metadata.agentId in comments for traceability**

```javascript
const agentId = params.metadata?.agentId || "unknown";
const comment = `[${agentId}]

Generated content here...

_Agent: ${agentId}_
_Timestamp: ${new Date().toISOString()}_`;

jira_post_comment(ticketKey, comment);
```

## 🔒 Critical Preservation Rules

### 11. Attachment Preservation

**CRITICAL: Preserve ALL attachment references from original description**

Add to instructions:
```markdown
**PRESERVE ATTACHMENTS**: CRITICAL - Preserve ALL attachment references from the initial description. Attachment links look like:
- !image.png|thumbnail!
- !image.png!
- [^attachment.pdf]

These MUST be kept in the new description, otherwise they will be lost permanently when the field is updated.
```

Common Jira attachment formats:
- Images: `!filename.png!` or `!filename.png|thumbnail!`
- Files: `[^filename.pdf]` or `[^filename.docx]`
- Embedded: `!screenshot-001.png|width=300!`

**ALWAYS scan the original description for these patterns and include them in the updated version.**

## 🔧 Code Reusability

### 12. Common Helper Functions Pattern

**Create reusable helpers to avoid code duplication (90% less code)**

Structure:
```
agents/
├── common/
│   ├── fileHelpers.js       # File operations
│   ├── jiraHelpers.js       # Jira operations
│   └── agentHelpers.js      # High-level helpers
└── [agent]-post.js          # Uses helpers
```

**File Helpers**:
```javascript
// agents/common/fileHelpers.js
function readDescription() {
    return file_read("output/description.md");
}

function readResponse() {
    return file_read("output/response.md");
}

function fileExists(path) {
    try {
        file_read(path);
        return true;
    } catch (e) {
        return false;
    }
}
```

**Jira Helpers**:
```javascript
// agents/common/jiraHelpers.js
function updateField(ticketKey, fieldName, value) {
    // Handle field name → customfield mapping
    jira_update_field(ticketKey, fieldName, value);
}

function postCommentWithContext(ticketKey, contextId, content, metadata) {
    const agentId = metadata?.agentId || "unknown";
    const comment = `[${contextId}] [${agentId}]\n\n${content}`;
    jira_post_comment(ticketKey, comment);
}

function markAsWIP(ticketKey) {
    if (!hasLabel(ticketKey, 'WIP')) {
        jira_add_label(ticketKey, 'WIP');
    }
}

function unmarkWIP(ticketKey) {
    jira_remove_label(ticketKey, 'WIP');
}

function assignForReview(ticketKey, assignee) {
    let accountId = assignee;

    // Auto-convert email to accountId
    if (assignee.includes('@')) {
        const accountInfo = jira_get_account_by_email(assignee);
        accountId = accountInfo.accountId;
    }

    jira_assign_ticket_to(accountId, ticketKey);
}
```

**Agent Helpers** (High-level):
```javascript
// agents/common/agentHelpers.js
function handleDescriptionUpdate(params, fieldName) {
    const ticketKey = params.ticket.key;
    const contextId = params.metadata?.contextId || "unknown";

    // Update field if description file exists
    if (fileExists("output/description.md")) {
        const description = readDescription();
        updateField(ticketKey, fieldName, description);
    }

    // Always post response as comment
    const response = readResponse();
    postCommentWithContext(ticketKey, contextId, response, params.metadata);

    // Remove WIP label
    unmarkWIP(ticketKey);

    return { success: true };
}
```

**Example post-action (9 lines instead of 80+)**:
```javascript
const { handleDescriptionUpdate, createErrorResult } = require('./common/agentHelpers');

function action(params) {
    try {
        return handleDescriptionUpdate(params, 'Description');
    } catch (error) {
        return createErrorResult(error);
    }
}
```

### 13. Correct MCP Tool for Assigning Tickets

**Use dedicated `jira_assign_ticket_to()` tool, not generic field update**

❌ **WRONG**:
```javascript
jira_update_field(ticketKey, 'Assignee', email);  // Will fail!
```

✅ **CORRECT**:
```javascript
// Option 1: Direct assign with accountId
const accountId = "123457:2a248756-40e8-49d6-8ddc-6852e518451f";
jira_assign_ticket_to(accountId, ticketKey);

// Option 2: Convert email → accountId first
const accountInfo = jira_get_account_by_email("user@example.com");
jira_assign_ticket_to(accountInfo.accountId, ticketKey);

// Option 3: Use helper (recommended)
assignForReview(ticketKey, 'user@example.com');  // Auto-converts
```

**Related MCP Tools**:
- `jira_assign_ticket_to(accountId, key)` - Assign ticket to user
- `jira_get_account_by_email(email)` - Get accountId from email
- `jira_get_my_profile()` - Get current user's accountId

## 📏 Documentation Standards

### 14. Skill Description Length

**Keep skill descriptions SHORT for performance**

❌ **Bad** (too verbose):
```markdown
---
description: Comprehensive documentation and assistance for DMtools which is an
  AI-powered development toolkit with 96+ MCP tools for Jira including ticket
  management and search and comments, and Azure DevOps with work items...
  [continues for 10 more lines]
---
```

✅ **Good** (concise):
```markdown
---
description: DMtools documentation - 96+ MCP tools for Jira, Azure DevOps, Figma,
  Confluence, Teams. Use for integrations, JavaScript agents, test generation.
---
```

**Why it matters**:
- Claude Code loads descriptions on EVERY skill invocation
- Long descriptions slow down processing
- Details belong in reference files, not main description

**Structure**:
- Description: 1-2 sentences (50-100 chars ideal)
- Quick Reference: Bullet points of capabilities
- Detailed Docs: In `references/` folder
- Examples: In separate files

## 📚 Common Instructions Files

Create reusable instruction files in `agents/instructions/common/`:

| File | Purpose |
|------|---------|
| `response_output.md` | Output formatting rules |
| `no_development.md` | Clarify agent doesn't write code |
| `error_handling.md` | How to handle errors |
| `preserve_references.md` | Don't break Jira links |
| `preserve_attachments.md` | How to handle images/attachments |
| `jira_context.md` | Understanding Jira ticket structure |
| `no_ticket_reference.md` | Don't reference ticket keys in content |
| `jira-markdown.md` | Jira markdown formatting guide |

Include relevant ones in `agentParams.instructions` array.

## ✅ Summary of Key Learnings

1. ✅ **Teammate + CLI pattern**: Don't create standalone JS agents for complex AI tasks
2. ✅ **outputType: "none"**: When post-action handles all I/O
3. ✅ **Fixed filenames**: For sequential execution (description.md, response.md)
4. ✅ **Post-action responsibilities**: Field updates + comment posting with agentId
5. ✅ **Include all templates**: Use file references, not inline text
6. ✅ **Jira markdown reference**: Always include for formatting guidance
7. ✅ **Conditional output**: Only create description.md if changes needed
8. ✅ **AgentId in comments**: For agent traceability
9. ✅ **Human-friendly names**: For automation-referenced configs
10. ✅ **Pre-action for WIP**: To prevent concurrent processing
11. ✅ **Preserve attachments**: CRITICAL - preserve all media references
12. ✅ **Common helpers**: Reusable functions in agents/common/ (90% less code)
13. ✅ **Use correct MCP tools**: `jira_assign_ticket_to()` for assignments
14. ✅ **Keep descriptions short**: Performance optimization for skill loading

---

*See also: [Teammate Configs](teammate-configs.md) | [JavaScript Agents](javascript-agents.md) | [CLI Integration](cli-integration.md)*
