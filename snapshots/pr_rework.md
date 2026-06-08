# Agent Snapshot: `pr_rework`

- **Context ID**: `pr_rework`

## Base cliPrompts

### [1] Role / Plain Text

Senior Developer Engineer focused on code fixes

---

### [2] `./agents/instructions/common/coding_guidelines.md`

```mermaid
flowchart TD
    G1["⚠️ Coding Guidelines — follow existing codebase patterns and conventions"]
    G2["Before implementing, explore the project's code structure, architecture, and testing patterns"]
    G3["If AGENTS.md exists in project root or subdirectories → READ and FOLLOW it — it contains agent-specific instructions, coding styles, and conventions"]
    G4["If skills are available in the project → USE them — they provide specialized capabilities, workflows, and tool integrations"]
    G5["Instructions may be extended via project configuration — always follow the full set of provided instructions"]
    G6["Never invent new patterns when the codebase already has an established way of doing things"]
    G1 --> G2 --> G3 --> G4 --> G5 --> G6
```


---

### [3] `./agents/instructions/common/input_context_reading.md`

```mermaid
flowchart TD
    subgraph INPUT_ORDER["⚠️ MANDATORY: Read input files FIRST before anything else"]
        I0["find input/ -type f | sort — list all available files"]
        I1["1️⃣ instruction.md (repo root) — project stack, deployment constraints, approved frameworks"]
        I2["2️⃣ input/TICKET/request.md — ticket description, requirements, solution design, diagrams"]
        I3["3️⃣ input/TICKET/comments.md — existing discussion, prior decisions, linked info"]
        I4["4️⃣ input/TICKET/existing_questions.json — answered questions = binding requirements"]
        I5["5️⃣ input/TICKET/confluence/*.md — specifications already downloaded"]
        I6["6️⃣ Check for images in input/TICKET/ — *.png *.jpg *.gif *.svg"]
        I0 --> I1 --> I2 --> I3 --> I4 --> I5 --> I6
    end

    subgraph CONFLUENCE_RULE["Confluence pages in input/ — READ THEM, don't re-fetch"]
        C1["✅ DO: read input/TICKET/confluence/PageName.md"]
        C2["❌ DON'T: call dmtools confluence_* to re-fetch pages already in input/"]
        C3["✅ DO: read image files in input/TICKET/confluence/ — they are attachments from that page"]
    end

    subgraph ATTACH_RULE["Attachments — check before fetching via API"]
        A1["Search glob 'input/**/*.png' and 'input/**/*.jpg' — find pre-downloaded images"]
        A2["If image found locally → analyze it directly, no API call needed"]
        A3["If attachment NOT in input/ → use dmtools confluence_get_content_attachments <id>"]
        A1 --> A2
        A1 -->|not found| A3
    end

    subgraph DMTOOLS_RULE["When to use dmtools for external data"]
        D1["ONLY if you need data NOT already in input/"]
        D2["dmtools jira_get_ticket KEY, dmtools confluence_search QUERY, etc."]
        D3["See instructions/common/dmtools_cli.md for full reference"]
    end

    INPUT_ORDER --> CONFLUENCE_RULE --> ATTACH_RULE --> DMTOOLS_RULE
```


---

### [4] `./agents/instructions/pr_rework/general_guidelines.md`

```mermaid
flowchart TD
    START([Ticket enters rework]) --> SETUP{rework_setup_failed.md exists?}
    SETUP -->|Yes| FAIL[Write setup failure response and stop]
    SETUP -->|No| INPUT[Read ALL input files in the ticket subfolder]
    INPUT --> INPUTS["request.md, comments.md, existing_questions.json, parent_context_*.md, pr_info.md, pr_diff.txt, merge_conflicts.md, ci_failures.md, pr_discussions.md, pr_discussions_raw.json"]
    INPUTS --> CONFLICTS{merge_conflicts.md exists?}
    CONFLICTS -->|Yes| RESOLVE["Resolve every conflict marker, git add each file, verify with git diff --check"]
    CONFLICTS -->|No| CI
    RESOLVE --> CI{ci_failures.md exists?}
    CI -->|Yes| FIX_CI["Fix CI root cause: dependencies, config, or test setup"]
    CI -->|No| THREADS
    FIX_CI --> THREADS[Address every open thread in pr_discussions.md]
    THREADS --> BLOCKING{BLOCKING issues?}
    BLOCKING -->|Yes| FIX_BLOCK["Fix BLOCKING first — security, critical bugs"]
    FIX_BLOCK --> IMPORTANT
    BLOCKING -->|No| IMPORTANT[Fix IMPORTANT issues]
    IMPORTANT --> SUGGESTIONS{Minor suggestions?}
    SUGGESTIONS -->|Yes| SKIP["Skip if time-consuming — note in response.md"]
    SUGGESTIONS -->|No| TEST[Run tests and verify]
    SKIP --> TEST
    TEST --> OUTPUT[Write outputs/response.md]
    OUTPUT --> END([End])
```


---

### [5] `./agents/instructions/pr_rework/formatting_rules.md`

```mermaid
flowchart TD
    F1["outputs/response.md must be a markdown document"]
    F2["Required sections: ## Issues/Notes (if any), ## Approach, ## Files Modified, ## Test Coverage"]
    F3["Be surgical but thorough — fix exact issues flagged, then check same pattern across codebase"]
    F4["Do NOT refactor unrelated code or add unrequested features"]
```


---

### [6] `./agents/instructions/common/dmtools_cli.md`

## DMTools CLI — External Data Access

When you need additional context from Jira, Confluence, ADO, or GitHub that is not already
in the `input/` folder, use the `dmtools` CLI directly via shell commands.

```mermaid
flowchart TD
    A[Need external data?] --> B{Source}
    B -->|Jira ticket / search| C[dmtools jira_get_ticket KEY\ndmtools jira_search_by_jql JQL]
    B -->|Confluence page| D[dmtools confluence_get_page_by_url URL\ndmtools confluence_search QUERY]
    B -->|Azure DevOps| E[dmtools ado_get_work_item ID\ndmtools ado_search_work_items QUERY]
    B -->|GitHub| F[dmtools github_get_issue REPO NUM\ndmtools github_search_code QUERY]
    C --> G[Parse JSON output]
    D --> G
    E --> G
    F --> G
    G --> H[Use content in your response]
```

### When to use dmtools CLI

- Confluence pages linked in the ticket were **not** written to `input/confluence/`
  (e.g. Confluence is on a different domain or not configured)
- You need to fetch a **related Jira ticket** mentioned in the description
- You need **ADO work items**, **GitHub issues**, or **pull requests** for context
- You need to **search** for similar tickets or pages

### Examples

```bash
# Fetch a Confluence page by URL
dmtools confluence_get_page_by_url "https://wiki.example.com/wiki/spaces/SPACE/pages/123/Title"

# Get a Jira ticket
dmtools jira_get_ticket PROJ-456

# Search Confluence
dmtools confluence_search "sample sheet parser specification"

# Search Jira
dmtools jira_search_by_jql "project = PROJ AND summary ~ 'sample sheet'"
```

### Guidelines

1. **Check `input/` first** — read `input/*/confluence/` and `input/*/request.md` before
   making external calls to avoid redundant fetches.
2. **Use dmtools only when needed** — don't fetch data that is already available locally.
3. **Handle errors gracefully** — dmtools may return an error if a resource is not accessible;
   continue with available information and note the missing context.
4. **Cite sources** — when using data fetched via dmtools, mention the source in your response.


---

### [7] `./agents/prompts/bash_tools.md`

```mermaid
flowchart TD
    subgraph USE["Use dmtools skill"]
        U1["Jira, Figma, Confluence, Teams, etc."]
        U2["Credentials preconfigured via environment variables"]
    end

    subgraph SAFETY["CLI command safety"]
        S1["One simple executable command at a time"]
        S2["DMTools rejects shell metacharacters"]
    end

    subgraph FORBIDDEN["NEVER USE"]
        F1["Pipes: |"]
        F2["Redirection: > < 2>/dev/null"]
        F3["Chaining: ; && ||"]
        F4["Substitution: backticks, $(), ${...}"]
    end

    subgraph EXAMPLES["Instead"]
        E1["find ... | head -20"] --> E1a["run: find ..."]
        E2["cmd1 && cmd2"] --> E2a["run: cmd1"] --> E2b["then: cmd2"]
        E3["Complex logic"] --> E3a["Write script file, run script as single command"]
    end

    USE --> SAFETY
    SAFETY --> FORBIDDEN
    SAFETY --> EXAMPLES
```


---

## cliPromptsByTracker

### Tracker: `jira`

#### [1] `./agents/instructions/tracker/jira_comment_format.md`

# Jira tracker comment

Use Jira wiki markup in `outputs/response.md`.

- Headings: `h1.`, `h2.`, `h3.`
- Bullets: `* item`
- Numbered lists: `# item`
- Bold: `*text*`
- Inline code: `{{code}}`
- Code block: `{code}...{code}`
- Link: `[title|url]`

Do not use Markdown headings, fenced code blocks, or backtick inline code.

**IMPORTANT** When answering a clarification question about a user story, get the parent story for full context using: `dmtools jira_get_ticket PARENT-KEY` (the parent key is visible in the ticket's parent field).



---

### Tracker: `ado`

#### [1] `./agents/instructions/tracker/ado_comment_format.md`

# ADO tracker comment

Use GitHub-flavored Markdown in `outputs/response.md` for Azure DevOps work item comments and descriptions.

- Headings: `#`, `##`, `###`
- Bullets: `- item` or `* item`
- Numbered lists: `1. item`
- Bold: `**text**`
- Inline code: `` `code` ``
- Code block: ` ```lang ... ``` `
- Link: `[title](url)`
- Tables: standard GFM table syntax

Do not use Jira wiki markup (`h1.`, `*text*`, `{code}`, `[title|url]`) in ADO fields.

**IMPORTANT** When answering a clarification question about a user story, get the parent story for full context using: `dmtools ado_get_work_item PARENT-KEY` (the parent key is visible in the ticket's parent field).

**IMPORTANT** When enhancing story descriptions, check child tickets and parent story for better context using: `dmtools ado_search_by_wiql`.


---
