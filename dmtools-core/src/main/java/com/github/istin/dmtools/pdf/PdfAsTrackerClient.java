// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.pdf.model.PdfPageAsTicket;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfAsTrackerClient implements TrackerClient<PdfPageAsTicket> {
    private static final Logger logger = LogManager.getLogger(PdfAsTrackerClient.class);
    public static void main(String[] args) {

    }

    private String folderWithPdfAssets;
    private boolean isWasParsed = false;

    public PdfAsTrackerClient(String folderWithPdfAssets) {
        this.folderWithPdfAssets = folderWithPdfAssets;
    }

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String tag(String initiator) {
        return "";
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        return ticket.getTicketDescription();
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String buildUrlToSearch(String query) {
        return query;
    }

    @Override
    public String getBasePath() {
        return folderWithPdfAssets;
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        return "";
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TextType getTextType() {
        return TextType.HTML;
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        String ticketFolder = findTicketFolder(ticket.getTicketKey());
        String fileString = readStringFromFile(ticketFolder, "labels.json");
        JSONArray labels = null;
        if (fileString == null) {
            labels = new JSONArray();
        } else {
            labels = new JSONArray(fileString);
        }
        if (!labels.toString().contains(label)) {
            labels.put(label);
            FileUtils.write(new File(ticketFolder, "labels.json"), labels.toString());
        }
    }

    @Override
    public PdfPageAsTicket createTicket(String body) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void updatePageSnapshot(PdfPageAsTicket ticket, String pageSnapshot) throws IOException {
        String ticketFolder = findTicketFolder(ticket.getTicketKey());
        FileUtils.write(new File(ticketFolder, "snapshot_description.txt"), pageSnapshot);
    }

    @Override
    public void deleteLabelInTicket(PdfPageAsTicket ticket, String label) throws IOException {
        String ticketFolder = findTicketFolder(ticket.getTicketKey());
        String fileString = readStringFromFile(ticketFolder, "labels.json");
        JSONArray labels = null;
        if (fileString == null) {
            labels = new JSONArray();
        } else {
            labels = new JSONArray(fileString);
        }
        for (int i=0; i < labels.length(); i++) {
            String labelFromArray = labels.getString(i);
            if (labelFromArray.equalsIgnoreCase(label)) {
                labels.remove(i);
                FileUtils.write(new File(ticketFolder, "labels.json"), labels.toString());
                break;
            }
        }
    }

    @Override
    public List<PdfPageAsTicket> searchAndPerform(String searchQuery, String[] fields) throws Exception {
        List<PdfPageAsTicket> tickets = new ArrayList<>();
        searchAndPerform(ticket -> {
            tickets.add(ticket);
            return false;
        }, searchQuery, fields);
        return tickets;
    }

    @Override
    public void searchAndPerform(JiraClient.Performer<PdfPageAsTicket> performer, String searchQuery, String[] fields) throws Exception {
        if (!isWasParsed) {
            ReadPDFFile.parsePdfFilesToTickets(folderWithPdfAssets);
            isWasParsed = true;
        }
        File cacheFolder = new File(folderWithPdfAssets + "/cache");
        File[] listOfFiles = cacheFolder.listFiles();

        if (listOfFiles == null) {
            return;
        }

        for (int i = 0; i < listOfFiles.length; i++) {
            File potentialPdfFileDir = listOfFiles[i];
            if (potentialPdfFileDir.isDirectory()) {
                if (searchAndPerformForFileTickets(performer, searchQuery, fields, potentialPdfFileDir)) {
                    return;
                }
            }

        }
    }

    private boolean searchAndPerformForFileTickets(JiraClient.Performer<PdfPageAsTicket> performer, String searchQuery, String[] fields, File fileCacheFolder) throws Exception {
        File[] listOfTickets = fileCacheFolder.listFiles();
        for (int i = 0; i < listOfTickets.length; i++) {
            File file = listOfTickets[i];
            if (file.isDirectory()) {
                try {
                    Integer number = Integer.parseInt(file.getName());
                } catch (Exception e) {
                    continue;
                }

                PdfPageAsTicket ticket = performTicket(fileCacheFolder.getName() + "-" + (i + 1), fields);
                if (ticket == null) {
                    continue;
                }

                if (searchQuery != null) {
                    String[] paramAndValue = searchQuery.split("=");
                    if (paramAndValue[0].equalsIgnoreCase("labels")) {
                        if (TrackerClient.Utils.isLabelExists(ticket, paramAndValue[1])) {
                            boolean isStopping = performer.perform(ticket);
                            if (isStopping) {
                                return true;
                            }
                        }
                    } else {
                        boolean isStopping = performer.perform(ticket);
                        if (isStopping) {
                            return true;
                        }
                    }
                } else {
                    boolean isStopping = performer.perform(ticket);
                    if (isStopping) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public PdfPageAsTicket performTicket(String ticketKey, String[] fields) throws IOException {
        String pathToTicketFolder = findTicketFolder(ticketKey);

        PdfPageAsTicket pdfPageAsTicket = new PdfPageAsTicket();
        pdfPageAsTicket.setKey(ticketKey);
        String trimmedString = readStringFromFile(pathToTicketFolder, "description.txt");
        trimmedString = StringUtils.normalizeSpace(trimmedString);
        pdfPageAsTicket.setDescription(trimmedString);

        pdfPageAsTicket.setSnapshotDescription(readStringFromFile(pathToTicketFolder, "snapshot_description.txt"));

        String labelsResponse = readStringFromFile(pathToTicketFolder, "labels.json");
        if (labelsResponse == null) {
            pdfPageAsTicket.setLabels(null);
        } else {
            pdfPageAsTicket.setLabels(new JSONArray(labelsResponse));
        }
        pdfPageAsTicket.setPageSnapshot(new File(pathToTicketFolder + "/page_snapshot.png"));

        File[] listOfFiles = new File(pathToTicketFolder).listFiles();

        logger.info(pathToTicketFolder);
        if (listOfFiles != null) {
            for (int i = 0; i < listOfFiles.length; i++) {
                File file = listOfFiles[i];
                if (file.isFile() && file.getName().startsWith("attachment")) {
                    pdfPageAsTicket.addAttachment(file);
                }
            }
        } else {
            logger.info("empty folder {}", pathToTicketFolder);
            return null;
        }
        return pdfPageAsTicket;
    }

    private static @NotNull String readStringFromFile(String pathToTicketFolder, String fileName) throws IOException {
        File file = new File(pathToTicketFolder + "/" + fileName);
        if (file.exists()) {
            return FileUtils.readFileToString(file, "UTF-8").trim();
        } else {
            return null;
        }
    }

    private @NotNull String findTicketFolder(String ticketKey) {
        int beginIndex = ticketKey.lastIndexOf("-");
        String pageNumber = ticketKey.substring(beginIndex + 1);
        String pathToTicketFolder = getBasePath() + "/cache/" + ticketKey.substring(0, beginIndex) + "/" + pageNumber;
        return pathToTicketFolder;
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getDefaultQueryFields() {
        return new String[0];
    }

    @Override
    public String[] getExtendedQueryFields() {
        return new String[0];
    }

    @Override
    public String getDefaultStatusField() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {

    }

    @Override
    public void setCacheGetRequestsEnabled(boolean isCacheOfGetRequestsEnabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValidImageUrl(String url) {
        throw new UnsupportedOperationException();
    }

    @Override
    public File convertUrlToFile(String href) throws Exception {
        throw new UnsupportedOperationException();
    }

}
