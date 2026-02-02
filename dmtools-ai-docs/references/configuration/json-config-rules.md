# JSON Configuration Rules for DMtools Jobs

## ⚠️ CRITICAL: Understanding the "name" Field

**The `"name"` field in DMtools job configuration is NOT a user-defined name or description.**

```
"name" = Java Job Class Name (Technical Identifier)
```

### What is the "name" field?

The `"name"` field is a **technical identifier** that tells DMtools **which Java class to instantiate**. It must exactly match the compiled Java class name in the DMtools codebase.

```java
// DMtools code (JobRunner.java):
if (jobName.equals("TestCasesGenerator")) {
    return new TestCasesGenerator();  // Java class instantiation
}
```

### What the "name" field is NOT:

- ❌ NOT a display name or title
- ❌ NOT a description of what the job does
- ❌ NOT something you can customize
- ❌ NOT user-defined or configurable

### Analogy:

```
"name": "TestCasesGenerator"  ≈  import com.github.istin.dmtools.qa.TestCasesGenerator;
```

Just like you cannot change `import` statements in code, you cannot change the `"name"` field in configuration.

### Example of Correct vs Incorrect:

```json
// ✅ CORRECT - Uses exact Java class name
{
  "name": "TestCasesGenerator",
  "params": { ... }
}

// ❌ WRONG - Custom name will cause error
{
  "name": "My Custom Test Generator",
  "params": { ... }
}
// Error: Unknown job: My Custom Test Generator
```

---

## Critical Rules

### 1. **Job Name Field is Immutable**

The `"name"` field in JSON configuration **MUST** exactly match the Java Job class name. This is not a user-configurable parameter.

❌ **WRONG** - Never do this:
```json
{
  "name": "My Custom Test Generator",
  "params": { ... }
}
```

✅ **CORRECT** - Use exact Java class name:
```json
{
  "name": "TestCasesGenerator",
  "params": { ... }
}
```

### Valid Job Names

| Job Name (use exactly as shown) | Purpose |
|----------------------------------|---------|
| `TestCasesGenerator` | Generate test cases from stories |
| `Teammate` | AI teammate for ticket analysis |
| `Expert` | Domain expert Q&A |
| `CodeGenerator` | Generate code from stories |
| `UnitTestsGenerator` | Generate unit tests |
| `DocumentationGenerator` | Generate documentation |
| `DiagramsCreator` | Create Mermaid diagrams |
| `SolutionArchitectureCreator` | Create architecture docs |
| `InstructionsGenerator` | Generate implementation instructions |
| `JEstimator` | Estimate story points |
| `RequirementsCollector` | Collect requirements |
| `UserStoryGenerator` | Generate user stories |
| `JSRunner` | Run JavaScript agents |

**Important**: Run `dmtools --list-jobs` to see all available job names.

## Configuration Structure

### Basic Structure

```json
{
  "name": "JobName",
  "params": {
    // Job-specific parameters
  }
}
```

### Required Fields

1. **name** (string, required) - Exact Java Job class name
2. **params** (object, required) - Job parameters

### Common Parameters

All jobs that extend `TrackerParams` support these common parameters:

```json
{
  "name": "JobName",
  "params": {
    "inputJql": "project = PROJ AND type = Story",
    "initiator": "user@company.com",
    "targetProject": "PROJ",
    "outputType": "comment",
    "fieldName": "Custom Field",
    "operationType": "Append",
    "preJSAction": "agents/js/preprocess.js",
    "postJSAction": "agents/js/postprocess.js",
    "attachResponseAsFile": false,
    "ticketContextDepth": 1,
    "chunkProcessingTimeoutInMinutes": 60
  }
}
```

## Configuration Validation

### How DMtools Resolves Job Name

```java
// JobRunner.java
public static Job<?, ?> createJobInstance(String jobName) {
    // jobName must EXACTLY match Job class name
    if (jobName.equals("TestCasesGenerator")) {
        return new TestCasesGenerator();
    } else if (jobName.equals("Teammate")) {
        return new Teammate();
    }
    // ...
}
```

If the name doesn't match exactly, you'll get:
```
Error: Unknown job: My Custom Test Generator
```

### Case Sensitivity

Job names are **case-sensitive**:

- ✅ `TestCasesGenerator` - Correct
- ❌ `testcasesgenerator` - Wrong
- ❌ `test-cases-generator` - Wrong
- ❌ `TestCasesGeneratorJob` - Wrong

## Best Practices

### 1. Use Real Configuration Examples

Always reference actual configuration files from the `agents/` directory:

```bash
# Copy existing configuration
cp agents/xray_test_cases_generator.json agents/my_test_generator.json

# Edit only the params, never change "name"
```

### 2. Configuration Inheritance

Jobs inherit parameters from parent classes:

```
TestCasesGeneratorParams
  extends Params
    extends TrackerParams
```

This means TestCasesGenerator supports:
- All TestCasesGeneratorParams fields
- All Params fields (isCodeAsSource, confluencePages, etc.)
- All TrackerParams fields (inputJql, outputType, etc.)

### 3. Validate Configuration

```bash
# Test configuration before committing
dmtools run agents/my_config.json
```

### 4. Use JSON Schema Validation (Optional)

For IDE support, you can reference JSON schemas:

```json
{
  "$schema": "path/to/job-schema.json",
  "name": "TestCasesGenerator",
  "params": {
    // IDE will provide autocomplete
  }
}
```

## Common Mistakes

### ❌ Mistake 1: Changing Job Name
```json
{
  "name": "MyTestGenerator",  // WRONG - not a valid Job class
  "params": { ... }
}
```

### ❌ Mistake 2: Missing Required Parameters
```json
{
  "name": "TestCasesGenerator",
  "params": {
    // Missing inputJql - required by TrackerParams
  }
}
```

### ❌ Mistake 3: Incorrect Parameter Names
```json
{
  "name": "TestCasesGenerator",
  "params": {
    "jqlQuery": "...",  // WRONG - should be "inputJql"
  }
}
```

### ❌ Mistake 4: Wrong Parameter Type
```json
{
  "name": "TestCasesGenerator",
  "params": {
    "isFindRelated": "true"  // WRONG - should be boolean, not string
  }
}
```

## Debugging Configuration Issues

### Check Job Name

```bash
# List all available jobs
dmtools --list-jobs

# Verify exact spelling
dmtools --list-jobs | grep TestCases
```

### Validate JSON Syntax

```bash
# Use jq to validate JSON
cat agents/my_config.json | jq .

# Check for syntax errors
dmtools run agents/my_config.json --validate
```

### Enable Debug Logging

```bash
# Run with debug output
dmtools --debug run agents/my_config.json
```

## Real Configuration Examples

### TestCasesGenerator (from agents/xray_test_cases_generator.json)

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "key in (TP-1309)",
    "testCasesPriorities": "Highest, High, Medium, Lowest, Low",
    "outputType": "creation",
    "existingTestCasesJql": "project = TP and issueType in ('Test', 'Precondition')",
    "testCasesCustomFields": ["xrayTestSteps", "xrayPreconditions"],
    "isFindRelated": true,
    "isConvertToJiraMarkdown": false,
    "testCaseIssueType": "Test",
    "preprocessJSAction": "agents/js/preprocessXrayTestCases.js"
  }
}
```

### Teammate (from agents/story_description.json)

```json
{
  "name": "Teammate",
  "params": {
    "agentParams": {
      "aiRole": "Experienced Business Analyst",
      "instructions": [
        "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story",
        "./agents/instructions/common/response_output.md"
      ],
      "formattingRules": "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown"
    },
    "outputType": "field",
    "fieldName": "Description",
    "operationType": "Replace",
    "inputJql": "key = DMC-532",
    "preJSAction": "agents/js/checkWipLabel.js",
    "postJSAction": "agents/js/assignForReview.js"
  }
}
```

## Summary

- ✅ **DO**: Use exact Job class names for `"name"` field
- ✅ **DO**: Reference real configuration files from `agents/` directory
- ✅ **DO**: Validate JSON syntax before running
- ✅ **DO**: Test configuration with `dmtools run`
- ❌ **DON'T**: Change or customize the `"name"` field
- ❌ **DON'T**: Invent parameter names - use documented ones
- ❌ **DON'T**: Mix string and boolean types

**Remember**: The `"name"` field is a technical identifier used by DMtools to instantiate the correct Job class. It is not a display name or description.
