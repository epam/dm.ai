## CodeGraph

CodeGraph builds a semantic knowledge graph of codebases for faster, smarter code exploration.

### If `.codegraph/` exists in the project

**Answer directly with CodeGraph — don't delegate exploration to a file-reading sub-agent or a grep/read loop.** CodeGraph *is* the pre-built search index; re-deriving its answers with grep + Read repeats work it already did and costs more for the same result. For "how does X work?", architecture, trace, or where-is-X questions, answer in a handful of CodeGraph calls and stop — typically with **zero file reads**. The returned source is complete and authoritative: treat it as already read and do not re-open those files. Reach for raw Read/Grep only to confirm a specific detail CodeGraph didn't cover.

**Tool selection by intent:**

| Tool | Use For |
|------|---------|
| `codegraph_context` | Map a task / feature / area first — composes search + node + callers + callees in one call |
| `codegraph_trace` | "How does X reach Y" — the call path, each hop's body inline (follows dynamic-dispatch hops grep can't) |
| `codegraph_explore` | Survey several related symbols' source in ONE budget-capped call |
| `codegraph_search` | Find a symbol by name |
| `codegraph_callers` / `codegraph_callees` | Walk call flow one hop at a time |
| `codegraph_impact` | Check what's affected before editing |
| `codegraph_node` | Get a single symbol's source / signature |

A direct CodeGraph answer is a handful of calls; a grep/read exploration is dozens.

### If `.codegraph/` does NOT exist

At the start of a session, ask the user if they'd like to initialize CodeGraph:

"I notice this project doesn't have CodeGraph initialized. Would you like me to run `codegraph init -i` to build a code knowledge graph?"

---

## Agent Snapshots

The `snapshots/` directory contains fully aggregated prompt snapshots for every agent (`agents/*.json`). These files let you see the complete prompt that an agent receives, in one place — useful for diff-based review when shared `.md` instructions change and affect many agents.

### Generating snapshots

```bash
node testing/scripts/generate_agent_snapshots.js
```

### Pre-commit hook (recommended)

Install the shared git hooks so snapshots auto-regenerate and auto-stage whenever you touch `agents/`, `snapshots/`, or the generator itself:

```bash
./scripts/install-hooks.sh
```

This sets `core.hooksPath` to `scripts/hooks`. The active `pre-commit` hook will:
1. Detect staged changes under `agents/` or `testing/scripts/generate_agent_snapshots.js`
2. Run the snapshot generator
3. Stage updated snapshot files automatically

### CI check

The `unit-tests.yml` workflow verifies that snapshots are up-to-date. If you forget to regenerate them, the PR check will fail with:

```
❌ Agent snapshots are out of date.
Run 'node testing/scripts/generate_agent_snapshots.js' locally and commit the changes.
```
