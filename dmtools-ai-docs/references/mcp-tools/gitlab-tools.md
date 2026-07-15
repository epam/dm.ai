# GITLAB MCP Tools

**Total Tools**: 28

## Quick Reference

```bash
# List all gitlab tools
dmtools list | jq '.tools[] | select(.name | startswith("gitlab_"))'

# Example usage
dmtools gitlab_get_mr_comments [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for gitlab tools
const result = gitlab_get_mr_comments(...);
const result = gitlab_add_mr_label(...);
const result = gitlab_remove_mr_label(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `gitlab_add_inline_mr_comment` | Create a new inline code review comment on a specific file and line in a GitLab merge request. Requires base_sha, head_sha, start_sha from the MR diff refs (use gitlab_get_mr to get them from diff_refs). | `workspace` (string, **required**)<br>`line` (string, **required**)<br>`startSha` (string, **required**)<br>`filePath` (string, **required**)<br>`baseSha` (string, **required**)<br>`text` (string, **required**)<br>`headSha` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `gitlab_add_mr_comment` | Add a general discussion comment to a GitLab merge request. | `workspace` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `gitlab_add_mr_label` | Add a label to a GitLab merge request. | `workspace` (string, **required**)<br>`label` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `gitlab_approve_mr` | Approve a GitLab merge request. Adds your approval to the MR. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_cancel_job` | Cancel a GitLab CI job. | `repository` (string, **required**)<br>`jobId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_create_mr` | Create a GitLab merge request from a source branch into a target branch. | `removeSourceBranch` (string, optional)<br>`workspace` (string, **required**)<br>`targetBranch` (string, **required**)<br>`sourceBranch` (string, **required**)<br>`description` (string, optional)<br>`repository` (string, **required**)<br>`title` (string, **required**) |
| `gitlab_delete_release_asset` | Delete a GitLab release asset by its asset name. Removes both the release link and the underlying Generic Package Registry file. Use gitlab_list_release_assets to find asset names. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`assetName` (string, **required**)<br>`packageName` (string, optional) |
| `gitlab_download_release_asset` | Download a GitLab release asset (a file published to the Generic Package Registry and attached to a release) to a local file path. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`assetName` (string, **required**)<br>`targetFilePath` (string, **required**)<br>`packageName` (string, optional) |
| `gitlab_get_job_logs` | Get GitLab CI job trace logs. | `repository` (string, **required**)<br>`jobId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr` | Get details of a specific GitLab merge request including title, description, state, author, diff_refs (base_sha, head_sha, start_sha needed for inline comments). | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr_activities` | Get all activities for a GitLab merge request including approvals and general discussion notes. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr_comments` | Get all comments for a GitLab merge request, including both inline code review comments (DiffNote) and general discussion notes. Excludes system-generated notes. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr_diff` | Get diff stats and changed files for a GitLab merge request. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr_diff_text` | Get the raw unified diff text for a GitLab merge request (suitable for locating file/line positions, e.g. for inline review comments). | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_mr_discussions` | Get all discussion threads for a GitLab merge request. Each discussion contains notes (comments) and a resolved status. Use the discussion id with gitlab_resolve_mr_thread. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_get_or_create_release` | Find an existing GitLab release by tag, or create one if it does not exist. Useful for a stable artefact storage release (mirrors github_get_or_create_draft_release). Note: GitLab releases have no draft concept; releases are visible as soon as they are created. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`releaseName` (string, optional)<br>`targetCommitish` (string, optional)<br>`body` (string, optional) |
| `gitlab_get_pipeline_jobs` | List jobs for a GitLab CI pipeline. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`pipelineId` (string, **required**) |
| `gitlab_list_mrs` | List merge requests for a GitLab project. State can be 'opened', 'closed', 'merged', or 'all'. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`state` (string, **required**) |
| `gitlab_list_pipeline_runs` | List recent GitLab CI pipelines. Optionally filter by status, ref, and limit. | `limit` (string, optional)<br>`workspace` (string, **required**)<br>`ref` (string, optional)<br>`repository` (string, **required**)<br>`status` (string, optional) |
| `gitlab_list_project_jobs` | List recent GitLab CI jobs for a project. | `repository` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_list_release_assets` | List all assets (asset links) attached to a GitLab release. Returns a JSON array of link objects including id, name, url, and direct_asset_url. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**) |
| `gitlab_merge_mr` | Merge a GitLab merge request. Optionally provide a custom merge commit message. | `workspace` (string, **required**)<br>`mergeCommitMessage` (string, optional)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `gitlab_rebase_mr` | Ask GitLab to rebase/update a merge request source branch with its target branch. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `gitlab_remove_mr_label` | Remove a label from a GitLab merge request. | `workspace` (string, **required**)<br>`label` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `gitlab_reply_to_mr_thread` | Reply to an existing discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions. | `workspace` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`discussionId` (string, **required**) |
| `gitlab_resolve_mr_thread` | Resolve (close) a review discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`discussionId` (string, **required**) |
| `gitlab_trigger_pipeline` | Trigger a GitLab CI pipeline for a branch or tag using the authenticated API token. | `variablesJson` (string, optional)<br>`workspace` (string, **required**)<br>`ref` (string, **required**)<br>`repository` (string, **required**) |
| `gitlab_upload_release_asset` | Upload a local file as a GitLab release asset. Internally publishes the file to the project's Generic Package Registry and attaches it to the release as an asset link, so it is discoverable the same way as a GitHub release asset. Returns the release link metadata including direct_asset_url. Set overwrite=true to automatically replace an existing asset with the same name before uploading (GitLab's Generic Package Registry rejects duplicate uploads with 409 Conflict otherwise). | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`filePath` (string, **required**)<br>`assetName` (string, optional)<br>`contentType` (string, optional)<br>`packageName` (string, optional)<br>`overwrite` (string, optional) |

## Detailed Parameter Information

### `gitlab_add_inline_mr_comment`

Create a new inline code review comment on a specific file and line in a GitLab merge request. Requires base_sha, head_sha, start_sha from the MR diff refs (use gitlab_get_mr to get them from diff_refs).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`line`** (string) 🔴 Required
  - Line number in the new file to comment on
  - Example: `42`

- **`startSha`** (string) 🔴 Required
  - Start commit SHA from MR diff_refs
  - Example: `abc123`

- **`filePath`** (string) 🔴 Required
  - Path to the file to comment on
  - Example: `src/main/Foo.java`

- **`baseSha`** (string) 🔴 Required
  - Base commit SHA from MR diff_refs
  - Example: `abc123`

- **`text`** (string) 🔴 Required
  - Comment text
  - Example: `This looks wrong`

- **`headSha`** (string) 🔴 Required
  - Head commit SHA from MR diff_refs
  - Example: `def456`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

**Example:**
```bash
dmtools gitlab_add_inline_mr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_add_inline_mr_comment("workspace", "line");
```

---

### `gitlab_add_mr_comment`

Add a general discussion comment to a GitLab merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`text`** (string) 🔴 Required
  - Comment text
  - Example: `LGTM!`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

**Example:**
```bash
dmtools gitlab_add_mr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_add_mr_comment("workspace", "text");
```

---

### `gitlab_add_mr_label`

Add a label to a GitLab merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`label`** (string) 🔴 Required
  - Label to add
  - Example: `pr_approved`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

**Example:**
```bash
dmtools gitlab_add_mr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_add_mr_label("workspace", "label");
```

---

### `gitlab_approve_mr`

Approve a GitLab merge request. Adds your approval to the MR.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_approve_mr "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_approve_mr("repository", "pullRequestId");
```

---

### `gitlab_cancel_job`

Cancel a GitLab CI job.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`jobId`** (string) 🔴 Required
  - GitLab job ID
  - Example: `123456`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_cancel_job "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_cancel_job("repository", "jobId");
```

---

### `gitlab_create_mr`

Create a GitLab merge request from a source branch into a target branch.

**Parameters:**

- **`removeSourceBranch`** (string) ⚪ Optional
  - Remove source branch after merge
  - Example: `true`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`targetBranch`** (string) 🔴 Required
  - Target branch name
  - Example: `main`

- **`sourceBranch`** (string) 🔴 Required
  - Source branch name
  - Example: `feature/PROJ-123`

- **`description`** (string) ⚪ Optional
  - Merge request description
  - Example: `Automated changes`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`title`** (string) 🔴 Required
  - Merge request title
  - Example: `PROJ-123 Implement feature`

**Example:**
```bash
dmtools gitlab_create_mr "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_create_mr("removeSourceBranch", "workspace");
```

---

### `gitlab_get_job_logs`

Get GitLab CI job trace logs.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`jobId`** (string) 🔴 Required
  - GitLab job ID
  - Example: `123456`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_job_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_job_logs("repository", "jobId");
```

---

### `gitlab_get_mr`

Get details of a specific GitLab merge request including title, description, state, author, diff_refs (base_sha, head_sha, start_sha needed for inline comments).

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr("repository", "pullRequestId");
```

---

### `gitlab_get_mr_activities`

Get all activities for a GitLab merge request including approvals and general discussion notes.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr_activities "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr_activities("repository", "pullRequestId");
```

---

### `gitlab_get_mr_comments`

Get all comments for a GitLab merge request, including both inline code review comments (DiffNote) and general discussion notes. Excludes system-generated notes.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr_comments "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr_comments("repository", "pullRequestId");
```

---

### `gitlab_get_mr_diff`

Get diff stats and changed files for a GitLab merge request.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr_diff "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr_diff("repository", "pullRequestId");
```

---

### `gitlab_get_mr_diff_text`

Get the raw unified diff text for a GitLab merge request (suitable for locating file/line positions, e.g. for inline review comments).

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr_diff_text "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr_diff_text("repository", "pullRequestId");
```

---

### `gitlab_get_mr_discussions`

Get all discussion threads for a GitLab merge request. Each discussion contains notes (comments) and a resolved status. Use the discussion id with gitlab_resolve_mr_thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_get_mr_discussions "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_mr_discussions("repository", "pullRequestId");
```

---

### `gitlab_get_pipeline_jobs`

List jobs for a GitLab CI pipeline.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`pipelineId`** (string) 🔴 Required
  - GitLab pipeline ID
  - Example: `123456`

**Example:**
```bash
dmtools gitlab_get_pipeline_jobs "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_pipeline_jobs("repository", "workspace");
```

---

### `gitlab_list_mrs`

List merge requests for a GitLab project. State can be 'opened', 'closed', 'merged', or 'all'.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`state`** (string) 🔴 Required
  - MR state: opened, closed, merged, all. 'open' is also accepted as a synonym for 'opened'.
  - Example: `opened`

**Example:**
```bash
dmtools gitlab_list_mrs "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_list_mrs("repository", "workspace");
```

---

### `gitlab_list_pipeline_runs`

List recent GitLab CI pipelines. Optionally filter by status, ref, and limit.

**Parameters:**

- **`limit`** (string) ⚪ Optional
  - Maximum number of pipelines to return
  - Example: `50`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`ref`** (string) ⚪ Optional
  - Branch or tag ref
  - Example: `main`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`status`** (string) ⚪ Optional
  - Pipeline status filter
  - Example: `failed`

**Example:**
```bash
dmtools gitlab_list_pipeline_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_list_pipeline_runs("limit", "workspace");
```

---

### `gitlab_list_project_jobs`

List recent GitLab CI jobs for a project.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_list_project_jobs "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_list_project_jobs("repository", "workspace");
```

---

### `gitlab_merge_mr`

Merge a GitLab merge request. Optionally provide a custom merge commit message.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`mergeCommitMessage`** (string) ⚪ Optional
  - Optional custom merge commit message
  - Example: `Merge feature branch`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

**Example:**
```bash
dmtools gitlab_merge_mr "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_merge_mr("workspace", "mergeCommitMessage");
```

---

### `gitlab_rebase_mr`

Ask GitLab to rebase/update a merge request source branch with its target branch.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

**Example:**
```bash
dmtools gitlab_rebase_mr "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_rebase_mr("repository", "pullRequestId");
```

---

### `gitlab_remove_mr_label`

Remove a label from a GitLab merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`label`** (string) 🔴 Required
  - Label to remove
  - Example: `pr_approved`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

**Example:**
```bash
dmtools gitlab_remove_mr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_remove_mr_label("workspace", "label");
```

---

### `gitlab_reply_to_mr_thread`

Reply to an existing discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`text`** (string) 🔴 Required
  - Reply text
  - Example: `Addressed in latest commit`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`discussionId`** (string) 🔴 Required
  - Discussion thread ID
  - Example: `6a9c1750b37d57bba1079be3bbd13a...`

**Example:**
```bash
dmtools gitlab_reply_to_mr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_reply_to_mr_thread("workspace", "text");
```

---

### `gitlab_resolve_mr_thread`

Resolve (close) a review discussion thread in a GitLab merge request. Use the discussion id from gitlab_get_mr_discussions.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`pullRequestId`** (string) 🔴 Required
  - Merge request IID
  - Example: `42`

- **`discussionId`** (string) 🔴 Required
  - Discussion thread ID to resolve
  - Example: `6a9c1750b37d57bba1079be3bbd13a...`

**Example:**
```bash
dmtools gitlab_resolve_mr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_resolve_mr_thread("workspace", "repository");
```

---

### `gitlab_trigger_pipeline`

Trigger a GitLab CI pipeline for a branch or tag using the authenticated API token.

**Parameters:**

- **`variablesJson`** (string) ⚪ Optional
  - Optional JSON object of CI variables
  - Example: `{"CONFIG_FILE":"agents/story_development.json"}`

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`ref`** (string) 🔴 Required
  - Branch or tag ref
  - Example: `main`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

**Example:**
```bash
dmtools gitlab_trigger_pipeline "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_trigger_pipeline("variablesJson", "workspace");
```

---


### `gitlab_get_or_create_release`

Find an existing GitLab release by tag, or create one if it does not exist. Useful for a stable artefact storage release (mirrors github_get_or_create_draft_release). Note: GitLab releases have no draft concept; releases are visible as soon as they are created.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`tagName`** (string) 🔴 Required
  - The Git tag name for the release. Reused to find an existing release.
  - Example: `pr-attachments-storage`

- **`releaseName`** (string) ⚪ Optional
  - The human-readable release name. If empty, tagName is used.
  - Example: `PR Attachments Storage`

- **`targetCommitish`** (string) ⚪ Optional
  - Optional branch or commit SHA the release's tag should point to when created (GitLab 'ref'). Required if the tag does not already exist.
  - Example: `main`

- **`body`** (string) ⚪ Optional
  - Optional Markdown release notes/description.
  - Example: `Internal storage release for PR attachments.`

**Example:**
```bash
dmtools gitlab_get_or_create_release "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_get_or_create_release("workspace", "repository", "tagName");
```

---

### `gitlab_upload_release_asset`

Upload a local file as a GitLab release asset. Internally publishes the file to the project's Generic Package Registry and attaches it to the release as an asset link, so it is discoverable the same way as a GitHub release asset. Returns the release link metadata including direct_asset_url. Set overwrite=true to automatically replace an existing asset with the same name before uploading (GitLab's Generic Package Registry rejects duplicate uploads with 409 Conflict otherwise).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`tagName`** (string) 🔴 Required
  - The tag name of the release returned by gitlab_get_or_create_release.
  - Example: `pr-attachments-storage`

- **`filePath`** (string) 🔴 Required
  - Absolute or relative path to the local file to upload.
  - Example: `/tmp/preview.png`

- **`assetName`** (string) ⚪ Optional
  - Optional asset filename shown in GitLab. Defaults to the local filename.
  - Example: `clip_123.png`

- **`contentType`** (string) ⚪ Optional
  - Optional MIME type. Defaults to detected type or application/octet-stream.
  - Example: `image/png`

- **`packageName`** (string) ⚪ Optional
  - Optional Generic Package Registry package name used to group the uploaded files. Defaults to 'release-assets'.
  - Example: `release-assets`

- **`overwrite`** (string) ⚪ Optional
  - If true, delete any existing asset (release link + underlying generic package file) with the same name before uploading. Defaults to false.
  - Example: `true`

**Example:**
```bash
dmtools gitlab_upload_release_asset "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_upload_release_asset("workspace", "repository", "tagName");
```

---

### `gitlab_list_release_assets`

List all assets (asset links) attached to a GitLab release. Returns a JSON array of link objects including id, name, url, and direct_asset_url.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`tagName`** (string) 🔴 Required
  - The tag name of the release.
  - Example: `pr-attachments-storage`

**Example:**
```bash
dmtools gitlab_list_release_assets "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_list_release_assets("workspace", "repository", "tagName");
```

---

### `gitlab_delete_release_asset`

Delete a GitLab release asset by its asset name. Removes both the release link and the underlying Generic Package Registry file. Use gitlab_list_release_assets to find asset names.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`tagName`** (string) 🔴 Required
  - The tag name of the release.
  - Example: `pr-attachments-storage`

- **`assetName`** (string) 🔴 Required
  - The name of the asset to delete, as returned by gitlab_list_release_assets.
  - Example: `clip_123.png`

- **`packageName`** (string) ⚪ Optional
  - Optional Generic Package Registry package name the asset was uploaded under. Defaults to 'release-assets'.
  - Example: `release-assets`

**Example:**
```bash
dmtools gitlab_delete_release_asset "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_delete_release_asset("workspace", "repository", "tagName");
```

---

### `gitlab_download_release_asset`

Download a GitLab release asset (a file published to the Generic Package Registry and attached to a release) to a local file path.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - GitLab group or namespace
  - Example: `mygroup`

- **`repository`** (string) 🔴 Required
  - Repository name
  - Example: `myrepo`

- **`tagName`** (string) 🔴 Required
  - The tag name of the release.
  - Example: `pr-attachments-storage`

- **`assetName`** (string) 🔴 Required
  - The name of the asset to download, as returned by gitlab_list_release_assets.
  - Example: `clip_123.png`

- **`targetFilePath`** (string) 🔴 Required
  - Local file path where the downloaded asset should be saved.
  - Example: `/tmp/downloaded_clip_123.png`

- **`packageName`** (string) ⚪ Optional
  - Optional Generic Package Registry package name the asset was uploaded under. Defaults to 'release-assets'.
  - Example: `release-assets`

**Example:**
```bash
dmtools gitlab_download_release_asset "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = gitlab_download_release_asset("workspace", "repository", "tagName");
```

---
