// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class GithubTag extends JSONModel implements ITag {

    public GithubTag() {
    }

    public GithubTag(String json) throws JSONException {
        super(json);
    }

    public GithubTag(JSONObject json) {
        super(json);
    }

    @Override
    public String getName() {
        return getString("name");
    }

}