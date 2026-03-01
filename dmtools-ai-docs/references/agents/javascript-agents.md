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

## 🔧 Debugging Agents

### Console Output

```javascript
function action(params) {
    // Use console for debugging
    console.log("Starting agent with params:", JSON.stringify(params));

    const result = someOperation();
    console.log("Operation result:", result);

    // Console output appears in DMtools logs
    console.error("This is an error message");
    console.warn("This is a warning");

    return result;
}
```

### Debug Mode

```javascript
function action(params) {
    const debug = params.debug || false;

    if (debug) {
        console.log("=== DEBUG MODE ===");
        console.log("Params:", JSON.stringify(params, null, 2));
        console.log("Environment:", {
            cwd: process.cwd(),
            timestamp: new Date().toISOString()
        });
    }

    // Your logic here

    if (debug) {
        console.log("=== EXECUTION COMPLETE ===");
    }
}
```

### Test Locally

```bash
# Create test file
cat > test_agent.js << 'EOF'
// Mock MCP functions for local testing
function jira_get_ticket(key) {
    return { key: key, fields: { summary: "Test" } };
}

// Your agent code
function action(params) {
    const ticket = jira_get_ticket(params.ticketKey);
    return { success: true, ticket: ticket };
}

// Test execution
const result = action({ ticketKey: "PROJ-123" });
console.log(result);
EOF

# Run with Node.js
node test_agent.js
```

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
  "name": "StoryProcessor",
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

*Next: [Teammate Configurations](teammate-configs.md) | [MCP Tools Usage Examples](examples/mcp-tools-usage.md)*