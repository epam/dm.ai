# TESTRAIL MCP Tools

**Total Tools**: 16

## Quick Reference

```bash
# List all testrail tools
dmtools list | jq '.tools[] | select(.name | startswith("testrail_"))'

# Example usage
dmtools testrail_get_projects [arguments]
```

## Usage in JavaScript Agents

```javascript
// Direct function calls for testrail tools
const result = testrail_get_projects(...);
const result = testrail_get_case(...);
const result = testrail_get_all_cases(...);
const result = testrail_get_suites(...);
```

## Available Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `testrail_create_case` | Create a new test case in TestRail | `description` (string, optional)<br>`priority_id` (string, optional)<br>`project_name` (string, **required**)<br>`title` (string, **required**)<br>`refs` (string, optional) |
| `testrail_create_case_detailed` | Create a new test case in TestRail with detailed fields (preconditions, steps, expected results, labels, type). Note: TestRail uses its own table format in text fields: \|\|\|:Col 1\|:Col 2\|:Col 3\n\|\|val1\|val2\|val3. Standard Markdown tables (\| Col \| Col \|) will be auto-converted to TestRail format. | `priority_id` (string, optional)<br>`refs` (string, optional)<br>`preconditions` (string, optional)<br>`type_id` (string, optional)<br>`label_ids` (string, optional)<br>`expected` (string, optional)<br>`project_name` (string, **required**)<br>`title` (string, **required**)<br>`steps` (string, optional) |
| `testrail_create_case_steps` | Create a TestRail test case using the 'Test Case (Steps)' template (template_id=2). Steps are provided as a JSON array: [{"content":"step text","expected":"expected result"}, ...]. Markdown tables in step content or expected are auto-converted to HTML tables. Use testrail_get_case_types for type_id, testrail_get_labels for label_ids. | `priority_id` (string, optional)<br>`refs` (string, optional)<br>`preconditions` (string, optional)<br>`type_id` (string, optional)<br>`label_ids` (string, optional)<br>`steps_json` (string, **required**)<br>`project_name` (string, **required**)<br>`title` (string, **required**) |
| `testrail_delete_case` | Delete a test case in TestRail by case ID | `case_id` (string, **required**) |
| `testrail_get_all_cases` | Get ALL test cases in a project (uses pagination to retrieve all cases). Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML. | `project_name` (string, **required**)<br>`format` (string, optional) |
| `testrail_get_case` | Get a TestRail test case by ID. Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML — raw HTML pasted from Google Docs/browsers can be 20-30x larger than necessary due to inline CSS styling on every tag. | `case_id` (string, **required**)<br>`format` (string, optional) |
| `testrail_get_case_types` | Get all available case types in TestRail (e.g., Automated, Functionality, Other) | None |
| `testrail_get_cases_by_refs` | Get test cases linked to a requirement/story via refs field | `project_name` (string, **required**)<br>`refs` (string, **required**) |
| `testrail_get_label` | Get a single label by ID | `label_id` (string, **required**) |
| `testrail_get_labels` | Get all labels for a project in TestRail | `project_name` (string, **required**) |
| `testrail_get_projects` | Get list of all projects in TestRail | None |
| `testrail_get_suites` | Get all test suites for a TestRail project | `project_name` (string, **required**) |
| `testrail_link_to_requirement` | Link a test case to a requirement by updating refs field | `case_id` (string, **required**)<br>`requirement_key` (string, **required**) |
| `testrail_search_cases` | Search TestRail test cases by project and optional filters. Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML. | `project_name` (string, **required**)<br>`section_id` (string, optional)<br>`suite_id` (string, optional)<br>`format` (string, optional) |
| `testrail_update_case` | Update a test case in TestRail | `case_id` (string, **required**)<br>`priority_id` (string, optional)<br>`title` (string, optional)<br>`refs` (string, optional) |
| `testrail_update_label` | Update a label title in TestRail. Maximum 20 characters allowed. | `project_name` (string, **required**)<br>`title` (string, **required**)<br>`label_id` (string, **required**) |

## Detailed Parameter Information

### `testrail_create_case`

Create a new test case in TestRail

**Parameters:**

- **`description`** (string) ⚪ Optional
  - Test case description/steps (optional)
  - Example: `1. Navigate to login page
2. Enter credentials
3. Click login`

- **`priority_id`** (string) ⚪ Optional
  - Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)
  - Example: `3`

- **`project_name`** (string) 🔴 Required
  - Project name
  - Example: `My Project`

- **`title`** (string) 🔴 Required
  - Test case title/summary
  - Example: `Verify login functionality`

- **`refs`** (string) ⚪ Optional
  - Reference to requirement (e.g., JIRA key)
  - Example: `PROJ-123`

**Example:**
```bash
dmtools testrail_create_case "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_create_case("description", "priority_id");
```

---

### `testrail_create_case_detailed`

Create a new test case in TestRail with detailed fields (preconditions, steps, expected results, labels, type). Note: TestRail uses its own table format in text fields: |||:Col 1|:Col 2|:Col 3\n||val1|val2|val3. Standard Markdown tables (| Col | Col |) will be auto-converted to TestRail format.

**Parameters:**

- **`priority_id`** (string) ⚪ Optional
  - Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)
  - Example: `3`

- **`refs`** (string) ⚪ Optional
  - Reference to requirement (e.g., JIRA key)
  - Example: `PROJ-123`

- **`preconditions`** (string) ⚪ Optional
  - Preconditions (optional). For tables use TestRail format: |||:Col1|:Col2\n||val1|val2
  - Example: `User is logged out`

- **`type_id`** (string) ⚪ Optional
  - Case type ID (optional). Use testrail_get_case_types to get available types.
  - Example: `1`

- **`label_ids`** (string) ⚪ Optional
  - Comma-separated label IDs (optional). Use testrail_get_labels to find IDs.
  - Example: `7,8`

- **`expected`** (string) ⚪ Optional
  - Expected results (optional)
  - Example: `User is logged in and redirected to dashboard`

- **`project_name`** (string) 🔴 Required
  - Project name
  - Example: `My Project`

- **`title`** (string) 🔴 Required
  - Test case title/summary
  - Example: `Verify login functionality`

- **`steps`** (string) ⚪ Optional
  - Test steps separated by double newline (optional)
  - Example: `Navigate to login page.\n\nEnter username %Username%.\n\nClick Login button.`

**Example:**
```bash
dmtools testrail_create_case_detailed "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_create_case_detailed("priority_id", "refs");
```

---

### `testrail_create_case_steps`

Create a TestRail test case using the 'Test Case (Steps)' template (template_id=2). Steps are provided as a JSON array: [{"content":"step text","expected":"expected result"}, ...]. Markdown tables in step content or expected are auto-converted to HTML tables. Use testrail_get_case_types for type_id, testrail_get_labels for label_ids.

**Parameters:**

- **`priority_id`** (string) ⚪ Optional
  - Priority ID: 1=Low, 2=Medium, 3=High, 4=Critical (optional, default=2)
  - Example: `3`

- **`refs`** (string) ⚪ Optional
  - Reference to requirement (e.g., JIRA key)
  - Example: `PROJ-123`

- **`preconditions`** (string) ⚪ Optional
  - Preconditions text (optional)
  - Example: `User is logged out`

- **`type_id`** (string) ⚪ Optional
  - Case type ID (optional). Use testrail_get_case_types to get available types.
  - Example: `1`

- **`label_ids`** (string) ⚪ Optional
  - Comma-separated label IDs (optional). Use testrail_get_labels to find IDs.
  - Example: `7,8`

- **`steps_json`** (string) 🔴 Required
  - JSON array of step objects: [{"content":"step","expected":"result"}, ...]. Markdown tables are auto-converted to HTML.
  - Example: `[{"content":"Open login page","expected":"Login form is displayed"},{"content":"Enter credentials","expected":"Fields populated"}]`

- **`project_name`** (string) 🔴 Required
  - Project name
  - Example: `My Project`

- **`title`** (string) 🔴 Required
  - Test case title/summary
  - Example: `Verify login functionality`

**Example:**
```bash
dmtools testrail_create_case_steps "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_create_case_steps("priority_id", "refs");
```

---

### `testrail_delete_case`

Delete a test case in TestRail by case ID

**Parameters:**

- **`case_id`** (string) 🔴 Required
  - The numeric test case ID to delete (without the C prefix)
  - Example: `123`

**Example:**
```bash
dmtools testrail_delete_case "value"
```

```javascript
// In JavaScript agent
const result = testrail_delete_case("case_id");
```

---

### `testrail_get_all_cases`

Get ALL test cases in a project (uses pagination to retrieve all cases). Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML.

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name to get all cases from
  - Example: `My Project`

- **`format`** (string) ⚪ Optional
  - Output format for HTML-bearing fields (preconditions, steps, expected results): 'html' (default, raw TestRail HTML) or 'md'/'markdown' (cleaned Markdown — much smaller and easier to read or feed to an LLM). If omitted, falls back to the `TESTRAIL_DEFAULT_FORMAT` env var (defaults to 'html' when unset).
  - Example: `markdown`

**Example:**
```bash
dmtools testrail_get_all_cases "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_all_cases("project_name", "format");
```

---

### `testrail_get_case`

Get a TestRail test case by ID. Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML — raw HTML pasted from Google Docs/browsers can be 20-30x larger than necessary due to inline CSS styling on every tag.

**Parameters:**

- **`case_id`** (string) 🔴 Required
  - The test case ID (numeric, without 'C' prefix)
  - Example: `123`

- **`format`** (string) ⚪ Optional
  - Output format for HTML-bearing fields (preconditions, steps, expected results): 'html' (default, raw TestRail HTML) or 'md'/'markdown' (cleaned Markdown — much smaller and easier to read or feed to an LLM). If omitted, falls back to the `TESTRAIL_DEFAULT_FORMAT` env var (defaults to 'html' when unset).
  - Example: `markdown`

**Example:**
```bash
dmtools testrail_get_case "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_case("case_id", "format");
```

---

### `testrail_get_case_types`

Get all available case types in TestRail (e.g., Automated, Functionality, Other)

**Parameters:** None

**Example:**
```bash
dmtools testrail_get_case_types
```

```javascript
// In JavaScript agent
const result = testrail_get_case_types();
```

---

### `testrail_get_cases_by_refs`

Get test cases linked to a requirement/story via refs field

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name to search in
  - Example: `My Project`

- **`refs`** (string) 🔴 Required
  - Reference ID (e.g., JIRA ticket key)
  - Example: `PROJ-123`

**Example:**
```bash
dmtools testrail_get_cases_by_refs "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_cases_by_refs("project_name", "refs");
```

---

### `testrail_get_label`

Get a single label by ID

**Parameters:**

- **`label_id`** (string) 🔴 Required
  - The label ID
  - Example: `7`

**Example:**
```bash
dmtools testrail_get_label "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_label("label_id");
```

---

### `testrail_get_labels`

Get all labels for a project in TestRail

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name
  - Example: `My Project`

**Example:**
```bash
dmtools testrail_get_labels "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_labels("project_name");
```

---

### `testrail_get_projects`

Get list of all projects in TestRail

**Parameters:** None

**Example:**
```bash
dmtools testrail_get_projects
```

```javascript
// In JavaScript agent
const result = testrail_get_projects();
```

---

### `testrail_get_suites`

Get all test suites for a TestRail project

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name to get suites from
  - Example: `My Project`

**Example:**
```bash
dmtools testrail_get_suites "value"
```

```javascript
// In JavaScript agent
const result = testrail_get_suites("project_name");
```

---

### `testrail_link_to_requirement`

Link a test case to a requirement by updating refs field

**Parameters:**

- **`case_id`** (string) 🔴 Required
  - The test case ID
  - Example: `123`

- **`requirement_key`** (string) 🔴 Required
  - Requirement key (e.g., JIRA ticket)
  - Example: `PROJ-123`

**Example:**
```bash
dmtools testrail_link_to_requirement "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_link_to_requirement("case_id", "requirement_key");
```

---

### `testrail_search_cases`

Search TestRail test cases by project and optional filters. Set format='markdown' to receive preconditions/steps/expected-result HTML fields converted to clean Markdown (tables preserved as GitHub-Flavoured Markdown) instead of raw TestRail HTML.

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name to search in
  - Example: `My Project`

- **`suite_id`** (string) ⚪ Optional
  - Suite ID to filter by (optional)
  - Example: `1`

- **`section_id`** (string) ⚪ Optional
  - Section ID to filter by (optional)
  - Example: `10`

- **`format`** (string) ⚪ Optional
  - Output format for HTML-bearing fields (preconditions, steps, expected results): 'html' (default, raw TestRail HTML) or 'md'/'markdown' (cleaned Markdown — much smaller and easier to read or feed to an LLM). If omitted, falls back to the `TESTRAIL_DEFAULT_FORMAT` env var (defaults to 'html' when unset).
  - Example: `markdown`

**Example:**
```bash
dmtools testrail_search_cases "value" "value" "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_search_cases("project_name", "suite_id", "section_id", "format");
```

---

### `testrail_update_case`

Update a test case in TestRail

**Parameters:**

- **`case_id`** (string) 🔴 Required
  - The test case ID to update
  - Example: `123`

- **`priority_id`** (string) ⚪ Optional
  - New priority ID (optional)
  - Example: `3`

- **`title`** (string) ⚪ Optional
  - New title (optional)
  - Example: `Updated title`

- **`refs`** (string) ⚪ Optional
  - New references (optional)
  - Example: `PROJ-123`

**Example:**
```bash
dmtools testrail_update_case "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_update_case("case_id", "priority_id");
```

---

### `testrail_update_label`

Update a label title in TestRail. Maximum 20 characters allowed.

**Parameters:**

- **`project_name`** (string) 🔴 Required
  - Project name
  - Example: `My Project`

- **`title`** (string) 🔴 Required
  - New label title (max 20 characters)
  - Example: `Release 2.0`

- **`label_id`** (string) 🔴 Required
  - The label ID to update
  - Example: `7`

**Example:**
```bash
dmtools testrail_update_label "value" "value"
```

```javascript
// In JavaScript agent
const result = testrail_update_label("project_name", "title");
```

---

