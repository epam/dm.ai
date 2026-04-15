# ADO MCP Tools

**Total Tools**: 31

## Quick Reference

```bash
# List all ado tools
dmtools list | jq '.tools[] | select(.name | startswith("ado_"))'

# Example usage
dmtools ado_get_work_item [arguments]
dmtools ado_list_prs [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for ado tools
const result = ado_get_work_item(...);
const result = ado_search_by_wiql(...);
const result = ado_get_comments(...);

// Pull request operations
const prs = ado_list_prs("ai-native-sdlc-blueprint", "active");
const pr = ado_get_pr("ai-native-sdlc-blueprint", "1");
const threads = ado_get_pr_comments("ai-native-sdlc-blueprint", "1");
ado_add_pr_comment("ai-native-sdlc-blueprint", "1", "Looks good!");
ado_resolve_pr_thread("ai-native-sdlc-blueprint", "1", "42", "fixed");
```

## Available Tools

### Work Item Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `ado_assign_work_item` | Assign a work item to a user | `userEmail` (string, **required**)<br>`id` (string, **required**) |
| `ado_create_work_item` | Create a new work item in Azure DevOps | `workItemType` (string, **required**)<br>`project` (string, **required**)<br>`description` (string, optional)<br>`fieldsJson` (object, optional)<br>`title` (string, **required**) |
| `ado_download_attachment` | Download an ADO work item attachment by URL and save it as a file | `href` (string, **required**) |
| `ado_get_changelog` | Get the complete history/changelog of a work item | `ticket` (object, optional)<br>`id` (string, **required**) |
| `ado_get_comments` | Get all comments for a work item | `ticket` (object, **required**)<br>`id` (string, **required**) |
| `ado_get_my_profile` | Get the current user's profile information from Azure DevOps | None |
| `ado_get_user_by_email` | Get user information by email address in Azure DevOps | `email` (string, **required**) |
| `ado_get_work_item` | Get a specific Azure DevOps work item by ID with optional field filtering | `fields` (array, optional)<br>`id` (string, **required**) |
| `ado_link_work_items` | Link two work items with a relationship (e.g., Parent-Child, Related, Tested By) | `sourceId` (string, **required**)<br>`targetId` (string, **required**)<br>`relationship` (string, **required**) |
| `ado_move_to_state` | Move a work item to a specific state | `id` (string, **required**)<br>`state` (string, **required**) |
| `ado_post_comment` | Post a comment to a work item | `comment` (string, **required**)<br>`id` (string, **required**) |
| `ado_search_by_wiql` | Search for work items using WIQL (Work Item Query Language) | `fields` (array, optional)<br>`wiql` (string, **required**) |
| `ado_update_description` | Update the description of a work item | `description` (string, **required**)<br>`id` (string, **required**) |
| `ado_update_tags` | Update the tags of a work item (semicolon-separated string) | `id` (string, **required**)<br>`tags` (string, **required**) |

### Pull Request Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `ado_list_prs` | List pull requests by status (active, completed, abandoned) | `repository` (string, **required**)<br>`status` (string, **required**) |
| `ado_get_pr` | Get PR details including title, description, status, author, reviewers, branches | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_get_pr_comments` | Get all comment threads for a PR with file context and status | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_add_pr_comment` | Add a general comment to a PR (creates a new thread) | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`text` (string, **required**) |
| `ado_reply_to_pr_thread` | Reply to an existing comment thread | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`threadId` (string, **required**)<br>`text` (string, **required**) |
| `ado_add_inline_comment` | Create inline code comment on specific file/line | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`filePath` (string, **required**)<br>`line` (string, **required**)<br>`text` (string, **required**)<br>`startLine` (string, optional)<br>`side` (string, optional) |
| `ado_resolve_pr_thread` | Resolve/close a comment thread (fixed, closed, byDesign, wontFix, pending, active) | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`threadId` (string, **required**)<br>`status` (string, optional, default: "fixed") |
| `ado_update_pr_comment` | Update (edit) an existing comment in a thread | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`threadId` (string, **required**)<br>`commentId` (string, **required**)<br>`text` (string, **required**) |
| `ado_delete_pr_comment` | Delete a comment from a thread | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`threadId` (string, **required**)<br>`commentId` (string, **required**) |
| `ado_get_pr_diff` | Get diff/changes for a PR (changed files with change types) | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_merge_pr` | Complete (merge) a PR with merge strategy | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`mergeStrategy` (string, optional, default: "squash")<br>`deleteSourceBranch` (string, optional, default: "true")<br>`commitMessage` (string, optional) |
| `ado_add_pr_label` | Add a label (tag) to a PR | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`label` (string, **required**) |
| `ado_remove_pr_label` | Remove a label from a PR by labelId | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`labelId` (string, **required**) |
| `ado_get_pr_reviewers` | Get all reviewers with vote status (10=approved, 0=no vote, -10=rejected) | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `ado_add_pr_reviewer` | Add a reviewer to a PR with optional initial vote | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`reviewerId` (string, **required**)<br>`vote` (string, optional) |
| `ado_set_pr_vote` | Set current user's vote on a PR | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`reviewerId` (string, **required**)<br>`vote` (string, **required**) |
| `ado_update_pr` | Update PR properties (title, description, status) | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`title` (string, optional)<br>`description` (string, optional)<br>`status` (string, optional) |
| `ado_get_pr_work_items` | Get work items linked to a PR | `repository` (string, **required**)<br>`pullRequestId` (string, **required**) |

## Detailed Parameter Information

### `ado_list_prs`

List pull requests in an Azure DevOps Git repository by status.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`status`** (string) 🔴 Required
  - The status: 'active', 'completed', or 'abandoned'. 'open'/'opened' accepted as synonym for 'active'.
  - Example: `active`

**Example:**
```bash
dmtools ado_list_prs "ai-native-sdlc-blueprint" "active"
```

```javascript
// In JavaScript agent
const result = ado_list_prs("ai-native-sdlc-blueprint", "active");
```

---

### `ado_get_pr`

Get details of an Azure DevOps pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name
  - Example: `ai-native-sdlc-blueprint`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID (numeric)
  - Example: `1`

**Example:**
```bash
dmtools ado_get_pr "ai-native-sdlc-blueprint" "1"
```

```javascript
const pr = ado_get_pr("ai-native-sdlc-blueprint", "1");
```

---

### `ado_get_pr_comments`

Get all comment threads for an Azure DevOps pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The Git repository name

- **`pullRequestId`** (string) 🔴 Required
  - The pull request ID

**Example:**
```javascript
const threads = ado_get_pr_comments("ai-native-sdlc-blueprint", "1");
```

---

### `ado_add_pr_comment`

Add a general comment to a pull request (creates a new thread).

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`text`** (string) 🔴 Required - The comment text (Markdown supported)

**Example:**
```javascript
ado_add_pr_comment("ai-native-sdlc-blueprint", "1", "Looks good! ✅");
```

---

### `ado_reply_to_pr_thread`

Reply to an existing comment thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`threadId`** (string) 🔴 Required - Thread ID from ado_get_pr_comments
- **`text`** (string) 🔴 Required - Reply text

**Example:**
```javascript
ado_reply_to_pr_thread("ai-native-sdlc-blueprint", "1", "42", "Fixed in latest commit.");
```

---

### `ado_add_inline_comment`

Create inline code comment on a specific file and line.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`filePath`** (string) 🔴 Required - File path (must start with /)
- **`line`** (string) 🔴 Required - Line number
- **`text`** (string) 🔴 Required - Comment text
- **`startLine`** (string) ⚪ Optional - Start line for multi-line comments
- **`side`** (string) ⚪ Optional - 'right' (new code, default) or 'left' (old code)

**Example:**
```javascript
ado_add_inline_comment("ai-native-sdlc-blueprint", "1", "/src/main/App.java", "42", "Consider refactoring.");
```

---

### `ado_resolve_pr_thread`

Resolve (close) a comment thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`threadId`** (string) 🔴 Required
- **`status`** (string) ⚪ Optional - 'fixed' (default), 'closed', 'byDesign', 'wontFix', 'pending', 'active'

**Example:**
```javascript
ado_resolve_pr_thread("ai-native-sdlc-blueprint", "1", "42", "fixed");
```

---

### `ado_update_pr_comment`

Update an existing comment in a thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`threadId`** (string) 🔴 Required
- **`commentId`** (string) 🔴 Required
- **`text`** (string) 🔴 Required

**Example:**
```javascript
ado_update_pr_comment("ai-native-sdlc-blueprint", "1", "42", "1", "Updated analysis.");
```

---

### `ado_delete_pr_comment`

Delete a comment from a thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`threadId`** (string) 🔴 Required
- **`commentId`** (string) 🔴 Required

---

### `ado_get_pr_diff`

Get diff/changes for a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required

---

### `ado_merge_pr`

Complete (merge) a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`mergeStrategy`** (string) ⚪ Optional - 'squash' (default), 'noFastForward', 'rebase', 'rebaseMerge'
- **`deleteSourceBranch`** (string) ⚪ Optional - default: 'true'
- **`commitMessage`** (string) ⚪ Optional

---

### `ado_add_pr_label`

Add a label to a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`label`** (string) 🔴 Required

---

### `ado_remove_pr_label`

Remove a label from a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`labelId`** (string) 🔴 Required - Label ID (not name)

---

### `ado_get_pr_reviewers`

Get all reviewers with vote status.

**Vote values:** 10=approved, 5=approved with suggestions, 0=no vote, -5=waiting for author, -10=rejected

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required

---

### `ado_add_pr_reviewer`

Add a reviewer to a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`reviewerId`** (string) 🔴 Required - Reviewer GUID
- **`vote`** (string) ⚪ Optional - Initial vote value

---

### `ado_set_pr_vote`

Set vote on a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`reviewerId`** (string) 🔴 Required - Reviewer GUID
- **`vote`** (string) 🔴 Required - Vote value (10, 5, 0, -5, -10)

---

### `ado_update_pr`

Update pull request properties.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required
- **`title`** (string) ⚪ Optional
- **`description`** (string) ⚪ Optional
- **`status`** (string) ⚪ Optional - 'active' or 'abandoned'

---

### `ado_get_pr_work_items`

Get work items linked to a pull request.

**Parameters:**

- **`repository`** (string) 🔴 Required
- **`pullRequestId`** (string) 🔴 Required

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

---

### `ado_download_attachment`

Download an ADO work item attachment by URL and save it as a file

**Parameters:**

- **`href`** (string) 🔴 Required
  - The attachment URL to download

---

### `ado_get_changelog`

Get the complete history/changelog of a work item

**Parameters:**

- **`ticket`** (object) ⚪ Optional
- **`id`** (string) 🔴 Required

---

### `ado_get_comments`

Get all comments for a work item

**Parameters:**

- **`ticket`** (object) 🔴 Required
- **`id`** (string) 🔴 Required

---

### `ado_get_my_profile`

Get the current user's profile information from Azure DevOps

**Parameters:** None

---

### `ado_get_user_by_email`

Get user information by email address in Azure DevOps

**Parameters:**

- **`email`** (string) 🔴 Required

---

### `ado_get_work_item`

Get a specific Azure DevOps work item by ID with optional field filtering

**Parameters:**

- **`fields`** (array) ⚪ Optional
- **`id`** (string) 🔴 Required
  - Example: `12345`

---

### `ado_link_work_items`

Link two work items with a relationship

**Parameters:**

- **`sourceId`** (string) 🔴 Required
- **`targetId`** (string) 🔴 Required
- **`relationship`** (string) 🔴 Required
  - Example: `parent`

---

### `ado_move_to_state`

Move a work item to a specific state

**Parameters:**

- **`id`** (string) 🔴 Required
- **`state`** (string) 🔴 Required
  - Example: `Active`

---

### `ado_post_comment`

Post a comment to a work item

**Parameters:**

- **`comment`** (string) 🔴 Required
- **`id`** (string) 🔴 Required

---

### `ado_search_by_wiql`

Search for work items using WIQL (Work Item Query Language)

**Parameters:**

- **`fields`** (array) ⚪ Optional
- **`wiql`** (string) 🔴 Required
  - Example: `SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] = 'Bug'`

---

### `ado_update_description`

Update the description of a work item

**Parameters:**

- **`description`** (string) 🔴 Required
- **`id`** (string) 🔴 Required

---

### `ado_update_tags`

Update the tags of a work item (semicolon-separated string)

**Parameters:**

- **`id`** (string) 🔴 Required
- **`tags`** (string) 🔴 Required

---

