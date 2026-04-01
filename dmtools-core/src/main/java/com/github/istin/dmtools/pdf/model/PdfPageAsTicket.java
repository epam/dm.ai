// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PdfPageAsTicket implements ITicket {

    private String key;
    private String description;
    private List<File> attachments = new ArrayList<>();
    private File pageSnapshot;
    private JSONArray labels;
    private String snapshotDescription;

    public PdfPageAsTicket() {

    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getStatus() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Status getStatusModel() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTicketKey() {
        return key;
    }

    @Override
    public String getIssueType() throws IOException {
        return "page";
    }

    @Override
    public String getTicketLink() {
        return getTicketKey();
    }

    @Override
    public String getPriority() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTicketTitle() throws IOException {
        return "";
    }

    public void setLabels(JSONArray labels) {
        this.labels = labels;
    }

    @Override
    public String getTicketDescription() {
        if (snapshotDescription != null) {
            return description + "\n" + snapshotDescription;
        }
        return description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getTicketDependenciesDescription() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getCreated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getUpdatedAsMillis() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IUser getCreator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resolution getResolution() {
        throw new UnsupportedOperationException();
    }

    public void setSnapshotDescription(String snapshotDescription) {
        this.snapshotDescription = snapshotDescription;
    }

    public String getSnapshotDescription() {
        return snapshotDescription;
    }

    @Override
    public JSONArray getTicketLabels() {
        return labels;
    }

    @Override
    public Fields getFields() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReportIteration getIteration() {
        return null;
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        return List.of();
    }

    @Override
    public double getProgress() throws IOException {
        throw new UnsupportedOperationException();
    }

    public List<File> getAttachmentsAsFiles() {
        return attachments;
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return attachments.stream().map((Function<File, IAttachment>) file -> new IAttachment() {
            @Override
            public String getName() {
                return file.getName();
            }

            @Override
            public String getUrl() {
                return file.getAbsolutePath();
            }

            @Override
            public String getContentType() {
                return "image/png";
            }
        }).collect(Collectors.toList());
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        return null;
    }

    @Override
    public String toText() throws IOException {
        return getTicketTitle() + "\n" + getTicketDescription();
    }

    @Override
    public double getWeight() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getKey() {
        return key;
    }

    public void addAttachment(File file) {
        attachments.add(file);
    }

    public void setPageSnapshot(File file) {
        this.pageSnapshot = file;
    }

    public File getPageSnapshot() {
        return pageSnapshot;
    }

}
