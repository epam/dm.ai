# Bitrise MCP Tools

**Total Tools**: 23

## Available Tools

| Tool Name | Description | Category |
|-----------|-------------|----------|
| `bitrise_list_apps` | List all Bitrise apps accessible with the current token. Returns app slugs, titles, project types and repo URLs. | apps |
| `bitrise_get_app` | Get details of a specific Bitrise app by its slug. | apps |
| `bitrise_list_builds` | List builds for a Bitrise app. Optionally filter by workflow, branch or status. Status codes: not_started, in_progress, success, failed, aborted. | builds |
| `bitrise_trigger_build` | Trigger a new Bitrise workflow build for an app. Supports custom branch, environment variables, and workflow selection. | builds |
| `bitrise_get_build` | Get details and current status of a specific Bitrise build. Returns status, branch, workflow, timing, commit info. | builds |
| `bitrise_abort_build` | Abort a running Bitrise build with an optional reason message. | builds |
| `bitrise_get_build_log` | Get the full log of a Bitrise build. Returns log chunks and expiring download URL for the complete log. | builds |
| `bitrise_list_workflows` | List all available workflow IDs defined in the bitrise.yml for a Bitrise app. | builds |
| `bitrise_list_build_artifacts` | List all artifacts produced by a Bitrise build (APKs, IPAs, logs, test results, etc.). | artifacts |
| `bitrise_get_build_artifact` | Get details and expiring download URL for a specific Bitrise build artifact. | artifacts |
| `bitrise_get_yml` | Download the bitrise.yml configuration file for a Bitrise app. | config |
| `bitrise_update_yml` | Upload/replace the bitrise.yml configuration file for a Bitrise app. Pass either the full YAML content or a local file path ending in .yml/.yaml. The YAML is validated via the Bitrise API before uploading. | config |
| `bitrise_validate_yml` | Validate a bitrise.yml file content via the Bitrise API. Returns validation errors and warnings without modifying the app configuration. Accepts YAML content or a local file path. | config |
| `bitrise_get_yml_config` | Get the JSON-structured config representation of the bitrise.yml for a Bitrise app. | config |
| `bitrise_update_yml_config` | Update the bitrise.yml using a JSON-structured config representation for a Bitrise app. | config |
| `bitrise_list_secrets` | List all secret environment variables for a Bitrise app. Values are not returned by default (protected). | secrets |
| `bitrise_get_secret` | Get metadata for a specific secret by name. The value is not returned unless it's non-protected. | secrets |
| `bitrise_upsert_secret` | Create or update a secret environment variable for a Bitrise app. | secrets |
| `bitrise_delete_secret` | Delete a secret environment variable from a Bitrise app. | secrets |
| `bitrise_get_secret_value` | Retrieve the plaintext value of a non-protected secret. Returns 403 if the secret is marked as protected. | secrets |
| `bitrise_list_pipelines` | List pipeline runs for a Bitrise app. | pipelines |
| `bitrise_get_pipeline` | Get details of a specific Bitrise pipeline run by its ID. | pipelines |
| `bitrise_abort_pipeline` | Abort a running Bitrise pipeline. | pipelines |

