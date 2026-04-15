// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.js;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.model.Metadata;
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

    @Test
    void testInputJqlForwardedToJSContext() throws Exception {
        // Given: inputJql set at TrackerParams level (as it arrives from encoded_config)
        String jsCode = """
            function action(params) {
                return {
                    inputJql: params.inputJql,
                    jobParamsInputJql: params.jobParams ? params.jobParams.inputJql : null,
                    hasInputJql: params.inputJql !== undefined && params.inputJql !== null
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        params.setInputJql("key = PROJ-123");
        params.setJobParams(Map.of("inputJql", "key = DEFAULT-999", "other", "value"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then: params.inputJql in JS should be the TrackerParams-level value
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("PROJ-123"),
                "JS should receive inputJql from TrackerParams level, got: " + resultStr);
        assertTrue(resultStr.contains("hasInputJql"));
        assertTrue(resultStr.contains("true"));
    }

    @Test
    void testMetadataForwardedToJSContext() throws Exception {
        // Given: metadata set at TrackerParams level (as it arrives from encoded_config)
        String jsCode = """
            function action(params) {
                return {
                    hasMetadata: params.metadata !== undefined && params.metadata !== null,
                    agentId: params.metadata ? params.metadata.agentId : null,
                    contextId: params.metadata ? params.metadata.contextId : null
                };
            }
            """;

        Metadata metadata = new Metadata();
        metadata.setAgentId("build_ios_simulator");
        metadata.setContextId("MAPC");

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        params.setMetadata(metadata);

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("hasMetadata"));
        assertTrue(resultStr.contains("true"));
        assertTrue(resultStr.contains("build_ios_simulator"),
                "JS should receive metadata.agentId, got: " + resultStr);
        assertTrue(resultStr.contains("MAPC"),
                "JS should receive metadata.contextId, got: " + resultStr);
    }

    @Test
    void testInputJqlNotForwardedWhenNull() throws Exception {
        // Given: inputJql is NOT set — should not pollute JS context
        String jsCode = """
            function action(params) {
                return {
                    hasInputJql: params.inputJql !== undefined && params.inputJql !== null,
                    hasMetadata: params.metadata !== undefined && params.metadata !== null,
                    keys: Object.keys(params)
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        // inputJql and metadata are NOT set

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then: neither inputJql nor metadata should appear in JS params
        assertNotNull(result);
        String resultStr = result.toString();
        // inputJql and metadata should not be in the keys
        assertFalse(resultStr.contains("\"inputJql\""),
                "inputJql should not be in JS params when null, got: " + resultStr);
    }

    @Test
    void testInputJqlPriorityOverJobParams() throws Exception {
        // Given: inputJql at both TrackerParams level AND inside jobParams
        // This simulates: file config has jobParams.inputJql default,
        // encoded_config adds params.inputJql override
        String jsCode = """
            function action(params) {
                var topLevel = params.inputJql || '';
                var fromJobParams = (params.jobParams && params.jobParams.inputJql) || '';
                return {
                    effectiveJql: topLevel || fromJobParams,
                    topLevelJql: topLevel,
                    jobParamsJql: fromJobParams
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        params.setInputJql("key = OVERRIDE-123");
        params.setJobParams(Map.of("inputJql", "key = DEFAULT-999"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then: effectiveJql should be the top-level override
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("OVERRIDE-123"),
                "Top-level inputJql should take priority, got: " + resultStr);
        // Both values should be present
        assertTrue(resultStr.contains("DEFAULT-999"),
                "jobParams.inputJql should still be accessible, got: " + resultStr);
    }

    // ─── Backward compatibility tests ───────────────────────────────────────

    @Test
    void testBackwardCompat_OldScriptsReadingOnlyJobParams() throws Exception {
        // Old-style scripts that read ONLY from params.jobParams must keep working
        // exactly as before. No top-level inputJql is set (simulates pre-fix dmtools).
        String oldStyleJS = """
            function action(params) {
                var jp = params.jobParams || {};
                var jql = jp.inputJql || 'none';
                return {
                    jql: jql,
                    hasTopLevelJql: params.inputJql !== undefined && params.inputJql !== null
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(oldStyleJS);
        // Only jobParams.inputJql is set — no TrackerParams.inputJql
        params.setJobParams(Map.of("inputJql", "key = LEGACY-456"));

        Object result = jsRunner.runJobImpl(params);

        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("LEGACY-456"),
                "Old scripts should still read jobParams.inputJql, got: " + resultStr);
        // params.inputJql should NOT appear because TrackerParams.inputJql was not set
        assertFalse(resultStr.contains("\"hasTopLevelJql\":true"),
                "Top-level inputJql should be absent when not set, got: " + resultStr);
    }

    @Test
    void testBackwardCompat_ExistingParamKeysUnchanged() throws Exception {
        // Verify that the standard param keys (jobParams, ticket, response, initiator)
        // are still present and nothing existing is removed.
        String jsCode = """
            function action(params) {
                var keys = Object.keys(params).sort();
                return {
                    keys: keys,
                    hasJobParams: 'jobParams' in params,
                    hasTicket: 'ticket' in params,
                    hasResponse: 'response' in params,
                    hasInitiator: 'initiator' in params
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        params.setJobParams(Map.of("key", "val"));
        params.setTicket(Map.of("key", "TEST-1"));
        params.setResponse("resp");
        params.setInitiator("user@example.com");

        Object result = jsRunner.runJobImpl(params);

        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("\"hasJobParams\":true"), "jobParams must be present");
        assertTrue(resultStr.contains("\"hasTicket\":true"), "ticket must be present");
        assertTrue(resultStr.contains("\"hasResponse\":true"), "response must be present");
        assertTrue(resultStr.contains("\"hasInitiator\":true"), "initiator must be present");
    }

    @Test
    void testBackwardCompat_JobParamsNotMutatedByTopLevelFields() throws Exception {
        // The top-level inputJql should NOT overwrite or modify jobParams.inputJql.
        // Both must coexist independently.
        String jsCode = """
            function action(params) {
                var jp = params.jobParams || {};
                return {
                    jobParamsJql: jp.inputJql,
                    topJql: params.inputJql,
                    areEqual: (jp.inputJql === params.inputJql)
                };
            }
            """;

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsCode);
        params.setInputJql("key = ENCODED-789");
        params.setJobParams(Map.of("inputJql", "key = FILE-DEFAULT-111"));

        Object result = jsRunner.runJobImpl(params);

        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("ENCODED-789"), "top-level inputJql present");
        assertTrue(resultStr.contains("FILE-DEFAULT-111"), "jobParams.inputJql intact");
        assertTrue(resultStr.contains("\"areEqual\":false"),
                "top-level and jobParams.inputJql must be independent, got: " + resultStr);
    }
}
