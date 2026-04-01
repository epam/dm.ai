// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ContentResult extends JSONModel {

    public static final String RESULTS = "results";

    public ContentResult() {
    }

    public ContentResult(String json) throws JSONException {
        super(json);
    }

    public ContentResult(JSONObject json) {
        super(json);
    }


    public List<Content> getContents() {
        return getModels(Content.class, RESULTS);
    }

    public List<Attachment> getAttachments() {
        return getModels(Attachment.class, RESULTS);
    }
}