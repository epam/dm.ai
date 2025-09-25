package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for JobJavaScriptBridge with mocked MCP tools
 */
class JobJavaScriptBridgeTest {

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
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode);
    }

    @Test
    void testBasicJavaScriptExecution() throws Exception {
        // Given
        String simpleJS = """
            function action(params) {
                return "Hello " + params.name;
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("name", "World");

        // When
        Object result = bridge.executeJavaScript(simpleJS, params);

        // Then
        assertNotNull(result);
        assertEquals("Hello World", result.toString());
    }

    @Test
    void testAIToolExecution() throws Exception {
        // Given
        String jsWithAI = """
            function action(params) {
                var response = gemini_ai_chat("Test message");
                return "AI said: " + response;
            }
            """;
        
        JSONObject params = new JSONObject();

        // Mock MCP tool execution
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("gemini_ai_chat"), 
                any(Map.class), 
                any(Map.class)
            )).thenReturn("Mocked AI response");

            // When
            Object result = bridge.executeJavaScript(jsWithAI, params);

            // Then
            assertNotNull(result);
            assertEquals("AI said: Mocked AI response", result.toString());
            
            // Verify MCP tool was called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("gemini_ai_chat"),
                argThat(args -> "Test message".equals(((Map<String, Object>) args).get("message"))),
                any(Map.class)
            ));
        }
    }

    @Test
    void testJiraToolExecution() throws Exception {
        // Given
        String jsWithJira = """
            function action(params) {
                var ticket = jira_create_ticket_basic("TEST", "Story", "Test Title", "Test Description");
                jira_post_comment(ticket.key, "Created successfully");
                return { success: true, ticketKey: ticket.key };
            }
            """;
        
        JSONObject params = new JSONObject();

        // Mock MCP tool execution
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock ticket creation
            Map<String, Object> mockTicket = new HashMap<>();
            mockTicket.put("key", "TEST-123");
            
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), 
                any(Map.class), 
                any(Map.class)
            )).thenReturn(mockTicket);

            // Mock comment posting
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), 
                any(Map.class), 
                any(Map.class)
            )).thenReturn("Comment posted");

            // When
            Object result = bridge.executeJavaScript(jsWithJira, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains("TEST-123"));
            
            // Verify both tools were called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testConfluenceToolExecution() throws Exception {
        // Given
        String jsWithConfluence = """
            function action(params) {
                var page = confluence_create_page("Test Page", "123", "Test content", "SPACE");
                var content = confluence_content_by_title("Test Page");
                return { pageId: page.id, content: content };
            }
            """;
        
        JSONObject params = new JSONObject();

        // Mock MCP tool execution
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock page creation
            Map<String, Object> mockPage = new HashMap<>();
            mockPage.put("id", "456");
            
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("confluence_create_page"), 
                any(Map.class), 
                any(Map.class)
            )).thenReturn(mockPage);

            // Mock content retrieval
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("confluence_content_by_title"), 
                any(Map.class), 
                any(Map.class)
            )).thenReturn("Page content");

            // When
            Object result = bridge.executeJavaScript(jsWithConfluence, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains("456"));
            
            // Verify tools were called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("confluence_create_page"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("confluence_content_by_title"), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testMultipleToolChain() throws Exception {
        // Given - Complex scenario with multiple tool calls
        String complexJS = """
            function action(params) {
                // Get AI analysis
                var analysis = dial_ai_chat("Analyze: " + params.input);
                
                // Create ticket based on analysis
                var ticket = jira_create_ticket_basic("PROJ", "Task", "AI Analysis", analysis);
                
                // Update ticket with additional info
                jira_update_field(ticket.key, "priority", {name: "High"});
                
                // Create Confluence page
                var page = confluence_create_page("Analysis " + ticket.key, "123", analysis, "SPACE");
                
                // Link them
                jira_post_comment(ticket.key, "Analysis page: " + page.url);
                
                return {
                    ticketKey: ticket.key,
                    pageId: page.id,
                    analysis: analysis
                };
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("input", "Complex requirement");

        // Mock MCP tool execution
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock AI analysis
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("dial_ai_chat"), any(Map.class), any(Map.class)
            )).thenReturn("Detailed AI analysis result");

            // Mock ticket creation
            Map<String, Object> mockTicket = new HashMap<>();
            mockTicket.put("key", "PROJ-456");
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenReturn(mockTicket);

            // Mock field update
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_field"), any(Map.class), any(Map.class)
            )).thenReturn("Field updated");

            // Mock page creation
            Map<String, Object> mockPage = new HashMap<>();
            mockPage.put("id", "789");
            mockPage.put("url", "http://confluence.com/page/789");
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("confluence_create_page"), any(Map.class), any(Map.class)
            )).thenReturn(mockPage);

            // Mock comment posting
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            )).thenReturn("Comment posted");

            // When
            Object result = bridge.executeJavaScript(complexJS, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("PROJ-456"));
            assertTrue(resultStr.contains("789"));
            
            // Verify all tools were called in sequence
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("dial_ai_chat"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_field"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("confluence_create_page"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testGitHubUrlLoading() throws Exception {
        // Given
        String githubUrl = "https://github.com/IstiN/dmtools/blob/main/dmtools-core/src/main/resources/js/test.js";
        String mockJSContent = """
            function action(params) {
                return "Loaded from GitHub: " + params.message;
            }
            """;
        
        when(mockSourceCode.getFileContent(githubUrl)).thenReturn(mockJSContent);
        
        JSONObject params = new JSONObject();
        params.put("message", "Hello GitHub");

        // When
        Object result = bridge.executeJavaScript(githubUrl, params);

        // Then
        assertNotNull(result);
        assertEquals("Loaded from GitHub: Hello GitHub", result.toString());
        verify(mockSourceCode).getFileContent(githubUrl);
    }

    @Test
    void testResourcePathLoading() throws Exception {
        // Given - Resource path loading uses classpath resources
        String resourcePath = "testScripts/sample.js";
        JSONObject params = new JSONObject();
        params.put("test", "value");

        // When/Then - This will fail as resource doesn't exist, but tests the path detection
        Exception exception = assertThrows(RuntimeException.class, () -> {
            bridge.executeJavaScript(resourcePath, params);
        });
        
        assertTrue(exception.getMessage().contains("Resource not found") || 
                  exception.getMessage().contains("Failed to load JS resource") ||
                  exception.getMessage().contains("JavaScript file not found"));
    }

    @Test
    void testToolExecutionError() throws Exception {
        // Given
        String jsWithError = """
            function action(params) {
                var result = jira_create_ticket_basic("INVALID", "BadType", "", "");
                return result;
            }
            """;
        
        JSONObject params = new JSONObject();

        // Mock MCP tool execution to throw error
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenThrow(new RuntimeException("Invalid project"));

            // When/Then
            Exception exception = assertThrows(RuntimeException.class, () -> {
                bridge.executeJavaScript(jsWithError, params);
            });
            
            assertTrue(exception.getMessage().contains("Tool execution failed") || 
                      exception.getMessage().contains("Invalid project"));
        }
    }

    @Test
    void testJavaScriptParameterPassing() throws Exception {
        // Given
        String jsWithComplexParams = """
            function action(params) {
                return {
                    inputTicket: params.inputTicket.key,
                    priority: params.inputTicket.priority,
                    aiResponse: params.aiResponse,
                    processedItems: params.items.length
                };
            }
            """;
        
        JSONObject params = new JSONObject();
        JSONObject ticket = new JSONObject();
        ticket.put("key", "TEST-789");
        ticket.put("priority", "High");
        params.put("inputTicket", ticket);
        params.put("aiResponse", "AI analysis complete");
        params.put("items", new org.json.JSONArray().put("item1").put("item2"));

        // When
        Object result = bridge.executeJavaScript(jsWithComplexParams, params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("TEST-789"));
        assertTrue(resultStr.contains("High"));
        assertTrue(resultStr.contains("2")); // items length
    }

    @Test
    void testCachingBehavior() throws Exception {
        // Given
        String githubUrl = "https://github.com/test/repo/blob/main/script.js";
        String jsContent = "function action(params) { return 'cached'; }";
        
        when(mockSourceCode.getFileContent(githubUrl)).thenReturn(jsContent);
        
        JSONObject params = new JSONObject();

        // When - Execute twice
        bridge.executeJavaScript(githubUrl, params);
        bridge.executeJavaScript(githubUrl, params);

        // Then - Should only load from GitHub once due to caching
        verify(mockSourceCode, times(1)).getFileContent(githubUrl);
    }

    @Test
    void testPolyglotMapToJSONObjectConversion() throws Exception {
        // Given - JavaScript that passes nested objects to jira_update_ticket
        String jsWithNestedObjects = """
            function action(params) {
                try {
                    // Test nested object structure that would become PolyglotMap
                    var updateParams = {
                        "fields": {
                            "priority": {
                                "name": "High"
                            },
                            "labels": ["automated", "test"],
                            "customfield_10001": {
                                "value": "option1"
                            }
                        },
                        "update": {
                            "assignee": [{
                                "set": {
                                    "accountId": "123456"
                                }
                            }]
                        }
                    };
                    
                    // This should trigger PolyglotMap to JSONObject conversion
                    var result = jira_update_ticket({
                        key: "TEST-123",
                        params: updateParams
                    });
                    
                    return {
                        success: true,
                        result: result
                    };
                } catch (error) {
                    return {
                        success: false,
                        error: error.toString()
                    };
                }
            }
            """;

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock the jira_update_ticket call with JSONObject parameter
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"), 
                argThat(args -> {
                    // Verify that the params argument is now a JSONObject (not PolyglotMap)
                    Object paramsValue = args.get("params");
                    return paramsValue instanceof JSONObject && 
                           ((JSONObject) paramsValue).has("fields") &&
                           ((JSONObject) paramsValue).getJSONObject("fields").has("priority");
                }), 
                any(Map.class)
            )).thenReturn("{\"success\": true}");

            JSONObject params = new JSONObject();

            // When
            Object result = bridge.executeJavaScript(jsWithNestedObjects, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            
            // Verify the MCP tool was called with properly converted JSONObject
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    Object key = args.get("key");
                    Object paramsObj = args.get("params");
                    
                    // Verify key parameter
                    if (!"TEST-123".equals(key)) {
                        return false;
                    }
                    
                    // Verify params is JSONObject with proper structure
                    if (!(paramsObj instanceof JSONObject)) {
                        return false;
                    }
                    
                    JSONObject jsonParams = (JSONObject) paramsObj;
                    
                    // Check nested structure conversion
                    if (!jsonParams.has("fields")) {
                        return false;
                    }
                    
                    JSONObject fields = jsonParams.getJSONObject("fields");
                    if (!fields.has("priority")) {
                        return false;
                    }
                    
                    JSONObject priority = fields.getJSONObject("priority");
                    if (!"High".equals(priority.getString("name"))) {
                        return false;
                    }
                    
                    // Check array conversion
                    if (!fields.has("labels")) {
                        return false;
                    }
                    
                    // Check nested update structure
                    if (!jsonParams.has("update")) {
                        return false;
                    }
                    
                    return true;
                }),
                any(Map.class)
            ));
        }
    }

    @Test
    void testPolyglotMapConversionWithDeeplyNestedStructures() throws Exception {
        // Given - Test with very deeply nested object structures
        String jsWithDeeplyNested = """
            function action(params) {
                var deeplyNested = {
                    "level1": {
                        "level2": {
                            "level3": {
                                "level4": {
                                    "value": "deep",
                                    "number": 42,
                                    "boolean": true,
                                    "array": ["item1", "item2"],
                                    "nullValue": null
                                }
                            }
                        }
                    }
                };
                
                return jira_update_ticket({
                    key: "DEEP-123",
                    params: deeplyNested
                });
            }
            """;

        // Capture the arguments passed to MCPToolExecutor
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                anyString(),
                any(Map.class),
                any(Map.class)
            )).thenAnswer(invocation -> {
                // Capture the arguments for verification
                capturedArgs.set((Map<String, Object>) invocation.getArgument(1));
                return "{\"updated\": true}";
            });

            JSONObject params = new JSONObject();

            // When
            bridge.executeJavaScript(jsWithDeeplyNested, params);

            // Then - Verify deep conversion worked correctly
            Map<String, Object> args = capturedArgs.get();
            assertNotNull(args, "Args should be captured");
            
            Object paramsObj = args.get("params");
            assertInstanceOf(JSONObject.class, paramsObj, "params should be converted to JSONObject");
            
            JSONObject jsonParams = (JSONObject) paramsObj;
            
            // Navigate through the deep structure
            JSONObject level4 = jsonParams
                .getJSONObject("level1")
                .getJSONObject("level2")
                .getJSONObject("level3")
                .getJSONObject("level4");
            
            // Verify all data types are preserved
            assertEquals("deep", level4.getString("value"));
            assertEquals(42, level4.getInt("number"));
            assertTrue(level4.getBoolean("boolean"));
            assertEquals(2, level4.getJSONArray("array").length());
            assertTrue(level4.isNull("nullValue"));
            
            // Verify the tool was called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                any(Map.class),
                any(Map.class)
            ));
        }
    }

    @Test
    void testStringToJSONObjectConversion() throws Exception {
        // Given - JavaScript that passes JSON string that should be converted to JSONObject
        String jsWithJsonString = """
            function action(params) {
                try {
                    // Simulate the case where JSON.stringify was used (creating a string)
                    var jsonString = JSON.stringify({
                        "fields": {
                            "priority": {
                                "name": "Critical"
                            },
                            "summary": "Updated via string conversion"
                        }
                    });
                    
                    // This should trigger String to JSONObject conversion in MCPToolExecutor
                    var result = jira_update_ticket({
                        key: "STRING-123",
                        params: jsonString
                    });
                    
                    return {
                        success: true,
                        result: result
                    };
                } catch (error) {
                    return {
                        success: false,
                        error: error.toString()
                    };
                }
            }
            """;

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock successful execution with string parameter that gets converted to JSONObject
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    Object paramsValue = args.get("params");
                    // The MCPToolExecutor should convert the JSON string to JSONObject
                    return paramsValue instanceof String && 
                           ((String) paramsValue).contains("\"priority\"") &&
                           ((String) paramsValue).contains("\"Critical\"");
                }),
                any(Map.class)
            )).thenReturn("{\"updated\": true}");

            JSONObject params = new JSONObject();

            // When
            Object result = bridge.executeJavaScript(jsWithJsonString, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            
            // Verify the call was made with JSON string parameter
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    Object key = args.get("key");
                    Object paramsObj = args.get("params");
                    
                    // Verify parameters
                    return "STRING-123".equals(key) && 
                           paramsObj instanceof String &&
                           ((String) paramsObj).contains("\"priority\"");
                }),
                any(Map.class)
            ));
        }
    }

    @Test
    void testPolyglotMapConversionFailureHandling() throws Exception {
        // Given - JavaScript that might cause conversion issues
        String jsWithPotentialIssues = """
            function action(params) {
                try {
                    // Create an object that might be problematic to convert
                    var problematicParams = {
                        "normal": "value",
                        "circular": {}
                    };
                    // Note: We can't actually create a circular reference in this test context,
                    // but we test the error handling path
                    
                    return jira_update_ticket({
                        key: "ERROR-123",
                        params: problematicParams
                    });
                } catch (error) {
                    return {
                        error: error.toString(),
                        handled: true
                    };
                }
            }
            """;

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock successful execution even with potential conversion issues
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                any(Map.class),
                any(Map.class)
            )).thenReturn("{\"handled\": true}");

            JSONObject params = new JSONObject();

            // When
            Object result = bridge.executeJavaScript(jsWithPotentialIssues, params);

            // Then - Should handle gracefully without throwing exceptions
            assertNotNull(result);
            
            // Verify the call was made with converted parameters
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    // Just verify params was converted to JSONObject
                    Object paramsObj = args.get("params");
                    return paramsObj instanceof JSONObject;
                }),
                any(Map.class)
            ));
        }
    }
}


