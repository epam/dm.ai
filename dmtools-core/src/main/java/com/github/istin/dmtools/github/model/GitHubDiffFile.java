// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IChange;
import org.json.JSONException;
import org.json.JSONObject;

public class GitHubDiffFile extends GitHubFile implements IChange {

    public GitHubDiffFile() {

    }

    public GitHubDiffFile(String json) throws JSONException {
        super(json);
    }

    public GitHubDiffFile(JSONObject json) {
        super(json);
    }

    public String getPatch() {
        return getString("patch");
    }

    @Override
    public String getFilePath() {
        return getPath();
    }
}