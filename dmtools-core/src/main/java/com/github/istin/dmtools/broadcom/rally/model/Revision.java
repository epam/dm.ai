// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.broadcom.rally.utils.RallyUtils;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Revision extends JSONModel implements IHistory {

    public Revision() {
    }

    public Revision(String json) throws JSONException {
        super(json);
    }

    public Revision(JSONObject json) {
        super(json);
    }

    public String getDescription() {
        return getString(RallyFields.DESCRIPTION);
    }

    public Date getCreationDate() {
        return DateUtils.parseRallyDate(getString(RallyFields.CREATION_DATE));
    }

    public RallyUser getUser() {
        return getModel(RallyUser.class, RallyFields.USER);
    }

    @Override
    public List<? extends IHistoryItem> getHistoryItems() {
        return RallyUtils.convertRevisionDescriptionToHistoryItems(getDescription());
    }

    @Override
    public IUser getAuthor() {
        return getUser();
    }

    @Override
    public Calendar getCreated() {
        return DateUtils.parseRallyCalendar(getString(RallyFields.CREATION_DATE));
    }
}
