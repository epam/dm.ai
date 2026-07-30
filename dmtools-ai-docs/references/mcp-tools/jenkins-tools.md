# Jenkins MCP Tools

**Total Tools**: 7

## Available Tools

| Tool Name | Description | Category |
|-----------|-------------|----------|
| `jenkins_test` | Test Jenkins connectivity by fetching the root system information | system |
| `jenkins_list_builds` | List recent builds for a Jenkins job. Provide the job path as folder/job-name. | builds |
| `jenkins_get_job_info` | Get details of a specific Jenkins build by job path and build number. | builds |
| `jenkins_get_build_log` | Get the raw console log text of a specific Jenkins build. | builds |
| `jenkins_trigger_job` | Trigger a Jenkins job. Provide parameters as a JSON object to use buildWithParameters. Returns 'queueUrl' — the Jenkins queue item URL (from the response's Location header) — which can be resolved to a build number via jenkins_get_queue_item. Prefer jenkins_trigger_job_and_wait when the caller needs to block until the build finishes. | builds |
| `jenkins_get_queue_item` | Check the status of a Jenkins queue item — the 'queueUrl' returned by jenkins_trigger_job. While the job is queued/blocked (e.g. waiting for an executor or an upstream lock) there is no build number yet; once Jenkins starts executing it, the response includes 'buildNumber'/'buildUrl'. Returns 'cancelled': true if the queued item was cancelled instead of executed. | builds |
| `jenkins_trigger_job_and_wait` | Trigger a Jenkins job and block until the resulting build finishes (or a timeout elapses). Internally resolves the queue item to a build number (falling back to build-number polling if no queue URL is available), then polls the build until it is no longer 'building'. Use this instead of jenkins_trigger_job + manual polling when the caller needs a synchronous result — e.g. before continuing a git workflow that depends on the triggered job's outcome (such as a branch-creation job that must finish before the branch can be checked out). | builds |

