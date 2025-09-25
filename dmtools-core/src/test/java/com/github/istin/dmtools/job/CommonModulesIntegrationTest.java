package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the common JavaScript modules (jiraHelpers.js and aiResponseParser.js)
 */
class CommonModulesIntegrationTest {

    @Mock
    private TrackerClient<?> trackerClient;
    
    @Mock
    private AI ai;
    
    @Mock
    private Confluence confluence;
    
    @Mock
    private SourceCode sourceCode;
    
    private JobJavaScriptBridge bridge;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(trackerClient, ai, confluence, sourceCode);
        
        // Set up common directory structure
        try {
            setupCommonModules();
        } catch (Exception e) {
            fail("Failed to setup common modules: " + e.getMessage());
        }
    }

    private void setupCommonModules() throws Exception {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir);
        
        // Create jiraHelpers.js module
        Path jiraHelpersFile = commonDir.resolve("jiraHelpers.js");
        Files.writeString(jiraHelpersFile, """
            function assignForReview(ticketKey, initiatorId) {
                return {
                    success: true,
                    message: "Ticket " + ticketKey + " assigned to " + initiatorId
                };
            }
            
            function extractTicketKey(result) {
                if (!result) return null;
                if (typeof result === 'string') {
                    try {
                        const parsed = JSON.parse(result);
                        return parsed && parsed.key ? parsed.key : null;
                    } catch (error) {
                        return null;
                    }
                }
                if (typeof result === 'object' && typeof result.key === 'string') {
                    return result.key;
                }
                return null;
            }
            
            function setTicketPriority(ticketKey, priority) {
                console.log('Set priority ' + priority + ' on ticket ' + ticketKey);
                return true;
            }
            
            module.exports = {
                assignForReview: assignForReview,
                extractTicketKey: extractTicketKey,
                setTicketPriority: setTicketPriority
            };
            """);

        // Create aiResponseParser.js module
        Path aiResponseParserFile = commonDir.resolve("aiResponseParser.js");
        Files.writeString(aiResponseParserFile, """
            function parseQuestionsResponse(response) {
                // Handle string responses (parse JSON)
                if (typeof response === 'string') {
                    try {
                        response = JSON.parse(response);
                    } catch (error) {
                        console.error('Invalid JSON from AI:', error);
                        return [];
                    }
                }
                
                // Expect array format
                if (!Array.isArray(response)) {
                    console.warn('AI response is not an array, got:', typeof response);
                    return [];
                }
                
                return response; // Trust AI to provide correct format
            }
            
            function buildSummary(summary, index) {
                const text = summary || 'Follow-up question #' + (index + 1);
                return text.length <= 120 ? text : text.slice(0, 117) + '...';
            }
            
            function buildDescription(question) {
                return question.description || 'Follow template from instructions.';
            }
            
            module.exports = {
                parseQuestionsResponse: parseQuestionsResponse,
                buildSummary: buildSummary,
                buildDescription: buildDescription
            };
            """);
    }

    @Test
    void testJiraHelpersModule() throws Exception {
        Path testScript = tempDir.resolve("testJiraHelpers.js");
        Files.writeString(testScript, """
            const { assignForReview, extractTicketKey, setTicketPriority } = require('./common/jiraHelpers.js');
            
            function action(params) {
                // Test assignForReview
                const assignResult = assignForReview("TEST-123", "user123");
                
                // Test extractTicketKey with different formats
                const keyFromString = extractTicketKey('{"key": "PROJ-456", "summary": "Test"}');
                const keyFromObject = extractTicketKey({key: "PROJ-789", summary: "Another test"});
                const keyFromNull = extractTicketKey(null);
                
                // Test setTicketPriority
                const priorityResult = setTicketPriority("TEST-123", "High");
                
                return {
                    success: true,
                    assignResult: assignResult,
                    keyFromString: keyFromString,
                    keyFromObject: keyFromObject,
                    keyFromNull: keyFromNull,
                    priorityResult: priorityResult
                };
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        
        // Verify assignForReview result
        JSONObject assignResult = jsonResult.getJSONObject("assignResult");
        assertTrue(assignResult.getBoolean("success"));
        assertEquals("Ticket TEST-123 assigned to user123", assignResult.getString("message"));
        
        // Verify extractTicketKey results
        assertEquals("PROJ-456", jsonResult.getString("keyFromString"));
        assertEquals("PROJ-789", jsonResult.getString("keyFromObject"));
        assertTrue(jsonResult.isNull("keyFromNull"));
        
        // Verify setTicketPriority result
        assertTrue(jsonResult.getBoolean("priorityResult"));
    }

    @Test
    void testAiResponseParserModule() throws Exception {
        Path testScript = tempDir.resolve("testAiResponseParser.js");
        Files.writeString(testScript, """
            const { 
                parseQuestionsResponse, 
                buildSummary, 
                buildDescription 
            } = require('./common/aiResponseParser.js');
            
            function action(params) {
                try {
                    // Test parseQuestionsResponse with valid JSON array
                    const questions1 = parseQuestionsResponse('[{"summary": "Test Q", "priority": "Low", "description": "Test desc"}]');
                    const questions2 = parseQuestionsResponse([{"summary": "Direct array", "priority": "High", "description": "Direct test"}]);
                    const questions3 = parseQuestionsResponse(null);
                    const questions4 = parseQuestionsResponse("invalid json");
                    
                    // Test buildSummary
                    const summary1 = buildSummary("Short summary", 0);
                    const summary2 = buildSummary("", 1);
                    const longSummary = "This is a very long summary that exceeds the 120 character limit and should be truncated with ellipsis at the end because it is too long for Jira";
                    const summary3 = buildSummary(longSummary, 2);
                    
                    // Test buildDescription
                    const desc1 = buildDescription({description: "Detailed description"});
                    const desc2 = buildDescription({description: ""});
                    const desc3 = buildDescription({});
                    
                    return {
                        success: true,
                        questions1: questions1,
                        questions2: questions2,
                        questions3: questions3,
                        questions4: questions4,
                        summary1: summary1,
                        summary2: summary2,
                        summary3: summary3,
                        desc1: desc1,
                        desc2: desc2,
                        desc3: desc3
                    };
                } catch (error) {
                    return {
                        success: false,
                        error: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        
        // Verify parseQuestionsResponse
        assertEquals(1, jsonResult.getJSONArray("questions1").length());
        assertEquals(1, jsonResult.getJSONArray("questions2").length());
        assertEquals(0, jsonResult.getJSONArray("questions3").length());
        assertEquals(0, jsonResult.getJSONArray("questions4").length()); // Invalid JSON returns empty array
        
        // Verify buildSummary
        assertEquals("Short summary", jsonResult.getString("summary1"));
        assertEquals("Follow-up question #2", jsonResult.getString("summary2"));
        assertTrue(jsonResult.getString("summary3").endsWith("..."));
        assertTrue(jsonResult.getString("summary3").length() <= 120);
        
        // Verify buildDescription (simplified - just returns description or fallback)
        assertEquals("Detailed description", jsonResult.getString("desc1"));
        assertEquals("Follow template from instructions.", jsonResult.getString("desc2"));
        assertEquals("Follow template from instructions.", jsonResult.getString("desc3"));
    }

    @Test
    void testRefactoredAssignForReviewScript() throws Exception {
        // Test the simplified assignForReview.js that uses common modules
        Path assignForReviewScript = tempDir.resolve("assignForReview.js");
        Files.writeString(assignForReviewScript, """
            const { assignForReview } = require('./common/jiraHelpers.js');
            
            function action(params) {
                try {
                    const ticketKey = params.ticket.key;
                    const initiatorId = params.initiator;
                    
                    return assignForReview(ticketKey, initiatorId);
                    
                } catch (error) {
                    return {
                        success: false,
                        error: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        params.put("ticket", new JSONObject().put("key", "PROJ-123"));
        params.put("initiator", "user456");
        
        Object result = bridge.executeJavaScript(assignForReviewScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertEquals("Ticket PROJ-123 assigned to user456", jsonResult.getString("message"));
    }

    @Test
    void testModularCreateQuestionsAndAssignForReview() throws Exception {
        // Test a simplified version of createQuestionsAndAssignForReview.js that uses common modules
        Path createQuestionsScript = tempDir.resolve("createQuestionsAndAssignForReview.js");
        Files.writeString(createQuestionsScript, """
            const { assignForReview, extractTicketKey } = require('./common/jiraHelpers.js');
            const { parseQuestionsResponse, buildSummary, buildDescription } = require('./common/aiResponseParser.js');
            
            function processQuestionTickets(response, parentKey) {
                const questions = parseQuestionsResponse(response);
                if (questions.length === 0) {
                    return [];
                }
                
                const createdTickets = [];
                questions.forEach(function(question, index) {
                    const summary = buildSummary(question.summary, index);
                    const description = buildDescription(question);
                    
                    // Mock ticket creation (in real scenario would call jira_create_ticket_with_parent)
                    const mockResult = '{"key": "' + parentKey.split('-')[0] + '-' + (1000 + index) + '"}';
                    const createdKey = extractTicketKey(mockResult);
                    
                    createdTickets.push({
                        summary: summary,
                        priority: question.priority,
                        key: createdKey
                    });
                });
                
                return createdTickets;
            }
            
            function action(params) {
                try {
                    const ticketKey = params.ticket.key;
                    const initiatorId = params.initiator;
                    
                    const createdQuestionTickets = processQuestionTickets(params.response, ticketKey);
                    const assignResult = assignForReview(ticketKey, initiatorId);
                    
                    if (!assignResult.success) {
                        return assignResult;
                    }
                    
                    return {
                        success: true,
                        message: "Ticket " + ticketKey + " assigned, moved to In Review, created " + createdQuestionTickets.length + " question subtasks",
                        createdQuestions: createdQuestionTickets
                    };
                    
                } catch (error) {
                    return {
                        success: false,
                        error: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        params.put("ticket", new JSONObject().put("key", "PROJ-123"));
        params.put("initiator", "user456");
        params.put("response", "[{\"summary\": \"Test question\", \"priority\": \"High\", \"description\": \"Test description\"}]");
        
        Object result = bridge.executeJavaScript(createQuestionsScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertTrue(jsonResult.getString("message").contains("created 1 question subtasks"));
        assertEquals(1, jsonResult.getJSONArray("createdQuestions").length());
        
        JSONObject createdQuestion = jsonResult.getJSONArray("createdQuestions").getJSONObject(0);
        assertEquals("Test question", createdQuestion.getString("summary"));
        assertEquals("High", createdQuestion.getString("priority"));
        assertEquals("PROJ-1000", createdQuestion.getString("key"));
    }

    /**
     * Helper method to convert JavaScript execution result to JSONObject
     */
    private JSONObject toJSONObject(Object result) {
        if (result instanceof String) {
            return new JSONObject((String) result);
        } else if (result instanceof JSONObject) {
            return (JSONObject) result;
        } else {
            throw new IllegalArgumentException("Expected String or JSONObject, got: " + result.getClass());
        }
    }
}
