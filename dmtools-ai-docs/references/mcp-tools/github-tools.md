# GitHub MCP Tools Reference

**Total tools**: 20
**Integration key**: `github`
**Categories**: `pull_requests`, `actions`

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

### `github_merge_pr`

Merge a pull request. Supports merge, squash, and rebase merge methods.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner/organization |
| `repository` | String | ✅ | Repository name |
| `pullRequestId` | String | ✅ | Pull request number |
| `mergeMethod` | String | ❌ | Merge method: `merge` (default), `squash`, or `rebase` |
| `commitTitle` | String | ❌ | Title for the merge commit (defaults to PR title) |
| `commitMessage` | String | ❌ | Extra detail to append to the merge commit message |

```bash
dmtools github_merge_pr workspace=IstiN repository=dmtools pullRequestId=74
dmtools github_merge_pr workspace=IstiN repository=dmtools pullRequestId=74 mergeMethod=squash
dmtools github_merge_pr workspace=IstiN repository=dmtools pullRequestId=74 mergeMethod=squash commitTitle="feat: my feature" commitMessage="Closes #123"
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

---

### `github_get_workflow_run_logs`

Download and extract **complete untruncated logs** for all jobs in a workflow run. Unlike `github_get_job_logs` which may be truncated for large logs, this tool downloads the full ZIP archive GitHub provides and returns all log files concatenated.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `runId` | String | ✅ | Workflow run ID |

```bash
dmtools github_get_workflow_run_logs workspace=IstiN repository=dmtools runId=22498697315
```

Returns all `.txt` log files from the ZIP concatenated together, each prefixed with its filename (e.g. `--- 0_Set up job.txt ---`).

**Use this instead of `github_get_job_logs` when:**
- Logs are very large and getting truncated
- You need logs from all jobs at once (not per job)
- You want the full raw output without pagination

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

### `github_list_workflow_runs`

List GitHub Actions workflow runs for a repository, optionally filtered by status or specific workflow file.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `status` | String | ❌ | Filter by status: `failure`, `success`, `in_progress`, `queued`, `cancelled`, `timed_out`, `action_required`, `neutral`, `skipped`, `stale`, `completed` |
| `workflowId` | String | ❌ | Workflow filename to filter runs (e.g. `rework.yml`). If omitted, returns runs for all workflows. |
| `perPage` | Integer | ❌ | Number of results per page (max 100, default 30) |

```bash
# All failed runs across all workflows
dmtools github_list_workflow_runs workspace=IstiN repository=dmtools status=failure

# Failed runs for a specific workflow
dmtools github_list_workflow_runs workspace=IstiN repository=dmtools status=failure workflowId=rework.yml perPage=50

# All currently running workflows
dmtools github_list_workflow_runs workspace=IstiN repository=dmtools status=in_progress
```

Returns JSON with `total_count` and `workflow_runs` array, each run containing `id`, `name`, `status`, `conclusion`, `html_url`, `head_branch`, `created_at`, `updated_at`.

---

### `github_repository_dispatch`

Trigger a GitHub repository dispatch event. Workflows with `on: repository_dispatch` and matching `event_type` will be triggered.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `eventType` | String | ✅ | Event type string — workflows listen via `types: [your-event]` |
| `clientPayload` | String | ❌ | JSON string with payload passed as `client_payload` to the workflow |

```bash
dmtools github_repository_dispatch workspace=IstiN repository=dmtools eventType=rework
dmtools github_repository_dispatch workspace=IstiN repository=dmtools eventType=rework clientPayload='{"ticket":"PROJ-123"}'
```

**Workflow YAML side:**
```yaml
on:
  repository_dispatch:
    types: [rework]
```

---

### `github_trigger_workflow`

Trigger a specific GitHub Actions workflow by filename (`workflow_dispatch`). The workflow must have `on: workflow_dispatch` configured.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `workspace` | String | ✅ | GitHub owner or organization name |
| `repository` | String | ✅ | Repository name |
| `workflowId` | String | ✅ | Workflow filename (e.g. `rework.yml`) |
| `inputs` | String | ❌ | JSON string with workflow inputs (e.g. `{"user_request":"...","branch":"main"}`) |
| `ref` | String | ❌ | Branch or tag to run on (default: `main`) |

```bash
dmtools github_trigger_workflow workspace=IstiN repository=dmtools workflowId=rework.yml \
  inputs='{"user_request":"Please rework PROJ-123"}' ref=main
```

**Workflow YAML side:**
```yaml
on:
  workflow_dispatch:
    inputs:
      user_request:
        required: true
```

**Difference from `github_repository_dispatch`:**

| | `github_repository_dispatch` | `github_trigger_workflow` |
|---|---|---|
| API | `POST /repos/.../dispatches` | `POST /repos/.../actions/workflows/{id}/dispatches` |
| Targets | Any workflow listening to the event type | Specific workflow file directly |
| Inputs | `client_payload` (free JSON) | `inputs` (named, declared in workflow YAML) |

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

// Download complete (untruncated) logs for a workflow run
const fullLogs = github_get_workflow_run_logs('ai-teammate', 'mytube', '22498697315');
// Returns all job logs concatenated — no truncation

// List all failed runs (all workflows)
const failedRuns = JSON.parse(github_list_workflow_runs('IstiN', 'dmtools', 'failure', null, 50));
print('Failed runs: ' + failedRuns.total_count);

// List failed runs for a specific workflow
const failedRework = JSON.parse(github_list_workflow_runs('IstiN', 'dmtools', 'failure', 'rework.yml', 30));
for (const run of failedRework.workflow_runs) {
    print(run.id + ' - ' + run.name + ' - ' + run.html_url);
}

// Trigger workflow dispatch (specific workflow file)
github_trigger_workflow('IstiN', 'dmtools', 'rework.yml',
    JSON.stringify({ user_request: 'Please rework PROJ-123' }),
    'main');

// Repository dispatch (any workflow listening to event type)
github_repository_dispatch('IstiN', 'dmtools', 'rework',
    JSON.stringify({ ticket: 'PROJ-123' }));
```

## Practical Example: Failed Workflows → Jira Bugs (with Deduplication)

A common automation pattern: scan for recently failed GitHub Actions runs and automatically create Jira bugs. Uses **label-based deduplication** so each failed run creates at most one bug.

```javascript
/**
 * Scans failed GitHub Actions runs and creates Jira bugs with deduplication.
 *
 * Deduplication strategy: Jira label  "gh-run-{runId}"
 * Before creating a bug we search Jira for an existing ticket with that label.
 * If one exists we skip creation; otherwise we fetch the full logs and create the bug.
 *
 * Required customParams in Teammate JSON config:
 * {
 *   "workspace": "my-org",
 *   "repository": "my-repo",
 *   "workflowId": "deploy.yml",       // optional — omit to check all workflows
 *   "jiraProject": "OPS",
 *   "maxRuns": 20
 * }
 */
function action(params) {
    const custom      = params.jobParams.customParams;
    const workspace   = custom.workspace;
    const repo        = custom.repository;
    const workflowId  = custom.workflowId  || null;   // optional
    const jiraProject = custom.jiraProject;
    const maxRuns     = custom.maxRuns     || 10;

    // 1. Get recently failed runs
    const runsJson = github_list_workflow_runs(workspace, repo, 'failure', workflowId, maxRuns);
    const runs = JSON.parse(runsJson).workflow_runs;

    if (!runs || runs.length === 0) {
        print('No failed runs found.');
        return { created: 0 };
    }

    let created = 0;

    for (const run of runs) {
        const runId = String(run.id);
        const label = 'gh-run-' + runId;

        // 2. Check for existing Jira bug with this label (deduplication)
        const jql = 'project = "' + jiraProject + '" AND labels = "' + label + '"';
        const existing = jira_search_by_jql(jql, 1);
        const existingIssues = JSON.parse(existing);

        if (existingIssues.total > 0) {
            print('Run ' + runId + ' already has a bug: ' + existingIssues.issues[0].key + ' — skipping.');
            continue;
        }

        // 3. Fetch full logs for this run
        print('Fetching logs for failed run ' + runId + ' (' + run.name + ')...');
        let logs = '';
        try {
            logs = github_get_workflow_run_logs(workspace, repo, runId);
            // Trim to avoid Jira description overflow (keep last 20 000 chars)
            if (logs.length > 20000) {
                logs = '...(truncated)...\n' + logs.slice(-20000);
            }
        } catch (e) {
            logs = 'Could not fetch logs: ' + e;
        }

        // 4. Build a concise summary line from log content
        const errorLine = extractFirstError(logs);

        // 5. Create Jira bug
        const summary = '[CI Failure] ' + run.name + ' — ' + (run.head_branch || 'unknown branch') +
                        (errorLine ? ': ' + errorLine : '');
        const description =
            'GitHub Actions run **' + runId + '** failed.\n\n' +
            '- **Workflow**: ' + run.name + '\n' +
            '- **Branch**: ' + (run.head_branch || 'N/A') + '\n' +
            '- **Triggered at**: ' + run.created_at + '\n' +
            '- **Run URL**: ' + run.html_url + '\n\n' +
            '**Logs:**\n{code}\n' + logs + '\n{code}';

        const newIssue = jira_create_ticket_basic(jiraProject, 'Bug', summary, description);
        const newKey   = JSON.parse(newIssue).key;

        // 6. Add deduplication label so this run is never filed twice
        jira_update_labels(newKey, label);

        print('Created ' + newKey + ' for run ' + runId);
        created++;
    }

    return { created: created };
}

/**
 * Extracts the first line that looks like an error from the log text.
 */
function extractFirstError(logs) {
    const lines = logs.split('\n');
    for (const line of lines) {
        const l = line.toLowerCase();
        if (l.includes('error') || l.includes('failed') || l.includes('exception')) {
            // Strip leading timestamp / log-prefix noise
            return line.replace(/^\d{4}-\d{2}-\d{2}T[\d:.Z]+ /, '').substring(0, 120);
        }
    }
    return '';
}
```

**Teammate JSON config** (`agents/detect_failed_workflows.json`):
```json
{
  "name": "FailedWorkflowMonitor",
  "params": {
    "jsAction": "agents/js/failedWorkflowsToJira.js",
    "customParams": {
      "workspace": "my-org",
      "repository": "my-repo",
      "workflowId": "deploy.yml",
      "jiraProject": "OPS",
      "maxRuns": 20
    }
  }
}
```

**MCP tools used in this example:**

| Tool | Purpose |
|------|---------|
| `github_list_workflow_runs` | Get recently failed runs |
| `github_get_workflow_run_logs` | Download full (untruncated) logs ZIP |
| `jira_search_by_jql` | Check if bug already exists (dedup via label) |
| `jira_create_ticket_basic` | Create the Bug ticket |
| `jira_update_labels` | Add deduplication label `gh-run-{runId}` |

---

## Setup

See [GitHub Configuration Guide](../configuration/integrations/github.md) for authentication and environment setup.
