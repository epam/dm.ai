// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class History extends JSONModel implements IHistory {

    private static final String ITEMS = "items";

    private static final String CREATED = "created";

    private static final String AUTHOR = "author";

    public History() {
    }

    public History(String json) throws JSONException {
        super(json);
    }

    public History(JSONObject json) {
        super(json);
    }

    public List<HistoryItem> getItems() {
        return getModels(HistoryItem.class, "items");
    }

    public Calendar getCreated(){
        try {
            Calendar instance = Calendar.getInstance();
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(getString(CREATED));
            instance.setTime(date);
            return instance;
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<? extends IHistoryItem> getHistoryItems() {
        return getItems();
    }

    @Override
    public IUser getAuthor() {
        return getModel(Assignee.class, AUTHOR);
    }
}