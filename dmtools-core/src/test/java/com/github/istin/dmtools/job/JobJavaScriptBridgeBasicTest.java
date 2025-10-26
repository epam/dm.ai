package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for JobJavaScriptBridge without complex MCP mocking
 */
class JobJavaScriptBridgeBasicTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private AI mockAI;

    @Mock
    private Confluence mockConfluence;

    @Mock
    private SourceCode mockSourceCode;

    private JobJavaScriptBridge bridge;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode, null);
    }

    @Test
    void testBasicJavaScriptExecution() throws Exception {
        // Given - Simple JavaScript without MCP tool calls
        String simpleJS = """
            function action(params) {
                console.log("JavaScript received params:", params);
                console.log("typeof params:", typeof params);
                console.log("params.name:", params.name);
                if (params && params.name) {
                    return "Hello " + params.name + "!";
                } else {
                    return "Hello undefined!";
                }
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("name", "World");

        // When
        Object result = bridge.executeJavaScript(simpleJS, params);

        // Then
        assertNotNull(result);
        assertEquals("Hello World!", result.toString());
    }

    @Test
    void testJavaScriptMathOperations() throws Exception {
        // Given
        String mathJS = """
            function action(params) {
                return {
                    sum: params.a + params.b,
                    product: params.a * params.b,
                    message: "Calculated for " + params.operation
                };
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("a", 10);
        params.put("b", 5);
        params.put("operation", "test");

        // When
        Object result = bridge.executeJavaScript(mathJS, params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("15")); // sum
        assertTrue(resultStr.contains("50")); // product
        assertTrue(resultStr.contains("test")); // operation
    }

    @Test
    void testJavaScriptArrayProcessing() throws Exception {
        // Given
        String arrayJS = """
            function action(params) {
                var numbers = params.numbers;
                var sum = 0;
                for (var i = 0; i < numbers.length; i++) {
                    sum += numbers[i];
                }
                return {
                    total: sum,
                    count: numbers.length,
                    average: sum / numbers.length
                };
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("numbers", new org.json.JSONArray().put(1).put(2).put(3).put(4).put(5));

        // When
        Object result = bridge.executeJavaScript(arrayJS, params);

        // Then
        assertNotNull(result);
        // This test will pass if basic JS execution works
    }

    @Test
    void testInlineJavaScriptDetection() throws Exception {
        // Given - JavaScript that should be detected as inline
        String inlineJS = "function action(params) { return 'inline'; }";
        
        JSONObject params = new JSONObject();

        // When
        Object result = bridge.executeJavaScript(inlineJS, params);

        // Then
        assertNotNull(result);
        assertEquals("inline", result.toString());
    }

    @Test
    void testJavaScriptContextReuse() throws Exception {
        // Given
        String js1 = "function action(params) { return 'first'; }";
        String js2 = "function action(params) { return 'second'; }";
        
        JSONObject params = new JSONObject();

        // When - Execute multiple times
        Object result1 = bridge.executeJavaScript(js1, params);
        Object result2 = bridge.executeJavaScript(js2, params);

        // Then - Each execution should be independent
        assertEquals("first", result1.toString());
        assertEquals("second", result2.toString());
    }

    @Test
    void testJavaScriptError() throws Exception {
        // Given - JavaScript with syntax error
        String errorJS = """
            function action(params) {
                // Intentional error - undefined variable
                return undefinedVariable + params.test;
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("test", "value");

        // When/Then - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            bridge.executeJavaScript(errorJS, params);
        });
    }

    @Test
    void testComplexJSONProcessing() throws Exception {
        // Given
        String complexJS = """
            function action(params) {
                var user = params.user;
                var processed = {
                    fullName: user.firstName + " " + user.lastName,
                    age: user.age,
                    isAdult: user.age >= 18,
                    contacts: {
                        email: user.email,
                        phone: user.phone
                    }
                };
                return processed;
            }
            """;
        
        JSONObject user = new JSONObject();
        user.put("firstName", "John");
        user.put("lastName", "Doe");
        user.put("age", 25);
        user.put("email", "john@example.com");
        user.put("phone", "123-456-7890");
        
        JSONObject params = new JSONObject();
        params.put("user", user);

        // When
        Object result = bridge.executeJavaScript(complexJS, params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("John Doe"));
        assertTrue(resultStr.contains("25"));
        assertTrue(resultStr.contains("john@example.com"));
    }
}
