# DMtools Jobs Reference

Complete reference for all 23 available jobs in DMtools. Jobs are specialized workflows that orchestrate MCP tools, AI agents, and data processing.

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
```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "project = PROJ AND type = Story",
    "aiProvider": "gemini",
    "outputType": "creation",
    "outputJiraIssueType": "Test",
    "preprocessJSAction": "agents/js/preprocessXrayTestCases.js"
  }
}
```

**Parameters**:
- `inputJql` - JQL query to find stories
- `aiProvider` - AI provider (gemini, openai, claude, etc.)
- `outputType` - Output format: `creation` (create Jira tickets), `field` (update field), `none` (dry run)
- `outputJiraIssueType` - Jira issue type for created tests (Test, Sub-task, etc.)
- `preprocessJSAction` - Optional JavaScript preprocessing
- `postprocessJSAction` - Optional JavaScript postprocessing

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
dmtools Teammate --inputJql "key = PROJ-123" --aiRole "Solution Architect"
```

**Configuration** (`agents/teammate_config.json`):
```json
{
  "name": "Teammate",
  "params": {
    "inputJql": "project = PROJ AND status = 'To Do'",
    "aiProvider": "gemini",
    "aiRole": "Senior Software Engineer",
    "instructions": "Analyze each ticket and provide implementation suggestions",
    "formattingRules": "Return JSON with {ticketKey, suggestions, estimatedHours}",
    "fewShots": [
      {
        "input": "Create login page",
        "output": "{\"suggestions\": [\"Use React\", \"Add validation\"], \"estimatedHours\": 8}"
      }
    ],
    "outputType": "field",
    "outputFieldName": "AI Analysis"
  }
}
```

**Key Parameters**:
- `aiRole` - Role for the AI (e.g., "Senior Engineer", "QA Lead", "Architect")
- `instructions` - What the AI should do with each ticket
- `formattingRules` - How to format the output
- `fewShots` - Example inputs/outputs for AI training
- `preprocessJSAction` - JavaScript preprocessing
- `postprocessJSAction` - JavaScript postprocessing

**Common Use Cases**:
1. **Code Review** - Analyze pull requests and provide feedback
2. **Architecture Review** - Review solution designs
3. **Requirement Analysis** - Extract requirements from stories
4. **Estimation** - Estimate effort for tickets
5. **Documentation** - Generate technical documentation

**See also**: [Teammate Configuration Guide](../agents/teammate-configs.md)

---

### Expert

Domain expert that answers questions based on context (tickets, documentation, code).

**Purpose**: Ask questions about your project and get AI-powered answers based on actual project context from Jira, Confluence, or code.

**Usage**:
```bash
# Ask question about specific tickets
dmtools Expert --inputJql "key in (PROJ-123, PROJ-456)" --question "What are the main technical challenges?"

# Analyze entire feature
dmtools Expert --inputJql "Epic Link = PROJ-100" --question "What is the overall architecture?"

# Use configuration
dmtools run agents/expert_config.json
```

**Configuration** (`agents/expert_config.json`):
```json
{
  "name": "Expert",
  "params": {
    "inputJql": "project = PROJ AND component = Backend",
    "aiProvider": "gemini",
    "question": "What are the main API endpoints and their purposes?",
    "includeComments": true,
    "includeCode": false
  }
}
```

**Parameters**:
- `inputJql` - JQL to gather context tickets
- `question` - Question to ask
- `aiProvider` - AI provider to use
- `includeComments` - Include Jira comments in context
- `includeCode` - Include source code in context
- `includeConfluence` - Include Confluence pages

**Use Cases**:
1. **Onboarding** - "What does this project do?"
2. **Technical Debt** - "What are the main technical debt items?"
3. **Dependencies** - "What external services does this feature depend on?"
4. **Testing Strategy** - "What should be our testing approach for this feature?"
5. **Architecture Questions** - "How does authentication work?"

**Output**: Returns AI-generated answer based on all provided context.

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
