package com.github.istin.dmtools.microsoft.teams.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Microsoft Teams chat.
 */
public class Chat extends JSONModel {
    
    public Chat() {
    }
    
    public Chat(String json) {
        super(json);
    }
    
    public Chat(JSONObject json) {
        super(json);
    }
    
    /**
     * Gets the chat ID.
     */
    public String getId() {
        return getString("id");
    }
    
    /**
     * Gets the chat topic/name.
     */
    public String getTopic() {
        return getString("topic");
    }
    
    /**
     * Gets the chat type (oneOnOne, group, meeting).
     */
    public String getChatType() {
        return getString("chatType");
    }
    
    /**
     * Gets the timestamp when the chat was created.
     */
    public String getCreatedDateTime() {
        return getString("createdDateTime");
    }
    
    /**
     * Gets the timestamp when the chat was last updated.
     */
    public String getLastUpdatedDateTime() {
        return getString("lastUpdatedDateTime");
    }
    
    /**
     * Gets the web URL for the chat.
     */
    public String getWebUrl() {
        return getString("webUrl");
    }
    
    /**
     * Gets the list of members in the chat.
     */
    public List<ChatMember> getMembers() {
        JSONArray membersArray = getJSONObject().optJSONArray("members");
        List<ChatMember> members = new ArrayList<>();
        if (membersArray != null) {
            for (int i = 0; i < membersArray.length(); i++) {
                members.add(new ChatMember(membersArray.getJSONObject(i)));
            }
        }
        return members;
    }
    
    /**
     * Gets the last message preview if available.
     */
    public JSONObject getLastMessagePreview() {
        return getJSONObject().optJSONObject("lastMessagePreview");
    }
    
    /**
     * Gets the body content of the last message preview.
     */
    public String getLastMessageContent() {
        JSONObject preview = getLastMessagePreview();
        if (preview != null) {
            JSONObject body = preview.optJSONObject("body");
            if (body != null) {
                return body.optString("content", "");
            }
        }
        return null;
    }
    
    /**
     * Gets the author name of the last message.
     */
    public String getLastMessageAuthor() {
        JSONObject preview = getLastMessagePreview();
        if (preview != null) {
            JSONObject from = preview.optJSONObject("from");
            if (from != null) {
                JSONObject user = from.optJSONObject("user");
                if (user != null) {
                    return user.optString("displayName", null);
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if the last message has attachments (files, videos, etc.).
     */
    public boolean hasLastMessageAttachments() {
        JSONObject preview = getLastMessagePreview();
        if (preview != null) {
            // Check for attachments field
            if (preview.has("attachments")) {
                org.json.JSONArray attachments = preview.optJSONArray("attachments");
                return attachments != null && attachments.length() > 0;
            }
        }
        return false;
    }
    
    /**
     * Checks if the last message is a system event message.
     * Uses the messageType field from the API - system messages have types like "systemEventMessage".
     * Regular user messages have messageType "message".
     */
    public boolean isLastMessageSystemEvent() {
        JSONObject preview = getLastMessagePreview();
        if (preview != null) {
            // Check messageType field - system messages have specific types different from "message"
            String messageType = preview.optString("messageType", null);
            return messageType != null && !messageType.equals("message");
        }
        return false;
    }
    
    /**
     * Gets the creation date of the last message (most accurate for sorting).
     * This is the actual message delivery date.
     * Returns null if the last message is deleted (to match Teams UI behavior).
     */
    public String getLastMessageCreatedDateTime() {
        JSONObject preview = getLastMessagePreview();
        if (preview != null) {
            // Ignore deleted messages - they shouldn't affect recency sorting
            boolean isDeleted = preview.optBoolean("isDeleted", false);
            if (!isDeleted) {
                return preview.optString("createdDateTime", null);
            }
        }
        return null;
    }
    
    /**
     * Gets the viewpoint information (user's view of the chat).
     */
    public JSONObject getViewpoint() {
        return getJSONObject().optJSONObject("viewpoint");
    }
    
    /**
     * Gets when the user last read messages in this chat.
     */
    public String getLastMessageReadDateTime() {
        JSONObject viewpoint = getViewpoint();
        if (viewpoint != null) {
            return viewpoint.optString("lastMessageReadDateTime", null);
        }
        return null;
    }
    
    /**
     * Checks if the chat is pinned by the user.
     */
    public boolean isPinned() {
        JSONObject viewpoint = getViewpoint();
        if (viewpoint != null) {
            return viewpoint.optBoolean("isHidden", false) == false 
                && viewpoint.optString("isPinned", "false").equals("true");
        }
        return false;
    }
    
    /**
     * Gets the unread message count for this chat.
     */
    public Integer getUnreadMessageCount() {
        if (getJSONObject().has("unreadMessageCount")) {
            return getJSONObject().getInt("unreadMessageCount");
        }
        return null;
    }
    
    /**
     * Checks if the chat has unread messages.
     * Uses unreadMessageCount if available, otherwise defaults to false (read).
     */
    public boolean hasUnreadMessages() {
        // Primary method: check unreadMessageCount field
        Integer unreadCount = getUnreadMessageCount();
        if (unreadCount != null) {
            return unreadCount > 0;
        }
        
        // Secondary: Check if lastMessagePreview has isRead indicator
        JSONObject preview = getLastMessagePreview();
        if (preview != null && preview.has("isRead")) {
            return !preview.optBoolean("isRead", true);
        }
        
        // Tertiary: Fall back to viewpoint timestamp comparison
        JSONObject viewpoint = getViewpoint();
        if (viewpoint != null) {
            String lastRead = viewpoint.optString("lastMessageReadDateTime", null);
            // Use lastMessageCreatedDateTime (actual message date) instead of lastUpdatedDateTime (can be stale)
            String lastMessageDate = getLastMessageCreatedDateTime();
            if (lastMessageDate == null) {
                lastMessageDate = getLastUpdatedDateTime(); // Fallback if no message preview
            }
            
            if (lastRead != null && !lastRead.isEmpty() && lastMessageDate != null && !lastMessageDate.isEmpty()) {
                // Parse timestamps and compare with tolerance
                // If the difference is less than 2 minutes, consider it read (to match Teams UI behavior)
                try {
                    java.time.Instant readTime = java.time.Instant.parse(lastRead);
                    java.time.Instant messageTime = java.time.Instant.parse(lastMessageDate);
                    long secondsDiff = messageTime.getEpochSecond() - readTime.getEpochSecond();
                    
                    // Consider unread only if last message is more than 120 seconds (2 minutes) after lastRead
                    return secondsDiff > 120;
                } catch (Exception e) {
                    // If parsing fails, fall back to simple string comparison
                    return lastMessageDate.compareTo(lastRead) > 0;
                }
            }
        }
        
        // Default to read if we can't determine status
        return false;
    }
    
    /**
     * Represents a chat member.
     */
    public static class ChatMember extends JSONModel {
        public ChatMember(JSONObject json) {
            super(json);
        }
        
        public String getDisplayName() {
            return getString("displayName");
        }
        
        public String getUserId() {
            return getString("userId");
        }
        
        public String getEmail() {
            return getString("email");
        }
    }
}
