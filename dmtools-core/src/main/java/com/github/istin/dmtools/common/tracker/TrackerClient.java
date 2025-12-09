package com.github.istin.dmtools.common.tracker;

import com.github.istin.dmtools.atlassian.confluence.ContentUtils;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IChangelog;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface TrackerClient<T extends ITicket> extends ContentUtils.UrlToImageFile {

    String linkIssueWithRelationship(String sourceKey, String anotherKey, String relationship) throws IOException;

    String tag(String initiator);

    String getTextFieldsOnly(ITicket ticket);

    enum TextType {
        HTML, MARKDOWN
    }

    String updateDescription(String key, String description) throws IOException;

    String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException;

    String buildUrlToSearch(String query);

    String getBasePath();

    String getTicketBrowseUrl(String ticketKey);

    String assignTo(String ticketKey, String userName) throws IOException;

    IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException;

    void deleteLabelInTicket(T ticket, String label) throws IOException;

    void addLabelIfNotExists(ITicket ticket, String label) throws IOException;

    String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException;

    T createTicket(String body);

    List<T> searchAndPerform(String searchQuery, String[] fields) throws Exception;

    void searchAndPerform(JiraClient.Performer<T> performer, String searchQuery, String[] fields) throws Exception;

    T performTicket(String ticketKey, String[] fields) throws IOException;

    void postCommentIfNotExists(String ticketKey, String comment) throws IOException;

    List<? extends IComment> getComments(String ticketKey, ITicket ticket) throws IOException;

    void postComment(String ticketKey, String comment) throws IOException;

    void deleteCommentIfExists(String ticketKey, String comment) throws IOException;

    String moveToStatus(String ticketKey, String statusName) throws IOException;

    String[] getDefaultQueryFields();

    String[] getExtendedQueryFields();

    String getDefaultStatusField();
    
    /**
     * Resolves a field name to its tracker-specific field code.
     * For Jira: converts human-friendly names to custom field IDs (e.g., "Story Points" -> "customfield_10016")
     * For ADO: adds namespace prefix if needed (e.g., "Description" -> "System.Description")
     * 
     * @param ticketKey The ticket key for context (used to extract project in Jira)
     * @param fieldName The human-friendly field name
     * @return The resolved field code/ID for the tracker
     * @throws IOException if field resolution fails
     */
    default String resolveFieldName(String ticketKey, String fieldName) throws IOException {
        // Default implementation: return field name as-is
        return fieldName;
    }

    List<? extends ITicket> getTestCases(ITicket ticket) throws IOException;

    void setLogEnabled(boolean isLogEnabled);

    void setCacheGetRequestsEnabled(boolean isCacheOfGetRequestsEnabled);

    List<? extends ReportIteration> getFixVersions(String projectCode) throws IOException;

    TextType getTextType();

    void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException;

    interface TrackerTicketFields {
        void set(String key, Object object);
    }

    interface FieldsInitializer {

        void init(TrackerTicketFields fields);

    }

    class Utils {
        public static String checkCommentStartedWith(TrackerClient trackerClient, String key, ITicket ticket, String commentPrefix) throws IOException {
            List<IComment> comments = trackerClient.getComments(key, ticket);
            return IComment.Impl.checkCommentStartedWith(comments, commentPrefix);
        }

        public static boolean isLabelExists(ITicket ticket, String label) {
            JSONArray ticketLabels = ticket.getTicketLabels();
            if (ticketLabels == null) {
                return false;
            }
            for (int i = 0; i < ticketLabels.length(); i++) {
                String labelFromArray = ticketLabels.getString(i);
                if (labelFromArray.equalsIgnoreCase(label)) {
                    return true;
                }
            }
            return false;
        }
    }

}
