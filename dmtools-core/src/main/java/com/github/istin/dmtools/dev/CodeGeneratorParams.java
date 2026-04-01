// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CodeGeneratorParams extends BaseJobParams {

    public static final String CONFLUENCE_ROOT_PAGE = "confluenceRootPage";
    public static final String EACH_PAGE_PREFIX = "eachPagePrefix";
    public static final String SOURCES = "sources";
    public static final String ROLE = "role";

    public CodeGeneratorParams() {

    }

    public CodeGeneratorParams(String json) throws JSONException {
        super(json);
    }

    public CodeGeneratorParams(JSONObject json) {
        super(json);
    }

    public String getConfluenceRootPage() {
        return getString(CONFLUENCE_ROOT_PAGE);
    }

    public String getEachPagePrefix() {
        return getString(EACH_PAGE_PREFIX);
    }

    public JSONArray getSources() {
        return getJSONArray(SOURCES);
    }

    public String getRole() {
        return getString(ROLE);
    }

}