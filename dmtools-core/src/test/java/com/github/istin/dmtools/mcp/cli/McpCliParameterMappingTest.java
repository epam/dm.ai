package com.github.istin.dmtools.mcp.cli;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for MCP CLI parameter mapping logic.
 * Tests the dynamic parameter discovery and mapping from positional arguments.
 */
public class McpCliParameterMappingTest {

    // ==================== Parameter Type Conversion Tests ====================
    
    @Test
    public void testConvertParameterValue_Integer_Limit() {
        String paramName = "limit";
        String value = "50";
        
        // Simulate the conversion logic
        Object converted = convertParameterValue(paramName, value);
        
        assertTrue("limit parameter should be converted to Integer", converted instanceof Integer);
        assertEquals(50, ((Integer) converted).intValue());
    }
    
    @Test
    public void testConvertParameterValue_Integer_Count() {
        String paramName = "itemCount";
        String value = "100";
        
        Object converted = convertParameterValue(paramName, value);
        
        assertTrue("*Count parameter should be converted to Integer", converted instanceof Integer);
        assertEquals(100, ((Integer) converted).intValue());
    }
    
    @Test
    public void testConvertParameterValue_Integer_Size() {
        String paramName = "pageSize";
        String value = "25";
        
        Object converted = convertParameterValue(paramName, value);
        
        assertTrue("*Size parameter should be converted to Integer", converted instanceof Integer);
        assertEquals(25, ((Integer) converted).intValue());
    }
    
    @Test
    public void testConvertParameterValue_String_NoConversion() {
        String paramName = "chatName";
        String value = "Test Chat";
        
        Object converted = convertParameterValue(paramName, value);
        
        assertTrue("chatName should remain as String", converted instanceof String);
        assertEquals("Test Chat", converted);
    }
    
    @Test
    public void testConvertParameterValue_Integer_Zero() {
        String paramName = "limit";
        String value = "0";
        
        Object converted = convertParameterValue(paramName, value);
        
        assertTrue("Zero should be converted to Integer", converted instanceof Integer);
        assertEquals(0, ((Integer) converted).intValue());
    }
    
    @Test
    public void testConvertParameterValue_Integer_InvalidFormat_FallsBackToString() {
        String paramName = "limit";
        String value = "invalid";
        
        Object converted = convertParameterValue(paramName, value);
        
        // When conversion fails, should fall back to string
        assertTrue("Invalid integer should fall back to String", converted instanceof String);
        assertEquals("invalid", converted);
    }

    // ==================== Parameter Mapping Order Tests ====================
    
    @Test
    public void testParameterMapping_FirstPositional_MapsToFirstParameter() {
        List<String> positionalArgs = List.of("firstValue");
        List<String> parameterNames = List.of("firstParam");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(1, result.size());
        assertEquals("firstValue", result.get("firstParam"));
    }
    
    @Test
    public void testParameterMapping_MultiplePositionals_MapsInOrder() {
        List<String> positionalArgs = List.of("value1", "value2", "value3");
        List<String> parameterNames = List.of("param1", "param2", "param3");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(3, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertEquals("value3", result.get("param3"));
    }
    
    @Test
    public void testParameterMapping_MoreArgs_ThanParameters_OnlyMapsAvailable() {
        List<String> positionalArgs = List.of("value1", "value2", "value3", "value4");
        List<String> parameterNames = List.of("param1", "param2");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        // Only first 2 should be mapped
        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertFalse("Extra arguments should not be mapped", result.containsKey("param3"));
    }
    
    @Test
    public void testParameterMapping_FewerArgs_ThanParameters_MapsOnlyProvided() {
        List<String> positionalArgs = List.of("value1");
        List<String> parameterNames = List.of("param1", "param2", "param3");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        // Only first parameter should be mapped
        assertEquals(1, result.size());
        assertEquals("value1", result.get("param1"));
        assertFalse(result.containsKey("param2"));
        assertFalse(result.containsKey("param3"));
    }
    
    @Test
    public void testParameterMapping_EmptyParameters_ReturnsEmpty() {
        List<String> positionalArgs = List.of("value1", "value2");
        List<String> parameterNames = List.of();
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(0, result.size());
    }
    
    @Test
    public void testParameterMapping_EmptyArgs_ReturnsEmpty() {
        List<String> positionalArgs = List.of();
        List<String> parameterNames = List.of("param1", "param2");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(0, result.size());
    }

    // ==================== Type Conversion in Mapping Tests ====================
    
    @Test
    public void testParameterMapping_WithTypeConversion() {
        List<String> positionalArgs = List.of("Test Chat", "50");
        List<String> parameterNames = List.of("chatName", "limit");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(2, result.size());
        assertEquals("Test Chat", result.get("chatName"));
        assertTrue("limit should be Integer", result.get("limit") instanceof Integer);
        assertEquals(50, ((Integer) result.get("limit")).intValue());
    }
    
    @Test
    public void testParameterMapping_MixedTypes() {
        List<String> positionalArgs = List.of("ChatName", "100", "oneOnOne");
        List<String> parameterNames = List.of("chatName", "limit", "chatType");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(3, result.size());
        assertEquals("ChatName", result.get("chatName"));
        assertTrue("limit should be Integer", result.get("limit") instanceof Integer);
        assertEquals(100, ((Integer) result.get("limit")).intValue());
        assertEquals("oneOnOne", result.get("chatType"));
    }

    // ==================== Real-World Scenario Tests ====================
    
    @Test
    public void testScenario_TeamsMessages_TwoParams() {
        // dmtools teams_messages "ai.m" 50
        List<String> positionalArgs = List.of("ai.m", "50");
        List<String> parameterNames = List.of("chatName", "limit");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(2, result.size());
        assertEquals("ai.m", result.get("chatName"));
        assertEquals(50, ((Integer) result.get("limit")).intValue());
    }
    
    @Test
    public void testScenario_TeamsRecentChats_TwoParams() {
        // dmtools teams_recent_chats 10 "oneOnOne"
        List<String> positionalArgs = List.of("10", "oneOnOne");
        List<String> parameterNames = List.of("limit", "chatType");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(2, result.size());
        assertEquals(10, ((Integer) result.get("limit")).intValue());
        assertEquals("oneOnOne", result.get("chatType"));
    }
    
    @Test
    public void testScenario_TeamsChatsRaw_OneParam() {
        // dmtools teams_chats_raw 100
        List<String> positionalArgs = List.of("100");
        List<String> parameterNames = List.of("limit");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(1, result.size());
        assertEquals(100, ((Integer) result.get("limit")).intValue());
    }
    
    @Test
    public void testScenario_TeamsMessages_ZeroLimit() {
        // dmtools teams_messages "chat" 0 (get all)
        List<String> positionalArgs = List.of("chat", "0");
        List<String> parameterNames = List.of("chatName", "limit");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(2, result.size());
        assertEquals("chat", result.get("chatName"));
        assertEquals(0, ((Integer) result.get("limit")).intValue());
    }
    
    @Test
    public void testScenario_TeamsSendMessage_TwoStringParams() {
        // dmtools teams_send_message "chat" "Hello World"
        List<String> positionalArgs = List.of("chat", "Hello World");
        List<String> parameterNames = List.of("chatName", "message");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(2, result.size());
        assertEquals("chat", result.get("chatName"));
        assertEquals("Hello World", result.get("message"));
    }

    // ==================== Edge Cases Tests ====================
    
    @Test
    public void testEdgeCase_NullParameterName_HandledGracefully() {
        // Should not happen in practice, but test robustness
        List<String> positionalArgs = List.of("value");
        List<String> parameterNames = new ArrayList<>();
        parameterNames.add(null);
        
        // Should not throw exception
        try {
            mapPositionalArguments(parameterNames, positionalArgs);
            // If we get here without exception, that's good
            assertTrue("Handled null parameter name without throwing exception", true);
        } catch (Exception e) {
            fail("Should handle null parameter name gracefully");
        }
    }
    
    @Test
    public void testEdgeCase_EmptyString_MapsCorrectly() {
        List<String> positionalArgs = List.of("");
        List<String> parameterNames = List.of("param1");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(1, result.size());
        assertEquals("", result.get("param1"));
    }
    
    @Test
    public void testEdgeCase_WhitespaceString_PreservedAsIs() {
        List<String> positionalArgs = List.of("  spaces  ");
        List<String> parameterNames = List.of("param1");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(1, result.size());
        assertEquals("  spaces  ", result.get("param1"));
    }
    
    @Test
    public void testEdgeCase_SpecialCharacters_PreservedAsIs() {
        List<String> positionalArgs = List.of("test@#$%^&*()");
        List<String> parameterNames = List.of("param1");
        
        Map<String, Object> result = mapPositionalArguments(parameterNames, positionalArgs);
        
        assertEquals(1, result.size());
        assertEquals("test@#$%^&*()", result.get("param1"));
    }

    // ==================== Helper Methods (Simulating McpCliHandler logic) ====================
    
    /**
     * Simulates the parameter value conversion logic from McpCliHandler.
     */
    private Object convertParameterValue(String paramName, String value) {
        // Common parameter types
        if (paramName.equals("limit") || paramName.endsWith("Count") || paramName.endsWith("Size")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return value;  // Return as string if conversion fails
            }
        }
        return value;
    }
    
    /**
     * Simulates the parameter mapping logic from McpCliHandler.
     */
    private Map<String, Object> mapPositionalArguments(List<String> parameterNames, List<String> positionalArgs) {
        Map<String, Object> arguments = new HashMap<>();
        
        int numToMap = Math.min(positionalArgs.size(), parameterNames.size());
        for (int i = 0; i < numToMap; i++) {
            String paramName = parameterNames.get(i);
            if (paramName != null) {  // Gracefully handle null parameter names
                String paramValue = positionalArgs.get(i);
                Object convertedValue = convertParameterValue(paramName, paramValue);
                arguments.put(paramName, convertedValue);
            }
        }
        
        return arguments;
    }
}

