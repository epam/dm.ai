package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.microsoft.common.networking.MicrosoftGraphRestClient;
import com.github.istin.dmtools.microsoft.teams.model.Channel;
import com.github.istin.dmtools.microsoft.teams.model.Chat;
import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import com.github.istin.dmtools.microsoft.teams.model.Team;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft Teams REST client implementation.
 * Provides read and write access to Teams chats, messages, teams, and channels.
 * Implements MCP tools for CLI and AI agent integration.
 */
public class TeamsClient extends MicrosoftGraphRestClient {
    private static final Logger logger = LogManager.getLogger(TeamsClient.class);
    
    private static final int DEFAULT_PAGE_SIZE = 50; // Microsoft Graph API max
    private static final int DEFAULT_MAX_MESSAGES = 100;
    
    /**
     * Creates a Teams client with configuration.
     * 
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID (use "common" for multi-tenant)
     * @param scopes OAuth 2.0 scopes (space-separated)
     * @param authMethod Authentication method: "browser", "device", or "refresh_token"
     * @param authPort Port for localhost redirect (browser flow)
     * @param tokenCachePath Path to token cache file
     * @param preConfiguredRefreshToken Optional pre-configured refresh token
     * @throws IOException if initialization fails
     */
    public TeamsClient(
            String clientId,
            String tenantId,
            String scopes,
            String authMethod,
            int authPort,
            String tokenCachePath,
            String preConfiguredRefreshToken) throws IOException {
        super(
                "https://graph.microsoft.com/v1.0",
                clientId,
                tenantId,
                scopes,
                authMethod,
                authPort,
                tokenCachePath,
                preConfiguredRefreshToken
        );
    }
    
    // ========== Chat Operations (Direct Access) ==========
    
    /**
     * Retrieves all chats for the current user.
     * 
     * @return List of chats
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_chats_raw",
        description = "List all chats for the current user with topic, type, and participant information (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<Chat> getChats() throws IOException {
        List<Chat> allChats = new ArrayList<>();
        java.util.Set<String> seenChatIds = new java.util.HashSet<>(); // Track seen chat IDs to avoid duplicates
        String url = path("/me/chats");
        int duplicateCount = 0;
        boolean isFirstRequest = true;
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            request.param("$orderby", "lastMessagePreview/createdDateTime desc");
            if (isFirstRequest) {
                // Expand members to get contact names, lastMessagePreview for last message
                request.param("$expand", "members,lastMessagePreview");
                isFirstRequest = false;
            }
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray chats = json.optJSONArray("value");
            if (chats != null) {
                for (int i = 0; i < chats.length(); i++) {
                    Chat chat = new Chat(chats.getJSONObject(i));
                    String chatId = chat.getId();
                    
                    // Only add if we haven't seen this chat before
                    if (chatId != null && !seenChatIds.contains(chatId)) {
                        seenChatIds.add(chatId);
                        allChats.add(chat);
                    } else if (chatId != null) {
                        duplicateCount++;
                        logger.debug("Skipping duplicate chat: {} (ID: {})", chat.getTopic(), chatId);
                    }
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        if (duplicateCount > 0) {
            logger.info("Retrieved {} unique chats (skipped {} duplicates)", allChats.size(), duplicateCount);
        } else {
            logger.info("Retrieved {} chats", allChats.size());
        }
        return allChats;
    }
    
    /**
     * Retrieves all chats with simplified information: chat name, last message, and date.
     * 
     * @return JSON string with simplified chat list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_chats",
        description = "List all chats showing only chat/contact names, last message (truncated to 100 chars), and date",
        integration = "teams",
        category = "communication"
    )
    public String getChatsSimplified() throws IOException {
        List<Chat> chats = getChats();
        JSONArray simplified = TeamsChatsSimplifier.simplifyChats(chats);
        return simplified.toString(2); // Pretty print with 2-space indent
    }
    
    /**
     * Retrieves recent chats sorted by last activity with filtering options.
     * Returns simplified information: chat name, last message with author, and date.
     * 
     * @param limit Maximum number of recent chats to retrieve (default: 50, max: 200)
     * @param chatType Filter by chat type: "oneOnOne", "group", "meeting", or "all" (default: "all")
     * @return JSON string with simplified chat list sorted by most recent activity
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_recent_chats",
        description = "Get recent chats sorted by last activity showing chat/contact names, last message with author, and date. Shows 'new: true' for unread messages. Filter by type: 'oneOnOne' for 1-on-1 chats, 'group' for group chats, 'meeting' for meeting chats, or 'all' (default). Only shows chats with activity in the last 90 days.",
        integration = "teams",
        category = "communication"
    )
    public String getRecentChatsSimplified(
            @MCPParam(name = "limit", description = "Maximum number of recent chats (default: 50, max: 200)", required = false, example = "50") Integer limit,
            @MCPParam(name = "chatType", description = "Filter by chat type: 'oneOnOne', 'group', 'meeting', or 'all' (default: 'all')", required = false, example = "oneOnOne") String chatType) throws IOException {
        
        // Assign default value to limit if null
        limit = (limit == null) ? 50 : Math.min(limit, 200);
        
        // Fetch more chats than needed for filtering (especially if filtering by chatType)
        // For specific chat types, fetch much more to account for filtering
        int fetchLimit;
        if (chatType != null && !chatType.trim().isEmpty() && !chatType.equalsIgnoreCase("all")) {
            fetchLimit = Math.min(limit * 10, 500);
        } else {
            fetchLimit = Math.min(limit * 3, 500);
        }
        
        // Fetch raw chats (limited)
        List<Chat> allChats = new ArrayList<>();
        java.util.Set<String> seenChatIds = new java.util.HashSet<>();
        String url = path("/me/chats");
        boolean isFirstRequest = true;
        int duplicateCount = 0;
        
        while (url != null && allChats.size() < fetchLimit) {
            GenericRequest request = new GenericRequest(this, url);
            if (isFirstRequest) {
                request.param("$expand", "members,lastMessagePreview");
                request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, fetchLimit)));
                isFirstRequest = false;
            }
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray chats = json.optJSONArray("value");
            if (chats != null) {
                for (int i = 0; i < chats.length(); i++) {
                    Chat chat = new Chat(chats.getJSONObject(i));
                    String chatId = chat.getId();
                    
                    if (chatId != null && !seenChatIds.contains(chatId)) {
                        seenChatIds.add(chatId);
                        allChats.add(chat);
                        if (allChats.size() >= fetchLimit) {
                            break;
                        }
                    } else if (chatId != null) {
                        duplicateCount++;
                    }
                }
            }
            
            if (allChats.size() < fetchLimit) {
                url = json.optString("@odata.nextLink", null);
            } else {
                url = null;
            }
        }
        
        if (duplicateCount > 0) {
            logger.debug("Skipped {} duplicate chats while fetching recent chats", duplicateCount);
        }
        
        // Use simplifier to sort, filter, and format
        JSONArray simplified = TeamsChatsSimplifier.getRecentChatsSimplified(allChats, limit, chatType, 90);
        logger.info("Retrieved {} recent chats (requested limit: {})", simplified.length(), limit);
        return simplified.toString(2);
    }
    
    /**
     * Retrieves messages from a specific chat by ID.
     * 
     * @param chatId The chat ID
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @return List of messages in chronological order
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_messages_raw",
        description = "Get messages from a chat by ID with sender info, content, timestamps, and attachments (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChatMessages(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        // Assign default value to limit if null
        limit = (limit == null) ? DEFAULT_MAX_MESSAGES : limit;
        boolean getAllMessages = (limit == 0);
        int maxMessages = getAllMessages ? Integer.MAX_VALUE : limit;
        
        List<ChatMessage> allMessages = new ArrayList<>();
        String url = path(String.format("/me/chats/%s/messages", chatId));
        boolean isFirstRequest = true;
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            // Only add query params on first request; nextLink already contains them
            if (isFirstRequest) {
                if (!getAllMessages) {
                    request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, maxMessages - allMessages.size())));
                } else {
                    request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                }
                request.param("$orderby", "createdDateTime desc");
                isFirstRequest = false;
            }
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray messages = json.optJSONArray("value");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    allMessages.add(new ChatMessage(messages.getJSONObject(i)));
                    if (!getAllMessages && allMessages.size() >= maxMessages) {
                        break;
                    }
                }
            }
            
            // Check for next page
            if (getAllMessages || allMessages.size() < maxMessages) {
                url = json.optString("@odata.nextLink", null);
            } else {
                url = null;
            }
        }
        
        logger.info("Retrieved {} messages from chat {}", allMessages.size(), chatId);
        return allMessages;
    }
    
    // ========== Chat Operations (Name-Based Access) ==========
    
    /**
     * Finds a chat by topic/name or participant name (case-insensitive partial match).
     * Searches both chat topics and participant names (useful for 1-on-1 chats).
     * 
     * @param chatName The chat name or participant name to search for
     * @return The first matching chat, or null if not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_find_chat_by_name_raw",
        description = "Find a chat by topic/name or participant name (case-insensitive partial match). Works for group chats and 1-on-1 chats. (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public Chat findChatByName(
            @MCPParam(name = "chatName", description = "The chat topic or participant name to search for", required = true) String chatName) throws IOException {
        
        // Use client-side filtering with early exit (faster than server-side for Teams API)
        // Safety limit: max 10 pages (500 chats) to avoid long searches
        final int MAX_PAGES = 10;
        
        String url = path("/me/chats");
        boolean isFirstRequest = true;
        int pageCount = 0;
        String searchLower = chatName.toLowerCase();
        
        while (url != null && pageCount < MAX_PAGES) {
            pageCount++;
            GenericRequest request = new GenericRequest(this, url);
            
            // Only add params on first request
            if (isFirstRequest) {
                request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                request.param("$expand", "members"); // Need members to search by participant name
                isFirstRequest = false;
            }
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray chats = json.optJSONArray("value");
            if (chats != null) {
                for (int i = 0; i < chats.length(); i++) {
                    Chat chat = new Chat(chats.getJSONObject(i));
                    
                    // Check topic first (group chats, meetings)
                    String topic = chat.getTopic();
                    if (topic != null && topic.toLowerCase().contains(searchLower)) {
                        logger.info("Found chat by topic: {} (ID: {}) on page {}", topic, chat.getId(), pageCount);
                        return chat;
                    }
                    
                    // Check individual member names (for 1-on-1 chats)
                    List<Chat.ChatMember> members = chat.getMembers();
                    if (members != null) {
                        for (Chat.ChatMember member : members) {
                            String memberDisplayName = member.getDisplayName();
                            if (memberDisplayName != null && memberDisplayName.toLowerCase().contains(searchLower)) {
                                logger.info("Found chat by member name: {} in chat (ID: {}) on page {}", 
                                    memberDisplayName, chat.getId(), pageCount);
                                return chat;
                            }
                        }
                    }
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        if (pageCount >= MAX_PAGES) {
            logger.warn("No chat found with name or member '{}' in first {} pages ({} chats)", 
                chatName, MAX_PAGES, MAX_PAGES * DEFAULT_PAGE_SIZE);
        } else {
            logger.warn("No chat found with name or member: {}", chatName);
        }
        return null;
    }
    
    /**
     * Retrieves messages from a chat by name.
     * 
     * @param chatName The chat name to search for
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @return List of messages, or empty list if chat not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_messages_by_name_raw",
        description = "Get messages from a chat by name (combines find + get messages) (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChatMessagesByName(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            return new ArrayList<>();
        }
        
        return getChatMessages(chat.getId(), limit);
    }
    
    /**
     * Gets messages from a chat by name with simplified, readable output.
     * Returns only essential information: author, body, date, reactions, mentions, attachments.
     * 
     * @param chatName The chat name to search for
     * @param limit Maximum number of messages (0 for all, default: 100)
     * @return JSON string with simplified message list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_messages_simple",
        description = "Get messages from a chat with simplified output showing only: author, body, date, reactions, mentions, and attachments",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessagesSimple(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        // Find chat
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            JSONObject error = new JSONObject();
            error.put("error", "Chat not found: " + chatName);
            return error.toString(2);
        }
        
        // Get messages
        String chatId = chat.getId();
        List<ChatMessage> messages = getChatMessages(chatId, limit);
        
        // Convert to simplified format using TeamsMessageSimplifier (pass chatId for transcript download URLs)
        JSONArray simplified = TeamsMessageSimplifier.simplifyMessages(messages, chatId);
        
        logger.info("Retrieved {} messages from chat '{}' (simplified)", simplified.length(), chatName);
        return simplified.toString(2); // Pretty print with 2-space indent
    }
    
    /**
     * Gets messages from a chat starting from a specific date.
     * Useful for incremental updates after downloading full history.
     * Uses smart pagination with early exit: stops fetching when encountering messages older than sinceDate.
     * Returns simplified format: author, body, date, reactions, mentions, attachments.
     * 
     * @param chatId The chat ID
     * @param sinceDate ISO 8601 date string (e.g., "2025-10-08T00:00:00Z")
     * @return JSON string with simplified message list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_messages_since",
        description = "Get messages from a chat starting from a specific date (ISO 8601 format, e.g., '2025-10-08T00:00:00Z'). Returns simplified format. Uses smart pagination with early exit for performance.",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessagesSince(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "sinceDate", description = "ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')", required = true, example = "2025-10-08T00:00:00Z") String sinceDate) throws IOException {
        
        // Validate and parse date
        java.time.Instant sinceInstant;
        try {
            sinceInstant = java.time.Instant.parse(sinceDate);
        } catch (Exception e) {
            throw new IOException("Invalid date format. Expected ISO 8601 (e.g., '2025-10-08T00:00:00Z'), got: " + sinceDate);
        }
        
        // Fetch messages with smart pagination and early exit
        // Safety limits: max 10 pages (500 messages) to stay under 1 minute
        final int MAX_PAGES = 10; // ~500 messages max, ~30-60 seconds
        
        List<ChatMessage> filteredMessages = new ArrayList<>();
        String url = path(String.format("/me/chats/%s/messages", chatId));
        boolean isFirstRequest = true;
        int totalFetched = 0;
        int pageCount = 0;
        boolean foundOlderMessage = false;
        
        while (url != null && !foundOlderMessage && pageCount < MAX_PAGES) {
            pageCount++;
            GenericRequest request = new GenericRequest(this, url);
            
            // Only add query params on first request
            if (isFirstRequest) {
                request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                request.param("$orderby", "createdDateTime desc");
                isFirstRequest = false;
            }
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray messages = json.optJSONArray("value");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    ChatMessage message = new ChatMessage(messages.getJSONObject(i));
                    totalFetched++;
                    
                    String createdDateTime = message.getCreatedDateTime();
                    if (createdDateTime != null) {
                        try {
                            java.time.Instant messageTime = java.time.Instant.parse(createdDateTime);
                            
                            if (messageTime.isAfter(sinceInstant)) {
                                // Message is newer than cutoff date - include it
                                filteredMessages.add(message);
                            } else {
                                // Found message older than cutoff date - stop pagination
                                foundOlderMessage = true;
                                logger.debug("Found older message at {}, stopping pagination", createdDateTime);
                                break;
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse message date: {}", createdDateTime);
                        }
                    }
                }
            }
            
            // Check for next page (only if we haven't found an older message yet)
            if (!foundOlderMessage) {
                url = json.optString("@odata.nextLink", null);
            }
        }
        
        // Log results with safety limit info
        if (pageCount >= MAX_PAGES && !foundOlderMessage) {
            logger.warn("Reached safety limit of {} pages ({} messages scanned). Some messages since {} may not be included.", 
                MAX_PAGES, totalFetched, sinceDate);
        }
        
        logger.info("Retrieved {} new messages from chat {} since {} (scanned {} messages in {} pages)", 
            filteredMessages.size(), chatId, sinceDate, totalFetched, pageCount);
        
        // Convert to simplified format using TeamsMessageSimplifier (pass chatId for transcript download URLs)
        JSONArray simplified = TeamsMessageSimplifier.simplifyMessages(filteredMessages, chatId);
        
        return simplified.toString(2); // Pretty print with 2-space indent
    }
    
    /**
     * Gets messages from a chat by name starting from a specific date.
     * Uses smart pagination with early exit for performance.
     * Returns simplified format: author, body, date, reactions, mentions, attachments.
     * 
     * @param chatName The chat name to search for
     * @param sinceDate ISO 8601 date string (e.g., "2025-10-08T00:00:00Z")
     * @return JSON string with simplified message list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_messages_by_name_since",
        description = "Get messages from a chat by name starting from a specific date (ISO 8601 format). Returns simplified format. Uses smart pagination with early exit for performance.",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessagesByNameSince(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "sinceDate", description = "ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')", required = true, example = "2025-10-08T00:00:00Z") String sinceDate) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            JSONObject error = new JSONObject();
            error.put("error", "Chat not found: " + chatName);
            return error.toString(2);
        }
        
        return getChatMessagesSince(chat.getId(), sinceDate);
    }
    
    // ========== Write Operations ==========
    
    /**
     * Sends a message to a chat by ID.
     * 
     * @param chatId The chat ID
     * @param content Message content (plain text or HTML)
     * @return The created message
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_send_message_raw",
        description = "Send a message to a chat by ID (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public ChatMessage sendChatMessage(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "content", description = "Message content (plain text or HTML)", required = true) String content) throws IOException {
        
        return sendChatMessageWithType(chatId, content, "text");
    }
    
    /**
     * Sends a message to a chat by name.
     * 
     * @param chatName The chat name to search for
     * @param content Message content (plain text or HTML)
     * @return The created message, or null if chat not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_send_message_by_name",
        description = "Send a message to a chat by name or participant name (finds chat, then sends message)",
        integration = "teams",
        category = "communication"
    )
    public String sendChatMessageByName(
            @MCPParam(name = "chatName", description = "The chat topic or participant name", required = true) String chatName,
            @MCPParam(name = "content", description = "Message content (plain text or HTML)", required = true) String content) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot send message: chat '{}' not found", chatName);
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", "Chat not found: " + chatName);
            return error.toString(2);
        }
        
        ChatMessage message = sendChatMessage(chat.getId(), content);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("chatName", chat.getTopic() != null ? chat.getTopic() : "1-on-1 chat");
        result.put("chatId", chat.getId());
        result.put("messageId", message.getId());
        result.put("createdDateTime", message.getCreatedDateTime());
        
        logger.info("Sent message to chat: {}", chatName);
        return result.toString(2);
    }
    
    /**
     * Sends a message with specific content type.
     */
    private ChatMessage sendChatMessageWithType(String chatId, String content, String contentType) throws IOException {
        String url = path(String.format("/me/chats/%s/messages", chatId));
        
        JSONObject messageBody = new JSONObject();
        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("contentType", contentType);
        messageBody.put("body", body);
        
        GenericRequest request = new GenericRequest(this, url);
        request.setBody(messageBody.toString());
        
        String response = post(request);
        ChatMessage message = new ChatMessage(response);
        
        logger.info("Message sent to chat {}: {} chars", chatId, content.length());
        return message;
    }
    
    // ========== Self Chat Operations (Personal Notes) ==========
    
    /**
     * Special chat ID for self chat (personal notes).
     * This is a fixed ID that represents your personal chat with yourself.
     */
    private static final String SELF_CHAT_ID = "48:notes";
    
    /**
     * Gets messages from your self chat (personal notes) in raw format.
     * The self chat is a special chat with ID "48:notes" where you can send messages to yourself.
     * 
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @return List of messages from self chat
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_myself_messages_raw",
        description = "Get messages from your personal self chat (notes to yourself) with full raw data",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getSelfChatMessages(
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        logger.info("Retrieving messages from self chat (personal notes)");
        return getChatMessages(SELF_CHAT_ID, limit);
    }
    
    /**
     * Gets messages from your self chat (personal notes) in simplified format.
     * The self chat is a special chat with ID "48:notes" where you can send messages to yourself.
     * 
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @return JSON string with simplified message list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_myself_messages_simple",
        description = "Get messages from your personal self chat (notes to yourself) with simplified output",
        integration = "teams",
        category = "communication"
    )
    public String getSelfChatMessagesSimple(
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        // Get messages
        List<ChatMessage> messages = getChatMessages(SELF_CHAT_ID, limit);
        
        // Convert to simplified format using TeamsMessageSimplifier (pass chatId for transcript download URLs)
        JSONArray simplified = TeamsMessageSimplifier.simplifyMessages(messages, SELF_CHAT_ID);
        
        logger.info("Retrieved {} messages from self chat (simplified)", simplified.length());
        return simplified.toString(2); // Pretty print with 2-space indent
    }
    
    /**
     * Sends a message to your self chat (personal notes).
     * The self chat is a special chat with ID "48:notes" where you can send messages to yourself.
     * 
     * @param content Message content (plain text or HTML)
     * @return JSON string with result (success/error) and message details
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_send_myself_message",
        description = "Send a message to your personal self chat (notes to yourself)",
        integration = "teams",
        category = "communication"
    )
    public String sendSelfChatMessage(
            @MCPParam(name = "content", description = "Message content (plain text or HTML)", required = true) String content) throws IOException {
        
        ChatMessage message = sendChatMessage(SELF_CHAT_ID, content);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("chatName", "Self Chat (Personal Notes)");
        result.put("chatId", SELF_CHAT_ID);
        result.put("messageId", message.getId());
        result.put("createdDateTime", message.getCreatedDateTime());
        
        logger.info("Sent message to self chat (personal notes)");
        return result.toString(2);
    }
    
    // ========== Teams and Channels Operations ==========
    
    /**
     * Retrieves teams the user is a member of.
     * 
     * @return List of teams
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_joined_teams_raw",
        description = "List teams the user is a member of (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<Team> getJoinedTeams() throws IOException {
        List<Team> allTeams = new ArrayList<>();
        String url = path("/me/joinedTeams");
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray teams = json.optJSONArray("value");
            if (teams != null) {
                for (int i = 0; i < teams.length(); i++) {
                    allTeams.add(new Team(teams.getJSONObject(i)));
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        logger.info("Retrieved {} teams", allTeams.size());
        return allTeams;
    }
    
    /**
     * Retrieves channels in a team.
     * 
     * @param teamId The team ID
     * @return List of channels
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_team_channels_raw",
        description = "Get channels in a specific team (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<Channel> getTeamChannels(
            @MCPParam(name = "teamId", description = "The team ID", required = true) String teamId) throws IOException {
        
        List<Channel> allChannels = new ArrayList<>();
        String url = path(String.format("/teams/%s/channels", teamId));
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray channels = json.optJSONArray("value");
            if (channels != null) {
                for (int i = 0; i < channels.length(); i++) {
                    allChannels.add(new Channel(channels.getJSONObject(i)));
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        logger.info("Retrieved {} channels from team {}", allChannels.size(), teamId);
        return allChannels;
    }
    
    /**
     * Finds a team by display name (case-insensitive partial match).
     * 
     * @param teamName The team name to search for
     * @return The first matching team, or null if not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_find_team_by_name_raw",
        description = "Find a team by display name (case-insensitive partial match) (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public Team findTeamByName(
            @MCPParam(name = "teamName", description = "The team name to search for", required = true) String teamName) throws IOException {
        
        // Try server-side filtering first (more efficient)
        List<Team> filteredTeams = new ArrayList<>();
        String url = path("/me/joinedTeams");
        boolean isFirstRequest = true;
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            // Use $filter with startswith for server-side filtering (only on first request)
            if (isFirstRequest) {
                request.param("$filter", String.format("startswith(displayName, '%s')", teamName.replace("'", "''")));
                isFirstRequest = false;
            }
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray teams = json.optJSONArray("value");
            if (teams != null) {
                for (int i = 0; i < teams.length(); i++) {
                    filteredTeams.add(new Team(teams.getJSONObject(i)));
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        // If server-side filtering returns results, use them
        if (!filteredTeams.isEmpty()) {
            if (filteredTeams.size() > 1) {
                logger.warn("Multiple teams found matching '{}', returning first match", teamName);
            }
            Team result = filteredTeams.get(0);
            logger.info("Found team: {} (ID: {})", result.getDisplayName(), result.getId());
            return result;
        }
        
        // Fallback to client-side filtering (case-insensitive partial match)
        logger.debug("Server-side filtering returned no results, falling back to client-side filtering");
        List<Team> allTeams = getJoinedTeams();
        List<Team> matches = new ArrayList<>();
        
        String searchLower = teamName.toLowerCase();
        for (Team team : allTeams) {
            String displayName = team.getDisplayName();
            if (displayName != null && displayName.toLowerCase().contains(searchLower)) {
                matches.add(team);
            }
        }
        
        if (matches.isEmpty()) {
            logger.warn("No team found with name: {}", teamName);
            return null;
        }
        
        if (matches.size() > 1) {
            logger.warn("Multiple teams found matching '{}', returning first match", teamName);
        }
        
        Team result = matches.get(0);
        logger.info("Found team: {} (ID: {})", result.getDisplayName(), result.getId());
        return result;
    }
    
    /**
     * Finds a channel by name within a team (case-insensitive partial match).
     * 
     * @param teamId The team ID
     * @param channelName The channel name to search for
     * @return The first matching channel, or null if not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_find_channel_by_name_raw",
        description = "Find a channel by name within a team (case-insensitive partial match) (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public Channel findChannelByName(
            @MCPParam(name = "teamId", description = "The team ID", required = true) String teamId,
            @MCPParam(name = "channelName", description = "The channel name to search for", required = true) String channelName) throws IOException {
        
        List<Channel> allChannels = getTeamChannels(teamId);
        List<Channel> matches = new ArrayList<>();
        
        String searchLower = channelName.toLowerCase();
        for (Channel channel : allChannels) {
            String displayName = channel.getDisplayName();
            if (displayName != null && displayName.toLowerCase().contains(searchLower)) {
                matches.add(channel);
            }
        }
        
        if (matches.isEmpty()) {
            logger.warn("No channel found with name: {} in team {}", channelName, teamId);
            return null;
        }
        
        if (matches.size() > 1) {
            logger.warn("Multiple channels found matching '{}', returning first match", channelName);
        }
        
        Channel result = matches.get(0);
        logger.info("Found channel: {} (ID: {})", result.getDisplayName(), result.getId());
        return result;
    }
    
    /**
     * Retrieves messages from a channel by team and channel names.
     * 
     * @param teamName The team name to search for
     * @param channelName The channel name to search for
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @return List of messages, or empty list if team/channel not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_channel_messages_by_name_raw",
        description = "Get messages from a channel by team and channel names (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChannelMessagesByName(
            @MCPParam(name = "teamName", description = "The team name to search for", required = true) String teamName,
            @MCPParam(name = "channelName", description = "The channel name to search for", required = true) String channelName,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        // Find team
        Team team = findTeamByName(teamName);
        if (team == null) {
            logger.error("Cannot get channel messages: team '{}' not found", teamName);
            return new ArrayList<>();
        }
        
        // Find channel
        Channel channel = findChannelByName(team.getId(), channelName);
        if (channel == null) {
            logger.error("Cannot get channel messages: channel '{}' not found in team '{}'", channelName, teamName);
            return new ArrayList<>();
        }
        
        // Get messages
        return getChannelMessages(team.getId(), channel.getId(), limit);
    }
    
    /**
     * Retrieves messages from a channel.
     */
    private List<ChatMessage> getChannelMessages(String teamId, String channelId, Integer limit) throws IOException {
        // Assign default value to limit if null
        limit = (limit == null) ? DEFAULT_MAX_MESSAGES : limit;
        boolean getAllMessages = (limit == 0);
        int maxMessages = getAllMessages ? Integer.MAX_VALUE : limit;
        
        List<ChatMessage> allMessages = new ArrayList<>();
        String url = path(String.format("/teams/%s/channels/%s/messages", teamId, channelId));
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            if (!getAllMessages) {
                request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, maxMessages - allMessages.size())));
            } else {
                request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
            }
            request.param("$orderby", "createdDateTime desc");
            
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray messages = json.optJSONArray("value");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    allMessages.add(new ChatMessage(messages.getJSONObject(i)));
                    if (!getAllMessages && allMessages.size() >= maxMessages) {
                        break;
                    }
                }
            }
            
            // Check for next page
            if (getAllMessages || allMessages.size() < maxMessages) {
                url = json.optString("@odata.nextLink", null);
            } else {
                url = null;
            }
        }
        
        logger.info("Retrieved {} messages from channel {} in team {}", allMessages.size(), channelId, teamId);
        return allMessages;
    }
}
