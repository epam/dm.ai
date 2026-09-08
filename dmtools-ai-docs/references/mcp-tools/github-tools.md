# GITHUB MCP Tools

**Total Tools**: 77

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
| `github_add_collaborator` | Add a collaborator to a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`username` (string, **required**)<br>`permission` (string, **required**) |
| `github_add_inline_comment` | Create a new inline code review comment on a specific file and line in a GitHub pull request. To comment on a range of lines, provide both startLine and line. Side is 'RIGHT' for new code (default) or 'LEFT' for old code. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`path` (string, **required**)<br>`line` (string, **required**)<br>`text` (string, **required**)<br>`commitId` (string, optional)<br>`startLine` (string, optional)<br>`side` (string, optional) |
| `github_add_labels` | Add labels to a GitHub issue | `owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`labels` (array, **required**)<br>`key` (string, optional) |
| `github_add_pr_comment` | Add a comment to a GitHub pull request discussion. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`text` (string, **required**) |
| `github_add_pr_label` | Add a label to a GitHub pull request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`label` (string, **required**) |
| `github_assign_issue` | Assign a GitHub issue to a user | `user` (string, **required**)<br>`owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`key` (string, optional) |
| `github_close_issue` | Close a GitHub issue | `owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`key` (string, optional) |
| `github_close_pr` | Close a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**) |
| `github_create_branch` | Create a new branch from an existing commit SHA | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`branch` (string, **required**)<br>`from_sha` (string, **required**) |
| `github_create_check_run` | Create a GitHub Check Run — a rich CI check with progress, annotations, and a full log visible in the PR 'Checks' tab. Use status=in_progress when starting, then call github_update_check_run to complete it. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`name` (string, **required**)<br>`headSha` (string, **required**)<br>`status` (string, optional)<br>`title` (string, optional)<br>`summary` (string, optional)<br>`text` (string, optional)<br>`externalId` (string, optional) |
| `github_create_comment` | Create a comment on a GitHub issue or pull request (PRs are issues upstream). | `workspace` (string, optional)<br>`repository` (string, optional)<br>`pullRequestId` (string, optional)<br>`body` (string, **required**)<br>`key` (string, optional) |
| `github_create_commit_status` | Create a commit status (the colored dot in PR checks). Use state=pending when AI analysis starts, success/failure/error when complete. The 'context' field acts as the status name and must be unique per check. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`sha` (string, **required**)<br>`state` (string, **required**)<br>`description` (string, optional)<br>`context` (string, optional)<br>`targetUrl` (string, optional) |
| `github_create_issue` | Create a GitHub issue | `owner` (string, optional)<br>`repo` (string, optional)<br>`title` (string, **required**)<br>`body` (string, optional)<br>`key` (string, optional) |
| `github_create_pr` | Create a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`title` (string, **required**)<br>`head` (string, **required**)<br>`base` (string, **required**) |
| `github_create_release` | Create a GitHub release for a tag | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`tag_name` (string, **required**)<br>`body` (string, optional) |
| `github_create_review` | Create a review on a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**)<br>`body` (string, **required**)<br>`event` (string, **required**) |
| `github_delete_branch` | Delete a branch in a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`branch` (string, **required**) |
| `github_delete_pr_comment` | Delete a comment on a GitHub pull request or issue by its comment ID. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`commentId` (string, **required**) |
| `github_delete_release_asset` | Delete a GitHub release asset by its asset ID. Use github_list_release_assets to find asset IDs. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`assetId` (string, **required**) |
| `github_disable_workflow` | Disable a GitHub Actions workflow by id | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`workflow_id` (number, **required**) |
| `github_dismiss_pr_review` | Dismiss a previously submitted GitHub pull request review (e.g. clear a REQUEST_CHANGES decision once the issues have been fixed and a new review approves). Requires repository admin rights, or being listed as allowed to dismiss reviews, on protected branches. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`reviewId` (string, **required**)<br>`message` (string, **required**) |
| `github_dismiss_review` | Dismiss a review on a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**)<br>`review_id` (number, **required**)<br>`message` (string, **required**) |
| `github_enable_workflow` | Enable a GitHub Actions workflow by id | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`workflow_id` (number, **required**) |
| `github_get_check_runs` | List check runs for a GitHub commit ref | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`ref` (string, **required**) |
| `github_get_codeowners` | Get the CODEOWNERS file from a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_get_commit` | Get a GitHub commit by SHA | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`sha` (string, **required**) |
| `github_get_commit_check_runs` | Get all check runs (CI/CD status checks) for a commit SHA in a GitHub repository. Returns details about each check including status, conclusion, and output. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`commitSha` (string, **required**) |
| `github_get_commits_from_branches` | Fetch commits from all branches whose name matches a given regex pattern, aggregated and de-duplicated. Useful for collecting commits from feature/*, release/* or similar groups of branches without specifying each branch individually. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`branchNameRegex` (string, **required**)<br>`since` (string, optional) |
| `github_get_file_content` | Get the contents of a file in a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`path` (string, **required**)<br>`ref` (string, optional) |
| `github_get_issue` | Get details of a GitHub issue including title, description, state, author, labels, assignees, and comments count. | `workspace` (string, optional)<br>`repository` (string, optional)<br>`issueNumber` (string, optional)<br>`key` (string, optional) |
| `github_get_job_logs` | Get the raw text logs for a specific GitHub Actions job. Returns the complete log output from all steps in the job. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`jobId` (string, **required**) |
| `github_get_or_create_draft_release` | Find an existing draft release by tag or name, or create one if it does not exist. Useful for a stable PR attachment storage release. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`tagName` (string, **required**)<br>`releaseName` (string, optional)<br>`targetCommitish` (string, optional)<br>`body` (string, optional) |
| `github_get_pr` | Get details of a GitHub pull request including title, description, status, author, branches, and merge info. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_get_pr_activities` | Get all activities for a GitHub pull request including reviews (approvals, change requests), inline code comments, and general discussion comments. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_get_pr_comments` | Get all comments for a GitHub pull request, including both inline code review comments and general discussion comments. Results are sorted by creation date. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_get_pr_conversations` | Get all review conversations (inline code comment threads) for a GitHub pull request. Groups inline code review comments into threads showing root comment and replies. Also includes general PR discussion comments as separate entries. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_get_pr_diff` | Get the diff statistics for a GitHub pull request (files changed, additions, deletions). Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestID` (string, **required**) |
| `github_get_pr_diff_text` | Get the raw unified diff text for a GitHub pull request. Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestID` (string, **required**) |
| `github_get_pr_files` | List the files changed in a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**) |
| `github_get_pr_review_threads` | Get all review threads for a GitHub pull request via GraphQL, including each thread's node ID (needed for resolving), resolved status, file path, line, and comments. Use the returned thread 'id' with github_resolve_pr_thread. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_get_release` | Get a GitHub release by tag name | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`tag` (string, **required**) |
| `github_get_repo` | Get a GitHub repository by owner and name | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_get_tree` | Get a GitHub git tree recursively by ref | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`ref` (string, **required**) |
| `github_get_workflow_run` | Get details of a specific GitHub Actions workflow run by ID. Returns status, conclusion, logs URL, and timing information. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`runId` (string, **required**) |
| `github_get_workflow_run_jobs` | Get all jobs for a specific GitHub Actions workflow run. Shows individual job statuses, steps, and logs URLs. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`runId` (string, **required**) |
| `github_get_workflow_run_logs` | Download and extract complete logs for all jobs in a GitHub Actions workflow run. Returns full untruncated log content from the ZIP archive GitHub provides. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`runId` (string, **required**) |
| `github_get_workflow_runs` | List GitHub Actions workflow runs for a repository | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_get_workflows` | List GitHub Actions workflows in a repository | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_list_branches` | List branches in a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_list_commits` | List commits in a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`sha` (string, optional) |
| `github_list_pr_reviews` | List all formal reviews (APPROVE/REQUEST_CHANGES/COMMENT decisions submitted via github_submit_pr_review or by human reviewers) for a GitHub pull request, in chronological order. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**) |
| `github_list_prs` | List pull requests in a GitHub repository by state. State can be 'open', 'closed', or 'merged'. Returns first page (up to 100) of pull requests. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`state` (string, **required**) |
| `github_list_prs_filtered` | List pull requests in a GitHub repository filtered by a regex pattern on the PR title. Fetches all PRs matching the given state and returns only those whose title matches the regex. Useful for large repos to narrow down results without loading entire history. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`state` (string, **required**)<br>`titleRegex` (string, **required**) |
| `github_list_release_assets` | List all assets attached to a GitHub release. Returns a JSON array of asset objects including id, name, size, and browser_download_url. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`releaseId` (string, **required**) |
| `github_list_releases` | List GitHub releases for a repository | `owner` (string, **required**)<br>`repo` (string, **required**) |
| `github_list_workflow_runs` | List GitHub Actions workflow runs for a repository, optionally filtered by status or specific workflow file. Use status='failure' to get all failed runs. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`status` (string, optional)<br>`workflowId` (string, optional)<br>`perPage` (number, optional)<br>`page` (number, optional)<br>`created` (string, optional) |
| `github_merge_pr` | Merge a GitHub pull request. Supports merge, squash, and rebase merge methods. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`mergeMethod` (string, optional)<br>`commitTitle` (string, optional)<br>`commitMessage` (string, optional) |
| `github_move_issue_to_status` | Move a GitHub issue to a status. 'done'/'closed' close the issue, 'open'/'reopened' reopen it; any other status is applied as an issue label. | `statusName` (string, **required**)<br>`owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`key` (string, optional) |
| `github_remove_collaborator` | Remove a collaborator from a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`username` (string, **required**) |
| `github_remove_label` | Remove a label from a GitHub issue | `owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`label` (string, **required**)<br>`key` (string, optional) |
| `github_remove_pr_label` | Remove a label from a GitHub pull request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`label` (string, **required**) |
| `github_reopen_issue` | Reopen a closed GitHub issue | `owner` (string, optional)<br>`repo` (string, optional)<br>`number` (number, optional)<br>`key` (string, optional) |
| `github_reopen_pr` | Reopen a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**) |
| `github_reply_to_pr_thread` | Reply to an existing inline code review comment thread in a GitHub pull request. Use the comment ID of the root comment (or any comment) in the thread as inReplyToId. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`inReplyToId` (string, **required**)<br>`text` (string, **required**) |
| `github_repository_dispatch` | Trigger a GitHub repository dispatch event. Workflows listening to 'on: repository_dispatch' with the matching event_type will be triggered. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`eventType` (string, **required**)<br>`clientPayload` (string, optional) |
| `github_request_reviewers` | Request reviewers on a GitHub pull request | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**)<br>`reviewers` (array, **required**) |
| `github_rerun_workflow` | Re-run a GitHub Actions workflow run by id | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`run_id` (number, **required**) |
| `github_resolve_pr_thread` | Resolve a review thread in a GitHub pull request. Requires the thread's GraphQL node ID, which can be obtained from github_get_pr_review_threads (the 'id' field of each thread). | `threadId` (string, **required**) |
| `github_search_issues` | Search GitHub issues (and pull requests) with a query string. Returns a JSON object with 'items'. | `query` (string, **required**)<br>`workspace` (string, optional)<br>`repository` (string, optional) |
| `github_submit_pr_review` | Submit a formal GitHub pull request review (a native reviewer decision, distinct from labels/comments). event=APPROVE marks the PR as approved by this reviewer; event=REQUEST_CHANGES formally blocks the PR (visible as 'Changes requested', and enforced by branch protection rules requiring approvals) until a new review or github_dismiss_pr_review clears it; event=COMMENT leaves a review without approving or blocking. 'body' is required for REQUEST_CHANGES and COMMENT. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pullRequestId` (string, **required**)<br>`event` (string, **required**)<br>`body` (string, optional) |
| `github_test` | Test GitHub connectivity by fetching the current user's profile | None |
| `github_trigger_workflow` | Trigger a specific GitHub Actions workflow by filename (workflow dispatch). The workflow must have 'on: workflow_dispatch' configured. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`workflowId` (string, **required**)<br>`inputs` (string, optional)<br>`ref` (string, optional) |
| `github_update_check_run` | Update an existing GitHub Check Run — set it to completed with success/failure conclusion, update summary and detailed text. Call this after github_create_check_run to finalize the check. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`checkRunId` (string, **required**)<br>`status` (string, **required**)<br>`conclusion` (string, optional)<br>`title` (string, optional)<br>`summary` (string, optional)<br>`text` (string, optional) |
| `github_update_file` | Create or update a file in a GitHub repository | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`path` (string, **required**)<br>`content` (string, **required**)<br>`message` (string, **required**)<br>`sha` (string, **required**) |
| `github_update_pr` | Update the title or body of a GitHub pull request. Only the provided fields are changed. | `owner` (string, **required**)<br>`repo` (string, **required**)<br>`number` (number, **required**)<br>`title` (string, optional)<br>`body` (string, optional) |
| `github_update_pr_comment` | Update (edit) an existing comment on a GitHub pull request or issue by its comment ID. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`commentId` (string, **required**)<br>`text` (string, **required**) |
| `github_upload_release_asset` | Upload a local file as a GitHub release asset. Returns the uploaded asset metadata including browser_download_url. Set overwrite=true to automatically delete an existing asset with the same name before uploading. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`releaseId` (string, **required**)<br>`filePath` (string, **required**)<br>`assetName` (string, optional)<br>`contentType` (string, optional)<br>`label` (string, optional)<br>`overwrite` (string, optional) |

## Detailed Parameter Information

### `github_add_collaborator`

Add a collaborator to a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`username`** (string) 🔴 Required
  - The collaborator login to add
  - Example: `octocat`

- **`permission`** (string) 🔴 Required
  - The permission level: push, pull, admin, maintain, or triage
  - Example: `push`

**Example:**
```bash
dmtools github_add_collaborator "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_collaborator("owner", "repo");
```

---

### `github_add_inline_comment`

Create a new inline code review comment on a specific file and line in a GitHub pull request. To comment on a range of lines, provide both startLine and line. Side is 'RIGHT' for new code (default) or 'LEFT' for old code.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`path`** (string) 🔴 Required
  - The relative file path in the repository
  - Example: `src/main/java/com/example/Foo.java`

- **`line`** (string) 🔴 Required
  - The line number in the file to comment on
  - Example: `42`

- **`text`** (string) 🔴 Required
  - The comment text (Markdown supported)
  - Example: `This should be refactored.`

- **`commitId`** (string) ⚪ Optional
  - The SHA of the commit to comment on. If empty, uses the PR head commit.
  - Example: `abc123def456`

- **`startLine`** (string) ⚪ Optional
  - For multi-line comments: the first line of the range. Must be less than line.
  - Example: `40`

- **`side`** (string) ⚪ Optional
  - Which diff side to comment on: RIGHT (new code, default) or LEFT (old code)
  - Example: `RIGHT`

**Example:**
```bash
dmtools github_add_inline_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_inline_comment("workspace", "repository");
```

---

### `github_add_labels`

Add labels to a GitHub issue

**Parameters:**

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`labels`** (array) 🔴 Required
  - The label names to add
  - Example: `["bug","help wanted"]`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_add_labels "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_labels("owner", "repo");
```

---

### `github_add_pr_comment`

Add a comment to a GitHub pull request discussion.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`text`** (string) 🔴 Required
  - The comment text to add
  - Example: `Looks good!`

**Example:**
```bash
dmtools github_add_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_pr_comment("workspace", "repository");
```

---

### `github_add_pr_label`

Add a label to a GitHub pull request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`label`** (string) 🔴 Required
  - The label name to add to the pull request
  - Example: `bug`

**Example:**
```bash
dmtools github_add_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = github_add_pr_label("workspace", "repository");
```

---

### `github_assign_issue`

Assign a GitHub issue to a user

**Parameters:**

- **`user`** (string) 🔴 Required
  - The assignee GitHub login
  - Example: `octocat`

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_assign_issue "value" "value"
```

```javascript
// In JavaScript agent
const result = github_assign_issue("user", "owner");
```

---

### `github_close_issue`

Close a GitHub issue

**Parameters:**

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_close_issue "value" "value"
```

```javascript
// In JavaScript agent
const result = github_close_issue("owner", "repo");
```

---

### `github_close_pr`

Close a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_close_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_close_pr("owner", "repo");
```

---

### `github_create_branch`

Create a new branch from an existing commit SHA

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`branch`** (string) 🔴 Required
  - The name of the new branch
  - Example: `feature/x`

- **`from_sha`** (string) 🔴 Required
  - The commit SHA to branch from
  - Example: `abc123`

**Example:**
```bash
dmtools github_create_branch "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_branch("owner", "repo");
```

---

### `github_create_check_run`

Create a GitHub Check Run — a rich CI check with progress, annotations, and a full log visible in the PR 'Checks' tab. Use status=in_progress when starting, then call github_update_check_run to complete it.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`name`** (string) 🔴 Required
  - The name of the check run displayed in the PR
  - Example: `dmtools / pr-review`

- **`headSha`** (string) 🔴 Required
  - The SHA of the commit to associate this check run with
  - Example: `abc123def456...`

- **`status`** (string) ⚪ Optional
  - The status: queued | in_progress | completed
  - Example: `in_progress`

- **`title`** (string) ⚪ Optional
  - Title shown in the check run output panel
  - Example: `AI PR Review`

- **`summary`** (string) ⚪ Optional
  - Markdown summary shown in the check run output panel
  - Example: `🔍 Analysis started...`

- **`text`** (string) ⚪ Optional
  - Additional details in Markdown (supports large content)
  - Example: `Full analysis results here...`

- **`externalId`** (string) ⚪ Optional
  - Optional external identifier for this check run
  - Example: `MAPC-6653`

**Example:**
```bash
dmtools github_create_check_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_check_run("workspace", "repository");
```

---

### `github_create_comment`

Create a comment on a GitHub issue or pull request (PRs are issues upstream).

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) ⚪ Optional
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) ⚪ Optional
  - The issue or pull request number
  - Example: `74`

- **`body`** (string) 🔴 Required
  - The comment body text
  - Example: `Looks good!`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to workspace/repository/pullRequestId)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_create_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_comment("workspace", "repository");
```

---

### `github_create_commit_status`

Create a commit status (the colored dot in PR checks). Use state=pending when AI analysis starts, success/failure/error when complete. The 'context' field acts as the status name and must be unique per check.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`sha`** (string) 🔴 Required
  - The commit SHA to set status on
  - Example: `abc123def456...`

- **`state`** (string) 🔴 Required
  - The state: pending | success | failure | error
  - Example: `pending`

- **`description`** (string) ⚪ Optional
  - Short human-readable description shown next to the status dot
  - Example: `AI analysis in progress...`

- **`context`** (string) ⚪ Optional
  - Unique identifier for this status check, e.g. 'dmtools/pr-review'
  - Example: `dmtools/pr-review`

- **`targetUrl`** (string) ⚪ Optional
  - Optional URL to link from the status (e.g. CI run URL)
  - Example: `https://github.com/owner/repo/actions/runs/123`

**Example:**
```bash
dmtools github_create_commit_status "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_commit_status("workspace", "repository");
```

---

### `github_create_issue`

Create a GitHub issue

**Parameters:**

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`title`** (string) 🔴 Required
  - The title of the new issue
  - Example: `Something is broken`

- **`body`** (string) ⚪ Optional
  - The issue description (markdown)
  - Example: `Steps to reproduce...`

- **`key`** (string) ⚪ Optional
  - Composite project key 'owner/repo' (alternative to owner/repo)
  - Example: `IstiN/dmtools`

**Example:**
```bash
dmtools github_create_issue "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_issue("owner", "repo");
```

---

### `github_create_pr`

Create a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`title`** (string) 🔴 Required
  - The title of the new pull request
  - Example: `Add feature X`

- **`head`** (string) 🔴 Required
  - The branch containing changes (source)
  - Example: `feature/x`

- **`base`** (string) 🔴 Required
  - The branch to merge changes into (target)
  - Example: `main`

**Example:**
```bash
dmtools github_create_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_pr("owner", "repo");
```

---

### `github_create_release`

Create a GitHub release for a tag

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`tag_name`** (string) 🔴 Required
  - The name of the tag the release targets
  - Example: `v1.0.0`

- **`body`** (string) ⚪ Optional
  - The release description (markdown)
  - Example: `Release notes`

**Example:**
```bash
dmtools github_create_release "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_release("owner", "repo");
```

---

### `github_create_review`

Create a review on a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

- **`body`** (string) 🔴 Required
  - The review body text
  - Example: `Looks good!`

- **`event`** (string) 🔴 Required
  - Review event: APPROVE, REQUEST_CHANGES, or COMMENT
  - Example: `APPROVE`

**Example:**
```bash
dmtools github_create_review "value" "value"
```

```javascript
// In JavaScript agent
const result = github_create_review("owner", "repo");
```

---

### `github_delete_branch`

Delete a branch in a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`branch`** (string) 🔴 Required
  - The name of the branch to delete
  - Example: `feature/x`

**Example:**
```bash
dmtools github_delete_branch "value" "value"
```

```javascript
// In JavaScript agent
const result = github_delete_branch("owner", "repo");
```

---

### `github_delete_pr_comment`

Delete a comment on a GitHub pull request or issue by its comment ID.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to delete
  - Example: `123456789`

**Example:**
```bash
dmtools github_delete_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_delete_pr_comment("workspace", "repository");
```

---

### `github_delete_release_asset`

Delete a GitHub release asset by its asset ID. Use github_list_release_assets to find asset IDs.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`assetId`** (string) 🔴 Required
  - The numeric asset ID to delete.
  - Example: `422721847`

**Example:**
```bash
dmtools github_delete_release_asset "value" "value"
```

```javascript
// In JavaScript agent
const result = github_delete_release_asset("workspace", "repository");
```

---

### `github_disable_workflow`

Disable a GitHub Actions workflow by id

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`workflow_id`** (number) 🔴 Required
  - The workflow id to disable
  - Example: `123456`

**Example:**
```bash
dmtools github_disable_workflow "value" "value"
```

```javascript
// In JavaScript agent
const result = github_disable_workflow("owner", "repo");
```

---

### `github_dismiss_pr_review`

Dismiss a previously submitted GitHub pull request review (e.g. clear a REQUEST_CHANGES decision once the issues have been fixed and a new review approves). Requires repository admin rights, or being listed as allowed to dismiss reviews, on protected branches.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`reviewId`** (string) 🔴 Required
  - The ID of the review to dismiss (from github_list_pr_reviews or the response of github_submit_pr_review)
  - Example: `1234567`

- **`message`** (string) 🔴 Required
  - The reason for dismissing this review
  - Example: `Superseded — issues fixed and re-reviewed as APPROVE.`

**Example:**
```bash
dmtools github_dismiss_pr_review "value" "value"
```

```javascript
// In JavaScript agent
const result = github_dismiss_pr_review("workspace", "repository");
```

---

### `github_dismiss_review`

Dismiss a review on a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

- **`review_id`** (number) 🔴 Required
  - The id of the review to dismiss
  - Example: `123456`

- **`message`** (string) 🔴 Required
  - The dismissal message
  - Example: `Outdated`

**Example:**
```bash
dmtools github_dismiss_review "value" "value"
```

```javascript
// In JavaScript agent
const result = github_dismiss_review("owner", "repo");
```

---

### `github_enable_workflow`

Enable a GitHub Actions workflow by id

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`workflow_id`** (number) 🔴 Required
  - The workflow id to enable
  - Example: `123456`

**Example:**
```bash
dmtools github_enable_workflow "value" "value"
```

```javascript
// In JavaScript agent
const result = github_enable_workflow("owner", "repo");
```

---

### `github_get_check_runs`

List check runs for a GitHub commit ref

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`ref`** (string) 🔴 Required
  - The branch, tag, or commit SHA to list check runs for
  - Example: `main`

**Example:**
```bash
dmtools github_get_check_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_check_runs("owner", "repo");
```

---

### `github_get_codeowners`

Get the CODEOWNERS file from a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_get_codeowners "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_codeowners("owner", "repo");
```

---

### `github_get_commit`

Get a GitHub commit by SHA

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`sha`** (string) 🔴 Required
  - The commit SHA (or ref) to fetch
  - Example: `abc123`

**Example:**
```bash
dmtools github_get_commit "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_commit("owner", "repo");
```

---

### `github_get_commit_check_runs`

Get all check runs (CI/CD status checks) for a commit SHA in a GitHub repository. Returns details about each check including status, conclusion, and output.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`commitSha`** (string) 🔴 Required
  - The commit SHA to get check runs for
  - Example: `abc123...`

**Example:**
```bash
dmtools github_get_commit_check_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_commit_check_runs("workspace", "repository");
```

---

### `github_get_commits_from_branches`

Fetch commits from all branches whose name matches a given regex pattern, aggregated and de-duplicated. Useful for collecting commits from feature/*, release/* or similar groups of branches without specifying each branch individually.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`branchNameRegex`** (string) 🔴 Required
  - Java regular expression matched against branch names. All branches with a matching name are included. Example: '^feature/' or 'release/\d+'.
  - Example: `^feature/`

- **`since`** (string) ⚪ Optional
  - Optional ISO date (yyyy-MM-dd) to limit commits to those after this date.
  - Example: `2024-01-01`

**Example:**
```bash
dmtools github_get_commits_from_branches "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_commits_from_branches("workspace", "repository");
```

---

### `github_get_file_content`

Get the contents of a file in a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`path`** (string) 🔴 Required
  - The file path within the repository
  - Example: `README.md`

- **`ref`** (string) ⚪ Optional
  - Branch, tag, or commit SHA (defaults to default branch)
  - Example: `main`

**Example:**
```bash
dmtools github_get_file_content "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_file_content("owner", "repo");
```

---

### `github_get_issue`

Get details of a GitHub issue including title, description, state, author, labels, assignees, and comments count.

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) ⚪ Optional
  - The GitHub repository name
  - Example: `dmtools`

- **`issueNumber`** (string) ⚪ Optional
  - The issue number
  - Example: `42`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to workspace/repository/issueNumber)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_get_issue "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_issue("workspace", "repository");
```

---

### `github_get_job_logs`

Get the raw text logs for a specific GitHub Actions job. Returns the complete log output from all steps in the job.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`jobId`** (string) 🔴 Required
  - The job ID (from github_get_workflow_run_jobs)
  - Example: `1234567890`

**Example:**
```bash
dmtools github_get_job_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_job_logs("workspace", "repository");
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

- **`releaseName`** (string) ⚪ Optional
  - The human-readable release name. If empty, tagName is used.
  - Example: `PR Attachments Storage`

- **`targetCommitish`** (string) ⚪ Optional
  - Optional branch or commit SHA the release should point to when created.
  - Example: `main`

- **`body`** (string) ⚪ Optional
  - Optional Markdown release notes/body.
  - Example: `Internal storage release for PR attachments.`

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

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr("workspace", "repository");
```

---

### `github_get_pr_activities`

Get all activities for a GitHub pull request including reviews (approvals, change requests), inline code comments, and general discussion comments.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_activities "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_activities("workspace", "repository");
```

---

### `github_get_pr_comments`

Get all comments for a GitHub pull request, including both inline code review comments and general discussion comments. Results are sorted by creation date.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_comments "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_comments("workspace", "repository");
```

---

### `github_get_pr_conversations`

Get all review conversations (inline code comment threads) for a GitHub pull request. Groups inline code review comments into threads showing root comment and replies. Also includes general PR discussion comments as separate entries.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_conversations "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_conversations("workspace", "repository");
```

---

### `github_get_pr_diff`

Get the diff statistics for a GitHub pull request (files changed, additions, deletions). Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestID`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_diff "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_diff("workspace", "repository");
```

---

### `github_get_pr_diff_text`

Get the raw unified diff text for a GitHub pull request. Requires IS_READ_PULL_REQUEST_DIFF env/config to be enabled.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestID`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_diff_text "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_diff_text("workspace", "repository");
```

---

### `github_get_pr_files`

List the files changed in a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_files "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_files("owner", "repo");
```

---

### `github_get_pr_review_threads`

Get all review threads for a GitHub pull request via GraphQL, including each thread's node ID (needed for resolving), resolved status, file path, line, and comments. Use the returned thread 'id' with github_resolve_pr_thread.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_get_pr_review_threads "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_pr_review_threads("workspace", "repository");
```

---

### `github_get_release`

Get a GitHub release by tag name

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`tag`** (string) 🔴 Required
  - The tag name of the release
  - Example: `v1.0.0`

**Example:**
```bash
dmtools github_get_release "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_release("owner", "repo");
```

---

### `github_get_repo`

Get a GitHub repository by owner and name

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_get_repo "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_repo("owner", "repo");
```

---

### `github_get_tree`

Get a GitHub git tree recursively by ref

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`ref`** (string) 🔴 Required
  - Branch, tag, or commit SHA to read the tree from
  - Example: `main`

**Example:**
```bash
dmtools github_get_tree "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_tree("owner", "repo");
```

---

### `github_get_workflow_run`

Get details of a specific GitHub Actions workflow run by ID. Returns status, conclusion, logs URL, and timing information.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `1234567890`

**Example:**
```bash
dmtools github_get_workflow_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run("workspace", "repository");
```

---

### `github_get_workflow_run_jobs`

Get all jobs for a specific GitHub Actions workflow run. Shows individual job statuses, steps, and logs URLs.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `1234567890`

**Example:**
```bash
dmtools github_get_workflow_run_jobs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run_jobs("workspace", "repository");
```

---

### `github_get_workflow_run_logs`

Download and extract complete logs for all jobs in a GitHub Actions workflow run. Returns full untruncated log content from the ZIP archive GitHub provides.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`runId`** (string) 🔴 Required
  - The workflow run ID
  - Example: `22498697315`

**Example:**
```bash
dmtools github_get_workflow_run_logs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_run_logs("workspace", "repository");
```

---

### `github_get_workflow_runs`

List GitHub Actions workflow runs for a repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_get_workflow_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflow_runs("owner", "repo");
```

---

### `github_get_workflows`

List GitHub Actions workflows in a repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_get_workflows "value" "value"
```

```javascript
// In JavaScript agent
const result = github_get_workflows("owner", "repo");
```

---

### `github_list_branches`

List branches in a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_list_branches "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_branches("owner", "repo");
```

---

### `github_list_commits`

List commits in a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`sha`** (string) ⚪ Optional
  - Branch or commit SHA to list from (defaults to default branch)
  - Example: `main`

**Example:**
```bash
dmtools github_list_commits "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_commits("owner", "repo");
```

---

### `github_list_pr_reviews`

List all formal reviews (APPROVE/REQUEST_CHANGES/COMMENT decisions submitted via github_submit_pr_review or by human reviewers) for a GitHub pull request, in chronological order.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_list_pr_reviews "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_pr_reviews("workspace", "repository");
```

---

### `github_list_prs`

List pull requests in a GitHub repository by state. State can be 'open', 'closed', or 'merged'. Returns first page (up to 100) of pull requests.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`state`** (string) 🔴 Required
  - The state of pull requests to list: 'open', 'closed', or 'merged'. 'opened' is accepted as a synonym for 'open'.
  - Example: `open`

**Example:**
```bash
dmtools github_list_prs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_prs("workspace", "repository");
```

---

### `github_list_prs_filtered`

List pull requests in a GitHub repository filtered by a regex pattern on the PR title. Fetches all PRs matching the given state and returns only those whose title matches the regex. Useful for large repos to narrow down results without loading entire history.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`state`** (string) 🔴 Required
  - The state of pull requests: 'open', 'closed', or 'merged'.
  - Example: `merged`

- **`titleRegex`** (string) 🔴 Required
  - Java regular expression matched against the PR title (case-sensitive). Only PRs whose title contains a match are returned. Example: '^feat\(.*\)' or 'TICKET-\d+'.
  - Example: `^feat\(`

**Example:**
```bash
dmtools github_list_prs_filtered "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_prs_filtered("workspace", "repository");
```

---

### `github_list_release_assets`

List all assets attached to a GitHub release. Returns a JSON array of asset objects including id, name, size, and browser_download_url.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`releaseId`** (string) 🔴 Required
  - The numeric GitHub release ID.
  - Example: `323096697`

**Example:**
```bash
dmtools github_list_release_assets "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_release_assets("workspace", "repository");
```

---

### `github_list_releases`

List GitHub releases for a repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

**Example:**
```bash
dmtools github_list_releases "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_releases("owner", "repo");
```

---

### `github_list_workflow_runs`

List GitHub Actions workflow runs for a repository, optionally filtered by status or specific workflow file. Use status='failure' to get all failed runs.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`status`** (string) ⚪ Optional
  - Filter by status: failure, success, in_progress, queued, cancelled, timed_out, action_required, neutral, skipped, stale, completed
  - Example: `failure`

- **`workflowId`** (string) ⚪ Optional
  - Optional workflow filename to filter runs (e.g. rework.yml). If omitted, returns runs for all workflows.
  - Example: `rework.yml`

- **`perPage`** (number) ⚪ Optional
  - Number of results per page (max 100, default 30)
  - Example: `50`

- **`page`** (number) ⚪ Optional
  - Page number for pagination (default 1)
  - Example: `2`

- **`created`** (string) ⚪ Optional
  - Filter by created date/range using GitHub search syntax, e.g. 2026-05-01..2026-05-31 or >=2026-05-01
  - Example: `2026-05-01..2026-05-31`

**Example:**
```bash
dmtools github_list_workflow_runs "value" "value"
```

```javascript
// In JavaScript agent
const result = github_list_workflow_runs("workspace", "repository");
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

- **`mergeMethod`** (string) ⚪ Optional
  - The merge method: 'merge' (default), 'squash', or 'rebase'
  - Example: `squash`

- **`commitTitle`** (string) ⚪ Optional
  - Title for the merge commit (optional, defaults to PR title)
  - Example: `Merge feature/my-branch into main`

- **`commitMessage`** (string) ⚪ Optional
  - Extra detail to append to the merge commit message (optional)
  - Example: `Closes #123`

**Example:**
```bash
dmtools github_merge_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_merge_pr("workspace", "repository");
```

---

### `github_move_issue_to_status`

Move a GitHub issue to a status. 'done'/'closed' close the issue, 'open'/'reopened' reopen it; any other status is applied as an issue label.

**Parameters:**

- **`statusName`** (string) 🔴 Required
  - The target status name
  - Example: `Done`

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_move_issue_to_status "value" "value"
```

```javascript
// In JavaScript agent
const result = github_move_issue_to_status("statusName", "owner");
```

---

### `github_remove_collaborator`

Remove a collaborator from a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`username`** (string) 🔴 Required
  - The collaborator login to remove
  - Example: `octocat`

**Example:**
```bash
dmtools github_remove_collaborator "value" "value"
```

```javascript
// In JavaScript agent
const result = github_remove_collaborator("owner", "repo");
```

---

### `github_remove_label`

Remove a label from a GitHub issue

**Parameters:**

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`label`** (string) 🔴 Required
  - The name of the label to remove
  - Example: `bug`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_remove_label "value" "value"
```

```javascript
// In JavaScript agent
const result = github_remove_label("owner", "repo");
```

---

### `github_remove_pr_label`

Remove a label from a GitHub pull request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`label`** (string) 🔴 Required
  - The label name to remove from the pull request
  - Example: `bug`

**Example:**
```bash
dmtools github_remove_pr_label "value" "value"
```

```javascript
// In JavaScript agent
const result = github_remove_pr_label("workspace", "repository");
```

---

### `github_reopen_issue`

Reopen a closed GitHub issue

**Parameters:**

- **`owner`** (string) ⚪ Optional
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) ⚪ Optional
  - The repository name
  - Example: `dmtools`

- **`number`** (number) ⚪ Optional
  - The issue number
  - Example: `42`

- **`key`** (string) ⚪ Optional
  - Composite issue key 'owner/repo#123' (alternative to owner/repo/number)
  - Example: `IstiN/dmtools#42`

**Example:**
```bash
dmtools github_reopen_issue "value" "value"
```

```javascript
// In JavaScript agent
const result = github_reopen_issue("owner", "repo");
```

---

### `github_reopen_pr`

Reopen a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

**Example:**
```bash
dmtools github_reopen_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_reopen_pr("owner", "repo");
```

---

### `github_reply_to_pr_thread`

Reply to an existing inline code review comment thread in a GitHub pull request. Use the comment ID of the root comment (or any comment) in the thread as inReplyToId.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`inReplyToId`** (string) 🔴 Required
  - The ID of the comment to reply to (from github_get_pr_conversations rootComment.id or replies[].id)
  - Example: `123456789`

- **`text`** (string) 🔴 Required
  - The reply text (Markdown supported)
  - Example: `Fixed in the latest commit.`

**Example:**
```bash
dmtools github_reply_to_pr_thread "value" "value"
```

```javascript
// In JavaScript agent
const result = github_reply_to_pr_thread("workspace", "repository");
```

---

### `github_repository_dispatch`

Trigger a GitHub repository dispatch event. Workflows listening to 'on: repository_dispatch' with the matching event_type will be triggered.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`eventType`** (string) 🔴 Required
  - The type of activity that triggers the workflow (event_type)
  - Example: `rework`

- **`clientPayload`** (string) ⚪ Optional
  - Optional JSON string with payload passed to the workflow as client_payload
  - Example: `{"key":"value"}`

**Example:**
```bash
dmtools github_repository_dispatch "value" "value"
```

```javascript
// In JavaScript agent
const result = github_repository_dispatch("workspace", "repository");
```

---

### `github_request_reviewers`

Request reviewers on a GitHub pull request

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

- **`reviewers`** (array) 🔴 Required
  - The reviewer logins to request
  - Example: `["octocat"]`

**Example:**
```bash
dmtools github_request_reviewers "value" "value"
```

```javascript
// In JavaScript agent
const result = github_request_reviewers("owner", "repo");
```

---

### `github_rerun_workflow`

Re-run a GitHub Actions workflow run by id

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`run_id`** (number) 🔴 Required
  - The workflow run id to re-run
  - Example: `1234567890`

**Example:**
```bash
dmtools github_rerun_workflow "value" "value"
```

```javascript
// In JavaScript agent
const result = github_rerun_workflow("owner", "repo");
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

### `github_search_issues`

Search GitHub issues (and pull requests) with a query string. Returns a JSON object with 'items'.

**Parameters:**

- **`query`** (string) 🔴 Required
  - The GitHub issue search query (e.g. 'repo:owner/name is:open label:bug')
  - Example: `repo:IstiN/dmtools is:open`

- **`workspace`** (string) ⚪ Optional
  - The GitHub owner/organization to scope the search to
  - Example: `IstiN`

- **`repository`** (string) ⚪ Optional
  - The GitHub repository to scope the search to
  - Example: `dmtools`

**Example:**
```bash
dmtools github_search_issues "value" "value"
```

```javascript
// In JavaScript agent
const result = github_search_issues("query", "workspace");
```

---

### `github_submit_pr_review`

Submit a formal GitHub pull request review (a native reviewer decision, distinct from labels/comments). event=APPROVE marks the PR as approved by this reviewer; event=REQUEST_CHANGES formally blocks the PR (visible as 'Changes requested', and enforced by branch protection rules requiring approvals) until a new review or github_dismiss_pr_review clears it; event=COMMENT leaves a review without approving or blocking. 'body' is required for REQUEST_CHANGES and COMMENT.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`pullRequestId`** (string) 🔴 Required
  - The pull request number
  - Example: `74`

- **`event`** (string) 🔴 Required
  - The review decision: APPROVE, REQUEST_CHANGES, or COMMENT
  - Example: `REQUEST_CHANGES`

- **`body`** (string) ⚪ Optional
  - The review's summary text. Required for REQUEST_CHANGES and COMMENT.
  - Example: `Blocking issues found, please address before merge.`

**Example:**
```bash
dmtools github_submit_pr_review "value" "value"
```

```javascript
// In JavaScript agent
const result = github_submit_pr_review("workspace", "repository");
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

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`workflowId`** (string) 🔴 Required
  - The workflow filename or ID to trigger
  - Example: `rework.yml`

- **`inputs`** (string) ⚪ Optional
  - JSON string with workflow inputs (e.g. {"user_request":"...","branch":"main"})
  - Example: `{"user_request":"Please rework PROJ-123"}`

- **`ref`** (string) ⚪ Optional
  - The branch or tag to run the workflow on (default: main)
  - Example: `main`

**Example:**
```bash
dmtools github_trigger_workflow "value" "value"
```

```javascript
// In JavaScript agent
const result = github_trigger_workflow("workspace", "repository");
```

---

### `github_update_check_run`

Update an existing GitHub Check Run — set it to completed with success/failure conclusion, update summary and detailed text. Call this after github_create_check_run to finalize the check.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`checkRunId`** (string) 🔴 Required
  - The ID of the check run to update (from github_create_check_run response)
  - Example: `1234567890`

- **`status`** (string) 🔴 Required
  - The new status: in_progress | completed
  - Example: `completed`

- **`conclusion`** (string) ⚪ Optional
  - Required when status=completed: success | failure | neutral | cancelled | skipped | timed_out | action_required
  - Example: `success`

- **`title`** (string) ⚪ Optional
  - Updated title for the check run output panel
  - Example: `AI PR Review — Complete`

- **`summary`** (string) ⚪ Optional
  - Updated Markdown summary for the check run output panel
  - Example: `✅ Review complete. Found 3 issues.`

- **`text`** (string) ⚪ Optional
  - Updated detailed Markdown content (full analysis, annotations etc.)
  - Example: `## Issues Found
...`

**Example:**
```bash
dmtools github_update_check_run "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_check_run("workspace", "repository");
```

---

### `github_update_file`

Create or update a file in a GitHub repository

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`path`** (string) 🔴 Required
  - The file path within the repository
  - Example: `README.md`

- **`content`** (string) 🔴 Required
  - The new file content (plain text)
  - Example: `Hello world`

- **`message`** (string) 🔴 Required
  - The commit message
  - Example: `Update README`

- **`sha`** (string) 🔴 Required
  - The blob SHA of the existing file (required to update)
  - Example: `abc123`

**Example:**
```bash
dmtools github_update_file "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_file("owner", "repo");
```

---

### `github_update_pr`

Update the title or body of a GitHub pull request. Only the provided fields are changed.

**Parameters:**

- **`owner`** (string) 🔴 Required
  - The repository owner (user or organization)
  - Example: `IstiN`

- **`repo`** (string) 🔴 Required
  - The repository name
  - Example: `dmtools`

- **`number`** (number) 🔴 Required
  - The pull request number
  - Example: `74`

- **`title`** (string) ⚪ Optional
  - The new title (omitted to leave unchanged)
  - Example: `Updated title`

- **`body`** (string) ⚪ Optional
  - The new body (omitted to leave unchanged)
  - Example: `Updated description`

**Example:**
```bash
dmtools github_update_pr "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_pr("owner", "repo");
```

---

### `github_update_pr_comment`

Update (edit) an existing comment on a GitHub pull request or issue by its comment ID.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`commentId`** (string) 🔴 Required
  - The ID of the comment to update
  - Example: `123456789`

- **`text`** (string) 🔴 Required
  - The new comment text (replaces existing content)
  - Example: `✅ Analysis complete.`

**Example:**
```bash
dmtools github_update_pr_comment "value" "value"
```

```javascript
// In JavaScript agent
const result = github_update_pr_comment("workspace", "repository");
```

---

### `github_upload_release_asset`

Upload a local file as a GitHub release asset. Returns the uploaded asset metadata including browser_download_url. Set overwrite=true to automatically delete an existing asset with the same name before uploading.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - The GitHub owner/organization name
  - Example: `IstiN`

- **`repository`** (string) 🔴 Required
  - The GitHub repository name
  - Example: `dmtools`

- **`releaseId`** (string) 🔴 Required
  - The numeric GitHub release ID returned by github_get_or_create_draft_release.
  - Example: `323096697`

- **`filePath`** (string) 🔴 Required
  - Absolute or relative path to the local file to upload.
  - Example: `/tmp/preview.png`

- **`assetName`** (string) ⚪ Optional
  - Optional asset filename shown in GitHub. Defaults to the local filename.
  - Example: `clip_123.png`

- **`contentType`** (string) ⚪ Optional
  - Optional MIME type. Defaults to detected type or application/octet-stream.
  - Example: `image/png`

- **`label`** (string) ⚪ Optional
  - Optional display label for the uploaded asset.
  - Example: `Screenshot`

- **`overwrite`** (string) ⚪ Optional
  - If true, delete any existing asset with the same name before uploading. Defaults to false.
  - Example: `true`

**Example:**
```bash
dmtools github_upload_release_asset "value" "value"
```

```javascript
// In JavaScript agent
const result = github_upload_release_asset("workspace", "repository");
```

---

