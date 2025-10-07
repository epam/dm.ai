package com.github.istin.dmtools.microsoft.teams.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Microsoft Teams chat message.
 */
public class ChatMessage extends JSONModel {
    
    public ChatMessage() {
    }
    
    public ChatMessage(String json) {
        super(json);
    }
    
    public ChatMessage(JSONObject json) {
        super(json);
    }
    
    /**
     * Gets the message ID.
     */
    public String getId() {
        return getString("id");
    }
    
    /**
     * Gets the timestamp when the message was created.
     */
    public String getCreatedDateTime() {
        return getString("createdDateTime");
    }
    
    /**
     * Gets the timestamp when the message was last modified.
     */
    public String getLastModifiedDateTime() {
        return getString("lastModifiedDateTime");
    }
    
    /**
     * Gets the message body content.
     */
    public String getContent() {
        JSONObject body = getJSONObject().optJSONObject("body");
        if (body != null) {
            return body.optString("content", "");
        }
        return "";
    }
    
    /**
     * Gets the message body content type (text, html).
     */
    public String getContentType() {
        JSONObject body = getJSONObject().optJSONObject("body");
        if (body != null) {
            return body.optString("contentType", "text");
        }
        return "text";
    }
    
    /**
     * Gets the sender information.
     */
    public Sender getFrom() {
        JSONObject from = getJSONObject().optJSONObject("from");
        if (from != null) {
            return new Sender(from);
        }
        return null;
    }
    
    /**
     * Gets the message type (message, systemEventMessage, etc.).
     */
    public String getMessageType() {
        return getString("messageType");
    }
    
    /**
     * Gets the list of attachments.
     */
    public List<Attachment> getAttachments() {
        JSONArray attachmentsArray = getJSONObject().optJSONArray("attachments");
        List<Attachment> attachments = new ArrayList<>();
        if (attachmentsArray != null) {
            for (int i = 0; i < attachmentsArray.length(); i++) {
                attachments.add(new Attachment(attachmentsArray.getJSONObject(i)));
            }
        }
        return attachments;
    }
    
    /**
     * Gets the web URL for the message.
     */
    public String getWebUrl() {
        return getString("webUrl");
    }
    
    /**
     * Strips HTML tags from content to get plain text.
     */
    public String getPlainTextContent() {
        String content = getContent();
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Basic HTML stripping (preserves line breaks)
        return content
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<div>", "\n")
                .replaceAll("</div>", "")
                .replaceAll("<p>", "\n")
                .replaceAll("</p>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .trim();
    }
    
    /**
     * Represents a message sender.
     */
    public static class Sender extends JSONModel {
        public Sender(JSONObject json) {
            super(json);
        }
        
        public String getDisplayName() {
            JSONObject user = getJSONObject().optJSONObject("user");
            if (user != null) {
                return user.optString("displayName", "");
            }
            JSONObject application = getJSONObject().optJSONObject("application");
            if (application != null) {
                return application.optString("displayName", "");
            }
            return "";
        }
        
        public String getUserId() {
            JSONObject user = getJSONObject().optJSONObject("user");
            if (user != null) {
                return user.optString("id", "");
            }
            return "";
        }
        
        public String getEmail() {
            JSONObject user = getJSONObject().optJSONObject("user");
            if (user != null) {
                String email = user.optString("userPrincipalName", "");
                if (!email.isEmpty()) {
                    return email;
                }
                email = user.optString("mail", "");
                if (!email.isEmpty()) {
                    return email;
                }
            }
            return "";
        }
        
        public boolean isApplication() {
            return getJSONObject().has("application");
        }
    }
    
    /**
     * Represents a message attachment.
     */
    public static class Attachment extends JSONModel {
        public Attachment(JSONObject json) {
            super(json);
        }
        
        public String getId() {
            return getString("id");
        }
        
        public String getName() {
            return getString("name");
        }
        
        public String getContentType() {
            return getString("contentType");
        }
        
        public String getContentUrl() {
            return getString("contentUrl");
        }
    }
}
