# JENKINS MCP Tools

**Total Tools**: 7

## Quick Reference

```bash
# List all jenkins tools
dmtools list | jq '.tools[] | select(.name | startswith("jenkins_"))'

# Example usage
dmtools jenkins_test [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for jenkins tools
const result = jenkins_test(...);
const result = jenkins_list_builds(...);
const result = jenkins_get_job_info(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `jenkins_get_build_log` | Get the raw console log text of a specific Jenkins build. | `jobPath` (string, **required**)<br>`buildNumber` (number, **required**) |
| `jenkins_get_job_info` | Get details of a specific Jenkins build by job path and build number. | `jobPath` (string, **required**)<br>`buildNumber` (number, **required**) |
| `jenkins_get_queue_item` | Check the status of a Jenkins queue item — the 'queueUrl' returned by jenkins_trigger_job. While the job is queued/blocked (e.g. waiting for an executor or an upstream lock) there is no build number yet; once Jenkins starts executing it, the response includes 'buildNumber'/'buildUrl'. Returns 'cancelled': true if the queued item was cancelled instead of executed. | `queueUrl` (string, **required**) |
| `jenkins_list_builds` | List recent builds for a Jenkins job. Provide the job path as folder/job-name. | `jobPath` (string, **required**)<br>`limit` (number, optional) |
| `jenkins_test` | Test Jenkins connectivity by fetching the root system information | None |
| `jenkins_trigger_job` | Trigger a Jenkins job. Provide parameters as a JSON object to use buildWithParameters. Returns 'queueUrl' — the Jenkins queue item URL (from the response's Location header) — which can be resolved to a build number via jenkins_get_queue_item. Prefer jenkins_trigger_job_and_wait when the caller needs to block until the build finishes. | `jobPath` (string, **required**)<br>`parametersJson` (string, optional) |
| `jenkins_trigger_job_and_wait` | Trigger a Jenkins job and block until the resulting build finishes (or a timeout elapses). Internally resolves the queue item to a build number (falling back to build-number polling if no queue URL is available), then polls the build until it is no longer 'building'. Use this instead of jenkins_trigger_job + manual polling when the caller needs a synchronous result — e.g. before continuing a git workflow that depends on the triggered job's outcome (such as a branch-creation job that must finish before the branch can be checked out). | `jobPath` (string, **required**)<br>`parametersJson` (string, optional)<br>`pollIntervalSeconds` (number, optional)<br>`timeoutSeconds` (number, optional) |

## Detailed Parameter Information

### `jenkins_get_build_log`

Get the raw console log text of a specific Jenkins build.

**Parameters:**

- **`jobPath`** (string) 🔴 Required
  - Jenkins job path, e.g. folder/job-name

- **`buildNumber`** (number) 🔴 Required
  - Build number

**Example:**
```bash
dmtools jenkins_get_build_log "value" "value"
```

```javascript
// In JavaScript agent
const result = jenkins_get_build_log("jobPath", "buildNumber");
```

---

### `jenkins_get_job_info`

Get details of a specific Jenkins build by job path and build number.

**Parameters:**

- **`jobPath`** (string) 🔴 Required
  - Jenkins job path, e.g. folder/job-name

- **`buildNumber`** (number) 🔴 Required
  - Build number

**Example:**
```bash
dmtools jenkins_get_job_info "value" "value"
```

```javascript
// In JavaScript agent
const result = jenkins_get_job_info("jobPath", "buildNumber");
```

---

### `jenkins_get_queue_item`

Check the status of a Jenkins queue item — the 'queueUrl' returned by jenkins_trigger_job. While the job is queued/blocked (e.g. waiting for an executor or an upstream lock) there is no build number yet; once Jenkins starts executing it, the response includes 'buildNumber'/'buildUrl'. Returns 'cancelled': true if the queued item was cancelled instead of executed.

**Parameters:**

- **`queueUrl`** (string) 🔴 Required
  - Queue item URL returned by jenkins_trigger_job's 'queueUrl' field, e.g. https://jenkins.example.com/queue/item/12345/

**Example:**
```bash
dmtools jenkins_get_queue_item "value"
```

```javascript
// In JavaScript agent
const result = jenkins_get_queue_item("queueUrl");
```

---

### `jenkins_list_builds`

List recent builds for a Jenkins job. Provide the job path as folder/job-name.

**Parameters:**

- **`jobPath`** (string) 🔴 Required
  - Jenkins job path, e.g. folder/job-name

- **`limit`** (number) ⚪ Optional
  - Max number of builds to return

**Example:**
```bash
dmtools jenkins_list_builds "value" "value"
```

```javascript
// In JavaScript agent
const result = jenkins_list_builds("jobPath", "limit");
```

---

### `jenkins_test`

Test Jenkins connectivity by fetching the root system information

**Parameters:** None

**Example:**
```bash
dmtools jenkins_test
```

```javascript
// In JavaScript agent
const result = jenkins_test();
```

---

### `jenkins_trigger_job`

Trigger a Jenkins job. Provide parameters as a JSON object to use buildWithParameters. Returns 'queueUrl' — the Jenkins queue item URL (from the response's Location header) — which can be resolved to a build number via jenkins_get_queue_item. Prefer jenkins_trigger_job_and_wait when the caller needs to block until the build finishes.

**Parameters:**

- **`jobPath`** (string) 🔴 Required
  - Jenkins job path, e.g. folder/job-name

- **`parametersJson`** (string) ⚪ Optional
  - JSON object of parameter names to values, e.g. {"BRANCH":"main"}

**Example:**
```bash
dmtools jenkins_trigger_job "value" "value"
```

```javascript
// In JavaScript agent
const result = jenkins_trigger_job("jobPath", "parametersJson");
```

---

### `jenkins_trigger_job_and_wait`

Trigger a Jenkins job and block until the resulting build finishes (or a timeout elapses). Internally resolves the queue item to a build number (falling back to build-number polling if no queue URL is available), then polls the build until it is no longer 'building'. Use this instead of jenkins_trigger_job + manual polling when the caller needs a synchronous result — e.g. before continuing a git workflow that depends on the triggered job's outcome (such as a branch-creation job that must finish before the branch can be checked out).

**Parameters:**

- **`jobPath`** (string) 🔴 Required
  - Jenkins job path, e.g. folder/job-name

- **`parametersJson`** (string) ⚪ Optional
  - JSON object of parameter names to values, e.g. {"BRANCH":"main"}

- **`pollIntervalSeconds`** (number) ⚪ Optional
  - Seconds to wait between status checks. Default: 10

- **`timeoutSeconds`** (number) ⚪ Optional
  - Maximum seconds to wait before giving up. Default: 600 (10 minutes)

**Example:**
```bash
dmtools jenkins_trigger_job_and_wait "value" "value"
```

```javascript
// In JavaScript agent
const result = jenkins_trigger_job_and_wait("jobPath", "parametersJson");
```

---

