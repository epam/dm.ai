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
        name = "teams_get_chats",
        description = "List all chats for the current user with topic, type, and participant information",
        integration = "teams",
        category = "communication"
    )
    public List<Chat> getChats() throws IOException {
        List<Chat> allChats = new ArrayList<>();
        String url = path("/me/chats");
        
        while (url != null) {
            GenericRequest request = new GenericRequest(this, url);
            String response = execute(request);
            JSONObject json = new JSONObject(response);
            
            JSONArray chats = json.optJSONArray("value");
            if (chats != null) {
                for (int i = 0; i < chats.length(); i++) {
                    allChats.add(new Chat(chats.getJSONObject(i)));
                }
            }
            
            // Check for next page
            url = json.optString("@odata.nextLink", null);
        }
        
        logger.info("Retrieved {} chats", allChats.size());
        return allChats;
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
        name = "teams_get_messages",
        description = "Get messages from a chat by ID with sender info, content, timestamps, and attachments",
        integration = "teams",
        category = "communication"
    )
    public List<ChatMessage> getChatMessages(
            @MCPParam(name = "chatId", description = "The chat ID", required = true) String chatId,
            @MCPParam(name = "limit", description = "Maximum number of messages (0 for all, default: 100)", required = false, example = "100") Integer limit) throws IOException {
        
        int maxMessages = (limit != null && limit > 0) ? limit : DEFAULT_MAX_MESSAGES;
        boolean getAllMessages = (limit != null && limit == 0);
        
        List<ChatMessage> allMessages = new ArrayList<>();
        String url = path(String.format("/me/chats/%s/messages", chatId));
        
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
        
        logger.info("Retrieved {} messages from chat {}", allMessages.size(), chatId);
        return allMessages;
    }
    
    // ========== Chat Operations (Name-Based Access) ==========
    
    /**
     * Finds a chat by topic/name (case-insensitive partial match).
     * 
     * @param chatName The chat name to search for
     * @return The first matching chat, or null if not found
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_find_chat_by_name",
        description = "Find a chat by topic/name (case-insensitive partial match)",
        integration = "teams",
        category = "communication"
    )
    public Chat findChatByName(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName) throws IOException {
        
        List<Chat> allChats = getChats();
        List<Chat> matches = new ArrayList<>();
        
        String searchLower = chatName.toLowerCase();
        for (Chat chat : allChats) {
            String topic = chat.getTopic();
            if (topic != null && topic.toLowerCase().contains(searchLower)) {
                matches.add(chat);
            }
        }
        
        if (matches.isEmpty()) {
            logger.warn("No chat found with name: {}", chatName);
            return null;
        }
        
        if (matches.size() > 1) {
            logger.warn("Multiple chats found matching '{}', returning first match", chatName);
        }
        
        Chat result = matches.get(0);
        logger.info("Found chat: {} (ID: {})", result.getTopic(), result.getId());
        return result;
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
        name = "teams_get_messages_by_name",
        description = "Get messages from a chat by name (combines find + get messages)",
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
        name = "teams_send_message",
        description = "Send a message to a chat by ID",
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
        description = "Send a message to a chat by name (combines find + send)",
        integration = "teams",
        category = "communication"
    )
    public ChatMessage sendChatMessageByName(
            @MCPParam(name = "chatName", description = "The chat name to search for", required = true) String chatName,
            @MCPParam(name = "content", description = "Message content (plain text or HTML)", required = true) String content) throws IOException {
        
        Chat chat = findChatByName(chatName);
        if (chat == null) {
            logger.error("Cannot send message: chat '{}' not found", chatName);
            return null;
        }
        
        return sendChatMessage(chat.getId(), content);
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
    
    // ========== Teams and Channels Operations ==========
    
    /**
     * Retrieves teams the user is a member of.
     * 
     * @return List of teams
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "teams_get_joined_teams",
        description = "List teams the user is a member of",
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
        name = "teams_get_team_channels",
        description = "Get channels in a specific team",
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
        name = "teams_find_team_by_name",
        description = "Find a team by display name (case-insensitive partial match)",
        integration = "teams",
        category = "communication"
    )
    public Team findTeamByName(
            @MCPParam(name = "teamName", description = "The team name to search for", required = true) String teamName) throws IOException {
        
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
        name = "teams_find_channel_by_name",
        description = "Find a channel by name within a team (case-insensitive partial match)",
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
        name = "teams_get_channel_messages_by_name",
        description = "Get messages from a channel by team and channel names",
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
        int maxMessages = (limit != null && limit > 0) ? limit : DEFAULT_MAX_MESSAGES;
        boolean getAllMessages = (limit != null && limit == 0);
        
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
