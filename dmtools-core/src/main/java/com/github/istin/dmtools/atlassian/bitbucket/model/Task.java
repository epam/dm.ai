// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.ITask;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Task extends JSONModel implements ITask {

    public Task() {
    }

    public Task(String json) throws JSONException {
        super(json);
    }

    public Task(JSONObject json) {
        super(json);
    }

    public String getText() {
        JSONObject content = getJSONObject("content");
        if (content != null) {
            String raw = content.getString("raw");
            if (raw != null) {
                return raw;
            }
        }

        return getString("text");
    }

    public String getState() {
        return getString("state");
    }

    @Override
    public String getBody() {
        return getText();
    }

    @Override
    public IUser getAuthor() {
        Assignee author = getModel(Assignee.class, "author");
        if (author == null) {
            author = getModel(Assignee.class, "user");
        }
        return author;
    }
}