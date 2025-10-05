package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaScriptExecutorTest {

    private TrackerClient<?> mockTrackerClient;
    private AI mockAI;
    private Confluence mockConfluence;
    private SourceCode mockSourceCode;
    private ITicket mockTicket;

    @BeforeEach
    void setUp() {
        mockTrackerClient = Mockito.mock(TrackerClient.class);
        mockAI = Mockito.mock(AI.class);
        mockConfluence = Mockito.mock(Confluence.class);
        mockSourceCode = Mockito.mock(SourceCode.class);
        mockTicket = Mockito.mock(ITicket.class);
    }

    @Test
    void testConstructor() {
        JavaScriptExecutor executor = new JavaScriptExecutor("console.log('test')");
        assertNotNull(executor);
    }

    @Test
    void testMcpConfiguration() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .mcp(mockTrackerClient, mockAI, mockConfluence, mockSourceCode);
        
        assertNotNull(executor);
    }

    @Test
    void testWithJobContext() {
        Object jobParams = new Object();
        Object ticket = new Object();
        Object response = "Response";
        
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .withJobContext(jobParams, ticket, response);
        
        assertNotNull(executor);
    }

    @Test
    void testWithCustomParameter() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("customKey", "customValue");
        
        assertNotNull(executor);
    }

    @Test
    void testFluentAPI() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .mcp(mockTrackerClient, mockAI, mockConfluence, mockSourceCode)
            .withJobContext(new Object(), mockTicket, "response")
            .with("key1", "value1")
            .with("key2", 123);
        
        assertNotNull(executor);
    }

    @Test
    void testExecute_NullCode() throws Exception {
        JavaScriptExecutor executor = new JavaScriptExecutor(null);
        Object result = executor.execute();
        assertNull(result);
    }

    @Test
    void testExecute_EmptyCode() throws Exception {
        JavaScriptExecutor executor = new JavaScriptExecutor("");
        Object result = executor.execute();
        assertNull(result);
    }

    @Test
    void testExecute_WhitespaceCode() throws Exception {
        JavaScriptExecutor executor = new JavaScriptExecutor("   ");
        Object result = executor.execute();
        assertNull(result);
    }

    @Test
    void testWithNullParameter() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("nullKey", null);
        
        assertNotNull(executor);
    }

    @Test
    void testWithStringParameter() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("stringKey", "string value");
        
        assertNotNull(executor);
    }

    @Test
    void testWithNumberParameter() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("intKey", 42)
            .with("doubleKey", 3.14);
        
        assertNotNull(executor);
    }

    @Test
    void testWithBooleanParameter() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("boolKey", true);
        
        assertNotNull(executor);
    }

    @Test
    void testWithJSONObjectParameter() {
        JSONObject json = new JSONObject();
        json.put("key", "value");
        
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("jsonKey", json);
        
        assertNotNull(executor);
    }

    @Test
    void testWithTicketParameter() throws Exception {
        when(mockTicket.getTicketKey()).thenReturn("TEST-123");
        when(mockTicket.getTicketTitle()).thenReturn("Test Title");
        when(mockTicket.getTicketDescription()).thenReturn("Test Description");
        when(mockTicket.getStatus()).thenReturn("Open");
        when(mockTicket.getIssueType()).thenReturn("Bug");
        when(mockTicket.getPriority()).thenReturn("High");
        
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("ticket", mockTicket);
        
        assertNotNull(executor);
    }

    @Test
    void testMultipleParameters() {
        JavaScriptExecutor executor = new JavaScriptExecutor("test")
            .with("param1", "value1")
            .with("param2", 123)
            .with("param3", true)
            .with("param4", null);
        
        assertNotNull(executor);
    }

    @Test
    void testExecutorChaining() {
        JavaScriptExecutor executor = new JavaScriptExecutor("console.log('test')")
            .mcp(mockTrackerClient, mockAI, mockConfluence, mockSourceCode)
            .withJobContext("params", "ticket", "response")
            .with("extra", "value");
        
        assertNotNull(executor);
    }
}
