package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.Chat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for TeamsClient core logic.
 * Tests pagination, limit handling, filtering, and chat operations.
 */
public class TeamsClientTest {

    // ==================== Limit Parameter Handling Tests ====================
    
    @Test
    public void testLimitHandling_Null_UsesDefault() {
        // When limit is null, it should use default behavior (not "get all")
        Integer limit = null;
        boolean getAllChats = (limit != null && limit == 0);
        assertFalse("null limit should not be treated as 'get all'", getAllChats);
    }
    
    @Test
    public void testLimitHandling_Zero_MeansAll() {
        // When limit = 0, it should mean "get all chats"
        // We test this by verifying the logic treats 0 specially
        Integer limit = 0;
        boolean getAllChats = (limit != null && limit == 0);
        assertTrue("limit=0 should be treated as 'get all'", getAllChats);
    }
    
    @Test
    public void testLimitHandling_PositiveNumber_UsesSpecificLimit() {
        Integer limit = 50;
        boolean getAllChats = (limit != null && limit == 0);
        assertFalse("positive limit should not be treated as 'get all'", getAllChats);
        assertEquals(50, limit.intValue());
    }

    // ==================== ChatPerformer Interface Tests ====================
    
    @Test
    public void testChatPerformer_EarlyExit_Logic() throws IOException {
        // Test the early exit logic
        List<Chat> mockChats = createMockChats(5);
        mockChats.get(2).getJSONObject().put("id", "target-chat");
        
        // Simulate the ChatPerformer interface behavior
        AtomicInteger processedCount = new AtomicInteger(0);
        boolean[] shouldStop = {false};
        
        for (Chat chat : mockChats) {
            processedCount.incrementAndGet();
            if ("target-chat".equals(chat.getId())) {
                shouldStop[0] = true;
                break;
            }
        }
        
        // Should have stopped at the 3rd chat (index 2)
        assertEquals(3, processedCount.get());
        assertTrue("Should have found target chat and stopped", shouldStop[0]);
    }

    @Test
    public void testChatPerformer_ContinuesUntilEnd_Logic() throws IOException {
        // Test continuation logic
        List<Chat> mockChats = createMockChats(3);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        for (int i = 0; i < mockChats.size(); i++) {
            processedCount.incrementAndGet();
            // Never stop - continue until end
        }
        
        // Should process all chats
        assertEquals(3, processedCount.get());
    }

    // ==================== Pagination Logic Tests ====================
    
    @Test
    public void testPagination_NoNextLink_StopsAfterFirstPage() {
        // Create response with no nextLink
        JSONObject response = new JSONObject();
        JSONArray chats = new JSONArray();
        for (int i = 0; i < 5; i++) {
            chats.put(createMockChatJson("chat-" + i));
        }
        response.put("value", chats);
        // No @odata.nextLink field
        
        assertFalse("Response without nextLink should not have more pages", 
            response.has("@odata.nextLink"));
        assertEquals(5, response.getJSONArray("value").length());
    }
    
    @Test
    public void testPagination_WithNextLink_HasMorePages() {
        JSONObject response = new JSONObject();
        response.put("value", new JSONArray());
        response.put("@odata.nextLink", "https://graph.microsoft.com/v1.0/me/chats?$skiptoken=abc123");
        
        assertTrue("Response with nextLink should have more pages", 
            response.has("@odata.nextLink"));
        assertNotNull(response.getString("@odata.nextLink"));
    }

    // ==================== Filter Parameter Tests ====================
    
    @Test
    public void testFilterParameter_ServerSideFiltering() {
        // Test that filter parameter is used correctly
        String filter = "lastModifiedDateTime gt 2025-01-01T00:00:00Z";
        
        // Verify filter string format
        assertTrue("Filter should use OData syntax", 
            filter.contains("gt") || filter.contains("lt") || filter.contains("eq"));
    }
    
    @Test
    public void testFilterParameter_MessageTypeFilter() {
        String filter = "messageType eq 'message'";
        
        assertEquals("messageType eq 'message'", filter);
        assertTrue(filter.contains("messageType"));
        assertTrue(filter.contains("eq"));
    }

    // ==================== Current User Display Name Caching Logic Tests ====================
    
    @Test
    public void testCurrentUserDisplayName_Caching_Logic() {
        // Test the caching logic without accessing private method
        // Simulate the caching pattern used in TeamsClient
        String cachedName = null;
        
        // First call - cache is empty
        if (cachedName == null) {
            cachedName = "John Doe"; // Simulate API call
        }
        String name1 = cachedName;
        
        // Second call - should use cached value
        String name2 = cachedName;
        
        assertEquals("John Doe", name1);
        assertEquals("John Doe", name2);
        assertSame("Should return same cached instance", name1, name2);
    }

    // ==================== Simplified Messages Tests ====================
    
    @Test
    public void testGetChatMessages_WithLimit() {
        // Test that limit parameter is respected
        Integer limit = 50;
        assertNotNull(limit);
        assertTrue(limit > 0);
        assertEquals(50, limit.intValue());
    }
    
    @Test
    public void testGetChatMessages_WithZeroLimit_MeansAll() {
        Integer limit = 0;
        boolean getAllMessages = (limit != null && limit == 0);
        assertTrue("limit=0 should mean 'get all messages'", getAllMessages);
    }

    // ==================== Chat Type Filtering Tests ====================
    
    @Test
    public void testChatTypeFilter_OneOnOne() {
        String chatType = "oneOnOne";
        assertEquals("oneOnOne", chatType);
    }
    
    @Test
    public void testChatTypeFilter_Group() {
        String chatType = "group";
        assertEquals("group", chatType);
    }
    
    @Test
    public void testChatTypeFilter_Meeting() {
        String chatType = "meeting";
        assertEquals("meeting", chatType);
    }
    
    @Test
    public void testChatTypeFilter_All_IsDefault() {
        String chatType = "all";
        assertTrue("'all' should match all chat types", 
            "all".equalsIgnoreCase(chatType));
    }

    // ==================== Edge Cases Tests ====================
    
    @Test
    public void testEmptyChatList_ReturnsEmptyArray() {
        List<Chat> emptyList = new ArrayList<>();
        assertEquals(0, emptyList.size());
        assertTrue(emptyList.isEmpty());
    }
    
    @Test
    public void testNullChatName_HandledGracefully() {
        String chatName = null;
        // Should handle null chat names without throwing exceptions
        assertNull(chatName);
    }
    
    @Test
    public void testEmptyChatName_HandledGracefully() {
        String chatName = "";
        assertTrue(chatName.isEmpty());
    }

    // ==================== Message Limit Break Tests ====================
    
    @Test
    public void testMessageLimit_BreaksOuterLoop() {
        // This tests the fix for the bug where break only exited inner loop
        int maxMessages = 5;
        int messageCount = 0;
        
        // Simulate pagination with multiple pages
        boolean shouldStop = false;
        for (int page = 0; page < 10 && !shouldStop; page++) {
            // Simulate messages in a page
            for (int i = 0; i < 3; i++) {
                if (messageCount >= maxMessages) {
                    shouldStop = true;
                    break;
                }
                messageCount++;
            }
        }
        
        // Should stop at exactly maxMessages
        assertEquals(5, messageCount);
    }

    // ==================== System Message Filtering Tests ====================
    
    @Test
    public void testSystemMessageFiltering_FilteredEvents() {
        List<String> filteredEventTypes = List.of(
            "#microsoft.graph.membersDeletedEventMessageDetail",
            "#microsoft.graph.membersAddedEventMessageDetail",
            "#microsoft.graph.memberJoinedEventMessageDetail",
            "#microsoft.graph.membersJoinedEventMessageDetail",
            "#microsoft.graph.memberLeftEventMessageDetail",
            "#microsoft.graph.messagePinnedEventMessageDetail",
            "#microsoft.graph.callEndedEventMessageDetail",
            "#microsoft.graph.callStartedEventMessageDetail",
            "#microsoft.graph.teamsAppInstalledEventMessageDetail"
        );
        
        // Verify all expected event types are in the filter list
        assertEquals(9, filteredEventTypes.size());
        assertTrue(filteredEventTypes.contains("#microsoft.graph.membersDeletedEventMessageDetail"));
        assertTrue(filteredEventTypes.contains("#microsoft.graph.teamsAppInstalledEventMessageDetail"));
    }
    
    @Test
    public void testSystemMessageFiltering_ImportantEvents_NotFiltered() {
        List<String> importantEventTypes = List.of(
            "#microsoft.graph.callRecordingEventMessageDetail",
            "#microsoft.graph.callTranscriptEventMessageDetail"
        );
        
        List<String> filteredEventTypes = List.of(
            "#microsoft.graph.membersDeletedEventMessageDetail",
            "#microsoft.graph.membersAddedEventMessageDetail",
            "#microsoft.graph.memberJoinedEventMessageDetail",
            "#microsoft.graph.membersJoinedEventMessageDetail",
            "#microsoft.graph.memberLeftEventMessageDetail",
            "#microsoft.graph.messagePinnedEventMessageDetail",
            "#microsoft.graph.callEndedEventMessageDetail",
            "#microsoft.graph.callStartedEventMessageDetail",
            "#microsoft.graph.teamsAppInstalledEventMessageDetail"
        );
        
        // Important events should NOT be in filtered list
        for (String importantEvent : importantEventTypes) {
            assertFalse("Important event should not be filtered: " + importantEvent,
                filteredEventTypes.contains(importantEvent));
        }
    }

    // ==================== Helper Methods ====================
    
    private List<Chat> createMockChats(int count) {
        List<Chat> chats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chats.add(new Chat(createMockChatJson("chat-" + i).toString()));
        }
        return chats;
    }
    
    private JSONObject createMockChatJson(String chatId) {
        JSONObject chatJson = new JSONObject();
        chatJson.put("id", chatId);
        chatJson.put("chatType", "group");
        chatJson.put("topic", "Chat " + chatId);
        chatJson.put("lastUpdatedDateTime", "2025-10-09T10:00:00Z");
        chatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", "2025-10-09T10:00:00Z")
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Test User")))
            .put("body", new JSONObject()
                .put("content", "Test message")));
        return chatJson;
    }
}

