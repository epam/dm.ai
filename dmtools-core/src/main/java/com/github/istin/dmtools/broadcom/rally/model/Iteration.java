// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class Iteration extends JSONModel implements ReportIteration {

    public Iteration() {
    }

    public Iteration(String json) throws JSONException {
        super(json);
    }

    public Iteration(JSONObject json) {
        super(json);
    }

    @Override
    public String getIterationName() {
        return getName();
    }

    @Override
    public int getId() {
        return getName().hashCode();
    }

    @Override
    public Date getStartDate() {
        return DateUtils.parseRallyDate(getString(RallyFields.START_DATE));
    }

    @Override
    public Date getEndDate() {
        return DateUtils.parseRallyDate(getString(RallyFields.END_DATE));
    }

    @Override
    public boolean isReleased() {
        return getEndDate().getTime() < System.currentTimeMillis();
    }

    public String getName() {
        return getString(RallyFields.NAME);
    }

    public String getType() {
        return getString(RallyFields._TYPE);
    }

    public String getState() {
        return getString(RallyFields.STATE);
    }

}
