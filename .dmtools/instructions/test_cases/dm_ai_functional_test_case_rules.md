# DM.AI Functional Test Case Rules

Use these rules when generating Test Case tickets for the DM.AI project.

## Goal

Generate test cases that validate the real product behavior a user or maintainer depends on, not only that files were edited.

For documentation stories, a good test case must prove that the documentation is correct by checking it against the implementation, CLI behavior, generated artifacts, or repository workflows.

## Prefer functional evidence

Each test case should include at least one concrete evidence source:

- a `dmtools` CLI command and expected output;
- a source-of-truth implementation file or config that defines the behavior;
- a generated documentation artifact that users consume;
- a GitHub workflow, install script, or release artifact used by users;
- a Jira/GitHub integration result visible to users.

Avoid test cases that only say "open the document and inspect it" unless the expected result names exact entries, exact files, and how they are verified.

## DM.AI documentation stories

When the story updates `teammate-configs.md`, `dmtools-ai-docs`, agent names, or agent descriptions:

1. Verify each documented agent against implementation/discovery sources, for example:
   - `./dmtools.sh list`
   - `dmtools-core/src/main/java/com/github/istin/dmtools/job/JobRunner.java`
   - agent JSON configs under `agents/`
   - generated docs under `dmtools-ai-docs/`
2. Validate concrete user-facing content:
   - public agent name;
   - short description;
   - whether it is active or deprecated;
   - replacement/migration path for deprecated aliases;
   - working usage example link.
3. Split cases by behavior, not by broad document section:
   - "agent registry names match CLI discovery";
   - "usage links resolve and show executable examples";
   - "deprecated aliases point to replacement public agents";
   - "generated skill docs include updated agent summaries".
4. Do not create cases that require checking unrelated files such as `CHANGELOG.md` unless the story explicitly asks for them.

## Step quality

Steps must be executable by a human or automatable by `test_case_automation`:

- include exact command or file path;
- include exact expected text/pattern where possible;
- define failure conditions clearly;
- avoid vague words like "correct", "relevant", "updated", or "proper" unless followed by measurable criteria.

## Expected result quality

Expected results must be specific enough to fail when documentation is misleading.

Good:
`./dmtools.sh list` contains `TestCasesGenerator`, and `dmtools-ai-docs/references/agents/teammate-configs.md` documents the same name with a usage link to `agents/test_cases_generator.json`.

Bad:
All docs are accurate and links are relevant.

## Coverage balance

For small documentation stories, prefer 3-5 high-signal cases over many broad cases:

1. registry/source-of-truth consistency;
2. user-facing documentation content;
3. usage example links or commands;
4. generated docs/skill package consistency if generation is part of the workflow;
5. deprecated alias migration only when obsolete names are actually present.
