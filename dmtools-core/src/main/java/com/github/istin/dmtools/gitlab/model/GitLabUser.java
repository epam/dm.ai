// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

public class GitLabUser extends JSONModel implements IUser {

    public GitLabUser() {
    }

    public GitLabUser(String json) {
        super(json);
    }

    public GitLabUser(JSONObject json) {
        super(json);
    }

    @Override
    public String getID() {
        return getString("id");
    }

    @Override
    public String getFullName() {
        return getString("name");
    }

    @Override
    public String getEmailAddress() {
        // GitLab API includes the email address in some user endpoints,
        // but typically not in the MR object itself.
        return getString("email");
    }

    public static GitLabUser create(String json) {
        GitLabUser user = new GitLabUser();
        if (json != null) {
            user.setJO(new JSONObject(json));
        }
        return user;
    }
}