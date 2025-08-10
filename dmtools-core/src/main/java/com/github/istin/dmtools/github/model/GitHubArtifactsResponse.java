package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class GitHubArtifactsResponse extends JSONModel {

    public GitHubArtifactsResponse() {
    }

    public GitHubArtifactsResponse(String json) throws JSONException {
        super(json);
    }

    public GitHubArtifactsResponse(JSONObject json) {
        super(json);
    }

    public Integer getTotalCount() {
        return getInt("total_count");
    }

    public List<GitHubArtifact> getArtifacts() {
        return getModels(GitHubArtifact.class, "artifacts");
    }
}
