// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.timeline;

import com.github.istin.dmtools.broadcom.rally.model.Iteration;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TimelineUtils {

    public static int getDefaultCurrentQuarter(int startYear) {
        List<Release> releases = generateYearsAndQuarters(startYear);
        Calendar currentCalendar = Calendar.getInstance();
        for (Release release : releases) {
            if (release.isMatchedToReleaseTimelines(currentCalendar)) {
                List<Sprint> sprints = release.getSprints();
                for (Sprint sprint : sprints) {
                    if (sprint.isMatchedToSprintTimelines(currentCalendar)) {
                        return sprint.getId();
                    }
                }
            }
        }
        throw new IllegalStateException("something is wrong with system dates");
    }

    public static int getDefaultCurrentYear() {
        return Year.now().getValue();
    }

    public static List<Release> generateYears(int startYear) {
        List<Release> releases = new ArrayList<Release>();
        int defaultCurrentYear = getDefaultCurrentYear();
        for (int year = startYear; year <= defaultCurrentYear; year++) {
            releases.add(new Release(year, String.valueOf(year), createYear(year)));
        }

        Calendar startDate = createCalendarForStartYear(2020);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        for (Release release : releases) {
            List<Sprint> sprints = release.getSprints();
            for (Sprint sprint : sprints) {
                sprint.setStartDateAsString(simpleDateFormat.format(new Date(startDate.getTimeInMillis())));
                sprint.setStartDate(new Date(startDate.getTimeInMillis()));
                startDate.add(Calendar.YEAR, 1);
                startDate.add(Calendar.MILLISECOND, -1);
                sprint.setEndDateAsString(simpleDateFormat.format(new Date(startDate.getTimeInMillis())));
                sprint.setEndDate(new Date(startDate.getTimeInMillis()));
                startDate.add(Calendar.MILLISECOND, 1);
            }
        }
        return releases;
    }

    public static List<Release> generateQuartersAndMonths(int startYear) {
        List<Release> releases = new ArrayList<Release>();
        AtomicInteger counter = new AtomicInteger(0);
        int defaultCurrentYear = getDefaultCurrentYear();
        for (int year = startYear; year <= defaultCurrentYear; year++) {
            List<Sprint> quarters = createQuarters(year, counter);
            for (Sprint quarter : quarters) {
                releases.add(new Release(quarter.getId(), year + quarter.getIterationName(), createMonths(year, quarter)));
            }

        }
        return releases;
    }

    private static List<Sprint> createMonths(int year, Sprint quarter) {
        return new ArrayList<>();
    }

    public static List<Release> generateYearsAndQuarters(int startYear) {
        List<Release> releases = new ArrayList<Release>();
        AtomicInteger counter = new AtomicInteger(0);
        int defaultCurrentYear = getDefaultCurrentYear();
        for (int year = startYear; year <= defaultCurrentYear; year++) {
            releases.add(new Release(year, String.valueOf(year), createQuarters(year, counter)));
        }

        Calendar startDate = createCalendarForStartYear(startYear);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        for (Release release : releases) {
            List<Sprint> sprints = release.getSprints();
            for (Sprint sprint : sprints) {
                sprint.setStartDateAsString(simpleDateFormat.format(new Date(startDate.getTimeInMillis())));
                sprint.setStartDate(new Date(startDate.getTimeInMillis()));
                startDate.add(Calendar.MONTH, 3);
                startDate.add(Calendar.MILLISECOND, -1);
                sprint.setEndDateAsString(simpleDateFormat.format(new Date(startDate.getTimeInMillis())));
                sprint.setEndDate(new Date(startDate.getTimeInMillis()));
                startDate.add(Calendar.MILLISECOND, 1);
            }
        }
        return releases;
    }

    @NotNull
    public static Calendar createCalendarForStartYear(int startYear) {
        Calendar startDate = Calendar.getInstance();
        startDate.set(Calendar.YEAR, startYear);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        startDate.set(Calendar.DAY_OF_YEAR, 1);
        return startDate;
    }

    private static List<Sprint> createYear(int year) {
        List<Sprint> yearArray = new ArrayList<>();
        yearArray.add(new Sprint(year, (String)null, (String)null, -1) {

            @Override
            public int getId() {
                return year;
            }

            @Override
            public String getIterationName() {
                return year + "y";
            }
        });

        return yearArray;
    }

    private static List<Sprint> createQuarters(int year, AtomicInteger counter) {
        List<Sprint> quarters = new ArrayList<>();
        final int q1 = counter.incrementAndGet();
        quarters.add(new Sprint(1, (String) null, (String)null, -1) {

            @Override
            public int getId() {
                return q1;
            }

            @Override
            public String getIterationName() {
                return year + "Q" + getNumber();
            }
        });

        final int q2 = counter.incrementAndGet();
        quarters.add(new Sprint(2, (String)null, (String)null, -1){

            @Override
            public int getId() {
                return q2;
            }

            @Override
            public String getIterationName() {
                return year + "Q" + getNumber();
            }
        });

        final int q3 = counter.incrementAndGet();
        quarters.add(new Sprint(3, (String)null, (String)null, -1){

            @Override
            public int getId() {
                return q3;
            }

            @Override
            public String getIterationName() {
                return year + "Q" + getNumber();
            }
        });

        final int q4 = counter.incrementAndGet();
        quarters.add(new Sprint(4, (String)null, (String)null, -1){

            @Override
            public int getId() {
                return q4;
            }

            @Override
            public String getIterationName() {
                return year + "Q" + getNumber();
            }
        });
        return quarters;
    }

    public static List<Release> convertRallyIterationToRelease(int idsCounter, Calendar startDate, Calendar endDate, List<? extends ReportIteration> iterations) {
        List<Release> releases = new ArrayList<>();
        int counter = idsCounter;
        Calendar now = Calendar.getInstance();
        for (ReportIteration iteration : iterations) {
            if (DateUtils.calendar(iteration.getStartDate()).compareTo(startDate) < 0 || DateUtils.calendar(iteration.getStartDate()).compareTo(endDate) > 0)
                continue;

            Release release = new Release();
            release.setId(counter);
            release.setName(iteration.getIterationName());
            Sprint sprint = new Sprint(counter, iteration.getStartDate(), iteration.getEndDate(), -1) {
                @Override
                public String getIterationName() {
                    return iteration.getIterationName();
                }
            };
            boolean matchedToSprintTimelines = sprint.isMatchedToSprintTimelines(now);
            sprint.setIsCurrent(matchedToSprintTimelines);
            release.setSprints(Collections.singletonList(sprint));
            releases.add(release);
            counter++;
        }
        return releases;
    }
}
