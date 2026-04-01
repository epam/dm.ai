// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class IssueType extends JSONModel {

    public enum Type {
        Test
    }

    public IssueType() {
    }

    public IssueType(String json) throws JSONException {
        super(json);
    }

    public IssueType(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    public String getName() {
        return getString("name");
    }

    public boolean isBug() {
        return isBug(getName());
    }

    public static boolean isBug(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("bug") || lowerCaseName.contains("defect") || lowerCaseName.contains("incident");
    }

    public static boolean isTestCase(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("test") || lowerCaseName.contains("test case")  || lowerCaseName.contains("testcase");
    }

    public static boolean isTask(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("task");
    }

    public static boolean isSubTask(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("sub-task");
    }

    public static boolean isExternalDelivery(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("external delivery");
    }

    public static boolean isEpic(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("epic");
    }

    public static boolean isStory(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("story");
    }

    public boolean isEpic() {
        String name = getName().toLowerCase();
        return name.contains("epic");
    }

    public boolean isTest() {
        String name = getName().toLowerCase();
        return name.contains("test");
    }

    public boolean isStory() {
        String name = getName().toLowerCase();
        return isStory(name) || isTask(name) && !name.contains("design") && !name.contains("automation") && !name.contains("configuration");
    }

    public boolean isProductStory() {
        if (isStory() && getName().toLowerCase().contains("product")) {
            return true;
        }
        return false;
    }


    public boolean isQuestion() {
        String name = getName().toLowerCase();
        return name.contains("question");
    }

    public boolean isDependency() {
        String name = getName().toLowerCase();
        return name.contains("dependency");
    }

    public boolean isClarification() {
        String name = getName().toLowerCase();
        return name.contains("clarification");
    }

    public boolean isDesignTask() {
        String name = getName().toLowerCase();
        return name.contains("design");
    }

    public boolean isImpactAssessment() {
        String name = getName().toLowerCase();
        return name.contains("impact assessment");
    }

    public boolean isConfigurationTask() {
        String name = getName().toLowerCase();
        return name.contains("configuration task");
    }
}