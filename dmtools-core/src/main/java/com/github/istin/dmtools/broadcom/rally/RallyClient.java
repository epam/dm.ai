// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally;

import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.broadcom.rally.model.*;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.ImageUtils;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class RallyClient extends AbstractRestClient implements TrackerClient<RallyIssue> {
    private static final Logger logger = LogManager.getLogger(RallyClient.class);
    private boolean isLogEnabled = true;

    public RallyClient(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("zsessionid", authorization)
                .header("Content-Type", "application/json");
    }

    @Override
    public String path(String path) {
        return basePath + "/slm/webservice/v2.0/" + path;
    }

    public String search(String query, String[] fields) throws IOException {
        GenericRequest genericRequest = search(query);
        genericRequest.setIgnoreCache(true);
        genericRequest.fields(fields);
        return execute(genericRequest);
    }

    @NotNull
    private GenericRequest search(String query) {
        return new GenericRequest(this, path("search/enhanced?query=" + query), "fetch");
    }
    public void clearCacheForIssue(String formattedID, String[] fields) {
        GenericRequest genericRequest = search("(FormattedId = " + formattedID+")");
        genericRequest.fields(fields);
        clearCache(genericRequest);
    }

    public RallyIssue getIssue(String formattedID, String[] fields) throws IOException {
        List<RallyIssue> issues = new RallyResponse(search("(FormattedId = " + formattedID+")", fields)).getQueryResult().getIssues();
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        for (RallyIssue issue : issues) {
            if (issue.getTicketKey().equalsIgnoreCase(formattedID)) {
                return issue;
            }
        }
        return null;
    }

    public String getBrowseUrl(RallyIssue issue) {
        return getTicketBrowseUrl(issue.getRef());
    }

    public static String getLastTwoSegments(String url) {
        if (url == null) {
            return null;
        }

        String[] segments = url.split("/");

        if (segments.length < 2) {
            return url;
        }

        return segments[segments.length - 2] + "/" + segments[segments.length - 1];
    }

    @Override
    public String tag(String initiator) {
        return "";
    }

    @Override
    public String getTicketBrowseUrl(String ticketKey) {
        return basePath + "/#/?detail=/" + getLastTwoSegments(ticketKey) + "&fdp=true";
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        RallyIssue issue;
        if (ticket instanceof RallyIssue) {
            issue = ((RallyIssue) ticket);
        } else {
            issue = getIssue(ticketKey, getDefaultQueryFields());
        }

        String ref = issue.getRevisionHistory().getRef();
        GenericRequest genericRequest = new GenericRequest(this, ref + "/revisions")
                .param("start", 1)
                .param("pagesize", 500);
        clearRequestIfExpired(genericRequest, ticket != null ? ticket.getUpdatedAsMillis() : null);
        return new RallyResponse(genericRequest.execute()).getQueryResult();
    }

    @Override
    public TextType getTextType() {
        return TextType.HTML;
    }

    @Override
    public void deleteLabelInTicket(RallyIssue ticket, String label) throws IOException {
        JSONArray tagsRefsWithoutTag = ticket.getTagsRefsWithoutTag(label);
        JSONObject updateBody = new JSONObject().put("Tags", tagsRefsWithoutTag);
        JSONObject jsonObject = new JSONObject().put(ticket.getIssueType(), updateBody);
        String response = updateTicketWithTags(ticket.getRef(), jsonObject);
    }

    @Override
    public void addLabelIfNotExists(ITicket ticket, String label) throws IOException {
        JSONArray jsonArray = ticket.getTicketLabels();
        if (jsonArray == null) {
            jsonArray = new JSONArray();
        }
        boolean wasFound = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            if (label.equalsIgnoreCase(jsonArray.optString(i))) {
                wasFound = true;
            }
        }
        if (!wasFound) {
            JSONArray tagsRefs = ticket.getTicketLabels();
            // If the label was not found, first check if it exists in Rally and get its reference
            String tagRef = findOrCreateTag(label);
            // Now associate this tag with the ticket
            if (tagRef != null) {
                JSONObject tag = new JSONObject().put("_ref", tagRef);
                JSONArray tagsToUpdate = tagsRefs.put(tag);
                JSONObject updateBody = new JSONObject().put("Tags", tagsToUpdate);
                JSONObject jsonObject = new JSONObject().put(ticket.getIssueType(), updateBody);
                String response = updateTicketWithTags(((RallyIssue)ticket).getRef(), jsonObject);
                logger.info(response);
            }
        }
    }

    public String findOrCreateTag(String label) throws IOException {
        // This method should search for the tag by label and return its ref if it exists.
        // If it doesn't exist, create the tag and then return its new ref.
        // Placeholder for the actual implementation

        GenericRequest genericRequest = new GenericRequest(this, path("Tag?query=(Name = \"" + label + "\")&fetch=true"));
        genericRequest.setIgnoreCache(true);
        String response = genericRequest.execute();
        List<RallyTag> tags = new RallyResponse(response).getQueryResult().getTags();
        if (tags == null || tags.isEmpty()) {
            // Tag does not exist; create it
            JSONObject newTag = new JSONObject();
            newTag.put("Name", label);

            JSONObject tagWrapper = new JSONObject();
            tagWrapper.put("Tag", newTag);

            GenericRequest tagCreationRequest = new GenericRequest(this, path("Tag/create"));
            tagCreationRequest.setBody(tagWrapper.toString());
            String newCreatedTagResponse = post(tagCreationRequest);
            JSONObject createResponseObject = new JSONObject(newCreatedTagResponse);
            // Assuming the creation response gives you the created tag object
            logger.info(createResponseObject);
            return createResponseObject.getJSONObject("CreateResult").getJSONObject("Object").getString("_ref");
        } else {
            return tags.get(0).getRef();
        }
    }

    protected String updateTicketWithTags(String ticketRef, JSONObject updateBody) throws IOException {
        // This method should send a request to Rally's REST API to update the ticket, associating it with the newly added tags.
        // Placeholder for the actual implementation
        logger.info("Updating ticket {} with tags: {}", ticketRef, updateBody.toString());
        // Send the update request to Rally here using a similar method to post(genericRequest);
        GenericRequest genericRequest = new GenericRequest(this, ticketRef);
        genericRequest.setBody(updateBody.toString());
        return put(genericRequest);
    }


    @Override
    public List<RallyIssue> searchAndPerform(String searchQuery, String[] fields) throws Exception {
        List<RallyIssue> tickets = new ArrayList<>();
        searchAndPerform(ticket -> {
            tickets.add(ticket);
            return false;
        }, searchQuery, fields);
        return tickets;
    }

    private Map<String, Integer> jqlExpirationInHours = new HashMap<>();

    public void setCacheExpirationForJQLInHours(String jql, Integer expirationValueInHours) {
        jqlExpirationInHours.put(jql, expirationValueInHours);
    }

    public RallyResponse search(String searchQuery, int startAt, String[] fields) throws IOException {
        GenericRequest searchQuerySearchRequest = search(searchQuery).
                fields(fields)
                .param("start", String.valueOf(startAt))
                .param("pagesize", 500);

        Integer expired = jqlExpirationInHours.get(searchQuery);
        if (expired != null) {
            clearRequestIfExpired(searchQuerySearchRequest, System.currentTimeMillis() - expired*60*60*1000);
        }


        try {
            String body = searchQuerySearchRequest.execute();
            return new RallyResponse(body);
        } catch (JSONException e) {
            clearCache(searchQuerySearchRequest);
            String body = searchQuerySearchRequest.execute();
            try {
                return new RallyResponse(body);
            } catch (JSONException e1) {
                logger.error("response: {}", body);
                throw e1;
            }
        }

    }

    @Override
    public RallyIssue createTicket(String body) {
        return new RallyIssue(body);
    }

    protected RallyIssue createTicket(RallyIssue ticket) {
        return ticket;
    }

    protected Class<RallyIssue> getTicketClass() {
        return RallyIssue.class;
    }

    @Override
    public void searchAndPerform(JiraClient.Performer<RallyIssue> performer, String searchQuery, String[] fields) throws Exception {
        RallyIssueType.QueryAndTypes queryAndTypes = RallyIssueType.parseQuery(searchQuery);
        searchQuery = queryAndTypes.getQuery();

        int startAt = 1;
        RallyResponse searchResults = search(searchQuery, startAt, fields);
        JSONArray errorMessages = searchResults.getQueryResult().getErrors();
        if (errorMessages != null && !errorMessages.isEmpty()) {
            logger.error("Error", errorMessages);
            return;
        }
        int maxResults = searchResults.getQueryResult().getPageSize();
        int total = searchResults.getQueryResult().getTotalResultCount();
        if (total == 0) {
            return;
        }

        boolean isBreak = false;
        int ticketIndex = 0;
        while (startAt == 1 || startAt < total) {
            startAt = startAt + maxResults;
            List<RallyIssue> tickets = searchResults.getQueryResult().getIssues();
            for (RallyIssue ticket : tickets) {
                List<String> types = queryAndTypes.getTypes();

                if (types != null) {
                    if (types.contains(ticket.getType())) {
                        isBreak = performer.perform(createTicket(ticket));
                    }
                } else {
                    isBreak = performer.perform(createTicket(ticket));
                }

                if (isBreak) {
                    break;
                }
                ticketIndex++;
            }
            if (isBreak) {
                break;
            }
            if (total < maxResults || startAt > total) {
                break;
            }
            searchResults = search(searchQuery, startAt, fields);
            maxResults = searchResults.getQueryResult().getPageSize();
            total = searchResults.getQueryResult().getTotalResultCount();
        }

    }

    @Override
    public RallyIssue performTicket(String ticketKey, String[] fields) throws IOException {
        return getIssue(ticketKey, fields);
    }

    @Override
    public void postCommentIfNotExists(String ticketKey, String text) throws IOException {
        List<? extends IComment> comments = getComments(ticketKey, null);
        if (comments != null) {
            for (IComment commentObject : comments) {
                if (text.equalsIgnoreCase(commentObject.getBody()) || ("<p>" + text + "</p>").equalsIgnoreCase(commentObject.getBody())) {
                    return;
                }
            }
        }
        postComment(ticketKey, text);
    }

    @Override
    public List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException {
        if (ticket == null) {
            ticket = performTicket(ticketKey, getDefaultQueryFields());
        }

        String ref = ((RallyIssue)ticket).getRef();
        GenericRequest genericRequest = createDiscussionGetRequest(ref);
        genericRequest.setIgnoreCache(true);
        clearRequestIfExpired(genericRequest, ticket.getUpdatedAsMillis());
        String response = genericRequest.execute();
        return new RallyResponse(response).getQueryResult().getComments();
    }

    private GenericRequest createDiscussionGetRequest(String ref) {
        return new GenericRequest(this, ref + "/Discussion")
                .param("order", "CreationDate DESC")
                .param("pagesize", 100)
                .param("start", 1);
    }

    @Override
    public void postComment(String ticketKey, String comment) throws IOException {
        RallyIssue ticket = performTicket(ticketKey, getDefaultQueryFields());
        String ref = ticket.getRef();
        JSONObject body = new JSONObject().put("ConversationPost",
                new JSONObject()
                        .put("Artifact", ref)
                        .put("Text", comment));
        GenericRequest genericRequest = new GenericRequest(this, path("conversationpost/create"));
        genericRequest.setBody(body.toString());
        post(genericRequest);
        clearCache(createDiscussionGetRequest(ref));
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String text) throws IOException {
        List<? extends IComment> comments = getComments(ticketKey, null);
        if (comments != null) {
            for (IComment commentObject : comments) {
                if (text.equalsIgnoreCase(commentObject.getBody()) || ("<p>" + text + "</p>").equalsIgnoreCase(commentObject.getBody())) {

                    // Assuming the ref is the full URL to the conversation post. If not, you might need to construct the URL.
                    GenericRequest genericRequest = new GenericRequest(this, ((Comment)commentObject).getRef());

                    // Executing DELETE request on the conversation post ref
                    delete(genericRequest);

                    // Optionally, clear cache or perform any cleanup if needed
                    RallyIssue ticket = performTicket(ticketKey, getDefaultQueryFields());
                    String ref = ticket.getRef();
                    clearCache(createDiscussionGetRequest(ref));
                    return;
                }
            }
        }
    }

    @Override
    public String moveToStatus(String ticketKey, String statusName) throws IOException {
        RallyIssue rallyIssue = performTicket(ticketKey, getDefaultQueryFields());
        if (rallyIssue.getStatus().equalsIgnoreCase(statusName)) {
            return null;
        }

        GenericRequest genericRequest = new GenericRequest(this, path("flowstate"));
        genericRequest
                .param("order", "OrderIndex ASC")
                .param("pagesize", 50)
                .param("start", 1)
                .param("query", "(Project.Name = \""+rallyIssue.getProjectName()+"\")");
        String response = genericRequest.execute();
        List<FlowState> flowStates = new RallyResponse(response).getQueryResult().getFlowStates();
        for (FlowState flowState : flowStates) {
            if (flowState.getRefObjectName().equalsIgnoreCase(statusName)) {
                GenericRequest updateFlowState = new GenericRequest(this, rallyIssue.getRef());
                updateFlowState.setBody(new JSONObject()
                        .put(rallyIssue.getType(),
                                new JSONObject().put(RallyFields.FLOW_STATE, flowState.getRef())
                        ).toString());
                String post = updateFlowState.post();
                clearCacheForIssue(ticketKey, getDefaultQueryFields());
                return post;
            }
        }
        return null;
    }

    public List<Iteration> iterations(String projectName, Calendar startDateCalendar) throws IOException {
        String startDate = DateUtils.formatToRallyDate(startDateCalendar);
//        String endDate = "And (EndDate > 2023-12-20T23:00:00.000Z)";
        String endDate = "";
        GenericRequest genericRequest = new GenericRequest(this, path("iteration?fetch=StartDate,EndDate,Name,Project,State&query=((Project.Name Contains \""+projectName+ "\") And (StartDate > " + startDate + "))" + endDate + "&order=StartDate ASC"))
                .param("start", 1)
                .param("pagesize", 500);
        QueryResult queryResult = new RallyResponse(genericRequest.execute()).getQueryResult();
        logger.info(queryResult.getJSONObject());
        return queryResult.getIterations();
    }

    @Override
    public String getDefaultStatusField() {
        return "FLOW STATE";
    }

    @Override
    public String[] getExtendedQueryFields() {
        return RallyFields.DEFAULT_EXTENDED;
    }

    public File downloadAttachment(String path) throws IOException {
        if (!path.startsWith("http")) {
            path = basePath + path;
        }
        GenericRequest genericRequest = new GenericRequest(this, path);
        return Impl.downloadFile(this, genericRequest, getCachedFile(genericRequest));
    }

    public String downloadImageAsBase64(String path) throws IOException {
        File imageFile = downloadAttachment(path);
        String[] split = path.split("\\.");
        String formatName = split[split.length - 1];
        return ImageUtils.convertToBase64(imageFile, formatName);
    }

    @Override
    public String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getCachedFile(GenericRequest genericRequest) {
        String url = genericRequest.url();
        String value = DigestUtils.md5Hex(url);
        String imageExtension = Impl.getFileImageExtension(url);
        return new File(getCacheFolderName() + "/" + value + imageExtension);
    }

    @Override
    public boolean isValidImageUrl(String url) {
        return url.contains("rally1.rallydev.com") && (url.endsWith("png") || url.endsWith("jpg") || url.endsWith("jpeg"));
    }

    @Override
    public File convertUrlToFile(String href) throws IOException {
        return downloadAttachment(href);
    }

    @Override
    public void setLogEnabled(boolean isLogEnabled) {
        this.isLogEnabled = isLogEnabled;
    }

    @Override
    public List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -100);
        return iterations(projectCode, calendar);
    }

    @Override
    public String updateDescription(String key, String description) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        throw new UnsupportedOperationException();
    }
}
