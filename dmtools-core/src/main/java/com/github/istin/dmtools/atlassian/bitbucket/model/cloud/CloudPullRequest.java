// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model.cloud;

import com.github.istin.dmtools.atlassian.bitbucket.model.PullRequest;
import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class CloudPullRequest extends PullRequest {

    public CloudPullRequest() {
    }

    public CloudPullRequest(String json) throws JSONException {
        super(json);
    }

    public CloudPullRequest(JSONObject json) {
        super(json);
    }


    @Override
    public Assignee getAuthor() {
        JSONObject authorObject = getJSONObject().getJSONObject("author");
        return new Assignee(authorObject);
    }

    @Override
    public String getTargetBranchName() {
        return getJSONObject("destination").getJSONObject("branch").getString("name");
    }

    @Override
    public String getSourceBranchName() {
        return getJSONObject("source").getJSONObject("branch").getString("name");
    }

    @Override
    public Long getCreatedDate() {
        String createdOnAsString = getString("created_on");
        Date date = DateUtils.parseCloudBitbucketDate(createdOnAsString);
        return date.getTime();
    }

    @Override
    public Long getClosedDate() {
        String createdOnAsString = getString("updated_on");
        Date date = DateUtils.parseCloudBitbucketDate(createdOnAsString);
        return date.getTime();
    }

    @Override
    public Long getUpdatedDate() {
        String createdOnAsString = getString("updated_on");
        Date date = DateUtils.parseCloudBitbucketDate(createdOnAsString);
        return date.getTime();
    }

}