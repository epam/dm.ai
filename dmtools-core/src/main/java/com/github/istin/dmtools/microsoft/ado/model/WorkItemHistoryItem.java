package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single field change in a work item history entry.
 */
public class WorkItemHistoryItem extends JSONModel implements IHistoryItem {

    private String fieldName;
    private String fromValue;
    private String toValue;

    public WorkItemHistoryItem() {
    }

    public WorkItemHistoryItem(String json) throws JSONException {
        super(json);
    }

    public WorkItemHistoryItem(JSONObject json) {
        super(json);
    }

    /**
     * Create a history item from field change data.
     */
    public WorkItemHistoryItem(String fieldName, String fromValue, String toValue) {
        this.fieldName = fieldName;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }

    @Override
    public String getField() {
        if (fieldName != null) {
            return fieldName;
        }
        // Try to get from JSON if available
        String field = getString("field");
        return field != null ? field : "";
    }

    @Override
    public String getFromAsString() {
        if (fromValue != null) {
            return fromValue;
        }
        // Try to get from JSON if available
        String from = getString("from");
        if (from == null) {
            from = getString("fromString");
        }
        return from != null ? from : "";
    }

    @Override
    public String getToAsString() {
        if (toValue != null) {
            return toValue;
        }
        // Try to get from JSON if available
        String to = getString("to");
        if (to == null) {
            to = getString("toString");
        }
        return to != null ? to : "";
    }
}

