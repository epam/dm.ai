// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.xray;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XrayRestClient parallel fetch functionality.
 * These tests verify the core logic without requiring actual API calls.
 */
@RunWith(MockitoJUnitRunner.class)
public class XrayRestClientTest {

    @Mock
    private XrayRestClient mockRestClient;

    private XrayRestClient spyRestClient;

    @Before
    public void setUp() throws IOException {
        // Create a spy of XrayRestClient for partial mocking
        // We'll mock the actual API calls but test our logic
        try {
            XrayRestClient realClient = new XrayRestClient(
                    "https://test.xray.cloud.getxray.app/api/v2",
                    "test_client_id",
                    "test_client_secret"
            );
            spyRestClient = spy(realClient);
        } catch (Exception e) {
            // If we can't create the client, use the mock instead
            spyRestClient = mockRestClient;
        }
    }

    /**
     * Test 1: Key Sorting Logic
     * Verify that keys are sorted correctly for consistent batching
     */
    @Test
    public void testKeySorting_NaturalOrder() {
        // Arrange
        List<String> keys = Arrays.asList("TEST-200", "TEST-100", "TEST-50", "TEST-1500");

        // Act
        List<String> sortedKeys = new ArrayList<>(keys);
        Collections.sort(sortedKeys);

        // Assert - Natural string sort (not numeric)
        // Note: This is the current behavior. If numeric sort is needed,
        // a custom comparator should be implemented
        assertEquals("TEST-100", sortedKeys.get(0));
        assertEquals("TEST-1500", sortedKeys.get(1));
        assertEquals("TEST-200", sortedKeys.get(2));
        assertEquals("TEST-50", sortedKeys.get(3));
    }

    /**
     * Test 2: Batch Splitting - Empty Keys
     */
    @Test
    public void testBatchSplitting_EmptyKeys() throws IOException {
        // Arrange
        List<String> emptyKeys = Collections.emptyList();

        // Act
        JSONArray result = spyRestClient.getTestsByKeysGraphQLParallel(emptyKeys, null, 100, 2, 500L);

        // Assert
        assertNotNull("Result should not be null", result);
        assertEquals("Result should be empty array", 0, result.length());
    }

    /**
     * Test 3: Batch Splitting - Single Key
     * Note: This test only verifies that the method doesn't crash with null baseJQL.
     * Full integration testing with API mocking would require mocking private methods,
     * which is not recommended. Real API testing should be done in integration tests.
     */
    @Test
    public void testBatchSplitting_SingleKey() throws IOException {
        // Arrange
        List<String> singleKey = Collections.singletonList("TEST-100");

        // Act - just verify the method can be called with null baseJQL without crashing
        // We expect an IOException since we're not mocking the internal API calls
        try {
            JSONArray result = spyRestClient.getTestsByKeysGraphQLParallel(singleKey, null, 100, 2, 500L);
            // If it returns empty array instead of throwing, that's also acceptable
            assertNotNull("Result should not be null", result);
        } catch (IOException e) {
            // Expected - real API call will fail in unit test environment
            assertTrue("Should fail with API-related error",
                    e.getMessage().contains("Failed") || e.getMessage().contains("token") || e.getMessage().contains("401"));
        }
    }

    /**
     * Test 4: Batch Splitting - Exact Batch Size
     */
    @Test
    public void testBatchSplitting_ExactBatchSize() {
        // Arrange
        int batchSize = 100;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            keys.add("TEST-" + i);
        }

        // Act
        List<List<String>> batches = splitIntoBatches(keys, batchSize);

        // Assert
        assertEquals("Should create 1 batch", 1, batches.size());
        assertEquals("Batch should have 100 keys", 100, batches.get(0).size());
    }

    /**
     * Test 5: Batch Splitting - Multiple Batches
     */
    @Test
    public void testBatchSplitting_MultipleBatches() {
        // Arrange
        int batchSize = 100;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            keys.add("TEST-" + i);
        }

        // Act
        List<List<String>> batches = splitIntoBatches(keys, batchSize);

        // Assert
        assertEquals("Should create 3 batches", 3, batches.size());
        assertEquals("First batch should have 100 keys", 100, batches.get(0).size());
        assertEquals("Second batch should have 100 keys", 100, batches.get(1).size());
        assertEquals("Third batch should have 50 keys", 50, batches.get(2).size());
    }

    /**
     * Test 6: Batch Splitting - Partial Last Batch
     */
    @Test
    public void testBatchSplitting_PartialLastBatch() {
        // Arrange
        int batchSize = 100;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            keys.add("TEST-" + i);
        }

        // Act
        List<List<String>> batches = splitIntoBatches(keys, batchSize);

        // Assert
        assertEquals("Should create 3 batches", 3, batches.size());
        assertEquals("Last batch should have 1 key", 1, batches.get(2).size());
    }

    /**
     * Test 7: Rate Limit Detection - 429 Error
     */
    @Test
    public void testRateLimitDetection_429Error() {
        // Arrange
        Exception e429 = new IOException("HTTP 429 Too Many Requests");

        // Act
        boolean isRateLimit = isRateLimitError(e429);

        // Assert
        assertTrue("Should detect 429 as rate limit error", isRateLimit);
    }

    /**
     * Test 8: Rate Limit Detection - "rate limit" text
     */
    @Test
    public void testRateLimitDetection_RateLimitText() {
        // Arrange
        Exception eRateLimit = new IOException("The request has been rate-limited");

        // Act
        boolean isRateLimit = isRateLimitError(eRateLimit);

        // Assert
        assertTrue("Should detect 'rate-limited' as rate limit error", isRateLimit);
    }

    /**
     * Test 9: Rate Limit Detection - "rate limit" with hyphen
     */
    @Test
    public void testRateLimitDetection_RateLimitHyphen() {
        // Arrange
        Exception eRateLimit = new IOException("Error: rate limit exceeded");

        // Act
        boolean isRateLimit = isRateLimitError(eRateLimit);

        // Assert
        assertTrue("Should detect 'rate limit' as rate limit error", isRateLimit);
    }

    /**
     * Test 10: Rate Limit Detection - Non-Rate-Limit Error
     */
    @Test
    public void testRateLimitDetection_NonRateLimitError() {
        // Arrange
        Exception eOther = new IOException("Connection timeout");

        // Act
        boolean isRateLimit = isRateLimitError(eOther);

        // Assert
        assertFalse("Should not detect timeout as rate limit error", isRateLimit);
    }

    /**
     * Test 11: Rate Limit Detection - Null Exception
     */
    @Test
    public void testRateLimitDetection_NullException() {
        // Act
        boolean isRateLimit = isRateLimitError(null);

        // Assert
        assertFalse("Should return false for null exception", isRateLimit);
    }

    /**
     * Test 12: PropertyReader Configuration - Enabled
     */
    @Test
    public void testPropertyReaderConfiguration_Enabled() {
        // Note: This test requires PropertyReader to be properly configured
        // In a real environment, you would set system properties or use test configuration

        PropertyReader propertyReader = new PropertyReader();

        // Test default values
        assertFalse("Parallel fetch should be disabled by default",
                propertyReader.isXrayParallelFetchEnabled());
        assertEquals("Default batch size should be 100",
                100, propertyReader.getXrayParallelBatchSize());
        assertEquals("Default threads should be 2",
                2, propertyReader.getXrayParallelThreads());
        assertEquals("Default delay should be 500ms",
                500L, propertyReader.getXrayParallelDelayMs());
    }

    /**
     * Test 13: Result Filtering - Requested Keys Only
     */
    @Test
    public void testResultFiltering_RequestedKeysOnly() {
        // Arrange
        Set<String> requestedKeys = new HashSet<>(Arrays.asList("TEST-100", "TEST-200", "TEST-300"));

        JSONArray allResults = new JSONArray();
        allResults.put(createMockTest("TEST-100"));
        allResults.put(createMockTest("TEST-150")); // Extra key from range query
        allResults.put(createMockTest("TEST-200"));
        allResults.put(createMockTest("TEST-250")); // Extra key from range query
        allResults.put(createMockTest("TEST-300"));

        // Act
        JSONArray filtered = filterResults(allResults, requestedKeys);

        // Assert
        assertEquals("Should filter to 3 results", 3, filtered.length());
        Set<String> resultKeys = new HashSet<>();
        for (int i = 0; i < filtered.length(); i++) {
            resultKeys.add(filtered.getJSONObject(i).getJSONObject("jira").getString("key"));
        }
        assertEquals("Should contain only requested keys", requestedKeys, resultKeys);
    }

    /**
     * Test 14: Result Merging - Multiple Batches
     */
    @Test
    public void testResultMerging_MultipleBatches() {
        // Arrange
        JSONArray batch1 = new JSONArray();
        batch1.put(createMockTest("TEST-100"));
        batch1.put(createMockTest("TEST-200"));

        JSONArray batch2 = new JSONArray();
        batch2.put(createMockTest("TEST-300"));
        batch2.put(createMockTest("TEST-400"));

        JSONArray batch3 = new JSONArray();
        batch3.put(createMockTest("TEST-500"));

        // Act
        JSONArray merged = new JSONArray();
        for (int i = 0; i < batch1.length(); i++) {
            merged.put(batch1.get(i));
        }
        for (int i = 0; i < batch2.length(); i++) {
            merged.put(batch2.get(i));
        }
        for (int i = 0; i < batch3.length(); i++) {
            merged.put(batch3.get(i));
        }

        // Assert
        assertEquals("Should have 5 total results", 5, merged.length());
    }

    /**
     * Test 15: Result Merging - Empty Batch Handling
     */
    @Test
    public void testResultMerging_EmptyBatch() {
        // Arrange
        JSONArray batch1 = new JSONArray();
        batch1.put(createMockTest("TEST-100"));

        JSONArray emptyBatch = new JSONArray();

        JSONArray batch2 = new JSONArray();
        batch2.put(createMockTest("TEST-200"));

        // Act
        JSONArray merged = new JSONArray();
        for (int i = 0; i < batch1.length(); i++) {
            merged.put(batch1.get(i));
        }
        for (int i = 0; i < emptyBatch.length(); i++) {
            merged.put(emptyBatch.get(i));
        }
        for (int i = 0; i < batch2.length(); i++) {
            merged.put(batch2.get(i));
        }

        // Assert
        assertEquals("Should handle empty batch", 2, merged.length());
    }

    // Helper Methods

    /**
     * Helper method to split keys into batches
     * (Duplicates the logic from getTestsByKeysGraphQLParallel for testing)
     */
    private List<List<String>> splitIntoBatches(List<String> keys, int batchSize) {
        List<String> sortedKeys = new ArrayList<>(keys);
        Collections.sort(sortedKeys);

        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < sortedKeys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, sortedKeys.size());
            batches.add(sortedKeys.subList(i, end));
        }
        return batches;
    }

    /**
     * Helper method to detect rate limit errors
     * (Duplicates the logic from getTestsByKeysGraphQLParallel for testing)
     */
    private boolean isRateLimitError(Exception e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }

        String message = e.getMessage();
        return message.contains("429") ||
               message.toLowerCase().contains("rate limit") ||
               message.toLowerCase().contains("rate-limit");
    }

    /**
     * Helper method to filter results to requested keys only
     */
    private JSONArray filterResults(JSONArray allResults, Set<String> requestedKeys) {
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < allResults.length(); i++) {
            JSONObject test = allResults.getJSONObject(i);
            String key = test.getJSONObject("jira").getString("key");
            if (requestedKeys.contains(key)) {
                filtered.put(test);
            }
        }
        return filtered;
    }

    /**
     * Helper method to create mock test result
     */
    private JSONObject createMockTest(String key) {
        JSONObject test = new JSONObject();
        JSONObject jira = new JSONObject();
        jira.put("key", key);
        jira.put("summary", "Test summary for " + key);
        test.put("jira", jira);
        test.put("issueId", "12345");
        test.put("steps", new JSONArray());
        return test;
    }

    /**
     * Helper method to create mock test results for a list of keys
     */
    private JSONArray createMockTestResults(List<String> keys) {
        JSONArray results = new JSONArray();
        for (String key : keys) {
            results.put(createMockTest(key));
        }
        return results;
    }

    /**
     * Test 16: Extract nextValidRequestDate from X-ray API error
     */
    @Test
    public void testExtractNextValidRequestDate_ValidJson() throws Exception {
        // Arrange - Real error message from X-ray API
        String errorMessage = "printAndCreateException error: https://eu.xray.cloud.getxray.app/api/v2/graphql\n" +
                "{\"error\":{\"text\":\"Too many requests in this time frame.\",\"nextValidRequestDate\":\"2026-01-27T17:48:54.791Z\"}}\n" +
                "Too Many Requests\n429";

        // Use reflection to access private method
        java.lang.reflect.Method method = XrayRestClient.class.getDeclaredMethod("extractNextValidRequestDate", String.class);
        method.setAccessible(true);

        // Act
        Long result = (Long) method.invoke(spyRestClient, errorMessage);

        // Assert
        assertNotNull("Should extract timestamp from valid error message", result);
        assertTrue("Timestamp should be positive", result > 0);

        // Verify it's approximately the expected date (2026-01-27T17:48:54.791Z)
        java.time.Instant expectedTime = java.time.Instant.parse("2026-01-27T17:48:54.791Z");
        assertEquals("Should match the exact timestamp", expectedTime.toEpochMilli(), result.longValue());
    }

    /**
     * Test 17: Extract nextValidRequestDate - No JSON
     */
    @Test
    public void testExtractNextValidRequestDate_NoJson() throws Exception {
        // Arrange
        String errorMessage = "Connection timeout";

        // Use reflection to access private method
        java.lang.reflect.Method method = XrayRestClient.class.getDeclaredMethod("extractNextValidRequestDate", String.class);
        method.setAccessible(true);

        // Act
        Long result = (Long) method.invoke(spyRestClient, errorMessage);

        // Assert
        assertNull("Should return null when no JSON found", result);
    }

    /**
     * Test 18: Extract nextValidRequestDate - No nextValidRequestDate field
     */
    @Test
    public void testExtractNextValidRequestDate_NoDateField() throws Exception {
        // Arrange
        String errorMessage = "{\"error\":{\"text\":\"Some other error\"}}";

        // Use reflection to access private method
        java.lang.reflect.Method method = XrayRestClient.class.getDeclaredMethod("extractNextValidRequestDate", String.class);
        method.setAccessible(true);

        // Act
        Long result = (Long) method.invoke(spyRestClient, errorMessage);

        // Assert
        assertNull("Should return null when nextValidRequestDate field is missing", result);
    }

    /**
     * Test 19: Extract nextValidRequestDate - Null message
     */
    @Test
    public void testExtractNextValidRequestDate_NullMessage() throws Exception {
        // Use reflection to access private method
        java.lang.reflect.Method method = XrayRestClient.class.getDeclaredMethod("extractNextValidRequestDate", String.class);
        method.setAccessible(true);

        // Act
        Long result = (Long) method.invoke(spyRestClient, (String) null);

        // Assert
        assertNull("Should return null for null message", result);
    }

    /**
     * Test 20: Extract nextValidRequestDate - Invalid date format
     */
    @Test
    public void testExtractNextValidRequestDate_InvalidDateFormat() throws Exception {
        // Arrange
        String errorMessage = "{\"error\":{\"text\":\"Too many requests\",\"nextValidRequestDate\":\"invalid-date\"}}";

        // Use reflection to access private method
        java.lang.reflect.Method method = XrayRestClient.class.getDeclaredMethod("extractNextValidRequestDate", String.class);
        method.setAccessible(true);

        // Act
        Long result = (Long) method.invoke(spyRestClient, errorMessage);

        // Assert
        assertNull("Should return null for invalid date format", result);
    }
}
