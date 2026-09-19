# Default Integration Aliases for MCP Tools

DMtools supports **cross-integration aliases** — universal tool names that map to
platform-specific implementations. Instead of calling `github_list_prs` or
`gitlab_list_mrs` directly, you can call `source_code_list_prs` and DMtools routes
the call to the correct integration based on environment variables.

---

## How It Works

1. You call a generic alias, e.g. `source_code_list_prs` or `tracker_get_ticket`.
2. DMtools looks up all implementations registered for that alias.
3. **Explicit key formats win first** (only for `tracker_*` calls carrying an issue key):
   `gh-123` or `owner/repo#123` always route to GitHub — they are not valid keys anywhere else.
4. If only one implementation exists, it is used automatically.
5. If multiple implementations exist (e.g., GitHub **and** GitLab), the routing
   property is consulted from the config chain (`config.properties` → `dmtools.env`
   → environment variables):
   - `DEFAULT_SOURCE_CODE` → `github` or `gitlab`
   - `DEFAULT_TRACKER` → `jira`, `ado`, or `github`
6. If no routing property is set, **key-format detection** applies to `tracker_*`
   calls: `PROJ-123` → Jira, bare integer (`12345`) → ADO.
7. Otherwise the first registered implementation is used (with a warning).

Alias resolution works the same in every entry point: the CLI
(`dmtools tracker_get_ticket ...`) and JavaScript agents (GraalJS), where
`tracker_*` functions are exposed as first-class globals alongside the canonical
vendor tools.

---

## Configuration

```bash
# Use GitHub for all source_code_* calls
DEFAULT_SOURCE_CODE=github

# Use GitLab for all source_code_* calls
DEFAULT_SOURCE_CODE=gitlab

# Use Jira for all tracker_* calls
DEFAULT_TRACKER=jira

# Use Azure DevOps for all tracker_* calls
DEFAULT_TRACKER=ado

# Use GitHub Issues for all tracker_* calls
DEFAULT_TRACKER=github
```

Add these to your `dmtools.env` file or export them as shell environment variables.

---

## Source Code Aliases (`source_code_*`)

These aliases abstract over **GitHub** and **GitLab** PR/MR operations.

| Alias | GitHub Implementation | GitLab Implementation |
|-------|-----------------------|-----------------------|
| `source_code_list_prs` | `github_list_prs` | `gitlab_list_mrs` |
| `source_code_get_pr` | `github_get_pr` | `gitlab_get_mr` |
| `source_code_get_pr_comments` | `github_get_pr_comments` | `gitlab_get_mr_comments` |
| `source_code_add_pr_comment` | `github_add_pr_comment` | `gitlab_add_mr_comment` |
| `source_code_get_pr_activities` | `github_get_pr_activities` | `gitlab_get_mr_activities` |
| `source_code_get_pr_discussions` | `github_get_pr_conversations` | `gitlab_get_mr_discussions` |
| `source_code_reply_to_pr_thread` | `github_reply_to_pr_thread` | `gitlab_reply_to_mr_thread` |
| `source_code_add_inline_comment` | `github_add_inline_comment` | `gitlab_add_inline_comment` |
| `source_code_resolve_pr_thread` | `github_resolve_pr_thread` | `gitlab_resolve_mr_thread` |
| `source_code_merge_pr` | `github_merge_pr` | `gitlab_merge_mr` |
| `source_code_get_pr_diff` | `github_get_pr_diff` | `gitlab_get_mr_diff` |

### Aligned Parameter Names (source_code_*)

| Alias Parameter | GitHub Parameter | GitLab Parameter |
|----------------|-----------------|-----------------|
| `workspace` | `workspace` | `workspace` |
| `repository` | `repository` | `repository` |
| `pullRequestId` | `pullRequestId` | `pullRequestId` |
| `text` | `text` | `text` |
| `filePath` | `path` (alias: `filePath`) | `filePath` |
| `threadId` | `inReplyToId` (alias: `threadId`) | `discussionId` (alias: `threadId`) |

---

## Tracker Aliases (`tracker_*`)

These aliases abstract over **Jira**, **Azure DevOps (ADO)** work items, and
**GitHub Issues**.

| Alias | Jira Implementation | ADO Implementation | GitHub Implementation |
|-------|---------------------|--------------------|-----------------------|
| `tracker_search` | `jira_search_by_jql` | `ado_search_by_wiql` | `github_search_issues` |
| `tracker_get_ticket` | `jira_get_ticket` | `ado_get_work_item` | `github_get_issue` |
| `tracker_get_comments` | `jira_get_comments` | `ado_get_work_item_comments` | `github_get_pr_comments` |
| `tracker_post_comment` | `jira_post_comment` | `ado_add_work_item_comment` | `github_create_comment` |
| `tracker_add_label` | `jira_add_label` | `ado_add_work_item_label` | `github_add_labels` |
| `tracker_remove_label` | `jira_remove_label` | `ado_remove_work_item_label` | `github_remove_label` |
| `tracker_assign_ticket` | `jira_assign_ticket_to` | `ado_assign_work_item` | `github_assign_issue` |
| `tracker_assign` | `jira_assign_ticket_to` | `ado_assign_work_item` | `github_assign_issue` |
| `tracker_move_to_status` | `jira_move_to_status` | `ado_move_to_state` | `github_move_issue_to_status` |
| `tracker_get_my_profile` | `jira_get_my_profile` | `ado_get_my_profile` | `github_test` |
| `tracker_get_user_by_email` | `jira_get_account_by_email` | `ado_get_user_by_email` | — |
| `tracker_link_tickets` | `jira_link_issues` | `ado_link_work_items` | — |
| `tracker_create_ticket` | `jira_create_ticket_basic` | `ado_create_work_item` | `github_create_issue` |
| `tracker_download_attachment` | `jira_download_attachment` | `ado_download_attachment` | — |

### Aligned Parameter Names (tracker_*)

| Alias Parameter | Jira Parameter | ADO Parameter |
|----------------|---------------|--------------|
| `key` | `key` | `id` (alias: `key`) |
| `query` | `jql` (alias: `query`) | `wiql` (aliases: `jql`, `query`) |
| `statusName` | `statusName` | `state` (alias: `statusName`) |
| `accountId` | `accountId` | `userEmail` (alias: `accountId`) |
| `issueType` | `issueType` | `workItemType` (alias: `issueType`) |
| `summary` | `summary` | `title` (alias: `summary`) |
| `sourceKey` | `sourceKey` | `sourceId` (alias: `sourceKey`) |
| `anotherKey` | `anotherKey` | `targetId` (alias: `anotherKey`) |

---

## Usage Examples

### CLI

```bash
# List PRs/MRs using the generic alias (routes via DEFAULT_SOURCE_CODE)
./dmtools.sh source_code_list_prs workspace=myorg repository=myrepo

# Search issues (routes via DEFAULT_TRACKER)
./dmtools.sh tracker_search query="status = In Progress"

# Get a ticket by key
./dmtools.sh tracker_get_ticket key=PROJ-123
```

### JavaScript Agents

```javascript
// Works for both GitHub and GitLab depending on DEFAULT_SOURCE_CODE
const prs = source_code_list_prs("myorg", "myrepo", "open");

// Works for Jira, ADO, or GitHub Issues depending on DEFAULT_TRACKER
const issues = tracker_search("status = In Progress AND assignee = currentUser()");
const ticket = tracker_get_ticket("PROJ-123");

// Key-format routing: gh-N always lands on GitHub Issues, even when the
// deployment default tracker is Jira or ADO
const ghIssue = tracker_get_ticket("gh-42");        // → github_get_issue
const ghIssue2 = tracker_get_ticket("owner/repo#42"); // → github_get_issue

// Post a comment using the alias
tracker_post_comment("PROJ-123", "This has been reviewed.");
```

---

## Adding New Aliases

To add a new alias to any `@MCPTool`-annotated method:

```java
@MCPTool(
    name = "github_list_prs",
    description = "List pull requests in a repository",
    integration = "github",
    category = "pull_requests",
    aliases = {"source_code_list_prs"}   // ← add aliases here
)
public List<PullRequest> listPullRequests(
        @MCPParam(name = "workspace", ...) String workspace,
        @MCPParam(name = "repository", ...) String repository) { ... }
```

After adding aliases, rebuild: `./gradlew :dmtools-core:compileJava`.
The annotation processor will update `MCPToolRegistry.ALIAS_TO_TOOL_NAMES` automatically.
