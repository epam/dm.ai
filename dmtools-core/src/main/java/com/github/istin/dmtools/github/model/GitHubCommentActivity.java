package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

/**
 * Wraps a GitHubComment (inline review comment) as an IActivity
 * so it can be included in pullRequestActivities results.
 * Extends JSONModel for proper MCP serialization.
 */
public class GitHubCommentActivity extends JSONModel implements IActivity {

    public GitHubCommentActivity(GitHubComment comment) {
        super(buildJSON(comment));
    }

    private static JSONObject buildJSON(GitHubComment comment) {
        JSONObject json = new JSONObject();
        json.put("action", "COMMENTED");
        json.put("comment", comment.getJSONObject());
        return json;
    }

    @Override
    public String getAction() {
        return getString("action");
    }

    @Override
    public IComment getComment() {
        return getModel(GitHubComment.class, "comment");
    }

    @Override
    public IUser getApproval() {
        return null;
    }
}
