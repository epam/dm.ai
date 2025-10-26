package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for MCP tool transformation and parameter passing
 */
class MCPToolTransformationTest {

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
    void testAIToolsParameterTransformation() throws Exception {
        // Test both AI tools
        String jsWithBothAI = """
            function action(params) {
                var geminiResult = gemini_ai_chat("Hello Gemini");
                var dialResult = dial_ai_chat("Hello Dial");
                return {
                    gemini: geminiResult,
                    dial: dialResult
                };
            }
            """;
        
        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("gemini_ai_chat"), any(Map.class), any(Map.class)
            )).thenReturn("Gemini response");

            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("dial_ai_chat"), any(Map.class), any(Map.class)
            )).thenReturn("Dial response");

            // When
            Object result = bridge.executeJavaScript(jsWithBothAI, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains("Gemini response"));
            assertTrue(result.toString().contains("Dial response"));

            // Verify parameter transformation for AI tools
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("gemini_ai_chat"),
                argThat(args -> "Hello Gemini".equals(((Map<String, Object>) args).get("message"))),
                any(Map.class)
            ));

            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("dial_ai_chat"),
                argThat(args -> "Hello Dial".equals(((Map<String, Object>) args).get("message"))),
                any(Map.class)
            ));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jira_create_ticket_basic",
        "jira_post_comment", 
        "jira_update_field",
        "jira_link_issues",
        "jira_move_to_status"
    })
    void testJiraToolsTransformation(String toolName) throws Exception {
        // Generic test for various Jira tools
        String jsTemplate = """
            function action(params) {
                var result = %s("param1", "param2", "param3", "param4");
                return { tool: "%s", result: result };
            }
            """;
        
        String js = String.format(jsTemplate, toolName, toolName);
        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq(toolName), any(Map.class), any(Map.class)
            )).thenReturn("Tool executed successfully");

            // When
            Object result = bridge.executeJavaScript(js, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains(toolName));
            
            // Verify tool was called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq(toolName), any(Map.class), any(Map.class)
            ));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "confluence_create_page",
        "confluence_update_page",
        "confluence_content_by_title",
        "confluence_find_content",
        "confluence_get_children_by_id"
    })
    void testConfluenceToolsTransformation(String toolName) throws Exception {
        // Generic test for various Confluence tools
        String jsTemplate = """
            function action(params) {
                var result = %s("param1", "param2", "param3");
                return { tool: "%s", result: result };
            }
            """;
        
        String js = String.format(jsTemplate, toolName, toolName);
        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq(toolName), any(Map.class), any(Map.class)
            )).thenReturn("Confluence tool executed");

            // When
            Object result = bridge.executeJavaScript(js, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains(toolName));
            
            // Verify tool was called
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq(toolName), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testComplexParameterTransformation() throws Exception {
        // Test parameter transformation for complex objects
        String jsWithComplexParams = """
            function action(params) {
                // Test object parameter transformation
                var ticket = jira_create_ticket_basic(
                    params.project,
                    params.issueType,
                    params.summary,
                    params.description
                );
                
                // Test array/object field updates
                var updateResult = jira_update_field(
                    ticket.key, 
                    "customField", 
                    {
                        value: params.fieldValue,
                        nested: { data: "test" }
                    }
                );
                
                return {
                    ticketKey: ticket.key,
                    updateResult: updateResult
                };
            }
            """;
        
        JSONObject params = new JSONObject();
        params.put("project", "TEST");
        params.put("issueType", "Story");
        params.put("summary", "Test Summary");
        params.put("description", "Test Description");
        params.put("fieldValue", "Custom Value");

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock ticket creation
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenReturn(Map.of("key", "TEST-123"));

            // Mock field update
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_field"), any(Map.class), any(Map.class)
            )).thenReturn("Field updated");

            // When
            Object result = bridge.executeJavaScript(jsWithComplexParams, params);

            // Then
            assertNotNull(result);
            assertTrue(result.toString().contains("TEST-123"));

            // Verify parameter transformation for ticket creation
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"),
                argThat(args -> {
                    Map<String, Object> argsMap = (Map<String, Object>) args;
                    return "TEST".equals(argsMap.get("project")) &&
                           "Story".equals(argsMap.get("issueType")) &&
                           "Test Summary".equals(argsMap.get("summary")) &&
                           "Test Description".equals(argsMap.get("description"));
                }),
                any(Map.class)
            ));

            // Verify parameter transformation for field update
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_field"),
                argThat(args -> {
                    Map<String, Object> argsMap = (Map<String, Object>) args;
                    return "TEST-123".equals(argsMap.get("key")) &&
                           "customField".equals(argsMap.get("field"));
                }),
                any(Map.class)
            ));
        }
    }

    @Test
    void testToolExecutionOrderAndState() throws Exception {
        // Test that tool executions maintain state and order
        String jsWithState = """
            function action(params) {
                var results = [];
                
                // Create multiple tickets in sequence
                for (var i = 0; i < 3; i++) {
                    var ticket = jira_create_ticket_basic(
                        "PROJ", 
                        "Task", 
                        "Task " + i, 
                        "Description " + i
                    );
                    results.push(ticket.key);
                    
                    // Comment on each ticket
                    jira_post_comment(ticket.key, "Created task " + i);
                }
                
                return {
                    createdTickets: results,
                    count: results.length
                };
            }
            """;
        
        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock sequential ticket creation
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenReturn(Map.of("key", "PROJ-1"))
              .thenReturn(Map.of("key", "PROJ-2"))
              .thenReturn(Map.of("key", "PROJ-3"));

            // Mock comment posting
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            )).thenReturn("Comment posted");

            // When
            Object result = bridge.executeJavaScript(jsWithState, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("PROJ-1"));
            assertTrue(resultStr.contains("PROJ-2"));
            assertTrue(resultStr.contains("PROJ-3"));
            assertTrue(resultStr.contains("3")); // count

            // Verify all tools were called the correct number of times
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            ), times(3));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_post_comment"), any(Map.class), any(Map.class)
            ), times(3));
        }
    }

    @Test
    void testErrorPropagationFromMCPTools() throws Exception {
        // Test that MCP tool errors are properly propagated to JavaScript
        String jsWithErrorHandling = """
            function action(params) {
                try {
                    var ticket = jira_create_ticket_basic("INVALID", "BadType", "", "");
                    return { success: true, ticket: ticket };
                } catch (error) {
                    return { 
                        success: false, 
                        error: error.message || "Tool execution failed",
                        attempted: "jira_create_ticket_basic"
                    };
                }
            }
            """;
        
        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenThrow(new RuntimeException("Invalid project INVALID"));

            // When
            Object result = bridge.executeJavaScript(jsWithErrorHandling, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("false")); // success: false
            assertTrue(resultStr.contains("jira_create_ticket_basic")); // attempted tool
        }
    }

    @Test
    void testMissingRequiredParameter_throwsHelpfulError() throws Exception {
        String jsMissingParam = """
            function action(params) {
                // Missing required 'project' argument
                return jira_create_ticket_basic(null, 'Task', 'Summary', 'Desc');
            }
            """;

        JSONObject params = new JSONObject();

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_basic"), any(Map.class), any(Map.class)
            )).thenThrow(new IllegalArgumentException("Required parameter 'project' is missing"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> bridge.executeJavaScript(jsMissingParam, params));
            assertTrue(ex.getMessage().contains("Required parameter 'project' is missing"));
        }
    }
}
