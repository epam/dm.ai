package com.github.istin.dmtools.github.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GitHub PR review thread (conversation) - a group of inline code comments
 * on the same code location. Includes the root comment and all replies.
 */
public class GitHubConversation {

    private final GitHubComment rootComment;
    private final List<GitHubComment> replies;

    public GitHubConversation(GitHubComment rootComment) {
        this.rootComment = rootComment;
        this.replies = new ArrayList<>();
    }

    public void addReply(GitHubComment reply) {
        replies.add(reply);
    }

    public GitHubComment getRootComment() {
        return rootComment;
    }

    public List<GitHubComment> getReplies() {
        return replies;
    }

    public String getPath() {
        return rootComment.getPath();
    }

    public String getFilePath() {
        return rootComment.getPath();
    }

    public int getTotalComments() {
        return 1 + replies.size();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("path", rootComment.getPath());
        json.put("rootComment", rootComment.getJSONObject());
        JSONArray repliesArray = new JSONArray();
        for (GitHubComment reply : replies) {
            repliesArray.put(reply.getJSONObject());
        }
        json.put("replies", repliesArray);
        json.put("totalComments", getTotalComments());
        return json;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }
}
