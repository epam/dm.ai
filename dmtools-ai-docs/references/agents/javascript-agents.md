# JavaScript Agents Development Guide

**→ See also: [Agent Best Practices](best-practices.md) for reusable helpers, common patterns, and critical preservation rules**

## 🎯 Overview

DMtools JavaScript agents run via **GraalJS** (polyglot JavaScript execution in JVM) and provide direct access to all 67+ MCP tools as native JavaScript functions. Agents are used for preprocessing data, post-processing results, and orchestrating workflows.

## 🏗️ Agent Structure

### Basic Agent Template

```javascript
/**
 * Agent Name: Process Jira Tickets
 * Description: Preprocesses Jira tickets before AI analysis
 * MCP Tools Used: jira_get_ticket, jira_update_labels, jira_post_comment
 */
function action(params) {
    try {
        // 1. Input validation
        if (!params.ticketKey) {
            return {
                success: false,
                error: "Missing required parameter: ticketKey"
            };
        }

        // 2. Use MCP tools directly as functions
        const ticket = jira_get_ticket(params.ticketKey);

        // 3. Process data
        const processed = {
            key: ticket.key,
            summary: ticket.fields.summary,
            priority: ticket.fields.priority.name,
            status: ticket.fields.status.name
        };

        // 4. Update ticket if needed
        if (processed.priority === "High" && !ticket.fields.labels.includes("urgent")) {
            jira_update_labels(ticket.key, ticket.fields.labels.concat(["urgent"]).join(","));
            jira_post_comment(ticket.key, "Marked as urgent due to high priority");
        }

        // 5. Return results
        return {
            success: true,
            data: processed
        };

    } catch (error) {
        return {
            success: false,
            error: error.toString()
        };
    }
}

// Entry point - DMtools calls this
action(params);
```

## 📦 The `params` Object

Every JS agent receives a single `params` argument with the following structure:

```javascript
{
    ticket: {               // Current ticket being processed
        key: "PROJ-123",
        fields: { summary, description, status, labels, priority, ... }
    },
    jobParams: {            // Full serialized job config (all params from JSON)
        inputJql: "...",
        initiator: "user@company.com",
        customParams: { ... },  // ← your custom data (see below)
        // ... all other params fields
    },
    response: "...",        // AI response string (null in preJSAction)
    initiator: "user@company.com",
    inputFolderPath: "/abs/path/input/PROJ-123"  // preCliJSAction only
}
```

### Accessing `customParams`

Pass arbitrary data from the JSON config to JS agents via `customParams`:

**JSON config:**
```json
{
  "name": "Teammate",
  "params": {
    "inputJql": "key = PROJ-123",
    "preJSAction": "agents/js/triggerWorkflow.js",
    "customParams": {
      "workflowId": "rework.yml",
      "targetBranch": "main",
      "flags": { "dryRun": false }
    }
  }
}
```

**JS agent:**
```javascript
function action(params) {
    const custom = params.jobParams.customParams;

    const workflowId   = custom.workflowId;       // "rework.yml"
    const targetBranch = custom.targetBranch;     // "main"
    const dryRun       = custom.flags.dryRun;     // false

    if (!dryRun) {
        github_trigger_workflow(
            "my-org",
            "my-repo",
            workflowId,
            JSON.stringify({ user_request: params.ticket.key }),
            targetBranch
        );
    }

    return { success: true };
}
```

## 🔌 MCP Tools Access

All 67+ MCP tools are available as direct JavaScript functions:

### Jira Tools (35+)

```javascript
// Get ticket
const ticket = jira_get_ticket("PROJ-123");

// Search tickets
const results = jira_search_by_jql("project = PROJ AND status = Open");

// Create ticket
const newTicket = jira_create_ticket_basic(
    "PROJ",           // project
    "Story",          // type
    "New Feature",    // summary
    "Description..."  // description
);

// Update ticket
jira_update_ticket("PROJ-123", {
    summary: "Updated summary",
    priority: { name: "High" }
});

// Add comment
jira_post_comment("PROJ-123", "Processing complete");

// Transition ticket
jira_transition_ticket("PROJ-123", "In Progress");

// Bulk operations
const stories = [
    { summary: "Story 1", description: "..." },
    { summary: "Story 2", description: "..." }
];
jira_bulk_create_stories("PROJ", JSON.stringify(stories));
```

### Azure DevOps Tools (23+)

```javascript
// Get work item
const workItem = ado_get_work_item(12345);

// Update work item
ado_update_work_item(12345, {
    "System.Title": "Updated title",
    "System.State": "Active"
});

// Add comment
ado_add_comment(12345, "Review complete");

// Move to state
ado_move_to_state(12345, "Resolved");

// Assign work item
ado_assign_work_item(12345, "user@company.com");
```

### AI Tools (10+)

```javascript
// Gemini
const response = gemini_ai_chat("Analyze this requirement: ...");

// OpenAI
const analysis = openai_ai_chat("Generate test cases for: ...");

// Claude via Bedrock
const review = bedrock_ai_chat("Review this code: ...");

// Ollama (local)
const summary = ollama_ai_chat("Summarize: ...");

// DIAL Enterprise
const result = dial_ai_chat("Process this request: ...");
```

### File Operations (4)

```javascript
// Read file
const content = file_read("/path/to/file.txt");

// Write file
file_write("/path/to/output.json", JSON.stringify(data, null, 2));

// Validate JSON
const isValid = file_validate_json(jsonString);
if (!isValid.valid) {
    console.error("JSON error:", isValid.error);
}

// List files
const files = file_list("/path/to/directory");
```

### Figma Tools (12+)

```javascript
// Get design layers
const layers = figma_get_layers("file-key");

// Extract icons
const icons = figma_get_icons("file-key");

// Download image
figma_download_image_as_file("file-key", "node-id", "/path/to/save.png");

// Get components
const components = figma_get_library_components("file-key");
```

### Confluence Tools (13+)

```javascript
// Search content
const pages = confluence_search_content_by_text("search term");

// Get page by title
const page = confluence_content_by_title("Page Title", "SPACE");

// Create page
confluence_create_page("SPACE", "New Page", "<p>Content</p>", "parent-id");

// Update page
confluence_update_page("page-id", "Updated Title", "<p>New content</p>");
```

## 📝 Real-World Examples

### Example 1: WIP Label Checker
From `agents/js/checkWipLabel.js`:

```javascript
/**
 * Check if ticket has WIP label
 * Used to prevent processing of work-in-progress items
 */
function action(params) {
    const ticket = params.ticket;
    const labels = ticket.fields.labels || [];

    const hasWipLabel = labels.some(label =>
        label.toLowerCase() === 'wip' ||
        label.toLowerCase() === 'work-in-progress'
    );

    if (hasWipLabel) {
        console.log(`Ticket ${ticket.key} has WIP label, skipping processing`);
        return {
            skip: true,
            reason: "WIP label present"
        };
    }

    return {
        skip: false,
        ticket: ticket
    };
}
```

### Example 2: Xray Precondition Handler
From `agents/js/preprocessXrayTestCases.js`:

```javascript
/**
 * Handle temporary precondition IDs in test cases
 * Creates actual preconditions in Jira and replaces temp IDs
 */
function action(params) {
    const newTestCases = params.newTestCases || [];
    const projectCode = params.ticket.key.split("-")[0];

    for (const testCase of newTestCases) {
        // Check for temporary precondition IDs
        if (testCase.customFields?.preconditions) {
            const tempPreconditions = testCase.customFields.preconditions;
            const realPreconditions = [];

            for (const precondition of tempPreconditions) {
                if (precondition.startsWith("@precondition-")) {
                    // Create actual precondition in Jira
                    const newPrecondition = jira_xray_create_precondition(
                        projectCode,
                        `Precondition for ${testCase.summary}`,
                        precondition.replace("@precondition-", "")
                    );
                    realPreconditions.push(newPrecondition.key);
                } else {
                    // Use existing precondition
                    realPreconditions.push(precondition);
                }
            }

            // Replace with real precondition keys
            testCase.customFields.preconditions = realPreconditions;
        }
    }

    return newTestCases;
}
```

### Example 3: Multi-Tool Workflow
```javascript
/**
 * Complete workflow: Analyze story, generate tests, update tickets
 */
function action(params) {
    const results = {
        analyzed: 0,
        testsCreated: 0,
        errors: []
    };

    try {
        // 1. Search for stories needing test cases
        const stories = jira_search_by_jql(
            "project = PROJ AND type = Story AND 'Test Cases' is EMPTY"
        );

        for (const story of stories) {
            try {
                // 2. Get full story details
                const fullStory = jira_get_ticket(story.key);

                // 3. Use AI to generate test cases
                const testCasesJson = gemini_ai_chat(`
                    Generate comprehensive test cases for this user story:
                    Title: ${fullStory.fields.summary}
                    Description: ${fullStory.fields.description}

                    Return as JSON array with fields: title, steps, expectedResult
                `);

                const testCases = JSON.parse(testCasesJson);

                // 4. Create test cases in Xray
                for (const tc of testCases) {
                    const test = jira_xray_create_test(
                        "PROJ",
                        tc.title,
                        tc.steps
                    );

                    // 5. Link test to story
                    jira_link_issues(test.key, story.key, "Tests");

                    results.testsCreated++;
                }

                // 6. Update story with completion comment
                jira_post_comment(story.key,
                    `✅ Generated ${testCases.length} test cases automatically`
                );

                // 7. Add label
                jira_update_labels(story.key, "tests-generated");

                results.analyzed++;

            } catch (error) {
                results.errors.push({
                    ticket: story.key,
                    error: error.toString()
                });
            }
        }

        return results;

    } catch (error) {
        return {
            success: false,
            error: error.toString()
        };
    }
}
```

## 🎯 Common Patterns

### Pattern 1: Error Handling

```javascript
function action(params) {
    try {
        // Validate inputs
        if (!params.required) {
            throw new Error("Missing required parameter");
        }

        // Main logic with try-catch for each operation
        let result;
        try {
            result = jira_get_ticket(params.ticketKey);
        } catch (e) {
            console.error("Failed to get ticket:", e);
            return { success: false, error: "Ticket not found" };
        }

        return { success: true, data: result };

    } catch (error) {
        // Global error handler
        console.error("Agent error:", error);
        return {
            success: false,
            error: error.toString(),
            stack: error.stack
        };
    }
}
```

### Pattern 2: Batch Processing

```javascript
function action(params) {
    const batchSize = 10;
    const items = params.items || [];
    const results = [];

    // Process in batches to avoid rate limits
    for (let i = 0; i < items.length; i += batchSize) {
        const batch = items.slice(i, i + batchSize);

        for (const item of batch) {
            try {
                const result = processItem(item);
                results.push(result);
            } catch (e) {
                console.error(`Failed to process ${item.id}:`, e);
                results.push({ id: item.id, error: e.toString() });
            }
        }

        // Rate limit protection
        if (i + batchSize < items.length) {
            sleep(1000); // 1 second delay between batches
        }
    }

    return results;
}
```

### Pattern 3: Data Transformation

```javascript
function action(params) {
    const ticket = jira_get_ticket(params.ticketKey);

    // Transform Jira data to custom format
    const transformed = {
        id: ticket.key,
        title: ticket.fields.summary,
        description: ticket.fields.description || "No description",
        metadata: {
            priority: ticket.fields.priority?.name || "Medium",
            status: ticket.fields.status?.name,
            assignee: ticket.fields.assignee?.displayName || "Unassigned",
            created: ticket.fields.created,
            updated: ticket.fields.updated
        },
        customFields: {}
    };

    // Map custom fields
    const fieldMapping = params.fieldMapping || {};
    for (const [jiraField, ourField] of Object.entries(fieldMapping)) {
        if (ticket.fields[jiraField]) {
            transformed.customFields[ourField] = ticket.fields[jiraField];
        }
    }

    return transformed;
}
```

## 🔧 Testing and Debugging Agents

### The Recommended Approach: JSRunner

**JSRunner is the primary way to test JS agents** — it runs the script inside the real GraalJS environment with live MCP tools, real Jira/Confluence connections, and the exact same `params` structure your agent will receive in production.

```bash
# Run agent with no params (useful for scripts that read their own config)
dmtools run agents/js/myScript.js

# Run with inline JSON params — becomes params.jobParams inside JS
dmtools run agents/js/myScript.js '{"ticketKey": "PROJ-123", "dryRun": true}'

# Run with params from a file (useful for complex inputs)
dmtools run agents/js/myScript.js "$(cat agents/js/test-params.json)"
```

**How params are mapped**: the JSON object you pass becomes `params.jobParams` inside the agent:

```javascript
// dmtools run agents/js/myScript.js '{"ticketKey":"PROJ-123","dryRun":true}'

function action(params) {
    const key    = params.jobParams.ticketKey;  // "PROJ-123"
    const dryRun = params.jobParams.dryRun;     // true
    // params.ticket   → null (no ticket context when running via JSRunner)
    // params.response → null (no AI response)
}
```

#### Simulating a postprocess action (with ticket + AI response)

Use the full JSON config form when you need to pass `ticket` or `response`:

```json
// agents/test/test-postprocess.json
{
  "name": "JSRunner",
  "params": {
    "jsPath": "agents/js/myPostAction.js",
    "jobParams": { "dryRun": true },
    "ticket": { "key": "PROJ-123", "fields": { "summary": "My story" } },
    "response": "[{\"summary\":\"Test case 1\",\"priority\":\"High\"}]"
  }
}
```

```bash
dmtools run agents/test/test-postprocess.json
```

#### Dry-run pattern

Add a `dryRun` guard in your agent to test logic without side effects:

```javascript
function action(params) {
    const dryRun = params.jobParams?.dryRun || false;

    const ticket = jira_get_ticket("PROJ-123");
    const comment = "Processing complete ✅";

    if (dryRun) {
        console.log("[DRY RUN] Would post comment:", comment);
        console.log("[DRY RUN] Ticket:", JSON.stringify(ticket, null, 2));
        return { dryRun: true, wouldPost: comment };
    }

    jira_post_comment(ticket.key, comment);
    return { success: true };
}
```

```bash
# Safe to run — makes no writes
dmtools run agents/js/myScript.js '{"dryRun": true}'

# Real run
dmtools run agents/js/myScript.js '{}'
```

---

### Console Output

`console.log/warn/error` output appears directly in the DMtools terminal — no special setup needed:

```javascript
function action(params) {
    console.log("Starting with:", JSON.stringify(params.jobParams));

    const ticket = jira_get_ticket("PROJ-123");
    console.log("Ticket summary:", ticket.fields.summary);

    console.warn("Watch out for this edge case");
    console.error("This is an error message");

    return { done: true };
}
```

### Debug Mode (opt-in verbose output)

Use a `debug` param flag to enable verbose output without cluttering production logs:

```javascript
function action(params) {
    const debug = params.jobParams?.debug || false;

    if (debug) {
        console.log("=== DEBUG: params ===");
        console.log(JSON.stringify(params, null, 2));
    }

    const tickets = jira_search_by_jql("project = PROJ AND status = Open");

    if (debug) {
        console.log("=== DEBUG: found", tickets.length, "tickets ===");
        tickets.forEach(t => console.log(" -", t.key, t.fields.summary));
    }

    // ... rest of logic
}
```

```bash
# Verbose run
dmtools run agents/js/myScript.js '{"debug": true}'

# Normal run
dmtools run agents/js/myScript.js '{}'
```

### Test Locally with Node.js (no DMtools needed)

Useful for unit-testing pure logic (transforms, JSON manipulation) without any API calls:

```bash
cat > test_agent.js << 'EOF'
// Stub MCP functions — return fixture data, no real API calls
function jira_get_ticket(key) {
    return { key: key, fields: { summary: "My Story", labels: [], priority: { name: "High" } } };
}
function jira_post_comment(key, text) {
    console.log("[STUB] jira_post_comment(", key, ",", text, ")");
}
function jira_update_labels(key, labels) {
    console.log("[STUB] jira_update_labels(", key, ",", labels, ")");
}

// Paste your agent function here (or require it if using Node modules)
function action(params) {
    const ticket = jira_get_ticket(params.ticketKey);
    if (ticket.fields.priority.name === "High") {
        jira_update_labels(ticket.key, "urgent");
        jira_post_comment(ticket.key, "Marked urgent");
    }
    return { success: true, key: ticket.key };
}

// Run it
const result = action({ ticketKey: "PROJ-123" });
console.log("Result:", JSON.stringify(result, null, 2));
EOF

node test_agent.js
```

> ⚠️ Node.js runs a different engine than GraalJS. This is only suitable for testing **pure business logic**. For MCP tool calls and real integrations, always use JSRunner (`dmtools run`).

---

### Choosing the Right Testing Approach

| Situation | Recommended approach |
|-----------|----------------------|
| Testing logic with real Jira/Confluence data | `dmtools run agents/js/myScript.js '{"ticketKey":"PROJ-123"}'` |
| Testing a post-action with ticket + AI response | Full JSRunner JSON config with `ticket` and `response` fields |
| Safe end-to-end test without side effects | Add `dryRun: true` param + guard in agent |
| Testing pure JS logic (no API calls) | `node test_agent.js` with stubbed MCP functions |
| Debugging unexpected behaviour | Add `console.log` + run with `debug: true` param |

## 🚀 Performance Tips

### 1. Minimize API Calls

```javascript
// ❌ Bad: Multiple API calls
for (const key of ticketKeys) {
    const ticket = jira_get_ticket(key);
    // Process ticket
}

// ✅ Good: Batch API call
const tickets = jira_get_tickets_by_ids(ticketKeys.join(","));
for (const ticket of tickets) {
    // Process ticket
}
```

### 2. Cache Results

```javascript
const cache = {};

function getCachedTicket(key) {
    if (!cache[key]) {
        cache[key] = jira_get_ticket(key);
    }
    return cache[key];
}
```

### 3. Parallel Processing

```javascript
function action(params) {
    const promises = params.ticketKeys.map(key =>
        Promise.resolve(jira_get_ticket(key))
    );

    Promise.all(promises).then(tickets => {
        // Process all tickets
        return tickets;
    });
}
```

## 📋 Available Global Functions

### Utility Functions

```javascript
// Sleep for milliseconds
sleep(1000);  // Sleep 1 second

// Parse JSON safely
const data = JSON.parse(jsonString);

// Stringify with formatting
const json = JSON.stringify(object, null, 2);

// Date operations
const now = new Date();
const iso = now.toISOString();
```

### Console Functions

```javascript
console.log("Info message");
console.error("Error message");
console.warn("Warning message");
console.debug("Debug message");
console.info("Info message");
```

## 🔗 Integration with DMtools

### Using in Teammate Configuration

```json
{
  "name": "Teammate",
  "params": {
    "preprocessJSAction": "agents/js/validateStory.js",
    "postprocessJSAction": "agents/js/updateTickets.js",
    "aiProvider": "gemini",
    "instructions": "Process user stories"
  }
}
```

### Direct Execution

```bash
# Run JavaScript agent directly
dmtools run-js agents/js/myAgent.js --param ticketKey=PROJ-123

# With JSON parameters
echo '{"ticketKey":"PROJ-123"}' | dmtools run-js agents/js/myAgent.js --stdin
```

## 🆘 Common Issues

### Issue: "Function not found"

```javascript
// Ensure MCP tool name is correct
// ❌ Wrong
const ticket = get_jira_ticket("PROJ-123");

// ✅ Correct
const ticket = jira_get_ticket("PROJ-123");
```

### Issue: "Cannot parse JSON"

```javascript
// Always validate JSON before parsing
try {
    const data = JSON.parse(response);
} catch (e) {
    console.error("Invalid JSON:", response);
    return { error: "Invalid JSON response" };
}
```

### Issue: "Rate limit exceeded"

```javascript
// Add delays between API calls
function processWithDelay(items) {
    for (const item of items) {
        processItem(item);
        sleep(100);  // 100ms delay
    }
}
```

---

## 🔀 Runtime Property Switching with `set_env_variable()`

When a workflow needs to interact with **multiple GitHub organisations** (or other integrations requiring different credentials), you can switch the active credential at runtime using `set_env_variable()`.

### How it works

`set_env_variable(propertyName, envVarName)` tells dmtools to read a different environment variable for the given property. The actual secret value stays inside Java — the JS agent only provides the **name** of the env var, never the value itself.

### Setup (environment / CI secrets)

Define your credentials as separate environment variables in your CI configuration:

```bash
FIRST_GITHUB_TOKEN=...    # token for first organisation
SECOND_GITHUB_TOKEN=...   # token for second organisation
```

### Usage in JS agents

```javascript
function action(params) {
    // Switch to first organisation's token
    set_env_variable("SOURCE_GITHUB_TOKEN", "FIRST_GITHUB_TOKEN");
    github_trigger_workflow({
        workspace: "first-org",
        repository: "some-repo",
        workflowId: "deploy.yml",
        inputs: "{}"
    });

    // Switch to second organisation's token
    set_env_variable("SOURCE_GITHUB_TOKEN", "SECOND_GITHUB_TOKEN");
    github_create_pull_request({
        workspace: "second-org",
        repository: "another-repo",
        sourceBranch: "feature/my-branch",
        targetBranch: "main",
        title: "My PR"
    });

    return { status: "done" };
}
```

### Security guarantees

| What JS sees | What stays in Java |
|---|---|
| `"FIRST_GITHUB_TOKEN"` (string name) | `ghp_xxxx...` (actual token value) |

- The env var **name** is validated: only uppercase letters, digits, and underscores (`[A-Z0-9_]`) are accepted.
- If the env var is not set, dmtools throws an error — the message contains the variable name only, never the token value.
- The token value is never logged or returned to JS context.

### Supported properties

Currently `set_env_variable()` supports switching the GitHub token:

| Property name | Effect |
|---|---|
| `SOURCE_GITHUB_TOKEN` | Recreates the GitHub client with the new token |

---

*Next: [Teammate Configurations](teammate-configs.md) | [MCP Tools Usage Examples](examples/mcp-tools-usage.md)*
