// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class Commit extends JSONModel implements ICommit {

    public Commit() {
    }

    public Commit(String json) throws JSONException {
        super(json);
    }

    public Commit(JSONObject json) {
        super(json);
    }

    @Override
    public String getId() {
        return getString("id");
    }

    @Override
    public String getHash() {
        return getString("hash");
    }

    @Override
    public String getMessage() {
        return getString("message");
    }

    public String getSummary() {
        return getJSONObject("summary").getString("raw");
    }

    @Override
    public IUser getAuthor() {
        JSONObject authorObject = getJSONObject("author");
        if (authorObject != null) {
            JSONObject userObject = authorObject.getJSONObject("user");
            if (userObject != null) {
                return new Assignee(userObject);
            }
        }
        return getModel(Assignee.class, "author");
    }

    @Override
    public Long getCommiterTimestamp() {
        Long committerTimestamp = getLong("committerTimestamp");
        if (committerTimestamp == null) {
                String dateString = getString("date"); {
                return DateUtils.parseBitbucketDate(dateString).getTime();
            }
        }
        return committerTimestamp;
    }

    @Override
    public Calendar getCommitterDate() {
        return Utils.getComitterDate(this);
    }

    @Override
    public String getUrl() {
        return "url is not supported";
    }

}