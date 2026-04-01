// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ba;

import com.github.istin.dmtools.job.BaseJobParams;
import org.json.JSONException;
import org.json.JSONObject;

public class UserStoryGeneratorParams extends BaseJobParams {

    public static final String EXISTING_USER_STORIES_JQL = "existingUserStoriesJql";
    public static final String OUTPUT_TYPE = "outputType";
    public static final String PRIORITIES = "priorities";

    public static final String OUTPUT_TYPE_TRACKER_COMMENT = "trackerComment";
    public static final String OUTPUT_TYPE_TRACKER_CREATION = "creation";
    public static final String PARENT_FIELD = "parentField";
    private static final String PROJECT_CODE = "projectCode";
    private static final String ISSUE_TYPE = "issueType";
    public static final String ACCEPTANCE_CRITERIA_FIELD = "acceptanceCriteriaField";
    public static final String RELATIONSHIP = "relationship";

    public UserStoryGeneratorParams() {
    }

    public UserStoryGeneratorParams(String json) throws JSONException {
        super(json);
    }

    public UserStoryGeneratorParams(JSONObject json) {
        super(json);
    }


    public String getExistingUserStoriesJql() {
        return getString(EXISTING_USER_STORIES_JQL);
    }

    public String getOutputType() {
        return getString(OUTPUT_TYPE);
    }

    public String getPriorities() {
        return getString(PRIORITIES);
    }

    public String getProjectCode() {
        return getString(PROJECT_CODE);
    }

    public String getIssueType() {
        return getString(ISSUE_TYPE);
    }

    public String getAcceptanceCriteriaField() {
        return getString(ACCEPTANCE_CRITERIA_FIELD);
    }

    public String getRelationship() {
        return getString(RELATIONSHIP);
    }

    public UserStoryGeneratorParams setExistingUserStoriesJql(String existingUserStoriesJql) {
        set(EXISTING_USER_STORIES_JQL, existingUserStoriesJql);
        return this;
    }

    public UserStoryGeneratorParams setOutputType(String outputType) {
        set(OUTPUT_TYPE, outputType);
        return this;
    }

    public UserStoryGeneratorParams setPriorities(String priorities) {
        set(PRIORITIES, priorities);
        return this;
    }

    public UserStoryGeneratorParams setProjectCode(String projectCode) {
        set(PROJECT_CODE, projectCode);
        return this;
    }

    public UserStoryGeneratorParams setIssueType(String issueType) {
        set(ISSUE_TYPE, issueType);
        return this;
    }

    public UserStoryGeneratorParams setAcceptanceCriteriaField(String acceptanceCriteriaField) {
        set(ACCEPTANCE_CRITERIA_FIELD, acceptanceCriteriaField);
        return this;
    }

    public UserStoryGeneratorParams setRelationship(String relationship) {
        set(RELATIONSHIP, relationship);
        return this;
    }

    public String getParentField() {
        return getString(PARENT_FIELD);

    }
}