package com.github.istin.dmtools.microsoft.teams.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

/**
 * Represents a Microsoft Teams team.
 */
public class Team extends JSONModel {
    
    public Team() {
    }
    
    public Team(String json) {
        super(json);
    }
    
    public Team(JSONObject json) {
        super(json);
    }
    
    /**
     * Gets the team ID.
     */
    public String getId() {
        return getString("id");
    }
    
    /**
     * Gets the team display name.
     */
    public String getDisplayName() {
        return getString("displayName");
    }
    
    /**
     * Gets the team description.
     */
    public String getDescription() {
        return getString("description");
    }
    
    /**
     * Gets the web URL for the team.
     */
    public String getWebUrl() {
        return getString("webUrl");
    }
    
    /**
     * Gets whether the team is archived.
     */
    public boolean isArchived() {
        return getJSONObject().optBoolean("isArchived", false);
    }
}
