// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.testrail;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Typed accessor wrapper over the generic {@code params} JSONObject for TestRail adapter configuration.
 * Defines all TestRail-specific key constants. Future custom trackers ship their own equivalent class.
 */
public class TestRailAdapterParams {

    public static final String PROJECT_NAMES = "projectNames";
    public static final String PROJECT_IDS = "projectIds";
    public static final String CREATION_MODE = "creationMode";
    public static final String TYPE_ID = "typeId";
    public static final String LABEL_IDS = "labelIds";
    public static final String TARGET_PROJECT = "targetProject";
    public static final String TARGET_PROJECT_ID = "targetProjectId";
    public static final String TYPE_NAME   = "typeName";   // e.g. "Functional"
    public static final String LABEL_NAMES = "labelNames"; // e.g. "ai_generated,Login"

    private final JSONObject raw;

    public TestRailAdapterParams(JSONObject raw) {
        this.raw = raw != null ? raw : new JSONObject();
    }

    /**
     * Returns the list of TestRail project names to operate on.
     */
    public String[] getProjectNames() {
        JSONArray arr = raw.optJSONArray(PROJECT_NAMES);
        if (arr == null || arr.length() == 0) {
            return new String[0];
        }
        String[] names = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            names[i] = arr.optString(i);
        }
        return names;
    }

    /**
     * Returns the list of TestRail project IDs to operate on.
     * Use when the project name resolution is unreliable (e.g. large instances
     * where the project sits beyond the first page of results).
     */
    public int[] getProjectIds() {
        JSONArray arr = raw.optJSONArray(PROJECT_IDS);
        if (arr == null || arr.length() == 0) return new int[0];
        int[] ids = new int[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            ids[i] = arr.optInt(i, -1);
        }
        return ids;
    }

    /**
     * Returns the test case creation mode: "simple", "detailed", or "steps".
     * Defaults to "simple".
     */
    public String getCreationMode() {
        return raw.optString(CREATION_MODE, "simple");
    }

    /**
     * Returns the TestRail case type ID, or null if not set.
     */
    public String getTypeId() {
        String val = raw.optString(TYPE_ID, null);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    /**
     * Returns the comma-separated label IDs string, or null if not set.
     */
    public String getLabelIds() {
        String val = raw.optString(LABEL_IDS, null);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    /**
     * Returns an optional override for the target project name.
     * When set, all test cases are created in this project regardless of the source ticket's project.
     */
    public String getTargetProject() {
        String val = raw.optString(TARGET_PROJECT, null);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    /**
     * Returns an optional override for the target project ID.
     * Takes precedence over {@link #getTargetProject()} when both are set.
     */
    public Integer getTargetProjectId() {
        if (!raw.has(TARGET_PROJECT_ID)) return null;
        int val = raw.optInt(TARGET_PROJECT_ID, -1);
        return val > 0 ? val : null;
    }

    /**
     * Returns the human-readable case type name (e.g. "Functional"), or null if not set.
     * Used when typeId is absent; resolved to a numeric ID at runtime.
     */
    public String getTypeName() {
        return raw.optString(TYPE_NAME, null);
    }

    /**
     * Returns label names as an array.
     * Supports both JSON array (["ai_generated", "Login"]) and legacy comma-separated string.
     */
    public String[] getLabelNames() {
        JSONArray arr = raw.optJSONArray(LABEL_NAMES);
        if (arr != null) {
            String[] names = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                names[i] = arr.optString(i);
            }
            return names;
        }
        // fallback: legacy comma-separated string
        String str = raw.optString(LABEL_NAMES, null);
        if (str == null || str.isEmpty()) return new String[0];
        String[] parts = str.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }
}
