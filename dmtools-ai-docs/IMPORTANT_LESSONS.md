# DMtools Skill - Lessons Learned & Improvements

This document tracks mistakes made during agent development and improvements needed in the dmtools skill description.

## Errors Made During BA Agents Development

### 1. Initial Approach Mistake
**Error**: Created standalone JavaScript agents instead of Teammate job configurations
**Should be**: Teammate job with pre/post JS actions and CLI delegation to GitHub Copilot CLI, Cursor CLI, codex cli, claude code, etc...

**Fix for skill description**:
```markdown
CRITICAL: Teammate Job Configuration Pattern

When creating BA agents or similar AI-powered agents:
- Use "Teammate" job (not standalone JS agents)
- Structure: Pre-Action → CLI Command (Copilot) → Post-Action
- Set skipAIProcessing: true when using CLI commands
- Set outputType: "none" when using post-action for output
```

### 2. Template References
**Error**: Use a lot of instructions in agent as text
**Should be**: Use file referencies in instructions

**Fix for skill description**:
```markdown
Template Instructions Pattern

When using story/epic/test templates:
- Add Jira markdown reference: "./agents/templates/jira-markdown.md"
- Example:
  "instructions": [
    "./agents/templates/story-template.md",
    "./agents/templates/jira-markdown.md",
    ...
  ]
```

### 3. Output File Structure
**Error**: Created unique files per ticket: `output/{TICKET_KEY}-description.md`
**Should be**: Fixed filenames when agents run sequentially: `output/description.md`, `output/response.md`

**Fix for skill description**:
```markdown
Output File Naming Convention

Sequential agents (one at a time):
- Use fixed names: output/description.md, output/response.md
- Overwrite on each run

Parallel agents (multiple concurrent):
- Use unique names: output/{TICKET_KEY}-description.md
- Preserve history
```

### 4. Post-Action Responsibilities
**Error**: Post-action only wrote file and posted comment
**Should be**: Post-action must:
1. Read description.md and set to description field - usually all lower case for jira
2. Read response.md and post as comment
3. Include contextId from metadata in comment

**Fix for skill description**:
```markdown
Post-Action Pattern for Description Updates

Standard post-action for description-writing agents:
1. Read output/description.md
2. Update ticket Description field: jira_update_field(ticketKey, "description", content)
3. Read output/response.md
4. Post as comment with contextId prefix
5. Handle missing files gracefully

Example:
const description = file_read("output/description.md");
jira_update_field(ticketKey, "description", description);

const response = file_read("output/response.md");
const contextId = params.metadata?.contextId || "unknown";
jira_post_comment(ticketKey, `[${contextId}]\n\n${response}`);
```

### 5. OutputType Configuration
**Error**: Used `outputType: "comment"` with Teammate job
**Should be**: Use `outputType: "none"` when post-action handles all output

**Fix for skill description**:
```markdown
OutputType Selection

outputType: "none" - When using post-action to handle all output (field updates, comments)
outputType: "field" - When Teammate should directly update a field
outputType: "comment" - When Teammate should directly post comment
outputType: "file" - When writing to file system only

For CLI-delegated agents with post-actions: Use "none"
```

### 6. File Naming for Automation
**Error**: Used kebab-case: `ba-story-description.json`
**Should be**: Human-friendly names for automation: `BA Description.json`

**Fix for skill description**:
```markdown
Configuration File Naming Convention

For automated workflows that reference config by name:
- Use human-friendly names: "BA Description.json", "BA Questions.json"
- Use Title Case with spaces
- Name matches the agent's display name

For internal/manual configs:
- kebab-case is acceptable: "sync-jira.json"
```

### 7. Pre-Action Usage
**Error**: Created pre-action for context prep
**Should be**: Pre-action typically used for WIP label marking to prevent concurrent work. In rare cases it will be used for context extensions

**Fix for skill description**:
```markdown
Pre-Action Common Patterns

Typical pre-action uses:
1. Mark ticket as WIP (add label) - prevents double processing
2. Validate prerequisites (ticket state, required fields)
3. Lock resources for concurrent safety

Usually NOT  (rare) needed for:
- Context preparation (Teammate handles this)
- Project detection (can be done in prompt)
- Simple validation
```

### 8. Conditional File Creation
**Error**: Always created description file even if no changes
**Should be**: Only create description.md if changes are needed, otherwise only response.md

**Fix for skill description**:
```markdown
Conditional Output Pattern

Add to instructions:
"If the current description already follows the template and requires no changes:
- Do NOT create output/description.md
- Create only output/response.md explaining that no changes were needed
- Mention what is already correct in the description"

Post-action must check:
if (file_exists("output/description.md")) {
  // Update field
} else {
  // Skip field update, only post comment
}
```

### 9. CLI Command Structure
**Error**: Not clear how to delegate to GitHub Copilot CLI
**Should be**: Use cliCommands with run-agent.sh

**Fix for skill description**:
```markdown
GitHub Copilot CLI Delegation Pattern

Structure:
"cliCommands": [
  "./scripts/run-agent.sh \"Read files from 'input' folder and follow instructions. Write description to output/description.md and summary to output/response.md\""
]

Set:
- skipAIProcessing: true (CLI handles AI processing)
- outputType: "none" (post-action handles output)

The run-agent.sh wrapper:
- Loads dmtools.env
- Executes: npx @github/copilot --allow-all-tools --model claude-sonnet-4.5 -p "prompt"
```

### 10. Agent ID in Comments
**Error**: Didn't include agentId metadata in Jira comments
**Should be**: Prefix comments with `[agentId]` to identify which agent created it

**Fix for skill description**:
```markdown
Comment Formatting with Agent ID

Always include metadata.agentId in comments for traceability:

const agentId = params.metadata?.agentId || "unknown";
const comment = `[${agentId}]

Generated content here...

_Agent: ${agentId}_
_Timestamp: ${new Date().toISOString()}_`;

jira_post_comment(ticketKey, comment);
```

### 11. Attachment Preservation
**Error**: Not explicitly instructing to preserve attachment links
**Should be**: CRITICAL instruction to preserve ALL attachment references from initial description

**Fix for skill description**:
```markdown
Attachment and Media Preservation

CRITICAL for description-updating agents:

Add to instructions:
"**PRESERVE ATTACHMENTS**: CRITICAL - Preserve ALL attachment references from the initial description. Attachment links look like:
- !image.png|thumbnail!
- !image.png!
- [^attachment.pdf]

These MUST be kept in the new description, otherwise they will be lost permanently when the field is updated."

Common attachment formats in Jira:
- Images: !filename.png! or !filename.png|thumbnail!
- Files: [^filename.pdf] or [^filename.docx]
- Embedded: !screenshot-001.png|width=300!

ALWAYS scan the original description for these patterns and include them in the updated version.
```

## Required Skill Description Updates

### Section to Add: "Teammate Job Patterns"

```markdown
## Teammate Job Patterns

### Description-Writing Agent Pattern

For agents that generate/update ticket descriptions:

**Config Structure**:
- name: "Teammate"
- metadata.agentId: Unique identifier (e.g., "story_description")
- agentParams.instructions: Include ALL templates + common instructions
- cliCommands: Delegate to run-agent.sh
- skipAIProcessing: true
- outputType: "none"
- preJSAction: Optional (for WIP marking)
- postJSAction: Required (read files, update field, post comment)

**Instructions Must Include**:
- All project templates
- Jira markdown reference / or other output service formats
- Common instructions (no_development, error_handling, etc.)
- Conditional file creation rule

**Post-Action Must**:
1. Check if output/description.md exists
2. If exists: Update Description field
3. Read output/response.md
4. Post as comment with [agentId] prefix

**Example Config**: See config/BA Description.json
```

### Section to Add: "File Output Conventions"

```markdown
## File Output Conventions

### Sequential Agent Execution
When agents run one at a time:
- Fixed filenames: output/description.md, output/response.md
- Overwrite on each run
- Post-action reads and processes

### Parallel Agent Execution
When multiple agents may run concurrently:
- Unique filenames: output/{TICKET_KEY}-{type}.md
- Preserve all outputs
- Aggregation step required

### Standard Output Files

**output/description.md**:
- Main content (description, test steps, customeFields values, etc.)
- Only created if changes are needed
- Used by post-action to update Jira field

**output/response.md**:
- Agent's response/summary
- Always created
- Posted as Jira comment by post-action
- Should explain what was done or why nothing was changed
```

### Section to Add: "Common Instructions Files"

```markdown
## Common Instructions Files

Create these reusable instruction files in agents/instructions/common/:

**response_output.md**: Output formatting rules
**no_development.md**: Clarify agent doesn't write code
**error_handling.md**: How to handle errors
**preserve_references.md**: Don't break Jira links
**media_handling.md**: How to handle images/attachments
**jira_context.md**: Understanding Jira ticket structure
**no_ticket_reference.md**: Don't reference ticket keys in content

Include relevant ones in agentParams.instructions array.
```

## Summary of Key Learnings

1. **Teammate + CLI pattern**: Don't create standalone JS agents for complex AI tasks
2. **outputType: "none"**: When post-action handles all I/O
3. **Fixed filenames**: For sequential execution (description.md, response.md)
4. **Post-action responsibilities**: Field updates + comment posting
5. **Include all templates**: Don't assume project, include UX/WAL/WCE
6. **Jira markdown reference**: Always include for formatting guidance
7. **Conditional output**: Only create description.md if changes needed
8. **AgentId in comments**: For agent traceability
9. **Human-friendly names**: For automation-referenced configs
10. **Pre-action for WIP**: To prevent concurrent processing
11. **Preserve attachments**: CRITICAL - preserve all attachment links (!image!, [^file]) from original description
12. **Common helpers**: Create reusable functions in agents/common/ to avoid code duplication (90% less code)

### 12. Code Reusability - Common Helpers
**Error**: Writing same code in every post-action (read files, update fields, post comments)
**Should be**: Create reusable helper functions in `agents/common/`

**Fix for skill description**:
```markdown
Common Helper Functions Pattern

Create reusable helpers to avoid code duplication:

Structure:
agents/
├── common/
│   ├── fileHelpers.js       # File operations
│   ├── jiraHelpers.js       # Jira operations
│   └── agentHelpers.js      # High-level helpers
└── [agent]-post.js          # Uses helpers

File Helpers:
- readDescription() / readResponse() / readQuestions()
- fileExists(path)
- cleanupOutputFiles()

Jira Helpers:
- updateField(ticketKey, fieldName, value)
- postCommentWithContext(ticketKey, contextId, content, metadata)
- addLabel() / removeLabel() / hasLabel()
- markAsWIP() / unmarkWIP() / isWIP()
- assignForReview()

Agent Helpers:
- handleDescriptionUpdate(params, fieldName) - Complete description update flow
- handleQuestionsPost(params) - Complete questions posting flow
- getTicketKey() / getContextId()
- createErrorResult(error)

Example post-action (9 lines instead of 80+):
const { handleDescriptionUpdate, createErrorResult } = require('./common/agentHelpers');

function action(params) {
    try {
        return handleDescriptionUpdate(params, 'Description');
    } catch (error) {
        return createErrorResult(error);
    }
}

Benefits:
- 90% less code
- Consistent behavior
- Easy to test
- Fast development
```

### 13. Wrong Jira MCP Tool for Assigning Tickets
**Error**: Used `jira_update_field(ticketKey, 'Assignee', assignee)` to assign tickets
**Should be**: Use dedicated `jira_assign_ticket_to(accountId, key)` MCP tool

**Why it's wrong**:
- `jira_update_field()` is a generic field updater - not designed for assignee
- Assignee requires **accountId** (not email, not username)
- Need to convert email → accountId first using `jira_get_account_by_email()`

**Fix for skill description**:
```markdown
Assigning Jira Tickets - Use Correct MCP Tool

❌ WRONG - Don't use generic field update:
jira_update_field(ticketKey, 'Assignee', email);  // Will fail!

✅ CORRECT - Use dedicated assign tool:

Option 1: Direct assign with accountId
const accountId = "123457:2a248756-40e8-49d6-8ddc-6852e518451f";
jira_assign_ticket_to(accountId, ticketKey);

Option 2: Convert email → accountId first
const accountInfo = jira_get_account_by_email("user@example.com");
jira_assign_ticket_to(accountInfo.accountId, ticketKey);

Option 3: Reusable helper (recommended)
// In agents/common/jiraHelpers.js
function assignForReview(ticketKey, assignee) {
    let accountId = assignee;

    // Auto-convert email to accountId
    if (assignee.includes('@')) {
        const accountInfo = jira_get_account_by_email(assignee);
        accountId = accountInfo.accountId;
    }

    jira_assign_ticket_to(accountId, ticketKey);
}

// Usage
assignForReview('UX-123', 'user@example.com');  // Works with email
assignForReview('UX-123', '123457:2a...');       // Works with accountId

Related MCP Tools:
- jira_assign_ticket_to(accountId, key) - Assign ticket to user
- jira_get_account_by_email(email) - Get accountId from email
- jira_get_my_profile() - Get current user's accountId

Documentation Reference:
See ~/.claude/skills/dmtools/references/mcp-tools/jira-tools.md
Section: "jira_assign_ticket_to"
```

### 14. Skill Description Length
**Error**: Making skill descriptions too long and verbose
**Should be**: Keep descriptions concise - usually small and focused

**Why it matters**:
- Claude Code loads descriptions on every invocation
- Long descriptions slow down processing
- Users need quick reference, not full documentation
- Details belong in reference files, not main description

**Fix for skill description**:
```markdown
Skill Description Best Practices

Keep the main skill description (SKILL.md frontmatter) SHORT:
- 1-2 sentences maximum
- Focus on WHEN to use the skill
- List key capabilities briefly
- Point to detailed docs in references/

Example - Good (concise):
---
name: dmtools
description: DMtools documentation - 96+ MCP tools for Jira, Azure DevOps, Figma,
  Confluence, Teams. Use for integrations, JavaScript agents, test generation.
---

Example - Bad (too verbose):
---
name: dmtools
description: Comprehensive documentation and assistance for DMtools which is an
  AI-powered development toolkit with 96+ MCP tools for Jira including ticket
  management and search and comments, and Azure DevOps with work items and boards,
  and Figma with design extraction, and Confluence for page management, and Teams
  for messages and chats, and AI providers like Gemini and OpenAI and Claude...
  [continues for 10 more lines]
---

Structure:
- Description: 1-2 sentences (50-100 chars ideal)
- Quick Reference: Bullet points of capabilities
- Detailed Docs: In references/ folder
- Examples: In separate files

Remember:
- Description is loaded on EVERY skill invocation
- Keep it small for performance
- Details go in reference docs
- Users can read full docs when needed
```

## Errors Made During MCP Tool Development

### 15. MCP Tool Parameter Types — boolean/Calendar Cause ClassCastException

**Error**: Annotated methods with `@MCPTool` that have `boolean` or `Calendar` parameters.

**What happened**: CLI execution fails with `java.lang.String cannot be cast to java.lang.Boolean`. The generated `MCPToolExecutor` can only convert String input to `String`, `Integer`, `Long`, and `String[]`. Any other type causes a runtime cast error.

**Should be**: Only use `String/Integer/Long/String[]` in `@MCPTool` methods. For methods that require complex types (boolean, Calendar, enums), create a **String-only wrapper** that calls the real method with defaults:

```java
// ❌ BAD: boolean causes ClassCastException at CLI runtime
@MCPTool(name = "github_list_prs", ...)
public List<IPullRequest> pullRequests(String workspace, String repository,
        String state, boolean checkAllRequests, Calendar startDate) throws IOException { ... }

// ✅ GOOD: wrapper with String-only params, calls real method with safe defaults
@MCPTool(name = "github_list_prs", ...)
public List<IPullRequest> listPullRequests(
        @MCPParam(name = "workspace", ...) String workspace,
        @MCPParam(name = "repository", ...) String repository,
        @MCPParam(name = "state", ...) String state) throws IOException {
    return pullRequests(workspace, repository, state, false, null);
}
```

**Fix for skill description**:
```markdown
MCP Tool Parameter Type Rules

Only use these types in @MCPTool-annotated method signatures:
- String
- Integer / int
- Long / long
- String[]

These types cause ClassCastException and MUST NOT be used:
- boolean / Boolean
- Calendar / Date
- Any enum
- Any complex object

For existing methods with incompatible types: create a separate wrapper method
that takes only String parameters and calls the real method with sensible defaults.
```

---

### 16. DMTOOLS_INTEGRATIONS Env Var Overrides getAvailableIntegrations()

**Error**: Added a new integration key to `McpCliHandler.getAvailableIntegrations()` but forgot to also add it to `DMTOOLS_INTEGRATIONS` in `dmtools.env`.

**What happened**: `./dmtools.sh list github` returned `{"tools": []}` even though the code was correct. The `DMTOOLS_INTEGRATIONS` env variable, when set, **completely replaces** the hardcoded list from `getAvailableIntegrations()`.

**Should be**: When registering a new integration, check `dmtools.env` for `DMTOOLS_INTEGRATIONS` and add the new key there too.

**Fix for skill description**:
```markdown
Adding a New Integration — DMTOOLS_INTEGRATIONS Gotcha

When you add "myintegration" to McpCliHandler.getAvailableIntegrations():
  integrations.addAll(Arrays.asList("jira", ..., "myintegration"));

Also check if dmtools.env has DMTOOLS_INTEGRATIONS set:
  DMTOOLS_INTEGRATIONS=jira,cli,file,...   ← must add "myintegration" here too!

If DMTOOLS_INTEGRATIONS is present in env, it OVERRIDES the getAvailableIntegrations()
hardcoded list. Any integration NOT listed in DMTOOLS_INTEGRATIONS will be invisible
to `dmtools list <integration>` even if registered in code.

The env var is useful for production environments to restrict which integrations
are exposed, but during development it's a common pitfall to forget to update it.

Full checklist when adding a new integration:
1. @MCPTool annotations on methods (String params only!)
2. clients.put("key", ...) in createClientInstances()
3. Add key to getAvailableIntegrations() list
4. Add key to DMTOOLS_INTEGRATIONS in dmtools.env
5. ./buildInstallLocal.sh
6. Verify: dmtools list myintegration  → should show tools
```

---

## Next Steps for Skill Improvement

- [ ] Update skill description with new patterns section
- [ ] Add Teammate job examples with CLI delegation
- [ ] Document post-action standard patterns
- [ ] Add file naming conventions
- [ ] Include common instructions file references
- [ ] Add troubleshooting section for CLI delegation
- [ ] Document metadata.contextId best practices
- [ ] Add common helpers pattern and examples
- [ ] Review and shorten skill descriptions for performance
