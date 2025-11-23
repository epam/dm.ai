# Azure DevOps Integration for DMTools

This package provides integration between DMTools and Azure DevOps (ADO) work item tracking.

## Overview

The ADO integration implements the `TrackerClient` interface, allowing DMTools to interact with Azure DevOps work items just like it does with Jira tickets. This enables:

- Creating, reading, and updating work items
- Searching work items using WIQL (Work Item Query Language)
- Managing comments on work items
- Assigning work items to users
- Managing tags/labels
- Linking work items with relationships
- And all other standard TrackerClient operations

## Configuration

### Environment Variables

Set the following environment variables in your `.env` or `dmtools.env` file:

```bash
# Required
ADO_ORGANIZATION=yourorganization       # Your Azure DevOps organization name
ADO_PROJECT=YourProject                 # Default project to work with
ADO_PAT_TOKEN=your-pat-token-here      # Personal Access Token

# Optional
ADO_BASE_PATH=https://dev.azure.com    # Base URL (defaults to dev.azure.com)
```

### Creating a Personal Access Token (PAT)

1. Go to `https://dev.azure.com/{your-organization}/_usersSettings/tokens`
2. Click "New Token"
3. Give it a name (e.g., "DMTools Integration")
4. Set expiration as needed
5. Select scopes:
   - **Work Items**: Read, write, & manage
   - **Code**: Read (optional, for source code integration)
6. Click "Create" and copy the token immediately (it won't be shown again)

## Usage

### Using MCP Tools

The ADO client exposes all operations as MCP tools with the `ado_` prefix:

```javascript
// Get a work item by ID
const workItem = await ado_get_work_item({ id: "12345" });

// Search using WIQL
const bugs = await ado_search_by_wiql({ 
  wiql: "SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug' AND [System.State] = 'Active'"
});

// Create a work item
await ado_create_work_item({
  project: "MyProject",
  workItemType: "Bug",
  title: "Critical bug found",
  description: "<p>Description in HTML format</p>"
});

// Update work item state
await ado_move_to_state({ id: "12345", state: "Active" });

// Assign work item
await ado_assign_work_item({ id: "12345", userEmail: "user@company.com" });

// Post a comment
await ado_post_comment({ id: "12345", comment: "This is a comment" });
```

### Using Programmatically

```java
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;

// Get singleton instance (uses environment variables)
BasicAzureDevOpsClient client = BasicAzureDevOpsClient.getInstance();

// Get a work item
WorkItem workItem = client.performTicket("12345", null);

// Search for work items
String wiql = "SELECT [System.Id] FROM WorkItems WHERE [System.State] = 'New'";
List<WorkItem> results = client.searchAndPerform(wiql, client.getDefaultQueryFields());

// Create a work item
String response = client.createTicketInProject(
    "MyProject",
    "Bug",
    "Bug title",
    "<p>Bug description</p>",
    fields -> {
        fields.set("Microsoft.VSTS.Common.Priority", 1);
        fields.set("System.Tags", "urgent;customer-reported");
    }
);
```

## Field Mapping

ADO uses a different field naming convention than Jira. The client provides automatic mapping for common fields:

| Common Name | ADO Field Name |
|------------|----------------|
| id | System.Id |
| title | System.Title |
| description | System.Description |
| state | System.State |
| assignedto | System.AssignedTo |
| priority | Microsoft.VSTS.Common.Priority |
| tags | System.Tags |
| storypoints | Microsoft.VSTS.Scheduling.StoryPoints |

You can use either the friendly name or the full ADO field name when querying or updating work items.

## Work Item Types

ADO supports various work item types depending on your process template:

**Agile Process:**
- User Story
- Bug
- Task
- Feature
- Epic
- Test Case

**Scrum Process:**
- Product Backlog Item
- Bug
- Task
- Feature
- Epic
- Test Case

**CMMI Process:**
- Requirement
- Bug
- Task
- Feature
- Epic
- Test Case

## WIQL (Work Item Query Language)

WIQL is ADO's query language for searching work items. Examples:

```sql
-- Get all active bugs
SELECT [System.Id] FROM WorkItems 
WHERE [System.WorkItemType] = 'Bug' 
AND [System.State] = 'Active'

-- Get items assigned to a user
SELECT [System.Id] FROM WorkItems 
WHERE [System.AssignedTo] = 'User Name'

-- Get items in a specific iteration
SELECT [System.Id] FROM WorkItems 
WHERE [System.IterationPath] = 'Project\\Sprint 1'

-- Get high priority items
SELECT [System.Id] FROM WorkItems 
WHERE [Microsoft.VSTS.Common.Priority] = 1
```

## Tags vs Labels

ADO uses tags instead of labels, and they're stored differently:
- **ADO Tags**: Semicolon-separated string (e.g., "tag1;tag2;tag3")
- **Jira Labels**: Array of strings

The client automatically converts between formats when using the label management methods.

## Relationships and Links

ADO supports various relationship types:

- `System.LinkTypes.Hierarchy-Forward` - Parent/Child
- `System.LinkTypes.Hierarchy-Reverse` - Child/Parent
- `System.LinkTypes.Related` - Related items
- `System.LinkTypes.Dependency-Forward` - Blocks
- `System.LinkTypes.Dependency-Reverse` - Blocked by

## Priority Mapping

ADO uses numeric priorities (1-4), which are mapped to friendly names:

| ADO Priority | Mapped Name |
|--------------|-------------|
| 1 | Critical |
| 2 | High |
| 3 | Medium |
| 4 | Low |

## Known Limitations

1. **Comments**: ADO comments cannot be deleted via API (only hidden or edited)
2. **Server**: Only ADO Cloud is supported (not ADO Server/on-premises)
3. **Attachments**: File attachment support is not yet implemented
4. **Changelog**: Full changelog/history retrieval is not yet implemented
5. **Test Cases**: Test case specific operations are not yet implemented

## Architecture

The implementation follows DMTools' standard patterns:

```
microsoft/ado/
├── AzureDevOpsClient.java          # Main client (extends AbstractRestClient)
├── BasicAzureDevOpsClient.java     # Singleton with env-based config
└── model/
    ├── WorkItem.java                # Work item model (implements ITicket)
    └── WorkItemComment.java         # Comment model (implements IComment)
```

## Dependencies

The ADO integration uses:
- OkHttp for HTTP requests
- org.json for JSON parsing
- DMTools common interfaces (TrackerClient, ITicket, etc.)
- MCP annotations for tool exposure

## Troubleshooting

### Authentication Errors

If you see authentication errors:
1. Verify your PAT token is valid and not expired
2. Check that the token has the required scopes (Work Items: Read & Write)
3. Ensure the organization name is correct

### Connection Errors

If you can't connect:
1. Verify the organization URL is accessible
2. Check your network/firewall settings
3. Ensure you're using the correct organization name

### Work Item Not Found

If work items aren't found:
1. Verify the work item ID exists
2. Check that the PAT token has access to the project
3. Ensure the project name is correct

## API Documentation

For detailed Azure DevOps REST API documentation, see:
- [Work Items API](https://learn.microsoft.com/en-us/rest/api/azure/devops/wit/)
- [WIQL Syntax](https://learn.microsoft.com/en-us/azure/devops/boards/queries/wiql-syntax)

## Contributing

When extending the ADO integration:
1. Follow existing patterns from JiraClient
2. Add @MCPTool annotations to all public methods
3. Use proper field name resolution
4. Handle ADO-specific data formats (tags, priorities, etc.)
5. Add appropriate error handling and logging

