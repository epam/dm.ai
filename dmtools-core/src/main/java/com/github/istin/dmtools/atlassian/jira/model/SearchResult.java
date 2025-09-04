package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SearchResult extends JSONModel {

    static final String MAX_RESULTS = "maxResults";
    static final String TOTAL = "total";
    public static final String ISSUES = "issues";
    public static final String ERROR_MESSAGES = "errorMessages";
    public static final String NEXT_PAGE_TOKEN = "nextPageToken";
    public static final String IS_LAST = "isLast";

    public SearchResult() {
    }

    public SearchResult(String json) throws JSONException {
        super(json);
    }

    public SearchResult(JSONObject json) {
        super(json);
    }

    public JSONArray getErrorMessages() {
        return getJSONArray(ERROR_MESSAGES);
    }

    @Deprecated
    public int getMaxResults() {
        return getInt(MAX_RESULTS);
    }

    @Deprecated
    public int getTotal() {
        return getInt(TOTAL);
    }

    public List<Ticket> getIssues() {
        return getModels(Ticket.class, ISSUES);
    }

    public boolean isLast() {
        return getJSONObject().optBoolean(IS_LAST, false);
    }

    public String getNextPageToken() {
        return getString(NEXT_PAGE_TOKEN);
    }

}