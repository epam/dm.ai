package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Represents an Azure DevOps work item comment.
 */
public class WorkItemComment extends JSONModel implements IComment {

    public WorkItemComment() {
    }

    public WorkItemComment(String json) throws JSONException {
        super(json);
    }

    public WorkItemComment(JSONObject json) {
        super(json);
    }

    @Override
    public String getBody() {
        return getString("text");
    }

    @Override
    public IUser getAuthor() {
        JSONObject createdBy = getJSONObject("createdBy");
        if (createdBy != null) {
            return new WorkItemCommentAuthor(createdBy);
        }
        return null;
    }

    @Override
    public String getId() {
        return getString("id");
    }

    @Override
    public Date getCreated() {
        String createdDate = getString("createdDate");
        if (createdDate != null) {
            return DateUtils.smartParseDate(createdDate);
        }
        return null;
    }

    /**
     * Simple User implementation for comment authors.
     */
    private static class WorkItemCommentAuthor implements IUser {
        private final JSONObject userData;

        public WorkItemCommentAuthor(JSONObject userData) {
            this.userData = userData;
        }

        @Override
        public String getID() {
            return userData.optString("id");
        }

        @Override
        public String getFullName() {
            return userData.optString("displayName");
        }

        @Override
        public String getEmailAddress() {
            return userData.optString("uniqueName");
        }
    }
}

