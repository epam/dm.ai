// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class BaseJobParams extends JSONModel {

    public static final String INPUT_JQL = "inputJql";
    public static final String INITIATOR = "initiator";
    public static final String CONFLUENCE_PAGES = "confluencePages";
    public static final String MODEL = "model";

    public BaseJobParams() {
    }

    public BaseJobParams(String json) throws JSONException {
        super(json);
    }

    public BaseJobParams(JSONObject json) {
        super(json);
    }

    public String getInputJQL() {
        return getString(INPUT_JQL);
    }

    public String getInitiator() {
        return getString(INITIATOR);
    }

    public String getModel() {
        return getString(MODEL);
    }

    public String[] getConfluencePages() {
        return getStringArray(CONFLUENCE_PAGES);
    }

    public BaseJobParams setInputJQL(String inputJQL) {
        set(INPUT_JQL, inputJQL);
        return this;
    }

    public BaseJobParams setConfluencePages(String... confluencePages) {
        setArray(CONFLUENCE_PAGES, confluencePages);
        return this;
    }

    public BaseJobParams setInitiator(String initiator) {
        set(INITIATOR, initiator);
        return this;
    }

    public BaseJobParams setModel(String model) {
        set(MODEL, model);
        return this;
    }

}