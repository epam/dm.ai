package com.github.istin.dmtools.common.timeline;

import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.report.IReleaseGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class QuartersReleaseGenerator implements IReleaseGenerator {

    private final String startDateAsString;
    private final Calendar startDate = Calendar.getInstance();
    private Calendar endDateCalendar;
    private List<Release> releases = new ArrayList<>();

    public QuartersReleaseGenerator(String startDateAsString) throws IOException {
        this(startDateAsString, null);
    }
    /**
     *
     * @param startDateAsString "dd.MM.yyyy"
     * @throws IOException connection errors
     */
    public QuartersReleaseGenerator(String startDateAsString, String endDateAsString) throws IOException {
        this.startDateAsString = startDateAsString;
        Calendar startDate = DateUtils.parseCalendar(startDateAsString);

        // Set to the first day of the quarter
        startDate.set(Calendar.DAY_OF_MONTH, 1);
        int month = startDate.get(Calendar.MONTH);
        int quarterStartMonth = (month / 3) * 3; // 0 for Q1, 3 for Q2, 6 for Q3, 9 for Q4
        startDate.set(Calendar.MONTH, quarterStartMonth);

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

            // Calculate the quarter number (1-4) and year
            int currentYear = startDate.get(Calendar.YEAR);
            int currentQuarter = (startDate.get(Calendar.MONTH) / 3) + 1;
            release.setName("Q" + currentQuarter + " " + currentYear);

            release.setCurrentEndDate(endDateCalendar);
            int finalCounter = counter;
            int finalCurrentQuarter = currentQuarter;
            int finalCurrentYear = currentYear;

            Sprint sprint = new Sprint(finalCounter, startDate.getTime(), getOneQuarterLater(startDate).getTime(), -1) {
                @Override
                public String getIterationName() {
                    return "Q" + finalCurrentQuarter + " " + finalCurrentYear;
                }
            };

            boolean matchedToSprintTimelines = sprint.isMatchedToSprintTimelines(endDateCalendar);
            sprint.setIsCurrent(matchedToSprintTimelines);
            release.setSprints(Collections.singletonList(sprint));
            releases.add(release);
            counter++;

            // Increment startDate by one quarter
            startDate = getOneQuarterLater(startDate);
        }

        // Debug print out the quarters
        for (Release release : releases) {
            System.out.println("Release ID: " + release.getId() + ", Name: " + release.getName());
        }
    }

    // Helper method to get date one quarter later
    private static Calendar getOneQuarterLater(Calendar date) {
        Calendar oneQuarterLater = (Calendar) date.clone();
        oneQuarterLater.add(Calendar.MONTH, 3);
        return oneQuarterLater;
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
