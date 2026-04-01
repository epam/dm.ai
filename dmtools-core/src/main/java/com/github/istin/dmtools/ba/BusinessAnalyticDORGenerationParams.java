// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONException;
import org.json.JSONObject;

public class BusinessAnalyticDORGenerationParams extends BaseJobParams {

    public static final String OUTPUT_CONFLUENCE_PAGE = "outputConfluencePage";

    public BusinessAnalyticDORGenerationParams() {
    }

    public BusinessAnalyticDORGenerationParams(String json) throws JSONException {
        super(json);
    }

    public BusinessAnalyticDORGenerationParams(JSONObject json) {
        super(json);
    }


    public String getOutputConfluencePage() {
        return getString(OUTPUT_CONFLUENCE_PAGE);
    }

    public BaseJobParams setOutputConfluencePage(String outputConfluencePage) {
        set(OUTPUT_CONFLUENCE_PAGE, outputConfluencePage);
        return this;
    }
}