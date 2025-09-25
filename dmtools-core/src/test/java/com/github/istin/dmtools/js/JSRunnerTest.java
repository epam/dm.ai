package com.github.istin.dmtools.js;

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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JSRunner
 */
class JSRunnerTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private AI mockAI;

    @Mock
    private Confluence mockConfluence;

    @Mock
    private SourceCode mockSourceCode;

    private JSRunner jsRunner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jsRunner = new JSRunner();
        
        // Inject mocked dependencies
        jsRunner.trackerClient = mockTrackerClient;
        jsRunner.ai = mockAI;
        jsRunner.confluence = mockConfluence;
        jsRunner.sourceCodes = List.of(mockSourceCode);
    }

    @Test
    void testBasicJavaScriptExecution() throws Exception {
        // Given
        String simpleJS = """
            function action(params) {
                return {
                    success: true,
                    message: "JavaScript executed successfully",
                    ticket: params.ticket,
                    response: params.response
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(simpleJS);
        params.setTicket(Map.of("key", "TEST-123", "summary", "Test ticket"));
        params.setResponse("AI response");
        params.setInitiator("test@example.com");

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("TEST-123"));
    }

    @Test
    void testJavaScriptWithMCPToolCall() throws Exception {
        // Given
        String jsWithMCPCall = """
            function action(params) {
                try {
                    var ticket = jira_get_ticket({
                        key: params.ticket.key,
                        fields: ["summary", "status"]
                    });
                    
                    return {
                        success: true,
                        ticketRetrieved: ticket,
                        originalTicket: params.ticket
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
            // Mock jira_get_ticket
            JSONObject mockTicketResponse = new JSONObject();
            mockTicketResponse.put("key", "TEST-123");
            mockTicketResponse.put("summary", "Test ticket");
            mockTicketResponse.put("status", "Open");
            
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_get_ticket"),
                argThat(args -> "TEST-123".equals(args.get("key"))),
                any(Map.class)
            )).thenReturn(mockTicketResponse.toString());

            JSRunner.JSParams params = new JSRunner.JSParams();
            params.setJsPath(jsWithMCPCall);
            params.setTicket(Map.of("key", "TEST-123"));
            params.setInitiator("test@example.com");

            // When
            Object result = jsRunner.runJobImpl(params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            
            // Verify MCP tool was called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_get_ticket"),
                argThat(args -> "TEST-123".equals(args.get("key"))),
                any(Map.class)
            ));
        }
    }

    @Test
    void testJavaScriptWithPriorityUpdate() throws Exception {
        // Given
        String priorityUpdateJS = """
            function action(params) {
                try {
                    var updateResult = jira_update_ticket({
                        key: params.ticket.key,
                        params: {
                            "update": {
                                "priority": [{
                                    "set": {
                                        "name": "High"
                                    }
                                }]
                            }
                        }
                    });
                    
                    return {
                        success: true,
                        updateResult: updateResult,
                        message: "Priority updated to High"
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
            // Mock jira_update_ticket
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    Object paramsObj = args.get("params");
                    return "TEST-123".equals(args.get("key")) && 
                           paramsObj instanceof JSONObject &&
                           ((JSONObject) paramsObj).has("update");
                }),
                any(Map.class)
            )).thenReturn("{\"success\": true}");

            JSRunner.JSParams params = new JSRunner.JSParams();
            params.setJsPath(priorityUpdateJS);
            params.setTicket(Map.of("key", "TEST-123"));
            params.setInitiator("test@example.com");

            // When
            Object result = jsRunner.runJobImpl(params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            assertTrue(resultStr.contains("Priority updated"));
            
            // Verify the update was called with correct parameters
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"),
                argThat(args -> {
                    Object key = args.get("key");
                    Object paramsObj = args.get("params");
                    
                    if (!"TEST-123".equals(key)) {
                        return false;
                    }
                    
                    if (!(paramsObj instanceof JSONObject)) {
                        return false;
                    }
                    
                    JSONObject jsonParams = (JSONObject) paramsObj;
                    if (!jsonParams.has("update")) {
                        return false;
                    }
                    
                    JSONObject update = jsonParams.getJSONObject("update");
                    if (!update.has("priority")) {
                        return false;
                    }
                    
                    return true;
                }),
                any(Map.class)
            ));
        }
    }

    @Test
    void testParameterPassing() throws Exception {
        // Given
        String paramTestJS = """
            function action(params) {
                return {
                    receivedJobParams: params.jobParams,
                    receivedTicket: params.ticket,
                    receivedResponse: params.response,
                    receivedInitiator: params.initiator,
                    allParamsKeys: Object.keys(params)
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(paramTestJS);
        params.setJobParams(Map.of("testParam", "testValue"));
        params.setTicket(Map.of("key", "TEST-123", "summary", "Test"));
        params.setResponse("AI analysis result");
        params.setInitiator("developer@company.com");

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("testValue"));
        assertTrue(resultStr.contains("TEST-123"));
        assertTrue(resultStr.contains("AI analysis result"));
        assertTrue(resultStr.contains("developer@company.com"));
        assertTrue(resultStr.contains("jobParams"));
        assertTrue(resultStr.contains("ticket"));
        assertTrue(resultStr.contains("response"));
        assertTrue(resultStr.contains("initiator"));
    }

    @Test
    void testErrorHandling() throws Exception {
        // Given
        String errorJS = """
            function action(params) {
                throw new Error("Intentional test error");
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(errorJS);
        params.setTicket(Map.of("key", "TEST-123"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then - JSRunner should return error in JSON format, not throw exception
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("false")); // success: false
        assertTrue(resultStr.contains("error")); // error field present
        assertTrue(resultStr.contains("Intentional test error")); // Original error message
    }

    @Test
    void testMissingActionFunction() throws Exception {
        // Given
        String invalidJS = """
            function notAction(params) {
                return {success: true};
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(invalidJS);
        params.setTicket(Map.of("key", "TEST-123"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then - JSRunner should return error in JSON format, not throw exception
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("false")); // success: false
        assertTrue(resultStr.contains("error")); // error field present
        assertTrue(resultStr.contains("action")); // error about missing action function
    }

    @Test
    void testEmptyJSPath() {
        // Given
        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath("");
        params.setTicket(Map.of("key", "TEST-123"));

        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            jsRunner.runJobImpl(params);
        });
        
        assertEquals("jsPath parameter is required", exception.getMessage());
    }

    @Test
    void testNullJSPath() {
        // Given
        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(null);
        params.setTicket(Map.of("key", "TEST-123"));

        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            jsRunner.runJobImpl(params);
        });
        
        assertEquals("jsPath parameter is required", exception.getMessage());
    }

    @Test
    void testComplexJavaScriptWithMultipleMCPCalls() throws Exception {
        // Given
        String complexJS = """
            function action(params) {
                var results = {
                    operations: [],
                    success: true
                };
                
                try {
                    // Get ticket
                    var ticket = jira_get_ticket({
                        key: params.ticket.key,
                        fields: ["summary", "status"]
                    });
                    results.operations.push({operation: "get_ticket", success: true});
                    
                    // Update priority
                    var updateResult = jira_update_ticket({
                        key: params.ticket.key,
                        params: {
                            "update": {
                                "priority": [{"set": {"name": "Medium"}}]
                            }
                        }
                    });
                    results.operations.push({operation: "update_priority", success: true});
                    
                    // Post comment
                    var commentResult = jira_post_comment({
                        key: params.ticket.key,
                        comment: "Priority updated by JSRunner test"
                    });
                    results.operations.push({operation: "post_comment", success: true});
                    
                    results.totalOperations = results.operations.length;
                    return results;
                    
                } catch (error) {
                    results.success = false;
                    results.error = error.toString();
                    return results;
                }
            }
            """;

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock all MCP calls
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_get_ticket"), any(Map.class), any(Map.class)
            )).thenReturn("{\"key\": \"TEST-123\", \"summary\": \"Test\"}");
            
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"), any(Map.class), any(Map.class)
            )).thenReturn("{\"success\": true}");
            
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            )).thenReturn("{\"id\": \"123\", \"body\": \"Priority updated by JSRunner test\"}");

            JSRunner.JSParams params = new JSRunner.JSParams();
            params.setJsPath(complexJS);
            params.setTicket(Map.of("key", "TEST-123"));
            params.setInitiator("test@example.com");

            // When
            Object result = jsRunner.runJobImpl(params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            assertTrue(resultStr.contains("totalOperations"));
            assertTrue(resultStr.contains("3")); // Should have 3 operations
            
            // Verify all MCP tools were called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_get_ticket"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testJSParamsSettersAndGetters() {
        // Given
        JSRunner.JSParams params = new JSRunner.JSParams();
        
        String jsPath = "test.js";
        Object jobParams = Map.of("key", "value");
        Object ticket = Map.of("key", "TEST-123");
        Object response = "AI response";
        String initiator = "test@example.com";

        // When
        params.setJsPath(jsPath);
        params.setJobParams(jobParams);
        params.setTicket(ticket);
        params.setResponse(response);
        params.setInitiator(initiator);

        // Then
        assertEquals(jsPath, params.getJsPath());
        assertEquals(jobParams, params.getJobParams());
        assertEquals(ticket, params.getTicket());
        assertEquals(response, params.getResponse());
        assertEquals(initiator, params.getInitiator());
    }
}
