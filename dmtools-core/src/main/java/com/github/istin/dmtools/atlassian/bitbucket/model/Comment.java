// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class Comment extends JSONModel implements IComment {

    public Comment() {
    }

    public Comment(String json) throws JSONException {
        super(json);
    }

    public Comment(JSONObject json) {
        super(json);
    }

    public String getText() {
        return getString("text");
    }

    public Assignee getUser() {
        Assignee author = getModel(Assignee.class, "author");
        if (author == null) {
            author = getModel(Assignee.class, "user");
        }
        return author;
    }

    @Override
    public IUser getAuthor() {
        return getUser();
    }

    @Override
    public String getBody() {
        return getText();
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public Date getCreated() {
        throw new UnsupportedOperationException();
    }
}