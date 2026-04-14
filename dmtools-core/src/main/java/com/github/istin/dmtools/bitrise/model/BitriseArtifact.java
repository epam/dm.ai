// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise.model;

import org.json.JSONObject;

public class BitriseArtifact {

    private final JSONObject json;

    public BitriseArtifact(JSONObject json) {
        this.json = json;
    }

    public BitriseArtifact(String json) {
        this(new JSONObject(json));
    }

    public String getSlug() {
        return json.optString("slug", null);
    }

    public String getTitle() {
        return json.optString("title", null);
    }

    public String getArtifactType() {
        return json.optString("artifact_type", null);
    }

    public String getDownloadUrl() {
        return json.optString("expiring_download_url", null);
    }

    public long getFileSizeBytes() {
        return json.optLong("file_size_bytes", 0L);
    }

    public boolean isPublicPageEnabled() {
        return json.optBoolean("is_public_page_enabled", false);
    }

    public String getPublicInstallPageUrl() {
        return json.optString("public_install_page_url", null);
    }

    public JSONObject toJSON() {
        return json;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
