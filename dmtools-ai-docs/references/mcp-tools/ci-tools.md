# CI MCP Tools

**Total Tools**: 4

Vendor-neutral `ci_*` tools routed by `DEFAULT_CI` (actions|gitlab-ci). Additive next to the untouched `github_*` / `gitlab_*` tools; absent from `dmtools list` when the variable is not set.

## Quick Reference

```bash
# List all ci tools
dmtools list | jq '.tools[] | select(.name | startswith("ci_"))'
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `ci_get_merge_state` | Mergeability of a PR/MR in the enum the SM speaks: CLEAN \| BEHIND \| DIRTY \| BLOCKED \| UNKNOWN. Both vendors' checks-settling transients return BLOCKED + reason. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`pr` (string, **required**) |
| `ci_get_verdict` | THE CI verdict for a run: pass \| fail \| pending \| none. Call with `runId`, or probe a head with `sha` (plus optional `workflow` for the stale-verdict fallback), or on GitLab with `pr`. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`runId` (string, optional)<br>`sha` (string, optional)<br>`workflow` (string, optional)<br>`pr` (string, optional) |
| `ci_list_runs` | List CI runs (newest first). Each run: runId, status, verdict (pass\|fail\|pending\|none), sha, url, startedAt, event. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`workflow` (string, optional)<br>`ref` (string, optional)<br>`status` (string, optional)<br>`limit` (string, optional) |
| `ci_trigger_workflow` | Trigger a CI run (fire-and-return run handle). `inputs` is a flat string map (JSON object string); non-string values are stringified at the alias boundary. | `workspace` (string, **required**)<br>`repository` (string, **required**)<br>`workflow` (string, **required**)<br>`ref` (string, optional)<br>`inputs` (string, optional) |

## Detailed Parameter Information

### `ci_get_merge_state`

Mergeability of a PR/MR in the enum the SM speaks: CLEAN | BEHIND | DIRTY | BLOCKED | UNKNOWN. Both vendors' checks-settling transients return BLOCKED + reason.

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
const result = ci_get_merge_state("workspace", "repository", "pr");
```

---

### `ci_get_verdict`

THE CI verdict for a run: pass | fail | pending | none. Call with `runId`, or probe a head with `sha` (plus optional `workflow` for the stale-verdict fallback), or on GitLab with `pr`.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`runId`** (string) ⚪ Optional
  - Run handle (from ci_trigger_workflow/ci_list_runs)

- **`sha`** (string) ⚪ Optional
  - Head commit sha to probe

- **`workflow`** (string) ⚪ Optional
  - Workflow file for the sha probe fallback

- **`pr`** (string) ⚪ Optional
  - PR/MR number (GitLab pipelines-of-MR probe)

**Example:**
```javascript
// In JavaScript agent
const result = ci_get_verdict("workspace", "repository", "runId", "sha", "workflow", "pr");
```

---

### `ci_list_runs`

List CI runs (newest first). Each run: runId, status, verdict (pass|fail|pending|none), sha, url, startedAt, event.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`workflow`** (string) ⚪ Optional
  - Workflow file / id filter

- **`ref`** (string) ⚪ Optional
  - Branch or tag ref filter

- **`status`** (string) ⚪ Optional
  - Provider run-status filter

- **`limit`** (string) ⚪ Optional
  - Max runs to return (default 30)

**Example:**
```javascript
// In JavaScript agent
const result = ci_list_runs("workspace", "repository", "workflow", "ref", "status", "limit");
```

---

### `ci_trigger_workflow`

Trigger a CI run (fire-and-return run handle). `inputs` is a flat string map (JSON object string); non-string values are stringified at the alias boundary.

**Parameters:**

- **`workspace`** (string) 🔴 Required
  - Repository owner / namespace

- **`repository`** (string) 🔴 Required
  - Repository name

- **`workflow`** (string) 🔴 Required
  - Workflow file / id

- **`ref`** (string) ⚪ Optional
  - Branch or tag ref (default: main)

- **`inputs`** (string) ⚪ Optional
  - Flat string map of run inputs (JSON object)

**Example:**
```javascript
// In JavaScript agent
const result = ci_trigger_workflow("workspace", "repository", "workflow", "ref", "inputs");
```

---
