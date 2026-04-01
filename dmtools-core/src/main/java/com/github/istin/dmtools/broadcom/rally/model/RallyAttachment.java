// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class RallyAttachment extends JSONModel implements IAttachment {

    public RallyAttachment() {
    }

    public RallyAttachment(String json) throws JSONException {
        super(json);
    }

    public RallyAttachment(JSONObject json) {
        super(json);
    }

    public String getRef() {
        return getString(RallyFields._REF);
    }

    @Override
    public String getName() {
        return getString("Name");
    }

    @Override
    public String getUrl() {
        return getRef().replaceAll("webservice/v2.x/", "") + "/"+ getName();
    }

    @Override
    public String getContentType() {
        return getString("ContentType");
    }


}
