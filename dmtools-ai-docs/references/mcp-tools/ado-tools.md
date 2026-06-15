# ADO MCP Tools

**Total Tools**: 37

## Quick Reference

```bash
# List all ado tools
dmtools list | jq '.tools[] | select(.name | startswith("ado_"))'

# Example usage
dmtools ado_get_work_item [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for ado tools
const result = ado_get_work_item(...);
const result = ado_search_by_wiql(...);
const result = ado_get_comments(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `ado_add_inline_comment` | Create a new inline code comment on a specific file and line range in an Azure DevOps pull request. Creates a new thread with file context. | `side` (string, optional)<br>`line` (string, **required**)<br>`filePath` (string, **required**)<br>`startLine` (string, optional)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_add_pr_comment` | Add a general comment to an Azure DevOps pull request (creates a new thread). For inline code comments, use ado_add_inline_comment instead. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`text` (string, **required**) |
| `ado_add_pr_label` | Add a label (tag) to an Azure DevOps pull request. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`label` (string, **required**) |
| `ado_add_pr_reviewer` | Add a reviewer to an Azure DevOps pull request. Optionally set their initial vote. | `reviewerId` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`vote` (string, optional) |
| `ado_assign_work_item` | Assign a work item to a user | `userEmail` (string, **required**)<br>`id` (string, **required**) |
| `ado_create_work_item` | Create a new work item in Azure DevOps | `workItemType` (string, **required**)<br>`project` (string, **required**)<br>`description` (string, optional)<br>`fieldsJson` (object, optional)<br>`title` (string, **required**) |
| `ado_delete_pr_comment` | Delete a comment from a pull request thread. Requires both threadId and commentId. | `threadId` (string, **required**)<br>`commentId` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_download_attachment` | Download an ADO work item attachment by URL and save it as a file | `href` (string, **required**) |
| `ado_get_changelog` | Get the complete history/changelog of a work item | `ticket` (object, optional)<br>`id` (string, **required**) |
| `ado_get_comments` | Get all comments for a work item | `ticket` (object, **required**)<br>`id` (string, **required**) |
| `ado_get_my_profile` | Get the current user's profile information from Azure DevOps | None |
| `ado_get_pipeline_logs` | Get combined logs for all tasks in a pipeline run (build ID). Equivalent to github_get_job_logs. | `tailLines` (number, optional)<br>`buildId` (number, **required**)<br>`taskName` (string, optional) |
| `ado_get_pipeline_run` | Get details of a specific pipeline run including state and result. | `pipelineId` (number, **required**)<br>`runId` (number, **required**) |
| `ado_get_pr` | Get details of an Azure DevOps pull request including title, description, status, author, reviewers, branches, and merge info. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_pr_comments` | Get all comment threads for an Azure DevOps pull request. Each thread contains comments, file context for inline comments, and status (active, fixed, closed, etc.). | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_pr_diff` | Get the diff/changes for an Azure DevOps pull request. Returns the list of changed files with change types. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_pr_reviewers` | Get all reviewers for an Azure DevOps pull request with their vote status. Vote: 10=approved, 5=approved with suggestions, 0=no vote, -5=waiting for author, -10=rejected. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_pr_work_items` | Get work items linked to an Azure DevOps pull request. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_user_by_email` | Get user information by email address in Azure DevOps | `email` (string, **required**) |
| `ado_get_work_item` | Get a specific Azure DevOps work item by ID with optional field filtering | `fields` (array, optional)<br>`id` (string, **required**) |
| `ado_link_work_items` | Link two work items with a relationship (e.g., Parent-Child, Related, Tested By) | `sourceId` (string, **required**)<br>`targetId` (string, **required**)<br>`relationship` (string, **required**) |
| `ado_list_pipeline_runs` | List recent runs of a pipeline. Equivalent to github_list_workflow_runs. | `top` (number, optional)<br>`pipelineId` (number, **required**) |
| `ado_list_pipelines` | List all pipelines defined in the ADO project. | None |
| `ado_list_prs` | List pull requests in an Azure DevOps Git repository by status. Status can be 'active', 'completed', or 'abandoned'. | `repository` (string, **required**)<br>`status` (string, **required**) |
| `ado_merge_pr` | Complete (merge) an Azure DevOps pull request. Sets status to 'completed' with the specified merge strategy. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`commitMessage` (string, optional)<br>`mergeStrategy` (string, optional)<br>`deleteSourceBranch` (string, optional) |
| `ado_move_to_state` | Move a work item to a specific state | `id` (string, **required**)<br>`state` (string, **required**) |
| `ado_post_comment` | Post a comment to a work item | `comment` (string, **required**)<br>`id` (string, **required**) |
| `ado_remove_pr_label` | Remove a label (tag) from an Azure DevOps pull request. Requires the labelId (from ado_get_pr or ado_list_prs labels array). | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`labelId` (string, **required**) |
| `ado_reply_to_pr_thread` | Reply to an existing comment thread in an Azure DevOps pull request. Use the threadId from ado_get_pr_comments. | `threadId` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_resolve_pr_thread` | Resolve (close) a comment thread in an Azure DevOps pull request. Sets the thread status to 'fixed'. Other statuses: 'active', 'closed', 'byDesign', 'pending', 'wontFix'. | `threadId` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`status` (string, optional) |
| `ado_search_by_wiql` | Search for work items using WIQL (Work Item Query Language) | `fields` (array, optional)<br>`wiql` (string, **required**) |
| `ado_set_pr_vote` | Set the current user's vote on a pull request. Vote values: 10=approve, 5=approve with suggestions, 0=reset/no vote, -5=wait for author, -10=reject. | `reviewerId` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`vote` (string, **required**) |
| `ado_trigger_pipeline` | Trigger a pipeline run in ADO. Equivalent to github_trigger_workflow. | `branch` (string, optional)<br>`variables` (string, optional)<br>`pipelineId` (number, **required**) |
| `ado_update_description` | Update the description of a work item | `description` (string, **required**)<br>`id` (string, **required**) |
| `ado_update_pr` | Update pull request properties such as title, description, or status. Use status='abandoned' to abandon a PR, or 'active' to reactivate. | `description` (string, optional)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`title` (string, optional)<br>`status` (string, optional) |
| `ado_update_pr_comment` | Update (edit) an existing comment in a pull request thread. Requires both threadId and commentId. | `threadId` (string, **required**)<br>`commentId` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_update_tags` | Update the tags of a work item (semicolon-separated string) | `id` (string, **required**)<br>`tags` (string, **required**) |

## Detailed Parameter Information

### `ado_add_inline_comment`

Create a new inline code comment on a specific file and line range in an Azure DevOps pull request. Creates a new thread with file context.

**Parameters:**

- **`side`** (string) ⚪ Optional
  - Which diff side to comment on: 'right' (new code, default) or 'left' (old code)
  - Example: `right`

- **`line`** (string) 🔴 Required
  - The line number to comment on (1-based)
  - Example: `42`

- **`filePath`** (string) 🔴 Required
  - The relative file path in the repository (must start with /)
  - Example: `/src/main/java/com/example/Foo.java`

- **`startLine`** (string) ⚪ Optional
  - For multi-line comments: the first line of the range. Must be less than or equal to line.
  - Example: `40`

- **`text`** (string) 🔴 Required
  - The comment text (Markdown supported)
  - Example: `This should be refactored.`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_add_inline_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_add_inline_comment("side", "line");
```

---

### `ado_add_pr_comment`

Add a general comment to an Azure DevOps pull request (creates a new thread). For inline code comments, use ado_add_inline_comment instead.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`text`** (string) 🔴 Required
  - The comment text to add (Markdown supported)
  - Example: `Looks good!`

**Example:**
```bash
dmtools ado_add_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_add_pr_comment("repository", "pullRequestId");
```

---

### `ado_add_pr_label`

Add a label (tag) to an Azure DevOps pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`label`** (string) 🔴 Required
  - The label name to add
  - Example: `needs-review`

**Example:**
```bash
dmtools ado_add_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_add_pr_label("repository", "pullRequestId");
```

---

### `ado_add_pr_reviewer`

Add a reviewer to an Azure DevOps pull request. Optionally set their initial vote.

**Parameters:**

- **`reviewerId`** (string) 🔴 Required
  - The reviewer's ID (GUID from ado_get_user_by_email or uniqueName)
  - Example: `ab1c2d3e-...`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`vote`** (string) ⚪ Optional
  - Optional initial vote: 10=approve, 5=approve with suggestions, 0=no vote, -5=wait for author, -10=reject
  - Example: `0`

**Example:**
```bash
dmtools ado_add_pr_reviewer "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_add_pr_reviewer("reviewerId", "repository");
```

---

### `ado_assign_work_item`

Assign a work item to a user

**Parameters:**

- **`userEmail`** (string) 🔴 Required
  - The user email or display name

- **`id`** (string) 🔴 Required
  - The work item ID

**Example:**
```bash
dmtools ado_assign_work_item "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_assign_work_item("userEmail", "id");
```

---

### `ado_create_work_item`

Create a new work item in Azure DevOps

**Parameters:**

- **`workItemType`** (string) 🔴 Required
  - The work item type (Bug, Task, User Story, etc.)

- **`project`** (string) 🔴 Required
  - The project name

- **`description`** (string) ⚪ Optional
  - The work item description (HTML)

- **`fieldsJson`** (object) ⚪ Optional
  - Additional fields as JSON object (e.g., {"Microsoft.VSTS.Common.Priority": 1})

- **`title`** (string) 🔴 Required
  - The work item title

**Example:**
```bash
dmtools ado_create_work_item "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_create_work_item("workItemType", "project");
```

---

### `ado_delete_pr_comment`

Delete a comment from a pull request thread. Requires both threadId and commentId.

**Parameters:**

- **`threadId`** (string) 🔴 Required
  - The ID of the thread containing the comment
  - Example: `42`

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to delete
  - Example: `2`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_delete_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_delete_pr_comment("threadId", "commentId");
```

---

### `ado_download_attachment`

Download an ADO work item attachment by URL and save it as a file

**Parameters:**

- **`href`** (string) 🔴 Required
  - The attachment URL to download

**Example:**
```bash
dmtools ado_download_attachment "value"
```

```javascript
// In JavaScript agent
const result = ado_download_attachment("href");
```

---

### `ado_get_changelog`

Get the complete history/changelog of a work item

**Parameters:**

- **`ticket`** (object) ⚪ Optional
  - Optional work item object (can be null)

- **`id`** (string) 🔴 Required
  - The work item ID

**Example:**
```bash
dmtools ado_get_changelog "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_changelog("ticket", "id");
```

---

### `ado_get_comments`

Get all comments for a work item

**Parameters:**

- **`ticket`** (object) 🔴 Required
  - Parameter ticket

- **`id`** (string) 🔴 Required
  - The work item ID

**Example:**
```bash
dmtools ado_get_comments "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_comments("ticket", "id");
```

---

### `ado_get_my_profile`

Get the current user's profile information from Azure DevOps

**Parameters:** None

**Example:**
```bash
dmtools ado_get_my_profile
```

```javascript
// In JavaScript agent
const result = ado_get_my_profile();
```

---

### `ado_get_pipeline_logs`

Get combined logs for all tasks in a pipeline run (build ID). Equivalent to github_get_job_logs.

**Parameters:**

- **`tailLines`** (number) ⚪ Optional
  - Lines to return from the end of each task log (default 200, 0 = all)

- **`buildId`** (number) 🔴 Required
  - The build/run ID returned by ado_trigger_pipeline or ado_list_pipeline_runs

- **`taskName`** (string) ⚪ Optional
  - Optional: filter logs to a specific task name (case-insensitive substring match)

**Example:**
```bash
dmtools ado_get_pipeline_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pipeline_logs("tailLines", "buildId");
```

---

### `ado_get_pipeline_run`

Get details of a specific pipeline run including state and result.

**Parameters:**

- **`pipelineId`** (number) 🔴 Required
  - The pipeline ID

- **`runId`** (number) 🔴 Required
  - The run ID

**Example:**
```bash
dmtools ado_get_pipeline_run "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pipeline_run("pipelineId", "runId");
```

---

### `ado_get_pr`

Get details of an Azure DevOps pull request including title, description, status, author, reviewers, branches, and merge info.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID (numeric)
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pr("repository", "pullRequestId");
```

---

### `ado_get_pr_comments`

Get all comment threads for an Azure DevOps pull request. Each thread contains comments, file context for inline comments, and status (active, fixed, closed, etc.).

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr_comments "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pr_comments("repository", "pullRequestId");
```

---

### `ado_get_pr_diff`

Get the diff/changes for an Azure DevOps pull request. Returns the list of changed files with change types.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr_diff "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pr_diff("repository", "pullRequestId");
```

---

### `ado_get_pr_reviewers`

Get all reviewers for an Azure DevOps pull request with their vote status. Vote: 10=approved, 5=approved with suggestions, 0=no vote, -5=waiting for author, -10=rejected.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr_reviewers "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pr_reviewers("repository", "pullRequestId");
```

---

### `ado_get_pr_work_items`

Get work items linked to an Azure DevOps pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr_work_items "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_pr_work_items("repository", "pullRequestId");
```

---

### `ado_get_user_by_email`

Get user information by email address in Azure DevOps

**Parameters:**

- **`email`** (string) 🔴 Required
  - User email address

**Example:**
```bash
dmtools ado_get_user_by_email "value"
```

```javascript
// In JavaScript agent
const result = ado_get_user_by_email("email");
```

---

### `ado_get_work_item`

Get a specific Azure DevOps work item by ID with optional field filtering

**Parameters:**

- **`fields`** (array) ⚪ Optional
  - Optional array of fields to include in the response

- **`id`** (string) 🔴 Required
  - The work item ID (numeric)
  - Example: `12345`

**Example:**
```bash
dmtools ado_get_work_item "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_get_work_item("fields", "id");
```

---

### `ado_link_work_items`

Link two work items with a relationship (e.g., Parent-Child, Related, Tested By)

**Parameters:**

- **`sourceId`** (string) 🔴 Required
  - The source work item ID

- **`targetId`** (string) 🔴 Required
  - The target work item ID to link to

- **`relationship`** (string) 🔴 Required
  - Relationship type (e.g., 'parent', 'child', 'related', 'tested by', 'tests')
  - Example: `parent`

**Example:**
```bash
dmtools ado_link_work_items "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_link_work_items("sourceId", "targetId");
```

---

### `ado_list_pipeline_runs`

List recent runs of a pipeline. Equivalent to github_list_workflow_runs.

**Parameters:**

- **`top`** (number) ⚪ Optional
  - Number of runs to return (default 10)

- **`pipelineId`** (number) 🔴 Required
  - The pipeline ID

**Example:**
```bash
dmtools ado_list_pipeline_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_list_pipeline_runs("top", "pipelineId");
```

---

### `ado_list_pipelines`

List all pipelines defined in the ADO project.

**Parameters:** None

**Example:**
```bash
dmtools ado_list_pipelines
```

```javascript
// In JavaScript agent
const result = ado_list_pipelines();
```

---

### `ado_list_prs`

List pull requests in an Azure DevOps Git repository by status. Status can be 'active', 'completed', or 'abandoned'.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`status`** (string) 🔴 Required
  - The status of pull requests to list: 'active', 'completed', or 'abandoned'. 'open'/'opened' accepted as synonym for 'active'.
  - Example: `active`

**Example:**
```bash
dmtools ado_list_prs "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_list_prs("repository", "status");
```

---

### `ado_merge_pr`

Complete (merge) an Azure DevOps pull request. Sets status to 'completed' with the specified merge strategy.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID to complete/merge
  - Example: `1`

- **`commitMessage`** (string) ⚪ Optional
  - Optional merge commit message
  - Example: `Merging feature branch`

- **`mergeStrategy`** (string) ⚪ Optional
  - The merge strategy: 'squash' (default), 'noFastForward', 'rebase', 'rebaseMerge'
  - Example: `squash`

- **`deleteSourceBranch`** (string) ⚪ Optional
  - Whether to delete the source branch after merging (default: true)
  - Example: `true`

**Example:**
```bash
dmtools ado_merge_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_merge_pr("repository", "pullRequestId");
```

---

### `ado_move_to_state`

Move a work item to a specific state

**Parameters:**

- **`id`** (string) 🔴 Required
  - The work item ID

- **`state`** (string) 🔴 Required
  - The target state name
  - Example: `Active`

**Example:**
```bash
dmtools ado_move_to_state "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_move_to_state("id", "state");
```

---

### `ado_post_comment`

Post a comment to a work item

**Parameters:**

- **`comment`** (string) 🔴 Required
  - The comment text

- **`id`** (string) 🔴 Required
  - The work item ID

**Example:**
```bash
dmtools ado_post_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_post_comment("comment", "id");
```

---

### `ado_remove_pr_label`

Remove a label (tag) from an Azure DevOps pull request. Requires the labelId (from ado_get_pr or ado_list_prs labels array).

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`labelId`** (string) 🔴 Required
  - The label ID to remove (from the PR labels array, not the label name)
  - Example: `e10671ab-...`

**Example:**
```bash
dmtools ado_remove_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_remove_pr_label("repository", "pullRequestId");
```

---

### `ado_reply_to_pr_thread`

Reply to an existing comment thread in an Azure DevOps pull request. Use the threadId from ado_get_pr_comments.

**Parameters:**

- **`threadId`** (string) 🔴 Required
  - The ID of the thread to reply to (from ado_get_pr_comments)
  - Example: `42`

- **`text`** (string) 🔴 Required
  - The reply text (Markdown supported)
  - Example: `Fixed in the latest commit.`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_reply_to_pr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_reply_to_pr_thread("threadId", "text");
```

---

### `ado_resolve_pr_thread`

Resolve (close) a comment thread in an Azure DevOps pull request. Sets the thread status to 'fixed'. Other statuses: 'active', 'closed', 'byDesign', 'pending', 'wontFix'.

**Parameters:**

- **`threadId`** (string) 🔴 Required
  - The ID of the thread to resolve (from ado_get_pr_comments)
  - Example: `42`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`status`** (string) ⚪ Optional
  - The new status: 'fixed' (default/resolved), 'closed', 'byDesign', 'wontFix', 'pending', 'active'
  - Example: `fixed`

**Example:**
```bash
dmtools ado_resolve_pr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_resolve_pr_thread("threadId", "repository");
```

---

### `ado_search_by_wiql`

Search for work items using WIQL (Work Item Query Language)

**Parameters:**

- **`fields`** (array) ⚪ Optional
  - Optional array of fields to include

- **`wiql`** (string) 🔴 Required
  - WIQL query string
  - Example: `SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug'`

**Example:**
```bash
dmtools ado_search_by_wiql "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_search_by_wiql("fields", "wiql");
```

---

### `ado_set_pr_vote`

Set the current user's vote on a pull request. Vote values: 10=approve, 5=approve with suggestions, 0=reset/no vote, -5=wait for author, -10=reject.

**Parameters:**

- **`reviewerId`** (string) 🔴 Required
  - The reviewer's ID (GUID) — use ado_get_my_profile or ado_get_user_by_email to get it
  - Example: `ab1c2d3e-...`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`vote`** (string) 🔴 Required
  - Vote value: 10=approve, 5=approve with suggestions, 0=reset, -5=wait for author, -10=reject
  - Example: `10`

**Example:**
```bash
dmtools ado_set_pr_vote "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_set_pr_vote("reviewerId", "repository");
```

---

### `ado_trigger_pipeline`

Trigger a pipeline run in ADO. Equivalent to github_trigger_workflow.

**Parameters:**

- **`branch`** (string) ⚪ Optional
  - The branch to run the pipeline on (e.g. 'main')

- **`variables`** (string) ⚪ Optional
  - JSON object of pipeline variables, e.g. {"myVar":"value"}

- **`pipelineId`** (number) 🔴 Required
  - The pipeline ID to trigger

**Example:**
```bash
dmtools ado_trigger_pipeline "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_trigger_pipeline("branch", "variables");
```

---

### `ado_update_description`

Update the description of a work item

**Parameters:**

- **`description`** (string) 🔴 Required
  - The new description (HTML format)

- **`id`** (string) 🔴 Required
  - The work item ID

**Example:**
```bash
dmtools ado_update_description "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_update_description("description", "id");
```

---

### `ado_update_pr`

Update pull request properties such as title, description, or status. Use status='abandoned' to abandon a PR, or 'active' to reactivate.

**Parameters:**

- **`description`** (string) ⚪ Optional
  - New description for the pull request (Markdown supported)
  - Example: `Updated description`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

- **`title`** (string) ⚪ Optional
  - New title for the pull request
  - Example: `Updated PR title`

- **`status`** (string) ⚪ Optional
  - New status: 'active' or 'abandoned'
  - Example: `active`

**Example:**
```bash
dmtools ado_update_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_update_pr("description", "repository");
```

---

### `ado_update_pr_comment`

Update (edit) an existing comment in a pull request thread. Requires both threadId and commentId.

**Parameters:**

- **`threadId`** (string) 🔴 Required
  - The ID of the thread containing the comment
  - Example: `42`

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to update
  - Example: `1`

- **`text`** (string) 🔴 Required
  - The new comment text (replaces existing content)
  - Example: `Updated analysis.`

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID
  - Example: `1`

**Example:**
```bash
dmtools ado_update_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_update_pr_comment("threadId", "commentId");
```

---

### `ado_update_tags`

Update the tags of a work item (semicolon-separated string)

**Parameters:**

- **`id`** (string) 🔴 Required
  - The work item ID

- **`tags`** (string) 🔴 Required
  - Tags as semicolon-separated string (e.g., 'tag1;tag2;tag3')

**Example:**
```bash
dmtools ado_update_tags "value" "value"
```

```javascript
// In JavaScript agent
const result = ado_update_tags("id", "tags");
```

---

