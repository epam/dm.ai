package com.github.istin.dmtools.testrail;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.testrail.model.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestRailClientTest {

    private String basePath = "https://example.testrail.com";
    private String username = "test@example.com";
    private String apiKey = "test_api_key";
    private TestRailClient client;

    @Before
    public void setUp() throws IOException {
        // Note: This creates a real client instance, but without actual HTTP calls
        // All HTTP calls would need to be mocked for integration testing
        // For unit testing, we test the logic without making real API calls
    }

    @Test
    public void testCreateTicket() {
        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        testCaseJson.put("title", "Test case title");

        TestCase testCase = new TestCase(basePath, testCaseJson);

        assertNotNull(testCase);
        assertEquals("C123", testCase.getKey());
    }

    @Test
    public void testResolveFieldName() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        // Test standard field name resolution
        assertEquals("title", client.resolveFieldName("C123", "title"));
        assertEquals("title", client.resolveFieldName("C123", "summary"));
        assertEquals("priority_id", client.resolveFieldName("C123", "priority"));
        assertEquals("type_id", client.resolveFieldName("C123", "type"));
        assertEquals("template_id", client.resolveFieldName("C123", "template"));
        assertEquals("milestone_id", client.resolveFieldName("C123", "milestone"));
        assertEquals("refs", client.resolveFieldName("C123", "refs"));
        assertEquals("refs", client.resolveFieldName("C123", "references"));
        assertEquals("estimate", client.resolveFieldName("C123", "estimate"));

        // Test description/custom field resolution
        assertEquals("custom_preconds", client.resolveFieldName("C123", "description"));
        assertEquals("custom_preconds", client.resolveFieldName("C123", "preconditions"));
        assertEquals("custom_steps", client.resolveFieldName("C123", "steps"));
        assertEquals("custom_expected", client.resolveFieldName("C123", "expected"));
        assertEquals("custom_expected", client.resolveFieldName("C123", "expected_result"));

        // Test custom field prefix
        assertEquals("custom_field", client.resolveFieldName("C123", "custom_field"));
        assertEquals("custom_automation_type", client.resolveFieldName("C123", "automation_type"));
    }

    @Test
    public void testGetDefaultQueryFields() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        String[] fields = client.getDefaultQueryFields();
        assertNotNull(fields);
        assertTrue(fields.length > 0);

        // Verify key fields are present
        boolean hasId = false;
        boolean hasTitle = false;
        boolean hasPriority = false;

        for (String field : fields) {
            if ("id".equals(field)) hasId = true;
            if ("title".equals(field)) hasTitle = true;
            if ("priority_id".equals(field)) hasPriority = true;
        }

        assertTrue(hasId);
        assertTrue(hasTitle);
        assertTrue(hasPriority);
    }

    @Test
    public void testGetExtendedQueryFields() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        String[] fields = client.getExtendedQueryFields();
        assertNotNull(fields);
        assertTrue(fields.length >= client.getDefaultQueryFields().length);

        // Verify extended fields include custom fields
        boolean hasCustomPreconds = false;
        boolean hasCustomSteps = false;

        for (String field : fields) {
            if ("custom_preconds".equals(field)) hasCustomPreconds = true;
            if ("custom_steps".equals(field)) hasCustomSteps = true;
        }

        assertTrue(hasCustomPreconds);
        assertTrue(hasCustomSteps);
    }

    @Test
    public void testGetDefaultStatusField() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        String statusField = client.getDefaultStatusField();
        assertEquals("", statusField); // TestRail test cases don't have status
    }

    @Test
    public void testGetTextType() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        assertEquals(TestRailClient.TextType.MARKDOWN, client.getTextType());
    }

    @Test
    public void testTag() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        assertEquals("@testuser", client.tag("testuser"));
    }

    @Test
    public void testGetTicketBrowseUrl() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        // Test with C prefix
        assertEquals(basePath + "/index.php?/cases/view/123",
                client.getTicketBrowseUrl("C123"));

        // Test without C prefix
        assertEquals(basePath + "/index.php?/cases/view/456",
                client.getTicketBrowseUrl("456"));
    }

    @Test
    public void testBuildUrlToSearch() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        String url = client.buildUrlToSearch("project_id=1");
        assertEquals(basePath + "/index.php?/cases/overview", url);
    }

    @Test
    public void testGetTextFieldsOnly() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        testCaseJson.put("title", "Test title");
        testCaseJson.put("custom_preconds", "Preconditions here");
        testCaseJson.put("custom_steps", "Steps here");

        TestCase testCase = new TestCase(basePath, testCaseJson);
        String text = client.getTextFieldsOnly(testCase);

        assertNotNull(text);
        assertTrue(text.contains("Test title"));
        assertTrue(text.contains("Preconditions here"));
        assertTrue(text.contains("Steps here"));
    }

    @Test
    public void testSetLogEnabled() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        // Should not throw exception
        client.setLogEnabled(true);
        client.setLogEnabled(false);
    }

    @Test
    public void testSetCacheGetRequestsEnabled() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        // Should not throw exception
        client.setCacheGetRequestsEnabled(true);
        client.setCacheGetRequestsEnabled(false);
    }

    @Test
    public void testGetBasePath() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        assertEquals(basePath, client.getBasePath());
    }

    @Test
    public void testGetProjectsMethodExists() throws Exception {
        client = new TestRailClient(basePath, username, apiKey);

        // Verify the method exists and can be called
        // Note: Actual API call would require mocking the HTTP response
        assertNotNull(client.getClass().getMethod("getProjects"));
    }

    @Test
    public void testPath() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        String path = client.path("/get_case/123");
        assertEquals(basePath + "/index.php?/api/v2/get_case/123", path);
    }

    @Test
    public void testGetCacheFolderName() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        assertEquals("cacheTestRail", client.getCacheFolderName());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMoveToStatusThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.moveToStatus("C123", "Approved");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAssignToThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.assignTo("C123", "user@example.com");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPostCommentThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.postComment("C123", "Test comment");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPostCommentIfNotExistsThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.postCommentIfNotExists("C123", "Test comment");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteCommentIfExistsThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.deleteCommentIfExists("C123", "Test comment");
    }

    @Test
    public void testGetComments() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        TestCase testCase = new TestCase(basePath, testCaseJson);

        // Should return empty list
        assertEquals(0, client.getComments("C123", testCase).size());
    }

    @Test
    public void testGetChangeLog() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        TestCase testCase = new TestCase(basePath, testCaseJson);

        // Should return null (not supported)
        assertNull(client.getChangeLog("C123", testCase));
    }

    @Test
    public void testGetFixVersions() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        // Should return empty list
        assertEquals(0, client.getFixVersions("TEST").size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAttachFileToTicketThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        client.attachFileToTicket("C123", "test.txt", "text/plain", null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConvertUrlToFileThrowsException() throws Exception {
        client = new TestRailClient(basePath, username, apiKey);
        client.convertUrlToFile("http://example.com/image.png");
    }

    @Test
    public void testIsValidImageUrl() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);
        assertFalse(client.isValidImageUrl("http://example.com/image.png"));
    }

    /**
     * Test that getInstance returns null when configuration is missing.
     * Note: This test depends on environment variables NOT being set.
     */
    @Test
    public void testGetInstanceWithMissingConfiguration() throws Exception {
        // This test assumes TESTRAIL_BASE_PATH is not set
        // In real environment, this would be configured
        // For now, we just test the method exists and can be called
        assertNotNull(TestRailClient.class.getDeclaredMethod("getInstance"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddLabelIfNotExistsThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        TestCase testCase = new TestCase(basePath, testCaseJson);

        client.addLabelIfNotExists(testCase, "SomeLabel");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteLabelInTicketThrowsException() throws IOException {
        client = new TestRailClient(basePath, username, apiKey);

        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        TestCase testCase = new TestCase(basePath, testCaseJson);

        client.deleteLabelInTicket(testCase, "SomeLabel");
    }

    /**
     * Test field initializer callback pattern.
     */
    @Test
    public void testFieldsInitializerPattern() {
        JSONObject fields = new JSONObject();

        TestRailClient.FieldsInitializer initializer = fieldsCallback -> {
            fieldsCallback.set("priority_id", 3);
            fieldsCallback.set("refs", "PROJ-123");
            fieldsCallback.set("custom_preconds", "Test preconditions");
        };

        initializer.init((key, value) -> fields.put(key, value));

        assertEquals(3, fields.getInt("priority_id"));
        assertEquals("PROJ-123", fields.getString("refs"));
        assertEquals("Test preconditions", fields.getString("custom_preconds"));
    }

    // ========== Markdown Table Conversion Tests ==========

    @Test
    public void testConvertMarkdownTableToTestRailFormat() {
        String markdown =
                "| Username | Password | Role |\n" +
                "|----------|----------|------|\n" +
                "| admin | pass123 | Admin |\n" +
                "| user | test456 | User |";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(markdown);

        assertTrue(result.contains("|||:Username|:Password|:Role"));
        assertTrue(result.contains("||admin|pass123|Admin"));
        assertTrue(result.contains("||user|test456|User"));
        // Should NOT contain Markdown table syntax
        assertFalse(result.contains("|-------"));
    }

    @Test
    public void testConvertMarkdownTableWithSurroundingText() {
        String markdown =
                "User is logged in.\n\n" +
                "**Test Data:**\n\n" +
                "| Col 1 | Col 2 |\n" +
                "|-------|-------|\n" +
                "| val1 | val2 |\n" +
                "| val3 | val4 |";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(markdown);

        assertTrue(result.contains("User is logged in."));
        assertTrue(result.contains("**Test Data:**"));
        assertTrue(result.contains("|||:Col 1|:Col 2"));
        assertTrue(result.contains("||val1|val2"));
        assertTrue(result.contains("||val3|val4"));
    }

    @Test
    public void testConvertAlreadyTestRailFormat() {
        String testrailFormat = "|||:Col 1|:Col 2\n||val1|val2";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(testrailFormat);

        // Should be unchanged
        assertEquals(testrailFormat, result);
    }

    @Test
    public void testConvertNoTables() {
        String plainText = "Just plain text\nWith multiple lines\nNo tables here";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(plainText);

        assertEquals(plainText, result);
    }

    @Test
    public void testConvertNullAndEmpty() {
        assertNull(TestRailClient.convertMarkdownTablesToTestRailFormat(null));
        assertEquals("", TestRailClient.convertMarkdownTablesToTestRailFormat(""));
    }

    @Test
    public void testConvertMarkdownTableWithAlignmentSeparators() {
        // Tables with alignment markers like :---|:---:|---:
        String markdown =
                "| Left | Center | Right |\n" +
                "|:-----|:------:|------:|\n" +
                "| a | b | c |";

        String result = TestRailClient.convertMarkdownTablesToTestRailFormat(markdown);

        assertTrue(result.contains("|||:Left|:Center|:Right"));
        assertTrue(result.contains("||a|b|c"));
    }

    // ========== Markdown Table to HTML Conversion Tests ==========

    @Test
    public void testConvertMarkdownTableToHtml() {
        String markdown =
                "| Col 1 | Col 2 | Col 3 |\n" +
                "|-------|-------|-------|\n" +
                "| val1  | val2  | val3  |\n" +
                "| val4  | val5  | val6  |";

        String result = TestRailClient.convertMarkdownTablesToHtml(markdown);

        assertTrue(result.contains("<table>"));
        assertTrue(result.contains("</table>"));
        // Header row in <thead>
        assertTrue(result.contains("<thead>"));
        assertTrue(result.contains("<th>Col 1</th>"));
        assertTrue(result.contains("<th>Col 2</th>"));
        // Data rows in <tbody>
        assertTrue(result.contains("<tbody>"));
        assertTrue(result.contains("<td>val1</td>"));
        assertTrue(result.contains("<td>val4</td>"));
        // Should not contain Markdown syntax
        assertFalse(result.contains("|----"));
        assertFalse(result.contains("| Col 1 |"));
    }

    @Test
    public void testConvertMarkdownTableToHtmlWithSurroundingText() {
        String markdown =
                "Open the page.\n\n" +
                "| User | Role |\n" +
                "|------|------|\n" +
                "| admin | Admin |\n\n" +
                "Click login.";

        String result = TestRailClient.convertMarkdownTablesToHtml(markdown);

        assertTrue(result.contains("Open the page."));
        assertTrue(result.contains("Click login."));
        assertTrue(result.contains("<table>"));
        assertTrue(result.contains("<th>User</th>"));
        assertTrue(result.contains("<td>admin</td>"));
    }

    @Test
    public void testConvertMarkdownTableToHtmlNullAndEmpty() {
        assertNull(TestRailClient.convertMarkdownTablesToHtml(null));
        assertEquals("", TestRailClient.convertMarkdownTablesToHtml(""));
    }

    @Test
    public void testConvertMarkdownTableToHtmlNoTable() {
        String text = "Just plain step text\nWith multiple lines";
        String result = TestRailClient.convertMarkdownTablesToHtml(text);
        assertEquals("<p>Just plain step text</p><p>With multiple lines</p>", result);
    }

    // ========== MCP Tools Method Existence Tests ==========

    @Test
    public void testGetLabelsMethodExists() throws Exception {
        assertNotNull(TestRailClient.class.getMethod("getLabels", String.class));
    }

    @Test
    public void testGetLabelMethodExists() throws Exception {
        assertNotNull(TestRailClient.class.getMethod("getLabel", String.class));
    }

    @Test
    public void testUpdateLabelMethodExists() throws Exception {
        assertNotNull(TestRailClient.class.getMethod("updateLabel", String.class, String.class, String.class));
    }

    @Test
    public void testGetCaseTypesMethodExists() throws Exception {
        assertNotNull(TestRailClient.class.getMethod("getCaseTypes"));
    }

    @Test
    public void testCreateCaseStepsMethodExists() throws Exception {
        assertNotNull(TestRailClient.class.getMethod("createCaseSteps",
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class));
    }
}
