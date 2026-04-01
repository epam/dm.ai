// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

public class FigmaUser extends JSONModel implements IUser {

    public FigmaUser() {
    }

    public FigmaUser(String json) {
        super(json);
    }

    public FigmaUser(JSONObject json) {
        super(json);
    }

    @Override
    public String getID() {
        return getString("id");
    }

    @Override
    public String getFullName() {
        return getString("handle");
    }

    @Override
    public String getEmailAddress() {
        // Figma API does not provide email address in pull request user object by default
        // You might need to make an additional API call to get the email address if required
        return getString("email");
    }

    public static FigmaUser create(String json) {
        FigmaUser user = new FigmaUser();
        if (json != null) {
            user.setJO(new JSONObject(json));
        }
        return user;
    }
}