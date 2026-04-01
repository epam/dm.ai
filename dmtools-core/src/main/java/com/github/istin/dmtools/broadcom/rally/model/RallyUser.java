// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class RallyUser extends JSONModel implements IUser {

    public RallyUser() {
    }

    public RallyUser(String json) throws JSONException {
        super(json);
    }

    public RallyUser(JSONObject json) {
        super(json);
    }

    @Override
    public String getID() {
        return getString(RallyFields._REF);
    }

    @Override
    public String getFullName() {
        return getString(RallyFields._REF_OBJECT_NAME);
    }

    @Override
    public String getEmailAddress() {
        throw new UnsupportedOperationException("not supported");
    }

}
