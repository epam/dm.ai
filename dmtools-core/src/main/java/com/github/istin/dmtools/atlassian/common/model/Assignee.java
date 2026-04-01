// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.common.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Assignee extends JSONModel implements IUser {

    public Assignee() {
    }

    public Assignee(String json) throws JSONException {
        super(json);
    }

    public Assignee(JSONObject json) {
        super(json);
    }

    public String getEmailAddress() {
        return getString("emailAddress");
    }

    public String getHtmlEmailAddress() {
        return "<a href=\"mailto:"+getEmailAddress()+"\">" + getDisplayName() + "</a>";
    }

    public String getDisplayName() {
        String displayName = getString("displayName");
        if (displayName == null) {
            displayName = getString("display_name");
            if (displayName == null) {
                return getFullName();
            }
        }
        return displayName;
    }

    @Override
    public String getID() {
        return getAccountId();
    }

    @Override
    public String getFullName() {
        return getDisplayName();
    }

    public String getName() {
        return getString("name");
    }

    public String getAccountId() {
        return getString("accountId");
    }

    public Boolean getActive() {
        return getBoolean("active");
    }

    @Override
    public int hashCode() {
        return getDisplayName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Assignee) {
            return ((Assignee)obj).getDisplayName().equals(getDisplayName());
        }
        return super.equals(obj);
    }
}
