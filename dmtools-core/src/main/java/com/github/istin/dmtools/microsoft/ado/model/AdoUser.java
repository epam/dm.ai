package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an Azure DevOps user profile.
 * Implements IUser interface for compatibility with common tracker models.
 */
public class AdoUser extends JSONModel implements IUser {

    public AdoUser() {
    }

    public AdoUser(String json) throws JSONException {
        super(json);
    }

    public AdoUser(JSONObject json) {
        super(json);
    }

    @Override
    public String getID() {
        // Try different possible fields for user ID
        String id = getString("id");
        if (id == null) {
            id = getString("descriptor");
        }
        if (id == null) {
            id = getString("uniqueName");
        }
        if (id == null) {
            // Fallback to displayName if no ID found
            id = getDisplayName();
        }
        return id;
    }

    @Override
    public String getFullName() {
        return getDisplayName();
    }

    @Override
    public String getEmailAddress() {
        String email = getString("emailAddress");
        if (email == null) {
            email = getString("mailAddress");
        }
        if (email == null) {
            email = getString("uniqueName");
        }
        return email;
    }

    /**
     * Get display name from various possible fields.
     */
    public String getDisplayName() {
        String displayName = getString("displayName");
        if (displayName == null) {
            displayName = getString("display_name");
        }
        if (displayName == null) {
            // Try to get from nested object
            JSONObject authenticatedUser = getJSONObject("authenticatedUser");
            if (authenticatedUser != null) {
                displayName = authenticatedUser.optString("displayName");
            }
        }
        if (displayName == null) {
            displayName = getString("name");
        }
        return displayName != null ? displayName : "Unknown User";
    }

    /**
     * Get unique name (usually email).
     */
    public String getUniqueName() {
        return getString("uniqueName");
    }

    /**
     * Get descriptor (ADO user identifier).
     */
    public String getDescriptor() {
        return getString("descriptor");
    }
}


