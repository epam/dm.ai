# DMtools Jobs Reference

Complete reference for all 23 available jobs in DMtools. Jobs are specialized workflows that orchestrate MCP tools, AI agents, and data processing.

---

## ⚠️ CRITICAL: The "name" Field is NOT User-Defined

**Before reading further, understand this fundamental rule:**

```json
{
  "name": "TestCasesGenerator"  // ← This is Java Class Name (NOT customizable)
}
```

The `"name"` field is a **technical identifier** that maps to a Java class in DMtools:

```
"name": "TestCasesGenerator" → new TestCasesGenerator() (Java code)
```

**What you MUST do:**
- ✅ Use **exact** class name from list below: `TestCasesGenerator`, `Teammate`, `Expert`, etc.
- ✅ Copy name exactly as shown (case-sensitive)
- ✅ Refer to [JSON Configuration Rules](../configuration/json-config-rules.md) when in doubt

**What you MUST NOT do:**
- ❌ Change or customize the name field
- ❌ Use descriptive names like "My Test Generator"
- ❌ Use different case like "testcasesgenerator"

**Why?** Because DMtools uses this name to instantiate the correct Java class. If the name doesn't match exactly, you get: `Error: Unknown job: <your-name>`

**See**: [JSON Configuration Rules](../configuration/json-config-rules.md) for complete explanation.

---

## 📋 Job Categories

### Business Analysis (BA)
- [RequirementsCollector](#requirementscollector) - Gather and analyze requirements from tickets
- [UserStoryGenerator](#userStorygenerator) - Generate user stories from requirements
- [PreSaleSupport](#presalesupport) - Pre-sales analysis and proposals
- [BAProductivityReport](#baproductivityreport) - BA team productivity metrics

### Quality Assurance (QA)
- [TestCasesGenerator](#testcasesgenerator) - Generate test cases from stories (Xray, Cucumber)
- [QAProductivityReport](#qaproductivityreport) - QA team productivity metrics

### Development (Dev)
- [CodeGenerator](#codegenerator) - Generate code from user stories
- [UnitTestsGenerator](#unittestsgenerator) - Generate unit tests for code
- [DevProductivityReport](#devproductivityreport) - Dev team productivity metrics
- [CommitsTriage](#commitstriage) - Analyze and categorize commits

### Architecture & Design
- [SolutionArchitectureCreator](#solutionarchitecturecreator) - Create solution architecture docs
- [DiagramsCreator](#diagramscreator) - Generate Mermaid diagrams
- [InstructionsGenerator](#instructionsgenerator) - Generate implementation instructions

### Productivity Reports
- [QAProductivityReport](#qaproductivityreport) - QA team productivity metrics
- [DevProductivityReport](#devproductivityreport) - Development team productivity metrics
- [BAProductivityReport](#baproductivityreport) - Business Analyst team productivity metrics

### Project Management
- [JEstimator](#jestimator) - Estimate story points and effort
- [ScrumMasterDaily](#scrummasterdaily) - Daily scrum reports
- [BusinessAnalyticDORGeneration](#businessanalyticdorgeneration) - Definition of Ready

### Documentation
- [DocumentationGenerator](#documentationgenerator) - Generate technical documentation

### AI Assistants
- **[Teammate](#teammate)** - Flexible AI teammate with custom instructions
- **[Expert](#expert)** - Domain expert for answering questions

### Utilities
- [JSRunner](#jsrunner) - Run JavaScript agents
- [KBProcessingJob](#kbprocessingjob) - Process knowledge base
- [SourceCodeTrackerSyncJob](#sourcecodetrackersy ncjob) - Sync source code with tracker
- [SourceCodeCommitTrackerSyncJob](#sourcecodecommittrackersy ncjob) - Sync commits with tracker

---

## 🎯 Featured Jobs

### TestCasesGenerator

Generate test cases from Jira stories using AI.

**Purpose**: Automatically create test cases (Xray format or Cucumber/Gherkin) from user stories.

**Usage**:
```bash
# Generate test cases for specific stories
dmtools TestCasesGenerator --inputJql "key in (PROJ-123, PROJ-456)"

# Generate for all stories in sprint
dmtools TestCasesGenerator --inputJql "sprint in openSprints() AND type = Story"

# Use configuration file
dmtools run agents/xray_test_cases_generator.json
```

**Configuration** (`agents/xray_test_cases_generator.json`):

**IMPORTANT**: The `"name"` field must exactly match the Job class name. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (TP-1309)",
    "testCasesPriorities": "Highest, High, Medium, Lowest, Low",
    "outputType": "creation",
    "existingTestCasesJql": "project = TP and issueType in ('Test', 'Precondition') and status not in (archived)",
    "testCasesRelatedFields": ["issuetype","summary", "description", "priority"],
    "testCasesExampleFields": ["issuetype", "summary", "description", "priority"],
    "testCasesCustomFields": ["xrayTestSteps", "xrayPreconditions"],
    "customFieldsRules": "Test steps must be generated in Xray JSON format...",
    "confluencePages": ["https://yourcompany.atlassian.net/wiki/spaces/YOUR_SPACE/pages/PAGE_ID/Template+Test+Case"],
    "relatedTestCasesRules": "https://yourcompany.atlassian.net/wiki/spaces/YOUR_SPACE/pages/PAGE_ID/Template+Test+Case+Related+Rules",
    "isOverridePromptExamples": true,
    "isFindRelated": true,
    "isConvertToJiraMarkdown": false,
    "includeOtherTicketReferences": true,
    "testCaseLinkRelationship": "relates to",
    "testCaseIssueType": "Test",
    "preprocessJSAction": "agents/js/preprocessXrayTestCases.js",
    "examples": "ql(project = TP and issuetype in (\"Test\") and labels = \"ai_example\")"
  }
}
```

**Core Parameters** (from TestCasesGeneratorParams):
- `existingTestCasesJql` - JQL to find existing test cases for deduplication
- `testCasesPriorities` - Comma-separated priority list (e.g., "High,Medium,Low")
- `testCaseIssueType` - Jira issue type for created tests (default: "Test Case")
- `relatedTestCasesRules` - Additional rules for finding related test cases (URL or text)
- `examples` - Examples for AI: text, URL, or `ql(JQL query)` to fetch from Jira
- `testCasesCustomFields` - Array of custom field names to include (e.g., Xray fields)
- `customFieldsRules` - Rules for custom fields (URL or text)

**Behavior Flags**:
- `isFindRelated` - Find and link existing related test cases (default: true)
- `isLinkRelated` - Actually link found test cases (default: true)
- `isGenerateNew` - Generate new test cases (default: true)
- `isConvertToJiraMarkdown` - Convert output to Jira markdown (default: true)
- `includeOtherTicketReferences` - Include linked tickets in context (default: true)
- `isOverridePromptExamples` - Override default prompt examples (default: false)

**Relationships**:
- `testCaseLinkRelationship` - Default relationship type (default: "is tested by")
- `testCaseLinkRelationshipForNew` - Relationship for new test cases (overrides default)
- `testCaseLinkRelationshipForExisting` - Relationship for existing test cases (overrides default)

**AI Models** (optional, defaults from config):
- `modelTestCasesCreation` - Model for generating test cases
- `modelTestCasesRelation` - Model for finding related test cases
- `modelTestCaseRelation` - Model for verifying individual test case relevance
- `modelTestCaseDeduplication` - Model for deduplication

**JavaScript Actions**:
- `preprocessJSAction` - JS file to preprocess test cases (e.g., handle preconditions)
- `postJSAction` - JS file to run after test case creation
- `jqlModifierJSAction` - JS file to dynamically modify existingTestCasesJql based on story

**Performance Tuning**:
- `enableParallelTestCaseCheck` - Enable parallel processing of test case chunks (default: false)
- `parallelTestCaseCheckThreads` - Thread count for parallel checks (default: 5)
- `enableParallelPostVerification` - Enable parallel verification (default: false)
- `parallelPostVerificationThreads` - Thread count for verification (default: 3)

**Inherited from TrackerParams**:
- `inputJql` - JQL query to find stories
- `initiator` - User who initiated the job
- `targetProject` - Target project code for test creation
- `outputType` - Output format: `creation` (create tickets), `field` (update field), `comment` (post comment), `none` (dry run)
- `fieldName` - Field name for `outputType: field`
- `operationType` - Operation: `Replace` or `Append` (default: Append)
- `preJSAction` - JavaScript to run before processing each ticket
- `attachResponseAsFile` - Attach AI response as file (default: false)

**Output Formats**:
1. **Xray Manual Test** - Step-by-step test cases with expected results
2. **Cucumber/Gherkin** - Scenario Outline with data tables

**See also**: [Test Generation Guide](../test-generation/xray-manual.md)

---

### InstructionsGenerator

Generate standardized instructions and templates by analyzing patterns in existing tickets.

**Purpose**: Extract common patterns from tickets (stories, test cases, specs) and generate reusable instructions/guidelines for creating similar content.

**Usage**:
```bash
# Generate story writing instructions from existing stories
dmtools InstructionsGenerator --inputJql "project = PROJ AND type = Story" \
  --fields "summary,description,acceptance_criteria" \
  --instructionType "user_story" \
  --outputDestination "file" \
  --outputPath "output/story-instructions.md"

# Generate test case guidelines and output to Confluence
dmtools InstructionsGenerator --inputJql "project = QA AND type = Test" \
  --fields "summary,description,test_steps" \
  --instructionType "test_cases" \
  --outputDestination "confluence" \
  --outputPath "https://yourcompany.atlassian.net/wiki/spaces/YOUR_SPACE/pages/PAGE_ID/Test+Guidelines"

# Use configuration file
dmtools run agents/instructions_generator.json
```

**Configuration** (`agents/instructions_generator_dmc.json`):

**IMPORTANT**: The `"name"` field must exactly match the Job class name. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "InstructionsGenerator",
  "params": {
    "inputJql": "project = DMC AND type = Story",
    "fields": ["summary", "description", "diagrams"],
    "instructionType": "user_story",
    "outputDestination": "file",
    "outputPath": "output/dmc-story-instructions.md",
    "mergeWithExisting": false,
    "additionalContext": "Focus on story description and diagrams formats. Pay attention to how diagrams are referenced and embedded in stories. Never mention texts from tickets in examples! That must be generic examples. With abstractions."
  }
}
```

**Core Parameters** (from InstructionsGeneratorParams):
- `fields` - **List of field names** to analyze and generate instructions for
  - Examples: `["summary", "description", "acceptance_criteria"]` for stories
  - Examples: `["description", "test_steps", "expected_results"]` for test cases
- `instructionType` - Type of instructions to generate (e.g., "user_story", "test_cases", "technical_spec")
- `outputDestination` - Where to write output: `"file"` or `"confluence"`
- `outputPath` - Output location:
  - For file: absolute or relative file path (e.g., `"output/instructions.md"`)
  - For Confluence: full URL to the page (e.g., `"https://company.atlassian.net/wiki/spaces/SPACE/pages/123/Page"`)

**Optional Parameters**:
- `confluencePages` - Array of Confluence page URLs or local file paths with additional context/rules
  - Example: `["https://company.atlassian.net/wiki/spaces/QA/pages/456/Test+Standards"]`
- `mergeWithExisting` - Merge new instructions with existing content (default: `true`)
  - When `true`, preserves existing content and intelligently merges with new instructions
  - When `false`, replaces existing content completely
- `model` - AI model to use (optional, uses default if not specified)
  - Example: `"gemini-2.0-flash"`, `"gpt-4o"`, `"claude-3-7-sonnet"`
- `additionalContext` - Custom context or rules for instruction generation
  - Example: `"Focus on mobile-first design patterns and accessibility"`
- `platform` - Target platform for formatting rules (default: `"jira"`)
  - Options: `"jira"`, `"ado"`, `"confluence"`, `"github"`, `"gitlab"`

**Performance Parameters**:
- `generationThreads` - Number of threads for parallel instruction generation (default: `4`)
  - Higher values speed up processing of large ticket sets
  - Recommended: 4-8 threads depending on AI provider rate limits
- `mergingThreads` - Number of threads for parallel merging of instruction chunks (default: `2`)
  - Merging is memory-intensive, so lower thread count is recommended
  - Recommended: 2-4 threads

**Inherited from TrackerParams**:
- `inputJql` - JQL query to find tickets to analyze
- `initiator` - User who initiated the job
- `targetProject` - Target project code

**How It Works**:
1. **Fetch tickets** - Retrieves all tickets matching `inputJql`
2. **Chunk preparation** - Splits tickets into manageable chunks based on token limits
3. **Parallel generation** - Processes chunks in parallel using multiple threads
4. **Pattern extraction** - AI analyzes specified fields to identify common patterns
5. **Instruction creation** - Generates standardized instructions based on patterns
6. **Merging** - Combines instructions from all chunks, removing duplicates
7. **Optional merge with existing** - If enabled, merges with existing content in output location
8. **Output** - Writes final instructions to file or Confluence page

**Output Format**:
Generated instructions typically include:
- **Field definitions** - What each field should contain
- **Format guidelines** - Structure and formatting rules
- **Best practices** - Quality criteria and common patterns
- **Examples** - Abstract examples (not actual ticket data)
- **Anti-patterns** - What to avoid

**Use Cases**:
1. **Story Writing Guidelines** - Generate instructions for writing user stories based on best examples from the project
2. **Test Case Templates** - Extract test case patterns to standardize test documentation
3. **Technical Spec Standards** - Create technical specification guidelines from existing specs
4. **Acceptance Criteria Patterns** - Generate AC writing instructions based on high-quality examples
5. **Bug Report Templates** - Create bug reporting standards from well-written bug reports
6. **Documentation Standards** - Extract documentation patterns for consistency

**Example Workflow**:
```bash
# Step 1: Generate initial instructions from 50 best stories
dmtools run agents/instructions_generator.json

# Step 2: Review and manually refine the generated instructions

# Step 3: Update instructions as project evolves (merges with existing)
# Edit config: "mergeWithExisting": true
dmtools run agents/instructions_generator.json
```

**Integration with Confluence**:
- Can read additional context from Confluence pages via `confluencePages` parameter
- Can write output directly to Confluence via `outputDestination: "confluence"`
- Automatically updates existing Confluence pages when `mergeWithExisting: true`
- Requires `CONFLUENCE_URL` and `CONFLUENCE_TOKEN` environment variables

**Tips**:
- Use high-quality tickets in your JQL query (e.g., add `AND status = Done AND labels = "best-practice"`)
- Start with `mergeWithExisting: false` for initial generation, then switch to `true` for updates
- Include diverse examples in your query to capture different patterns
- Use `additionalContext` to guide the AI toward specific aspects you want to emphasize
- For large datasets (100+ tickets), increase `generationThreads` to 6-8 for faster processing
- Review generated instructions before deploying to team - AI provides a starting point that benefits from human refinement

---

### Teammate

Flexible AI assistant that can be configured for any task with custom instructions.

**Purpose**: General-purpose AI teammate that follows your custom instructions to analyze tickets, generate content, or perform automated workflows.

**Usage**:
```bash
# Run teammate with configuration
dmtools run agents/teammate_config.json

# Direct execution
dmtools Teammate --inputJql "key = PROJ-123"
```

**Configuration** (`agents/story_description.json` - real example):

**IMPORTANT**: The `"name"` field must exactly match the Job class name. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "Teammate",
  "params": {
    "metadata": {
      "contextId": "story_description"
    },
    "agentParams": {
      "aiRole": "Experienced Business Analyst",
      "instructions": [
        "https://yourcompany.atlassian.net/wiki/spaces/YOUR_SPACE/pages/PAGE_ID/Template+Story",
        "./agents/instructions/common/response_output.md",
        "./agents/instructions/common/no_development.md",
        "./agents/instructions/common/error_handling.md",
        "./agents/instructions/common/preserve_references.md",
        "./agents/instructions/common/media_handling.md",
        "./agents/instructions/common/jira_context.md",
        "./agents/instructions/enhancement/no_ticket_reference.md",
        "**IMPORTANT** your role just write description of the story based on the confluence page template!"
      ],
      "knownInfo": "",
      "formattingRules": "https://yourcompany.atlassian.net/wiki/spaces/YOUR_SPACE/pages/PAGE_ID/Template+Jira+Markdown",
      "fewShots": ""
    },
    "cliCommands": [
      "./cicd/scripts/run-cursor-agent.sh \"**IMPORTANT** implementation details and development is not part of the task...\""
    ],
    "outputType": "field",
    "fieldName": "Description",
    "operationType": "Replace",
    "ticketContextDepth": 0,
    "attachResponseAsFile": false,
    "skipAIProcessing": true,
    "inputJql": "key = DMC-532",
    "initiator": "712020:2a248756-40e8-49d6-8ddc-6852e518451f",
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/assignForReview.js"
  }
}
```

**Agent Parameters** (nested in `agentParams` - RequestDecompositionAgent.Result):
- `aiRole` - Role for the AI (e.g., "Senior Engineer", "QA Lead", "Architect") - can be URL to Confluence
- `request` - Specific request/question for this ticket (set automatically from ticket text)
- `instructions` - Array of instruction strings or URL to Confluence page - what the AI should do
- `tasks` - Array of specific tasks to complete
- `questions` - Array of questions to answer
- `knownInfo` - Known information/context - can be URL to Confluence or ticket references
- `formattingRules` - How to format the output - can be URL to Confluence
- `fewShots` - Example inputs/outputs for AI training - can be URL or text

**Teammate-Specific Parameters** (from TeammateParams):
- `hooksAsContext` - Array of hook names to call and include responses as context (e.g., ["build", "test"])
- `cliCommands` - Array of CLI commands to execute (e.g., ["cursor-agent --help", "npm test"])
- `skipAIProcessing` - Skip AI processing and use only CLI output (default: false)
- `indexes` - Array of index configurations for additional context
  - Each index has `integration` (index name) and `storagePath` (path to index)
- `systemRequestCommentAlias` - Alias for system request in comments

**Index Configuration** (IndexConfig):
```json
{
  "indexes": [
    {
      "integration": "mermaid-architecture",
      "storagePath": "/path/to/mermaid/index"
    }
  ]
}
```

**CLI Integration**:

**IMPORTANT**: When using CLI agents (Cursor, Claude, Copilot, Gemini CLI), set `skipAIProcessing: true`.

Teammate can execute external CLI agents with full workspace context:
- **Input folder**: Teammate creates `input/` with ticket context
- **CLI execution**: Agents (cursor-agent, claude, copilot, etc.) run with full codebase access
- **Output folder**: CLI agents write results to `output/`
- **Post-processing**: JavaScript post-actions process `output/` files (create PRs, update tickets, etc.)

**Pattern**: Input context → CLI agent → Output files → Post-action processing

**Use cases**: Code generation, bug fixing, test creation where full workspace context is needed.

**See**: [CLI Integration Guide](../agents/cli-integration.md) for complete examples and patterns.

**Inherited from TrackerParams**:
- `inputJql` - JQL query to find tickets
- `initiator` - User who initiated the job
- `outputType` - Output format: `field`, `comment`, `none` (default: comment)
- `fieldName` - Field name for `outputType: field`
- `operationType` - Operation: `Replace` or `Append` (default: Append)
- `preJSAction` - JavaScript to run before AI processing (can return false to skip)
- `postJSAction` - JavaScript to run after AI processing
- `attachResponseAsFile` - Attach AI response as file (default: false)
- `ticketContextDepth` - Depth of linked tickets to include (default: 1)
- `chunkProcessingTimeoutInMinutes` - Timeout for chunk processing (default: 0 = no timeout)

**Inherited from Params** (for code/Confluence search):
- `isCodeAsSource` - Search codebase for context (default: false)
- `isConfluenceAsSource` - Search Confluence for context (default: false)
- `isTrackerAsSource` - Search tracker for context (default: false)
- `sourceCodeConfig` - Array of source code configurations
- `filesLimit` - Max files from code search (default: 10)
- `confluenceLimit` - Max Confluence pages (default: 10)
- `trackerLimit` - Max tracker tickets (default: 10)

**Common Use Cases**:
1. **Code Review** - Analyze pull requests and provide feedback
2. **Architecture Review** - Review solution designs
3. **Requirement Analysis** - Extract requirements from stories
4. **Estimation** - Estimate effort for tickets
5. **Documentation** - Generate technical documentation
6. **CLI Tool Integration** - Run tools like cursor-agent and process their output
7. **Index-Based Analysis** - Analyze using Mermaid diagrams or other indexed data

**See also**:
- [Teammate Configuration Guide](../agents/teammate-configs.md)
- [GitHub Actions Workflow](../workflows/github-actions-teammate.md) - Run Teammate in CI/CD

---

### Expert

Domain expert that answers questions based on context (tickets, documentation, code).

**Purpose**: Ask questions about your project and get AI-powered answers based on actual project context from Jira, Confluence, or code.

**Usage**:
```bash
# Ask question about specific tickets
dmtools Expert --inputJql "key in (PROJ-123, PROJ-456)" --request "What are the main technical challenges?"

# Analyze entire feature
dmtools Expert --inputJql "Epic Link = PROJ-100" --systemRequest "What is the overall architecture?"

# Use configuration
dmtools run agents/expert_config.json
```

**Configuration** (`agents/expert_config.json`):
```json
{
  "name": "Expert",
  "params": {
    "inputJql": "project = PROJ AND component = Backend",
    "systemRequest": "You are a senior software architect. Analyze the tickets and provide architectural recommendations.",
    "request": "What are the main API endpoints and their purposes?",
    "projectContext": "This is a microservices-based system using Spring Boot and PostgreSQL",
    "outputType": "comment",
    "isCodeAsSource": true,
    "isConfluenceAsSource": true,
    "filesLimit": 20,
    "requestDecompositionChunkProcessing": false
  }
}
```

**Core Parameters** (from ExpertParams):
- `projectContext` - Overall project context (text or Confluence URL) - describes the project
- `request` - Specific question or request to analyze each ticket
- `systemRequest` - System-level instructions (text or Confluence URL) - defines expert role and behavior
- `systemRequestCommentAlias` - Alias for system request shown in comments
- `keywordsBlacklist` - Keywords to exclude from search (text or Confluence URL)
- `requestDecompositionChunkProcessing` - Process context in chunks for large datasets (default: false)

**Context Sources**:
Expert can gather context from multiple sources using flags from Params:
- `isCodeAsSource` - Search and include codebase files (default: false)
- `isConfluenceAsSource` - Search and include Confluence pages (default: false)
- `isTrackerAsSource` - Search and include related tickets (default: false)
- `confluencePages` - Array of specific Confluence page URLs to include
- `transformConfluencePagesToMarkdown` - Convert Confluence to markdown (default: true)

**Search Limits** (when using context sources):
- `filesLimit` - Max files from code search (default: 10)
- `filesIterations` - Number of search iterations for code (default: 1)
- `confluenceLimit` - Max Confluence pages from search (default: 10)
- `confluenceIterations` - Number of search iterations for Confluence (default: 1)
- `trackerLimit` - Max tracker tickets from search (default: 10)
- `trackerIterations` - Number of search iterations for tracker (default: 1)

**Source Code Configuration**:
- `sourceCodeConfig` - Array of SourceCodeConfig objects for code repositories
  - Each config specifies repository path, branch, file patterns, etc.

**Inherited from TrackerParams**:
- `inputJql` - JQL query to find tickets for analysis
- `initiator` - User who initiated the job
- `outputType` - Output format: `field`, `comment` (default), `none`
- `fieldName` - Field name for `outputType: field`
- `operationType` - Operation: `Replace` or `Append` (default: Append)
- `preJSAction` - JavaScript to run before AI processing (can return false to skip)
- `postJSAction` - JavaScript to run after AI processing
- `attachResponseAsFile` - Attach AI response as file (default: false)
- `ticketContextDepth` - Depth of linked tickets to include (default: 1)
- `chunkProcessingTimeoutInMinutes` - Timeout for chunk processing (default: 0 = no timeout)

**Use Cases**:
1. **Onboarding** - "What does this project do?" with codebase and Confluence context
2. **Technical Debt** - "What are the main technical debt items?" across tickets and code
3. **Dependencies** - "What external services does this feature depend on?" with code analysis
4. **Testing Strategy** - "What should be our testing approach for this feature?"
5. **Architecture Questions** - "How does authentication work?" with code and documentation context
6. **Impact Analysis** - "What will be affected by this change?" with codebase search

**Output**: Returns AI-generated answer based on all provided context (tickets, code, Confluence).

**How It Works**:
1. Loads tickets from `inputJql`
2. Optionally searches codebase, Confluence, or tracker for additional context
3. Processes all context through AI with `systemRequest` and `request`
4. Returns structured answer based on all gathered information

---

### QAProductivityReport

Generate productivity reports for QA team showing test cases created, bugs found, stories moved to done, and other QA metrics.

**Purpose**: Track QA team productivity over time with metrics like test cases created, bugs reported, stories tested, field changes, and comment activity.

**Usage**:
```bash
# Generate QA productivity report
dmtools QAProductivityReport --start_date "01.01.2026" --inputJql "project = QA"

# Use configuration file
dmtools run reports/qa_productivity.json
```

**Configuration** (`reports/qa_productivity.json`):

**IMPORTANT**: The `"name"` field must be exactly `"QAProductivityReport"`. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "QAProductivityReport",
  "params": {
    "start_date": "01.01.2026",
    "inputJql": "project = QA AND issuetype in ('Test', Story, Task, Bug) AND (created >= 2026-01-01 OR updated >= 2026-01-01)",
    "report_name": "qa_team_report",
    "is_weight": true,
    "test_cases_project_code": "QA",
    "bugs_project_code": "QA",
    "statuses_done": ["Done", "Closed"],
    "statuses_in_testing": ["In Testing", "Testing"],
    "statuses_in_development": ["In Progress", "In Review"],
    "ignore_ticket_prefixes": ["[DRAFT]", "[POC]"],
    "formula": "reports/qa_productivity_formula.js",
    "employees": "reports/qa_team.json",
    "comments_regex": ".*tested.*|.*verified.*"
  }
}
```

**Core Parameters** (from QAProductivityReportParams):
- `bugs_project_code` - Project code where bugs are created (e.g., "QA", "BUGS")
- `test_cases_project_code` - Project code where test cases are created (e.g., "QA", "TESTS")
- `statuses_done` - Array of statuses considered "done" (e.g., ["Done", "Closed"])
- `statuses_in_testing` - Array of statuses for testing phase (e.g., ["In Testing"])
- `statuses_in_development` - Array of development statuses (e.g., ["In Progress", "In Review"])
- `comments_regex` - Regex to filter relevant QA comments (optional)

**Common Parameters** (from ProductivityJobParams):
- `start_date` - Report start date in format "DD.MM.YYYY" (e.g., "01.01.2026")
- `end_date` - Report end date (optional, defaults to now)
- `report_name` - Name for generated report file
- `is_weight` - Use story points for weighting (default: false)
- `is_dark_mode` - Generate report in dark mode (default: false)
- `formula` - Path to JavaScript formula file for custom calculations
- `employees` - Path to JSON file with employee list
- `ignore_ticket_prefixes` - Array of prefixes to ignore (e.g., ["[DRAFT]", "[POC]"])

**Inherited from BaseJobParams**:
- `inputJql` - JQL query to find tickets for analysis

**Metrics Tracked**:
- Created bugs count
- Created test cases count
- Stories moved to Done (by responsible QA)
- Items moved to Reopened (First Time Right failures)
- Number of attachments added
- Number of components assigned
- Ticket fields changed
- Test ticket links created
- Comments written (matching regex)
- Vacation days

**Output**: HTML report file with metrics grouped by employee and time period (weeks by default).

**Employee File Format** (`reports/qa_team.json`):
```json
{
  "testers": [
    {
      "name": "John Doe",
      "jiraName": "john.doe",
      "email": "john.doe@company.com"
    },
    {
      "name": "Jane Smith",
      "jiraName": "jane.smith",
      "email": "jane.smith@company.com"
    }
  ]
}
```

**Formula File** (`reports/qa_productivity_formula.js`):
```javascript
// Custom productivity calculation
function calculate(metrics) {
    var score = 0;
    score += metrics.created_tests * 3;
    score += metrics.created_bugs * 2;
    score += metrics.stories_done * 5;
    return score;
}
```

---

### DevProductivityReport

Generate productivity reports for Development team showing stories/bugs moved to testing, pull requests, code changes, and time spent metrics.

**Purpose**: Track developer productivity with metrics like stories completed, bugs fixed, pull requests, code review activity, and time spent in different statuses.

**Usage**:
```bash
# Generate Dev productivity report
dmtools DevProductivityReport --start_date "01.01.2026" --inputJql "project = DEV"

# Use configuration file
dmtools run reports/dev_productivity.json
```

**Configuration** (`reports/dev_productivity.json`):

**IMPORTANT**: The `"name"` field must be exactly `"DevProductivityReport"`. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "DevProductivityReport",
  "params": {
    "start_date": "01.01.2026",
    "inputJql": "project = DEV AND issuetype in (Story, Bug, Task) AND (created >= 2026-01-01 OR updated >= 2026-01-01)",
    "report_name": "dev_team_report",
    "is_weight": true,
    "statuses_ready_for_testing": ["Ready for Testing", "Code Review Done"],
    "statuses_in_testing": ["In Testing", "QA"],
    "statuses_in_development": ["In Progress", "In Review", "Development"],
    "initial_status": "To Do",
    "calc_weight_type": "STORY_POINTS",
    "time_period_type": "WEEKS",
    "sources": [
      {
        "type": "github",
        "workspace": "my-org",
        "repository": "my-repo",
        "branch": "main"
      }
    ],
    "formula": "reports/dev_productivity_formula.js",
    "employees": "reports/dev_team.json",
    "ignore_ticket_prefixes": ["[SPIKE]", "[RESEARCH]"],
    "comment_regex_responsible": "Implemented by:\\s+(\\w+)"
  }
}
```

**Core Parameters** (from DevProductivityReportParams):
- `statuses_ready_for_testing` - Statuses when work is ready for QA (e.g., ["Ready for Testing"])
- `statuses_in_testing` - QA/testing statuses (e.g., ["In Testing", "QA"])
- `statuses_in_development` - Development work statuses (e.g., ["In Progress", "In Review"])
- `initial_status` - Starting status for time calculation (e.g., "To Do")
- `calc_weight_type` - How to calculate weight: `TIME_SPENT` or `STORY_POINTS`
- `time_period_type` - Report grouping: `WEEKS` or `QUARTERS`
- `sources` - Array of source code repository configurations (GitHub, GitLab, Bitbucket)
- `comment_regex_responsible` - Regex to extract responsible developer from comments (optional)
- `excel_metrics_params` - Additional Excel-based metrics (optional)

**Common Parameters** (from ProductivityJobParams):
- Same as QAProductivityReport (start_date, report_name, is_weight, formula, employees, etc.)

**Metrics Tracked**:
- Stories moved to Testing (total and First Time Right)
- Bugs moved to Testing (total and First Time Right)
- Time spent on story development (in days)
- Time spent on bugfixing (in days)
- Pull requests created
- Pull request changes (lines added/removed)
- Pull request comments given (positive and negative)
- Pull request approvals
- Vacation days

**Source Configuration** for Pull Request Metrics:
```json
"sources": [
  {
    "type": "github",
    "workspace": "my-organization",
    "repository": "backend-service",
    "branch": "main",
    "token": "${SOURCE_GITHUB_TOKEN}"
  }
]
```

**CalcWeightType Options**:
- `TIME_SPENT` - Weight by actual time spent in development statuses
- `STORY_POINTS` - Weight by story points from ticket

**TimePeriodType Options**:
- `WEEKS` - Group metrics by weeks
- `QUARTERS` - Group metrics by quarters

**Output**: HTML report file with developer metrics grouped by time period, including PR activity if source repositories configured.

---

### BAProductivityReport

Generate productivity reports for Business Analyst team showing features/stories created, tickets moved to done, field changes, and Figma activity.

**Purpose**: Track BA team productivity with metrics like features created, stories written, tickets completed, field updates, and design collaboration (Figma comments).

**Usage**:
```bash
# Generate BA productivity report
dmtools BAProductivityReport --start_date "01.01.2026" --inputJql "project = BA"

# Use configuration file
dmtools run reports/ba_productivity.json
```

**Configuration** (`reports/ba_productivity.json`):

**IMPORTANT**: The `"name"` field must be exactly `"BAProductivityReport"`. See [JSON Configuration Rules](../configuration/json-config-rules.md).

```json
{
  "name": "BAProductivityReport",
  "params": {
    "start_date": "01.01.2026",
    "inputJql": "project = BA AND issuetype in (Feature, Story, Task) AND (created >= 2026-01-01 OR updated >= 2026-01-01)",
    "report_name": "ba_team_report",
    "is_weight": true,
    "feature_project_code": "BA",
    "story_project_code": "BA",
    "statuses_done": ["Done", "Closed", "Resolved"],
    "statuses_in_progress": ["In Progress", "Analysis", "Review"],
    "figma_files": [
      "https://www.figma.com/file/abc123/Product-Design",
      "https://www.figma.com/file/xyz789/UX-Mockups"
    ],
    "formula": "reports/ba_productivity_formula.js",
    "employees": "reports/ba_team.json",
    "ignore_ticket_prefixes": ["[TEMPLATE]", "[EXAMPLE]"]
  }
}
```

**Core Parameters** (from BAProductivityReportParams):
- `feature_project_code` - Project code where features are created (e.g., "BA", "FEATURES")
- `story_project_code` - Project code where stories are created (e.g., "BA", "STORIES")
- `statuses_done` - Array of completion statuses (e.g., ["Done", "Closed"])
- `statuses_in_progress` - Array of work-in-progress statuses (e.g., ["In Progress", "Analysis"])
- `figma_files` - Array of Figma file URLs to track comment activity (optional)

**Common Parameters** (from ProductivityJobParams):
- Same as QAProductivityReport (start_date, report_name, is_weight, formula, employees, etc.)

**Metrics Tracked**:
- Created features count
- Created stories count
- Tasks moved to Done (by responsible BA)
- Items moved to Reopened (First Time Right failures)
- Number of attachments added
- Number of components assigned
- Ticket fields changed
- Figma comments posted (if figma_files configured)
- Vacation days

**Figma Integration**:
When `figma_files` is provided, the report includes comments posted by BAs in Figma design files. Requires `FIGMA_TOKEN` environment variable.

**Output**: HTML report file with BA metrics grouped by employee and time period (weeks).

**Employee File Format** (`reports/ba_team.json`):
```json
{
  "businessAnalysts": [
    {
      "name": "Alice Johnson",
      "jiraName": "alice.johnson",
      "email": "alice.johnson@company.com",
      "figmaEmail": "alice.johnson@company.com"
    },
    {
      "name": "Bob Williams",
      "jiraName": "bob.williams",
      "email": "bob.williams@company.com",
      "figmaEmail": "bob.williams@company.com"
    }
  ]
}
```

**Use Cases**:
1. **Weekly Team Reports** - Track BA team output week by week
2. **Sprint Reviews** - Show BA contributions per sprint
3. **Performance Reviews** - Objective metrics for evaluation
4. **Capacity Planning** - Understand team throughput
5. **Process Improvement** - Identify bottlenecks and First Time Right rates

---

### JSRunner

Run a JavaScript agent directly from the CLI without creating a JSON config file.

**Purpose**: Execute a `.js` file as a `JSRunner` job. Useful for rapid testing, CI scripting, and isolating pre/post actions.

**Shorthand syntax** (no config file needed):
```bash
# Run JS file with no parameters
dmtools run agents/js/myScript.js

# Run with raw JSON parameters
dmtools run agents/js/myScript.js '{"key": "PROJ-123", "mode": "test"}'

# Run with parameters from a file
dmtools run agents/js/myScript.js "$(cat params.json)"
```

When `dmtools run` receives a path ending in `.js`, it automatically constructs a `JSRunner` config in memory — no JSON file is required.

**Equivalent full JSON config** (`agents/jsrunner_example.json`):
```json
{
  "name": "JSRunner",
  "params": {
    "jsPath": "agents/js/myScript.js",
    "jobParams": {"key": "PROJ-123", "mode": "test"}
  }
}
```

**`JSRunner.JSParams` fields**:
| Field | Description |
|-------|-------------|
| `jsPath` | Path to the JS file. Can be a file path, `classpath:` resource, GitHub URL, or inline JS code. |
| `jobParams` | Object passed as `params.jobParams` inside the JS function. |
| `ticket` | Optional ticket object passed as `params.ticket`. |
| `response` | Optional AI response string passed as `params.response`. |

**Accessing `jobParams` inside JS**:
```javascript
function action(params) {
    var key = params.jobParams.key;      // "PROJ-123"
    var mode = params.jobParams.mode;    // "test"
    return { processed: key, mode: mode };
}
```

**Parameter encoding**: The second CLI argument may be:
- Raw JSON: `'{"key":"PROJ-123"}'` — used directly.
- Base64 or URL-encoded JSON — decoded automatically via `EncodingDetector`.
- Omitted or blank — `jobParams` defaults to `{}`.

**Use cases**:
- Rapid testing of a JS agent without creating a config file
- CI/CD pipelines with dynamic parameters via shell variables
- Running pre/post actions in isolation to debug them
- One-off data transformations using MCP tools

---

## 🚀 Quick Start

### List All Jobs
```bash
dmtools --list-jobs
```

### Run a Job
```bash
# Direct execution with parameters
dmtools <JobName> --param1 value1 --param2 value2

# Using configuration file
dmtools run agents/config.json
```

### Get Job Help
```bash
dmtools <JobName> --help
```

---

## 📚 Related Documentation

- [JavaScript Agents](../agents/javascript-agents.md) - Preprocessing/postprocessing
- [Teammate Configs](../agents/teammate-configs.md) - AI teammate configuration
- [Test Generation](../test-generation/xray-manual.md) - Test case generation
- [MCP Tools](../mcp-tools/README.md) - Available MCP tools

---

*For full job list and parameters, run `dmtools --list-jobs`*
