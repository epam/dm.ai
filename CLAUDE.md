# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Building
```bash
# Build all modules
./gradlew buildAll

# Build specific module
./gradlew :dmtools-core:shadowJar        # Core library (creates fatJAR)
./gradlew :dmtools-server:bootJar         # Spring Boot server
./gradlew :dmtools-automation:shadowJar   # Automation module

# Clean all
./gradlew cleanAll
```

### Local Installation (For Development)
```bash
# Build and install to ~/.dmtools/dmtools.jar in one command
./buildInstallLocal.sh

# This script does:
# 1. Builds the fat JAR (./gradlew :dmtools-core:shadowJar)
# 2. Reads version from gradle.properties
# 3. Copies build/libs/dmtools-v{version}-all.jar to ~/.dmtools/dmtools.jar
# 4. After this, ./dmtools.sh will use your locally built version

# Manual alternative:
./gradlew :dmtools-core:shadowJar
cp build/libs/dmtools-v*-all.jar ~/.dmtools/dmtools.jar
```

### Testing
```bash
# RECOMMENDED: Run unit tests only (fast, no external API calls)
./gradlew :dmtools-core:test

# Run all unit tests across modules
./gradlew testAll

# Run specific test
./gradlew :dmtools-core:test --tests "ClassName.methodName"

# ⚠️ AVOID: Integration tests (make real API calls, require credentials)
# ./gradlew :dmtools-core:integrationTest
# Only run integration tests when explicitly needed for API validation
```

**Important**: Integration tests are intentionally excluded from normal builds and CI:
- They make real API calls to Jira, Confluence, GitHub, etc.
- They require valid credentials in environment variables
- They are slow and can hit API rate limits
- Use unit tests for development and regular testing

### Running Locally
```bash
# CLI (after building or installing)
./dmtools.sh <command> [args]
./dmtools.sh list                    # List all MCP tools
./dmtools.sh jira_get_ticket KEY-123  # Execute MCP tool
./dmtools.sh --help                   # Show help

# With debug output
./dmtools.sh --debug <command> [args]

# Server (Spring Boot)
./gradlew :dmtools-server:bootRun
# Or after building:
java -jar dmtools-appengine.jar
```

## Architecture Overview

### Multi-Module Structure
- **dmtools-core**: Core library with Job system, MCP tools, integrations, AI providers
- **dmtools-server**: Spring Boot REST API with OAuth2, JPA persistence, multi-tenancy
- **dmtools-automation**: Browser/mobile test automation (Selenium, Playwright, Appium)
- **dmtools-mcp-annotations**: SOURCE retention annotations for MCP tool generation
- **dmtools-annotation-processor**: Compile-time code generator for MCP infrastructure

### Entry Points
1. **CLI (Jobs)**: `JobRunner.main()` - Execute 20+ jobs from command line
2. **MCP CLI**: `McpCliHandler.processMcpCommand()` - Execute 67 MCP tools
3. **REST API**: `DmToolsServerApplication.main()` - Web service with OAuth2
4. **Job Factory**: `JobRunner.createJobInstance()` - Thread-safe job instantiation

### Core Systems

#### 1. Job System (`com.github.istin.dmtools.job`)
- **Interface**: `Job<Params, Result>` - Generic job contract
- **Base**: `AbstractJob<Params, Result>` - Thread-local context support
- **Runner**: `JobRunner` - Factory-based instantiation, prevents race conditions
- **Context**: `JobContext` - Thread-local configuration and attributes
- 20+ specialized jobs: BA (RequirementsCollector, UserStoryGenerator), Dev (CodeGenerator, UnitTestsGenerator), QA (TestCasesGenerator), Reporting (DevProductivityReport)

#### 2. MCP Tools System
Three-component architecture:
1. **Annotations** (`dmtools-mcp-annotations`): `@MCPTool`, `@MCPParam` with SOURCE retention
2. **Annotation Processor** (`dmtools-annotation-processor`): Generates `MCPToolRegistry`, `MCPSchemaGenerator`, `MCPToolExecutor` at compile time into `build/generated/sources/`
3. **CLI Handler** (`McpCliHandler`): Executes tools via `mcp <tool_name> [args]`

**67+ Built-in MCP Tools**: Jira (35+), Confluence (13+), ADO (23+), Figma (12+), Teams (29+), AI (10+), File (4), CLI (1)

Tool invocation supports: JSON via `--data`, stdin via `--stdin-data`, positional args, key=value pairs

**JavaScript Access**: All MCP tools are available as direct function calls in JS agents (via GraalJS)

**Complete reference**: `docs/README-MCP.md`

#### 3. AI Integration (`com.github.istin.dmtools.ai`)
- **Interface**: `AI.java` - `chat(model, message)`, supports multi-turn conversations
- **Providers**: OpenAI, Gemini, Claude, DIAL, Bedrock (AWS), Anthropic, Ollama
- **Configuration**: Runtime provider selection via env vars (OPENAI_API_KEY, GEMINI_API_KEY, DIAL_API_KEY, ANTHROPIC_API_KEY)
- **Observer**: `ConversationObserver` pattern for monitoring
- **Token Counter**: `Claude35TokenCounter` for context management

#### 4. Integration System (`com.github.istin.dmtools.common.tracker`)
- **Interface**: `TrackerClient<T extends ITicket>` - Generic abstraction for issue tracking
- **Implementations**: Jira (`BasicJiraClient`), Confluence, GitHub, GitLab, Bitbucket, Figma, Teams, SharePoint
- Factory pattern for client instantiation

#### 5. Dependency Injection (`com.github.istin.dmtools.di`)
- **Framework**: Dagger 2
- **46+ Components**: `AIComponentsModule`, `ConfigurationModule`, `TrackerModule`, `SourceCodeModule`, agent-specific components
- **Scopes**: Singleton for most clients
- Configuration-driven provider selection

### Thread Safety Pattern
Jobs use `JobContext` with thread-local storage for isolated configuration:
```java
JobContext.withContext(context, () -> {
    // Job execution with isolated config
});
```
This enables parallel execution with different configurations without cross-thread contamination.

### Package Structure (dmtools-core/src/main/java)
```
com.github.istin.dmtools/
├── ai/              # AI integrations (AI interface, providers, JAssistant)
├── mcp/             # Model Context Protocol (CLI handler, generated code)
├── job/             # Job execution system (Job, AbstractJob, JobRunner)
├── common/          # Core abstractions (TrackerClient, config, utils)
├── atlassian/       # Jira, Confluence, Bitbucket
├── github/          # GitHub integration
├── gitlab/          # GitLab integration
├── figma/           # Figma design system
├── microsoft/       # Teams, SharePoint
├── di/              # Dagger dependency injection (46+ components)
├── ba/              # Business Analysis jobs
├── qa/              # QA jobs (TestCasesGenerator)
├── dev/             # Development jobs (CodeGenerator, UnitTestsGenerator)
├── report/          # Productivity reporting
├── documentation/   # Documentation generation
├── diagram/         # Mermaid diagram generation
├── sync/            # Source code sync jobs
├── context/         # Context management
├── kb/              # Knowledge base processing
├── js/              # JavaScript execution (GraalVM)
└── teammate/        # AI teammate workflows
```

## Configuration

Configuration sources (precedence order):
1. Environment variables
2. `dmtools.env` file (current directory or script directory)
3. `dmtools-local.env` file
4. `application.properties` (server)

Key variables:
```bash
# Jira
JIRA_BASE_PATH=https://your-company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64(email:token)
JIRA_AUTH_TYPE=Bearer

# AI Providers
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4-turbo
OPENAI_BASE_PATH=https://api.openai.com/v1/chat/completions  # Optional
OPENAI_MAX_TOKENS=4096  # Optional, default: 4096
OPENAI_TEMPERATURE=0.7  # Optional, default: -1 (don't send, use model default)
                        # Set to -1 or negative to skip sending temperature parameter
                        # Some models (o1, o3) don't support custom temperature
OPENAI_MAX_TOKENS_PARAM_NAME=max_completion_tokens  # Optional, default: "max_completion_tokens"
                                                     # Use "max_tokens" for older models (gpt-3.5-turbo)
                                                     # Use empty string to skip sending this parameter

# Gemini - API Key Mode (Public API)
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-2.0-flash-exp  # Optional, can also use GEMINI_DEFAULT_MODEL

# Gemini - Vertex AI Mode (Google Cloud Service Account)
GEMINI_VERTEX_ENABLED=true
GEMINI_VERTEX_PROJECT_ID=my-gcp-project
GEMINI_VERTEX_LOCATION=europe-west4  # or us-central1, asia-northeast1, global, etc.
GEMINI_VERTEX_API_VERSION=v1beta1    # Optional: "v1" (default) or "v1beta1" (required for global location)
GEMINI_VERTEX_CREDENTIALS_PATH=/path/to/service-account.json  # OR use inline JSON
GEMINI_VERTEX_CREDENTIALS_JSON={"type":"service_account",...}  # Alternative to file path
GEMINI_MODEL=gemini-2.5-flash-lite  # Model to use

DIAL_API_KEY=...
DIAL_MODEL=gpt-4

ANTHROPIC_API_KEY=...
ANTHROPIC_MODEL=claude-3-5-sonnet-20241022

# GitHub/GitLab
SOURCE_GITHUB_TOKEN=...
GITLAB_TOKEN=...

# Context Limits
PROMPT_CHUNK_TOKEN_LIMIT=4000
PROMPT_CHUNK_MAX_SINGLE_FILE_SIZE_MB=4
```

## Code Style Rules (from .cursor/rules/common.mdc)

### Package Imports
- Always use package imports instead of fully qualified class names
- Never write full package paths unless there's a naming conflict
- Use import statements at the top of the file
- Examples:
  - ✅ Good: `import com.github.istin.dmtools.ai.ollama.OllamaAIClient;` then use `OllamaAIClient`
  - ❌ Bad: `new com.github.istin.dmtools.ai.ollama.OllamaAIClient(...)`
  - ✅ Good (when conflict exists): `new java.util.Date()` vs `new java.sql.Date()`

### General Guidelines
- Always respond in English. No comments in code in Russian.
- Don't write documentation unless it's asked for.
- Prefer clean, readable code with proper imports over verbose fully qualified names.

### Testability and Testing Requirements

**CRITICAL: All code MUST be testable and tested before completing work.**

#### Design for Testability
- **Use Dependency Injection**: Constructor injection (Dagger 2) for all external dependencies
- **Avoid static methods**: Use instance methods to enable mocking
- **Separate concerns**: Business logic should be independent of framework code
- **Interface over implementation**: Program to interfaces for easy mocking

**Example - Testable Design**:
```java
// ✅ GOOD: Testable with dependency injection
public class TestCaseProcessor {
    private final TrackerClient jiraClient;
    private final AI aiClient;

    @Inject
    public TestCaseProcessor(TrackerClient jiraClient, AI aiClient) {
        this.jiraClient = jiraClient;
        this.aiClient = aiClient;
    }

    public List<TestCase> processTicket(String ticketKey) {
        // Can be easily tested with mocks
    }
}

// ❌ BAD: Hard to test
public class TestCaseProcessor {
    public List<TestCase> processTicket(String ticketKey) {
        BasicJiraClient client = new BasicJiraClient(); // Hard-coded dependency
        // Cannot mock external calls
    }
}
```

#### Use Mocks for External Dependencies
**Always mock**:
- API clients (Jira, Confluence, GitHub, AI providers)
- File system operations
- Database connections
- Network calls
- Time-dependent operations

**Mocking Framework**: Mockito (already configured)

**Example - Mocking in Tests**:
```java
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseProcessorTest {

    @Mock
    private TrackerClient mockJiraClient;

    @Mock
    private AI mockAiClient;

    private TestCaseProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TestCaseProcessor(mockJiraClient, mockAiClient);
    }

    @Test
    void shouldProcessTicketSuccessfully() {
        // Given
        String ticketKey = "PROJ-123";
        when(mockJiraClient.performTicket(ticketKey))
            .thenReturn(createMockTicket());

        // When
        List<TestCase> result = processor.processTicket(ticketKey);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(mockJiraClient).performTicket(ticketKey);
    }
}
```

#### Testing Before Completion (MANDATORY)

**Before finishing any development work, you MUST**:

1. **Write unit tests** for new functionality
2. **Run tests locally**: `./gradlew :dmtools-core:test`
3. **Verify all tests pass**: Check test output for failures
4. **Achieve meaningful coverage**: Test happy path + error cases
5. **Do NOT use integration tests** for regular development (they hit real APIs)

**Test Coverage Guidelines**:
- ✅ **Test**: Business logic, data transformations, validation rules
- ✅ **Test**: Edge cases, null handling, error conditions
- ✅ **Mock**: External API calls, file I/O, network operations
- ❌ **Don't test**: Simple getters/setters, framework code

**Example Development Flow**:
```bash
# 1. Write code
# 2. Write unit tests
# 3. Run tests
./gradlew :dmtools-core:test --tests "TestCaseProcessorTest"

# 4. Check results
# BUILD SUCCESSFUL means tests passed

# 5. Only then complete the task
```

**If tests fail**: Fix the code or tests, never skip this step.

## Development Workflow

### Adding a New MCP Tool
1. Create method in appropriate client class (e.g., `BasicJiraClient`, `BasicConfluence`)
2. Annotate with `@MCPTool(name, description, integration, category)`
3. Annotate parameters with `@MCPParam(name, description, required, example)`
4. Build project to trigger annotation processor: `./gradlew :dmtools-core:compileJava`
5. Generated code appears in `dmtools-core/build/generated/sources/annotationProcessor/java/main`
6. **Write unit tests** for the new tool with mocks (MANDATORY)
7. **Update documentation** in `docs/README-MCP.md`:
   - Add tool to the appropriate category table (Jira, Confluence, etc.)
   - Include tool name, parameters, and clear description
   - Follow existing format in the file
   - Update tool count if adding to new category
8. Verify tool is available:
   - CLI: `./dmtools.sh list` (should show new tool)
   - CLI execution: `./dmtools.sh <tool_name> <args>`
   - JavaScript agents: `tool_name()` function (via GraalJS)

**Complete reference**: `docs/README-MCP.md`

**Example Documentation Entry**:
```markdown
| Tool | Parameters | Description |
|------|------------|-------------|
| `jira_update_labels` | `key`, `labels` | Update labels for a Jira ticket. Labels parameter is comma-separated string. |
```

### Adding a New Job
1. Create class extending `AbstractJob<Params, Result>`
2. Implement `public Result runJob(Params params)`
3. Use `JobContext.current()` to access thread-local configuration
4. Register in `JobRunner.createJobInstance()` and `JobRunner.getStaticInstances()`
5. Add Dagger component if needed in `com.github.istin.dmtools.di`

### Working with AI
1. Access AI via `AI` interface from JobContext or dependency injection
2. Provider selected at runtime via configuration
3. Use `AI.chat(model, message)` or `AI.chat(model, Message[])` for multi-turn
4. Token counting via `Claude35TokenCounter` for context management

### Creating AI Agents

**Important Architecture Principles**:
- **Jobs are orchestrators** - coordinate multiple agents and manage workflow
- **Agents are small and focused** - each agent performs ONE specific task
- **Agents are reusable** - can be composed in different jobs

#### Java Agents (Recommended for Core Logic)

**1. Extend AbstractSimpleAgent**:
```java
public class TestCaseGeneratorAgent extends AbstractSimpleAgent<TestCaseGeneratorAgent.Params, List<TestCase>> {

    public TestCaseGeneratorAgent() {
        super("agents/test_case_generator");  // References XML prompt file
        DaggerTestCaseGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public List<TestCase> transformAIResponse(Params params, String response) throws Exception {
        // Parse AI response and return typed result
        JSONArray jsonArray = AIResponseParser.parseResponseAsJSONArray(response);
        // ... transform to List<TestCase>
    }
}
```

**2. Create XML Prompt** in `dmtools-core/src/main/resources/ftl/prompts/agents/<name>.xml`:
```xml
<prompt>
    <role>
        You're a highly skilled software testing engineer specialising in designing tests.
    </role>
    <instructions>
        Your task is to write test cases and follow these rules:
        1. Each generated Test Case must include priority from the list: {priorities}
        2. Don't create duplicates, check {existing_test_cases}
        3. Return only valid JSON format without any additional text or formatting
    </instructions>
    <input_data>
        <story_description>
            ${global.storyDescription}
        </story_description>
        <priorities>
            ${global.priorities}
        </priorities>
    </input_data>
    <formatting>
        <rules>
            Return results as a JSON array with JSON objects inside.
            Each JSON object must include 'priority', 'summary', and 'description'.
        </rules>
    </formatting>
</prompt>
```

**Key Points**:
- **Prompts MUST be in XML format** with clear structure: `<role>`, `<instructions>`, `<input_data>`, `<formatting>`
- Use FreeMarker templates for dynamic content: `${global.paramName}`
- Agent prompt path: `agents/<name>` maps to `ftl/prompts/agents/<name>.xml`
- Keep agents focused - ONE responsibility per agent

**3. Create Dagger Component** (if needed):
```java
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface TestCaseGeneratorAgentComponent {
    void inject(TestCaseGeneratorAgent agent);
}
```

#### JavaScript Agents (For Preprocessing/Orchestration)

JS agents run via **GraalJS** (polyglot JavaScript execution in JVM) and are used for:
- **Preprocessing**: Data validation, transformation before AI calls
- **Postprocessing**: Handling temporary IDs, creating related tickets
- **Light orchestration**: Coordinating simple workflows

**Location**: `agents/js/*.js`

**🔌 Full Access to MCP Tools**: JavaScript agents have direct access to **67+ MCP tools** as functions:

| Category | Example Functions | Total Tools |
|----------|------------------|-------------|
| **Jira** | `jira_get_ticket()`, `jira_search_by_jql()`, `jira_post_comment()`, `jira_create_ticket_basic()` | 35+ |
| **Jira Xray** | `jira_xray_add_test_step()`, `jira_xray_create_precondition()`, `jira_xray_get_test_details()` | 10+ |
| **Confluence** | `confluence_content_by_title()`, `confluence_search_content_by_text()`, `confluence_create_page()` | 13+ |
| **ADO** | `ado_get_work_item()`, `ado_move_to_state()`, `ado_assign_work_item()`, `ado_add_comment()` | 23+ |
| **Figma** | `figma_get_layers()`, `figma_get_icons()`, `figma_download_image_as_file()` | 12+ |
| **Teams** | `teams_send_message()`, `teams_get_messages()`, `teams_download_file()` | 29+ |
| **AI** | `gemini_ai_chat()`, `anthropic_ai_chat()`, `ollama_ai_chat()`, `bedrock_ai_chat()` | 10+ |
| **File** | `file_read()`, `file_write()`, `file_validate_json()` | 4 |
| **CLI** | `cli_execute_command()` | 1 |

**Complete reference**: See `docs/README-MCP.md` for all 67+ tools with parameters and descriptions.

**Example with MCP Tools** (`agents/js/preprocessXrayTestCases.js`):
```javascript
/**
 * Preprocess Xray Test Cases - Handle Preconditions with Temporary IDs
 * Uses MCP tools: jira_xray_create_precondition, jira_xray_add_preconditions_to_test
 */
function action(params) {
    const newTestCases = params.newTestCases;
    const ticket = params.ticket;
    const projectCode = ticket.key.split("-")[0];

    // Use MCP tool to create precondition
    for (const testCase of newTestCases) {
        if (testCase.customFields?.preconditions) {
            // Create actual precondition in Jira using MCP tool
            const precondition = jira_xray_create_precondition(
                projectCode,
                "Setup database",
                "Database must be populated with test data"
            );

            // Replace temporary ID with real precondition key
            testCase.customFields.preconditions = [precondition.key];
        }
    }

    return modifiedTestCases;
}
```

**Execution via Job Config** (`agents/*.json`):
```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (TP-1309)",
    "preprocessJSAction": "agents/js/preprocessXrayTestCases.js"
  }
}
```

**Run with**: `./dmtools.sh run agents/xray_test_cases_generator.json`

#### Agent vs Job Guidelines

| Aspect | Agent | Job |
|--------|-------|-----|
| Purpose | Single focused task | Orchestrates multiple agents |
| Size | Small (~100-200 lines) | Can be larger |
| AI Interaction | One prompt, one call | Multiple agent calls |
| Reusability | High - used by multiple jobs | Lower - specific workflow |
| Examples | TestCaseGeneratorAgent, RequestDecompositionAgent | TestCasesGenerator, CodeGenerator |

**Example Flow**:
```
Job: TestCasesGenerator
├─ Load story from Jira
├─ Agent: TestCaseGeneratorAgent → Generate test cases
├─ JS: preprocessXrayTestCases.js → Handle preconditions
├─ Create test tickets in Jira
└─ Return result
```

## Testing

- **Unit tests**: `dmtools-core/src/test/java` - USE THESE for development
- **Integration tests**: `dmtools-core/src/integrationTest/java` - AVOID in normal development
- **Test framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito 5.18.0

**MANDATORY: Test Before Completing Work**:
```bash
# Write unit tests for new code
# Run tests to verify functionality
./gradlew :dmtools-core:test

# Or run specific test class
./gradlew :dmtools-core:test --tests "YourTestClass"

# ✅ All tests must pass before completing task
# ❌ Never skip testing - it's a required step
```

**What to Test**:
- ✅ **Business logic**: Calculations, transformations, validation
- ✅ **Edge cases**: Null values, empty collections, boundary conditions
- ✅ **Error handling**: Exception scenarios, validation failures
- ✅ **Mock external calls**: Jira, Confluence, AI, file I/O, network

**What NOT to Test**:
- ❌ Simple getters/setters
- ❌ Framework code (Spring, Dagger)
- ❌ External services (use mocks instead)

**Integration Test Warning**:
- Integration tests make real API calls to external services (Jira, Confluence, GitHub, etc.)
- They require valid credentials configured in environment variables
- They are intentionally excluded from normal build and CI pipeline
- Only run explicitly when validating actual API integration: `./gradlew :dmtools-core:integrationTest`
- For regular development, rely on unit tests only

## Deployment Artifacts

- **Core CLI**: `build/libs/dmtools-v{version}-all.jar` (Main-Class: `JobRunner`)
- **Server**: `dmtools-appengine.jar` (Spring Boot executable)
- **Automation**: `dmtools-automation-v{version}-all.jar`
- **Installation**: `curl -fsSL https://raw.githubusercontent.com/IstiN/dmtools/main/install.sh | bash`

## Key Implementation Patterns

1. **Job Factory Pattern**: Fresh job instances per execution for thread safety
2. **Thread-Local Context**: Isolated configuration per thread/job
3. **Annotation-Driven Generation**: Zero-runtime overhead (SOURCE retention)
4. **Tracker Abstraction**: Generic `TrackerClient<T>` for multiple platforms
5. **Multi-Model AI**: Runtime provider selection via configuration
6. **Dagger DI**: Modular components with singleton scope
7. **Agent Architecture**: Jobs orchestrate, agents execute (small, focused, reusable)
8. **XML-Based Prompts**: All AI agent prompts in XML format with FreeMarker templates
9. **GraalJS Integration**: JavaScript agents for preprocessing/postprocessing via polyglot execution
