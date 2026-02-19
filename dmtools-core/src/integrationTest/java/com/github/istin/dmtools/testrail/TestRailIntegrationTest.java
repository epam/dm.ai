package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.testrail.model.TestCase;
import com.github.istin.dmtools.testrail.model.TestCaseFields;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TestRail client.
 *
 * Requires environment variables:
 * - TESTRAIL_BASE_PATH
 * - TESTRAIL_USERNAME
 * - TESTRAIL_API_KEY
 * - TESTRAIL_PROJECT (optional, defaults to "DMTools Tests")
 */
@EnabledIfEnvironmentVariable(named = "TESTRAIL_BASE_PATH", matches = ".*")
public class TestRailIntegrationTest {

    private static TestRailClient client;
    private static final String PROJECT_NAME = System.getenv().getOrDefault("TESTRAIL_PROJECT", "DMTools Tests");

    @BeforeAll
    public static void setUp() throws IOException {
        String basePath = System.getenv("TESTRAIL_BASE_PATH");
        String username = System.getenv("TESTRAIL_USERNAME");
        String apiKey = System.getenv("TESTRAIL_API_KEY");

        assertNotNull(basePath, "TESTRAIL_BASE_PATH must be set");
        assertNotNull(username, "TESTRAIL_USERNAME must be set");
        assertNotNull(apiKey, "TESTRAIL_API_KEY must be set");

        client = new TestRailClient(basePath, username, apiKey);
    }

    @Test
    public void testGetProjects() throws IOException {
        String response = client.getProjects();
        assertNotNull(response);
        assertTrue(response.contains("\"projects\""), "Response should contain projects array");
    }

    @Test
    public void testGetAllCases() throws Exception {
        List<TestCase> cases = client.getAllCases(PROJECT_NAME);
        assertNotNull(cases);
        assertTrue(cases.size() > 0, "Should have at least one test case");

        // Verify first case has required fields
        TestCase firstCase = cases.get(0);
        assertNotNull(firstCase.getKey());
        assertNotNull(firstCase.getTicketTitle());
    }

    @Test
    public void testGetCaseWithHTMLFormatting() throws IOException {
        // Test case 43 has HTML formatting in preconditions, steps, expected
        TestCase testCase = client.getCase("43");

        assertNotNull(testCase);
        assertEquals(43, testCase.getInt("id"));
        assertEquals("Formatted Test Case", testCase.getTicketTitle());

        // Check HTML content in custom fields
        String preconds = testCase.getString("custom_preconds");
        assertNotNull(preconds);
        assertTrue(preconds.contains("<table"), "Preconditions should contain HTML table");
        assertTrue(preconds.contains("<tbody>"), "Should have table body");
        assertTrue(preconds.contains("header"), "Should have table headers");

        String steps = testCase.getString("custom_steps");
        assertNotNull(steps);
        assertTrue(steps.contains("<ol>") || steps.contains("<p>"), "Steps should contain HTML");
        assertTrue(steps.contains("%NewUsername%"), "Should preserve variable placeholders");

        String expected = testCase.getString("custom_expected");
        assertNotNull(expected);
        assertTrue(expected.contains("<p>"), "Expected should contain HTML paragraph");
    }

    @Test
    public void testGetCaseWithMarkdownFormatting() throws IOException {
        // Test case 1 has Markdown formatting
        TestCase testCase = client.getCase("1");

        assertNotNull(testCase);
        assertEquals(1, testCase.getInt("id"));

        // Check Markdown content
        String preconds = testCase.getString("custom_preconds");
        assertNotNull(preconds);
        assertTrue(preconds.contains("# This is a H1"), "Should have Markdown H1");
        assertTrue(preconds.contains("|||"), "Should have Markdown table syntax");
        assertTrue(preconds.contains("* "), "Should have Markdown bullet points");
    }

    @Test
    public void testGetCaseWithLabels() throws IOException {
        // Test case 1 has labels
        TestCase testCase = client.getCase("1");

        assertNotNull(testCase);

        // Check labels array using getJSONObject()
        Object labelsObj = testCase.getJSONObject().opt("labels");
        assertNotNull(labelsObj);
        assertTrue(labelsObj.toString().contains("Login") || labelsObj.toString().contains("Manual"),
                "Should have Login or Manual label");
    }

    @Test
    public void testCreateCaseDetailed() throws IOException {
        // Create a test case with all fields
        String response = client.createCaseDetailed(
                PROJECT_NAME,
                "Integration Test Case - " + System.currentTimeMillis(),
                "User must be logged in",
                "1. Open application\n\n2. Navigate to settings\n\n3. Verify page loaded",
                "Settings page is displayed",
                "2", // Medium priority
                null, // type_id - use default
                "INTEG-TEST-" + System.currentTimeMillis(),
                null // label_ids
        );

        assertNotNull(response);
        assertTrue(response.contains("\"id\""), "Response should contain test case ID");
        assertTrue(response.contains("\"title\""), "Response should contain title");

        // Verify created case has correct fields
        assertTrue(response.contains("custom_preconds"), "Should have preconditions");
        assertTrue(response.contains("custom_steps"), "Should have steps");
        assertTrue(response.contains("custom_expected"), "Should have expected results");
    }

    @Test
    public void testCreateCaseSteps() throws IOException {
        long ts = System.currentTimeMillis();
        String stepsJson = "[" +
                "{\"content\":\"Open the application\",\"expected\":\"App loads successfully\"}," +
                "{\"content\":\"Navigate to settings page\",\"expected\":\"Settings page is displayed with all options\"}," +
                "{\"content\":\"Update username to %NewName%\",\"expected\":\"Username updated and confirmation message shown\"}" +
                "]";

        String response = client.createCaseSteps(
                PROJECT_NAME,
                "Steps Template - Integration Test - " + ts,
                "User is logged in",
                stepsJson,
                "3",    // High priority
                null,   // type_id - use default
                "STEPS-INTEG-" + ts,
                null    // label_ids
        );

        assertNotNull(response);
        JSONObject responseObj = new JSONObject(response);
        int caseId = responseObj.getInt("id");
        assertTrue(caseId > 0, "Case ID should be positive");
        assertEquals(2, responseObj.getInt("template_id"), "Should use Steps template");
        assertEquals(3, responseObj.getInt("priority_id"), "Priority should be High");

        // GET and verify steps_separated
        TestCase verifyCase = client.getCase(String.valueOf(caseId));
        List<TestCaseFields.TestStep> steps = verifyCase.getTestCaseFields().getCustomStepsSeparated();
        assertNotNull(steps);
        assertEquals(3, steps.size(), "Should have 3 steps");
        assertTrue(steps.get(0).getContent().contains("Open"), "Step 1 content");
    }

    @Test
    public void testGetCasesByRefs() throws Exception {
        // Create a case with specific ref
        String uniqueRef = "REF-INTEG-" + System.currentTimeMillis();
        client.createCase(
                PROJECT_NAME,
                "Test for refs search",
                "Testing refs functionality",
                "2",
                uniqueRef
        );

        // Search by refs
        List<TestCase> cases = client.getCasesByRefs(uniqueRef, PROJECT_NAME);
        assertNotNull(cases);
        assertEquals(1, cases.size(), "Should find exactly one case with this ref");

        TestCase foundCase = cases.get(0);
        String refs = foundCase.getString("refs");
        assertTrue(refs.contains(uniqueRef), "Case should have the search ref");
    }

    @Test
    public void testLinkToRequirement() throws IOException {
        // Create a test case
        String response = client.createCase(
                PROJECT_NAME,
                "Test for linking - " + System.currentTimeMillis(),
                "Test description",
                "2",
                null
        );

        // Extract case ID from response
        assertTrue(response.contains("\"id\""));
        String caseId = response.split("\"id\":")[1].split(",")[0].trim();

        // Link to requirement
        String linkResponse = client.linkToRequirement(caseId, "LINK-REQ-123");
        assertNotNull(linkResponse);
        assertTrue(linkResponse.contains("LINK-REQ-123"), "Response should contain the linked requirement");
    }

    @Test
    public void testUpdateCase() throws IOException {
        // Create a test case
        String createResponse = client.createCase(
                PROJECT_NAME,
                "Case for update test",
                "Original description",
                "1",
                "UPDATE-001"
        );

        String caseId = createResponse.split("\"id\":")[1].split(",")[0].trim();

        // Update the case
        String updateResponse = client.updateCase(
                caseId,
                "Updated Title",
                "3", // Change priority to High
                "UPDATE-001,UPDATE-002" // Add second ref
        );

        assertNotNull(updateResponse);
        assertTrue(updateResponse.contains("Updated Title"), "Should have updated title");
        assertTrue(updateResponse.contains("\"priority_id\":3"), "Should have updated priority");
        assertTrue(updateResponse.contains("UPDATE-002"), "Should have new ref");
    }

    @Test
    public void testCaseFieldsAndPriorities() throws IOException {
        TestCase testCase = client.getCase("1");

        // Test priority mapping
        Integer priorityId = testCase.getInt("priority_id");
        assertNotNull(priorityId);
        assertTrue(priorityId >= 1 && priorityId <= 4, "Priority should be 1-4");

        // Test refs field
        String refs = testCase.getString("refs");
        // refs can be null or contain comma-separated values

        // Test created_on timestamp using getJSONObject()
        Long createdOn = testCase.getJSONObject().optLong("created_on");
        assertNotNull(createdOn);
        assertTrue(createdOn > 0, "Created timestamp should be positive");
    }
}
