package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class GitHubArtifact extends JSONModel {

    public GitHubArtifact() {
    }

    public GitHubArtifact(String json) throws JSONException {
        super(json);
    }

    public GitHubArtifact(JSONObject json) {
        super(json);
    }

    public Long getId() {
        return getLong("id");
    }

    public String getName() {
        return getString("name");
    }

    public String getArchiveDownloadUrl() {
        return getString("archive_download_url");
    }

    public Long getSize() {
        return getLong("size_in_bytes");
    }

    public String getCreatedAt() {
        return getString("created_at");
    }

    public String getUpdatedAt() {
        return getString("updated_at");
    }

    public Boolean isExpired() {
        return getBoolean("expired");
    }
}
