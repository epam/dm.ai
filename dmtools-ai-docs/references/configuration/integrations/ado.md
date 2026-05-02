# Azure DevOps (ADO) Configuration Guide

## 🎯 Overview

DMtools provides 23+ MCP tools for Azure DevOps integration, enabling work item management, sprint planning, and cross-platform synchronization with Jira.

If you only need the Azure DevOps-focused AI assistant package, install `/dmtools-ado` with the instructions in [../../installation/README.md#install-only-the-skills-you-need](../../installation/README.md#install-only-the-skills-you-need).

## 🔑 Personal Access Token (PAT) Setup

### Step 1: Create Personal Access Token

1. Go to Azure DevOps: `https://dev.azure.com/{your-organization}`
2. Click on **User Settings** (top right) → **Personal Access Tokens**
3. Click **"+ New Token"**
4. Configure token:
   - **Name**: "DMtools Integration"
   - **Organization**: Select your organization
   - **Expiration**: Set appropriate expiry (max 1 year)
   - **Scopes**: Select "Custom defined" and enable:
     - Work Items: Read & Write
     - Code: Read & Write (required for PR operations)
     - Pull Request Threads: Read & Write
     - Project and Team: Read

5. Click **Create** and copy the token immediately

### Step 2: Configure DMtools

Add to your `dmtools.env`:

```bash
# Azure DevOps Configuration
ADO_BASE_PATH=https://dev.azure.com/YourOrganization
ADO_PAT=YOUR_ADO_PERSONAL_ACCESS_TOKEN
ADO_PROJECT=YourProject
ADO_AREA_PATH=YourProject\\Team1\\Backend
ADO_ITERATION_PATH=YourProject\\Sprint 23
```

## 🔧 Configuration Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `ADO_BASE_PATH` | Organization URL | `https://dev.azure.com/contoso` |
| `ADO_PAT` | Personal Access Token | `YOUR_ADO_PERSONAL_ACCESS_TOKEN...` |
| `ADO_PROJECT` | Default project name | `MyProject` |

### Optional Variables

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `ADO_AREA_PATH` | Default area path | `MyProject\\Backend` | Project root |
| `ADO_ITERATION_PATH` | Default iteration | `MyProject\\Sprint 23` | Current iteration |
| `ADO_TEAM` | Default team | `Backend Team` | Default team |
| `ADO_API_VERSION` | API version | `7.0` | `6.0` |
| `ADO_WORK_ITEM_TYPE` | Default type | `User Story` | `Task` |

## 📋 Available ADO MCP Tools (23+)

### Work Item Operations

| Tool | Description | Example |
|------|-------------|---------|
| `ado_get_work_item` | Get work item details | `dmtools ado_get_work_item 12345` |
| `ado_create_work_item` | Create new work item | `dmtools ado_create_work_item "User Story" "New feature"` |
| `ado_update_work_item` | Update work item | `dmtools ado_update_work_item 12345 --data '{"System.Title":"Updated"}'` |
| `ado_delete_work_item` | Delete work item | `dmtools ado_delete_work_item 12345` |
| `ado_search_work_items` | Search with WIQL | `dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [State] = 'Active'"` |

### State & Assignment

| Tool | Description | Example |
|------|-------------|---------|
| `ado_move_to_state` | Change work item state | `dmtools ado_move_to_state 12345 "Resolved"` |
| `ado_assign_work_item` | Assign to user | `dmtools ado_assign_work_item 12345 "john.doe@company.com"` |
| `ado_get_state_transitions` | Get valid transitions | `dmtools ado_get_state_transitions 12345` |

### Comments & Relations

| Tool | Description | Example |
|------|-------------|---------|
| `ado_add_comment` | Add comment | `dmtools ado_add_comment 12345 "Review complete"` |
| `ado_get_comments` | Get all comments | `dmtools ado_get_comments 12345` |
| `ado_add_relation` | Link work items | `dmtools ado_add_relation 12345 12346 "Parent"` |

### Pull Request Operations

| Tool | Description | Example |
|------|-------------|---------|
| `ado_list_prs` | List PRs by status | `dmtools ado_list_prs "my-repo" "active"` |
| `ado_get_pr` | Get PR details | `dmtools ado_get_pr "my-repo" "1"` |
| `ado_get_pr_comments` | Get PR threads | `dmtools ado_get_pr_comments "my-repo" "1"` |
| `ado_add_pr_comment` | Add PR comment | `dmtools ado_add_pr_comment "my-repo" "1" "LGTM"` |
| `ado_reply_to_pr_thread` | Reply to thread | `dmtools ado_reply_to_pr_thread "my-repo" "1" "42" "Fixed"` |
| `ado_add_inline_comment` | Inline code comment | `dmtools ado_add_inline_comment "my-repo" "1" "/src/App.java" "42" "Refactor"` |
| `ado_resolve_pr_thread` | Resolve thread | `dmtools ado_resolve_pr_thread "my-repo" "1" "42" "fixed"` |
| `ado_update_pr_comment` | Edit comment | `dmtools ado_update_pr_comment "my-repo" "1" "42" "1" "Updated"` |
| `ado_delete_pr_comment` | Delete comment | `dmtools ado_delete_pr_comment "my-repo" "1" "42" "2"` |
| `ado_get_pr_diff` | Get PR changes | `dmtools ado_get_pr_diff "my-repo" "1"` |
| `ado_merge_pr` | Complete/merge PR | `dmtools ado_merge_pr "my-repo" "1" "squash"` |
| `ado_add_pr_label` | Add PR label | `dmtools ado_add_pr_label "my-repo" "1" "needs-review"` |
| `ado_remove_pr_label` | Remove PR label | `dmtools ado_remove_pr_label "my-repo" "1" "label-id"` |
| `ado_get_pr_reviewers` | Get reviewers | `dmtools ado_get_pr_reviewers "my-repo" "1"` |
| `ado_add_pr_reviewer` | Add reviewer | `dmtools ado_add_pr_reviewer "my-repo" "1" "user-guid"` |
| `ado_set_pr_vote` | Vote on PR | `dmtools ado_set_pr_vote "my-repo" "1" "user-guid" "10"` |
| `ado_update_pr` | Update PR title/desc | `dmtools ado_update_pr "my-repo" "1" "New title"` |
| `ado_get_pr_work_items` | Linked work items | `dmtools ado_get_pr_work_items "my-repo" "1"` |

### Sprint & Planning

| Tool | Description | Example |
|------|-------------|---------|
| `ado_get_iterations` | List iterations | `dmtools ado_get_iterations` |
| `ado_get_current_iteration` | Get current sprint | `dmtools ado_get_current_iteration` |
| `ado_move_to_iteration` | Assign to sprint | `dmtools ado_move_to_iteration 12345 "Sprint 23"` |

## 🔄 Jira to ADO Mapping

### Work Item Type Mapping

```bash
# dmtools.env
ADO_TYPE_MAPPING=Story:User Story,Bug:Bug,Task:Task,Epic:Epic,Test:Test Case

# Jira → ADO
# Story → User Story
# Bug → Bug
# Task → Task
# Epic → Epic
# Test → Test Case
```

### Field Mapping

```bash
# Common field mappings
ADO_FIELD_MAPPING=summary:System.Title,description:System.Description,priority:Microsoft.VSTS.Common.Priority,storyPoints:Microsoft.VSTS.Scheduling.StoryPoints

# Custom field mapping
ADO_CUSTOM_FIELDS=customfield_10001:Custom.BusinessValue,customfield_10002:Custom.RiskLevel
```

### State Mapping

```bash
# Jira Status → ADO State
ADO_STATE_MAPPING=To Do:New,In Progress:Active,In Review:Resolved,Done:Closed

# Or use JSON for complex mappings
ADO_STATE_MAPPING_JSON='{
  "To Do": "New",
  "In Progress": "Active",
  "Code Review": "Resolved",
  "Testing": "Resolved",
  "Done": "Closed"
}'
```

## 💡 Usage Examples

### Example 1: Sync Jira Stories to ADO

```javascript
// agents/js/syncJiraToAdo.js
function action(params) {
    // Get Jira stories
    const stories = jira_search_by_jql("project = PROJ AND type = Story AND updated >= -7d");

    const results = {
        created: 0,
        updated: 0,
        errors: []
    };

    for (const story of stories) {
        try {
            // Check if already exists in ADO
            const existingItems = ado_search_work_items(
                `SELECT [Id] FROM WorkItems WHERE [Custom.JiraKey] = '${story.key}'`
            );

            if (existingItems.length > 0) {
                // Update existing
                ado_update_work_item(existingItems[0].id, {
                    "System.Title": story.fields.summary,
                    "System.Description": story.fields.description,
                    "Microsoft.VSTS.Common.Priority": mapPriority(story.fields.priority.name)
                });
                results.updated++;
            } else {
                // Create new
                const newItem = ado_create_work_item("User Story", {
                    "System.Title": story.fields.summary,
                    "System.Description": story.fields.description,
                    "System.AreaPath": params.areaPath || "MyProject",
                    "System.IterationPath": params.iterationPath || "MyProject\\Sprint 23",
                    "Custom.JiraKey": story.key
                });
                results.created++;
            }
        } catch (error) {
            results.errors.push({ story: story.key, error: error.toString() });
        }
    }

    return results;
}

function mapPriority(jiraPriority) {
    const mapping = {
        "Highest": 1,
        "High": 2,
        "Medium": 3,
        "Low": 4,
        "Lowest": 4
    };
    return mapping[jiraPriority] || 3;
}
```

### Example 2: Create Test Cases in ADO

```json
// agents/ado_test_generator.json
{
  "name": "ADOTestGenerator",
  "params": {
    "inputSource": "ado",
    "inputWiql": "SELECT [Id] FROM WorkItems WHERE [System.WorkItemType] = 'User Story' AND [System.State] = 'Active'",
    "aiProvider": "gemini",
    "aiRole": "You are a QA engineer creating test cases for Azure DevOps",
    "instructions": "Generate test cases compatible with Azure Test Plans",
    "outputType": "ado_creation",
    "adoWorkItemType": "Test Case",
    "adoAreaPath": "MyProject\\Testing",
    "postprocessJSAction": "agents/js/linkTestsToStory.js"
  }
}
```

### Example 3: Sprint Planning

```javascript
// Get current sprint items
const currentSprint = ado_get_current_iteration();
const sprintItems = ado_search_work_items(
    `SELECT [Id], [Title], [State], [Assigned To]
     FROM WorkItems
     WHERE [System.IterationPath] = '${currentSprint.path}'
     AND [System.WorkItemType] IN ('User Story', 'Bug')`
);

// Move high-priority items to current sprint
const highPriorityItems = ado_search_work_items(
    `SELECT [Id] FROM WorkItems
     WHERE [Microsoft.VSTS.Common.Priority] <= 2
     AND [System.State] = 'New'`
);

for (const item of highPriorityItems) {
    ado_move_to_iteration(item.id, currentSprint.path);
}
```

## 🔍 Common WIQL Queries

```bash
# Active work items in current sprint
dmtools ado_search_work_items "SELECT [Id], [Title] FROM WorkItems WHERE [System.State] = 'Active' AND [System.IterationPath] UNDER 'MyProject\\Sprint 23'"

# Unassigned high-priority items
dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [System.AssignedTo] = '' AND [Microsoft.VSTS.Common.Priority] <= 2"

# Recently updated items
dmtools ado_search_work_items "SELECT [Id], [Title] FROM WorkItems WHERE [System.ChangedDate] >= @Today - 7"

# Items by area
dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [System.AreaPath] UNDER 'MyProject\\Backend'"

# Bugs in testing
dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug' AND [System.State] = 'Testing'"
```

## 🧪 Testing Your Configuration

### 1. Test Connection

```bash
# Get a work item (use any valid ID)
dmtools ado_get_work_item 1

# List iterations
dmtools ado_get_iterations
```

### 2. Test Search

```bash
# Simple search
dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [System.WorkItemType] = 'Task'"
```

### 3. Test Write Operations

```bash
# Create a test work item
dmtools ado_create_work_item "Task" "Test from DMtools" --data '{"System.Description":"Testing DMtools integration"}'

# Add a comment
dmtools ado_add_comment [WORK_ITEM_ID] "Test comment from DMtools"
```

## 🔄 Bidirectional Sync

### Jira → ADO Sync Configuration

```json
{
  "name": "JiraToADOSync",
  "params": {
    "syncDirection": "jira-to-ado",
    "jiraJql": "project = PROJ AND updated >= -1d",
    "adoProject": "MyProject",
    "fieldMapping": {
      "summary": "System.Title",
      "description": "System.Description",
      "priority": "Microsoft.VSTS.Common.Priority",
      "storyPoints": "Microsoft.VSTS.Scheduling.StoryPoints"
    },
    "stateMapping": {
      "To Do": "New",
      "In Progress": "Active",
      "Done": "Closed"
    },
    "typeMapping": {
      "Story": "User Story",
      "Bug": "Bug",
      "Task": "Task"
    }
  }
}
```

### ADO → Jira Sync Configuration

```json
{
  "name": "ADOToJiraSync",
  "params": {
    "syncDirection": "ado-to-jira",
    "adoWiql": "SELECT [Id] FROM WorkItems WHERE [System.ChangedDate] >= @Today - 1",
    "jiraProject": "PROJ",
    "fieldMapping": {
      "System.Title": "summary",
      "System.Description": "description",
      "Microsoft.VSTS.Common.Priority": "priority",
      "Microsoft.VSTS.Scheduling.StoryPoints": "customfield_10001"
    }
  }
}
```

## 🔒 Security Best Practices

### 1. PAT Security

```bash
# Never commit PATs
echo "dmtools.env" >> .gitignore

# Use minimal permissions
# Only grant: Work Items (Read, Write), Project (Read)
```

### 2. Rotate PATs Regularly

```bash
# Every 90 days:
# 1. Create new PAT in Azure DevOps
# 2. Update dmtools.env
# 3. Test connection
dmtools ado_get_work_item 1
# 4. Revoke old PAT
```

### 3. Use Service Accounts

Create dedicated service account for DMtools:
- Email: `dmtools-service@company.com`
- Permissions: Contributor role in specific projects only

## 🐛 Troubleshooting

### Authentication Failed

```bash
# Error: 401 Unauthorized

# Check PAT is valid and not expired
# Verify organization name in URL
# Ensure PAT has required permissions
```

### Work Item Type Not Found

```bash
# Error: Invalid work item type

# List available types:
dmtools ado_get_work_item_types

# Use exact type name (case-sensitive)
```

### Area/Iteration Path Issues

```bash
# Error: TF200016: The area path is not valid

# List valid paths:
dmtools ado_get_areas
dmtools ado_get_iterations

# Use full path with backslashes:
ADO_AREA_PATH=MyProject\\Team1\\Component
```

### API Version Issues

```bash
# If getting version errors, specify API version:
ADO_API_VERSION=7.0  # Latest
# or
ADO_API_VERSION=6.0  # Stable
```

## 📚 Advanced Features

### Custom Fields

```bash
# Access custom fields
dmtools ado_get_work_item 12345

# Update custom fields
dmtools ado_update_work_item 12345 --data '{
  "Custom.BusinessValue": "High",
  "Custom.ReleaseVersion": "2.0"
}'
```

### Attachments

```bash
# Add attachment to work item
dmtools ado_add_attachment 12345 /path/to/file.pdf

# List attachments
dmtools ado_get_attachments 12345
```

### Tags

```bash
# Add tags
dmtools ado_update_work_item 12345 --data '{"System.Tags":"important; needs-review; sprint23"}'

# Search by tags
dmtools ado_search_work_items "SELECT [Id] FROM WorkItems WHERE [System.Tags] CONTAINS 'important'"
```

## 🔗 Useful Resources

- [Azure DevOps REST API](https://docs.microsoft.com/en-us/rest/api/azure/devops/)
- [WIQL Syntax Reference](https://docs.microsoft.com/en-us/azure/devops/boards/queries/wiql-syntax)
- [Work Item Types](https://docs.microsoft.com/en-us/azure/devops/boards/work-items/about-work-items)
- [PAT Documentation](https://docs.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate)

---

*Next: [Figma Integration](figma.md) | [Confluence Setup](confluence.md)*
