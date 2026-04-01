// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Release implements ReportIteration {


    private Calendar endDateCalendar;

    public enum Style {
        BY_RELEASE,
        BY_SPRINTS,
        BY_WEEKS
    }

    private int id;

    private String name;

    private List<Sprint> sprints;

    public Release(int id, String name, List<Sprint> sprints) {
        this.id = id;
        this.name = name;
        this.sprints = sprints;
    }

    public Release() {
    }

    public void setCurrentEndDate(Calendar endDateCalendar) {
        this.endDateCalendar = endDateCalendar;
    }

    public List<Sprint> getSprints() {
        return sprints;
    }

    public List<? extends ReportIteration> getIterationsByStyle(Style style) {
        if (style == Style.BY_SPRINTS || style == Style.BY_RELEASE) {
            return sprints;
        }
        if (style == Style.BY_WEEKS) {
            List<ReportIteration> weeks = new ArrayList<>();
            for (Sprint sprint : getSprints()) {
                weeks.addAll(sprint.getWeeks());
            }
            return weeks;
        }
        throw new IllegalArgumentException("invalid argument");
    }

    public void setSprints(List<Sprint> sprints) {
        this.sprints = sprints;
    }

    @Override
    public String getIterationName() {
        return getName();
    }

    public int getId() {
        return id;
    }

    @Override
    public Date getStartDate() {
        return sprints.get(0).getStartDate();
    }

    @Override
    public Date getEndDate() {
        return sprints.get(sprints.size()-1).getEndDate();
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Calendar getStartDateAsCalendar() {
        Date startDate = getStartDate();
        Calendar instance = Calendar.getInstance();
        instance.setTime(startDate);
        return instance;
    }

    public Calendar getEndDateAsCalendar() {
        Date endDate = getEndDate();
        Calendar instance = Calendar.getInstance();
        instance.setTime(endDate);
        return instance;
    }

    @Override
    public boolean isReleased() {
        return false;
    }

    public String getStartDateAsString() {
        return sprints.get(0).getStartDateAsString();
    }

    public String getEndDateAsString() {
        return sprints.get(sprints.size()-1).getEndDateAsString();
    }

    public boolean isMatchedToReleaseTimelines(Calendar date) {
        return getStartDateAsCalendar().getTimeInMillis() <= date.getTimeInMillis() && getEndDateAsCalendar().getTimeInMillis() >= date.getTimeInMillis();
    }

    public boolean beforeReleaseStart(Calendar date) {
        return getStartDateAsCalendar().getTimeInMillis() > date.getTimeInMillis();
    }

    public boolean afterReleaseEnds(Calendar date) {
        return getEndDateAsCalendar().getTimeInMillis() < date.getTimeInMillis();
    }

    public boolean getIsCurrent() {
        Calendar now = endDateCalendar == null ? Calendar.getInstance() : endDateCalendar;
        return isMatchedToReleaseTimelines(now);
    }

}
