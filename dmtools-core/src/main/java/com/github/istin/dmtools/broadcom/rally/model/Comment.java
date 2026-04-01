// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

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

    @Override
    public IUser getAuthor() {
        return getModel(RallyUser.class, RallyFields.USER);
    }

    @Override
    public String getBody() {
        return getString("Text");
    }

    @Override
    public String getId() {
        return getString("ObjectUUID");
    }

    @Override
    public Date getCreated() {
        throw new UnsupportedOperationException();
    }

    public String getRef() {
        return getString("_ref");
    }
}
