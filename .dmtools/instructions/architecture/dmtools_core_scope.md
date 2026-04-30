# dmtools Core Scope

- Work only in the dmtools CLI/core module.
- Keep API/server/UI work out of scope.
- Target `dmtools-core/src/main/java` unless the ticket explicitly names another core path.
- Create only `[CORE]` subtasks. Do not create `[API]`, `[UI]`, or `[SD API]` subtasks.
- New MCP tools must follow the existing `@MCPTool` / `@MCPParam` annotation pattern.
- Do not propose Spring Boot endpoints, REST API work, or frontend changes.
