package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.Chat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for simplifying Teams chats for human-readable output.
 * Converts complex Chat objects into simplified JSON with only essential fields:
 * name, lastMessage, lastUpdated, and new (unread) indicator.
 */
public class TeamsChatsSimplifier {
    
    /**
     * Simplifies a list of chats to JSON format with basic info.
     * 
     * @param chats List of Chat objects to simplify
     * @param currentUserDisplayName Current user's display name (to exclude from 1-on-1 chat names)
     * @return JSONArray with simplified chat objects
     */
    public static JSONArray simplifyChats(List<Chat> chats, String currentUserDisplayName) {
        JSONArray simplified = new JSONArray();
        
        for (Chat chat : chats) {
            JSONObject chatInfo = simplifyChat(chat, false, currentUserDisplayName);
            simplified.put(chatInfo);
        }
        
        return simplified;
    }
    
    /**
     * Simplifies a single chat to JSON format.
     * 
     * @param chat Chat object to simplify
     * @param includeAuthor If true, includes author name as separate field (for recent chats)
     * @param currentUserDisplayName Current user's display name (to exclude from 1-on-1 chat names)
     * @return JSONObject with simplified chat data
     */
    public static JSONObject simplifyChat(Chat chat, boolean includeAuthor, String currentUserDisplayName) {
        JSONObject chatInfo = new JSONObject();
        
        // Get chat name or contact names (for 1-on-1, show only the other person)
        String displayName = getChatDisplayName(chat, currentUserDisplayName);
        chatInfo.put("chatName", displayName);
        
        // Get last message content
        String lastMessage = chat.getLastMessageContent();
        String author = chat.getLastMessageAuthor();
        
        if (includeAuthor) {
            // For recent chats: separate author and message fields
            if (author != null && !author.isEmpty()) {
                chatInfo.put("author", author);
            }
            lastMessage = formatLastMessageContent(chat);
        } else {
            // For all chats: truncate to 100 chars
            if (lastMessage != null && !lastMessage.isEmpty()) {
                lastMessage = TeamsMessageSimplifier.cleanHtml(lastMessage);
                if (lastMessage.length() > 100) {
                    lastMessage = lastMessage.substring(0, 100) + "...";
                }
            } else {
                lastMessage = "";
            }
        }
        chatInfo.put("lastMessage", lastMessage);
        
        // Get last updated date
        String lastDate = chat.getLastUpdatedDateTime();
        chatInfo.put("lastUpdated", lastDate != null ? lastDate : "");
        
        // Add "new": true ONLY if unread (omit if read)
        if (chat.hasUnreadMessages()) {
            chatInfo.put("new", true);
        }
        
        return chatInfo;
    }
    
    /**
     * Gets recent chats sorted by last activity with filtering options.
     * 
     * @param chats List of all chats
     * @param limit Maximum number of recent chats to return
     * @param chatType Filter by chat type: "oneOnOne", "group", "meeting", or "all"
     * @param cutoffDays Number of days to look back for active chats (default: 90)
     * @param currentUserDisplayName Current user's display name (to exclude from 1-on-1 chat names)
     * @return JSONArray with simplified, sorted, filtered chat list
     */
    public static JSONArray getRecentChatsSimplified(List<Chat> chats, int limit, String chatType, int cutoffDays, String currentUserDisplayName) {
        // Calculate cutoff date
        Instant cutoffDate = Instant.now().minus(cutoffDays, ChronoUnit.DAYS);
        
        // Sort by last activity (most recent first)
        List<Chat> sortedChats = new ArrayList<>(chats);
        sortedChats.sort(Comparator.comparing(
            (Chat c) -> {
                String date = c.getLastMessageCreatedDateTime();
                if (date == null) date = c.getLastUpdatedDateTime();
                return date != null ? date : "";
            },
            Comparator.reverseOrder() // descending order (most recent first)
        ));
        
        // Filter out old inactive chats, chats with only system messages, and empty messages
        List<Chat> activeChats = new ArrayList<>();
        for (Chat chat : sortedChats) {
            // Filter system events with no content
            if (chat.isLastMessageSystemEvent()) {
                String content = chat.getLastMessageContent();
                boolean hasAttachments = chat.hasLastMessageAttachments();
                // Skip if no content (or only <systemEventMessage/> placeholder) and no attachments
                boolean isEmpty = content == null || content.trim().isEmpty() || content.equals("<systemEventMessage/>");
                if (isEmpty && !hasAttachments) {
                    continue;
                }
            }
            
            // Skip chats with empty last message content UNLESS they have attachments
            String lastMessageContent = chat.getLastMessageContent();
            boolean hasAttachments = chat.hasLastMessageAttachments();
            if ((lastMessageContent == null || lastMessageContent.trim().isEmpty()) && !hasAttachments) {
                continue;
            }
            
            // Filter by date
            String lastActivityDate = chat.getLastMessageCreatedDateTime();
            if (lastActivityDate == null) {
                lastActivityDate = chat.getLastUpdatedDateTime();
            }
            
            if (lastActivityDate != null) {
                try {
                    Instant activityTime = Instant.parse(lastActivityDate);
                    if (activityTime.isBefore(cutoffDate)) {
                        continue; // Skip old chats
                    }
                } catch (Exception e) {
                    // If date parsing fails, include the chat anyway
                }
            }
            
            activeChats.add(chat);
        }
        
        // Filter by chatType if specified
        if (chatType != null && !chatType.trim().isEmpty() && !chatType.equalsIgnoreCase("all")) {
            List<Chat> filteredChats = new ArrayList<>();
            for (Chat chat : activeChats) {
                if (chatType.equalsIgnoreCase(chat.getChatType())) {
                    filteredChats.add(chat);
                    // limit = 0 means unlimited
                    if (limit > 0 && filteredChats.size() >= limit) {
                        break;
                    }
                }
            }
            activeChats = filteredChats;
        }
        
        // Return only the requested number of chats (limit = 0 means all)
        int effectiveLimit = (limit <= 0) ? activeChats.size() : limit;
        List<Chat> recentChats = activeChats.subList(0, Math.min(effectiveLimit, activeChats.size()));
        
        // Convert to simplified format with author names
        JSONArray simplified = new JSONArray();
        for (Chat chat : recentChats) {
            JSONObject chatInfo = simplifyChat(chat, true, currentUserDisplayName); // includeAuthor = true
            simplified.put(chatInfo);
        }
        
        return simplified;
    }
    
    /**
     * Gets a display name for a chat (topic for group chats, contact names for 1-on-1).
     * For 1-on-1 chats, shows only the OTHER person's name (excludes the current user).
     * 
     * @param chat Chat object
     * @param currentUserDisplayName Current user's display name to exclude
     * @return Display name string
     */
    private static String getChatDisplayName(Chat chat, String currentUserDisplayName) {
        String topic = chat.getTopic();
        if (topic != null && !topic.isEmpty()) {
            return topic;
        }
        
        // For 1-on-1 chats without topic, use participant names
        List<Chat.ChatMember> members = chat.getMembers();
        if (members != null && !members.isEmpty()) {
            // For 1-on-1 chats (exactly 2 members), show only the OTHER person's name
            if (members.size() == 2 && currentUserDisplayName != null && !currentUserDisplayName.isEmpty()) {
                // Find the member who is NOT the current user
                for (Chat.ChatMember member : members) {
                    String memberName = member.getDisplayName();
                    if (memberName != null && !memberName.equals(currentUserDisplayName)) {
                        return memberName;
                    }
                }
            }
            
            // Fallback: show all member names (for group chats or if we can't determine current user)
            List<String> names = new ArrayList<>();
            for (Chat.ChatMember member : members) {
                String name = member.getDisplayName();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
            if (!names.isEmpty()) {
                return String.join(", ", names);
            }
        }
        
        return "Unnamed Chat";
    }
    
    /**
     * Formats last message content for recent chats view (without author, just cleaned message).
     * 
     * @param chat Chat object
     * @return Formatted message string (cleaned HTML)
     */
    private static String formatLastMessageContent(Chat chat) {
        String lastMessage = chat.getLastMessageContent();
        boolean hasAttachments = chat.hasLastMessageAttachments();
        boolean isSystemEvent = chat.isLastMessageSystemEvent();
        
        String formattedMessage;
        if (lastMessage != null && !lastMessage.isEmpty() && !lastMessage.equals("<systemEventMessage/>")) {
            formattedMessage = TeamsMessageSimplifier.cleanHtml(lastMessage);
        } else if (hasAttachments) {
            // Show placeholder for attachment-only messages (videos, files, etc.)
            formattedMessage = "[Attachment]";
        } else if (isSystemEvent) {
            // System event with no real content (like meeting ended notification)
            formattedMessage = "[System Event]";
        } else {
            formattedMessage = "";
        }
        
        return formattedMessage;
    }
}

