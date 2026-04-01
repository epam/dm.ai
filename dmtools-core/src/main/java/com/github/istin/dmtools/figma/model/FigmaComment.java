// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class FigmaComment extends JSONModel implements IComment {

    public FigmaComment() {
    }

    public FigmaComment(String json) throws JSONException {
        super(json);
    }

    public FigmaComment(JSONObject json) {
        super(json);
    }

    @Override
    public IUser getAuthor() {
        return getModel(FigmaUser.class, "user");
    }

    @Override
    public String getBody() {
        return getString("message");
    }

    @Override
    public String getId() {
        return getString("id");
    }

    @Override
    public Date getCreated() {
        return DateUtils.smartParseDate(getString("created_at"));
    }

}