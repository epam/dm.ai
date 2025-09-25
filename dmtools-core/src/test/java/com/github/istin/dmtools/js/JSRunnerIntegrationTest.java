package com.github.istin.dmtools.js;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for JSRunner with file operations
 */
class JSRunnerIntegrationTest {

    @TempDir
    Path tempDir;

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
    void testExecuteJavaScriptFromFile() throws Exception {
        // Given - Create a test JavaScript file
        String jsContent = """
            function action(params) {
                return {
                    success: true,
                    message: "Executed from file",
                    ticket: params.ticket.key,
                    fileTest: true
                };
            }
            """;
        
        Path jsFile = tempDir.resolve("test-script.js");
        Files.writeString(jsFile, jsContent);

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsFile.toString());
        params.setTicket(Map.of("key", "FILE-123", "summary", "File test"));
        params.setInitiator("file-test@example.com");

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("Executed from file"));
        assertTrue(resultStr.contains("FILE-123"));
        assertTrue(resultStr.contains("fileTest"));
    }

    @Test
    void testExecuteComplexJavaScriptFromFile() throws Exception {
        // Given - Create a complex test script similar to the one we use in production
        String complexJSContent = """
            function normalizePriority(priority) {
                const allowed = ['Highest', 'High', 'Medium', 'Low', 'Lowest'];
                const normalized = String(priority).trim().toLowerCase();
                const matched = allowed.find(value => value.toLowerCase() === normalized);
                return matched || 'Medium';
            }

            function processQuestionTickets(response, parentKey) {
                if (!response || !Array.isArray(response)) {
                    return [];
                }
                
                const projectKey = parentKey.split('-')[0];
                const createdTickets = [];
                
                response.forEach(function(question, index) {
                    if (!question || typeof question !== 'object') {
                        return;
                    }
                    
                    const summary = question.summary || ('Question #' + (index + 1));
                    const priority = normalizePriority(question.priority);
                    
                    try {
                        const result = jira_create_ticket_with_parent({
                            project: projectKey,
                            issueType: 'Subtask',
                            summary: summary,
                            description: question.description || 'Follow template from instructions.',
                            parentKey: parentKey
                        });
                        
                        // Extract ticket key from result
                        let createdKey = null;
                        if (result && typeof result === 'string') {
                            try {
                                const parsed = JSON.parse(result);
                                createdKey = parsed.key;
                            } catch (e) {
                                // Ignore parse errors
                            }
                        }
                        
                        if (createdKey && priority) {
                            jira_update_ticket({
                                key: createdKey,
                                params: {
                                    "update": {
                                        "priority": [{
                                            "set": {
                                                "name": priority
                                            }
                                        }]
                                    }
                                }
                            });
                        }
                        
                        createdTickets.push({
                            summary: summary,
                            priority: priority,
                            key: createdKey,
                            success: true
                        });
                        
                    } catch (error) {
                        createdTickets.push({
                            summary: summary,
                            priority: priority,
                            error: error.toString(),
                            success: false
                        });
                    }
                });
                
                return createdTickets;
            }

            function action(params) {
                try {
                    const ticketKey = params.ticket.key;
                    const questions = JSON.parse(params.response);
                    
                    console.log("Processing ticket:", ticketKey);
                    const createdQuestionTickets = processQuestionTickets(questions, ticketKey);
                    
                    return {
                        success: true,
                        message: `Processed ${createdQuestionTickets.length} questions for ${ticketKey}`,
                        createdQuestions: createdQuestionTickets,
                        totalCreated: createdQuestionTickets.filter(q => q.success).length,
                        totalFailed: createdQuestionTickets.filter(q => !q.success).length
                    };
                    
                } catch (error) {
                    console.error("Processing failed:", error);
                    return {
                        success: false,
                        error: error.toString(),
                        message: "Question processing failed"
                    };
                }
            }
            """;
        
        Path jsFile = tempDir.resolve("complex-script.js");
        Files.writeString(jsFile, complexJSContent);

        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            // Mock ticket creation
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_with_parent"), any(Map.class), any(Map.class)
            )).thenReturn("{\"key\": \"COMPLEX-456\", \"id\": \"12345\"}");
            
            // Mock priority update
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"), any(Map.class), any(Map.class)
            )).thenReturn("{\"success\": true}");

            JSRunner.JSParams params = new JSRunner.JSParams();
            params.setJsPath(jsFile.toString());
            params.setTicket(Map.of("key", "COMPLEX-123", "summary", "Complex test"));
            params.setResponse("[{\"summary\":\"Test question\",\"priority\":\"High\",\"description\":\"Test description\"}]");
            params.setInitiator("complex-test@example.com");

            // When
            Object result = jsRunner.runJobImpl(params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("success"));
            assertTrue(resultStr.contains("Processed 1 questions"));
            assertTrue(resultStr.contains("COMPLEX-123"));
            assertTrue(resultStr.contains("totalCreated"));
            
            // Verify MCP calls were made
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_create_ticket_with_parent"), any(Map.class), any(Map.class)
            ));
            mcpMock.verify(() -> MCPToolExecutor.executeTool(
                eq("jira_update_ticket"), any(Map.class), any(Map.class)
            ));
        }
    }

    @Test
    void testFileNotFound() throws Exception {
        // Given
        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath("/non/existent/file.js");
        params.setTicket(Map.of("key", "ERROR-123"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then - JSRunner should return error in JSON format, not throw exception
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("false")); // success: false
        assertTrue(resultStr.contains("error")); // error field present
        assertTrue(resultStr.contains("JavaScript file not found")); // Error about missing file
    }

    @Test
    void testMalformedJavaScriptFile() throws Exception {
        // Given - Create a malformed JavaScript file
        String malformedJS = """
            function action(params) {
                // Missing closing brace
                return {
                    success: true
            """;
        
        Path jsFile = tempDir.resolve("malformed.js");
        Files.writeString(jsFile, malformedJS);

        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath(jsFile.toString());
        params.setTicket(Map.of("key", "ERROR-123"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then - JSRunner should return error in JSON format, not throw exception
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("false")); // success: false
        assertTrue(resultStr.contains("error")); // error field present
        // Should contain syntax error information
        assertTrue(resultStr.contains("SyntaxError") || resultStr.contains("execution failed"));
    }

    @Test
    void testResourceFilePath() throws Exception {
        // This test would require setting up classpath resources
        // For now, we'll test the path handling logic
        
        JSRunner.JSParams params = new JSRunner.JSParams();
        params.setJsPath("classpath:non-existent-resource.js");
        params.setTicket(Map.of("key", "RESOURCE-123"));

        // When
        Object result = jsRunner.runJobImpl(params);

        // Then - JSRunner should return error in JSON format, not throw exception
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("success"));
        assertTrue(resultStr.contains("false")); // success: false
        assertTrue(resultStr.contains("error")); // error field present
        assertTrue(resultStr.contains("JavaScript file not found") || resultStr.contains("execution failed"));
    }
}
