# Xray Manual Test Generation Guide

## 🎯 Overview

DMtools integrates with Xray for Jira to automatically generate comprehensive manual test cases from user stories, including test steps, preconditions, and test sets.

## 🔧 Xray Configuration

### Prerequisites

1. **Xray Plugin**: Installed in your Jira instance
2. **Project Configuration**: Xray test issue types enabled
3. **Permissions**: Create/edit Test, Precondition, Test Set issue types

### Environment Setup

```bash
# dmtools.env
JIRA_BASE_PATH=https://company.atlassian.net
JIRA_LOGIN_PASS_TOKEN=base64_encoded_credentials
DEFAULT_TRACKER=jira_xray

# Optional: Xray Cloud API (for advanced features)
XRAY_BASE_PATH=https://xray.cloud.getxray.app/api/v2
XRAY_CLIENT_ID=E5E7EXAMPLE1234567890
XRAY_CLIENT_SECRET=8a6bEXAMPLE1234567890abcdef

# Optional: Xray enrichment control
# Default is true to preserve existing client behavior.
XRAY_ENRICHMENT_ENABLED_BY_DEFAULT=true

# Custom fields (find these in Jira admin)
XRAY_TEST_TYPE_FIELD=customfield_10030
XRAY_TEST_STEPS_FIELD=customfield_10031
XRAY_PRECONDITIONS_FIELD=customfield_10032
```

### Default Behavior

- Set `DEFAULT_TRACKER=jira_xray` to make `TestCasesGenerator` create Xray tests through `XrayClient`.
- Set `"outputType": "creation"` in the job config to create issues automatically instead of only posting/generated output.
- DMtools now enriches Xray search results by default, including the searches used to load existing/related test cases for deduplication and comparison.
- Existing client configs do **not** need to add extra fields or change logic for this behavior.
- If a workspace needs the faster non-enriched search path, set `XRAY_ENRICHMENT_ENABLED_BY_DEFAULT=false`.

## 📋 Test Case Structure

### Xray Test Format

```json
{
  "fields": {
    "project": { "key": "PROJ" },
    "issuetype": { "name": "Test" },
    "summary": "Verify user can login with valid credentials",
    "description": "Test case for validating login functionality",
    "priority": { "name": "High" },
    "labels": ["automation", "login", "smoke"],
    "customfield_10030": "Manual",  // Test Type
    "customfield_10031": [           // Test Steps
      {
        "index": 1,
        "step": "Navigate to login page",
        "data": "URL: https://app.example.com/login",
        "result": "Login page is displayed with username and password fields"
      },
      {
        "index": 2,
        "step": "Enter valid username",
        "data": "Username: testuser@example.com",
        "result": "Username is entered in the field"
      },
      {
        "index": 3,
        "step": "Enter valid password",
        "data": "Password: ValidPass123!",
        "result": "Password is entered (masked)"
      },
      {
        "index": 4,
        "step": "Click Login button",
        "data": "",
        "result": "User is successfully logged in and redirected to dashboard"
      }
    ],
    "customfield_10032": ["PROJ-PC-1", "PROJ-PC-2"]  // Precondition references
  }
}
```

## 🚀 Generating Test Cases

### Basic Test Generation

```bash
# Generate tests for a specific story
dmtools TestCasesGenerator --inputJql "key = PROJ-123"

# Generate for all stories in sprint
dmtools TestCasesGenerator --inputJql "sprint in openSprints() AND type = Story"

# Using configuration file
dmtools run agents/xray_test_cases_generator.json
```

### Configuration File Example

```json
{
  "name": "TestCasesGenerator",
  "params": {
    "inputJql": "project = PROJ AND type = Story AND 'Test Cases' is EMPTY",
    "aiProvider": "gemini",
    "aiModel": "gemini-2.0-flash-exp",
    "aiRole": "You are an expert QA engineer specializing in Xray test case design",
    "instructions": "Generate detailed manual test cases with clear steps. Each step must have: action, test data (if applicable), and expected result.",
    "formattingRules": "Return JSON array. Each test case must include: priority (Critical/High/Medium/Low), summary, description, testSteps array with (index, step, data, result)",
    "outputType": "creation",
    "testCaseIssueType": "Test",
    "outputJiraProject": "PROJ",
    "outputJiraIssueType": "Test",
    "customFields": {
      "testType": "Manual",
      "testRepository": "/Login/Authentication"
    },
    "preprocessJSAction": "agents/js/enrichStoryData.js",
    "postprocessJSAction": "agents/js/createXrayTests.js"
  }
}
```

### Existing Test Case Search

When `existingTestCasesJql` or related test case lookup is used, DMtools loads Xray-enriched ticket data by default. This keeps deduplication and related-test analysis working without requiring callers to pass a special sentinel field or change existing job definitions.

## 🎨 Test Step Formatting

### Standard Test Step Structure

```javascript
// Each test step must have:
{
  "index": 1,                    // Step number (sequential)
  "step": "Action to perform",   // What the tester does
  "data": "Input data if any",   // Test data, URLs, values
  "result": "Expected outcome"   // What should happen
}
```

### Best Practices for Test Steps

```javascript
// ✅ Good Test Step
{
  "index": 1,
  "step": "Click on 'Create Account' button",
  "data": "",
  "result": "Registration form is displayed with all required fields"
}

// ✅ Good Test Step with Data
{
  "index": 2,
  "step": "Fill in the registration form",
  "data": "First Name: John, Last Name: Doe, Email: john.doe@test.com, Password: Test@123",
  "result": "All fields are populated with valid data, password is masked"
}

// ❌ Bad Test Step (too vague)
{
  "index": 1,
  "step": "Test login",
  "data": "Use test data",
  "result": "It works"
}
```

## 🔄 Preconditions Management

### Creating Preconditions

```javascript
// Create standalone precondition
const precondition = jira_xray_create_precondition(
    "PROJ",
    "User account exists",
    "A valid user account with email 'test@example.com' and password 'Test123!' exists in the system"
);

// Reference in test case
const testCase = {
  "fields": {
    "customfield_10032": [precondition.key]  // Link precondition
  }
};
```

### Automatic Precondition Handling

```javascript
// agents/js/preprocessXrayTestCases.js
function action(params) {
    const testCases = params.testCases;
    const projectKey = params.projectKey;
    const preconditionMap = {};

    for (const testCase of testCases) {
        // Handle temporary precondition IDs
        if (testCase.preconditions) {
            const realPreconditions = [];

            for (const pc of testCase.preconditions) {
                if (pc.startsWith("@temp-")) {
                    // Check if already created
                    if (!preconditionMap[pc]) {
                        // Create new precondition
                        const newPc = jira_xray_create_precondition(
                            projectKey,
                            pc.replace("@temp-", ""),
                            testCase.preconditionDescriptions[pc]
                        );
                        preconditionMap[pc] = newPc.key;
                    }
                    realPreconditions.push(preconditionMap[pc]);
                } else {
                    // Use existing precondition key
                    realPreconditions.push(pc);
                }
            }

            testCase.fields.customfield_10032 = realPreconditions;
        }
    }

    return testCases;
}
```

## 📊 Priority Configuration

### Priority Mapping

```json
{
  "priorityMapping": {
    "Critical": {
      "jiraPriority": "Highest",
      "description": "Blocks critical functionality, no workaround",
      "examples": ["Login", "Payment", "Data loss scenarios"]
    },
    "High": {
      "jiraPriority": "High",
      "description": "Major functionality affected, difficult workaround",
      "examples": ["Core features", "Security", "Performance"]
    },
    "Medium": {
      "jiraPriority": "Medium",
      "description": "Minor functionality affected, workaround exists",
      "examples": ["UI issues", "Non-critical features"]
    },
    "Low": {
      "jiraPriority": "Low",
      "description": "Cosmetic issues, nice-to-have features",
      "examples": ["Tooltips", "Formatting", "Edge cases"]
    }
  }
}
```

### AI Instructions for Priority

```javascript
const aiInstructions = `
When generating test cases, assign priority based on:
- Critical: Core functionality, security, data integrity
- High: Major features, user workflows, integration points
- Medium: Secondary features, error handling, validation
- Low: UI polish, edge cases, nice-to-have scenarios

Distribute priorities realistically:
- ~10% Critical
- ~30% High
- ~40% Medium
- ~20% Low
`;
```

## 🏷️ Label Management

### Automatic Labeling

```json
{
  "labelingRules": {
    "automatic": ["auto-generated", "xray"],
    "byFeature": {
      "login": ["authentication", "security"],
      "payment": ["financial", "critical"],
      "api": ["integration", "backend"]
    },
    "byPriority": {
      "Critical": ["smoke-test", "regression"],
      "High": ["regression"]
    }
  }
}
```

### Label-based Test Organization

```javascript
// Find all smoke tests
const smokeTests = jira_search_by_jql(
    "project = PROJ AND type = Test AND labels in ('smoke-test')"
);

// Create test set for smoke tests
const testSet = jira_xray_create_test_set(
    "PROJ",
    "Smoke Test Suite",
    "Critical path test cases for quick validation"
);

// Add tests to set
for (const test of smokeTests) {
    jira_xray_add_test_to_set(testSet.key, test.key);
}
```

## 🔍 Deduplication Strategies

### Preventing Duplicate Test Cases

```javascript
function checkDuplicates(newTestCase, existingTests) {
    // Check by exact title match
    const exactMatch = existingTests.find(
        test => test.fields.summary.toLowerCase() === newTestCase.summary.toLowerCase()
    );
    if (exactMatch) return { isDuplicate: true, match: exactMatch };

    // Check by similarity (80% threshold)
    for (const existing of existingTests) {
        const similarity = calculateSimilarity(
            newTestCase.summary,
            existing.fields.summary
        );
        if (similarity > 0.8) {
            return {
                isDuplicate: true,
                match: existing,
                similarity: similarity
            };
        }
    }

    return { isDuplicate: false };
}

function calculateSimilarity(str1, str2) {
    // Implement string similarity algorithm
    // (Levenshtein distance, Jaccard similarity, etc.)
    return 0.0; // Placeholder
}
```

### Configuration for Deduplication

```json
{
  "deduplication": {
    "enabled": true,
    "checkExisting": true,
    "similarityThreshold": 0.8,
    "matchFields": ["summary", "description"],
    "action": "skip",  // "skip", "update", "version"
    "logDuplicates": true
  }
}
```

## 💡 Advanced Examples

### Example 1: Complete Test Generation Workflow

```javascript
// Full workflow with all features
function generateXrayTests(storyKey) {
    // 1. Get story details
    const story = jira_get_ticket(storyKey);

    // 2. Get existing tests to avoid duplicates
    const existingTests = jira_search_by_jql(
        `project = ${story.fields.project.key} AND type = Test AND "Tests" = "${storyKey}"`
    );

    // 3. Generate test cases with AI
    const prompt = `
        Story: ${story.fields.summary}
        Description: ${story.fields.description}
        Acceptance Criteria: ${story.fields.customfield_10100}

        Generate comprehensive manual test cases covering:
        1. Happy path scenarios
        2. Error handling
        3. Boundary conditions
        4. Security aspects
        5. Performance considerations

        Format as JSON with Xray structure.
    `;

    const aiResponse = gemini_ai_chat(prompt);
    const generatedTests = JSON.parse(aiResponse);

    // 4. Process each test case
    const createdTests = [];
    for (const testData of generatedTests) {
        // Check for duplicates
        const duplicate = checkDuplicates(testData, existingTests);
        if (duplicate.isDuplicate) {
            console.log(`Skipping duplicate: ${testData.summary}`);
            continue;
        }

        // Create test in Xray
        const test = jira_xray_create_test(
            story.fields.project.key,
            testData.summary,
            testData.description
        );

        // Add test steps
        for (const step of testData.steps) {
            jira_xray_add_test_step(
                test.key,
                step.step,
                step.data,
                step.result
            );
        }

        // Add preconditions
        if (testData.preconditions) {
            jira_xray_add_preconditions_to_test(
                test.key,
                testData.preconditions
            );
        }

        // Link to story
        jira_link_issues(test.key, storyKey, "Tests");

        // Add labels
        jira_update_labels(test.key, testData.labels.join(","));

        createdTests.push(test);
    }

    // 5. Create test set
    if (createdTests.length > 0) {
        const testSet = jira_xray_create_test_set(
            story.fields.project.key,
            `Test Set for ${story.fields.summary}`,
            `Automated test generation for story ${storyKey}`
        );

        // Add all tests to set
        for (const test of createdTests) {
            jira_xray_add_test_to_set(testSet.key, test.key);
        }
    }

    return {
        story: storyKey,
        testsCreated: createdTests.length,
        testSet: testSet?.key
    };
}
```

### Example 2: Test Case Templates

```json
{
  "testTemplates": {
    "login": {
      "preconditions": ["@user-exists"],
      "steps": [
        {
          "template": "Navigate to {url}",
          "result": "Page loads successfully"
        },
        {
          "template": "Enter {username} in username field",
          "result": "Username is accepted"
        },
        {
          "template": "Enter {password} in password field",
          "result": "Password is masked"
        },
        {
          "template": "Click {button}",
          "result": "{expectedOutcome}"
        }
      ]
    },
    "crud": {
      "operations": ["Create", "Read", "Update", "Delete"],
      "generateFor": "each",
      "priority": "High"
    }
  }
}
```

## 🐛 Troubleshooting

### Custom Fields Not Found

```bash
# List all fields to find Xray fields
dmtools jira_get_fields | grep -i xray

# Common Xray field names:
# - Test Type: customfield_10030
# - Test Steps: customfield_10031
# - Preconditions: customfield_10032
```

### Test Steps Not Saving

```javascript
// Ensure correct JSON structure
const testSteps = [
  {
    "index": 1,  // Must be sequential
    "step": "Action",
    "data": "",  // Can be empty string, not null
    "result": "Expected"
  }
];

// Validate before sending
if (!Array.isArray(testSteps)) {
    throw new Error("Test steps must be an array");
}
```

### Precondition Linking Issues

```bash
# Verify precondition exists
dmtools jira_get_ticket PROJ-PC-1

# Check issue type
# Must be "Precondition" type, not "Test" or "Story"
```

## 🔗 Resources

- [Xray Documentation](https://docs.getxray.app/)
- [Xray REST API](https://docs.getxray.app/display/XRAY/REST+API)
- [Test Management Best Practices](https://docs.getxray.app/display/XRAY/Testing+Best+Practices)

---

*Next: [Cucumber/Gherkin Tests](cucumber-gherkin.md) | [Preconditions Guide](preconditions.md)*
