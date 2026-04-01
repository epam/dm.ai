// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.report.IReleaseGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WeeksReleaseGenerator implements IReleaseGenerator {

    private static final Logger logger = LogManager.getLogger(WeeksReleaseGenerator.class);

    private final String startDateAsString;
    private final Calendar startDate = Calendar.getInstance();
    private Calendar endDateCalendar;
    private List<Release> releases = new ArrayList<>();

    public WeeksReleaseGenerator(String startDateAsString) throws IOException {
        this(startDateAsString, null);
    }
    /**
     *
     * @param startDateAsString "dd.MM.yyyy"
     * @throws IOException connection errors
     */
    public WeeksReleaseGenerator(String startDateAsString, String endDateAsString) throws IOException {
        this.startDateAsString = startDateAsString;
        Calendar startDate = DateUtils.parseCalendar(startDateAsString); // Example start date (you can replace this with actual start date parsing)
        startDate.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        this.startDate.setTime(startDate.getTime());
        if (endDateAsString == null) {
            endDateCalendar = Calendar.getInstance();
        } else {
            endDateCalendar = DateUtils.parseCalendar(endDateAsString);
            endDateCalendar.add(Calendar.DATE, 1);
        }
        int counter = 0;


        while (startDate.before(endDateCalendar)) {
            Release release = new Release();
            release.setId(counter);
            release.setName("Week " + counter);
            release.setCurrentEndDate(endDateCalendar);
            int finalCounter = counter;
            Sprint sprint = new Sprint(finalCounter, startDate.getTime(), getOneWeekLater(startDate).getTime(), -1) {
                @Override
                public String getIterationName() {
                    return "Week " + finalCounter;
                }
            };

            boolean matchedToSprintTimelines = sprint.isMatchedToSprintTimelines(endDateCalendar);
            sprint.setIsCurrent(matchedToSprintTimelines);
            release.setSprints(Collections.singletonList(sprint));
            releases.add(release);
            counter++;

            // Increment startDate by one week
            startDate.add(Calendar.WEEK_OF_YEAR, 1);
        }

        // releases endDateCalendar contains weekly releases until the current week
        for (Release release : releases) {
            logger.info("Release ID: " + release.getId() + ", Name: " + release.getName());
        }
    }

    // Helper method to get date one week later
    private static Calendar getOneWeekLater(Calendar date) {
        Calendar oneWeekLater = (Calendar) date.clone();
        oneWeekLater.add(Calendar.WEEK_OF_YEAR, 1);
        return oneWeekLater;
    }

    public List<Release> getReleases() {
        return releases;
    }

    @Override
    public List<Release> generate() {
        return releases;
    }

    @Override
    public int getTypeOfReleases() {
        return Release.Style.BY_RELEASE.ordinal();
    }

    @Override
    public Release getCurrentIteration() {
        for (Release release : releases) {
            if (release.getIsCurrent()) {
                return release;
            }
        }
        return null;
    }

    @Override
    public int getStartSprint() {
        return 0;
    }

    @Override
    public int getStartFixVersion() {
        return 0;
    }

    @Override
    public int getExtraSprintTimeline() {
        return 0;
    }

    @Override
    public Calendar getStartDate() {
        return startDate;
    }

    @Override
    public long getMaxTime() {
        return 0;
    }
}
