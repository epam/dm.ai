# GitHub MCP Tools Reference

**Total tools**: 16
**Integration key**: `github`
**Category**: `pull_requests`

## Quick Start

```bash
# List available GitHub tools
dmtools list github

# Get PR details
dmtools github_get_pr workspace=IstiN repository=dmtools pullRequestId=74

# Get all comments (inline + discussion)
dmtools github_get_pr_comments workspace=IstiN repository=dmtools pullRequestId=74
```

## Tools

### `github_list_prs`

List pull requests in a GitHub repository by state.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `state` | String | ✅ | `open`, `closed`, or `merged` |

```bash
dmtools github_list_prs workspace=IstiN repository=dmtools state=open
```

Returns an array of pull request objects with `number`, `title`, `state`, `user`, `head`, `base`, `merged_at`, etc.

---

### `github_get_pr`

Get full details of a single pull request.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr workspace=IstiN repository=dmtools pullRequestId=74
```

Returns: `number`, `title`, `state`, `body`, `user` (author), `head`/`base` branches, `merged`, `merged_at`, `merge_commit_sha`, `labels`, `assignees`, `requested_reviewers`.

---

### `github_get_pr_comments`

Get **all** comments for a pull request — both inline code review comments and general discussion comments, merged and sorted by creation date.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr_comments workspace=IstiN repository=dmtools pullRequestId=74
```

Returns an array of comment objects with `id`, `body`, `user`, `created_at`, `updated_at`, and for inline comments: `path` (file), `line`, `in_reply_to_id`.

---

### `github_get_pr_conversations`

Get inline code review comments grouped into **conversation threads** (root comment + replies). Also includes general discussion comments as standalone entries.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr_conversations workspace=IstiN repository=dmtools pullRequestId=74
```

Returns an array of conversation objects:
```json
{
  "path": "src/Main.java",
  "rootComment": { "id": 123, "body": "...", "user": {...} },
  "replies": [ { "id": 456, "body": "...", "in_reply_to_id": 123 } ],
  "totalComments": 2
}
```

Use this instead of `github_get_pr_comments` when you need to understand the **context** of a discussion thread (who replied to what).

---

### `github_get_pr_activities`

Get **all activities** for a pull request: reviews (approvals, change requests, dismissals) and all comments (inline + discussion).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr_activities workspace=IstiN repository=dmtools pullRequestId=74
```

Returns an array of activity objects. Each activity has an `action` field:
- Review activities: `action` = `"APPROVED"`, `"CHANGES_REQUESTED"`, `"COMMENTED"`, `"DISMISSED"` — includes reviewer `user`, `state`, `body`, `submitted_at`
- Comment activities: `action` = `"COMMENTED"` — includes full comment details

---

### `github_add_pr_comment`

Post a comment to the general pull request discussion.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |
| `text` | String | ✅ | Comment body (Markdown supported) |

```bash
dmtools github_add_pr_comment workspace=IstiN repository=dmtools pullRequestId=74 text="Looks good!"
```

---

### `github_reply_to_pr_thread`

Reply to an existing **inline code review comment thread**. Use the comment ID from `github_get_pr_conversations` (`rootComment.id` or `replies[].id`) as `inReplyToId`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |
| `inReplyToId` | String | ✅ | ID of the comment to reply to |
| `text` | String | ✅ | Reply text (Markdown supported) |

```bash
# First get the conversation to find the comment ID
dmtools github_get_pr_conversations workspace=IstiN repository=dmtools pullRequestId=74

# Then reply using the rootComment.id
dmtools github_reply_to_pr_thread workspace=IstiN repository=dmtools pullRequestId=74 inReplyToId=123456789 text="Fixed in the latest commit."
```

---

### `github_add_inline_comment`

Create a **new inline code review comment** on a specific file and line. Optionally spans a range of lines (`startLine` to `line`).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |
| `path` | String | ✅ | Relative file path (e.g. `src/main/java/Foo.java`) |
| `line` | String | ✅ | Line number to comment on |
| `text` | String | ✅ | Comment text (Markdown supported) |
| `commitId` | String | — | Commit SHA to comment on. If empty, uses PR head commit automatically. |
| `startLine` | String | — | First line of a multi-line comment range (must be less than `line`) |
| `side` | String | — | `RIGHT` (new code, default) or `LEFT` (old code removed) |

```bash
# Single-line comment on new code (commitId auto-fetched)
dmtools github_add_inline_comment workspace=IstiN repository=dmtools pullRequestId=74 \
  path=src/main/java/Foo.java line=42 text="This should use Optional<> instead."

# Multi-line comment
dmtools github_add_inline_comment workspace=IstiN repository=dmtools pullRequestId=74 \
  path=src/main/java/Foo.java startLine=10 line=15 text="Extract this block into a method."
```

---

### `github_get_pr_review_threads`

Get all review threads for a pull request via **GraphQL**, including each thread's node ID (needed to resolve threads), resolved status, file path, line, and all comments.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr_review_threads workspace=IstiN repository=dmtools pullRequestId=74
```

Returns GraphQL response with `data.repository.pullRequest.reviewThreads.nodes`, each containing:
- `id` — GraphQL node ID (use this with `github_resolve_pr_thread`)
- `isResolved` — whether the thread is resolved
- `path`, `line`, `startLine` — file location
- `comments.nodes` — comments with `databaseId`, `body`, `author.login`

---

### `github_resolve_pr_thread`

Resolve a review thread via **GraphQL mutation**. Requires the thread's GraphQL node ID from `github_get_pr_review_threads`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `threadId` | String | ✅ | GraphQL node ID of the thread (from `github_get_pr_review_threads` → `thread.id`) |

```bash
# Step 1: get thread IDs
dmtools github_get_pr_review_threads workspace=IstiN repository=dmtools pullRequestId=74

# Step 2: resolve a specific thread using its id
dmtools github_resolve_pr_thread threadId="PRRT_kwDOBQfyNc5A_example"
```

Returns: `{ "data": { "resolveReviewThread": { "thread": { "id": "...", "isResolved": true } } } }`

---

### `github_add_pr_label`

Add a label to a pull request. The label must already exist in the repository.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |
| `label` | String | ✅ | Label name to add |

```bash
dmtools github_add_pr_label workspace=IstiN repository=dmtools pullRequestId=74 label=bug
```

---

### `github_get_pr_diff`

Get diff statistics for a pull request (files changed, lines added/deleted).

> **Note**: Requires `IS_READ_PULL_REQUEST_DIFF=true` in your environment configuration.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `pullRequestID` | String | ✅ | Pull request number |

```bash
dmtools github_get_pr_diff workspace=IstiN repository=dmtools pullRequestID=74
```

Returns: files changed, additions, deletions per file.

---

### `github_get_commit_check_runs`

Get all check runs (CI/CD status checks) for a specific commit SHA. Returns details about each check including status, conclusion, and output.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `commitSha` | String | ✅ | Commit SHA to get check runs for |

```bash
dmtools github_get_commit_check_runs workspace=IstiN repository=dmtools commitSha=abc123def456...
```

Returns JSON with `total_count` and array of `check_runs`, each containing:
- `id` — Check run ID
- `name` — Check name (e.g., "unit-tests", "Unit Tests")
- `status` — `queued`, `in_progress`, or `completed`
- `conclusion` — `success`, `failure`, `cancelled`, `skipped`, etc. (only when completed)
- `html_url` — Link to check run details
- `output` — Check output with `title`, `summary`, `text`

---

### `github_get_workflow_run`

Get details of a specific GitHub Actions workflow run by ID. Returns status, conclusion, logs URL, and timing information.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `runId` | String | ✅ | Workflow run ID (from check runs or PR status) |

```bash
dmtools github_get_workflow_run workspace=IstiN repository=dmtools runId=22403207268
```

Returns workflow run object with:
- `id`, `name` — Run ID and workflow name
- `status` — `queued`, `in_progress`, or `completed`
- `conclusion` — `success`, `failure`, `cancelled`, etc.
- `html_url` — Link to run
- `jobs_url`, `logs_url` — API endpoints for jobs and logs
- `event`, `head_sha`, `head_branch` — Trigger info
- `created_at`, `updated_at`, `run_started_at` — Timing

---

### `github_get_workflow_run_jobs`

Get all jobs for a specific GitHub Actions workflow run. Shows individual job statuses, steps, and logs URLs.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `runId` | String | ✅ | Workflow run ID |

```bash
dmtools github_get_workflow_run_jobs workspace=IstiN repository=dmtools runId=22403207268
```

Returns JSON with `total_count` and array of `jobs`, each containing:
- `id` — Job ID (use with `github_get_job_logs`)
- `name` — Job name (e.g., "unit-tests", "build")
- `status`, `conclusion` — Job status and result
- `steps` — Array of steps with individual status/conclusion
- `html_url` — Link to job details

---

### `github_get_job_logs`

Get the raw text logs for a specific GitHub Actions job. Returns the complete log output from all steps in the job.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `jobId` | String | ✅ | Job ID from `github_get_workflow_run_jobs` |

```bash
dmtools github_get_job_logs workspace=IstiN repository=dmtools jobId=64855476586
```

Returns raw text log output from all job steps. Useful for debugging test failures, build errors, or analyzing CI/CD behavior.

**Common workflow for debugging failed CI**:
```bash
# 1. Get check runs for the failing commit
dmtools github_get_commit_check_runs workspace=IstiN repository=dmtools commitSha=abc123...

# 2. Find the failed workflow run ID from check_runs, then get run details
dmtools github_get_workflow_run workspace=IstiN repository=dmtools runId=22403207268

# 3. Get jobs to find which one failed
dmtools github_get_workflow_run_jobs workspace=IstiN repository=dmtools runId=22403207268

# 4. Get logs from the failed job
dmtools github_get_job_logs workspace=IstiN repository=dmtools jobId=64855476586
```

---

## Usage in JavaScript Agents

```javascript
// List open PRs
const prs = github_list_prs('IstiN', 'dmtools', 'open');

// Get PR details
const pr = github_get_pr('IstiN', 'dmtools', '74');
print('PR Title: ' + pr.title + ' (' + pr.state + ')');

// Get all comments
const comments = github_get_pr_comments('IstiN', 'dmtools', '74');
print('Total comments: ' + comments.length);

// Get review threads
const conversations = github_get_pr_conversations('IstiN', 'dmtools', '74');
for (const conv of conversations) {
    print('Thread on ' + conv.path + ': ' + conv.totalComments + ' comments');
}

// Get all activities (reviews + comments)
const activities = github_get_pr_activities('IstiN', 'dmtools', '74');
const approvals = activities.filter(a => JSON.parse(a).action === 'APPROVED');
print('Approvals: ' + approvals.length);

// Add a general discussion comment
github_add_pr_comment('IstiN', 'dmtools', '74', 'Automated review complete.');

// Reply to an inline review thread
// (get the comment ID first from github_get_pr_conversations)
github_reply_to_pr_thread('IstiN', 'dmtools', '74', '123456789', 'Fixed in latest commit.');

// Create a new inline code review comment
github_add_inline_comment('IstiN', 'dmtools', '74',
    'src/main/java/Foo.java', '42', 'This should use Optional<>.',
    '', null, 'RIGHT');  // commitId empty = auto-fetch from PR head

// Get review threads with GraphQL IDs (needed to resolve)
const threadsJson = github_get_pr_review_threads('IstiN', 'dmtools', '74');
const threads = JSON.parse(threadsJson).data.repository.pullRequest.reviewThreads.nodes;
const openThreads = threads.filter(t => !t.isResolved);
print('Open threads: ' + openThreads.length);

// Resolve a thread using its GraphQL node ID
github_resolve_pr_thread('PRRT_kwDOBQfyNc5A_example');

// Check CI/CD status for a commit
const checkRuns = JSON.parse(github_get_commit_check_runs('IstiN', 'dmtools', 'abc123def456...'));
const failedChecks = checkRuns.check_runs.filter(c => c.conclusion === 'failure');
print('Failed checks: ' + failedChecks.length);

// Get workflow run details
const run = JSON.parse(github_get_workflow_run('IstiN', 'dmtools', '22403207268'));
print('Workflow: ' + run.name + ' - ' + run.conclusion);

// Get all jobs for a workflow run
const jobs = JSON.parse(github_get_workflow_run_jobs('IstiN', 'dmtools', '22403207268'));
const failedJobs = jobs.jobs.filter(j => j.conclusion === 'failure');
print('Failed jobs: ' + failedJobs.length);

// Get job logs for debugging
const logs = github_get_job_logs('IstiN', 'dmtools', '64855476586');
if (logs.includes('FAILED')) {
    print('Found test failures in logs');
    // Parse logs to extract failure details
}
```

## Setup

See [GitHub Configuration Guide](../configuration/integrations/github.md) for authentication and environment setup.
