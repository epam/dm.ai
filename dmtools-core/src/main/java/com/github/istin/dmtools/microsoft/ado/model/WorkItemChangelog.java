package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents Azure DevOps work item changelog (history of updates).
 */
public class WorkItemChangelog extends JSONModel implements IChangelog {

    public WorkItemChangelog() {
    }

    public WorkItemChangelog(String json) throws JSONException {
        super(json);
    }

    public WorkItemChangelog(JSONObject json) {
        super(json);
    }

    @Override
    public List<? extends IHistory> getHistories() {
        List<WorkItemHistory> histories = new ArrayList<>();
        
        // ADO returns updates as an array in the "value" field
        JSONArray updates = getJSONArray("value");
        if (updates == null) {
            // If it's a single update object, wrap it
            JSONObject singleUpdate = getJSONObject("value");
            if (singleUpdate != null) {
                updates = new JSONArray();
                updates.put(singleUpdate);
            } else {
                // Try direct array
                updates = getJSONArray("updates");
            }
        }
        
        if (updates != null) {
            for (int i = 0; i < updates.length(); i++) {
                try {
                    JSONObject update = updates.getJSONObject(i);
                    histories.add(new WorkItemHistory(update));
                } catch (org.json.JSONException e) {
                    // Skip invalid entries
                    continue;
                }
            }
        }
        
        return histories;
    }
}

