// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CommentsResult extends JSONModel {

    private static final String MAX_RESULTS = "maxResults";
    private static final String TOTAL = "total";
    private static final String START_AT = "startAt";
    public static final String COMMENTS = "comments";

    public CommentsResult() {
    }

    public CommentsResult(String json) throws JSONException {
        super(json);
    }

    public CommentsResult(JSONObject json) {
        super(json);
    }


    public int getMaxResults() {
        return getInt(MAX_RESULTS);
    }

    public int getTotal() {
        return getInt(TOTAL);
    }

    public List<Comment> getComments() {
        return getModels(Comment.class, COMMENTS);
    }

}
