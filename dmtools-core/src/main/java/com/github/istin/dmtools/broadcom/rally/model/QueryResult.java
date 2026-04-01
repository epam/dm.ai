// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class QueryResult extends JSONModel implements IChangelog {

    private static final String RESULTS = "Results";

    public QueryResult() {
    }

    public QueryResult(String json) throws JSONException {
        super(json);
    }

    public QueryResult(JSONObject json) {
        super(json);
    }

    public List<RallyIssue> getIssues() {
        return getModels(RallyIssue.class, RESULTS);
    }

    public List<Revision> getRevisions() {
        return getModels(Revision.class, RESULTS);
    }

    public List<Iteration> getIterations() {
        return getModels(Iteration.class, RESULTS);
    }

    public JSONArray getErrors() {
        return getJSONArray("Errors");
    }

    public int getTotalResultCount() {
        return getInt("TotalResultCount");
    }

    public int getPageSize() {
        return getInt("PageSize");
    }

    @Override
    public List<? extends IHistory> getHistories() {
        List<Revision> revisions = getRevisions();
        Collections.reverse(revisions);
        return revisions;
    }

    public List<? extends IComment> getComments() {
        return getModels(Comment.class, RESULTS);
    }

    public List<FlowState> getFlowStates() {
        return getModels(FlowState.class, RESULTS);
    }

    public List<RallyTag> getTags() {
        return getModels(RallyTag.class, RESULTS);
    }

    public List<? extends IAttachment> getAttachments() {
        return getModels(RallyAttachment.class, RESULTS);
    }
}
