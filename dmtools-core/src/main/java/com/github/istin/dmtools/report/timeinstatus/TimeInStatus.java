// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IHistory;
import com.github.istin.dmtools.common.model.IHistoryItem;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class TimeInStatus {
    private static final Logger logger = LogManager.getLogger(TimeInStatus.class);
    private final TrackerClient<? extends ITicket> tracker;

    private List<String> finalStatuses = new ArrayList<>();

    public TimeInStatus(TrackerClient<? extends ITicket> tracker) {
        this.tracker = tracker;
        finalStatuses.addAll(Arrays.asList("done", Status.REJECTED.toLowerCase(), Status.CANCELLED.toLowerCase()));
    }

    public static class HoursAndStartDate {
        public final int hoursSpent;
        public final Calendar startDate;

        public HoursAndStartDate(int hoursSpent, Calendar startDate) {
            this.hoursSpent = hoursSpent;
            this.startDate = startDate;
        }
    }

    public HoursAndStartDate findHoursSpentOnDevelopment(ITicket ticket) throws Exception {
        List<TimeInStatus.Item> itemList = check(ticket, Arrays.asList("in development", "in review"), "draft");
        if (!itemList.isEmpty()) {
            int hoursResult = 0;
            Calendar startDate = null;
            Calendar endDate = null;
            for (TimeInStatus.Item item : itemList) {
                if (item.getStatusName().equalsIgnoreCase("in development") || item.getStatusName().equalsIgnoreCase("in review")) {
                    int currentTimeInStatusHours = item.getHours();
                    int weekendDaysBetweenTwoDates = DateUtils.getWeekendDaysBetweenTwoDates(item.getStartDate().getTimeInMillis(), item.getEndDate().getTimeInMillis());
                    currentTimeInStatusHours = currentTimeInStatusHours - weekendDaysBetweenTwoDates*24;
                    if (currentTimeInStatusHours < 0) {
                        currentTimeInStatusHours = 1;
                    }
                    hoursResult += currentTimeInStatusHours;
                    if (startDate == null) {
                        startDate = item.getStartDate();
                    }
                    endDate = item.getEndDate();
                }
            }

            if (startDate == null || endDate == null) {
                return null;
            }

            if (hoursResult < 1) {
                hoursResult = DateUtils.getHoursDuration(endDate.getTimeInMillis(), startDate.getTimeInMillis());
            }
            logger.info("{} {}", tracker.getTicketBrowseUrl(ticket.getKey()), hoursResult);
            return new HoursAndStartDate(hoursResult, startDate);
        }
        return null;
    }

    public static class Item {
        private ITicket ticket;
        private String statusName;
        private Calendar startDate;
        private Calendar endDate;

        public Item(ITicket ticket, String statusName, Calendar startDate, Calendar endDate) {
            this.ticket = ticket;
            this.statusName = statusName;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public String getStatusName() {
            return statusName;
        }

        public Calendar getStartDate() {
            return startDate;
        }

        public Calendar getEndDate() {
            return endDate;
        }

        public int getDays() {
            return DateUtils.getDaysDuration(endDate, startDate);
        }

        public int getHours() {
            String key = getTicket().getKey();
            int days = getDays();
            int countOfNightHours = (days) * 16;
            if (countOfNightHours < 0) {
                countOfNightHours = 0;
            }
            Integer hoursDuration = DateUtils.getHoursDuration(endDate.getTimeInMillis(), startDate.getTimeInMillis());
            return hoursDuration - countOfNightHours;
        }

        @Override
        public String toString() {
            return "[ '"+ticket.getTicketKey()+"', '"+statusName+"', new Date("
                    +startDate.get(Calendar.YEAR)+", "+startDate.get(Calendar.MONTH)+", "+startDate.get(Calendar.DATE)
                    +", "+startDate.get(Calendar.HOUR_OF_DAY) +", "+startDate.get(Calendar.MINUTE) +", "+startDate.get(Calendar.SECOND)
                    +"), new Date("+endDate.get(Calendar.YEAR)+", "+endDate.get(Calendar.MONTH)+", "+endDate.get(Calendar.DATE)+
                    ", "+endDate.get(Calendar.HOUR_OF_DAY) +", "+endDate.get(Calendar.MINUTE) +", "+endDate.get(Calendar.SECOND)  +") ]";
        }

        public ITicket getTicket() {
            return ticket;
        }

        public void setTicket(Ticket ticket) {
            this.ticket = ticket;
        }
    }

    public List<String> getFinalStatuses() {
        return finalStatuses;
    }

    public void setFinalStatuses(List<String> finalStatuses) {
        this.finalStatuses = finalStatuses;
    }

    public List<Item> check(ITicket ticket, List<String> listOfStatuses, String firstDefaultStatus) throws Exception {
        IChangelog changeLog = tracker.getChangeLog(ticket.getTicketKey(), ticket);
        List<IHistory> histories = (List<IHistory>) changeLog.getHistories();

        List<Item> itemsFirstTimeRight = new ArrayList<>();

        Calendar createdCalendar = Calendar.getInstance();
        createdCalendar.setTime(ticket.getCreated());

        Calendar lastChanged = createdCalendar;
        String lastStatus = firstDefaultStatus;

        for (IHistory history : histories) {
            List<IHistoryItem> items = (List<IHistoryItem>) history.getHistoryItems();
            for (IHistoryItem historyItem : items) {
                if (historyItem.getField().equalsIgnoreCase(tracker.getDefaultStatusField())) {
                    String toString = historyItem.getToAsString();
                    if (toString != null) {
                        String toStringInLowerCase = toString.toLowerCase();

                        if (needToAdd(listOfStatuses, lastStatus, toStringInLowerCase)) {
                            itemsFirstTimeRight.add(new Item(ticket, lastStatus, lastChanged, history.getCreated()));
                        } else if (lastStatus.equalsIgnoreCase(toStringInLowerCase) && !itemsFirstTimeRight.isEmpty()) {
                            itemsFirstTimeRight.get(itemsFirstTimeRight.size() - 1).endDate = history.getCreated();
                        }
                        lastChanged = history.getCreated();
                        lastStatus = toStringInLowerCase;
                    }
                }
            }
        }

        Calendar now = Calendar.getInstance();
        if (finalStatuses.contains(lastStatus)) {
            now.setTimeInMillis(lastChanged.getTimeInMillis());
            now.add(Calendar.DATE, 1);
            if (now.getTimeInMillis() > System.currentTimeMillis()) {
                now.setTimeInMillis(System.currentTimeMillis());
            }
        } else {
            now.setTimeInMillis(System.currentTimeMillis());
        }
        itemsFirstTimeRight.add(new Item(ticket, lastStatus, lastChanged, now));
        return itemsFirstTimeRight;
    }

    private boolean needToAdd(List<String> listOfStatuses, String lastStatus, String toStringInLowerCase) {
        return (listOfStatuses == null || listOfStatuses.isEmpty() || listOfStatuses.contains(lastStatus)) && !lastStatus.contains(toStringInLowerCase);
    }

}
