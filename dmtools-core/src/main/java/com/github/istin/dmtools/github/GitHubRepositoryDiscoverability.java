// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.JSONResourceReader;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitHubRepositoryDiscoverability extends JSONModel {

    public static final String DEFAULT_RESOURCE = "github-repository-discoverability.json";

    public GitHubRepositoryDiscoverability() {
    }

    public GitHubRepositoryDiscoverability(String json) {
        super(json);
    }

    public GitHubRepositoryDiscoverability(JSONObject json) {
        super(json);
    }

    public static GitHubRepositoryDiscoverability loadDefault() {
        JSONObject jsonObject = JSONResourceReader.getInstance(DEFAULT_RESOURCE).getJsonObject();
        if (jsonObject == null) {
            throw new IllegalStateException("GitHub repository discoverability resource is missing: " + DEFAULT_RESOURCE);
        }
        return new GitHubRepositoryDiscoverability(jsonObject.toString());
    }

    public String getShortDescription() {
        return getString("shortDescription");
    }

    public String getAboutText() {
        return getString("aboutText");
    }

    public String[] getTopics() {
        return getStringArrayOrEmpty("topics");
    }

    public String[] getSupportingKeywords() {
        return getStringArrayOrEmpty("supportingKeywords");
    }

    public String getSocialPreviewDirection() {
        return getNestedString("socialPreview", "direction");
    }

    public String getSocialPreviewValueLine() {
        return getNestedString("socialPreview", "valueLine");
    }

    public String[] getSocialPreviewStyleRules() {
        return getNestedStringArray("socialPreview", "styleRules");
    }

    private String getNestedString(String objectKey, String valueKey) {
        JSONObject nestedObject = getJSONObject(objectKey);
        if (nestedObject == null || nestedObject.isNull(valueKey)) {
            return null;
        }
        return nestedObject.optString(valueKey, null);
    }

    private String[] getNestedStringArray(String objectKey, String arrayKey) {
        JSONObject nestedObject = getJSONObject(objectKey);
        if (nestedObject == null) {
            return new String[0];
        }
        JSONArray jsonArray = nestedObject.optJSONArray(arrayKey);
        if (jsonArray == null) {
            return new String[0];
        }
        String[] values = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            values[i] = jsonArray.optString(i, null);
        }
        return values;
    }

    private String[] getStringArrayOrEmpty(String key) {
        String[] values = getStringArray(key);
        return values == null ? new String[0] : values;
    }
}
