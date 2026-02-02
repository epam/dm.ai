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
    "confluencePages": ["https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665495/Template+Test+Case"],
    "relatedTestCasesRules": "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/55443457/Template+Test+Case+Related+Rules",
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
        "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story",
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
      "formattingRules": "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown",
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
Teammate can execute CLI commands and include their output in the context:
- Commands run from project root directory
- Input context (ticket + params) saved to temp file
- Output collected and included in AI context or used as final response
- Clean up happens automatically

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
