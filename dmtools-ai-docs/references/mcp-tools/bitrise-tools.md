# Bitrise MCP Tools Reference

**Total tools**: 23  
**Integration key**: `bitrise`  
**Categories**: `apps`, `builds`, `artifacts`, `config`, `secrets`, `pipelines`

## Quick Start

```bash
# Set up credentials
export BITRISE_TOKEN=your_personal_access_token
export BITRISE_APP_SLUG=your_default_app_slug   # optional

# List your apps
dmtools bitrise_list_apps

# Trigger a build
dmtools bitrise_trigger_build appSlug=abc123 workflowId=primary branch=main

# Check build status
dmtools bitrise_get_build appSlug=abc123 buildSlug=build_slug_here

# List artifacts
dmtools bitrise_list_build_artifacts appSlug=abc123 buildSlug=build_slug_here
```

## Configuration

| Environment Variable | Required | Description |
|---------------------|----------|-------------|
| `BITRISE_TOKEN` | ✅ | Personal Access Token from [bitrise.io account settings](https://app.bitrise.io/me/profile#/security) |
| `BITRISE_APP_SLUG` | ❌ | Default app slug (avoids repeating it in every call) |
| `BITRISE_BASE_PATH` | ❌ | Override API base URL (default: `https://api.bitrise.io/v0.1`) |

Get your token: **bitrise.io → Account Settings → Security → Personal Access Tokens**

---

## Apps

### `bitrise_list_apps`

List all Bitrise apps accessible with the current token.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sortBy` | String | ❌ | Sort by: `last_build_at` or `created_at` |
| `title` | String | ❌ | Filter by app title (substring) |
| `limit` | Integer | ❌ | Max results (1–50, default 50) |

```bash
dmtools bitrise_list_apps sortBy=last_build_at limit=10
```

Returns array of app objects with `slug`, `title`, `project_type`, `repo_url`, `status`.

---

### `bitrise_get_app`

Get details of a specific Bitrise app.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug identifier |

```bash
dmtools bitrise_get_app appSlug=abc123def456
```

---

## Builds

### `bitrise_list_builds`

List builds for a Bitrise app with optional filters.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `workflowId` | String | ❌ | Filter by workflow name (e.g. `primary`) |
| `branch` | String | ❌ | Filter by branch name |
| `status` | String | ❌ | Filter by status: `not_started` \| `in_progress` \| `success` \| `failed` \| `aborted` |
| `limit` | Integer | ❌ | Max results (default 20, max 100) |
| `next` | String | ❌ | Pagination cursor from `paging.next` |

```bash
# All failed builds on main
dmtools bitrise_list_builds appSlug=abc123 branch=main status=failed limit=20

# In-progress builds for a specific workflow
dmtools bitrise_list_builds appSlug=abc123 workflowId=deploy status=in_progress
```

Build status codes: `0`=not started, `1`=in progress, `2`=success, `3`=failed, `4`=aborted.

---

### `bitrise_trigger_build`

Trigger a new Bitrise workflow build.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `workflowId` | String | ✅ | Workflow to run (e.g. `primary`, `deploy`) |
| `branch` | String | ❌ | Branch to build (default: repo default branch) |
| `commitMessage` | String | ❌ | Commit message for the build |
| `envVars` | String | ❌ | JSON array of env overrides: `[{"mapped_to":"KEY","value":"val","is_expand":true}]` |

```bash
# Simple trigger
dmtools bitrise_trigger_build appSlug=abc123 workflowId=primary branch=main

# With env var overrides
dmtools bitrise_trigger_build appSlug=abc123 workflowId=deploy \
  envVars='[{"mapped_to":"DEPLOY_ENV","value":"staging"}]'
```

Returns JSON with `build_slug` and `build_number` — save these to poll status.

**JSRunner example:**
```javascript
const result = JSON.parse(bitrise_trigger_build('abc123', 'primary', 'main', 'Triggered by AI', ''));
const buildSlug = result.build_slug;
print('Build triggered: ' + buildSlug);
```

---

### `bitrise_get_build`

Get current status and details of a specific build.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `buildSlug` | String | ✅ | Build slug |

```bash
dmtools bitrise_get_build appSlug=abc123 buildSlug=build_slug_abc
```

Returns: `slug`, `build_number`, `status` (0–4), `status_text`, `branch`, `triggered_workflow`, `started_on_worker_at`, `finished_at`, `duration`.

**Poll until complete (JSRunner):**
```javascript
function waitForBuild(appSlug, buildSlug, maxWaitMs) {
  const start = Date.now();
  while (Date.now() - start < maxWaitMs) {
    const build = JSON.parse(bitrise_get_build(appSlug, buildSlug));
    const data = build.data || build;
    if (data.status === 2) return 'success';
    if (data.status === 3) return 'failed';
    if (data.status === 4) return 'aborted';
    sleep(30000); // wait 30 seconds between polls
  }
  return 'timeout';
}
```

---

### `bitrise_abort_build`

Abort a running build.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `buildSlug` | String | ✅ | Build slug to abort |
| `reason` | String | ❌ | Human-readable reason for aborting |

```bash
dmtools bitrise_abort_build appSlug=abc123 buildSlug=build_slug_abc reason="Superseded by newer build"
```

---

### `bitrise_get_build_log`

Get the full log of a completed or running build.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `buildSlug` | String | ✅ | Build slug |

```bash
dmtools bitrise_get_build_log appSlug=abc123 buildSlug=build_slug_abc
```

Returns `log_chunks` (array of log text parts) and `expiring_download_url` for the full archived log.

---

### `bitrise_list_workflows`

List all workflow IDs defined in the app's `bitrise.yml`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |

```bash
dmtools bitrise_list_workflows appSlug=abc123
```

Returns an array of workflow ID strings (e.g. `["primary", "deploy", "test"]`).

---

## Artifacts

### `bitrise_list_build_artifacts`

List all artifacts produced by a build (APKs, IPAs, test reports, etc.).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `buildSlug` | String | ✅ | Build slug |

```bash
dmtools bitrise_list_build_artifacts appSlug=abc123 buildSlug=build_slug_abc
```

Returns array of artifact objects with `slug`, `title`, `artifact_type`, `file_size_bytes`.

---

### `bitrise_get_build_artifact`

Get details and an expiring download URL for a specific artifact.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `buildSlug` | String | ✅ | Build slug |
| `artifactSlug` | String | ✅ | Artifact slug (from `bitrise_list_build_artifacts`) |

```bash
dmtools bitrise_get_build_artifact appSlug=abc123 buildSlug=build_slug_abc artifactSlug=artifact_xyz
```

Returns: `slug`, `title`, `artifact_type`, `expiring_download_url` (valid ~10 min), `file_size_bytes`, `public_install_page_url`.

---

## bitrise.yml Configuration

### `bitrise_get_yml`

Download the raw `bitrise.yml` for an app.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |

```bash
dmtools bitrise_get_yml appSlug=abc123
```

---

### `bitrise_update_yml`

Upload/replace the `bitrise.yml` for an app. Automatically validates the YAML before uploading.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `ymlContent` | String | ✅ | YAML content **or a file path** ending in `.yml`/`.yaml` (file is read automatically) |

```bash
# Pass inline YAML
dmtools bitrise_update_yml appSlug=abc123 ymlContent="format_version: '11'\n..."

# Pass a file path — content is read from disk and validated before upload
dmtools bitrise_update_yml appSlug=abc123 ymlContent=/path/to/bitrise.yml
```

⚠️ This replaces the entire `bitrise.yml`. Always fetch the current version first with `bitrise_get_yml`.  
⚠️ Validation runs automatically before upload via `POST /validate-bitrise-yml`; the upload is aborted on validation failure.

---

### `bitrise_validate_yml`

Validate a `bitrise.yml` against the Bitrise API without uploading it.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `ymlContent` | String | ✅ | YAML content **or a file path** (same resolution logic as `bitrise_update_yml`) |
| `appSlug` | String | ❌ | Optional — validate against a specific app's constraints |

```bash
# Validate a local file
dmtools bitrise_validate_yml ymlContent=/path/to/bitrise.yml

# Validate inline YAML scoped to an app
dmtools bitrise_validate_yml appSlug=abc123 ymlContent="format_version: '11'\n..."
```

---

### `bitrise_get_yml_config`

Get the JSON-structured representation of the `bitrise.yml`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |

```bash
dmtools bitrise_get_yml_config appSlug=abc123
```

---

### `bitrise_update_yml_config`

Update the `bitrise.yml` using a JSON-structured config object.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `configJson` | String | ✅ | JSON representation of the bitrise.yml config |

```bash
dmtools bitrise_update_yml_config appSlug=abc123 configJson='{"format_version":"11","workflows":{}}'
```

---

## Secrets & Environment Variables

### `bitrise_list_secrets`

List all secret environment variables for an app. **Values are not returned** for protected secrets.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |

```bash
dmtools bitrise_list_secrets appSlug=abc123
```

---

### `bitrise_get_secret`

Get metadata for a specific secret (name, protection status). Value is returned only for non-protected secrets.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `secretName` | String | ✅ | Environment variable name (e.g. `MY_API_KEY`) |

```bash
dmtools bitrise_get_secret appSlug=abc123 secretName=MY_API_KEY
```

---

### `bitrise_upsert_secret`

Create or update a secret. Uses PUT (update) with automatic fallback to POST (create) on 404.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `secretName` | String | ✅ | Environment variable name |
| `value` | String | ✅ | Secret value |
| `isProtected` | Boolean | ❌ | Prevent value from being read back via API (default: server default) |
| `isExposedForPullRequests` | Boolean | ❌ | Expose to PR builds (default: false) |
| `expandInStepInputs` | Boolean | ❌ | Expand/interpolate in step inputs |

```bash
# Create protected secret
dmtools bitrise_upsert_secret appSlug=abc123 secretName=DEPLOY_KEY \
  value=s3cr3t isProtected=true isExposedForPullRequests=false

# Expose to PR builds (less sensitive)
dmtools bitrise_upsert_secret appSlug=abc123 secretName=PUBLIC_ENDPOINT \
  value=https://staging.example.com isProtected=false isExposedForPullRequests=true
```

---

### `bitrise_delete_secret`

Delete a secret environment variable.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `secretName` | String | ✅ | Secret name to delete |

```bash
dmtools bitrise_delete_secret appSlug=abc123 secretName=OLD_SECRET
```

---

### `bitrise_get_secret_value`

Retrieve the plaintext value of a **non-protected** secret. Returns HTTP 403 for protected secrets.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `secretName` | String | ✅ | Secret name |

```bash
dmtools bitrise_get_secret_value appSlug=abc123 secretName=NON_PROTECTED_VAR
```

---

## Pipelines

### `bitrise_list_pipelines`

List pipeline runs for an app.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |

```bash
dmtools bitrise_list_pipelines appSlug=abc123
```

---

### `bitrise_get_pipeline`

Get details of a specific pipeline run.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `pipelineId` | String | ✅ | Pipeline UUID |

```bash
dmtools bitrise_get_pipeline appSlug=abc123 pipelineId=pipeline-uuid-here
```

---

### `bitrise_abort_pipeline`

Abort a running pipeline.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `appSlug` | String | ✅ | App slug |
| `pipelineId` | String | ✅ | Pipeline UUID to abort |

```bash
dmtools bitrise_abort_pipeline appSlug=abc123 pipelineId=pipeline-uuid-here
```

---

## JSRunner Integration

> ⚠️ **Security Warning**: JSRunner scripts may log all function arguments (including parameter values) for
> debugging purposes. **Never pass raw secret values** to tools like `bitrise_upsert_secret` from JSRunner
> scripts. Store secrets in environment variables and reference them by name, or use the Bitrise Secrets UI.

All Bitrise tools are available in JavaScript agent scripts. The function signature mirrors the MCP parameters positionally:

```javascript
// Trigger build and poll until done
function triggerAndWait(appSlug, workflowId, branch) {
  // Trigger
  const triggerResult = JSON.parse(
    bitrise_trigger_build(appSlug, workflowId, branch, 'Triggered by agent', '')
  );
  const buildSlug = triggerResult.build_slug || triggerResult.data?.slug;
  print('Build triggered: ' + buildSlug);

  // Poll
  const maxWait = 30 * 60 * 1000; // 30 min
  const start = Date.now();
  while (Date.now() - start < maxWait) {
    const buildInfo = JSON.parse(bitrise_get_build(appSlug, buildSlug));
    const data = buildInfo.data || buildInfo;
    const status = data.status;
    print('Build status: ' + data.status_text + ' (' + status + ')');
    if (status === 2) return { success: true, buildSlug };
    if (status === 3) return { success: false, reason: 'failed', buildSlug };
    if (status === 4) return { success: false, reason: 'aborted', buildSlug };
    sleep(30000);
  }
  return { success: false, reason: 'timeout', buildSlug };
}

// Read artifacts from a completed build
function getArtifactUrls(appSlug, buildSlug) {
  const artifacts = JSON.parse(bitrise_list_build_artifacts(appSlug, buildSlug));
  return (artifacts.data || []).map(a => ({
    name: a.title,
    url: a.expiring_download_url,
    type: a.artifact_type
  }));
}

// Update a secret
bitrise_upsert_secret('abc123', 'NEW_SECRET', 'value123', true, false, true);

// Read bitrise.yml
const yml = bitrise_get_yml('abc123');
print('Current yml:\n' + yml);
```

---

## Common Patterns

### CI/CD Automation
```javascript
// 1. Trigger deploy workflow
const build = JSON.parse(bitrise_trigger_build(appSlug, 'deploy', 'main', '', ''));

// 2. Wait for completion
const status = waitForBuild(appSlug, build.build_slug, 45 * 60 * 1000);

// 3. Get artifacts if successful
if (status === 'success') {
  const artifacts = JSON.parse(bitrise_list_build_artifacts(appSlug, build.build_slug));
  print('Artifacts: ' + JSON.stringify(artifacts.data.map(a => a.title)));
}
```

### Secret Rotation
```javascript
// Rotate a secret value
bitrise_upsert_secret(appSlug, 'API_KEY', newKeyValue, true, false, true);
print('Secret rotated successfully');
```

### bitrise.yml Inspection
```javascript
// List all workflows defined in the app
const workflows = JSON.parse(bitrise_list_workflows(appSlug));
print('Available workflows: ' + JSON.stringify(workflows));

// Download and inspect current yml
const yml = bitrise_get_yml(appSlug);
print('bitrise.yml length: ' + yml.length + ' chars');
```
