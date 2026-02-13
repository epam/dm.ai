package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.TrackerRule;
import com.github.istin.dmtools.report.model.KeyTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class TrackerDataSource extends DataSource {
    private final TrackerClient trackerClient;
    private final String jql;
    private final List<String> extraFields;

    public TrackerDataSource(TrackerClient trackerClient, String jql) {
        this.trackerClient = trackerClient;
        this.jql = jql;
        this.extraFields = null;
    }

    public TrackerDataSource(TrackerClient trackerClient, String jql, List<String> extraFields) {
        this.trackerClient = trackerClient;
        this.jql = jql;
        this.extraFields = (extraFields == null || extraFields.isEmpty()) ? null : extraFields;
    }

    @Override
    public void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception {
        TrackerRule rule = metric.getRule();
        if (rule == null) {
            return;
        }

        trackerClient.searchAndPerform(new JiraClient.Performer<ITicket>() {
            @Override
            public boolean perform(ITicket ticket) throws Exception {
                try {
                    List<KeyTime> keyTimes = rule.check(trackerClient, ticket);

                    if (!keyTimes.isEmpty()) {
                        JSONObject rawMetadata = extractRawMetadata(ticket);
                        collector.collect(keyTimes, rawMetadata, ticket.getTicketKey());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing ticket " + ticket.getTicketKey() + ": " + e.getMessage());
                }
                return false;
            }
        }, jql, resolveQueryFields());
    }

    private String[] resolveQueryFields() {
        String[] defaults = trackerClient.getDefaultQueryFields();
        if (extraFields == null || extraFields.isEmpty()) {
            return defaults;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (defaults != null) {
            java.util.Collections.addAll(merged, defaults);
        }
        for (String f : extraFields) {
            if (f != null && !f.trim().isEmpty()) {
                merged.add(f.trim());
            }
        }
        return merged.toArray(new String[0]);
    }

    @Override
    public JSONObject extractRawMetadata(Object item) {
        ITicket ticket = (ITicket) item;

        // Build normalized metadata using ITicket interface methods
        // This works consistently across Jira, ADO, Rally, etc.
        JSONObject json = new JSONObject();
        json.put("key", ticket.getTicketKey());

        try {
            String title = ticket.getTicketTitle();
            if (title != null) json.put("summary", title);
        } catch (Exception e) { /* ignore */ }

        try {
            String status = ticket.getStatus();
            if (status != null) json.put("status", status);
        } catch (Exception e) { /* ignore */ }

        try {
            String priority = ticket.getPriority();
            if (priority != null) json.put("priority", priority);
        } catch (Exception e) { /* ignore */ }

        try {
            String issueType = ticket.getIssueType();
            if (issueType != null) json.put("issueType", issueType);
        } catch (Exception e) { /* ignore */ }

        try {
            double weight = ticket.getWeight();
            if (weight > 0) json.put("weight", weight);
        } catch (Exception e) { /* ignore */ }

        try {
            String link = ticket.getTicketLink();
            if (link != null) json.put("link", link);
        } catch (Exception e) { /* ignore */ }

        try {
            JSONArray labels = ticket.getTicketLabels();
            if (labels != null && labels.length() > 0) json.put("labels", labels);
        } catch (Exception e) { /* ignore */ }

        try {
            java.util.Date created = ticket.getCreated();
            if (created != null) json.put("created", created.toInstant().toString());
        } catch (Exception e) { /* ignore */ }

        return json;
    }

    @Override
    public String getSourceName() {
        return "tracker";
    }
}
