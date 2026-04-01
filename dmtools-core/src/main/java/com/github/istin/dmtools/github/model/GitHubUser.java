// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

public class GitHubUser extends JSONModel implements IUser {

    public GitHubUser() {
    }

    public GitHubUser(String json) {
        super(json);
    }

    public GitHubUser(JSONObject json) {
        super(json);
    }

    @Override
    public String getID() {
        return getString("id");
    }

    @Override
    public String getFullName() {
        return getString("login");
    }

    @Override
    public String getEmailAddress() {
        // GitHub API does not provide email address in pull request user object by default
        // You might need to make an additional API call to get the email address if required
        return getString("email");
    }

    public static GitHubUser create(String json) {
        GitHubUser user = new GitHubUser();
        if (json != null) {
            user.setJO(new JSONObject(json));
        }
        return user;
    }
}