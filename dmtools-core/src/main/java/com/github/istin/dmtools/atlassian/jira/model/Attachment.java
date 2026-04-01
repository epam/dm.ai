// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

public class Attachment extends JSONModel implements IAttachment, ToText {

    public static final String AUTHOR = "author";

    private static final String FILENAME = "filename";
    public static final String CONTENT = "content";

    public Attachment() {
    }

    public Attachment(String json) throws JSONException {
        super(json);
    }

    public Attachment(JSONObject json) {
        super(json);
    }

    public Assignee getAuthor() {
        return getModel(Assignee.class, AUTHOR);
    }

    public String getFilename() {
        return getString(FILENAME);
    }

    public String getContent() {
        return getString(CONTENT);
    }

    public Date getCreated() {
        return Fields.getCreatedUtils(this);
    }

    @Override
    public String getName() {
        return getFilename();
    }

    @Override
    public String getUrl() {
        return getContent();
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String toText() throws IOException {
        return StringUtils.transformJSONToText(new StringBuilder(), getJSONObject(), false).toString();
    }
}