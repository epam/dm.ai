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
