// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FixVersion extends JSONModel implements Comparable<FixVersion>, ReportIteration {

    private static final Logger logger = LogManager.getLogger(FixVersion.class);

    //"05/Nov/14"
    public static final String USER_RELEASE_DATE = "userReleaseDate";
    public static final String USER_START_DATE = "userStartDate";

    public static final String RELEASE_DATE = "releaseDate";
    public static final String START_DATE = "startDate";
    public static final String NAME = "name";

    public FixVersion() {
    }

    public FixVersion(String json) throws JSONException {
        super(json);
    }

    public FixVersion(JSONObject json) {
        super(json);
    }

    public String getName() {
        return getString(NAME);
    }

    @Override
    public String getIterationName() {
        return getName();
    }

    @Override
    public int getId() {
        return getIdAsString().hashCode();
    }

    public String getIdAsString() {
        return getString("id");
    }

    @Override
    public Date getStartDate() {
        String userStartDate = getUserStartDate();
        if (userStartDate == null || userStartDate.isEmpty()) {
            logger.info(getJSONObject().toString());
        }
        return DateUtils.parseJiraDate(userStartDate);
    }

    @Override
    public Date getEndDate() {
        return DateUtils.parseJiraDate(getUserReleaseDate());
    }

    @Override
    public boolean isReleased() {
        return getReleased();
    }

    public String getUserReleaseDate() {
        return getString(USER_RELEASE_DATE);
    }

    public String getUserStartDate() {
        return getString(USER_START_DATE);
    }

    public void setUserReleaseDate(String date) {
        set(USER_RELEASE_DATE, date);
    }

    public void setUserStartDate(String date) {
        set(USER_START_DATE, date);
    }

    public void setReleaseDate(String date) {
        set(RELEASE_DATE, date);
    }

    public void setStartDate(String date) {
        set(START_DATE, date);
    }

    public boolean getArchived() {
        return getBoolean("archived");
    }
    public boolean getReleased() {
        return getBoolean("released");
    }

    public boolean isNotPlanned() {
        return "Not Planned".equalsIgnoreCase(getName()) || "unknown".equalsIgnoreCase(getName());
    }

    @Override
    public int compareTo(@NotNull FixVersion o) {
        return this.getName().compareTo(o.getName());
    }

    @Override
    public boolean equals(Object o) {
        return this.getName().equals(((FixVersion)o).getName());
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }
}