package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.ai.model.Metadata;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AITest {

    private AI mockAI;

    @BeforeEach
    void setUp() {
        mockAI = mock(AI.class);
        // Enable real method for default interface methods
        when(mockAI.normalizeMessageRoles(any(Message[].class))).thenCallRealMethod();
        when(mockAI.roleName()).thenReturn("assistant");
    }

    @Test
    void testNormalizeMessageRoles_WithAssistantRole() {
        Message msg1 = new Message("user", "Hello", null);
        Message msg2 = new Message("assistant", "Hi there", null);
        Message msg3 = new Message("user", "How are you?", null);
        
        Message[] messages = {msg1, msg2, msg3};
        Message[] result = mockAI.normalizeMessageRoles(messages);
        
        assertSame(messages, result);
        assertEquals("user", msg1.getRole());
        assertEquals("assistant", msg2.getRole());
        assertEquals("user", msg3.getRole());
    }

    @Test
    void testNormalizeMessageRoles_WithModelRole() {
        when(mockAI.roleName()).thenReturn("model");
        
        Message msg1 = new Message("user", "Hello", null);
        Message msg2 = new Message("assistant", "Hi there", null);
        Message msg3 = new Message("model", "Hello again", null);
        
        Message[] messages = {msg1, msg2, msg3};
        Message[] result = mockAI.normalizeMessageRoles(messages);
        
        assertSame(messages, result);
        assertEquals("user", msg1.getRole());
        assertEquals("model", msg2.getRole()); // Converted from assistant
        assertEquals("model", msg3.getRole()); // Already model
    }

    @Test
    void testNormalizeMessageRoles_NullMessages() {
        Message[] result = mockAI.normalizeMessageRoles((Message[]) null);
        assertNull(result);
    }

    @Test
    void testNormalizeMessageRoles_EmptyMessages() {
        Message[] messages = {};
        Message[] result = mockAI.normalizeMessageRoles(messages);
        assertSame(messages, result);
        assertEquals(0, result.length);
    }

    @Test
    void testNormalizeMessageRoles_SystemRole() {
        Message msg1 = new Message("system", "System message", null);
        Message msg2 = new Message("user", "User message", null);
        
        Message[] messages = {msg1, msg2};
        Message[] result = mockAI.normalizeMessageRoles(messages);
        
        assertEquals("system", msg1.getRole()); // System role unchanged
        assertEquals("user", msg2.getRole());
    }

    @Test
    void testUtilsChatAsBoolean_True() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat("model-1", "Is this true?")).thenReturn("true");
        
        Boolean result = AI.Utils.chatAsBoolean(mockAI, "model-1", "Is this true?");
        assertTrue(result);
    }

    @Test
    void testUtilsChatAsBoolean_False() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat("model-1", "Is this false?")).thenReturn("false");
        
        Boolean result = AI.Utils.chatAsBoolean(mockAI, "model-1", "Is this false?");
        assertFalse(result);
    }

    @Test
    void testUtilsChatAsBoolean_WithoutModel() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat(null, "Question?")).thenReturn("true");
        
        boolean result = AI.Utils.chatAsBoolean(mockAI, "Question?");
        assertTrue(result);
    }

    @Test
    void testUtilsChatAsJSONArray_WithModel() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat(eq("model-1"), eq("Get array"), (File) isNull())).thenReturn("[1, 2, 3]");
        
        JSONArray result = AI.Utils.chatAsJSONArray(mockAI, "model-1", "Get array");
        assertNotNull(result);
        assertEquals(3, result.length());
    }

    @Test
    void testUtilsChatAsJSONArray_WithoutModel() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat(isNull(), eq("Get array"), (File) isNull())).thenReturn("[\"a\", \"b\"]");
        
        JSONArray result = AI.Utils.chatAsJSONArray(mockAI, "Get array");
        assertNotNull(result);
        assertEquals(2, result.length());
    }

    @Test
    void testUtilsChatAsJSONObject_WithModel() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat(eq("model-1"), eq("Get object"), (File) isNull())).thenReturn("{\"key\": \"value\"}");
        
        JSONObject result = AI.Utils.chatAsJSONObject(mockAI, "model-1", "Get object");
        assertNotNull(result);
        assertEquals("value", result.getString("key"));
    }

    @Test
    void testUtilsChatAsJSONObject_WithoutModel() throws Exception {
        AI mockAI = mock(AI.class);
        when(mockAI.chat(isNull(), eq("Get object"), (File) isNull())).thenReturn("{\"name\": \"test\"}");
        
        JSONObject result = AI.Utils.chatAsJSONObject(mockAI, "Get object");
        assertNotNull(result);
        assertEquals("test", result.getString("name"));
    }
}
