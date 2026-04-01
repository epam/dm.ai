// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Activity extends JSONModel implements IActivity {

    public Activity() {
    }

    public Activity(String json) throws JSONException {
        super(json);
    }

    public Activity(JSONObject json) {
        super(json);
    }

    @Override
    public String getAction() {
        return getString("action");
    }

    @Override
    public IComment getComment() {
        return getModel(Comment.class, "comment");
    }

    @Override
    public IUser getApproval() {
        Approval approval = getModel(Approval.class, "approval");
        if (approval == null) {
            return null;
        }
        return approval.getUser();
    }

}