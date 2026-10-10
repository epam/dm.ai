# SCM MCP Tools

**Total Tools**: 15

Vendor-neutral `scm_*` tools routed by `DEFAULT_SCM` (github|gitlab). Additive next to the untouched `github_*` / `gitlab_*` tools; absent from `dmtools list` when the variable is not set.

## Quick Reference

```bash
# List all scm tools
dmtools list | jq '.tools[] | select(.name | startswith("scm_"))'
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `scm_add_labels` | Add labels to a PR/MR or issue. Labels are plain strings. | `workspace` (string, optional)<br>`repository` (string, optional)<br>`pr` (string, **required**)<br>`labels` (array, **required**) |
| `scm_add_pr_comment` | Comment on a pull request / merge request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**)<br>`text` (string, **required**) |
| `scm_approve` | Approve a pull request / merge request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**)<br>`body` (string, optional) |
| `scm_close_issue` | Close an issue (SM close-on-merge finishing move). | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`issue` (string, **required**) |
| `scm_create_comment` | Comment on an issue (the SM issue-carrier channel). GitLab: rides the MR-note shape — `issue` is the MR iid, not a GitLab issue number. | `workspace` (string, optional)<br>`repository` (string, optional)<br>`issue` (string, **required**)<br>`body` (string, **required**) |
| `scm_get_diff` | Raw unified diff text of a pull request / merge request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**) |
| `scm_get_issue` | Fetch one issue by number. | `workspace` (string, optional)<br>`repository` (string, optional)<br>`issue` (string, **required**) |
| `scm_get_pr` | Fetch one pull request / merge request (provider PR payload). | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**) |
| `scm_get_pr_comments` | List comments of a pull request / merge request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**) |
| `scm_get_reviews` | List reviews / approvals of a pull request / merge request. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**) |
| `scm_list_branches` | List repository branches (name + head sha). | `workspace` (string, **required**)<br>`repository` (string, **required**) |
| `scm_list_prs` | List pull requests / merge requests. State filter is the normalized enum: open (default), closed, merged, all. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`state` (string, optional) |
| `scm_merge_pr` | Merge a pull request / merge request. Merge method per provider config (GitHub: mergeMethod merge\|squash\|rebase). | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**)<br>`mergeMethod` (string, optional)<br>`commitTitle` (string, optional)<br>`commitMessage` (string, optional) |
| `scm_remove_label` | Remove one label from a PR/MR or issue (an absent label is a no-op on GitLab-shaped providers). | `workspace` (string, optional)<br>`repository` (string, optional)<br>`pr` (string, **required**)<br>`label` (string, **required**) |
| `scm_search_issues` | Search issues (GitHub search syntax; scoped to the configured repository when the query has no repo: qualifier). | `workspace` (string, optional)<br>`repository` (string, optional)<br>`query` (string, **required**) |

## Detailed Parameter Information

### `scm_add_labels`

Add labels to a PR/MR or issue. Labels are plain strings.

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - Repository owner / namespace

- **`repository`** (string) ⚪ Optional
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

- **`labels`** (array) 🔴 Required
  - Labels to add

**Example:**
```javascript
// In JavaScript agent
const result = scm_add_labels("workspace", "repository", "pr", "labels");
```

---

### `scm_add_pr_comment`

Comment on a pull request / merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

- **`text`** (string) 🔴 Required
  - Comment text

**Example:**
```javascript
// In JavaScript agent
const result = scm_add_pr_comment("workspace", "repository", "pr", "text");
```

---

### `scm_approve`

Approve a pull request / merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

- **`body`** (string) ⚪ Optional
  - Optional approval summary text

**Example:**
```javascript
// In JavaScript agent
const result = scm_approve("workspace", "repository", "pr", "body");
```

---

### `scm_close_issue`

Close an issue (SM close-on-merge finishing move).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`issue`** (string) 🔴 Required
  - Issue number

**Example:**
```javascript
// In JavaScript agent
const result = scm_close_issue("workspace", "repository", "issue");
```

---

### `scm_create_comment`

Comment on an issue (the SM issue-carrier channel). GitLab: rides the MR-note shape — `issue` is the MR iid, not a GitLab issue number.

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - Repository owner / namespace

- **`repository`** (string) ⚪ Optional
  - Repository name

- **`issue`** (string) 🔴 Required
  - Issue number (GitLab: MR iid)

- **`body`** (string) 🔴 Required
  - Comment body

**Example:**
```javascript
// In JavaScript agent
const result = scm_create_comment("workspace", "repository", "issue", "body");
```

---

### `scm_get_diff`

Raw unified diff text of a pull request / merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

**Example:**
```javascript
// In JavaScript agent
const result = scm_get_diff("workspace", "repository", "pr");
```

---

### `scm_get_issue`

Fetch one issue by number.

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - Repository owner / namespace

- **`repository`** (string) ⚪ Optional
  - Repository name

- **`issue`** (string) 🔴 Required
  - Issue number

**Example:**
```javascript
// In JavaScript agent
const result = scm_get_issue("workspace", "repository", "issue");
```

---

### `scm_get_pr`

Fetch one pull request / merge request (provider PR payload).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

**Example:**
```javascript
// In JavaScript agent
const result = scm_get_pr("workspace", "repository", "pr");
```

---

### `scm_get_pr_comments`

List comments of a pull request / merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

**Example:**
```javascript
// In JavaScript agent
const result = scm_get_pr_comments("workspace", "repository", "pr");
```

---

### `scm_get_reviews`

List reviews / approvals of a pull request / merge request.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

**Example:**
```javascript
// In JavaScript agent
const result = scm_get_reviews("workspace", "repository", "pr");
```

---

### `scm_list_branches`

List repository branches (name + head sha).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

**Example:**
```javascript
// In JavaScript agent
const result = scm_list_branches("workspace", "repository");
```

---

### `scm_list_prs`

List pull requests / merge requests. State filter is the normalized enum: open (default), closed, merged, all.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`state`** (string) ⚪ Optional
  - Normalized state filter: open, closed, merged, all

**Example:**
```javascript
// In JavaScript agent
const result = scm_list_prs("workspace", "repository", "state");
```

---

### `scm_merge_pr`

Merge a pull request / merge request. Merge method per provider config (GitHub: mergeMethod merge|squash|rebase).

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

- **`mergeMethod`** (string) ⚪ Optional
  - merge | squash | rebase (GitHub)

- **`commitTitle`** (string) ⚪ Optional
  - Override the merge commit title (GitHub)

- **`commitMessage`** (string) ⚪ Optional
  - Override the merge commit message (GitHub)

**Example:**
```javascript
// In JavaScript agent
const result = scm_merge_pr("workspace", "repository", "pr", "mergeMethod", "commitTitle", "commitMessage");
```

---

### `scm_remove_label`

Remove one label from a PR/MR or issue (an absent label is a no-op on GitLab-shaped providers).

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - Repository owner / namespace

- **`repository`** (string) ⚪ Optional
  - Repository name

- **`pr`** (string) 🔴 Required
  - PR / MR number

- **`label`** (string) 🔴 Required
  - The label to remove

**Example:**
```javascript
// In JavaScript agent
const result = scm_remove_label("workspace", "repository", "pr", "label");
```

---

### `scm_search_issues`

Search issues (GitHub search syntax; scoped to the configured repository when the query has no repo: qualifier).

**Parameters:**

- **`workspace`** (string) ⚪ Optional
  - Repository owner / namespace

- **`repository`** (string) ⚪ Optional
  - Repository name

- **`query`** (string) 🔴 Required
  - Search query

**Example:**
```javascript
// In JavaScript agent
const result = scm_search_issues("workspace", "repository", "query");
```

---
