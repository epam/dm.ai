package com.github.istin.dmtools.microsoft.teams.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

/**
 * Represents a Microsoft Teams channel.
 */
public class Channel extends JSONModel {
    
    public Channel() {
    }
    
    public Channel(String json) {
        super(json);
    }
    
    public Channel(JSONObject json) {
        super(json);
    }
    
    /**
     * Gets the channel ID.
     */
    public String getId() {
        return getString("id");
    }
    
    /**
     * Gets the channel display name.
     */
    public String getDisplayName() {
        return getString("displayName");
    }
    
    /**
     * Gets the channel description.
     */
    public String getDescription() {
        return getString("description");
    }
    
    /**
     * Gets the channel membership type (standard, private, shared).
     */
    public String getMembershipType() {
        return getString("membershipType");
    }
    
    /**
     * Gets the web URL for the channel.
     */
    public String getWebUrl() {
        return getString("webUrl");
    }
    
    /**
     * Gets the timestamp when the channel was created.
     */
    public String getCreatedDateTime() {
        return getString("createdDateTime");
    }
}
