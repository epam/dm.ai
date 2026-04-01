// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class TransitionsResult extends JSONModel {

    public static final String TRANSITIONS = "transitions";

    public TransitionsResult() {
    }

    public TransitionsResult(String json) throws JSONException {
        super(json);
    }

    public TransitionsResult(JSONObject json) {
        super(json);
    }

    public List<Transition> getTransitions() {
        return getModels(Transition.class, TRANSITIONS);
    }

}