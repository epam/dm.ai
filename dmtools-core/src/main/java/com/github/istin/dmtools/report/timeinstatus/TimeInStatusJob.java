// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.atlassian.jira.model.FieldOption;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.report.ReportUtils;
import com.github.istin.dmtools.report.freemarker.GenericCell;
import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.freemarker.TicketLinkCell;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TimeInStatusJob {

    public static final String[] FIELDS = new String[] {
            Fields.CREATED,
            Fields.UPDATED,
            Fields.SUMMARY
    };

    public static void main(TrackerClient<? extends ITicket> trackerClient, BasicConfluence confluence, Params params) throws Exception {
        TimeInStatus timeInStatus = new TimeInStatus(trackerClient);
        if (params.finalStatuses != null) {
            timeInStatus.getFinalStatuses().addAll(Arrays.asList(params.finalStatuses));
        }

        List<String> statuses = new ArrayList<>(Arrays.asList(params.getStatusesToCheck()));
        Set<String> genericStatuses = new HashSet<>();


        List<String> fields = new ArrayList<>(Arrays.asList(FIELDS));
        if (params.getExtraFields() != null) {
            fields.addAll(Arrays.asList(params.getExtraFields()));
        }

        List<TimeInStatus.Item> items = new ArrayList<>();

        String filterJql = params.getFilter();
        if (filterJql != null) {
            initFromFilter(trackerClient, params, timeInStatus, statuses, genericStatuses, items, filterJql);
        } else {
            initFromScope(params, timeInStatus, statuses, genericStatuses, items, params.getScope());
        }

        statuses.addAll(genericStatuses);
        List<GenericRow> rows = new ArrayList<>();


        GenericRow currentRow = null;
        currentRow = new GenericRow();
        currentRow.getCells().add(new GenericCell("Summary"));
        currentRow.getCells().add(new GenericCell("Link"));
        for (String status : statuses) {
            currentRow.getCells().add(new GenericCell(status));
        }
        if (params.extraFieldsNames != null) {
            for (String extraFieldsName : params.extraFieldsNames) {
                currentRow.getCells().add(new GenericCell(extraFieldsName));
            }
        }
        currentRow.getCells().add(new GenericCell("Cycle Time"));

        rows.add(currentRow);

        String prevKey = null;
        int prevCycleTime = 0;

        for (TimeInStatus.Item item : items) {
            ITicket ticket = item.getTicket();
            if (prevKey == null || !prevKey.equals(ticket.getKey())) {
                if (prevKey != null) {
                    currentRow.getCells().add(new GenericCell(prevCycleTime + ""));
                }
                prevKey = ticket.getKey();
                prevCycleTime = 0;

                currentRow = new GenericRow();
                currentRow.getCells().add(new GenericCell(ticket.getTicketTitle()));
                currentRow.getCells().add(new TicketLinkCell(prevKey, ticket.getTicketLink()));
                for (String status : statuses) {
                    currentRow.getCells().add(new GenericCell());
                }

                if (params.extraFields != null) {
                    for (String extraField : params.extraFields) {
                        JSONObject jsonObject = ticket.getFieldsAsJSON();
                        Object o = new JSONModel(jsonObject).getJSONObject().opt(extraField);
                        if ((o instanceof String || o instanceof Integer || o instanceof Double)) {
                            currentRow.getCells().add(new GenericCell(o.toString()));
                        } else if (o instanceof JSONObject) {
                            FieldOption fieldOption = new FieldOption((JSONObject) o);
                            currentRow.getCells().add(new GenericCell(fieldOption.getValue()));
                        } else {
                            currentRow.getCells().add(new GenericCell("&nbsp;"));
                        }
                    }
                }
                rows.add(currentRow);
            }
            for (int i = 0; i < statuses.size(); i++) {
                String status = statuses.get(i);
                if (status.equalsIgnoreCase(item.getStatusName())) {
                    GenericCell genericCell = currentRow.getCells().get(i+2);
                    String text = genericCell.getText();
                    Integer daysDuration = DateUtils.getDaysDuration(item.getEndDate().getTimeInMillis(), item.getStartDate().getTimeInMillis());
                    if (text != null && !text.isEmpty()) {
                        genericCell.setText(String.valueOf(daysDuration + Integer.parseInt(text)));
                    } else {
                        genericCell.setText(String.valueOf(daysDuration));
                    }
                    prevCycleTime = prevCycleTime + daysDuration;
                    break;
                }
            }
        }

        if (prevKey != null) {
            currentRow.getCells().add(new GenericCell(prevCycleTime + ""));
        }

        TimeInStatusReport timeInStatusReport = new TimeInStatusReport();
        timeInStatusReport.setName(params.getReportName());
        timeInStatusReport.setFilter(filterJql);
        timeInStatusReport.setItems(items);
        timeInStatusReport.setRows(rows);
        timeInStatusReport.setIsData(false);
        timeInStatusReport.setTableHeight("10000px");

        File result = new ReportUtils().write(timeInStatusReport.getName() + (timeInStatusReport.getIsData() ? "_raw" : ""), "time_in_status", timeInStatusReport, null);
        timeInStatusReport.setIsData(true);
        if (params.getRootPageName() != null) {
            publish(params.getRootPageName(), params.getPrefix(), " Chart", result, confluence);
        }

        result = new ReportUtils().write(timeInStatusReport.getName() + (timeInStatusReport.getIsData() ? "_raw" : ""), "time_in_status", timeInStatusReport, null);
        if (params.getRootPageName() != null) {
            publish(params.getRootPageName(), params.getPrefix(), " Raw Data", result, confluence);
        }
    }

    private static void initFromScope(Params params, TimeInStatus timeInStatus, List<String> statuses, Set<String> genericStatuses, List<TimeInStatus.Item> items, List<? extends ITicket> scope) throws Exception {
        for (ITicket ticket : scope) {
            analyzeTimeInStatus(ticket, params, timeInStatus, statuses, genericStatuses, items);
        }
    }

    private static void initFromFilter(TrackerClient<? extends ITicket> trackerClient, Params params, TimeInStatus timeInStatus, List<String> statuses, Set<String> genericStatuses, List<TimeInStatus.Item> items, String filterJql) throws Exception {
        trackerClient.searchAndPerform(new JiraClient.Performer() {

            @Override
            public boolean perform(ITicket ticket) throws Exception {
                analyzeTimeInStatus(ticket, params, timeInStatus, statuses, genericStatuses, items);
                return false;
            }

        },
                filterJql, trackerClient.getDefaultQueryFields());
    }

    private static void analyzeTimeInStatus(ITicket ticket, Params params, TimeInStatus timeInStatus, List<String> statuses, Set<String> genericStatuses, List<TimeInStatus.Item> items) throws Exception {
        List<TimeInStatus.Item> check = timeInStatus.check(ticket, statuses, params.getInitialStatus());
        for (TimeInStatus.Item item : check) {
            boolean isFound = false;
            for (String genericStatus : statuses) {
                if (genericStatus.equalsIgnoreCase(item.getStatusName())) {
                    isFound = true;
                    break;
                }
            }
            if (!isFound) {
                genericStatuses.add(item.getStatusName());
            }
        }
        items.addAll(check);
    }

    static void publish(String rootPageName, String prefix, String postfix, File reportFile, BasicConfluence confluence) throws IOException {
        String fileContent = Confluence.macroHTML(FileUtils.readFileToString(reportFile, "UTF-8"));
        Content rootPage = confluence.findContent(rootPageName);
        String title = prefix + " " + "Time In Status" + postfix;
        Content endPage = confluence.findOrCreate(title, rootPage.getId(), fileContent);
        confluence.updatePage(endPage.getId(), title, rootPage.getId(), fileContent);
    }

    public static class Params {
        private String rootPageName;
        private String prefix;
        private final String reportName;
        private String filter;
        private final String initialStatus;
        private final String[] statusesToCheck;

        private String[] extraFields;

        private String[] extraFieldsNames;

        private String[] finalStatuses;

        private List<? extends ITicket> scope;

        public Params(String rootPageName, String prefix, String reportName, List<? extends ITicket> scope, String initialStatus, String[] statusesToCheck) {
            this.rootPageName = rootPageName;
            this.prefix = prefix;
            this.reportName = reportName;
            this.scope = scope;
            this.initialStatus = initialStatus;
            this.statusesToCheck = statusesToCheck;
        }

        public Params(String reportName, String filter, String initialStatus, String[] statusesToCheck) {
            this.reportName = reportName;
            this.filter = filter;
            this.initialStatus = initialStatus;
            this.statusesToCheck = statusesToCheck;
        }

        public Params(String rootPageName, String prefix, String reportName, String filter, String initialStatus, String[] statusesToCheck) {
            this.rootPageName = rootPageName;
            this.prefix = prefix;
            this.reportName = reportName;
            this.filter = filter;
            this.initialStatus = initialStatus;
            this.statusesToCheck = statusesToCheck;
        }

        public String getRootPageName() {
            return rootPageName;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getReportName() {
            return reportName;
        }

        public String getFilter() {
            return filter;
        }

        public String getInitialStatus() {
            return initialStatus;
        }

        public String[] getStatusesToCheck() {
            return statusesToCheck;
        }

        public String[] getExtraFields() {
            return extraFields;
        }

        public Params setExtraFields(String... extraFields) {
            this.extraFields = extraFields;
            return this;
        }

        public String[] getExtraFieldsNames() {
            return extraFieldsNames;
        }

        public Params setExtraFieldsNames(String... extraFieldsNames) {
            this.extraFieldsNames = extraFieldsNames;
            return this;
        }

        public String[] getFinalStatuses() {
            return finalStatuses;
        }

        public Params setFinalStatuses(String... finalStatuses) {
            this.finalStatuses = finalStatuses;
            return this;
        }

        public List<? extends ITicket> getScope() {
            return scope;
        }
    }
}
