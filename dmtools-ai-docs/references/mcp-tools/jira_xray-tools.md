# Jira_xray MCP Tools

**Total Tools**: 10

## Available Tools

| Tool Name | Description | Category |
|-----------|-------------|----------|
| `jira_xray_create_precondition` | Create a Precondition issue in Xray with optional steps (converted to definition). Returns the created ticket key. | xray_management |
| `jira_xray_search_tickets` | Search for Jira tickets using JQL query and enrich Test/Precondition issues with X-ray test steps and preconditions. Returns list of tickets with X-ray data. | search |
| `jira_xray_get_test_details` | Get test details including steps and preconditions using X-ray GraphQL API. Returns JSONObject with test details. | test_retrieval |
| `jira_xray_get_test_steps` | Get test steps for a test issue using X-ray GraphQL API. Returns JSONArray of test steps. | test_retrieval |
| `jira_xray_get_preconditions` | Get preconditions for a test issue using X-ray GraphQL API. Returns JSONArray of precondition objects. | test_retrieval |
| `jira_xray_get_precondition_details` | Get Precondition details including definition using X-ray GraphQL API. Returns JSONObject with precondition details. | test_retrieval |
| `jira_xray_add_test_step` | Add a single test step to a test issue using X-ray GraphQL API. Returns JSONObject with created step details. | test_management |
| `jira_xray_add_test_steps` | Add multiple test steps to a test issue using X-ray GraphQL API. Returns JSONArray of created step objects. | test_management |
| `jira_xray_add_precondition_to_test` | Add a single precondition to a test issue using X-ray GraphQL API. Returns JSONObject with result. | test_management |
| `jira_xray_add_preconditions_to_test` | Add multiple preconditions to a test issue using X-ray GraphQL API. Returns JSONArray of results. | test_management |

