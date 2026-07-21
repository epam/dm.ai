# GITHUB MCP Tools

**Total Tools**: 35

## Quick Reference

```bash
# List all github tools
dmtools list | jq '.tools[] | select(.name | startswith("github_"))'

# Example usage
dmtools github_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for github tools
const result = github_test(...);
const result = github_list_prs(...);
const result = github_list_prs_filtered(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `github_add_inline_comment` | Create a new inline code review comment on a specific file and line in a GitHub pull request. To comment on a range of lines, provide both startLine and line. Side is 'RIGHT' for new code (default) or 'LEFT' for old code. | `path` (string, **required**)<br>`workspace` (string, **required**)<br>`side` (string, optional)<br>`line` (string, **required**)<br>`startLine` (string, optional)<br>`text` (string, **required**)<br>`commitId` (string, optional)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_add_pr_comment` | Add a comment to a GitHub pull request discussion. | `workspace` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_add_pr_label` | Add a label to a GitHub pull request. | `workspace` (string, **required**)<br>`label` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_create_check_run` | Create a GitHub Check Run — a rich CI check with progress, annotations, and a full log visible in the PR 'Checks' tab. Use status=in_progress when starting, then call github_update_check_run to complete it. | `summary` (string, optional)<br>`workspace` (string, **required**)<br>`name` (string, **required**)<br>`externalId` (string, optional)<br>`headSha` (string, **required**)<br>`text` (string, optional)<br>`repository` (string, **required**)<br>`title` (string, optional)<br>`status` (string, optional) |
| `github_create_commit_status` | Create a commit status (the colored dot in PR checks). Use state=pending when AI analysis starts, success/failure/error when complete. The 'context' field acts as the status name and must be unique per check. | `workspace` (string, **required**)<br>`context` (string, optional)<br>`description` (string, optional)<br>`state` (string, **required**)<br>`repository` (string, **required**)<br>`sha` (string, **required**)<br>`targetUrl` (string, optional) |
| `github_delete_pr_comment` | Delete a comment on a GitHub pull request or issue by its comment ID. | `repository` (string, **required**)<br>`commentId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_delete_release_asset` | Delete a GitHub release asset by its asset ID. Use github_list_release_assets to find asset IDs. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`assetId` (string, **required**) |
| `github_get_commit_check_runs` | Get all check runs (CI/CD status checks) for a commit SHA in a GitHub repository. Returns details about each check including status, conclusion, and output. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`commitSha` (string, **required**) |
| `github_get_commits_from_branches` | Fetch commits from all branches whose name matches a given regex pattern, aggregated and de-duplicated. Useful for collecting commits from feature/*, release/* or similar groups of branches without specifying each branch individually. | `branchNameRegex` (string, **required**)<br>`workspace` (string, **required**)<br>`repository` (string, **required**)<br>`since` (string, optional) |
| `github_get_job_logs` | Get the raw text logs for a specific GitHub Actions job. Returns the complete log output from all steps in the job. | `repository` (string, **required**)<br>`jobId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_or_create_draft_release` | Find an existing draft release by tag or name, or create one if it does not exist. Useful for a stable PR attachment storage release. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`targetCommitish` (string, optional)<br>`body` (string, optional)<br>`releaseName` (string, optional) |
| `github_get_pr` | Get details of a GitHub pull request including title, description, status, author, branches, and merge info. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_activities` | Get all activities for a GitHub pull request including reviews (approvals, change requests), inline code comments, and general discussion comments. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_comments` | Get all comments for a GitHub pull request, including both inline code review comments and general discussion comments. Results are sorted by creation date. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_conversations` | Get all review conversations (inline code comment threads) for a GitHub pull request. Groups inline code review comments into threads showing root comment and replies. Also includes general PR discussion comments as separate entries. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_diff` | Get the diff statistics for a GitHub pull request (files changed, additions, deletions). Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled. | `repository` (string, **required**)<br>`pullRequestID` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_diff_text` | Get the raw unified diff text for a GitHub pull request. Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled. | `repository` (string, **required**)<br>`pullRequestID` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_pr_review_threads` | Get all review threads for a GitHub pull request via GraphQL, including each thread's node ID (needed for resolving), resolved status, file path, line, and comments. Use the returned thread 'id' with github_resolve_pr_thread. | `repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_get_workflow_run` | Get details of a specific GitHub Actions workflow run by ID. Returns status, conclusion, logs URL, and timing information. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`runId` (string, **required**) |
| `github_get_workflow_run_jobs` | Get all jobs for a specific GitHub Actions workflow run. Shows individual job statuses, steps, and logs URLs. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`runId` (string, **required**) |
| `github_get_workflow_run_logs` | Download and extract complete logs for all jobs in a GitHub Actions workflow run. Returns full untruncated log content from the ZIP archive GitHub provides. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`runId` (string, **required**) |
| `github_list_prs` | List pull requests in a GitHub repository by state. State can be 'open', 'closed', or 'merged'. Returns first page (up to 100) of pull requests. | `repository` (string, **required**)<br>`workspace` (string, **required**)<br>`state` (string, **required**) |
| `github_list_prs_filtered` | List pull requests in a GitHub repository filtered by a regex pattern on the PR title. Fetches all PRs matching the given state and returns only those whose title matches the regex. Useful for large repos to narrow down results without loading entire history. | `titleRegex` (string, **required**)<br>`workspace` (string, **required**)<br>`state` (string, **required**)<br>`repository` (string, **required**) |
| `github_list_release_assets` | List all assets attached to a GitHub release. Returns a JSON array of asset objects including id, name, size, and browser_download_url. | `repository` (string, **required**)<br>`releaseId` (string, **required**)<br>`workspace` (string, **required**) |
| `github_list_workflow_runs` | List GitHub Actions workflow runs for a repository, optionally filtered by status or specific workflow file. Use status='failure' to get all failed runs. | `workspace` (string, **required**)<br>`perPage` (number, optional)<br>`created` (string, optional)<br>`page` (number, optional)<br>`repository` (string, **required**)<br>`workflowId` (string, optional)<br>`status` (string, optional) |
| `github_merge_pr` | Merge a GitHub pull request. Supports merge, squash, and rebase merge methods. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`commitMessage` (string, optional)<br>`mergeMethod` (string, optional)<br>`commitTitle` (string, optional) |
| `github_remove_pr_label` | Remove a label from a GitHub pull request. | `workspace` (string, **required**)<br>`label` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_reply_to_pr_thread` | Reply to an existing inline code review comment thread in a GitHub pull request. Use the comment ID of the root comment (or any comment) in the thread as inReplyToId. | `workspace` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`inReplyToId` (string, **required**) |
| `github_repository_dispatch` | Trigger a GitHub repository dispatch event. Workflows listening to 'on: repository_dispatch' with the matching event_type will be triggered. | `workspace` (string, **required**)<br>`eventType` (string, **required**)<br>`clientPayload` (string, optional)<br>`repository` (string, **required**) |
| `github_resolve_pr_thread` | Resolve a review thread in a GitHub pull request. Requires the thread's GraphQL node ID, which can be obtained from github_get_pr_review_threads (the 'id' field of each thread). | `threadId` (string, **required**) |
| `github_test` | Test GitHub connectivity by fetching the current user's profile | None |
| `github_trigger_workflow` | Trigger a specific GitHub Actions workflow by filename (workflow dispatch). The workflow must have 'on: workflow_dispatch' configured. | `workspace` (string, **required**)<br>`ref` (string, optional)<br>`repository` (string, **required**)<br>`workflowId` (string, **required**)<br>`inputs` (string, optional) |
| `github_update_check_run` | Update an existing GitHub Check Run — set it to completed with success/failure conclusion, update summary and detailed text. Call this after github_create_check_run to finalize the check. | `conclusion` (string, optional)<br>`summary` (string, optional)<br>`workspace` (string, **required**)<br>`checkRunId` (string, **required**)<br>`text` (string, optional)<br>`repository` (string, **required**)<br>`title` (string, optional)<br>`status` (string, **required**) |
| `github_update_pr_comment` | Update (edit) an existing comment on a GitHub pull request or issue by its comment ID. | `commentId` (string, **required**)<br>`workspace` (string, **required**)<br>`text` (string, **required**)<br>`repository` (string, **required**) |
| `github_upload_release_asset` | Upload a local file as a GitHub release asset. Returns the uploaded asset metadata including browser_download_url. Set overwrite=true to automatically delete an existing asset with the same name before uploading. | `workspace` (string, **required**)<br>`releaseId` (string, **required**)<br>`filePath` (string, **required**)<br>`assetName` (string, optional)<br>`label` (string, optional)<br>`repository` (string, **required**)<br>`contentType` (string, optional)<br>`overwrite` (string, optional) |

## Detailed Parameter Information

### `github_add_inline_comment`

Create a new inline code review comment on a specific file and line in a GitHub pull request. To comment on a range of lines, provide both startLine and line. Side is 'RIGHT' for new code (default) or 'LEFT' for old code.

**Parameters:**

- **`path`** (string) 🔴 Required
  - The relative file path in the repository
  - Example: `src/main/java/com/example/Foo.java`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`side`** (string) ⚪ Optional
  - Which diff side to comment on: RIGHT (new code, default) or LEFT (old code)
  - Example: `RIGHT`

- **`line`** (string) 🔴 Required
  - The line number in the file to comment on
  - Example: `42`

- **`startLine`** (string) ⚪ Optional
  - For multi-line comments: the first line of the range. Must be less than line.
  - Example: `40`

- **`text`** (string) 🔴 Required
  - The comment text (Markdown supported)
  - Example: `This should be refactored.`

- **`commitId`** (string) ⚪ Optional
  - The SHA of the commit to comment on. If empty, uses the PR head commit.
  - Example: `abc123def456`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_add_inline_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_inline_comment("path", "workspace");
```

---

### `github_add_pr_comment`

Add a comment to a GitHub pull request discussion.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`text`** (string) 🔴 Required
  - The comment text to add
  - Example: `Looks good!`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_add_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_pr_comment("workspace", "text");
```

---

### `github_add_pr_label`

Add a label to a GitHub pull request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`label`** (string) 🔴 Required
  - The label name to add to the pull request
  - Example: `bug`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_add_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_pr_label("workspace", "label");
```

---

### `github_create_check_run`

Create a GitHub Check Run — a rich CI check with progress, annotations, and a full log visible in the PR 'Checks' tab. Use status=in_progress when starting, then call github_update_check_run to complete it.

**Parameters:**

- **`summary`** (string) ⚪ Optional
  - Markdown summary shown in the check run output panel
  - Example: `🔍 Analysis started...`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`name`** (string) 🔴 Required
  - The name of the check run displayed in the PR
  - Example: `dmtools / pr-review`

- **`externalId`** (string) ⚪ Optional
  - Optional external identifier for this check run
  - Example: `MAPC-6653`

- **`headSha`** (string) 🔴 Required
  - The SHA of the commit to associate this check run with
  - Example: `abc123def456...`

- **`text`** (string) ⚪ Optional
  - Additional details in Markdown (supports large content)
  - Example: `Full analysis results here...`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`title`** (string) ⚪ Optional
  - Title shown in the check run output panel
  - Example: `AI PR Review`

- **`status`** (string) ⚪ Optional
  - The status: queued | in_progress | completed
  - Example: `in_progress`

**Example:**
```bash
dmtools github_create_check_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_check_run("summary", "workspace");
```

---

### `github_create_commit_status`

Create a commit status (the colored dot in PR checks). Use state=pending when AI analysis starts, success/failure/error when complete. The 'context' field acts as the status name and must be unique per check.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`context`** (string) ⚪ Optional
  - Unique identifier for this status check, e.g. 'dmtools/pr-review'
  - Example: `dmtools/pr-review`

- **`description`** (string) ⚪ Optional
  - Short human-readable description shown next to the status dot
  - Example: `AI analysis in progress...`

- **`state`** (string) 🔴 Required
  - The state: pending | success | failure | error
  - Example: `pending`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`sha`** (string) 🔴 Required
  - The commit SHA to set status on
  - Example: `abc123def456...`

- **`targetUrl`** (string) ⚪ Optional
  - Optional URL to link from the status (e.g. CI run URL)
  - Example: `https://github.com/owner/repo/actions/runs/123`

**Example:**
```bash
dmtools github_create_commit_status "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_commit_status("workspace", "context");
```

---

### `github_delete_pr_comment`

Delete a comment on a GitHub pull request or issue by its comment ID.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to delete
  - Example: `123456789`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_delete_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_delete_pr_comment("repository", "commentId");
```

---

### `github_delete_release_asset`

Delete a GitHub release asset by its asset ID. Use github_list_release_assets to find asset IDs.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`assetId`** (string) 🔴 Required
  - The numeric asset ID to delete.
  - Example: `422721847`

**Example:**
```bash
dmtools github_delete_release_asset "value" "value"
```

```javascript
// In JavaScript agent
const result = github_delete_release_asset("repository", "workspace");
```

---

### `github_get_commit_check_runs`

Get all check runs (CI/CD status checks) for a commit SHA in a GitHub repository. Returns details about each check including status, conclusion, and output.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`commitSha`** (string) 🔴 Required
  - The commit SHA to get check runs for
  - Example: `abc123...`

**Example:**
```bash
dmtools github_get_commit_check_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_commit_check_runs("repository", "workspace");
```

---

### `github_get_commits_from_branches`

Fetch commits from all branches whose name matches a given regex pattern, aggregated and de-duplicated. Useful for collecting commits from feature/*, release/* or similar groups of branches without specifying each branch individually.

**Parameters:**

- **`branchNameRegex`** (string) 🔴 Required
  - Java regular expression matched against branch names. All branches with a matching name are included. Example: '^feature/' or 'release/\d+'.
  - Example: `^feature/`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`since`** (string) ⚪ Optional
  - Optional ISO date (yyyy-MM-dd) to limit commits to those after this date.
  - Example: `2024-01-01`

**Example:**
```bash
dmtools github_get_commits_from_branches "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_commits_from_branches("branchNameRegex", "workspace");
```

---

### `github_get_job_logs`

Get the raw text logs for a specific GitHub Actions job. Returns the complete log output from all steps in the job.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`jobId`** (string) 🔴 Required
  - The job ID (from github_get_workflow_run_jobs)
  - Example: `1234567890`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_job_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_job_logs("repository", "jobId");
```

---

### `github_get_or_create_draft_release`

Find an existing draft release by tag or name, or create one if it does not exist. Useful for a stable PR attachment storage release.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`tagName`** (string) 🔴 Required
  - The Git tag name for the release. Reused to find an existing draft release.
  - Example: `pr-attachments-storage`

- **`targetCommitish`** (string) ⚪ Optional
  - Optional branch or commit SHA the release should point to when created.
  - Example: `main`

- **`body`** (string) ⚪ Optional
  - Optional Markdown release notes/body.
  - Example: `Internal storage release for PR attachments.`

- **`releaseName`** (string) ⚪ Optional
  - The human-readable release name. If empty, tagName is used.
  - Example: `PR Attachments Storage`

**Example:**
```bash
dmtools github_get_or_create_draft_release "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_or_create_draft_release("workspace", "repository");
```

---

### `github_get_pr`

Get details of a GitHub pull request including title, description, status, author, branches, and merge info.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr("repository", "pullRequestId");
```

---

### `github_get_pr_activities`

Get all activities for a GitHub pull request including reviews (approvals, change requests), inline code comments, and general discussion comments.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_activities "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_activities("repository", "pullRequestId");
```

---

### `github_get_pr_comments`

Get all comments for a GitHub pull request, including both inline code review comments and general discussion comments. Results are sorted by creation date.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_comments "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_comments("repository", "pullRequestId");
```

---

### `github_get_pr_conversations`

Get all review conversations (inline code comment threads) for a GitHub pull request. Groups inline code review comments into threads showing root comment and replies. Also includes general PR discussion comments as separate entries.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_conversations "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_conversations("repository", "pullRequestId");
```

---

### `github_get_pr_diff`

Get the diff statistics for a GitHub pull request (files changed, additions, deletions). Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestID`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_diff "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_diff("repository", "pullRequestID");
```

---

### `github_get_pr_diff_text`

Get the raw unified diff text for a GitHub pull request. Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestID`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_diff_text "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_diff_text("repository", "pullRequestID");
```

---

### `github_get_pr_review_threads`

Get all review threads for a GitHub pull request via GraphQL, including each thread's node ID (needed for resolving), resolved status, file path, line, and comments. Use the returned thread 'id' with github_resolve_pr_thread.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_get_pr_review_threads "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_review_threads("repository", "pullRequestId");
```

---

### `github_get_workflow_run`

Get details of a specific GitHub Actions workflow run by ID. Returns status, conclusion, logs URL, and timing information.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `1234567890`

**Example:**
```bash
dmtools github_get_workflow_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run("repository", "workspace");
```

---

### `github_get_workflow_run_jobs`

Get all jobs for a specific GitHub Actions workflow run. Shows individual job statuses, steps, and logs URLs.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `1234567890`

**Example:**
```bash
dmtools github_get_workflow_run_jobs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run_jobs("repository", "workspace");
```

---

### `github_get_workflow_run_logs`

Download and extract complete logs for all jobs in a GitHub Actions workflow run. Returns full untruncated log content from the ZIP archive GitHub provides.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `22498697315`

**Example:**
```bash
dmtools github_get_workflow_run_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run_logs("repository", "workspace");
```

---

### `github_list_prs`

List pull requests in a GitHub repository by state. State can be 'open', 'closed', or 'merged'. Returns first page (up to 100) of pull requests.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`state`** (string) 🔴 Required
  - The state of pull requests to list: 'open', 'closed', or 'merged'. 'opened' is accepted as a synonym for 'open'.
  - Example: `open`

**Example:**
```bash
dmtools github_list_prs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_prs("repository", "workspace");
```

---

### `github_list_prs_filtered`

List pull requests in a GitHub repository filtered by a regex pattern on the PR title. Fetches all PRs matching the given state and returns only those whose title matches the regex. Useful for large repos to narrow down results without loading entire history.

**Parameters:**

- **`titleRegex`** (string) 🔴 Required
  - Java regular expression matched against the PR title (case-sensitive). Only PRs whose title contains a match are returned. Example: '^feat\(.*\)' or 'TICKET-\d+'.
  - Example: `^feat\(`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`state`** (string) 🔴 Required
  - The state of pull requests: 'open', 'closed', or 'merged'.
  - Example: `merged`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_list_prs_filtered "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_prs_filtered("titleRegex", "workspace");
```

---

### `github_list_release_assets`

List all assets attached to a GitHub release. Returns a JSON array of asset objects including id, name, size, and browser_download_url.

**Parameters:**

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`releaseId`** (string) 🔴 Required
  - The numeric GitHub release ID.
  - Example: `323096697`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

**Example:**
```bash
dmtools github_list_release_assets "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_release_assets("repository", "releaseId");
```

---

### `github_list_workflow_runs`

List GitHub Actions workflow runs for a repository, optionally filtered by status or specific workflow file. Use status='failure' to get all failed runs.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`perPage`** (number) ⚪ Optional
  - Number of results per page (max 100, default 30)
  - Example: `50`

- **`created`** (string) ⚪ Optional
  - Filter by created date/range using GitHub search syntax, e.g. 2026-05-01..2026-05-31 or >=2026-05-01
  - Example: `2026-05-01..2026-05-31`

- **`page`** (number) ⚪ Optional
  - Page number for pagination (default 1)
  - Example: `2`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workflowId`** (string) ⚪ Optional
  - Optional workflow filename to filter runs (e.g. rework.yml). If omitted, returns runs for all workflows.
  - Example: `rework.yml`

- **`status`** (string) ⚪ Optional
  - Filter by status: failure, success, in_progress, queued, cancelled, timed_out, action_required, neutral, skipped, stale, completed
  - Example: `failure`

**Example:**
```bash
dmtools github_list_workflow_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_workflow_runs("workspace", "perPage");
```

---

### `github_merge_pr`

Merge a GitHub pull request. Supports merge, squash, and rebase merge methods.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number to merge
  - Example: `74`

- **`commitMessage`** (string) ⚪ Optional
  - Extra detail to append to the merge commit message (optional)
  - Example: `Closes #123`

- **`mergeMethod`** (string) ⚪ Optional
  - The merge method: 'merge' (default), 'squash', or 'rebase'
  - Example: `squash`

- **`commitTitle`** (string) ⚪ Optional
  - Title for the merge commit (optional, defaults to PR title)
  - Example: `Merge feature/my-branch into main`

**Example:**
```bash
dmtools github_merge_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_merge_pr("workspace", "repository");
```

---

### `github_remove_pr_label`

Remove a label from a GitHub pull request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`label`** (string) 🔴 Required
  - The label name to remove from the pull request
  - Example: `bug`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_remove_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = github_remove_pr_label("workspace", "label");
```

---

### `github_reply_to_pr_thread`

Reply to an existing inline code review comment thread in a GitHub pull request. Use the comment ID of the root comment (or any comment) in the thread as inReplyToId.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`text`** (string) 🔴 Required
  - The reply text (Markdown supported)
  - Example: `Fixed in the latest commit.`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`inReplyToId`** (string) 🔴 Required
  - The ID of the comment to reply to (from github_get_pr_conversations rootComment.id or replies[].id)
  - Example: `123456789`

**Example:**
```bash
dmtools github_reply_to_pr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = github_reply_to_pr_thread("workspace", "text");
```

---

### `github_repository_dispatch`

Trigger a GitHub repository dispatch event. Workflows listening to 'on: repository_dispatch' with the matching event_type will be triggered.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`eventType`** (string) 🔴 Required
  - The type of activity that triggers the workflow (event_type)
  - Example: `rework`

- **`clientPayload`** (string) ⚪ Optional
  - Optional JSON string with payload passed to the workflow as client_payload
  - Example: `{"key":"value"}`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_repository_dispatch "value" "value"
```

```javascript
// In JavaScript agent
const result = github_repository_dispatch("workspace", "eventType");
```

---

### `github_resolve_pr_thread`

Resolve a review thread in a GitHub pull request. Requires the thread's GraphQL node ID, which can be obtained from github_get_pr_review_threads (the 'id' field of each thread).

**Parameters:**

- **`threadId`** (string) 🔴 Required
  - The GraphQL node ID of the review thread to resolve (from github_get_pr_review_threads thread.id)
  - Example: `PRRT_kwDOBQfyNc5A...`

**Example:**
```bash
dmtools github_resolve_pr_thread "value"
```

```javascript
// In JavaScript agent
const result = github_resolve_pr_thread("threadId");
```

---

### `github_test`

Test GitHub connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools github_test
```

```javascript
// In JavaScript agent
const result = github_test();
```

---

### `github_trigger_workflow`

Trigger a specific GitHub Actions workflow by filename (workflow dispatch). The workflow must have 'on: workflow_dispatch' configured.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`ref`** (string) ⚪ Optional
  - The branch or tag to run the workflow on (default: main)
  - Example: `main`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workflowId`** (string) 🔴 Required
  - The workflow filename or ID to trigger
  - Example: `rework.yml`

- **`inputs`** (string) ⚪ Optional
  - JSON string with workflow inputs (e.g. {"user_request":"...","branch":"main"})
  - Example: `{"user_request":"Please rework PROJ-123"}`

**Example:**
```bash
dmtools github_trigger_workflow "value" "value"
```

```javascript
// In JavaScript agent
const result = github_trigger_workflow("workspace", "ref");
```

---

### `github_update_check_run`

Update an existing GitHub Check Run — set it to completed with success/failure conclusion, update summary and detailed text. Call this after github_create_check_run to finalize the check.

**Parameters:**

- **`conclusion`** (string) ⚪ Optional
  - Required when status=completed: success | failure | neutral | cancelled | skipped | timed_out | action_required
  - Example: `success`

- **`summary`** (string) ⚪ Optional
  - Updated Markdown summary for the check run output panel
  - Example: `✅ Review complete. Found 3 issues.`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`checkRunId`** (string) 🔴 Required
  - The ID of the check run to update (from github_create_check_run response)
  - Example: `1234567890`

- **`text`** (string) ⚪ Optional
  - Updated detailed Markdown content (full analysis, annotations etc.)
  - Example: `## Issues Found
...`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`title`** (string) ⚪ Optional
  - Updated title for the check run output panel
  - Example: `AI PR Review — Complete`

- **`status`** (string) 🔴 Required
  - The new status: in_progress | completed
  - Example: `completed`

**Example:**
```bash
dmtools github_update_check_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_check_run("conclusion", "summary");
```

---

### `github_update_pr_comment`

Update (edit) an existing comment on a GitHub pull request or issue by its comment ID.

**Parameters:**

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to update
  - Example: `123456789`

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`text`** (string) 🔴 Required
  - The new comment text (replaces existing content)
  - Example: `✅ Analysis complete.`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_update_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_pr_comment("commentId", "workspace");
```

---

### `github_upload_release_asset`

Upload a local file as a GitHub release asset. Returns the uploaded asset metadata including browser_download_url. Set overwrite=true to automatically delete an existing asset with the same name before uploading.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`releaseId`** (string) 🔴 Required
  - The numeric GitHub release ID returned by github_get_or_create_draft_release.
  - Example: `323096697`

- **`filePath`** (string) 🔴 Required
  - Absolute or relative path to the local file to upload.
  - Example: `/tmp/preview.png`

- **`assetName`** (string) ⚪ Optional
  - Optional asset filename shown in GitHub. Defaults to the local filename.
  - Example: `clip_123.png`

- **`label`** (string) ⚪ Optional
  - Optional display label for the uploaded asset.
  - Example: `Screenshot`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`contentType`** (string) ⚪ Optional
  - Optional MIME type. Defaults to detected type or application/octet-stream.
  - Example: `image/png`

- **`overwrite`** (string) ⚪ Optional
  - If true, delete any existing asset with the same name before uploading. Defaults to false.
  - Example: `true`

**Example:**
```bash
dmtools github_upload_release_asset "value" "value"
```

```javascript
// In JavaScript agent
const result = github_upload_release_asset("workspace", "releaseId");
```

---

