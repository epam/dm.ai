package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;
import java.util.*;

public class BasicJiraClient extends JiraClient<Ticket> {

    public static final String BASE_PATH;
    public static final String TOKEN;
    public static final String AUTH_TYPE;

    private static final boolean IS_JIRA_LOGGING_ENABLED;
    private static final boolean IS_JIRA_CLEAR_CACHE;

    private static final boolean IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES;

    private static final boolean IS_JIRA_WAIT_BEFORE_PERFORM;

    private static final Long SLEEP_TIME_REQUEST;

    private static final String[] JIRA_EXTRA_FIELDS;

    public static final String JIRA_EXTRA_FIELDS_PROJECT;

    private static final int JIRA_SEARCH_MAX_RESULTS;

    public static final String[] DEFAULT_QUERY_FIELDS = {
            Fields.SUMMARY,
            Fields.STATUS,
            Fields.ATTACHMENT,
            Fields.UPDATED,
            Fields.CREATED,
            Fields.CREATOR,
            Fields.REPORTER,
            Fields.COMPONENTS,
            Fields.ISSUETYPE,
            Fields.FIXVERSIONS,
            Fields.STORY_POINTS,
            Fields.LABELS,
            Fields.PRIORITY,
            Fields.PARENT
    };
    public static final String[] EXTENDED_QUERY_FIELDS = {
            Fields.DESCRIPTION,
            Fields.ISSUE_LINKS
    };

    static {
        PropertyReader propertyReader = new PropertyReader();
        BASE_PATH = propertyReader.getJiraBasePath();
        String jiraLoginPassToken = propertyReader.getJiraLoginPassToken();
        if (jiraLoginPassToken == null || jiraLoginPassToken.isEmpty()) {
            String email = propertyReader.getJiraEmail();
            String token = propertyReader.getJiraApiToken();
            if (email != null && token != null) {
                String credentials = email.trim() + ":" + token.trim();
                TOKEN = Base64.getEncoder().encodeToString(credentials.getBytes());
            } else {
                TOKEN = jiraLoginPassToken;
            }
        } else {
            TOKEN = jiraLoginPassToken;
        }
        AUTH_TYPE = propertyReader.getJiraAuthType();
        IS_JIRA_LOGGING_ENABLED = propertyReader.isJiraLoggingEnabled();
        IS_JIRA_CLEAR_CACHE = propertyReader.isJiraClearCache();
        IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES = propertyReader.isJiraTransformCustomFieldsToNames();
        IS_JIRA_WAIT_BEFORE_PERFORM = propertyReader.isJiraWaitBeforePerform();
        SLEEP_TIME_REQUEST = propertyReader.getSleepTimeRequest();
        JIRA_EXTRA_FIELDS = propertyReader.getJiraExtraFields();
        JIRA_EXTRA_FIELDS_PROJECT = propertyReader.getJiraExtraFieldsProject();
        JIRA_SEARCH_MAX_RESULTS = propertyReader.getJiraMaxSearchResults();
    }


    private static BasicJiraClient instance;
    private final String[] defaultJiraFields;
    private final String[] extendedJiraFields;
    private final String[] customCodesOfConfigFields;

    public static TrackerClient<? extends ITicket> getInstance() throws IOException {
        if (instance == null) {
            if (BASE_PATH == null || BASE_PATH.isEmpty()) {
                return null;
            }
            instance = new BasicJiraClient();
        }
        return instance;
    }

    public BasicJiraClient() throws IOException {
        super(BASE_PATH, TOKEN, JIRA_SEARCH_MAX_RESULTS);
        if (AUTH_TYPE != null) {
            setAuthType(AUTH_TYPE);
        }
        setLogEnabled(IS_JIRA_LOGGING_ENABLED);
        setWaitBeforePerform(IS_JIRA_WAIT_BEFORE_PERFORM);
        setSleepTimeRequest(SLEEP_TIME_REQUEST);
        setClearCache(IS_JIRA_CLEAR_CACHE);
        setTransformCustomFieldsToNames(IS_JIRA_TRANSFORM_CUSTOM_FIELDS_TO_NAMES);
        setProjectContext(JIRA_EXTRA_FIELDS_PROJECT);

        List<String> defaultFields = new ArrayList<>(Arrays.asList(DEFAULT_QUERY_FIELDS));

        if (JIRA_EXTRA_FIELDS_PROJECT != null && JIRA_EXTRA_FIELDS != null) {
            List<String> customCodes = new ArrayList<>();
            for (int i = 0; i < JIRA_EXTRA_FIELDS.length; i++) {
                String extraField = JIRA_EXTRA_FIELDS[i];
                // Use getAllFieldCustomCodes to get ALL custom fields with this name
                List<String> fieldCustomCodes = getAllFieldCustomCodes(JIRA_EXTRA_FIELDS_PROJECT, extraField);
                if (!fieldCustomCodes.isEmpty()) {
                    // Add ALL matching custom fields
                    customCodes.addAll(fieldCustomCodes);
                    defaultFields.addAll(fieldCustomCodes);
                    log("Extra field '" + extraField + "' resolved to " + fieldCustomCodes.size() + " custom fields: " + fieldCustomCodes);
                } else {
                    log("Extra field '" + extraField + "' not found in custom fields");
                }
            }
            customCodesOfConfigFields = customCodes.toArray(new String[0]);
        } else {
            customCodesOfConfigFields = null;
        }

        defaultJiraFields = defaultFields.toArray(new String[0]);

        List<String> extendedFields = new ArrayList<>();
        extendedFields.addAll(Arrays.asList(EXTENDED_QUERY_FIELDS));
        extendedFields.addAll(defaultFields);

        extendedJiraFields = extendedFields.toArray(new String[0]);
    }



    public String getTextFieldsOnly(ITicket ticket) {
        StringBuilder ticketDescription = null;
        try {
            ticketDescription = new StringBuilder(ticket.getTicketTitle());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ticketDescription.append("\n").append(ticket.getTicketDescription());
        if (customCodesOfConfigFields != null) {
            for (String customField : customCodesOfConfigFields) {
                if (customField != null) {
                    String value = ticket.getFields().getString(customField);
                    if (value != null) {
                        ticketDescription.append("\n").append(value);
                    }
                }
            }
        }
        return ticketDescription.toString();
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {

    }

    @Override
    public String[] getDefaultQueryFields() {
        return defaultJiraFields;
    }

    @Override
    public String[] getExtendedQueryFields() {
        return extendedJiraFields;
    }

    @Override
    public TextType getTextType() {
        return TextType.MARKDOWN;
    }
}
