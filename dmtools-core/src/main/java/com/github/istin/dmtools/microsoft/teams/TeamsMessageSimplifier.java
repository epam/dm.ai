package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for simplifying Teams chat messages for human-readable output.
 * Converts complex ChatMessage objects into simplified JSON with only essential fields:
 * author, date, body, reactions, mentions, and attachments.
 */
public class TeamsMessageSimplifier {
    
    /**
     * Simplifies a list of chat messages to JSON format.
     * Filters out system messages and returns only regular messages with essential information.
     * 
     * @param messages List of ChatMessage objects to simplify
     * @return JSONArray with simplified message objects
     */
    public static JSONArray simplifyMessages(List<ChatMessage> messages) {
        return simplifyMessages(messages, null);
    }
    
    /**
     * Simplifies a list of chat messages to JSON array format with chat context.
     * Filters out system messages and returns only regular messages with essential information.
     * 
     * @param messages List of ChatMessage objects to simplify
     * @param chatId Optional chat ID for constructing download URLs (needed for transcripts)
     * @return JSONArray with simplified message objects
     */
    public static JSONArray simplifyMessages(List<ChatMessage> messages, String chatId) {
        JSONArray simplified = new JSONArray();
        
        for (ChatMessage message : messages) {
            JSONObject simpleMsg = simplifyMessage(message, chatId);
            if (simpleMsg != null) {
                simplified.put(simpleMsg);
            }
        }
        
        return simplified;
    }
    
    /**
     * Simplifies a single chat message to JSON format.
     * 
     * @param message ChatMessage object to simplify
     * @return JSONObject with simplified message data, or null if message should be skipped (empty system messages)
     */
    public static JSONObject simplifyMessage(ChatMessage message) {
        return simplifyMessage(message, null);
    }
    
    /**
     * Simplifies a single chat message to JSON format with chat context.
     * 
     * @param message ChatMessage object to simplify
     * @param chatId Optional chat ID for constructing download URLs (needed for transcripts)
     * @return JSONObject with simplified message data, or null if message should be skipped (empty system messages)
     */
    public static JSONObject simplifyMessage(ChatMessage message, String chatId) {
        JSONObject simpleMsg = new JSONObject();
        
        // Check if this is a system event message with recording/transcript info
        if (!"message".equals(message.getMessageType())) {
            JSONObject eventDetail = message.getEventDetail();
            if (eventDetail != null) {
                String eventType = eventDetail.optString("@odata.type", "");
                
                // Handle call recording events
                if (eventType.equals("#microsoft.graph.callRecordingEventMessageDetail")) {
                    String recordingUrl = eventDetail.optString("callRecordingUrl", null);
                    if (recordingUrl != null && !recordingUrl.isEmpty()) {
                        simpleMsg.put("date", message.getCreatedDateTime());
                        simpleMsg.put("type", "recording");
                        simpleMsg.put("title", eventDetail.optString("callRecordingDisplayName", "Meeting Recording"));
                        simpleMsg.put("url", recordingUrl);
                        String duration = eventDetail.optString("callRecordingDuration", null);
                        if (duration != null) {
                            simpleMsg.put("duration", duration);
                        }
                        return simpleMsg;
                    }
                }
                
                // Handle call transcript events
                if (eventType.equals("#microsoft.graph.callTranscriptEventMessageDetail")) {
                    simpleMsg.put("date", message.getCreatedDateTime());
                    simpleMsg.put("type", "transcript");
                    simpleMsg.put("title", "Meeting Transcript");
                    
                    // Include IDs for reference
                    String transcriptICalUid = eventDetail.optString("callTranscriptICalUid", null);
                    if (transcriptICalUid != null) {
                        simpleMsg.put("transcriptICalUid", transcriptICalUid);
                    }
                    
                    String messageId = message.getId();
                    if (messageId != null) {
                        simpleMsg.put("messageId", messageId);
                    }
                    
                    // Construct download URL if chatId is available
                    if (chatId != null && messageId != null) {
                        simpleMsg.put("chatId", chatId);
                        // URL to get hosted contents (transcript file)
                        String hostedContentsUrl = String.format(
                            "https://graph.microsoft.com/v1.0/chats/%s/messages/%s/hostedContents",
                            chatId, messageId
                        );
                        simpleMsg.put("hostedContentsUrl", hostedContentsUrl);
                    }
                    
                    return simpleMsg;
                }
            }
            
            // Skip other system messages
            return null;
        }
        
        // Process regular messages
        
        // Author
        ChatMessage.Sender from = message.getFrom();
        if (from != null) {
            simpleMsg.put("author", from.getDisplayName());
        } else {
            simpleMsg.put("author", "Unknown");
        }
        
        // Date
        String date = message.getCreatedDateTime();
        simpleMsg.put("date", date != null ? date : "");
        
        // Body (clean HTML tags for readability)
        String body = message.getContent();
        if (body != null && !body.isEmpty()) {
            body = cleanHtml(body);
            simpleMsg.put("body", body);
        } else {
            simpleMsg.put("body", "");
        }
        
        // Reactions (if any)
        List<ChatMessage.Reaction> reactions = message.getReactions();
        if (reactions != null && !reactions.isEmpty()) {
            JSONArray reactionsArray = extractReactions(reactions);
            if (reactionsArray.length() > 0) {
                simpleMsg.put("reactions", reactionsArray);
            }
        }
        
        // Mentions (if any)
        List<ChatMessage.Mention> mentions = message.getMentions();
        if (mentions != null && !mentions.isEmpty()) {
            JSONArray mentionsArray = extractMentions(mentions);
            if (mentionsArray.length() > 0) {
                simpleMsg.put("mentions", mentionsArray);
            }
        }
        
        // Attachments (if any)
        List<ChatMessage.Attachment> attachments = message.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            JSONArray attachmentsArray = extractAttachments(attachments);
            if (attachmentsArray.length() > 0) {
                simpleMsg.put("attachments", attachmentsArray);
            }
        }
        
        return simpleMsg;
    }
    
    /**
     * Cleans HTML content by removing tags and decoding common HTML entities.
     * 
     * @param html HTML string to clean
     * @return Plain text with HTML removed
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        return html
            .replaceAll("<[^>]+>", "")       // Remove HTML tags
            .replaceAll("&nbsp;", " ")        // Decode non-breaking space
            .replaceAll("&lt;", "<")          // Decode less than
            .replaceAll("&gt;", ">")          // Decode greater than
            .replaceAll("&amp;", "&")         // Decode ampersand
            .replaceAll("&quot;", "\"")       // Decode quote
            .replaceAll("&#39;", "'")         // Decode apostrophe
            .trim();
    }
    
    /**
     * Extracts and groups reactions by type, showing counts for duplicates.
     * 
     * @param reactions List of reactions
     * @return JSONArray with formatted reactions (e.g., "❤️ ×3", "👍")
     */
    public static JSONArray extractReactions(List<ChatMessage.Reaction> reactions) {
        JSONArray reactionsArray = new JSONArray();
        
        // Group reactions by type and count them
        Map<String, Integer> reactionCounts = new HashMap<>();
        for (ChatMessage.Reaction reaction : reactions) {
            String type = reaction.getReactionType();
            if (type != null && !type.isEmpty()) {
                reactionCounts.put(type, reactionCounts.getOrDefault(type, 0) + 1);
            }
        }
        
        // Format reactions with counts
        for (Map.Entry<String, Integer> entry : reactionCounts.entrySet()) {
            String display = entry.getValue() > 1 
                ? entry.getKey() + " ×" + entry.getValue() 
                : entry.getKey();
            reactionsArray.put(display);
        }
        
        return reactionsArray;
    }
    
    /**
     * Extracts mention display names from a list of mentions.
     * 
     * @param mentions List of mentions
     * @return JSONArray with user display names
     */
    public static JSONArray extractMentions(List<ChatMessage.Mention> mentions) {
        JSONArray mentionsArray = new JSONArray();
        
        for (ChatMessage.Mention mention : mentions) {
            if (mention.getMentioned() != null) {
                ChatMessage.Sender user = mention.getMentioned().getUser();
                if (user != null) {
                    String displayName = user.getDisplayName();
                    if (displayName != null && !displayName.isEmpty()) {
                        mentionsArray.put(displayName);
                    }
                }
            }
        }
        
        return mentionsArray;
    }
    
    /**
     * Extracts and formats attachment information with smart handling for different types:
     * - messageReference (replies): Shows preview text
     * - adaptive cards: Extracts card title
     * - other attachments: Shows name, content type, and URL (if available)
     * 
     * @param attachments List of attachments
     * @return JSONArray with formatted attachment objects
     */
    public static JSONArray extractAttachments(List<ChatMessage.Attachment> attachments) {
        JSONArray attachmentsArray = new JSONArray();
        
        for (ChatMessage.Attachment attachment : attachments) {
            String contentType = attachment.getContentType();
            String name = attachment.getName();
            String contentUrl = attachment.getContentUrl();
            
            // Handle messageReference (replies) - simple text format
            if ("messageReference".equals(contentType)) {
                String replyText = extractReplyPreview(attachment);
                if (replyText != null) {
                    attachmentsArray.put(replyText);
                    continue;
                }
            }
            
            // Handle adaptive cards (YouTube, links, etc.) - simple text format
            if ("application/vnd.microsoft.card.adaptive".equals(contentType)) {
                String cardText = extractAdaptiveCardTitle(attachment);
                if (cardText != null) {
                    attachmentsArray.put(cardText);
                    continue;
                }
                attachmentsArray.put("Adaptive Card");
                continue;
            }
            
            // For actual file attachments, return structured object with URL
            JSONObject attachmentInfo = new JSONObject();
            if (name != null && !name.isEmpty()) {
                attachmentInfo.put("name", name);
            }
            if (contentType != null && !contentType.isEmpty()) {
                attachmentInfo.put("type", contentType);
            }
            if (contentUrl != null && !contentUrl.isEmpty()) {
                attachmentInfo.put("url", contentUrl);
            }
            
            // If we have structured data, add the object; otherwise fall back to simple string
            if (attachmentInfo.length() > 0) {
                attachmentsArray.put(attachmentInfo);
            } else {
                attachmentsArray.put(contentType != null ? contentType : "Unknown attachment");
            }
        }
        
        return attachmentsArray;
    }
    
    /**
     * Extracts preview text from a messageReference attachment (reply).
     * 
     * @param attachment Attachment with messageReference content
     * @return Formatted reply preview, or null if extraction fails
     */
    private static String extractReplyPreview(ChatMessage.Attachment attachment) {
        try {
            String content = attachment.getContent();
            if (content != null && !content.isEmpty()) {
                JSONObject contentJson = new JSONObject(content);
                String preview = contentJson.optString("messagePreview", "");
                if (!preview.isEmpty()) {
                    // Truncate long previews
                    if (preview.length() > 100) {
                        preview = preview.substring(0, 97) + "...";
                    }
                    return "Reply: " + preview;
                } else {
                    return "Reply to message";
                }
            }
        } catch (Exception e) {
            // Fall through to return null
        }
        return null;
    }
    
    /**
     * Extracts title from an adaptive card attachment.
     * 
     * @param attachment Attachment with adaptive card content
     * @return Formatted card title, or null if extraction fails
     */
    private static String extractAdaptiveCardTitle(ChatMessage.Attachment attachment) {
        try {
            String cardContent = attachment.getContent();
            if (cardContent != null && !cardContent.isEmpty()) {
                JSONObject cardJson = new JSONObject(cardContent);
                JSONArray cardBody = cardJson.optJSONArray("body");
                if (cardBody != null) {
                    // Look for the title TextBlock (bolder weight)
                    for (int i = 0; i < cardBody.length(); i++) {
                        JSONObject block = cardBody.optJSONObject(i);
                        if (block != null && "TextBlock".equals(block.optString("type"))) {
                            String text = block.optString("text", "");
                            if (!text.isEmpty() && "bolder".equals(block.optString("weight"))) {
                                return "Card: " + text;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to return null
        }
        return null;
    }
}

