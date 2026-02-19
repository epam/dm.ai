package com.github.istin.dmtools.testrail.model;

import com.github.istin.dmtools.common.model.ITicket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestCaseTest {

    private TestCase testCase;
    private String basePath = "https://example.testrail.com";

    @Before
    public void setUp() {
        JSONObject testCaseJson = new JSONObject();
        testCaseJson.put("id", 123);
        testCaseJson.put("title", "Verify login functionality");
        testCaseJson.put("priority_id", 3); // High
        testCaseJson.put("type_id", 1);
        testCaseJson.put("refs", "PROJ-456,PROJ-789");
        testCaseJson.put("created_on", 1609459200L); // 2021-01-01 00:00:00
        testCaseJson.put("updated_on", 1609545600L); // 2021-01-02 00:00:00
        testCaseJson.put("estimate", "30m");
        testCaseJson.put("custom_preconds", "User must be logged out");
        testCaseJson.put("custom_steps", "1. Navigate to login page\n2. Enter credentials\n3. Click login");
        testCaseJson.put("custom_expected", "User should be logged in");

        testCase = new TestCase(basePath, testCaseJson);
    }

    @Test
    public void testGetKey() {
        assertEquals("C123", testCase.getKey());
    }

    @Test
    public void testGetTicketKey() {
        assertEquals("C123", testCase.getTicketKey());
    }

    @Test
    public void testGetStatus() throws IOException {
        assertEquals("Active", testCase.getStatus());
    }

    @Test
    public void testGetIssueType() throws IOException {
        assertEquals("Test Case", testCase.getIssueType());
    }

    @Test
    public void testGetPriority() throws IOException {
        assertEquals("High", testCase.getPriority());
    }

    @Test
    public void testGetPriorityAsEnum() {
        assertEquals(ITicket.TicketPriority.High, testCase.getPriorityAsEnum());
    }

    @Test
    public void testGetPriorityLow() throws IOException {
        testCase.set("priority_id", 1);
        assertEquals("Low", testCase.getPriority());
        assertEquals(ITicket.TicketPriority.Low, testCase.getPriorityAsEnum());
    }

    @Test
    public void testGetPriorityMedium() throws IOException {
        testCase.set("priority_id", 2);
        assertEquals("Medium", testCase.getPriority());
        assertEquals(ITicket.TicketPriority.Medium, testCase.getPriorityAsEnum());
    }

    @Test
    public void testGetPriorityCritical() throws IOException {
        testCase.set("priority_id", 4);
        assertEquals("Critical", testCase.getPriority());
        assertEquals(ITicket.TicketPriority.Critical, testCase.getPriorityAsEnum());
    }

    @Test
    public void testGetTicketTitle() throws IOException {
        assertEquals("Verify login functionality", testCase.getTicketTitle());
    }

    @Test
    public void testGetTicketDescription() {
        String description = testCase.getTicketDescription();
        assertNotNull(description);
        assertTrue(description.contains("Preconditions:"));
        assertTrue(description.contains("User must be logged out"));
        assertTrue(description.contains("Steps:"));
        assertTrue(description.contains("Navigate to login page"));
        assertTrue(description.contains("Expected Result:"));
        assertTrue(description.contains("User should be logged in"));
    }

    @Test
    public void testGetTicketLink() {
        assertEquals("https://example.testrail.com/index.php?/cases/view/123", testCase.getTicketLink());
    }

    @Test
    public void testGetCreated() {
        assertNotNull(testCase.getCreated());
        assertEquals(1609459200000L, testCase.getCreated().getTime());
    }

    @Test
    public void testGetUpdatedAsMillis() {
        assertEquals(Long.valueOf(1609545600000L), testCase.getUpdatedAsMillis());
    }

    @Test
    public void testGetTicketLabels() {
        // Set up real TestRail labels (array of objects with id, title)
        JSONArray labelsArray = new JSONArray();
        labelsArray.put(new JSONObject().put("id", 7).put("title", "Login").put("created_by", 2));
        labelsArray.put(new JSONObject().put("id", 8).put("title", "Manual").put("created_by", 30));
        testCase.set("labels", labelsArray);

        JSONArray labels = testCase.getTicketLabels();
        assertNotNull(labels);
        assertEquals(2, labels.length());
        assertEquals("Login", labels.getString(0));
        assertEquals("Manual", labels.getString(1));
    }

    @Test
    public void testGetTicketLabelsEmpty() {
        // No labels set - should return empty array
        testCase.set("labels", new JSONArray());
        JSONArray labels = testCase.getTicketLabels();
        assertNotNull(labels);
        assertEquals(0, labels.length());
    }

    @Test
    public void testGetTicketLabelsNull() {
        // labels field not present at all
        JSONObject json = new JSONObject();
        json.put("id", 999);
        json.put("title", "No labels case");
        TestCase noLabelsCase = new TestCase(basePath, json);

        JSONArray labels = noLabelsCase.getTicketLabels();
        assertNotNull(labels);
        assertEquals(0, labels.length());
    }

    @Test
    public void testGetWeight() {
        // "30m" is TestRail time format (not a numeric double), so it returns 0
        assertEquals(0.0, testCase.getWeight(), 0.01);
    }

    @Test
    public void testGetWeightWithNumericEstimate() {
        testCase.set("estimate", "5");
        assertEquals(5.0, testCase.getWeight(), 0.01);
    }

    @Test
    public void testGetWeightWithInvalidEstimate() {
        testCase.set("estimate", "invalid");
        assertEquals(0.0, testCase.getWeight(), 0.01);
    }

    @Test
    public void testGetWeightWithNoEstimate() {
        testCase.set("estimate", null);
        assertEquals(0.0, testCase.getWeight(), 0.01);
    }

    @Test
    public void testGetFieldValueAsString() {
        assertEquals("Verify login functionality", testCase.getFieldValueAsString("title"));
    }

    @Test
    public void testGetFields() {
        assertNull(testCase.getFields()); // TestRail doesn't use Jira Fields model
    }

    @Test
    public void testGetFieldsAsJSON() {
        JSONObject fieldsJson = testCase.getFieldsAsJSON();
        assertNotNull(fieldsJson);
        assertEquals(123, fieldsJson.getInt("id"));
        assertEquals("Verify login functionality", fieldsJson.getString("title"));
    }

    @Test
    public void testToText() throws IOException {
        String text = testCase.toText();
        assertNotNull(text);
        assertTrue(text.contains("Verify login functionality"));
    }

    @Test
    public void testEquals() {
        JSONObject anotherJson = new JSONObject();
        anotherJson.put("id", 123);
        anotherJson.put("title", "Different title");

        TestCase anotherTestCase = new TestCase(basePath, anotherJson);
        assertEquals(testCase, anotherTestCase); // Same ID means equal
    }

    @Test
    public void testNotEquals() {
        JSONObject anotherJson = new JSONObject();
        anotherJson.put("id", 456);

        TestCase anotherTestCase = new TestCase(basePath, anotherJson);
        assertNotEquals(testCase, anotherTestCase);
    }

    @Test
    public void testHashCode() {
        assertEquals("C123".hashCode(), testCase.hashCode());
    }

    @Test
    public void testGetProgress() throws IOException {
        assertEquals(0.0, testCase.getProgress(), 0.01); // Test cases don't have progress
    }

    @Test
    public void testGetAttachments() {
        assertNotNull(testCase.getAttachments());
        assertEquals(0, testCase.getAttachments().size());
    }

    @Test
    public void testGetIteration() {
        assertNull(testCase.getIteration());
    }

    @Test
    public void testGetIterations() {
        assertNotNull(testCase.getIterations());
        assertEquals(0, testCase.getIterations().size());
    }

    @Test
    public void testGetResolution() {
        assertNull(testCase.getResolution());
    }

    @Test
    public void testGetCreator() {
        testCase.set("created_by", 42);
        assertNotNull(testCase.getCreator());
        assertEquals("User 42", testCase.getCreator().getFullName());
        assertEquals("42", testCase.getCreator().getID());
    }

    @Test
    public void testGetCreatorNull() {
        assertNull(testCase.getCreator());
    }

    @Test
    public void testGetTestCaseFields() {
        TestCaseFields fields = testCase.getTestCaseFields();
        assertNotNull(fields);
        assertEquals(Integer.valueOf(123), fields.getId());
        assertEquals("Verify login functionality", fields.getTitle());
        assertEquals(Integer.valueOf(3), fields.getPriorityId());
    }
}
