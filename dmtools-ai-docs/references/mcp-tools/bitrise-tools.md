# BITRISE MCP Tools

**Total Tools**: 24

## Quick Reference

```bash
# List all bitrise tools
dmtools list | jq '.tools[] | select(.name | startswith("bitrise_"))'

# Example usage
dmtools bitrise_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for bitrise tools
const result = bitrise_test(...);
const result = bitrise_list_apps(...);
const result = bitrise_get_app(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `bitrise_abort_build` | Abort a running Bitrise build with an optional reason message. | `appSlug` (string, **required**)<br>`buildSlug` (string, **required**)<br>`reason` (string, optional) |
| `bitrise_abort_pipeline` | Abort a running Bitrise pipeline. | `appSlug` (string, **required**)<br>`pipelineId` (string, **required**) |
| `bitrise_delete_secret` | Delete a secret environment variable from a Bitrise app. | `appSlug` (string, **required**)<br>`secretName` (string, **required**) |
| `bitrise_get_app` | Get details of a specific Bitrise app by its slug. | `appSlug` (string, **required**) |
| `bitrise_get_build` | Get details and current status of a specific Bitrise build. Returns status, branch, workflow, timing, commit info. | `appSlug` (string, **required**)<br>`buildSlug` (string, **required**) |
| `bitrise_get_build_artifact` | Get details and expiring download URL for a specific Bitrise build artifact. | `appSlug` (string, **required**)<br>`buildSlug` (string, **required**)<br>`artifactSlug` (string, **required**) |
| `bitrise_get_build_log` | Get the full log of a Bitrise build. Returns log chunks and expiring download URL for the complete log. | `appSlug` (string, **required**)<br>`buildSlug` (string, **required**) |
| `bitrise_get_pipeline` | Get details of a specific Bitrise pipeline run by its ID. | `appSlug` (string, **required**)<br>`pipelineId` (string, **required**) |
| `bitrise_get_secret` | Get metadata for a specific secret by name. The value is not returned unless it's non-protected. | `appSlug` (string, **required**)<br>`secretName` (string, **required**) |
| `bitrise_get_secret_value` | Retrieve the plaintext value of a non-protected secret. Returns 403 if the secret is marked as protected. | `appSlug` (string, **required**)<br>`secretName` (string, **required**) |
| `bitrise_get_yml` | Download the bitrise.yml configuration file for a Bitrise app. | `appSlug` (string, **required**) |
| `bitrise_get_yml_config` | Get the JSON-structured config representation of the bitrise.yml for a Bitrise app. | `appSlug` (string, **required**) |
| `bitrise_list_apps` | List all Bitrise apps accessible with the current token. Returns app slugs, titles, project types and repo URLs. | `sortBy` (string, optional)<br>`title` (string, optional)<br>`limit` (number, optional) |
| `bitrise_list_build_artifacts` | List all artifacts produced by a Bitrise build (APKs, IPAs, logs, test results, etc.). | `appSlug` (string, **required**)<br>`buildSlug` (string, **required**) |
| `bitrise_list_builds` | List builds for a Bitrise app. Optionally filter by workflow, branch or status. Status codes: not_started, in_progress, success, failed, aborted. | `appSlug` (string, **required**)<br>`workflowId` (string, optional)<br>`branch` (string, optional)<br>`status` (string, optional)<br>`limit` (number, optional)<br>`next` (string, optional) |
| `bitrise_list_pipelines` | List pipeline runs for a Bitrise app. | `appSlug` (string, **required**) |
| `bitrise_list_secrets` | List all secret environment variables for a Bitrise app. Values are not returned by default (protected). | `appSlug` (string, **required**) |
| `bitrise_list_workflows` | List all available workflow IDs defined in the bitrise.yml for a Bitrise app. | `appSlug` (string, **required**) |
| `bitrise_test` | Test Bitrise connectivity by fetching the current user's profile | None |
| `bitrise_trigger_build` | Trigger a new Bitrise workflow build for an app. Supports custom branch, environment variables, and workflow selection. | `appSlug` (string, **required**)<br>`workflowId` (string, **required**)<br>`branch` (string, optional)<br>`commitMessage` (string, optional)<br>`envVars` (string, optional) |
| `bitrise_update_yml` | Upload/replace the bitrise.yml configuration file for a Bitrise app. Pass either the full YAML content or a local file path ending in .yml/.yaml. The YAML is validated via the Bitrise API before uploading. | `appSlug` (string, **required**)<br>`ymlContent` (string, **required**) |
| `bitrise_update_yml_config` | Update the bitrise.yml using a JSON-structured config representation for a Bitrise app. | `appSlug` (string, **required**)<br>`configJson` (string, **required**) |
| `bitrise_upsert_secret` | Create or update a secret environment variable for a Bitrise app. | `appSlug` (string, **required**)<br>`secretName` (string, **required**)<br>`value` (string, **required**)<br>`isProtected` (boolean, optional)<br>`isExposedForPullRequests` (boolean, optional)<br>`expandInStepInputs` (boolean, optional) |
| `bitrise_validate_yml` | Validate a bitrise.yml file content via the Bitrise API. Returns validation errors and warnings without modifying the app configuration. Accepts YAML content or a local file path. | `ymlContent` (string, **required**)<br>`appSlug` (string, optional) |

## Detailed Parameter Information

### `bitrise_abort_build`

Abort a running Bitrise build with an optional reason message.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`buildSlug`** (string) 🔴 Required
  - The build slug to abort
  - Example: `build_slug_abc`

- **`reason`** (string) ⚪ Optional
  - Human-readable reason for aborting the build
  - Example: `Superseded by newer build`

**Example:**
```bash
dmtools bitrise_abort_build "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_abort_build("appSlug", "buildSlug");
```

---

### `bitrise_abort_pipeline`

Abort a running Bitrise pipeline.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`pipelineId`** (string) 🔴 Required
  - The pipeline run ID (UUID) to abort
  - Example: `pipeline-uuid-here`

**Example:**
```bash
dmtools bitrise_abort_pipeline "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_abort_pipeline("appSlug", "pipelineId");
```

---

### `bitrise_delete_secret`

Delete a secret environment variable from a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`secretName`** (string) 🔴 Required
  - The secret name to delete
  - Example: `MY_API_KEY`

**Example:**
```bash
dmtools bitrise_delete_secret "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_delete_secret("appSlug", "secretName");
```

---

### `bitrise_get_app`

Get details of a specific Bitrise app by its slug.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The app slug identifier
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_get_app "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_app("appSlug");
```

---

### `bitrise_get_build`

Get details and current status of a specific Bitrise build. Returns status, branch, workflow, timing, commit info.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`buildSlug`** (string) 🔴 Required
  - The build slug identifier
  - Example: `build_slug_abc`

**Example:**
```bash
dmtools bitrise_get_build "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_build("appSlug", "buildSlug");
```

---

### `bitrise_get_build_artifact`

Get details and expiring download URL for a specific Bitrise build artifact.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`buildSlug`** (string) 🔴 Required
  - The build slug
  - Example: `build_slug_abc`

- **`artifactSlug`** (string) 🔴 Required
  - The artifact slug
  - Example: `artifact_slug_xyz`

**Example:**
```bash
dmtools bitrise_get_build_artifact "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_build_artifact("appSlug", "buildSlug");
```

---

### `bitrise_get_build_log`

Get the full log of a Bitrise build. Returns log chunks and expiring download URL for the complete log.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`buildSlug`** (string) 🔴 Required
  - The build slug
  - Example: `build_slug_abc`

**Example:**
```bash
dmtools bitrise_get_build_log "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_build_log("appSlug", "buildSlug");
```

---

### `bitrise_get_pipeline`

Get details of a specific Bitrise pipeline run by its ID.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`pipelineId`** (string) 🔴 Required
  - The pipeline run ID (UUID)
  - Example: `pipeline-uuid-here`

**Example:**
```bash
dmtools bitrise_get_pipeline "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_pipeline("appSlug", "pipelineId");
```

---

### `bitrise_get_secret`

Get metadata for a specific secret by name. The value is not returned unless it's non-protected.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`secretName`** (string) 🔴 Required
  - The secret environment variable name
  - Example: `MY_API_KEY`

**Example:**
```bash
dmtools bitrise_get_secret "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_secret("appSlug", "secretName");
```

---

### `bitrise_get_secret_value`

Retrieve the plaintext value of a non-protected secret. Returns 403 if the secret is marked as protected.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`secretName`** (string) 🔴 Required
  - The secret name
  - Example: `MY_API_KEY`

**Example:**
```bash
dmtools bitrise_get_secret_value "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_secret_value("appSlug", "secretName");
```

---

### `bitrise_get_yml`

Download the bitrise.yml configuration file for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_get_yml "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_yml("appSlug");
```

---

### `bitrise_get_yml_config`

Get the JSON-structured config representation of the bitrise.yml for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_get_yml_config "value"
```

```javascript
// In JavaScript agent
const result = bitrise_get_yml_config("appSlug");
```

---

### `bitrise_list_apps`

List all Bitrise apps accessible with the current token. Returns app slugs, titles, project types and repo URLs.

**Parameters:**

- **`sortBy`** (string) ⚪ Optional
  - Sort apps by: last_build_at or created_at
  - Example: `last_build_at`

- **`title`** (string) ⚪ Optional
  - Filter apps by title (case-insensitive substring match)
  - Example: `MyApp`

- **`limit`** (number) ⚪ Optional
  - Max number of apps to return (1-50, default 50)
  - Example: `20`

**Example:**
```bash
dmtools bitrise_list_apps "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_apps("sortBy", "title");
```

---

### `bitrise_list_build_artifacts`

List all artifacts produced by a Bitrise build (APKs, IPAs, logs, test results, etc.).

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`buildSlug`** (string) 🔴 Required
  - The build slug
  - Example: `build_slug_abc`

**Example:**
```bash
dmtools bitrise_list_build_artifacts "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_build_artifacts("appSlug", "buildSlug");
```

---

### `bitrise_list_builds`

List builds for a Bitrise app. Optionally filter by workflow, branch or status. Status codes: not_started, in_progress, success, failed, aborted.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`workflowId`** (string) ⚪ Optional
  - Filter by workflow ID / name
  - Example: `primary`

- **`branch`** (string) ⚪ Optional
  - Filter by branch name
  - Example: `main`

- **`status`** (string) ⚪ Optional
  - Filter by status: not_started | in_progress | success | failed | aborted
  - Example: `failed`

- **`limit`** (number) ⚪ Optional
  - Max results to return (default 20, max 100)
  - Example: `50`

- **`next`** (string) ⚪ Optional
  - Pagination cursor from previous response paging.next
  - Example: `next_cursor_value`

**Example:**
```bash
dmtools bitrise_list_builds "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_builds("appSlug", "workflowId");
```

---

### `bitrise_list_pipelines`

List pipeline runs for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_list_pipelines "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_pipelines("appSlug");
```

---

### `bitrise_list_secrets`

List all secret environment variables for a Bitrise app. Values are not returned by default (protected).

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_list_secrets "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_secrets("appSlug");
```

---

### `bitrise_list_workflows`

List all available workflow IDs defined in the bitrise.yml for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_list_workflows "value"
```

```javascript
// In JavaScript agent
const result = bitrise_list_workflows("appSlug");
```

---

### `bitrise_test`

Test Bitrise connectivity by fetching the current user's profile

**Parameters:** None

**Example:**
```bash
dmtools bitrise_test
```

```javascript
// In JavaScript agent
const result = bitrise_test();
```

---

### `bitrise_trigger_build`

Trigger a new Bitrise workflow build for an app. Supports custom branch, environment variables, and workflow selection.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`workflowId`** (string) 🔴 Required
  - Workflow ID to trigger (e.g. 'primary', 'deploy')
  - Example: `primary`

- **`branch`** (string) ⚪ Optional
  - Branch to build (defaults to main/master)
  - Example: `main`

- **`commitMessage`** (string) ⚪ Optional
  - Commit message for the build
  - Example: `Triggered by DMtools`

- **`envVars`** (string) ⚪ Optional
  - JSON array of env var objects: [{"mapped_to":"KEY","value":"val","is_expand":true}]
  - Example: `[{"mapped_to":"MY_VAR","value":"hello"}]`

**Example:**
```bash
dmtools bitrise_trigger_build "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_trigger_build("appSlug", "workflowId");
```

---

### `bitrise_update_yml`

Upload/replace the bitrise.yml configuration file for a Bitrise app. Pass either the full YAML content or a local file path ending in .yml/.yaml. The YAML is validated via the Bitrise API before uploading.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`ymlContent`** (string) 🔴 Required
  - Full YAML content OR a local file path to a .yml/.yaml file
  - Example: `/path/to/bitrise.yml`

**Example:**
```bash
dmtools bitrise_update_yml "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_update_yml("appSlug", "ymlContent");
```

---

### `bitrise_update_yml_config`

Update the bitrise.yml using a JSON-structured config representation for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`configJson`** (string) 🔴 Required
  - JSON string of the bitrise.yml config object
  - Example: `{"format_version":"11","workflows":{}}`

**Example:**
```bash
dmtools bitrise_update_yml_config "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_update_yml_config("appSlug", "configJson");
```

---

### `bitrise_upsert_secret`

Create or update a secret environment variable for a Bitrise app.

**Parameters:**

- **`appSlug`** (string) 🔴 Required
  - The Bitrise app slug
  - Example: `abc123def456`

- **`secretName`** (string) 🔴 Required
  - The secret name (environment variable key)
  - Example: `MY_API_KEY`

- **`value`** (string) 🔴 Required
  - The secret value
  - Example: `s3cr3t_v4lu3`

- **`isProtected`** (boolean) ⚪ Optional
  - If true the value cannot be read back via API (default: true)
  - Example: `true`

- **`isExposedForPullRequests`** (boolean) ⚪ Optional
  - Whether the secret is available in PR builds
  - Example: `false`

- **`expandInStepInputs`** (boolean) ⚪ Optional
  - Whether the value is expanded (interpolated) in step inputs
  - Example: `true`

**Example:**
```bash
dmtools bitrise_upsert_secret "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_upsert_secret("appSlug", "secretName");
```

---

### `bitrise_validate_yml`

Validate a bitrise.yml file content via the Bitrise API. Returns validation errors and warnings without modifying the app configuration. Accepts YAML content or a local file path.

**Parameters:**

- **`ymlContent`** (string) 🔴 Required
  - Full YAML content OR a local file path to a .yml/.yaml file to validate
  - Example: `/path/to/bitrise.yml`

- **`appSlug`** (string) ⚪ Optional
  - Optional app slug for app-specific validation (stack, machines, licenses)
  - Example: `abc123def456`

**Example:**
```bash
dmtools bitrise_validate_yml "value" "value"
```

```javascript
// In JavaScript agent
const result = bitrise_validate_yml("ymlContent", "appSlug");
```

---

