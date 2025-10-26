package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.microsoft.common.networking.MicrosoftGraphRestClient;
import com.github.istin.dmtools.microsoft.sharepoint.BasicSharePointClient;
import com.github.istin.dmtools.microsoft.teams.model.Channel;
import com.github.istin.dmtools.microsoft.teams.model.Chat;
import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import com.github.istin.dmtools.microsoft.teams.model.Team;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Microsoft Teams REST client implementation.
 * Provides read and write access to Teams chats, messages, teams, and channels.
 * Implements MCP tools for CLI and AI agent integration.
 */
public class TeamsClient extends MicrosoftGraphRestClient {
    private static final Logger logger = LogManager.getLogger(TeamsClient.class);
    
    private static final int DEFAULT_PAGE_SIZE = 50; // Microsoft Graph API max
    private static final int DEFAULT_MAX_MESSAGES = 100;
    
    // Cache current user's display name (lazy loaded)
    private String currentUserDisplayName;
    
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
    
    // ========== Helper Methods ==========
    
    /**
     * Gets the current user's display name (cached after first call).
     * 
     * @return Current user's display name
     * @throws IOException if request fails
     */
    private String getCurrentUserDisplayName() throws IOException {
        if (currentUserDisplayName == null) {
            GenericRequest request = new GenericRequest(this, path("/me"));
            String response = execute(request);
            JSONObject user = new JSONObject(response);
            currentUserDisplayName = user.optString("displayName", "");
            logger.debug("Cached current user display name: {}", currentUserDisplayName);
        }
        return currentUserDisplayName;
    }
    
    // ========== Chat Operations (Direct Access) ==========
    
    /**
     * Callback interface for processing chats during pagination.
     * Similar to JiraClient's Performer pattern.
     */
    public interface ChatPerformer {
        /**
         * Process a chat.
         * @param chat The chat to process
         * @return true to stop iteration, false to continue
         * @throws IOException if processing fails
         */
        boolean perform(Chat chat) throws IOException;
    }
    
    /**
     * Callback interface for processing messages during pagination.
     * Allows early exit and on-the-fly filtering of messages.
     */
    public interface MessagePerformer {
        /**
         * Process a message.
         * @param message The message to process
         * @return true to stop iteration, false to continue
         * @throws IOException if processing fails
         */
        boolean perform(ChatMessage message) throws IOException;
    }
    
    /**
     * Retrieves chats for the current user.
     * 
     * @param limit Maximum number of chats to retrieve (0 for all, default: 50)
     * @return List of chats
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_chats_raw",
        description = "List chats for the current user with topic, type, and participant information (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<Chat> getChatsRaw(
            @MCPParam(name = "limit", description = "Maximum number of chats (0 for all, default: 50)", required = false, example = "50") Integer limit) throws IOException {
        
        List<Chat> allChats = new ArrayList<>();
        
        // Use getChatsAndPerform to collect all chats
        getChatsAndPerform(limit, chat -> {
            allChats.add(chat);
            return false; // Continue to collect all
        });
        
        logger.info("Retrieved {} chats", allChats.size());
        return allChats;
    }
    
    /**
     * Retrieves chats for the current user with performer callback for early termination.
     * Similar to JiraClient's searchAndPerform pattern.
     * 
     * @param limit Maximum number of chats to retrieve (0 for all)
     * @param performer Callback to process each chat; return true to stop iteration
     * @throws IOException if request fails
     */
    public void getChatsAndPerform(Integer limit, ChatPerformer performer) throws IOException {
        // Handle limit parameter: null or 0 means "get all chats"
        // Note: MCP parameter parsing may convert "0" to null, so we need to handle both cases
        boolean getAllChats = (limit == null || limit == 0);
        int maxChats = getAllChats ? Integer.MAX_VALUE : (limit != null ? limit : 50);
        
        Set<String> seenChatIds = new HashSet<>(); // Track seen chat IDs to avoid duplicates
        String url = path("/me/chats");
        int duplicateCount = 0;
        int processedCount = 0;
        boolean isFirstRequest = true;
        boolean stopEarly = false;
        
        while (url != null && processedCount < maxChats && !stopEarly) {
            GenericRequest request = new GenericRequest(this, url);
            if (isFirstRequest) {
                // Only add query parameters on first request; @odata.nextLink already includes them
                if (!getAllChats) {
                    request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, maxChats - processedCount)));
                } else {
                    request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                }
                request.param("$orderby", "lastMessagePreview/createdDateTime desc");
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
                    
                    // Only process if we haven't seen this chat before
                    if (chatId != null && !seenChatIds.contains(chatId)) {
                        seenChatIds.add(chatId);
                        
                        // Call performer - if it returns true, stop iteration
                        stopEarly = performer.perform(chat);
                        processedCount++;
                        
                        if (stopEarly || (!getAllChats && processedCount >= maxChats)) {
                            break;
                        }
                    } else if (chatId != null) {
                        duplicateCount++;
                        logger.debug("Skipping duplicate chat: {} (ID: {})", chat.getTopic(), chatId);
                    }
                }
            }
            
            // Check for next page
            if (!stopEarly && (getAllChats || processedCount < maxChats)) {
                url = json.optString("@odata.nextLink", null);
            } else {
                url = null;
            }
        }
        
        if (duplicateCount > 0) {
            logger.debug("Processed {} unique chats (skipped {} duplicates)", processedCount, duplicateCount);
        }
    }
    
    /**
     * Retrieves chats with simplified information: chat name, last message, and date.
     * 
     * @param limit Maximum number of chats to retrieve (0 for all, default: 50)
     * @return JSON string with simplified chat list
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_chats",
        description = "List chats showing only chat/contact names, last message (truncated to 100 chars), and date",
        integration = "teams",
        category = "communication"
    )
    public String getChats(
            @MCPParam(name = "limit", description = "Maximum number of chats (0 for all, default: 50)", required = false, example = "50") Integer limit) throws IOException {
        List<Chat> chats = getChatsRaw(limit);
        String currentUser = getCurrentUserDisplayName();
        JSONArray simplified = TeamsChatsSimplifier.simplifyChats(chats, currentUser);
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
        name = "teams_recent_chats",
        description = "Get recent chats sorted by last activity showing chat/contact names, last message with author, and date. Shows 'new: true' for unread messages. Filter by type: 'oneOnOne' for 1-on-1 chats, 'group' for group chats, 'meeting' for meeting chats, or 'all' (default). Only shows chats with activity in the last 90 days.",
        integration = "teams",
        category = "communication"
    )
    public String getRecentChats(
            @MCPParam(name = "limit", description = "Maximum number of recent chats (0 for all, default: 50)", required = false, example = "50") Integer limit,
            @MCPParam(name = "chatType", description = "Filter by chat type: 'oneOnOne', 'group', 'meeting', or 'all' (default: 'all')", required = false, example = "oneOnOne") String chatType) throws IOException {
        
        // Handle limit parameter: null defaults to 50, 0 means "get all chats"
        int targetLimit = (limit != null && limit > 0) ? limit : 50; // Default to 50 if null
        boolean getAllChats = (limit != null && limit == 0); // Only 0 means "get all"
        
        // Fetch more chats than needed for filtering (especially if filtering by chatType)
        // For specific chat types, fetch much more to account for filtering
        int fetchLimit;
        if (getAllChats) {
            // Get all chats for filtering
            fetchLimit = 0;
        } else if (chatType != null && !chatType.trim().isEmpty() && !chatType.equalsIgnoreCase("all")) {
            // For specific chat types, fetch much more to account for filtering
            fetchLimit = targetLimit * 10;
        } else {
            // For all chat types, fetch a bit more to account for duplicates/filtering
            fetchLimit = targetLimit * 3;
        }
        
        // Use the shared getChats() method for pagination
        List<Chat> allChats = getChatsRaw(fetchLimit);
        
        // Get current user's display name for proper chat naming
        String currentUser = getCurrentUserDisplayName();
        
        // Use simplifier to sort, filter, and format
        // Pass 0 to simplifier to get all chats when getAllChats is true
        int simplifierLimit = getAllChats ? 0 : targetLimit;
        JSONArray simplified = TeamsChatsSimplifier.getRecentChatsSimplified(allChats, simplifierLimit, chatType, 90, currentUser);
        logger.info("Retrieved {} recent chats (requested limit: {}, getAllChats: {})", simplified.length(), limit, getAllChats);
        return simplified.toString(2);
    }
    
    /**
     * Retrieves messages from a specific chat by ID.
     * 
     * @param chatId The chat ID
     * @param limit Maximum number of messages to retrieve (0 for all, default: 100)
     * @param filter Optional OData filter expression for server-side filtering (e.g., "createdDateTime gt 2025-01-01T00:00:00Z")
     * @return List of messages in chronological order
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_messages_by_chat_id_raw",
        description = "Get messages from a chat by ID with optional server-side filtering. Use $filter syntax with lastModifiedDateTime: 'lastModifiedDateTime gt 2025-01-01T00:00:00Z' (returns raw JSON). Note: createdDateTime is not supported in filters.",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChatMessagesRaw(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit,
            @MCPParam(name = "filter", description = "Optional OData filter (e.g., 'lastModifiedDateTime gt 2025-01-01T00:00:00Z')", required = false, example = "lastModifiedDateTime gt 2025-01-01T00:00:00Z") String filter) throws IOException {
        
        logger.info("Retrieving RAW messages from chat {}: limit={}, filter={}", chatId, limit, filter != null ? filter : "none");
        
        // Use performer pattern to collect all messages without filtering
        List<ChatMessage> allMessages = new ArrayList<>();
        getMessagesAndPerform(chatId, limit, filter, message -> {
            allMessages.add(message);
            return false; // Continue iteration
        });
        
        logger.info("Retrieved {} RAW messages from chat {} (filter: {})", allMessages.size(), chatId, filter != null ? filter : "none");
        return allMessages;
    }
    
    /**
     * Iterates through messages with callback-based processing for efficient pagination.
     * Supports early exit when desired message is found or condition is met.
     * Does NOT filter messages - filtering should be done in the performer callback if needed.
     * 
     * @param chatId The chat ID
     * @param limit Maximum number of messages to process (0 for all, null for default)
     * @param filter Optional OData filter for server-side filtering
     * @param performer Callback to process each message (return true to stop iteration)
     * @throws IOException if request fails
     */
    public void getMessagesAndPerform(String chatId, Integer limit, String filter, MessagePerformer performer) throws IOException {
        getMessagesAndPerform(chatId, limit, filter, "desc", performer);
    }
    
    /**
     * Iterates through messages with callback-based processing for efficient pagination.
     * Supports early exit when desired message is found or condition is met.
     * Does NOT filter messages - filtering should be done in the performer callback if needed.
     * 
     * NOTE: Microsoft Teams API only supports descending order. When ascending order is requested,
     * all messages are fetched in descending order and then reversed programmatically.
     * 
     * @param chatId The chat ID
     * @param limit Maximum number of messages to process (0 for all, null for default)
     * @param filter Optional OData filter for server-side filtering
     * @param sorting Sort order: "asc" for oldest first (reversed programmatically), "desc" for newest first (default)
     * @param performer Callback to process each message (return true to stop iteration)
     * @throws IOException if request fails
     */
    public void getMessagesAndPerform(String chatId, Integer limit, String filter, String sorting, MessagePerformer performer) throws IOException {
        // Handle limit parameter
        boolean getAllMessages = (limit == null || limit == 0);
        int maxMessages = getAllMessages ? Integer.MAX_VALUE : limit;
        boolean isAscOrder = sorting != null && sorting.equalsIgnoreCase("asc");
        
        // Microsoft Teams API only supports DESC order
        // If ASC is requested, fetch all in DESC and reverse programmatically
        if (isAscOrder) {
            List<ChatMessage> allMessages = new ArrayList<>();
            String url = path(String.format("/me/chats/%s/messages", chatId));
            boolean isFirstRequest = true;
            int pageCount = 0;
            
            logger.info("Starting message iteration with ASC order (will reverse after fetch): limit={}, getAllMessages={}", limit, getAllMessages);
            
            // Fetch all messages in DESC order (API only supports this)
            while (url != null) {
                pageCount++;
                GenericRequest request = new GenericRequest(this, url);
                
                if (isFirstRequest) {
                    if (!getAllMessages) {
                        request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, maxMessages)));
                    } else {
                        request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                    }
                    request.param("$orderby", "createdDateTime desc"); // API only supports desc
                    
                    if (filter != null && !filter.trim().isEmpty()) {
                        request.param("$filter", filter.trim());
                        logger.debug("Applying server-side filter: {}", filter);
                    }
                    
                    isFirstRequest = false;
                }
                
                String response = execute(request);
                JSONObject json = new JSONObject(response);
                
                JSONArray messages = json.optJSONArray("value");
                if (messages != null) {
                    logger.debug("Retrieved {} messages in page {}", messages.length(), pageCount);
                    
                    for (int i = 0; i < messages.length(); i++) {
                        ChatMessage message = new ChatMessage(messages.getJSONObject(i));
                        allMessages.add(message);
                        
                        if (!getAllMessages && allMessages.size() >= maxMessages) {
                            url = null; // Stop fetching
                            break;
                        }
                    }
                }
                
                if (url != null && (getAllMessages || allMessages.size() < maxMessages)) {
                    url = json.optString("@odata.nextLink", null);
                } else {
                    url = null;
                }
            }
            
            logger.info("Fetched {} messages, reversing for ASC order", allMessages.size());
            
            // Reverse to get oldest first
            Collections.reverse(allMessages);
            
            // Now perform on reversed list
            boolean stopEarly = false;
            for (ChatMessage message : allMessages) {
                stopEarly = performer.perform(message);
                if (stopEarly) {
                    break;
                }
            }
            
            logger.info("Completed message iteration (ASC): processed={}, pages={}", allMessages.size(), pageCount);
            
        } else {
            // DESC order - process as we fetch (efficient streaming)
            String url = path(String.format("/me/chats/%s/messages", chatId));
            boolean isFirstRequest = true;
            int pageCount = 0;
            int processedCount = 0;
            boolean stopEarly = false;
            
            logger.info("Starting message iteration with DESC order: limit={}, getAllMessages={}", limit, getAllMessages);
            
            while (url != null && !stopEarly) {
                pageCount++;
                GenericRequest request = new GenericRequest(this, url);
                
                if (isFirstRequest) {
                    if (!getAllMessages) {
                        request.param("$top", String.valueOf(Math.min(DEFAULT_PAGE_SIZE, maxMessages - processedCount)));
                    } else {
                        request.param("$top", String.valueOf(DEFAULT_PAGE_SIZE));
                    }
                    request.param("$orderby", "createdDateTime desc");
                    
                    if (filter != null && !filter.trim().isEmpty()) {
                        request.param("$filter", filter.trim());
                        logger.debug("Applying server-side filter: {}", filter);
                    }
                    
                    isFirstRequest = false;
                }
                
                String response = execute(request);
                JSONObject json = new JSONObject(response);
                
                JSONArray messages = json.optJSONArray("value");
                if (messages != null) {
                    logger.debug("Retrieved {} messages in page {}, processed so far: {}", messages.length(), pageCount, processedCount);
                    
                    for (int i = 0; i < messages.length() && !stopEarly; i++) {
                        ChatMessage message = new ChatMessage(messages.getJSONObject(i));
                        
                        stopEarly = performer.perform(message);
                        processedCount++;
                        
                        if (!getAllMessages && processedCount >= maxMessages) {
                            stopEarly = true;
                            break;
                        }
                    }
                }
                
                if (!stopEarly && (getAllMessages || processedCount < maxMessages)) {
                    url = json.optString("@odata.nextLink", null);
                } else {
                    url = null;
                }
            }
            
            logger.info("Completed message iteration (DESC): processed={}, pages={}", processedCount, pageCount);
        }
    }
    
    /**
     * Determines if a message should be skipped as a noisy system event.
     * Based on filtering logic from TeamsMessageSimplifier.
     * 
     * @param message The message to check
     * @return true if message should be skipped, false otherwise
     */
    private boolean shouldSkipNoisyMessage(ChatMessage message) {
        // Only filter system messages, not regular messages
        if ("message".equals(message.getMessageType())) {
            return false;
        }
        
        JSONObject eventDetail = message.getEventDetail();
        if (eventDetail == null) {
            // System message without event detail - skip it
            return true;
        }
        
        String eventType = eventDetail.optString("@odata.type", "");
        
        // Skip common noisy events (same logic as TeamsMessageSimplifier)
        return eventType.equals("#microsoft.graph.membersDeletedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.membersAddedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.memberJoinedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.membersJoinedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.memberLeftEventMessageDetail") ||
               eventType.equals("#microsoft.graph.messagePinnedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.callEndedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.callStartedEventMessageDetail") ||
               eventType.equals("#microsoft.graph.teamsAppInstalledEventMessageDetail");
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
        name = "teams_chat_by_name_raw",
        description = "Find a chat by topic/name or participant name (case-insensitive partial match). Works for group chats and 1-on-1 chats. (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public Chat findChatByName(
            @MCPParam(name = "chatName", description = "The chat topic or participant name to search for", required = true) String chatName) throws IOException {
        
        // Use getChatsAndPerform with early exit for efficient search
        // Search through all chats until found (0 = unlimited)
        String searchLower = chatName.toLowerCase();
        final Chat[] foundChat = {null}; // Array to capture result in lambda
        
        getChatsAndPerform(0, chat -> {
            // Check topic first (group chats, meetings)
            String topic = chat.getTopic();
            if (topic != null && topic.toLowerCase().contains(searchLower)) {
                logger.info("Found chat by topic: {} (ID: {})", topic, chat.getId());
                foundChat[0] = chat;
                return true; // Stop iteration
            }
            
            // Check individual member names (for 1-on-1 chats)
            List<Chat.ChatMember> members = chat.getMembers();
            if (members != null) {
                for (Chat.ChatMember member : members) {
                    String memberDisplayName = member.getDisplayName();
                    if (memberDisplayName != null && memberDisplayName.toLowerCase().contains(searchLower)) {
                        logger.info("Found chat by member name: {} in chat (ID: {})", 
                            memberDisplayName, chat.getId());
                        foundChat[0] = chat;
                        return true; // Stop iteration
                    }
                }
            }
            
            return false; // Continue to next chat
        });
        
        if (foundChat[0] == null) {
            logger.warn("No chat found with name or member: {}", chatName);
        }
        return foundChat[0];
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
        name = "teams_messages_raw",
        description = "Get messages from a chat by name (combines find + get messages) (returns raw JSON)",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChatMessagesByNameRaw(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            return new ArrayList<>();
        }
        
        return getChatMessagesRaw(chat.getId(), limit, null);
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
        name = "teams_messages",
        description = "Get messages from a chat with simplified output showing only: author, body, date, reactions, mentions, and attachments",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessages(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit,
            @MCPParam(name = "sorting", description = "Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')", required = false, example = "desc") String sorting) throws IOException {
        
        // Find chat
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            JSONObject error = new JSONObject();
            error.put("error", "Chat not found: " + chatName);
            return error.toString(2);
        }
        
        // Get messages using performer pattern for efficient iteration with noisy message filtering
        String chatId = chat.getId();
        List<ChatMessage> messages = new ArrayList<>();
        
        // Handle limit: we need to fetch more messages from API to account for filtered noisy ones
        // Set fetchLimit to 0 (unlimited) and apply limit after filtering
        // null → default 100, 0 → all messages
        int targetLimit;
        boolean getAllMessages;
        if (limit == null) {
            targetLimit = DEFAULT_MAX_MESSAGES; // Default to 100
            getAllMessages = false;
        } else if (limit == 0) {
            targetLimit = Integer.MAX_VALUE;
            getAllMessages = true;
        } else {
            targetLimit = limit;
            getAllMessages = false;
        }
        
        // Default sorting to desc if not provided
        String sortOrder = (sorting != null && !sorting.trim().isEmpty()) ? sorting : "desc";
        
        getMessagesAndPerform(chatId, 0, null, sortOrder, message -> {
            // Filter out noisy system messages in the performer
            if (!shouldSkipNoisyMessage(message)) {
                messages.add(message);
                // Stop when we have enough non-noisy messages
                if (!getAllMessages && messages.size() >= targetLimit) {
                    return true; // Stop iteration
                }
            }
            return false; // Continue iteration
        });
        
        // Convert to simplified format using TeamsMessageSimplifier (pass chatId for transcript download URLs)
        JSONArray simplified = TeamsMessageSimplifier.simplifyMessages(messages, chatId);
        
        logger.info("Retrieved {} messages from chat '{}'", simplified.length(), chatName);
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
        name = "teams_messages_since_by_id",
        description = "Get messages from a chat starting from a specific date (ISO 8601 format, e.g., '2025-10-08T00:00:00Z'). Returns simplified format. Uses smart pagination with early exit for performance.",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessagesSince(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "sinceDate", description = "ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')", required = true, example = "2025-10-08T00:00:00Z") String sinceDate,
            @MCPParam(name = "sorting", description = "Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')", required = false, example = "desc") String sorting) throws IOException {
        
        // Validate and parse date format
        Instant sinceInstant;
        try {
            sinceInstant = Instant.parse(sinceDate);
        } catch (Exception e) {
            throw new IOException("Invalid date format. Expected ISO 8601 (e.g., '2025-10-08T00:00:00Z'), got: " + sinceDate);
        }
        
        // Default sorting to desc if not provided
        String sortOrder = (sorting != null && !sorting.trim().isEmpty()) ? sorting : "desc";
        
        logger.info("Retrieving messages from chat {} since {} using performer with early exit, sorting: {}", chatId, sinceDate, sortOrder);
        
        // Use the performer pattern for efficient iteration with early exit
        // If sorting is desc: Messages are ordered newest first, so we can stop when we hit the date boundary
        // If sorting is asc: Messages are ordered oldest first, collect all until we reach current time
        List<ChatMessage> filteredMessages = new ArrayList<>();
        boolean isDescOrder = sortOrder.equalsIgnoreCase("desc");
        
        getMessagesAndPerform(chatId, 0, null, sortOrder, message -> {
            try {
                String createdDateTime = message.getCreatedDateTime();
                if (createdDateTime != null) {
                    Instant messageInstant = Instant.parse(createdDateTime);
                    
                    if (isDescOrder) {
                        // DESC order: newest first, stop when we hit older messages
                        if (messageInstant.isBefore(sinceInstant) || messageInstant.equals(sinceInstant)) {
                            logger.debug("Reached date boundary at message {}, stopping iteration", createdDateTime);
                            return true; // Stop iteration
                        }
                    } else {
                        // ASC order: oldest first, skip messages before cutoff
                        if (messageInstant.isBefore(sinceInstant) || messageInstant.equals(sinceInstant)) {
                            return false; // Skip this message, continue iteration
                        }
                    }
                    
                    // Message is after the cutoff date - filter noisy messages and include
                    if (!shouldSkipNoisyMessage(message)) {
                        filteredMessages.add(message);
                    }
                }
                return false; // Continue iteration
            } catch (Exception e) {
                logger.warn("Failed to parse message date: {}", e.getMessage());
                // Include message if we can't parse the date (safe default)
                if (!shouldSkipNoisyMessage(message)) {
                    filteredMessages.add(message);
                }
                return false; // Continue iteration
            }
        });
        
        // Convert to simplified format using TeamsMessageSimplifier (pass chatId for transcript download URLs)
        JSONArray simplified = TeamsMessageSimplifier.simplifyMessages(filteredMessages, chatId);
        
        logger.info("Retrieved {} messages from chat {} since {} using early exit", 
            simplified.length(), chatId, sinceDate);
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
        name = "teams_messages_since",
        description = "Get messages from a chat by name starting from a specific date (ISO 8601 format). Returns simplified format. Uses smart pagination with early exit for performance.",
        integration = "teams",
        category = "communication"
    )
    public String getChatMessagesByNameSince(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "sinceDate", description = "ISO 8601 date string (e.g., '2025-10-08T00:00:00Z')", required = true, example = "2025-10-08T00:00:00Z") String sinceDate,
            @MCPParam(name = "sorting", description = "Sort order: 'asc' for oldest first, 'desc' for newest first (default: 'desc')", required = false, example = "desc") String sorting) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot get messages: chat '{}' not found", chatName);
            JSONObject error = new JSONObject();
            error.put("error", "Chat not found: " + chatName);
            return error.toString(2);
        }
        
        return getChatMessagesSince(chat.getId(), sinceDate, sorting);
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
        name = "teams_send_message_by_id",
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
        name = "teams_send_message",
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
        name = "teams_myself_messages_raw",
        description = "Get messages from your personal self chat (notes to yourself) with full raw data",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getSelfChatMessages(
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        logger.info("Retrieving messages from self chat (personal notes)");
        return getChatMessagesRaw(SELF_CHAT_ID, limit, null);
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
        name = "teams_myself_messages",
        description = "Get messages from your personal self chat (notes to yourself) with simplified output",
        integration = "teams",
        category = "communication"
    )
    public String getSelfChatMessagesSimple(
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        // Get messages
        List<ChatMessage> messages = getChatMessagesRaw(SELF_CHAT_ID, limit, null);
        
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
    
    // ========== File Download Operations ==========
    
    /**
     * Downloads a file from a Teams message URL (Graph API or SharePoint).
     * Auto-detects SharePoint sharing URLs and delegates to SharePoint download method.
     * For Graph API hostedContents URLs, downloads directly using Teams authentication.
     * 
     * @param url The URL (Graph API hostedContents URL or SharePoint sharing URL)
     * @param outputPath Local file path to save the downloaded file
     * @return JSON with download status and file information
     * @throws IOException if download fails
     */
    @MCPTool(
        name = "teams_download_file",
        description = "Download a file from Teams (Graph API hostedContents or SharePoint sharing URL). Auto-detects URL type and uses appropriate method.",
        integration = "teams",
        category = "communication"
    )
    public String downloadFile(
            @MCPParam(name = "url", description = "Graph API URL or SharePoint sharing URL", required = true, 
                example = "https://graph.microsoft.com/v1.0/chats/.../hostedContents/.../$value") String url,
            @MCPParam(name = "outputPath", description = "Local file path to save to", required = true, 
                example = "/tmp/file.ext") String outputPath) throws IOException {
        
        logger.info("Downloading file from: {}", url);
        
        try {
            // Check if this is a SharePoint sharing URL (format: https://...sharepoint.com/:x:/...)
            if (url.contains("sharepoint.com") && url.matches(".*sharepoint\\.com/:[a-z]:/.*")) {
                logger.info("Detected SharePoint sharing URL, delegating to SharePoint download");
                // Delegate to SharePoint download method
                // SharePoint uses the same authentication as Teams (Microsoft Graph)
                return BasicSharePointClient.getInstance().downloadFile(url, outputPath);
            }
            
            // Otherwise, treat as Graph API hostedContents URL
            logger.info("Processing as Graph API hostedContents URL");
            
            // Create output file and ensure directory exists
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Download using authenticated request
            GenericRequest request = new GenericRequest(this, url);
            File downloadedFile = RestClient.Impl.downloadFile(this, request, outputFile);
            
            // Return success info
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("outputPath", downloadedFile.getAbsolutePath());
            result.put("fileSize", downloadedFile.length());
            result.put("url", url);
            result.put("method", "graphApi");
            
            logger.info("Downloaded {} bytes to {}", downloadedFile.length(), downloadedFile.getAbsolutePath());
            return result.toString(2);
            
        } catch (Exception e) {
            logger.error("Failed to download file from {}: {}", url, e.getMessage());
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("url", url);
            return error.toString(2);
        }
    }
    
    /**
     * Gets the hosted contents (files/transcripts) for a specific message.
     * This is useful for getting transcript files which are stored as hosted content.
     * 
     * @param chatId The chat ID containing the message
     * @param messageId The message ID that has hosted contents
     * @return JSON array of hosted content items
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_message_hosted_contents",
        description = "Get hosted contents (files/transcripts) for a specific message. Returns list of files with download URLs.",
        integration = "teams",
        category = "communication"
    )
    public String getMessageHostedContents(
            @MCPParam(name = "chatId", description = "Chat ID", required = true) String chatId,
            @MCPParam(name = "messageId", description = "Message ID", required = true) String messageId) throws IOException {
        
        String url = path(String.format("/chats/%s/messages/%s/hostedContents", chatId, messageId));
        logger.info("Getting hosted contents from: {}", url);
        
        GenericRequest request = new GenericRequest(this, url);
        String responseStr = execute(request);
        JSONObject response = new JSONObject(responseStr);
        
        JSONArray hostedContents = response.optJSONArray("value");
        if (hostedContents == null) {
            hostedContents = new JSONArray();
        }
        
        // Add download URLs for each hosted content
        JSONArray result = new JSONArray();
        for (int i = 0; i < hostedContents.length(); i++) {
            JSONObject content = hostedContents.getJSONObject(i);
            String contentId = content.optString("id", "");
            if (!contentId.isEmpty()) {
                // Construct download URL
                String downloadUrl = path(String.format(
                    "/chats/%s/messages/%s/hostedContents/%s/$value",
                    chatId, messageId, contentId
                ));
                content.put("downloadUrl", downloadUrl);
            }
            result.put(content);
        }
        
        return result.toString(2);
    }
    
    /**
     * Gets call transcripts for a specific call using the Call Records API.
     * This is the primary way to access meeting transcripts.
     * 
     * @param callId The call ID from the meeting
     * @return JSON array of transcript objects with download URLs
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_call_transcripts",
        description = "Get transcripts for a call/meeting using Call Records API. Returns list of transcripts with download URLs.",
        integration = "teams",
        category = "communication"
    )
    public String getCallTranscripts(
            @MCPParam(name = "callId", description = "Call ID from the meeting", required = true) String callId) throws IOException {
        
        String url = path(String.format("/communications/callRecords/%s/transcripts", callId));
        logger.info("Getting call transcripts from: {}", url);
        
        GenericRequest request = new GenericRequest(this, url);
        String responseStr = execute(request);
        JSONObject response = new JSONObject(responseStr);
        
        JSONArray transcripts = response.optJSONArray("value");
        if (transcripts == null) {
            transcripts = new JSONArray();
        }
        
        // Add download URLs for each transcript
        JSONArray result = new JSONArray();
        for (int i = 0; i < transcripts.length(); i++) {
            JSONObject transcript = transcripts.getJSONObject(i);
            String transcriptId = transcript.optString("id", "");
            if (!transcriptId.isEmpty()) {
                // Construct download URL for the transcript content
                String downloadUrl = path(String.format(
                    "/communications/callRecords/%s/transcripts/%s/content",
                    callId, transcriptId
                ));
                transcript.put("downloadUrl", downloadUrl);
            }
            result.put(transcript);
        }
        
        return result.toString(2);
    }
    
    /**
     * Searches for transcript files in a user's OneDrive.
     * Transcripts are typically stored in OneDrive/Recordings folder.
     * Uses existing Sites.Read.All permission to access files.
     * 
     * @param userId User ID (typically meeting organizer)
     * @param searchQuery Search term (e.g., meeting name, "transcript", ".vtt")
     * @return JSON array of found files with download URLs
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_search_user_drive_files",
        description = "Search for files in a user's OneDrive (e.g., meeting transcripts/recordings). Returns list of files with download URLs.",
        integration = "teams",
        category = "communication"
    )
    public String searchUserDriveFiles(
            @MCPParam(name = "userId", description = "User ID to search OneDrive", required = true) String userId,
            @MCPParam(name = "searchQuery", description = "Search term (meeting name, 'transcript', '.vtt', etc.)", required = true) String searchQuery) throws IOException {
        
        // URL encode the search query
        String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
        String url = path(String.format("/users/%s/drive/root/search(q='%s')", userId, encodedQuery));
        logger.info("Searching user drive: {}", url);
        
        GenericRequest request = new GenericRequest(this, url);
        String responseStr = execute(request);
        JSONObject response = new JSONObject(responseStr);
        
        JSONArray files = response.optJSONArray("value");
        if (files == null) {
            files = new JSONArray();
        }
        
        // Process files and add download information
        JSONArray result = new JSONArray();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            
            // Only include files, not folders
            if (file.has("file")) {
                JSONObject fileInfo = new JSONObject();
                fileInfo.put("name", file.optString("name"));
                fileInfo.put("size", file.optLong("size", 0));
                fileInfo.put("webUrl", file.optString("webUrl"));
                fileInfo.put("downloadUrl", file.optString("@microsoft.graph.downloadUrl"));
                fileInfo.put("id", file.optString("id"));
                fileInfo.put("createdDateTime", file.optString("createdDateTime"));
                fileInfo.put("lastModifiedDateTime", file.optString("lastModifiedDateTime"));
                
                // Add parent path if available
                JSONObject parentRef = file.optJSONObject("parentReference");
                if (parentRef != null) {
                    fileInfo.put("path", parentRef.optString("path", ""));
                }
                
                result.put(fileInfo);
            }
        }
        
        return result.toString(2);
    }
    
    /**
     * Gets transcript metadata for a recording file in OneDrive/SharePoint.
     * Uses SharePoint REST API to access transcript information.
     * 
     * @param driveId Drive ID where the recording is stored
     * @param itemId Item ID of the recording file
     * @return JSON array of transcript objects with download information
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_recording_transcripts",
        description = "Get transcript metadata for a recording file. Returns list of available transcripts with download URLs.",
        integration = "teams",
        category = "communication"
    )
    public String getRecordingTranscripts(
            @MCPParam(name = "driveId", description = "Drive ID from the recording file", required = true) String driveId,
            @MCPParam(name = "itemId", description = "Item ID of the recording file", required = true) String itemId) throws IOException {
        
        // Construct SharePoint REST API URL
        // Format: https://domain-my.sharepoint.com/_api/v2.1/drives/{driveId}/items/{itemId}/media/transcripts
        String url = String.format("https://graph.microsoft.com/v1.0/drives/%s/items/%s", driveId, itemId);
        logger.info("Getting recording transcripts for drive {} item {}", driveId, itemId);
        
        try {
            GenericRequest request = new GenericRequest(this, url);
            String responseStr = execute(request);
            JSONObject item = new JSONObject(responseStr);
            
            // Check if item has media property with transcripts
            JSONObject media = item.optJSONObject("media");
            if (media == null) {
                logger.info("No media property found on item");
                return new JSONArray().toString(2);
            }
            
            // Try to get transcripts (this might require additional Graph API call)
            // For now, return item metadata and construct SharePoint transcript URL
            JSONArray result = new JSONArray();
            JSONObject transcriptInfo = new JSONObject();
            
            // Get parent reference to construct SharePoint site URL
            JSONObject parentRef = item.optJSONObject("parentReference");
            if (parentRef != null) {
                String siteId = parentRef.optString("siteId", "");
                String path = parentRef.optString("path", "");
                
                transcriptInfo.put("driveId", driveId);
                transcriptInfo.put("itemId", itemId);
                transcriptInfo.put("siteId", siteId);
                transcriptInfo.put("path", path);
                transcriptInfo.put("note", "Use teams_download_recording_transcript with driveId, itemId, and transcriptId to download");
            }
            
            result.put(transcriptInfo);
            return result.toString(2);
            
        } catch (Exception e) {
            logger.error("Failed to get transcripts: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("note", "Transcripts may not be available or may require accessing via SharePoint URL directly");
            return new JSONArray().put(error).toString(2);
        }
    }
    
    /**
     * Lists available transcripts for a recording file using direct API call.
     * Attempts to query SharePoint REST API for transcript metadata.
     * 
     * @param driveId Drive ID where the recording is stored
     * @param itemId Item ID of the recording file
     * @return JSON with available transcripts
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_list_recording_transcripts",
        description = "List available transcripts for a recording file. Returns transcript IDs that can be downloaded.",
        integration = "teams",
        category = "communication"
    )
    public String listRecordingTranscripts(
            @MCPParam(name = "driveId", description = "Drive ID", required = true) String driveId,
            @MCPParam(name = "itemId", description = "Recording item ID", required = true) String itemId) throws IOException {
        
        logger.info("Listing transcripts for drive {} item {}", driveId, itemId);
        
        JSONArray attempts = new JSONArray();
        
        // Try multiple possible endpoints
        String[] endpoints = {
            // Try Graph API media endpoint
            String.format("https://graph.microsoft.com/v1.0/drives/%s/items/%s?$expand=media", driveId, itemId),
            // Try direct media endpoint
            String.format("https://graph.microsoft.com/v1.0/drives/%s/items/%s/media", driveId, itemId),
            // Try Graph API with select
            String.format("https://graph.microsoft.com/v1.0/drives/%s/items/%s?$select=id,name,media", driveId, itemId)
        };
        
        for (String url : endpoints) {
            try {
                logger.info("Trying endpoint: {}", url);
                GenericRequest request = new GenericRequest(this, url);
                String responseStr = execute(request);
                
                JSONObject attempt = new JSONObject();
                attempt.put("endpoint", url);
                attempt.put("success", true);
                attempt.put("response", new JSONObject(responseStr));
                attempts.put(attempt);
                
                // If we got a response, try to extract transcript info
                JSONObject response = new JSONObject(responseStr);
                if (response.has("media")) {
                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("media", response.getJSONObject("media"));
                    result.put("note", "Found media property - check for transcript information");
                    return result.toString(2);
                }
                
            } catch (Exception e) {
                JSONObject attempt = new JSONObject();
                attempt.put("endpoint", url);
                attempt.put("success", false);
                attempt.put("error", e.getMessage());
                attempts.put(attempt);
            }
        }
        
        // If no endpoints worked, return summary
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("note", "None of the Graph API endpoints returned transcript information");
        result.put("attempts", attempts);
        result.put("suggestion", "Transcripts may require SharePoint-specific API access or may not be available via Graph API");
        return result.toString(2);
    }
    
    /**
     * Fetches SharePoint page HTML for a recording and attempts to extract transcript information.
     * Parses the HTML to find transcript IDs and download URLs.
     * 
     * @param webUrl SharePoint webUrl of the recording file
     * @return JSON with transcript information extracted from HTML
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_extract_transcript_from_sharepoint",
        description = "Extract transcript information by parsing SharePoint HTML page. Useful for finding transcript IDs.",
        integration = "teams",
        category = "communication"
    )
    public String extractTranscriptFromSharePoint(
            @MCPParam(name = "webUrl", description = "SharePoint webUrl of the recording", required = true) String webUrl) throws IOException {
        
        logger.info("Fetching SharePoint HTML from: {}", webUrl);
        
        try {
            // Fetch the HTML page using authenticated request
            GenericRequest request = new GenericRequest(this, webUrl);
            String html = execute(request);
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("htmlLength", html.length());
            
            // Try to extract transcript-related information from HTML
            JSONArray transcripts = new JSONArray();
            
            // Look for transcript API URLs in the HTML
            // Pattern: /media/transcripts/{uuid}/streamContent
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "/media/transcripts/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
            );
            java.util.regex.Matcher matcher = pattern.matcher(html);
            
            java.util.Set<String> foundIds = new java.util.HashSet<>();
            while (matcher.find()) {
                String transcriptId = matcher.group(1);
                if (foundIds.add(transcriptId)) {
                    JSONObject transcript = new JSONObject();
                    transcript.put("transcriptId", transcriptId);
                    transcript.put("note", "Use teams_download_recording_transcript to download");
                    transcripts.put(transcript);
                }
            }
            
            result.put("transcripts", transcripts);
            result.put("transcriptsFound", transcripts.length());
            
            // Also look for driveId and itemId in the URL
            java.util.regex.Pattern drivePattern = java.util.regex.Pattern.compile("drives/([^/]+)");
            java.util.regex.Matcher driveMatcher = drivePattern.matcher(html);
            if (driveMatcher.find()) {
                result.put("driveId", driveMatcher.group(1));
            }
            
            java.util.regex.Pattern itemPattern = java.util.regex.Pattern.compile("items/([^/]+)");
            java.util.regex.Matcher itemMatcher = itemPattern.matcher(html);
            if (itemMatcher.find()) {
                result.put("itemId", itemMatcher.group(1));
            }
            
            return result.toString(2);
            
        } catch (Exception e) {
            logger.error("Failed to extract transcript info: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error.toString(2);
        }
    }
    
    /**
     * Downloads a transcript file from a recording using SharePoint REST API.
     * This accesses transcripts via the SharePoint _api/v2.1 endpoint.
     * First fetches the item to get the SharePoint site URL, then constructs the proper SharePoint REST API URL.
     * 
     * @param driveId Drive ID where the recording is stored
     * @param itemId Item ID of the recording file
     * @param transcriptId Transcript ID (UUID)
     * @param outputPath Local file path to save the transcript
     * @return JSON with download status
     * @throws IOException if download fails
     */
    @MCPTool(
        name = "teams_download_recording_transcript",
        description = "Download transcript (VTT) file from a Teams recording using SharePoint API. Requires driveId, itemId, and transcriptId.",
        integration = "teams",
        category = "communication"
    )
    public String downloadRecordingTranscript(
            @MCPParam(name = "driveId", description = "Drive ID", required = true) String driveId,
            @MCPParam(name = "itemId", description = "Recording item ID", required = true) String itemId,
            @MCPParam(name = "transcriptId", description = "Transcript ID (UUID)", required = true) String transcriptId,
            @MCPParam(name = "outputPath", description = "Local file path to save", required = true) String outputPath) throws IOException {
        
        logger.info("Downloading transcript {} for item {}", transcriptId, itemId);
        
        try {
            // First, get the item to extract SharePoint site URL
            String itemUrl = String.format("https://graph.microsoft.com/v1.0/drives/%s/items/%s", driveId, itemId);
            GenericRequest itemRequest = new GenericRequest(this, itemUrl);
            String itemResponseStr = execute(itemRequest);
            JSONObject itemResponse = new JSONObject(itemResponseStr);
            
            // Extract SharePoint site URL from webUrl
            String webUrl = itemResponse.optString("webUrl", "");
            if (webUrl.isEmpty()) {
                throw new IOException("Could not get webUrl from item");
            }
            
            // Parse SharePoint site URL (e.g., "https://epam-my.sharepoint.com/personal/ira_skrypnik_epam_com/...")
            // Extract: https://epam-my.sharepoint.com/personal/ira_skrypnik_epam_com
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(https://[^/]+/personal/[^/]+)");
            java.util.regex.Matcher matcher = pattern.matcher(webUrl);
            
            String sharePointSiteUrl;
            if (matcher.find()) {
                sharePointSiteUrl = matcher.group(1);
            } else {
                throw new IOException("Could not extract SharePoint site URL from webUrl: " + webUrl);
            }
            
            // Construct SharePoint REST API URL
            String url = String.format(
                "%s/_api/v2.1/drives/%s/items/%s/media/transcripts/%s/streamContent",
                sharePointSiteUrl, driveId, itemId, transcriptId
            );
            
            logger.info("Using SharePoint REST API URL: {}", url);
            
            // Create output file
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Download using authenticated request
            GenericRequest request = new GenericRequest(this, url);
            File downloadedFile = RestClient.Impl.downloadFile(this, request, outputFile);
            
            // Return success info
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("outputPath", downloadedFile.getAbsolutePath());
            result.put("fileSize", downloadedFile.length());
            result.put("transcriptId", transcriptId);
            result.put("sharePointUrl", url);
            result.put("method", "sharepointRestApi");
            
            logger.info("Downloaded {} bytes to {}", downloadedFile.length(), downloadedFile.getAbsolutePath());
            return result.toString(2);
            
        } catch (Exception e) {
            logger.error("Failed to download transcript: {}", e.getMessage());
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("note", "Transcripts require SharePoint REST API access with proper authentication");
            return error.toString(2);
        }
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
        
        Channel result = matches.getFirst();
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
        // Handle limit parameter: null or 0 means "get all messages"
        // Note: MCP parameter parsing may convert "0" to null, so we need to handle both cases
        boolean getAllMessages = (limit == null || limit == 0);
        int maxMessages = getAllMessages ? Integer.MAX_VALUE : limit;
        
        List<ChatMessage> allMessages = new ArrayList<>();
        String url = path(String.format("/teams/%s/channels/%s/messages", teamId, channelId));
        boolean isFirstRequest = true;
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            // Only add query parameters on first request; @odata.nextLink already includes them
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
        
        logger.info("Retrieved {} messages from channel {} in team {}", allMessages.size(), channelId, teamId);
        return allMessages;
    }
}
