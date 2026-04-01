// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProductivityDataReportParams extends ProductivityJobParams {

    public static final String COMMENT_PATTERNS = "comment_patterns";
    public static final String COLLECT_REQUESTS = "collect_requests";
    public static final String REQUEST_EXTRACTION_PATTERN = "request_extraction_pattern";

    public ProductivityDataReportParams() {
    }

    public ProductivityDataReportParams(String json) throws JSONException {
        super(json);
    }

    public ProductivityDataReportParams(JSONObject json) {
        super(json);
    }

    public Map<String, String> getCommentPatterns() {
        JSONObject patternsJson = getJSONObject(COMMENT_PATTERNS);
        if (patternsJson == null) {
            return new HashMap<>();
        }
        Map<String, String> patterns = new HashMap<>();
        for (String key : patternsJson.keySet()) {
            patterns.put(key, patternsJson.getString(key));
        }
        return patterns;
    }

    public void setCommentPatterns(Map<String, String> patterns) {
        JSONObject patternsJson = new JSONObject();
        for (Map.Entry<String, String> entry : patterns.entrySet()) {
            patternsJson.put(entry.getKey(), entry.getValue());
        }
        set(COMMENT_PATTERNS, patternsJson);
    }

    public Boolean isCollectRequests() {
        return getBoolean(COLLECT_REQUESTS);
    }

    public void setCollectRequests(boolean collectRequests) {
        set(COLLECT_REQUESTS, collectRequests);
    }

    public String getRequestExtractionPattern() {
        return getString(REQUEST_EXTRACTION_PATTERN);
    }

    public void setRequestExtractionPattern(String pattern) {
        set(REQUEST_EXTRACTION_PATTERN, pattern);
    }
}

