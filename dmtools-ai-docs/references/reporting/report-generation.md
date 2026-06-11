# Report Generator Skill Guide

This guide explains how to generate DMtools reports, which data sources and metrics you can use, and how to tune formulas, scores, and output. The example config for this guide lives at `dmtools-ai-docs/references/examples/report-generator-job.json`.

**Quick Start**

1. Create or edit a report config file like `dmtools-ai-docs/references/examples/report-generator-job.json`.
2. Run the report:

```bash
dmtools run dmtools-ai-docs/references/examples/report-generator-job.json
```

3. Find the output in the configured `outputPath` (HTML and JSON).

**Report Config Structure**

The report is a job config with `"name": "ReportGenerator"` and a `params` object. The exact class name `"ReportGeneratorJob"` is also supported.

```json
{
  "name": "ReportGenerator",
  "params": {
    "reportName": "My Code Contribution Report",
    "startDate": "2024-03-01",
    "endDate": "2026-02-08",
    "employees": [],
    "aliases": {},
    "dataSources": [],
    "timeGrouping": [],
    "aggregation": {},
    "output": {},
    "computedMetrics": [],
    "customCharts": []
  }
}
```

**Core Parameters**

- `reportName`: Display name in HTML.
- `startDate`: Start date in `YYYY-MM-DD`.
- `endDate`: End date in `YYYY-MM-DD`. If omitted, today is used.
- `employees`: Optional list of people to include.
- `aliases`: Name normalization and bot mapping.
- `dataSources`: One or more sources (tracker, PRs, commits, CSV).
- `timeGrouping`: Multiple groupings can be generated from one run.
- `aggregation`: Formula-based score config.
- `output`: Where and how to save output.
- `computedMetrics`: Derived metrics using formulas.
- `customCharts`: Extra charts in HTML.

**Employees And Aliases**

Use `aliases` to merge multiple names into a single person and map bots.

```json
"aliases": {
  "Uladzimir Klyshevich": ["IstiN", "uladzimir-klyshevich"],
  "AI Teammate": ["ai-teammate","github-actions[bot]","copilot","cursor[bot]","copilot-pull-request-reviewer[bot]"]
}
```

**Data Sources**

Supported data sources in the example config:

- `tracker`: Jira/ADO tracker data via JQL.
- `pullRequests`: Pull requests from GitHub, GitLab, or Bitbucket (via `sourceType`).
- `commits`: Git commits from GitHub, GitLab, or Bitbucket (via `sourceType`).
- `csv`: Custom CSV files.
- `jsonl`: Line-delimited JSON files (generic structured data).
- `figma`: Figma comments from one or more Figma files.

**Tracker Source**

```json
{
  "name": "tracker",
  "params": {
    "jql": "project = DMC AND issueType in (Story, Task) and status in (Done)"
  },
  "metrics": [ ... ]
}
```

Tracker source parameter:

- `jql`: Jira Query Language filter.
- `fields` (optional): Additional tracker fields to include in the query (array or comma-separated string). These are merged with default fields. Use this to request fields like `description` so token metrics don’t trigger extra fetches.

**Pull Requests Source**

```json
{
  "name": "pullRequests",
  "params": {
    "sourceType": "github",
    "workspace": "IstiN",
    "repository": "dmtools",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

Pull request source parameters:

- `sourceType`: `github`, `gitlab`, or `bitbucket`.
- `workspace`: GitHub org or user.
- `repository`: Repository name.
- `branch`: Branch name.
- `titleRegex` *(optional)*: Java regex matched against the PR title (substring `find()`). Only PRs whose title contains a match are collected. Useful to limit data in large repositories. Example: `^feat\\(`, `TICKET-\\d+`.

Example with title filter (only feature PRs):

```json
{
  "name": "pullRequests",
  "params": {
    "sourceType": "github",
    "workspace": "IstiN",
    "repository": "dmtools",
    "titleRegex": "^feat\\("
  },
  "metrics": [ ... ]
}
```

Example for GitLab:

```json
{
  "name": "pullRequests",
  "params": {
    "sourceType": "gitlab",
    "workspace": "my-group",
    "repository": "my-repo",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

Example for Bitbucket:

```json
{
  "name": "pullRequests",
  "params": {
    "sourceType": "bitbucket",
    "workspace": "my-workspace",
    "repository": "my-repo",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

**Commits Source**

```json
{
  "name": "commits",
  "params": {
    "sourceType": "github",
    "workspace": "IstiN",
    "repository": "dmtools",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

Commits source parameters:

- `sourceType`, `workspace`, `repository`: same as PRs.
- `branch`: Exact branch name to fetch commits from.
- `branchNameRegex` *(optional)*: Java regex matched against branch names. When set, all branches whose name contains a match are included and their commits are aggregated and de-duplicated by hash. Replaces `branch` for dynamic multi-branch collection. Example: `^feature/`, `release/\\d+`.

> **Tip**: Use `branchNameRegex` instead of `branch` for large repositories where you want commits from many branches (e.g., all `feature/*` branches) without specifying each one individually.

Example with branch regex (all feature branches):

```json
{
  "name": "commits",
  "params": {
    "sourceType": "github",
    "workspace": "IstiN",
    "repository": "dmtools",
    "branchNameRegex": "^feature/",
    "startDate": "2024-01-01"
  },
  "metrics": [ ... ]
}
```

Example for GitLab:

```json
{
  "name": "commits",
  "params": {
    "sourceType": "gitlab",
    "workspace": "my-group",
    "repository": "my-repo",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

Example for Bitbucket:

```json
{
  "name": "commits",
  "params": {
    "sourceType": "bitbucket",
    "workspace": "my-workspace",
    "repository": "my-repo",
    "branch": "main"
  },
  "metrics": [ ... ]
}
```

**CSV Source**

```json
{
  "name": "csv",
  "params": {
    "filePath": "agents/reports/cursor_usage.csv",
    "whenColumn": "Date",
    "defaultWho": "Uladzimir Klyshevich"
  },
  "metrics": [ ... ]
}
```

CSV source parameters:

- `filePath`: Absolute or relative path.
- `whenColumn`: Column name for date.
- `defaultWho`: Who to attribute if CSV has no person column.

**JSONL Source**

Reads JSON Lines (`.jsonl`) or JSON (`.json`) files from a folder or a single file. Useful for custom event logs, Copilot usage exports, CI metrics, or any line-delimited JSON data.

```json
{
  "name": "jsonl",
  "params": {
    "folderPath": "path/to/jsonl/data",
    "whoField": "user",
    "whenField": "date"
  },
  "metrics": [ ... ]
}
```

JSONL source parameters:

- `folderPath`: Directory scanned recursively for `.json` and `.jsonl` files.
- `filePath`: Alternative to `folderPath` — read a single file.
- `whoField`: Field name for the person (default: `user_login`). Supports dot-notation for nested fields.
- `whenField`: Field name for the date (default: `day`). Supports dot-notation and custom `dateFormat`.
- `dateFormat`: Optional Java `SimpleDateFormat` pattern (default: `yyyy-MM-dd`).

**Figma Source**

Figma data source does not require params at the source level. Instead, specify `files` on the metric.

```json
{
  "name": "figma",
  "metrics": [
    {
      "name": "FigmaCommentMetric",
      "params": {
        "label": "Figma Comments",
        "isPersonalized": true,
        "files": ["FIGMA_FILE_KEY_1", "FIGMA_FILE_KEY_2"]
      }
    }
  ]
}
```

Figma requirements:

- Figma integration must be configured.
- `files` is required on each Figma metric.

**Metrics**

Metrics are defined per data source. The metric `name` must match a built-in class, while `label` is the display name.

Common metric parameters:

- `label`: Display label in charts.
- `isWeight`: Use weighted sums instead of counts.
- `isPersonalized`: Per-person breakdown.
- `divider`: Divide weight values (e.g. `1000` for K).
- `filterFields`: Only for ticket field rules.
- `includeInitial`: Include initial baseline value.
- `creatorFilterMode`: `all`, `exclude`, `only`.
- `useDivider`: Enables field-specific weighting in change rules.
- `isSimilarity`: Similarity mode for field changes.
- `commentsRegex`: Regex filter for comments.
- `statuses`: Statuses for moved-to-status rule.
- `mode`: Token change mode (`mixed`, `delta`, `added`, `removed`, `rewritten`, `contribution`).

**Tracker Rules**

Example snippet:

- `TicketMovedToStatusRule`
- `TicketCreatorsRule`
- `TicketFieldsChangesRule`
- `TicketFieldsTokensChangedRule`
- `TicketFieldsTokensRetainedRule`
- `CommentsWrittenRule`

Examples:

```json
{
  "name": "TicketMovedToStatusRule",
  "params": {
    "statuses": ["Done"],
    "label": "Accepted Tickets",
    "isWeight": true
  }
}
```

```json
{
  "name": "TicketFieldsChangesRule",
  "params": {
    "label": "Description Changes",
    "isWeight": true,
    "filterFields": ["description"],
    "includeInitial": true,
    "useDivider": false,
    "creatorFilterMode": "all"
  }
}
```

```json
{
  "name": "TicketFieldsTokensChangedRule",
  "params": {
    "label": "Description Tokens Contribution (K)",
    "divider": 1000,
    "isWeight": true,
    "filterFields": ["description"],
    "includeInitial": true,
    "mode": "contribution",
    "creatorFilterMode": "all"
  }
}
```

```json
{
  "name": "TicketFieldsTokensRetainedRule",
  "params": {
    "label": "Description Tokens Retained (K)",
    "divider": 1000,
    "isWeight": true,
    "filterFields": ["description"],
    "includeInitial": true,
    "creatorFilterMode": "all"
  }
}
```

```json
{
  "name": "CommentsWrittenRule",
  "params": {
    "label": "Test Case Agent Comments",
    "isWeight": true,
    "commentsRegex": "Test Case Agent - similar test cases are linked and new test cases are generated"
  }
}
```

**Pull Request Metrics**

Example snippet:

- `PullRequestsMetricSource`
- `PullRequestsMergedByMetricSource`
- `PullRequestsDeclinedMetricSource`
- `PullRequestsCommentsMetricSource`
- `PullRequestsApprovalsMetricSource`

All PR metric sources accept an optional `titleRegex` param (at the **metric** or **data source** level) to filter PRs by title:

```json
{
  "name": "PullRequestsMetricSource",
  "params": {
    "label": "Feature PRs Created",
    "isWeight": false,
    "isPersonalized": true,
    "titleRegex": "^feat\\("
  }
}
```

**Commit Metrics**

Example snippet:

- `CommitsMetricSource`
- `LinesOfCodeMetricSource`

Both commit metric sources accept an optional `branchNameRegex` param to collect from multiple branches:

```json
{
  "name": "LinesOfCodeMetricSource",
  "params": {
    "label": "Lines Of Code (K)",
    "isWeight": true,
    "isPersonalized": true,
    "divider": 1000,
    "branchNameRegex": "^feature/"
  }
}
```

**CSV Metrics**

CSV metrics use `CsvMetricSource` at the metric level.

```json
{
  "name": "CsvMetricSource",
  "params": {
    "weightColumn": "Output Tokens",
    "label": "Output Tokens (10M)",
    "isWeight": true,
    "isPersonalized": true,
    "divider": 10000000
  }
}
```

**CSV Row Filtering**

Use `filterColumn` + `filterValue` (or `filterValues` for multiple) to include only rows matching a specific column value. Useful when a single CSV contains multiple model types or categories.

Single value filter:
```json
{
  "name": "CsvMetricSource",
  "params": {
    "weightColumn": "Tokens",
    "label": "GPT-4 Tokens",
    "isWeight": true,
    "filterColumn": "Model",
    "filterValue": "gpt-4"
  }
}
```

Multiple values filter:
```json
{
  "name": "CsvMetricSource",
  "params": {
    "weightColumn": "Tokens",
    "label": "GPT Tokens",
    "isWeight": true,
    "filterColumn": "Model",
    "filterValues": ["gpt-4", "gpt-4o", "gpt-3.5-turbo"]
  }
}
```

This allows splitting one CSV into multiple metrics by type:
```json
"metrics": [
  { "name": "CsvMetricSource", "params": { "label": "Claude Tokens", "weightColumn": "Tokens", "filterColumn": "Model", "filterValue": "claude-3-5-sonnet" } },
  { "name": "CsvMetricSource", "params": { "label": "GPT-4 Tokens",   "weightColumn": "Tokens", "filterColumn": "Model", "filterValue": "gpt-4" } }
]
```

CSV metric parameters:

- `weightColumn` (required): Column with numeric values.
- `whoColumn`: Column for person name.
- `whenColumn`: Column for date (default: `Date`).
- `defaultWho`: Fallback person name.
- `dateFormat`: Custom date format (e.g. `yyyy-MM-dd`).
- `weightMultiplier`: Multiply each value by this factor.
- `divider`: Divide the aggregated result by this factor.
- `filterColumn`: Column name to filter rows by.
- `filterValue`: Single value to match (case-insensitive).
- `filterValues`: Array of values to match (case-insensitive, OR logic).

CSV parsing notes:

- Dates are read from `whenColumn`.
- Numeric values can be quoted.
- Invalid values like `NaN`, `N/A`, empty strings are skipped.
- Filter matching is case-insensitive.

**JSONL Metrics**

JSONL metrics use `JsonlMetricSource` at the metric level.

```json
{
  "name": "JsonlMetricSource",
  "params": {
    "label": "Total Events",
    "weightField": "count",
    "isWeight": true,
    "isPersonalized": true
  }
}
```

JSONL metric parameters:

- `weightField` (required): Field path with numeric value. Supports dot-notation for nested objects (e.g. `totals.by_model.acceptances`).
- `whoField`: Overrides source-level `whoField` for this metric.
- `whenField`: Overrides source-level `whenField` for this metric.
- `weightMultiplier`: Multiply each value by this factor before aggregation.
- `filterField` + `filterValue`: Include only top-level rows where the field equals the value (case-insensitive).
- `arrayField`: Path to a JSON array inside the row.
- `arrayFilterField` + `arrayFilterValue`: Within `arrayField`, include only objects whose field equals the value. Use `"*"` for `arrayFilterValue` to include every object.
- `groupByField`: When set, the metric value is attributed to the value of this field instead of the person. Useful for breaking down by model, feature, IDE, language, etc.
- `dateFormat`: Custom date format for this metric.

Example breakdowns:

```json
{
  "name": "JsonlMetricSource",
  "params": {
    "label": "Feature Acceptances",
    "arrayField": "totals_by_feature",
    "arrayFilterField": "feature",
    "arrayFilterValue": "code_completion",
    "weightField": "code_acceptance_activity_count",
    "isWeight": true,
    "isPersonalized": true
  }
}
```

```json
{
  "name": "JsonlMetricSource",
  "params": {
    "label": "Acceptances by Model",
    "arrayField": "totals_by_model",
    "arrayFilterField": "model",
    "arrayFilterValue": "*",
    "weightField": "code_acceptance_activity_count",
    "groupByField": "model",
    "isWeight": true,
    "isPersonalized": true
  }
}
```

**Figma Metrics**

Figma metrics use one of these names:

- `FigmaCommentMetric`
- `FigmaCommentsMetricSource`

Parameters:

- `files` (required): Array (or comma-separated string) of Figma file keys.

**Computed Metrics**

Computed metrics are formulas referencing other metric labels.

```json
"computedMetrics": [
  {
    "label": "Input Tokens (10M)",
    "formula": "${Total Tokens R/W (10M)} - ${Output Tokens (10M)}",
    "isWeight": true,
    "isPersonalized": true
  }
]
```

Rules:

- Use `${Metric Label}` placeholders.
- Supports `+ - * /` and parentheses.
- Missing metrics resolve to `0`.

**Aggregation Score**

The aggregation formula produces a report-wide “score” and is visualized as a chart before the radar.

You can set it inline or in a file:

```json
"aggregation": {
  "label": "All Metrics Score",
  "formulaFile": "agents/reports/aggregation_formula.js",
  "formula": "...fallback..."
}
```

`formulaFile` is used if present. The file contains a GraalJS expression:

```js
(
  (${Completed Tickets} + ${Accepted Tickets} + ${Solution Completed})
  + (${Lines Of Code (K)} * 0.3)
  + (${Output Tokens (10M)} * 0.6)
)
- (
  (${Cost ($)} * 0.1)
  + (${Total Tokens R/W (10M)} * 0.2)
)
```

**Custom Charts**

Custom charts are visual-only groups in the HTML.

```json
"customCharts": [
  {
    "title": "Questions Quality",
    "type": "ratio",
    "metrics": ["Questions Created", "Irrelevant Questions"]
  },
  {
    "title": "Dev vs QA Output",
    "type": "comparison",
    "metrics": ["Dev Completed", "Test Cases", "Bugs Created"]
  }
]
```

**Output Settings**

```json
"output": {
  "mode": "combined",
  "saveRawMetadata": true,
  "outputPath": "agents/reports/output"
}
```

Output parameters:

- `mode`: `combined` or separate JSON files per grouping.
- `saveRawMetadata`: Include keyTimes in raw dataset.
- `outputPath`: Folder to store JSON and HTML.
- `visualizer`: Set to `none` to skip HTML generation.

**Time Grouping**

Multiple time groupings can be generated in a single run.

```json
"timeGrouping": [
  {"type": "daily"},
  {"type": "weekly"},
  {"type": "bi-weekly"},
  {"type": "monthly"},
  {"type": "yearly"}
]
```

**Validation Tips**

- Ensure metric labels used in formulas exactly match `label` strings.
- Keep CSV headers consistent with `weightColumn` names.
- Use `aliases` to group people and bots correctly.
- Use `creatorFilterMode: "all"` if you want to count both creator and editors.

**Where To Start**

Use the example config as a template:

- `dmtools-ai-docs/references/examples/report-generator-job.json`
- `dmtools-ai-docs/references/examples/report-generator-jsonl-job.json`
- `dmtools-ai-docs/references/examples/copilot-usage-report.json`
- [ReportGenerator quick reference](../jobs/README.md#reportgenerator)
- [ReportVisualizer quick reference](../jobs/README.md#reportvisualizer)
