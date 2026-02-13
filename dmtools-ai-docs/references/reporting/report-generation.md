# Report Generator Skill Guide

This guide explains how to generate DMtools reports, which data sources and metrics you can use, and how to tune formulas, scores, and output. All examples below are taken from `agents/reports/dmc_report.json`.

**Quick Start**

1. Create or edit a report config file like `agents/reports/dmc_report.json`.
2. Run the report:

```bash
dmtools run agents/reports/dmc_report.json
```

3. Find the output in the configured `outputPath` (HTML and JSON).

**Report Config Structure**

The report is a job config with `"name": "ReportGenerator"` and a `params` object.

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

Supported data sources in `dmc_report.json`:

- `tracker`: Jira/ADO tracker data via JQL.
- `pullRequests`: Pull requests from GitHub, GitLab, or Bitbucket (via `sourceType`).
- `commits`: Git commits from GitHub, GitLab, or Bitbucket (via `sourceType`).
- `csv`: Custom CSV files.
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

- `sourceType`, `workspace`, `repository`, `branch` (same as PRs).

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

From `dmc_report.json`:

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

From `dmc_report.json`:

- `PullRequestsMetricSource`
- `PullRequestsMergedByMetricSource`
- `PullRequestsDeclinedMetricSource`
- `PullRequestsCommentsMetricSource`
- `PullRequestsApprovalsMetricSource`

Example:

```json
{
  "name": "PullRequestsMetricSource",
  "params": {
    "label": "PRs Created",
    "isWeight": false,
    "isPersonalized": true
  }
}
```

**Commit Metrics**

From `dmc_report.json`:

- `CommitsMetricSource`
- `LinesOfCodeMetricSource`

Example:

```json
{
  "name": "LinesOfCodeMetricSource",
  "params": {
    "label": "Lines Of Code (K)",
    "isWeight": true,
    "isPersonalized": true,
    "divider": 1000
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

CSV parsing notes:

- Dates are read from `whenColumn`.
- Numeric values can be quoted.
- Invalid values like `NaN`, `N/A`, empty strings are skipped.

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

Use the existing config as a template:

- `agents/reports/dmc_report.json`
- `agents/reports/aggregation_formula.js`
