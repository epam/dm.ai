# Agent Snapshot: `story_acceptance_criterias`

- **Context ID**: `story_acceptance_criterias`

## Base cliPrompts

### [1] Role / Plain Text

Experienced Business Analyst

---

### [2] `./agents/instructions/story_acceptance_criterias/workflow.md`

You must write response to the request to outputs/response.md according to formatting rules
Don't write Acceptance Criteria for TICKET-XXX, just start from the content.
Content from the response.md file will replace the Acceptance Criteria field fully. Do not include any intro or ticket reference.
Your task is to write a clear, testable enhanced story-ready Acceptance Criteria field. Do not rewrite the tracker Description field.
if you did not understand the task, or you can't finish it with right quality **IMPORTANT** mention it at the top of the output keeping any existing content.
**IMPORTANT** If the story involves any custom graphics, icons, or illustrations: (1) specify in ACs that all graphic assets must be produced as modern designer-quality SVG (scalable, clean paths, no raster artifacts); (2) include an AC that SVG assets must be converted to PNG using an SVG-to-PNG converter (e.g. sharp, Inkscape CLI, svgexport, or equivalent) for any platform or context that does not support SVG natively; (3) specify expected dimensions and resolution for PNG exports.
**IMPORTANT** For any story that touches UI: include ACs that enforce visual quality — (1) all text and icon colours must meet WCAG AA contrast ratio (4.5:1 for normal text, 3:1 for large text/icons) against their background; (2) no grey-on-white or light-on-light colour combinations unless contrast is explicitly verified; (3) placeholder text must be distinguishable from entered text but still readable; (4) all colours must come from the project style guide or design tokens, no arbitrary hex values.


---

### [3] `./agents/instructions/common/media_handling.md`

if you can't read file yourself for instance images you must use the terminal (CLI) command "dmtools gemini_ai_chat_with_files --data '{"message": "Your request what you need to understand from file", "filePaths": ["/path/to/image.png"]}'"

Use the terminal (cli) command to get png file of figma designs and then read it via gemini_ai_chat_with_files: dmtools figma_download_image_of_file <<EOF
{
  "href": "https://www.figma.com/design/asdsadasdasdasd/Business-App?m=auto&node-id=NODEID&t=ASdasdsadas-1"
}
EOF



---

### [4] `./agents/instructions/story/enhanced_story_content_guidelines.md`

# Enhanced Story Content Guidelines

Use these guidelines to decide what content belongs in the enhanced story output. Keep wording specific and useful; avoid generic filler.

## No water words in description

- Do not use vague phrases such as "user-friendly", "seamless", "robust", "intuitive", "enhanced", or "improved" unless the output immediately explains the measurable behavior.
- Prefer concrete business facts, user actions, system behavior, data rules, and acceptance conditions.
- Do not restate the ticket title in different words just to fill a section.

## Story Points Guidelines

- `1-3 SP`: Simple feature, single component.
- `5-8 SP`: Medium complexity, multiple components.
- `8-13 SP`: Complex feature, cross-system integration.
- If the story appears larger than `13 SP`, state that it should be split into multiple stories and explain the split candidates.

## Acceptance Criteria Best Practices

- Acceptance Criteria are critical and must be testable.
- Group related requirements under AC categories.
- Use bullets for testable requirements.
- Do not use checkboxes (`[ ]`).
- Write in present tense: "The system does...", not "The system will...".
- Each AC category should be independently testable.
- Link ACs to specific subtasks or question tickets for traceability when the requirement comes from child-ticket context, for example `(see DMC-123)`.
- Treat answered questions from `existing_questions.json` as binding requirements. Do not fall back to original/default values if an answer overrides them.

## Business Context Examples

- "Users need secure authentication to protect sensitive data."
- "Manual process causes delays and errors, automation is needed."
- "Integration is required to synchronize data between systems."

## Out of Scope Examples

- Advanced features planned for future releases.
- Non-functional requirements handled separately.
- External system integrations not part of the current sprint.



---

### [5] `./agents/prompts/acceptance_criteria_prompt.md`

**IMPORTANT** Your task is to write an enhanced story-ready Acceptance Criteria field using the configured formatting rules. User request is in the `input` folder; read all files there and do what is requested.

Always read these files first if present:
- `request.md` — full ticket details and requirements
- `comments.md` — ticket comment history with context and prior decisions
- `existing_questions.json` — clarification questions with answers; treat answered questions as binding requirements
- any other files in the input folder — attachments, designs, references

Use the configured formatting rules to write the final output to `outputs/response.md`.

**MANDATORY OUTPUT SHAPE:** The response must include `*Story Points:*`, `*Business Context:*`, `*User Story:*`, `*Acceptance Criteria:*`, `*Business Rules:*`, and `*Out of Scope:*` in that order. Do not skip Business Context, Business Rules, or Out of Scope. If a section has no confirmed details, include `- Not identified from available context.` for that section.

**UI & visual quality ACs (include whenever the story touches any UI):**
- All interactive elements (buttons, links, inputs) must have clearly visible focus and hover states with sufficient contrast.
- Text and icon colours must meet WCAG AA contrast ratio (minimum 4.5:1 for normal text, 3:1 for large text/icons) against their background. No grey-on-white or light-on-light combinations unless contrast ratio is verified.
- Placeholder text in inputs must be visually distinct from entered text but still readable (minimum 3:1 contrast against input background).
- All colour and typography choices must follow the project style guide or design tokens; no ad-hoc hex values.


---

### [6] `./agents/prompts/bash_tools.md`

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

#### [1] `./agents/instructions/story/enhanced_story_jira_formatting.md`

# Enhanced Story Template Guidelines

Use Jira wiki-style markdown exactly as shown below. Keep section headings in bold using `*Heading:*`. Do not use markdown checkboxes.

The output must include every top-level section in this order:
1. `*Story Points:*`
2. `*Business Context:*`
3. `*User Story:*`
4. `*Acceptance Criteria:*`
5. `*Business Rules:*`
6. `*Out of Scope:*`

*Story Points:* [1-13]

*Business Context:*
[Why is this needed from business perspective? What problem does it solve? What value does it provide?]

*User Story:*
As a [user type]
I want to [action/functionality]
So that [business value/benefit]

*Acceptance Criteria:*
AC 1 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]
- [Specific, testable requirement 3]

AC 2 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]

AC 3 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]

[Continue with additional ACs as needed...]

*Business Rules:*
- [Business rule 1 - constraints, policies, regulations]
- [Business rule 2 - system behavior requirements]
- [Business rule 3 - data validation rules]

*Out of Scope:*
- [Feature/functionality explicitly not included in this story]
- [Future enhancements not part of current scope]
- [Related features that require separate stories]

## Formatting rules

- Replace all bracketed placeholders with concrete content.
- Do not omit any top-level section. If there is no confirmed content for a mandatory section, write a concise explicit fallback such as `- Not identified from available context.`.
- Omit placeholder-only bullets only after replacing the section with real content or an explicit `Not identified from available context.` bullet.
- Keep AC numbering sequential: `AC 1`, `AC 2`, `AC 3`.
- Never write AC identifiers in the form `AC-1`, `AC-2`, etc. (uppercase letters, hyphen, digits with no trailing colon). Jira's Smart Link engine matches `[A-Z]+-[0-9]+` and, because `AC` is a real project key, every such token is auto-rendered as an inline ticket card inside the Acceptance Criteria section. Always use the space-separated form `AC 1`, `AC 2`, `AC 3` instead.
- Use plain bullets under each AC category.
- Do not add an introduction, conclusion, ticket key heading, or "Acceptance Criteria for ..." prefix.
- If critical information is missing, put the blocker at the top and keep any useful existing context below it.

**IMPORTANT** Read 'input/existing_questions.json' to see existing question subtasks for this story (fields: key, summary, description, status, answer). Use answered questions as context. If you need full details run: `dmtools jira_get_ticket KEY`.

**IMPORTANT** You must check child tickets and parent story for better context using: `dmtools jira_search_by_jql`.


---

### Tracker: `ado`

#### [1] `./agents/instructions/tracker/ado_story_formatting.md`

Use GitHub-flavored Markdown exactly as shown below. Keep section headings in bold using `**Heading:**`. Use `-` for bullets.

**IMPORTANT** Read 'input/existing_questions.json' to see existing question subtasks for this story (fields: key, summary, description, status, answer). Use answered questions as context. If you need full details run: `dmtools ado_get_work_item KEY`.

**IMPORTANT** You must check child tickets and parent story for better context using: `dmtools ado_search_by_wiql`.

The output must include every top-level section in this order:
1. `**Story Points:**`
2. `**Business Context:**`
3. `**User Story:**`
4. `**Acceptance Criteria:**`
5. `**Business Rules:**`
6. `**Out of Scope:**`

**Story Points:** [1-13]

**Business Context:**
[Why is this needed from business perspective? What problem does it solve? What value does it provide?]

**User Story:**
As a [user type]
I want to [action/functionality]
So that [business value/benefit]

**Acceptance Criteria:**
AC 1 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]
- [Specific, testable requirement 3]

AC 2 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]

AC 3 - [Category Name]
- [Specific, testable requirement 1]
- [Specific, testable requirement 2]

[Continue with additional ACs as needed...]

**Business Rules:**
- [Business rule 1 - constraints, policies, regulations]
- [Business rule 2 - system behavior requirements]
- [Business rule 3 - data validation rules]

**Out of Scope:**
- [Feature/functionality explicitly not included in this story]
- [Future enhancements not part of current scope]
- [Related features that require separate stories]

## Formatting rules

- Replace all bracketed placeholders with concrete content.
- Do not omit any top-level section. If there is no confirmed content for a mandatory section, write a concise explicit fallback such as `- Not identified from available context.`.
- Omit placeholder-only bullets only after replacing the section with real content or an explicit `Not identified from available context.` bullet.
- Keep AC numbering sequential: `AC 1`, `AC 2`, `AC 3`.
- Never write AC identifiers in the form `AC-1`, `AC-2`, etc. Always use the space-separated form `AC 1`, `AC 2`, `AC 3` instead.
- Use plain bullets under each AC category.
- Do not add an introduction, conclusion, ticket key heading, or "Acceptance Criteria for ..." prefix.
- If critical information is missing, put the blocker at the top and keep any useful existing context below it.


---
