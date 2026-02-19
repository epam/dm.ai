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
 * Integration test for creating a full-featured TestRail test case with:
 * - Different priorities (1=Low, 2=Medium, 3=High, 4=Critical)
 * - Different types (type_id)
 * - Labels
 * - Formatted tables in preconditions/steps
 * - HTML/Markdown content
 *
 * This test does POST to create, then GET to verify everything was saved correctly.
 */
@EnabledIfEnvironmentVariable(named = "TESTRAIL_BASE_PATH", matches = ".*")
public class TestRailFullFeaturedTest {

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
    public void testCreateFullFeaturedCase_ThenVerifyAllFields() throws IOException {
        long timestamp = System.currentTimeMillis();

        // Step 1: Create test case with all features
        String preconditions = "User is logged in.\n\n" +
                "**Test Data Table:**\n\n" +
                "| Username | Password | Role | Expected |\n" +
                "|----------|----------|------|----------|\n" +
                "| admin | pass123 | Admin | Success |\n" +
                "| user | test456 | User | Success |\n" +
                "| guest | guest789 | Guest | Denied |";

        String steps = "1. Navigate to login page.\n\n" +
                "2. Enter username from table above.\n\n" +
                "3. Enter corresponding password.\n\n" +
                "4. Select role from dropdown.\n\n" +
                "5. Click Login button.\n\n" +
                "6. Verify redirect and permissions.";

        String expected = "User is redirected to appropriate dashboard based on role.\n\n" +
                "Success message is displayed.\n\n" +
                "User permissions match the selected role.";

        String response = client.createCaseDetailed(
                PROJECT_NAME,
                "Full Featured Test - " + timestamp,
                preconditions,
                steps,
                expected,
                "4", // Critical priority
                null, // type_id - use default
                "INTEG-FULL-" + timestamp,
                null // label_ids
        );

        // Step 2: Extract case ID from response
        assertNotNull(response);
        JSONObject responseObj = new JSONObject(response);
        int caseId = responseObj.getInt("id");
        assertTrue(caseId > 0, "Case ID should be positive");

        // Verify immediate response has correct priority
        assertEquals(4, responseObj.getInt("priority_id"), "Response should have priority_id=4 (Critical)");

        // Step 3: GET the case to verify all fields were saved
        TestCase verifyCase = client.getCase(String.valueOf(caseId));
        assertNotNull(verifyCase);

        // Verify basic fields
        assertEquals(caseId, verifyCase.getInt("id"));
        assertEquals("Full Featured Test - " + timestamp, verifyCase.getTicketTitle());

        // Verify priority
        int savedPriority = verifyCase.getInt("priority_id");
        assertEquals(4, savedPriority, "Saved priority should be 4 (Critical)");

        // Verify type
        int typeId = verifyCase.getInt("type_id");
        assertTrue(typeId > 0, "Type ID should be set");

        // Verify refs
        String refs = verifyCase.getString("refs");
        assertTrue(refs.contains("INTEG-FULL-" + timestamp), "Refs should contain our reference");

        // Verify preconditions with table
        String savedPreconditions = verifyCase.getString("custom_preconds");
        assertNotNull(savedPreconditions);
        assertTrue(savedPreconditions.contains("Test Data Table") || savedPreconditions.contains("table"),
                "Preconditions should contain table content");
        assertTrue(savedPreconditions.contains("admin") && savedPreconditions.contains("user"),
                "Table data should be preserved");

        // Verify steps
        String savedSteps = verifyCase.getString("custom_steps");
        assertNotNull(savedSteps);
        assertTrue(savedSteps.contains("Navigate to login page") || savedSteps.contains("login"),
                "Steps should contain login instruction");

        // Verify expected results
        String savedExpected = verifyCase.getString("custom_expected");
        assertNotNull(savedExpected);
        assertTrue(savedExpected.contains("dashboard") || savedExpected.contains("Success"),
                "Expected should contain success criteria");

        // Check labels (might be empty if not supported)
        Object labelsObj = verifyCase.getJSONObject().opt("labels");
        System.out.println("Labels: " + labelsObj);
        // Note: Labels might be empty array if TestRail doesn't support them via API
    }

    @Test
    public void testDifferentPriorities() throws IOException {
        // Test all priority levels: 1=Low, 2=Medium, 3=High, 4=Critical
        int[] priorities = {1, 2, 3, 4};
        String[] priorityNames = {"Low", "Medium", "High", "Critical"};

        for (int i = 0; i < priorities.length; i++) {
            int priority = priorities[i];
            String name = priorityNames[i];

            String response = client.createCase(
                    PROJECT_NAME,
                    "Priority Test - " + name,
                    "Testing priority " + priority,
                    String.valueOf(priority),
                    "PRI-TEST-" + priority
            );

            JSONObject responseObj = new JSONObject(response);
            int caseId = responseObj.getInt("id");

            // Verify priority was saved
            assertEquals(priority, responseObj.getInt("priority_id"),
                    "Priority should be " + priority + " (" + name + ")");

            // GET to double-check
            TestCase verifyCase = client.getCase(String.valueOf(caseId));
            assertEquals(priority, verifyCase.getInt("priority_id"),
                    "Saved priority should match for " + name);

            System.out.println("✓ Priority " + priority + " (" + name + ") verified for case C" + caseId);
        }
    }

    @Test
    public void testFormattedTableInPreconditions() throws IOException {
        // Create case with Markdown table
        String markdownTable = "Test accounts:\n\n" +
                "| Account Type | Username | Password | Access Level |\n" +
                "|--------------|----------|----------|-------------|\n" +
                "| Admin | admin@test.com | Admin123! | Full |\n" +
                "| Manager | manager@test.com | Mgr456! | Limited |\n" +
                "| User | user@test.com | User789! | Read-only |";

        String response = client.createCaseDetailed(
                PROJECT_NAME,
                "Test with Markdown Table",
                markdownTable,
                "1. Select account type from table\n\n2. Login with credentials\n\n3. Verify access level",
                "Access level matches table specification",
                "2",
                null, // type_id - use default
                "TABLE-TEST-" + System.currentTimeMillis(),
                null // label_ids
        );

        JSONObject responseObj = new JSONObject(response);
        int caseId = responseObj.getInt("id");

        // Verify table was saved
        TestCase verifyCase = client.getCase(String.valueOf(caseId));
        String savedPreconditions = verifyCase.getString("custom_preconds");

        assertTrue(savedPreconditions.contains("Account Type") || savedPreconditions.contains("admin@test.com"),
                "Table headers or data should be preserved");
        assertTrue(savedPreconditions.contains("Admin") && savedPreconditions.contains("Manager"),
                "Multiple rows should be preserved");

        System.out.println("✓ Markdown table preserved in case C" + caseId);
        System.out.println("Saved preconditions: " + savedPreconditions.substring(0, Math.min(200, savedPreconditions.length())));
    }

    @Test
    public void testCaseTypes() throws IOException {
        // Get the case to see what type_id is used
        TestCase sampleCase = client.getCase("1");
        int defaultTypeId = sampleCase.getInt("type_id");

        System.out.println("Default type_id from existing case: " + defaultTypeId);

        // Create a case and verify type is set
        String response = client.createCase(
                PROJECT_NAME,
                "Type Test Case",
                "Testing type_id field",
                "2",
                "TYPE-TEST-" + System.currentTimeMillis()
        );

        JSONObject responseObj = new JSONObject(response);
        int typeId = responseObj.getInt("type_id");

        assertTrue(typeId > 0, "Type ID should be positive");
        System.out.println("✓ Type ID " + typeId + " set for new case");
    }

    @Test
    public void testCreateCaseStepsTemplate() throws IOException {
        long timestamp = System.currentTimeMillis();

        // Build steps JSON with text steps and one step containing a Markdown table
        String stepsJson = "[\n" +
                "  {\"content\": \"Open the login page at https://example.com/login\", " +
                "   \"expected\": \"Login form is displayed with username, password fields and Login button\"},\n" +
                "  {\"content\": \"Enter credentials from the table below:\\n" +
                "| Account Type | Username | Password |\\n" +
                "|-------------|----------|----------|\\n" +
                "| Admin | admin@test.com | Admin123! |\\n" +
                "| User | user@test.com | User456! |\", " +
                "   \"expected\": \"Credentials are entered in the respective fields\"},\n" +
                "  {\"content\": \"Click the Login button\", " +
                "   \"expected\": \"User is redirected to dashboard. Role matches the account type used.\"}\n" +
                "]";

        String response = client.createCaseSteps(
                PROJECT_NAME,
                "Steps Template - Login Verification - " + timestamp,
                "User is logged out and on the home page",
                stepsJson,
                "3",         // High priority
                "7",         // Other type
                "STEPS-TEST-" + timestamp,
                "7,8"        // Labels: Login, Manual
        );

        assertNotNull(response);
        JSONObject responseObj = new JSONObject(response);
        int caseId = responseObj.getInt("id");
        assertTrue(caseId > 0, "Case ID should be positive");

        // Verify template
        assertEquals(2, responseObj.getInt("template_id"), "Should use Steps template (template_id=2)");
        assertEquals(3, responseObj.getInt("priority_id"), "Priority should be High");
        assertEquals(7, responseObj.getInt("type_id"), "Type should match");

        // Verify labels
        JSONArray labels = responseObj.optJSONArray("labels");
        assertNotNull(labels);
        assertEquals(2, labels.length(), "Should have 2 labels");

        // GET the case to verify steps were saved
        TestCase verifyCase = client.getCase(String.valueOf(caseId));
        assertNotNull(verifyCase);

        // Verify preconditions
        String savedPreconds = verifyCase.getString("custom_preconds");
        assertNotNull(savedPreconds);
        assertTrue(savedPreconds.contains("logged out"), "Preconditions should be saved");

        // Verify steps_separated
        List<TestCaseFields.TestStep> savedSteps = verifyCase.getTestCaseFields().getCustomStepsSeparated();
        assertNotNull(savedSteps);
        assertEquals(3, savedSteps.size(), "Should have 3 steps");

        // Step 1: plain text
        TestCaseFields.TestStep step1 = savedSteps.get(0);
        assertTrue(step1.getContent().contains("login page"), "Step 1 content");
        assertTrue(step1.getExpected().contains("Login form"), "Step 1 expected");

        // Step 2: should contain table (converted to HTML)
        TestCaseFields.TestStep step2 = savedSteps.get(1);
        String step2Content = step2.getContent();
        assertTrue(step2Content.contains("table") || step2Content.contains("admin@test.com"),
                "Step 2 should contain table or table data: " + step2Content.substring(0, Math.min(300, step2Content.length())));

        // Step 3: plain text
        TestCaseFields.TestStep step3 = savedSteps.get(2);
        assertTrue(step3.getContent().contains("Login button"), "Step 3 content");

        System.out.println("✓ Steps template case created: C" + caseId);
        System.out.println("✓ template_id=2, priority=3, labels=" + labels.length());
        System.out.println("✓ Steps count: " + savedSteps.size());
        System.out.println("✓ Step 2 (table) content: " + step2Content.substring(0, Math.min(200, step2Content.length())));
        System.out.println("View at: " + TestRailClient.BASE_PATH + "/index.php?/cases/view/" + caseId);
    }
}
