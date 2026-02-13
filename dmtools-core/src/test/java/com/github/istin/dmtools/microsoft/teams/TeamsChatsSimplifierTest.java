package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.Chat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for TeamsChatsSimplifier utility class.
 */
public class TeamsChatsSimplifierTest {
    
    private Chat groupChat;
    private Chat oneOnOneChat;
    private Chat unreadChat;
    private Chat oldChat;
    private Chat systemEventChat;
    
    @Before
    public void setUp() {
        // Use recent dates (within last 7 days) to avoid cutoff filter issues
        String recentDate1 = Instant.now().minus(5, ChronoUnit.DAYS).toString();
        String recentDate2 = Instant.now().minus(4, ChronoUnit.DAYS).toString();
        String recentDate3 = Instant.now().minus(3, ChronoUnit.DAYS).toString();

        // Group chat with topic
        JSONObject groupChatJson = new JSONObject();
        groupChatJson.put("id", "chat-1");
        groupChatJson.put("chatType", "group");
        groupChatJson.put("topic", "Project Team");
        groupChatJson.put("lastUpdatedDateTime", recentDate1);
        groupChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", recentDate1)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Alice")))
            .put("body", new JSONObject()
                .put("content", "This is a test message about the project")));
        groupChat = new Chat(groupChatJson.toString());

        // One-on-one chat (no topic, use member names)
        JSONObject oneOnOneChatJson = new JSONObject();
        oneOnOneChatJson.put("id", "chat-2");
        oneOnOneChatJson.put("chatType", "oneOnOne");
        oneOnOneChatJson.put("lastUpdatedDateTime", recentDate2);
        oneOnOneChatJson.put("members", new JSONArray()
            .put(new JSONObject()
                .put("displayName", "Bob")
                .put("userId", "user-1"))
            .put(new JSONObject()
                .put("displayName", "Charlie")
                .put("userId", "user-2")));
        oneOnOneChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", recentDate2)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Bob")))
            .put("body", new JSONObject()
                .put("content", "Hey, how are you?")));
        oneOnOneChat = new Chat(oneOnOneChatJson.toString());

        // Chat with unread messages
        JSONObject unreadChatJson = new JSONObject();
        unreadChatJson.put("id", "chat-3");
        unreadChatJson.put("chatType", "group");
        unreadChatJson.put("topic", "Urgent Discussion");
        unreadChatJson.put("lastUpdatedDateTime", recentDate3);
        String readDate = Instant.now().minus(4, ChronoUnit.DAYS).toString(); // Read before last update
        unreadChatJson.put("viewpoint", new JSONObject()
            .put("lastMessageReadDateTime", readDate)); // Read time before last updated
        unreadChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", recentDate3)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "David")))
            .put("body", new JSONObject()
                .put("content", "Please respond ASAP!")));
        unreadChat = new Chat(unreadChatJson.toString());
        
        // Old chat (91 days ago)
        String oldDate = Instant.now().minus(91, ChronoUnit.DAYS).toString();
        JSONObject oldChatJson = new JSONObject();
        oldChatJson.put("id", "chat-4");
        oldChatJson.put("chatType", "group");
        oldChatJson.put("topic", "Old Project");
        oldChatJson.put("lastUpdatedDateTime", oldDate);
        oldChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", oldDate)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Eve")))
            .put("body", new JSONObject()
                .put("content", "This is an old message")));
        oldChat = new Chat(oldChatJson.toString());
        
        // System event chat (empty content)
        JSONObject systemChatJson = new JSONObject();
        systemChatJson.put("id", "chat-5");
        systemChatJson.put("chatType", "meeting");
        systemChatJson.put("topic", "Weekly Standup");
        systemChatJson.put("lastUpdatedDateTime", "2025-10-09T13:00:00Z");
        systemChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", "2025-10-09T13:00:00Z")
            .put("messageType", "systemEventMessage")
            .put("body", new JSONObject()
                .put("content", "")));  // Empty content
        systemEventChat = new Chat(systemChatJson.toString());
    }
    
    @Test
    public void testSimplifyChat_GroupChat() {
        JSONObject result = TeamsChatsSimplifier.simplifyChat(groupChat, false, "Bob");

        assertNotNull(result);
        assertEquals("Project Team", result.getString("chatName"));
        assertTrue(result.getString("lastMessage").contains("This is a test message"));
        // Verify lastUpdated is a valid ISO date string (not checking exact value since it's dynamic)
        String lastUpdated = result.getString("lastUpdated");
        assertNotNull("lastUpdated should not be null", lastUpdated);
        assertTrue("lastUpdated should be in ISO format", lastUpdated.matches("\\d{4}-\\d{2}-\\d{2}T.*"));
        assertFalse(result.has("new")); // Not unread
    }
    
    @Test
    public void testSimplifyChat_OneOnOne() {
        JSONObject result = TeamsChatsSimplifier.simplifyChat(oneOnOneChat, false, "Bob");

        assertNotNull(result);
        // For 1-on-1, should show only the OTHER person (not the one who sent last message)
        // Last message was from "Bob", so we should see "Charlie"
        assertEquals("Charlie", result.getString("chatName"));
        assertEquals("Hey, how are you?", result.getString("lastMessage"));
        // Verify lastUpdated is a valid ISO date string (not checking exact value since it's dynamic)
        String lastUpdated = result.getString("lastUpdated");
        assertNotNull("lastUpdated should not be null", lastUpdated);
        assertTrue("lastUpdated should be in ISO format", lastUpdated.matches("\\d{4}-\\d{2}-\\d{2}T.*"));
        assertFalse(result.has("new"));
    }
    
    @Test
    public void testSimplifyChat_WithAuthor() {
        JSONObject result = TeamsChatsSimplifier.simplifyChat(groupChat, true, "Bob");
        
        assertNotNull(result);
        assertEquals("Project Team", result.getString("chatName"));
        // Should have separate author and lastMessage fields
        assertEquals("Alice", result.getString("author"));
        String lastMessage = result.getString("lastMessage");
        assertFalse(lastMessage.startsWith("Alice: ")); // Author should NOT be in message
        assertTrue(lastMessage.contains("This is a test message"));
    }
    
    @Test
    public void testSimplifyChat_Unread() {
        JSONObject result = TeamsChatsSimplifier.simplifyChat(unreadChat, false, "Bob");
        
        assertNotNull(result);
        assertEquals("Urgent Discussion", result.getString("chatName"));
        assertTrue(result.has("new"));
        assertTrue(result.getBoolean("new"));
    }
    
    @Test
    public void testSimplifyChat_LongMessage_Truncated() {
        // Create chat with long message (150 chars)
        String longMessage = "a".repeat(150);
        JSONObject chatJson = new JSONObject();
        chatJson.put("id", "chat-long");
        chatJson.put("chatType", "group");
        chatJson.put("topic", "Long Message Chat");
        chatJson.put("lastUpdatedDateTime", "2025-10-09T10:00:00Z");
        chatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", "2025-10-09T10:00:00Z")
            .put("body", new JSONObject()
                .put("content", longMessage)));
        Chat longChat = new Chat(chatJson.toString());
        
        JSONObject result = TeamsChatsSimplifier.simplifyChat(longChat, false, "Bob");
        
        assertNotNull(result);
        assertEquals("Long Message Chat", result.getString("chatName"));
        String lastMessage = result.getString("lastMessage");
        assertTrue(lastMessage.endsWith("..."));
        assertEquals(103, lastMessage.length()); // 100 chars + "..."
    }
    
    @Test
    public void testSimplifyChats_MultipleChats() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);
        chats.add(oneOnOneChat);
        chats.add(unreadChat);
        
        JSONArray result = TeamsChatsSimplifier.simplifyChats(chats, "Bob");
        
        assertEquals(3, result.length());
        assertEquals("Project Team", result.getJSONObject(0).getString("chatName"));
        // For 1-on-1 chat, should show only the other person
        assertEquals("Charlie", result.getJSONObject(1).getString("chatName"));
        assertEquals("Urgent Discussion", result.getJSONObject(2).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_BasicSorting() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);   // 2025-10-09T10:00:00Z
        chats.add(oneOnOneChat); // 2025-10-09T11:00:00Z
        chats.add(unreadChat);   // 2025-10-09T12:00:00Z
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 90, "Bob");
        
        assertEquals(3, result.length());
        // Should be sorted by most recent first
        assertEquals("Urgent Discussion", result.getJSONObject(0).getString("chatName"));
        // For 1-on-1, should show only the other person
        assertEquals("Charlie", result.getJSONObject(1).getString("chatName"));
        assertEquals("Project Team", result.getJSONObject(2).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_WithLimit() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);
        chats.add(oneOnOneChat);
        chats.add(unreadChat);
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 2, "all", 90, "Bob");
        
        assertEquals(2, result.length());
        assertEquals("Urgent Discussion", result.getJSONObject(0).getString("chatName"));
        assertEquals("Charlie", result.getJSONObject(1).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_FilterByType() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);    // group
        chats.add(oneOnOneChat); // oneOnOne
        chats.add(unreadChat);   // group
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "group", 90, "Bob");
        
        assertEquals(2, result.length());
        assertEquals("Urgent Discussion", result.getJSONObject(0).getString("chatName"));
        assertEquals("Project Team", result.getJSONObject(1).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_FilterByTypeOneOnOne() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);    // group
        chats.add(oneOnOneChat); // oneOnOne
        chats.add(unreadChat);   // group
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "oneOnOne", 90, "Bob");
        
        assertEquals(1, result.length());
        assertEquals("Charlie", result.getJSONObject(0).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_ExcludeOldChats() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);   // Recent
        chats.add(oneOnOneChat); // Recent
        chats.add(oldChat);      // 91 days ago
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 90, "Bob");
        
        // Old chat should be filtered out
        assertEquals(2, result.length());
        assertEquals("Charlie", result.getJSONObject(0).getString("chatName"));
        assertEquals("Project Team", result.getJSONObject(1).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_ExcludeSystemEvents() {
        // Also test with <systemEventMessage/> placeholder
        JSONObject systemChat2Json = new JSONObject();
        systemChat2Json.put("id", "chat-6");
        systemChat2Json.put("chatType", "meeting");
        systemChat2Json.put("topic", "System Event Chat 2");
        systemChat2Json.put("lastUpdatedDateTime", "2025-10-09T13:00:00Z");
        systemChat2Json.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", "2025-10-09T13:00:00Z")
            .put("messageType", "systemEventMessage")
            .put("body", new JSONObject()
                .put("content", "<systemEventMessage/>")));  // Placeholder content
        Chat systemEventChat2 = new Chat(systemChat2Json.toString());
        
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);
        chats.add(systemEventChat); // System event with empty content
        chats.add(systemEventChat2); // System event with placeholder content
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 90, "Bob");
        
        // Both system event chats should be filtered out
        assertEquals(1, result.length());
        assertEquals("Project Team", result.getJSONObject(0).getString("chatName"));
    }
    
    @Test
    public void testGetRecentChatsSimplified_IncludesAuthorNames() {
        List<Chat> chats = new ArrayList<>();
        chats.add(groupChat);
        
        JSONArray result = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 90, "Bob");
        
        assertEquals(1, result.length());
        JSONObject chat = result.getJSONObject(0);
        // Should have separate author field when includeAuthor=true
        assertEquals("Alice", chat.getString("author"));
        String lastMessage = chat.getString("lastMessage");
        // Author should NOT be in the message itself
        assertFalse(lastMessage.startsWith("Alice: "));
    }
    
    @Test
    public void testGetRecentChatsSimplified_CustomCutoffDays() {
        // Create recent chat (1 day ago) - use dynamic date instead of hardcoded groupChat
        String dateRecent = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        JSONObject recentChatJson = new JSONObject();
        recentChatJson.put("id", "chat-recent");
        recentChatJson.put("chatType", "group");
        recentChatJson.put("topic", "Project Team");
        recentChatJson.put("lastUpdatedDateTime", dateRecent);
        recentChatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", dateRecent)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Alice")))
            .put("body", new JSONObject()
                .put("content", "Recent message")));
        Chat recentChat = new Chat(recentChatJson.toString());
        
        // Create chat 50 days ago
        String date50DaysAgo = Instant.now().minus(50, ChronoUnit.DAYS).toString();
        JSONObject chatJson = new JSONObject();
        chatJson.put("id", "chat-50");
        chatJson.put("chatType", "group");
        chatJson.put("topic", "50 Day Old Chat");
        chatJson.put("lastUpdatedDateTime", date50DaysAgo);
        chatJson.put("lastMessagePreview", new JSONObject()
            .put("createdDateTime", date50DaysAgo)
            .put("from", new JSONObject()
                .put("user", new JSONObject()
                    .put("displayName", "Alice")))
            .put("body", new JSONObject()
                .put("content", "Old message")));
        Chat chat50Days = new Chat(chatJson.toString());
        
        List<Chat> chats = new ArrayList<>();
        chats.add(recentChat); // Recent (1 day ago)
        chats.add(chat50Days); // 50 days ago
        
        // With 90-day cutoff: should include both
        JSONArray result90 = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 90, "Bob");
        assertEquals(2, result90.length());
        
        // With 30-day cutoff: should exclude 50-day-old chat
        JSONArray result30 = TeamsChatsSimplifier.getRecentChatsSimplified(chats, 10, "all", 30, "Bob");
        assertEquals(1, result30.length());
        assertEquals("Project Team", result30.getJSONObject(0).getString("chatName"));
    }
}

