package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import io.github.furstenheim.CopyDown;
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
        
        // Check if this is a system event message
        // NOTE: Noisy system messages are filtered at the performer level (TeamsClient.shouldSkipNoisyMessage)
        // This simplifier only handles messages that passed the filter
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
                    
                    // Include call ID for Call Records API
                    String callId = eventDetail.optString("callId", null);
                    if (callId != null) {
                        simpleMsg.put("callId", callId);
                    }
                    
                    // Include meeting organizer ID for onlineMeetings API
                    JSONObject meetingOrganizer = eventDetail.optJSONObject("meetingOrganizer");
                    if (meetingOrganizer != null) {
                        JSONObject user = meetingOrganizer.optJSONObject("user");
                        if (user != null) {
                            String organizerId = user.optString("id", null);
                            if (organizerId != null) {
                                simpleMsg.put("organizerId", organizerId);
                            }
                        }
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
                
                // Include other system messages with basic information
                // (Noisy events have already been filtered at the performer level)
                simpleMsg.put("date", message.getCreatedDateTime());
                simpleMsg.put("type", "system");
                simpleMsg.put("messageType", message.getMessageType());
                simpleMsg.put("eventType", eventType.replace("#microsoft.graph.", ""));
                // Include basic event detail for context
                simpleMsg.put("eventDetail", eventDetail);
                
                return simpleMsg;
            }
            
            // If no eventDetail, skip the system message
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
        List<String> extractedImages = null;
        if (body != null && !body.isEmpty()) {
            // Extract images before cleaning HTML
            extractedImages = extractImageUrls(body);
            body = cleanHtml(body);
        }
        
        // If body is empty or only contained attachment placeholders, try to extract from adaptive cards
        if (body == null || body.trim().isEmpty()) {
            List<ChatMessage.Attachment> messageAttachments = message.getAttachments();
            String cardContent = extractAdaptiveCardContent(messageAttachments);
            if (cardContent != null && !cardContent.isEmpty()) {
                simpleMsg.put("body", cardContent);
                body = cardContent; // Set for downstream processing
            } else {
                simpleMsg.put("body", "");
            }
        } else {
            simpleMsg.put("body", body);
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
        JSONArray attachmentsArray = new JSONArray();
        
        // Add extracted images from body first
        if (extractedImages != null && !extractedImages.isEmpty()) {
            for (String imageUrl : extractedImages) {
                JSONObject imageAttachment = new JSONObject();
                imageAttachment.put("type", "image");
                imageAttachment.put("url", imageUrl);
                attachmentsArray.put(imageAttachment);
            }
        }
        
        // Add regular attachments
        if (attachments != null && !attachments.isEmpty()) {
            JSONArray regularAttachments = extractAttachments(attachments);
            for (int i = 0; i < regularAttachments.length(); i++) {
                attachmentsArray.put(regularAttachments.get(i));
            }
        }
        
        if (attachmentsArray.length() > 0) {
            simpleMsg.put("attachments", attachmentsArray);
        }
        
        return simpleMsg;
    }
    
    /**
     * Extracts image URLs from HTML content.
     * Looks for <img> tags and extracts their src attributes.
     * Skips hostedContents URLs as they require authentication and are not publicly accessible.
     * 
     * @param html HTML string to extract images from
     * @return List of image URLs, or empty list if none found
     */
    private static List<String> extractImageUrls(String html) {
        List<String> imageUrls = new java.util.ArrayList<>();
        if (html == null || html.isEmpty()) {
            return imageUrls;
        }
        
        // Simple regex to extract img src attributes
        // Matches: <img ... src="URL" ... >
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<img[^>]+src=\"([^\"]+)\"[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        
        while (matcher.find()) {
            String url = matcher.group(1);
            // Skip hostedContents URLs - they require Teams authentication and aren't publicly accessible
            // These inline pasted images will remain in the markdown body but won't be added as separate attachments
            if (url != null && !url.isEmpty() && !url.contains("/hostedContents/")) {
                imageUrls.add(url);
            }
        }
        
        return imageUrls;
    }
    
    /**
     * Converts HTML content to Markdown using CopyDown library.
     * This preserves links as [text](URL) format and handles other HTML elements properly.
     * 
     * @param html HTML string to convert
     * @return Markdown-formatted text
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        try {
            // Use CopyDown to convert HTML to Markdown
            CopyDown converter = new CopyDown();
            String markdown = converter.convert(html);
            
            // Clean up excessive whitespace and trim
            return markdown
                .replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n")  // Reduce multiple blank lines to max 2
                .trim();
        } catch (Exception e) {
            // Fallback to simple HTML stripping if conversion fails
            return html
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .trim();
        }
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
            
            // Skip adaptive cards - their content is extracted into the message body
            if ("application/vnd.microsoft.card.adaptive".equals(contentType)) {
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
     * Includes the author of the message being replied to.
     * 
     * @param attachment Attachment with messageReference content
     * @return Formatted reply preview with author, or null if extraction fails
     */
    private static String extractReplyPreview(ChatMessage.Attachment attachment) {
        try {
            String content = attachment.getContent();
            if (content != null && !content.isEmpty()) {
                JSONObject contentJson = new JSONObject(content);
                String preview = contentJson.optString("messagePreview", "");
                
                // Extract the author of the message being replied to
                String authorName = null;
                JSONObject messageSender = contentJson.optJSONObject("messageSender");
                if (messageSender != null) {
                    JSONObject user = messageSender.optJSONObject("user");
                    if (user != null) {
                        authorName = user.optString("displayName", null);
                    }
                }
                
                // Format the reply text
                StringBuilder replyText = new StringBuilder("Reply");
                if (authorName != null && !authorName.isEmpty()) {
                    replyText.append(" to ").append(authorName);
                }
                
                if (!preview.isEmpty()) {
                    // Truncate long previews
                    if (preview.length() > 100) {
                        preview = preview.substring(0, 97) + "...";
                    }
                    replyText.append(": ").append(preview);
                } else {
                    // If no preview but we have an author, still show the reply
                    if (authorName == null) {
                        return "Reply to message";
                    }
                }
                
                return replyText.toString();
            }
        } catch (Exception e) {
            // Fall through to return null
        }
        return null;
    }
    
    /**
     * Recursively extracts content from adaptive card body elements.
     * Handles TextBlock, ChoiceSet, Container, Column, ColumnSet structures.
     * 
     * @param elements JSONArray of card body elements
     * @param content StringBuilder to append extracted content to
     */
    private static void extractCardBodyContent(JSONArray elements, StringBuilder content) {
        if (elements == null) {
            return;
        }
        
        for (int i = 0; i < elements.length(); i++) {
            JSONObject block = elements.optJSONObject(i);
            if (block == null) {
                continue;
            }
            
            String blockType = block.optString("type", "");
            
            // Extract text from TextBlock elements
            if ("TextBlock".equals(blockType)) {
                String text = block.optString("text", "");
                if (!text.isEmpty()) {
                    // Bold text (questions/titles) - make prominent
                    if ("bolder".equals(block.optString("weight"))) {
                        content.append("**").append(text).append("**\n");
                    } else {
                        content.append(text).append("\n");
                    }
                }
            }
            
            // Extract choices from ChoiceSet (poll options - initial question view)
            else if ("Input.ChoiceSet".equals(blockType)) {
                JSONArray choices = block.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    for (int j = 0; j < choices.length(); j++) {
                        JSONObject choice = choices.optJSONObject(j);
                        if (choice != null) {
                            String title = choice.optString("title", "");
                            if (!title.isEmpty()) {
                                content.append("  - ").append(title).append("\n");
                            }
                        }
                    }
                }
            }
            
            // Recursively process Container elements (poll results view)
            else if ("Container".equals(blockType)) {
                JSONArray items = block.optJSONArray("items");
                if (items != null) {
                    extractCardBodyContent(items, content);
                }
            }
            
            // Recursively process ColumnSet elements (poll results view)
            else if ("ColumnSet".equals(blockType)) {
                JSONArray columns = block.optJSONArray("columns");
                if (columns != null) {
                    extractCardBodyContent(columns, content);
                }
            }
            
            // Recursively process Column elements (poll results view)
            else if ("Column".equals(blockType)) {
                JSONArray items = block.optJSONArray("items");
                if (items != null) {
                    extractCardBodyContent(items, content);
                }
            }
        }
    }
    
    /**
     * Extracts full content from adaptive card attachments (e.g., polls).
     * Parses the JSON structure to extract questions, options, and other text.
     * 
     * @param attachments List of attachments to search
     * @return Formatted card content as string, or null if not found
     */
    private static String extractAdaptiveCardContent(List<ChatMessage.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        
        for (ChatMessage.Attachment attachment : attachments) {
            if ("application/vnd.microsoft.card.adaptive".equals(attachment.getContentType())) {
                try {
                    String cardContentStr = attachment.getContent();
                    if (cardContentStr != null && !cardContentStr.isEmpty()) {
                        JSONObject cardJson = new JSONObject(cardContentStr);
                        StringBuilder content = new StringBuilder();
                        
                        // Extract body elements
                        JSONArray cardBody = cardJson.optJSONArray("body");
                        if (cardBody != null) {
                            extractCardBodyContent(cardBody, content);
                        }
                        
                        String result = content.toString().trim();
                        if (!result.isEmpty()) {
                            return "[Poll/Card] " + result;
                        }
                    }
                } catch (Exception e) {
                    // Fall through to try next attachment
                }
            }
        }
        
        return null;
    }
    
}

