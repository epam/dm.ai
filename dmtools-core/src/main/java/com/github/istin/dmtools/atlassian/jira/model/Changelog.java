// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.utils.DateInterval;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class Changelog extends JSONModel implements IChangelog {

    public static final String MAX_RESULTS = "maxResults";
    public static final String TOTAL = "total";
    public static final String HISTORIES = "histories";
    public static final String ERROR_MESSAGES = "errorMessages";


    public Changelog() {
    }

    public Changelog(String json) throws JSONException {
        super(json);
    }

    public Changelog(JSONObject json) {
        super(json);
    }

    public JSONArray getErrorMessages() {
        return getJSONArray(ERROR_MESSAGES);
    }

    public int getMaxResults() {
        return getInt(MAX_RESULTS);
    }

    public int getTotal() {
        return getInt(TOTAL);
    }

    @Override
    public List<? extends IHistory> getHistories() {
        List<? extends IHistory> models = getModels(History.class, HISTORIES);
        if (models.size() > 1) {
            if (models.get(0).getCreated().compareTo(models.get(1).getCreated()) > 0) {
                Collections.reverse(models);
            }
        }
        return models;
    }

    public DateInterval dateIntervalBetweenTwoStatuses(String firstStatus, String secondStatus) {
        boolean shouldHandleFirstStatus = true;
        boolean shouldHandleSecondStatus = true;
        Calendar firstStatusDate = null;
        Calendar secondStatusDate = null;

        for (IHistory history: getHistories()) {
            for (IHistoryItem historyItem: history.getHistoryItems()) {
                if (historyItem.getToAsString() == null) {
                    continue;
                }
                String toString = historyItem.getToAsString().toLowerCase();
                if (toString.equals(firstStatus) && shouldHandleFirstStatus) {
                    shouldHandleFirstStatus = false;
                    firstStatusDate = history.getCreated();
                }
                if (toString.equals(secondStatus) && shouldHandleSecondStatus) {
                    shouldHandleSecondStatus = false;
                    secondStatusDate = history.getCreated();
                }
            }
        }

        if (firstStatusDate == null || secondStatusDate == null) {
            return null;
        }

        return new DateInterval(firstStatusDate, secondStatusDate);
    }

}