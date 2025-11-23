package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Represents a single history entry (update) for an Azure DevOps work item.
 */
public class WorkItemHistory extends JSONModel implements IHistory {

    public WorkItemHistory() {
    }

    public WorkItemHistory(String json) throws JSONException {
        super(json);
    }

    public WorkItemHistory(JSONObject json) {
        super(json);
    }

    /**
     * Get the list of field changes for this history entry.
     * 
     * <p><b>Note:</b> Due to ADO API limitations, this method currently returns an empty list.
     * The ADO updates API doesn't directly provide old/new values in a single call.
     * To get field-level change details, you would need to:
     * 1. Fetch the current revision (this history entry)
     * 2. Fetch the previous revision
     * 3. Compare the field values between revisions
     * 
     * <p>This comparison logic is not currently implemented. The changelog still provides
     * useful information (author, timestamp, revision number) even without field-level details.
     * 
     * @return Empty list - field-level change details are not available
     */
    @Override
    public List<? extends IHistoryItem> getHistoryItems() {
        // ADO updates API doesn't directly provide old/new values in a single call
        // We would need to compare revisions to determine what changed.
        // For now, return empty list as field-level change details are not available.
        // The changelog still provides useful metadata (author, timestamp, revision number).
        return new ArrayList<>();
    }
    
    /**
     * Get the fields object from this history entry.
     */
    public JSONObject getFields() {
        return getJSONObject("fields");
    }

    @Override
    public IUser getAuthor() {
        JSONObject revisedBy = getJSONObject("revisedBy");
        if (revisedBy != null) {
            String displayName = revisedBy.optString("displayName");
            if (displayName == null || displayName.isEmpty()) {
                displayName = revisedBy.optString("uniqueName", "Unknown");
            }
            return new HistoryUser(displayName);
        }
        return null;
    }

    @Override
    public Calendar getCreated() {
        String revisedDate = getString("revisedDate");
        if (revisedDate != null) {
            Date date = DateUtils.smartParseDate(revisedDate);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                return cal;
            }
        }
        return null;
    }

    /**
     * Get revision number.
     */
    public int getRevision() {
        return getInt("rev");
    }

    /**
     * Get the work item ID.
     */
    public int getWorkItemId() {
        return getInt("id");
    }

    /**
     * Simple User implementation for history authors.
     */
    private static class HistoryUser implements IUser {
        private final String displayName;

        public HistoryUser(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getID() {
            return displayName;
        }

        @Override
        public String getFullName() {
            return displayName;
        }

        @Override
        public String getEmailAddress() {
            return null;
        }
    }
}

