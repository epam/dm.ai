# TestRail MCP Tools

**Total Tools**: 15

## Quick Reference

```bash
# List all testrail tools
dmtools list | jq '.tools[] | select(.name | startswith("testrail_"))'

# Example usage
dmtools testrail_get_cases_by_refs PROJ-123 "My Project"
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for testrail tools
const cases = testrail_get_cases_by_refs("PROJ-123", "My Project");
const caseTypes = testrail_get_case_types();
const labels = testrail_get_labels("My Project");
```

## Environment Setup

```bash
# dmtools.env
TESTRAIL_BASE_PATH=https://your-company.testrail.io
TESTRAIL_USERNAME=your-email@company.com
TESTRAIL_API_KEY=your_api_key_from_my_settings
TESTRAIL_PROJECT=My Project        # Default project name (optional)
TESTRAIL_LOGGING_ENABLED=true      # Enable debug logging (optional)
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `testrail_get_projects` | Get list of all projects in TestRail instance | None |
| `testrail_get_case` | Get a test case by ID | `case_id` (string, **required**) — numeric ID without 'C' prefix |
| `testrail_get_all_cases` | Get ALL test cases in a project (auto-paginated) | `project_name` (string, **required**) |
| `testrail_search_cases` | Search test cases by project with optional filters | `project_name` (string, **required**)<br>`suite_id` (string, optional)<br>`section_id` (string, optional) |
| `testrail_get_cases_by_refs` | Get test cases linked to a requirement/story via refs field | `refs` (string, **required**) — e.g. Jira ticket key<br>`project_name` (string, **required**) |
| `testrail_create_case` | Create a basic test case (description → preconditions field) | `project_name` (string, **required**)<br>`title` (string, **required**)<br>`description` (string, optional)<br>`priority_id` (string, optional) — 1=Low, 2=Medium, 3=High, 4=Critical<br>`refs` (string, optional) |
| `testrail_create_case_detailed` | Create a test case using **Text template** (template_id=1). Single text fields for steps and expected. Markdown tables auto-converted to TestRail `\|\|\|` format. | `project_name` (string, **required**)<br>`title` (string, **required**)<br>`preconditions` (string, optional)<br>`steps` (string, optional)<br>`expected` (string, optional)<br>`priority_id` (string, optional)<br>`type_id` (string, optional)<br>`refs` (string, optional)<br>`label_ids` (string, optional) — comma-separated |
| `testrail_create_case_steps` | Create a test case using **Steps template** (template_id=2). Steps as JSON array. Markdown tables in content/expected auto-converted to HTML. | `project_name` (string, **required**)<br>`title` (string, **required**)<br>`steps_json` (string, **required**) — JSON array `[{"content":"step","expected":"result"},...]`<br>`preconditions` (string, optional)<br>`priority_id` (string, optional)<br>`type_id` (string, optional)<br>`refs` (string, optional)<br>`label_ids` (string, optional) — comma-separated |
| `testrail_update_case` | Update a test case. All parameters except case_id are optional | `case_id` (string, **required**)<br>`title` (string, optional)<br>`priority_id` (string, optional)<br>`refs` (string, optional) |
| `testrail_delete_case` | Delete a test case by ID | `case_id` (string, **required**) — numeric ID without 'C' prefix |
| `testrail_link_to_requirement` | Link a test case to a requirement by appending key to refs field | `case_id` (string, **required**)<br>`requirement_key` (string, **required**) — e.g. PROJ-123 |
| `testrail_get_labels` | Get all labels for a project | `project_name` (string, **required**) |
| `testrail_get_label` | Get a single label by ID | `label_id` (string, **required**) |
| `testrail_update_label` | Update a label title (max 20 characters) | `label_id` (string, **required**)<br>`project_name` (string, **required**)<br>`title` (string, **required**) |
| `testrail_get_case_types` | Get all available case types (e.g., Automated, Functional, Other) | None |

## Key Concepts

### Test Case ID Format

TestRail test case IDs have a `C` prefix (e.g., `C42`). MCP tools that accept `case_id` expect the **numeric part only** (e.g., `42`).

```bash
# Correct
dmtools testrail_get_case 42
dmtools testrail_delete_case 42

# Wrong
dmtools testrail_get_case C42
```

### Templates

| Template | `template_id` | Tool | Fields |
|----------|--------------|------|--------|
| Text | 1 | `testrail_create_case_detailed` | `preconditions`, `steps` (text), `expected` (text) |
| Steps | 2 | `testrail_create_case_steps` | `preconditions`, `steps_json` (JSON array) |

### Priority IDs

| ID | Name |
|----|------|
| 1  | Low |
| 2  | Medium |
| 3  | High |
| 4  | Critical |

Use `testrail_get_case_types` to find available `type_id` values.
Use `testrail_get_labels` to find available `label_ids` for your project.
