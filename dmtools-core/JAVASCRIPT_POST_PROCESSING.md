# JavaScript Post-Processing for AI Jobs

## Overview

DMTools Expert and Teammate jobs support custom JavaScript post-processing through the `postJSAction` parameter. This allows users to write custom logic that executes after AI processing, with access to 61+ MCP (Model Context Protocol) tools for Jira, Confluence, AI, and Figma operations.

## How It Works

### 1. JavaScript Function Structure

All JavaScript post-processing functions must follow this pattern:

```javascript
function action(params) {
    try {
        // Your custom logic here
        // Access params.ticket, params.jobParams, params.response, etc.
        // Use MCP tools: jira_*, confluence_*, gemini_ai_chat, dial_ai_chat, figma_*
        
        return {
            success: true,
            message: "Processing completed successfully",
            data: {} // Optional result data
        };
    } catch (error) {
        console.error("Processing failed:", error);
        return {
            success: false,
            error: error.toString(),
            message: "Processing failed"
        };
    }
}
```

### 2. Available Parameters

The `params` object contains:

- **`params.ticket`** - The ticket being processed with properties:
  - `key` - Ticket key (e.g., "PROJ-123")
  - `title` - Ticket title/summary
  - `description` - Ticket description
  - `status` - Current status
  - `labels` - Array of labels
  - `priority` - Priority level
  - `issueType` - Issue type
  - `creator` - Ticket creator info
  - `fields` - Raw Jira fields JSON

- **`params.jobParams`** - Job execution parameters
  - `customParams` - Custom parameters passed in JSON config (e.g. `params.jobParams.customParams.myValue`)
- **`params.response`** - AI agent response
- **`params.initiator`** - Job initiator information
- Additional context via job-specific parameters

### 3. JavaScript Source Options

The `postJSAction` parameter accepts three formats:

#### Inline JavaScript
```javascript
function action(params) {
    jira_update_field({
        key: params.ticket.key,
        field: "labels",
        value: ["processed", "automated"]
    });
    return { success: true };
}
```

#### Resource Path
```
agentFunctions/customProcessor.js
```
Loads JavaScript from `src/main/resources/agentFunctions/customProcessor.js`

#### GitHub URL
```
https://github.com/user/repo/blob/main/scripts/processor.js
```
Loads JavaScript directly from GitHub repository

### 4. Available MCP Tools

#### Jira Operations (40+ tools)
```javascript
// Get ticket information
const ticket = jira_get_ticket({key: "PROJ-123"});

// Update fields
jira_update_field({
    key: "PROJ-123",
    field: "labels", 
    value: ["processed", "automated"]
});

// Post comments
jira_post_comment({
    ticketKey: "PROJ-123",
    comment: "Processed by JavaScript automation"
});

// Create tickets
const newTicket = jira_create_ticket_basic({
    project: "PROJ",
    issueType: "Task",
    summary: "Automated Task",
    description: "Created by JavaScript post-processing"
});

// Link tickets
jira_link_issues({
    sourceKey: "PROJ-123",
    anotherKey: newTicket.key,
    relationship: "implements"
});

// Search tickets
const results = jira_search_by_jql({
    searchQueryJQL: "project = PROJ AND status = Open",
    fields: ["key", "summary", "status"]
});
```

#### AI Operations (2 tools)
```javascript
// Gemini AI
const analysis = gemini_ai_chat({
    message: "Analyze this ticket: " + params.ticket.title
});

// Dial AI
const technicalAnalysis = dial_ai_chat({
    message: "Provide technical analysis for: " + params.ticket.description
});
```

#### Confluence Operations (16 tools)
```javascript
// Create pages
const page = confluence_create_page({
    title: "Analysis Results for " + params.ticket.key,
    body: "<h1>Analysis</h1><p>" + analysis + "</p>",
    space: "DOCS",
    parentId: "123456"
});

// Search content
const searchResults = confluence_search_content_by_text({
    query: params.ticket.key,
    limit: 10
});

// Update pages
confluence_update_page({
    contentId: page.id,
    title: "Updated Analysis",
    body: "<h1>Updated</h1><p>New content</p>",
    space: "DOCS",
    parentId: "123456"
});
```

#### Figma Operations (6 tools)
```javascript
// Get file structure
const fileStructure = figma_get_file_structure({
    href: "https://www.figma.com/file/abc123/Design"
});

// Extract visual elements
const icons = figma_get_icons({
    href: "https://www.figma.com/file/abc123/Design"
});

// Download assets
const svgContent = figma_get_svg_content({
    href: "https://www.figma.com/file/abc123/Design",
    nodeId: "1:2"
});
```

## Examples

### Basic Ticket Processing
See: [`examples/js/simple-test.js`](examples/js/simple-test.js)

```javascript
function action(params) {
    // Add processing label
    jira_update_field({
        key: params.ticket.key,
        field: "labels",
        value: ["js-processed", "automated"]
    });
    
    // Post confirmation comment
    jira_post_comment({
        ticketKey: params.ticket.key,
        comment: "✅ Ticket processed successfully by JavaScript automation"
    });
    
    return {
        success: true,
        message: "Basic processing completed"
    };
}
```

### AI-Enhanced Analysis
See: [`examples/js/ai-mcp-examples.js`](examples/js/ai-mcp-examples.js)

```javascript
function action(params) {
    // Get AI analysis
    const analysis = gemini_ai_chat({
        message: `Analyze this ticket and suggest next steps: ${params.ticket.title}`
    });
    
    // Create analysis page in Confluence
    const page = confluence_create_page({
        title: `AI Analysis: ${params.ticket.key}`,
        body: `<h1>AI Analysis</h1><p>${analysis}</p>`,
        space: "ANALYSIS",
        parentId: "123456"
    });
    
    // Link page in ticket comment
    jira_post_comment({
        ticketKey: params.ticket.key,
        comment: `AI analysis completed: [View Analysis|${page.webUILink}]`
    });
    
    return {
        success: true,
        message: "AI analysis completed",
        data: { analysisPageId: page.id }
    };
}
```

### Comprehensive Workflow
See: [`examples/js/jira-mcp-examples.js`](examples/js/jira-mcp-examples.js)

```javascript
function action(params) {
    const results = {};
    
    // 1. Update ticket labels
    results.labelUpdate = jira_update_field({
        key: params.ticket.key,
        field: "labels",
        value: ["workflow-processed", "automated"]
    });
    
    // 2. Create related subtask
    results.subtask = jira_create_ticket_basic({
        project: params.ticket.key.split('-')[0],
        issueType: "Sub-task",
        summary: `Follow-up for ${params.ticket.key}`,
        description: "Automated follow-up task created by JavaScript processing"
    });
    
    // 3. Link the subtask
    if (results.subtask && results.subtask.key) {
        results.linkResult = jira_link_issues({
            sourceKey: params.ticket.key,
            anotherKey: results.subtask.key,
            relationship: "implements"
        });
    }
    
    // 4. Get AI recommendations
    const aiRecommendations = dial_ai_chat({
        message: `What are the next steps for completing: ${params.ticket.title}?`
    });
    
    // 5. Post summary comment
    jira_post_comment({
        ticketKey: params.ticket.key,
        comment: `🤖 Automated processing completed:
        
• Created subtask: ${results.subtask ? results.subtask.key : 'Failed'}
• AI Recommendations: ${aiRecommendations}
• Processing timestamp: ${new Date().toISOString()}`
    });
    
    return {
        success: true,
        message: "Comprehensive workflow completed",
        data: results
    };
}
```

## Error Handling

JavaScript execution is sandboxed and errors are caught to prevent job failures:

```javascript
function action(params) {
    try {
        // Risky operation
        const result = jira_update_field({
            key: params.ticket.key,
            field: "nonexistent_field",
            value: "test"
        });
        
        return { success: true, result: result };
    } catch (error) {
        // Error is logged but job continues
        console.error("Field update failed:", error);
        
        // Post error as comment instead
        jira_post_comment({
            ticketKey: params.ticket.key,
            comment: `⚠️ Automated processing encountered an issue: ${error.message}`
        });
        
        return {
            success: false,
            error: error.toString(),
            message: "Processing failed but error handled gracefully"
        };
    }
}
```

## Configuration

### Expert Job
In the Expert job configuration, add the `postJSAction` parameter with your JavaScript code, resource path, or GitHub URL.

### Teammate Job  
In the Teammate job configuration, add the `postJSAction` parameter with your JavaScript code, resource path, or GitHub URL.

### Parameter Limits
- Maximum JavaScript code length: 500,000 characters
- Execution timeout: Reasonable limits to prevent infinite loops
- Memory usage: Monitored to prevent excessive consumption

## Security

- JavaScript execution runs in a secure GraalVM sandbox
- No access to file system or network (except through MCP tools)
- No access to sensitive system information
- Limited to provided MCP tool operations only

## Best Practices

1. **Always include error handling** - Use try-catch blocks
2. **Return meaningful results** - Include success status and messages
3. **Use console.log for debugging** - Output will appear in job logs
4. **Keep functions focused** - One clear purpose per function
5. **Validate parameters** - Check for required data before processing
6. **Use MCP tools efficiently** - Batch operations when possible
7. **Document your logic** - Add comments for complex operations

## Complete Examples Reference

For comprehensive examples of all MCP tools and usage patterns, see:

- **[`examples/js/simple-test.js`](examples/js/simple-test.js)** - Basic functionality test
- **[`examples/js/jira-mcp-examples.js`](examples/js/jira-mcp-examples.js)** - 40+ Jira MCP tools
- **[`examples/js/confluence-mcp-examples.js`](examples/js/confluence-mcp-examples.js)** - 16 Confluence MCP tools
- **[`examples/js/ai-mcp-examples.js`](examples/js/ai-mcp-examples.js)** - AI integration examples
- **[`examples/js/figma-mcp-examples.js`](examples/js/figma-mcp-examples.js)** - Design system integration
- **[`examples/js/README.md`](examples/js/README.md)** - Detailed documentation and usage instructions

## Troubleshooting

### Common Issues

1. **Function not found**: Ensure your function is named `action`
2. **Parameter undefined**: Check `params.ticket.key` and other parameter access
3. **MCP tool failed**: Verify parameter names and types match tool requirements
4. **Timeout errors**: Reduce complexity or use more efficient MCP operations

### Debugging

- Use `console.log()` to output debug information
- Check job execution logs for JavaScript output and errors
- Test with simple operations first, then build complexity
- Verify MCP tool parameters using the example files

## Related Documentation

- **Job Configuration**: See job JSON configuration files in `dmtools-server/src/main/resources/jobs/`
- **MCP Tools**: Full tool definitions in generated MCP classes
- **Localization**: Language-specific text in `dmtools-server/src/main/resources/i18n/`
