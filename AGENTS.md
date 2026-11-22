# AGENTS.md

High-level guide and key lessons for coding agents working in `epam/dm.ai`.

## CodeGraph

If `.codegraph/` exists in the project, use `codegraph_*` tools (`codegraph_context`, `codegraph_search`, `codegraph_node`, `codegraph_callers`, `codegraph_callees`, `codegraph_trace`, `codegraph_impact`) first before falling back to broad `grep`/`read` loops.

## High-Level Structure

- **`dmtools-core`**: Core Java library — Job runner (`JobRunner`), MCP tool system, tracker integrations (`TrackerClient` for Jira, ADO, GitHub, GitLab, Rally), Confluence/Figma/Teams/TestRail/Xray clients, AI providers (`AI`), and GraalJS bridge (`JobJavaScriptBridge`).
- **`dmtools-mcp-annotations`** & **`dmtools-annotation-processor`**: Source-retention `@MCPTool` / `@MCPParam` annotations and compile-time generator producing `MCPToolRegistry`, `MCPSchemaGenerator`, and `MCPToolExecutor`.
- **`dmtools-server`**: Spring Boot REST/OAuth2 server (`DmToolsServerApplication`).
- **`dmtools-automation`**: Browser/mobile automation (Selenium, Playwright, Appium).
- **`dmtools-ai-docs`**: Skill and end-user documentation (`SKILL.md`, `references/configuration/`, `references/mcp-tools/`).
- **`landing`**: Astro/TypeScript web landing and DMTools Agent Factory setup generator (`landing/src/lib/factory.ts`).

## Build & Test Commands

```bash
# Build core fat JAR
./gradlew :dmtools-core:shadowJar

# Run unit tests (fast, mocked — mandatory before finishing changes)
./gradlew :dmtools-core:test --tests "YourTestClass"
./gradlew :dmtools-core:test

# Landing / factory generator tests
npm --prefix landing test
```

## Key Lessons & Invariants

1. **Unit tests only in development & CI**: Never run `integrationTest` in regular development — it hits live external APIs and requires real credentials. Mock external clients with JUnit 5 + Mockito.
2. **MCP tool parameter types**: `@MCPTool` methods only support `String`, `Integer`/`int`, `Long`/`long`, and `String[]`. If a client method takes `boolean`, `Date`/`Calendar`, enums, or complex objects, expose a String-parameter MCP wrapper method.
3. **Registering new MCP integrations**: Adding a new integration requires:
   - Annotating methods with `@MCPTool` / `@MCPParam`
   - Registering the client in `McpCliHandler.createClientInstances()` and `getAvailableIntegrations()`
   - Updating `dmtools-ai-docs/references/` and `docs/README-MCP.md`.
4. **JavaScript agents & tool aliases**: GraalJS (`JobJavaScriptBridge`) resolves only canonical MCP tool names (e.g. `jira_get_ticket`, `github_list_prs`, `ado_get_work_item`). `tracker_*` aliases resolve only in the CLI (`McpCliHandler.resolveToolAlias`).
5. **Configuration precedence**: `config.properties` (highest) → `dmtools.env` → environment variables (lowest). All keys read by `PropertyReader` are documented in `dmtools.env.example` and `dmtools-core/PROPERTIES.md`.
6. **Thread-safe job context**: Jobs use `JobContext` thread-local storage (`JobContext.withContext(...)`) and constructor injection (Dagger 2) for isolation and testability.
7. **Code style & quality gate**:
   - Use package imports (no inline fully qualified class names unless resolving a name collision).
   - English only in code and comments.
   - Changed Java files must stay under the 2800-line quality gate limit and pass the `jscpd` duplication check.
