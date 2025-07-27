package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.model.*;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.IntegrationType;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import okhttp3.OkHttpClient.Builder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class JiraClient<T extends Ticket> implements RestClient, TrackerClient<T>, UriToObject {
    private final Logger logger;  // Changed from static to instance member
    public static final String PARAM_JQL = "jql";
    public static final String PARAM_FIELDS = "fields";
    public static final String PARAM_START_AT = "startAt";
    private final OkHttpClient client;
    private String basePath;
    private boolean isReadCacheGetRequestsEnabled = true;
    private boolean isWaitBeforePerform = false;
    @Setter
    @Getter
    private long sleepTimeRequest;
    private String authorization;
    private String cacheFolderName;
    private boolean isClearCache = false;
    @Setter
    @Getter
    private String authType = "Basic";
    private Long instanceCreationTime = System.currentTimeMillis();

    private boolean isLogEnabled = true;

    public void setClearCache(boolean clearCache) throws IOException {
        isClearCache = clearCache;
        initCache();
    }
    public static String parseJiraProject(String key) {
        return key.split("-")[0].toUpperCase();
    }

    public void setLogEnabled(boolean logEnabled) {
        isLogEnabled = logEnabled;
    }

    // Default constructor - backward compatibility
    public JiraClient(String basePath, String authorization) throws IOException {
        this(basePath, authorization, LogManager.getLogger(JiraClient.class));
    }
    
    // NEW: Constructor with logger injection for server-managed mode
    public JiraClient(String basePath, String authorization, Logger logger) throws IOException {
        this.basePath = basePath;
        this.authorization = authorization;
        this.logger = logger != null ? logger : LogManager.getLogger(JiraClient.class);
        Builder builder = new Builder();
        builder.connectTimeout(20, TimeUnit.SECONDS);
        builder.writeTimeout(20, TimeUnit.SECONDS);
        builder.readTimeout(20, TimeUnit.SECONDS);
        this.client = builder.build();

        setCacheFolderNameAndReinit("cache" + getClass().getSimpleName());
    }

    protected void initCache() throws IOException {
        File cache = new File(getCacheFolderName());
        log("cache folder: " + cache.getAbsolutePath());
        if (!cache.exists()) {
            cache.mkdirs();
        }
        if (isClearCache) {
            cache.mkdirs();
            FileUtils.deleteDirectory(cache);
        }
    }

    @Override
    @MCPTool(
            name = "jira_get_ticket_browse_url",
            description = "return jira ticket url to open in broswer",
            integration = "jira",
            category = "data_extraction"
    )
    public String getTicketBrowseUrl(@MCPParam(name = "ticket_key", description = "The Jira ticket key to to generate url to", required = true)
                                         String ticketKey
    ) {
        return basePath + "/browse/" + ticketKey;
    }

    public void setCacheGetRequestsEnabled(boolean cacheGetRequestsEnabled) {
        isReadCacheGetRequestsEnabled = cacheGetRequestsEnabled;
    }

    public GenericRequest search() {
        return new GenericRequest(this, path("search"));
    }

    public void deleteIssueLink(String id) throws IOException {
        new GenericRequest(this, path("issueLink/" + id)).delete();
    }

    @Override
    public String assignTo(String ticketKey, String userName) throws IOException {
        GenericRequest jiraRequest = new GenericRequest(this, path("issue/" + ticketKey + "/assignee"));
        jiraRequest.setBody(new JSONObject().put("name", userName).toString());
        return jiraRequest.put();
    }

    @Override
    public IChangelog getChangeLog(String ticketKey, ITicket ticket) throws IOException {
        GenericRequest genericRequest = createChangelogRequest(ticketKey);
        clearRequestIfExpired(genericRequest, ticket != null ? ticket.getUpdatedAsMillis() : null);
        String body = null;
        try {
            body = genericRequest.execute();
            return createTicket(body).getChangelog();
        } catch (JSONException e) {
            logger.error(body);
            logger.error(ticketKey);
            logger.error(e);
            clearCache(genericRequest);
            return createTicket(genericRequest.execute()).getChangelog();
        }
    }

    private void clearRequestIfExpired(GenericRequest genericRequest, Long updated) throws IOException {
        File cachedFile = getCachedFile(genericRequest);
        clearRequestIfExpired(genericRequest, updated, cachedFile);
    }

    private void clearRequestIfExpired(GenericRequest genericRequest, Long updated, File cachedFile) throws IOException {
        if (cachedFile.exists() && updated != null) {
            BasicFileAttributes attr = Files.readAttributes(cachedFile.toPath(), BasicFileAttributes.class);
            FileTime fileTime = attr.lastModifiedTime();
            if (fileTime.toMillis() < updated) {
                clearCache(genericRequest);
            }
        }
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
            jsonArray.put(label);
            log(updateField(ticket.getKey(), Fields.LABELS, jsonArray));
        }
    }

    @Override
    public void deleteLabelInTicket(T ticket, String label) throws IOException {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public GenericRequest createChangelogRequest(String key) {
        return new GenericRequest(this, path("issue/" + key + "?expand=changelog&fields=summary"));
    }

    protected T createTicket(String body) {
        return (T) new Ticket(body);
    }

    protected T createTicket(Ticket ticket) {
        return (T) ticket;
    }

    protected Class<? extends Ticket> getTicketClass() {
        return Ticket.class;
    }


    public void log(String message) {
        if (isLogEnabled) {
            logger.info(message);
        }
    }

    private Map<String, Integer> jqlExpirationInHours = new HashMap<>();

    public void setCacheExpirationForJQLInHours(String jql, Integer expirationValueInHours) {
        jqlExpirationInHours.put(jql, expirationValueInHours);
    }


    public interface Performer<T extends ITicket> {

        boolean perform(T ticket) throws Exception;

    }

    public static abstract class ProgressPerformer implements Performer<Ticket> {

        public abstract boolean perform(Ticket ticket, int index, int start, int end) throws Exception;

        @Override
        public boolean perform(Ticket ticket) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @MCPTool(
            name = "jira_search_and_perform",
            description = "Search for Jira tickets using JQL and perform an action on each ticket",
            integration = "jira",
            category = "search"
    )
    public List<T> searchAndPerform(String searchQueryJQL, String[] fields) throws Exception {
        List<T> tickets = new ArrayList<>();
        searchAndPerform(ticket -> {
            tickets.add(ticket);
            return false;
        }, searchQueryJQL, fields);
        return tickets;
    }


    @Override
    public void searchAndPerform(Performer<T> performer, String searchQueryJQL, String[] fields) throws Exception {
        int startAt = 0;
        SearchResult searchResults = search(searchQueryJQL, startAt, fields);
        JSONArray errorMessages = searchResults.getErrorMessages();
        if (errorMessages != null) {
            System.err.println(errorMessages);
            return;
        }
        int maxResults = searchResults.getMaxResults();
        int total = searchResults.getTotal();
        if (total == 0) {
            log("total search query results: " + 0);
            return;
        }

        boolean isBreak = false;
        int ticketIndex = 0;
        while (startAt == 0 || startAt < total) {
            startAt = startAt + maxResults;
            List<Ticket> tickets = searchResults.getIssues();
            for (Ticket ticket : tickets) {
                if (performer instanceof ProgressPerformer) {
                    isBreak = ((ProgressPerformer) performer).perform(createTicket(ticket), ticketIndex, startAt, total);
                    log("total search query results: " + total);
                } else {
                    isBreak = performer.perform(createTicket(ticket));
                    log("total search query results: " + total);
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
            log(startAt + " " + total);
            searchResults = search(searchQueryJQL, startAt, fields);
            maxResults = searchResults.getMaxResults();
            total = searchResults.getTotal();
        }

    }

    @MCPTool(
            name = "jira_search_with_pagination",
            description = "Search for Jira tickets using JQL with pagination support",
            integration = "jira",
            category = "search"
    )
    public SearchResult search(
        @MCPParam(name = "jql", description = "JQL query string to search for tickets", required = true, example = "project = PROJ AND status = Open")
        String jql,
        @MCPParam(name = "startAt", description = "Starting index for pagination (0-based)", required = true, example = "0")
        int startAt,
        @MCPParam(name = "fields", description = "Array of field names to include in the response", required = true, example = "['summary', 'status', 'assignee']")
        String[] fields
    ) throws IOException {
        GenericRequest jqlSearchRequest = search().
                param(PARAM_JQL, jql)
                .param(PARAM_FIELDS, StringUtils.concatenate(",", fields))
                .param(PARAM_START_AT, String.valueOf(startAt));

        Integer expired = jqlExpirationInHours.get(jql);
        if (expired != null) {
            clearRequestIfExpired(jqlSearchRequest, System.currentTimeMillis() - expired*60*60*1000);
        }


        try {
            String body = jqlSearchRequest.execute();
            return new SearchResult(body);
        } catch (JSONException e) {
            clearCache(jqlSearchRequest);
            String body = jqlSearchRequest.execute();
            try {
                return new SearchResult(body);
            } catch (JSONException e1) {
                logger.error("response: {}", body);
                throw e1;
            }
        }

    }

    public GenericRequest filter(String id) {
        return new GenericRequest(this, path("filter/" + id));
    }

    public String filterJQL(String id) throws IOException {
        JSONObject jsonObject = new JSONObject(filter(id).execute());
        return getQueryMap(new URL(jsonObject.optString("searchUrl")).getQuery()).get("jql");
    }

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }

    public GenericRequest getTicket(final String ticket) {
        return new GenericRequest(this, path("issue/" + ticket));
    }

    public GenericRequest getWorklog(final String ticket) {
        return new GenericRequest(this, path("issue/" + ticket + "/worklog"));
    }

    public GenericRequest getConfig() {
        return new GenericRequest(this, path("serverInfo"));
    }

    @MCPTool(
            name = "jira_get_my_profile",
            description = "Get the current user's profile information from Jira",
            integration = "jira",
            category = "user_management"
    )
    public IUser performMyProfile() throws IOException {
        return new Assignee(new GenericRequest(this, path("myself")).execute());
    }

    @MCPTool(
            name = "jira_get_user_profile",
            description = "Get a specific user's profile information from Jira",
            integration = "jira",
            category = "user_management"
    )
    public IUser performProfile(@MCPParam(name = "userId", description = "The user ID to get profile for", required = true) String userId) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("user"));
        genericRequest.param("accountId", userId);
        return new Assignee(genericRequest.execute());
    }

    @Override
    @MCPTool(
            name = "jira_get_ticket",
            description = "Get a specific Jira ticket by key with optional field filtering",
            integration = "jira",
            category = "ticket_management"
    )
    public T performTicket(@MCPParam(name = "ticketKey", description = "The Jira ticket key to retrieve", required = true) String ticketKey, 
                          @MCPParam(name = "fields", description = "Optional array of fields to include in the response", required = false) String[] fields) throws IOException {
        GenericRequest jiraRequest = createPerformTicketRequest(ticketKey, fields);
        String response = jiraRequest.execute();
        if (response.contains("errorMessages")) {
            return null;
        }
        return createTicket(response);
    }

    protected GenericRequest createPerformTicketRequest(String ticketKey, String[] fields) {
        GenericRequest jiraRequest = getTicket(ticketKey);
        if (fields != null && fields.length > 0) {
            jiraRequest.param("fields", StringUtils.concatenate(",", fields));
        }
        return jiraRequest;
    }

    public List<RemoteLink> performGettingRemoteLinks(String ticket) throws IOException {
        return JSONModel.convertToModels(RemoteLink.class, new JSONArray(getRemoteLinks(ticket).execute()));
    }

    private boolean subtasksCallIsNotSupported = false;

    @MCPTool(
            name = "jira_get_subtasks",
            description = "Get all subtasks of a specific Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public List<T> performGettingSubtask(@MCPParam(name = "ticket", description = "The parent ticket key to get subtasks for", required = true) String ticket) throws IOException {
        if (subtasksCallIsNotSupported) {
            return Collections.emptyList();
        }
        GenericRequest subtasks = getSubtasks(ticket);
        try {
            return JSONModel.convertToModels(getTicketClass(), new JSONArray(subtasks.execute()));
        } catch (JSONException e) {
            clearCache(subtasks);
            throw e;
        } catch (AtlassianRestClient.RestClientException e) {
            subtasksCallIsNotSupported = true;
            e.getMessage().contains("404");
            return Collections.emptyList();
        }
    }

    public GenericRequest getSubtasks(final String ticket) {
        return new GenericRequest(this, path("issue/" + ticket + "/subtask"));
    }

    public GenericRequest getRemoteLinks(final String ticket) {
        return new GenericRequest(this, path("issue/" + ticket + "/remotelink"));
    }

    public GenericRequest comment(final String key, ITicket ticket) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("issue/" + key + "/comment"));
        clearRequestIfExpired(genericRequest, ticket == null ? null : ticket.getFields().getUpdatedAsMillis());
        return genericRequest;
    }

    @Override
    @MCPTool(
            name = "jira_post_comment_if_not_exists",
            description = "Post a comment to a Jira ticket only if it doesn't already exist",
            integration = "jira",
            category = "comment_management"
    )
    public void postCommentIfNotExists(@MCPParam(name = "ticketKey", description = "The Jira ticket key to post comment to", required = true) String ticketKey, 
                                     @MCPParam(name = "comment", description = "The comment text to post", required = true) String comment) throws IOException {
        if (getTextType() == TrackerClient.TextType.MARKDOWN) {
            comment = StringUtils.convertToMarkdown(comment);
        }
        List<? extends IComment> comments = getComments(ticketKey, null);
        if (comments != null) {
            for (IComment commentObject : comments) {
                if (comment.equalsIgnoreCase(commentObject.getBody())) {
                    return;
                }
            }
        }
        GenericRequest commentPostRequest = comment(ticketKey, null);
        commentPostRequest.setBody(new JSONObject().put("body", comment).toString()).post();
        clearCache(commentPostRequest);
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    @MCPTool(
            name = "jira_get_comments",
            description = "Get all comments for a specific Jira ticket",
            integration = "jira",
            category = "comment_management"
    )
    public List<? extends IComment> getComments(@MCPParam(name = "key", description = "The Jira ticket key to get comments for", required = true) String key, 
                                               @MCPParam(name = "ticket", description = "Optional ticket object for cache validation", required = false) ITicket ticket) throws IOException {
        return new CommentsResult(comment(key, ticket).execute()).getComments();
    }

    public void clearCache(GenericRequest jiraRequest) throws IOException {
        File cachedFile = getCachedFile(jiraRequest);
        if (cachedFile.exists()) {
            cachedFile.delete();
        }
    }

    @NotNull
    public File getCachedFile(GenericRequest jiraRequest) throws IOException {
        String url = jiraRequest.url();
        return getCachedFile(url);
    }

    @NotNull
    public File getCachedFile(String url) throws IOException {
        if (url.contains("content")) {
            Attachment attachment = new Attachment(new GenericRequest(this, url.replaceAll("content/", "")).execute());
            String[] split = url.split("/");
            return new File(getCacheFolderName() + "/" + split[split.length-2] + "_" + split[split.length-1] + "_" + attachment.getName());
        } else {
            String value = DigestUtils.md5Hex(url);
            String imageExtension = Impl.getFileImageExtension(url);
            return new File(getCacheFolderName() + "/" + value + imageExtension);
        }
    }

    @Override
    @MCPTool(
            name = "jira_post_comment",
            description = "Post a comment to a Jira ticket",
            integration = "jira",
            category = "comment_management"
    )
    public void postComment(@MCPParam(name = "ticketKey", description = "The Jira ticket key to post comment to", required = true) String ticketKey, 
                          @MCPParam(name = "comment", description = "The comment text to post", required = true) String comment) throws IOException {
        if (getTextType() == TrackerClient.TextType.MARKDOWN) {
            comment = StringUtils.convertToMarkdown(comment);
        }
        GenericRequest commentPostRequest = comment(ticketKey, null);
        commentPostRequest.setBody(new JSONObject().put("body", comment).toString()).post();
        clearCache(commentPostRequest);
    }

    public void deleteRemoteLink(final String ticket, final String globalId) throws IOException {
        new GenericRequest(this, path("issue/" + ticket + "/remotelink?globalId=" + URLEncoder.encode(globalId))).delete();
    }

    @Override
    @MCPTool(
            name = "jira_get_fix_versions",
            description = "Get all fix versions for a specific Jira project",
            integration = "jira",
            category = "project_management"
    )
    public List<? extends ReportIteration> getFixVersions(@MCPParam(name = "project", description = "The Jira project key to get fix versions for", required = true) final String project) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("project/" + project + "/versions"));
        //genericRequest.setIgnoreCache(true);
        return JSONModel.convertToModels(FixVersion.class, new JSONArray(genericRequest.execute()));
    }

    public String createFixVersion(final String project, String fixVersion, Date startDate, Date endDate) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, basePath + "/rest/api/2/project/" + project + "/version");
        genericRequest.setBody(new JSONObject()
                .put("name", fixVersion)
                        .put("startDate", DateUtils.formatToJiraDate(startDate.getTime()))
                .put("releaseDate", DateUtils.formatToJiraDate(endDate.getTime()))
                .toString());
        return post(genericRequest);
    }

    @MCPTool(
            name = "jira_get_components",
            description = "Get all components for a specific Jira project",
            integration = "jira",
            category = "project_management"
    )
    public List<Component> getComponents(@MCPParam(name = "project", description = "The Jira project key to get components for", required = true) final String project) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("project/" + project + "/components"));
        genericRequest.setIgnoreCache(true);
        return JSONModel.convertToModels(Component.class, new JSONArray(genericRequest.execute()));
    }

    public String updateFixVersion(final FixVersion fixVersion) throws IOException {
        return fixVersion(fixVersion).setBody(fixVersion.getJSONObject().toString()).put();
    }

    public GenericRequest fixVersion(final FixVersion fixVersion) {
        return new GenericRequest(this, path("version/" + fixVersion.getIdAsString()));
    }

    public String moveFixVersion(final FixVersion fixVersion, final FixVersion afterFixVersion) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("version/" + fixVersion.getId() + "/move"));
        genericRequest.setBody(new JSONObject().put("after", "/rest/api/latest/version/"+afterFixVersion.getId()).toString());
        return genericRequest.post();
    }

    public Status findStatus(final String project, final String type, final String statusName) throws IOException {
        List<ProjectStatus> projectStatuses = getStatuses(project);
        for (ProjectStatus projectStatus : projectStatuses) {
            String name = projectStatus.getName();
            if (name.equalsIgnoreCase(type)) {
                List<Status> statuses = projectStatus.getStatuses();
                for (Status status : statuses) {
                    if (status.getName().equalsIgnoreCase(statusName)) {
                        return status;
                    }
                }
            }
        }
        return null;
    }

    @MCPTool(
            name = "jira_get_project_statuses",
            description = "Get all statuses for a specific Jira project",
            integration = "jira",
            category = "project_management"
    )
    public List<ProjectStatus> getStatuses(@MCPParam(name = "project", description = "The Jira project key to get statuses for", required = true) final String project) throws IOException {
        return JSONModel.convertToModels(ProjectStatus.class, new JSONArray(new GenericRequest(this, path("project/" + project + "/statuses")).execute()));
    }

    public ReportIteration findVersion(final String fixVersion, String project) throws IOException {
        List<? extends ReportIteration> fixVersions = getFixVersions(project);
        for (ReportIteration version : fixVersions) {
            if (version.getIterationName().equalsIgnoreCase(fixVersion)) {
                return version;
            }
        }
        return null;
    }

    public GenericRequest createTicket() {
        return new GenericRequest(this, path("issue"));
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        GenericRequest jiraRequest = createTicket();

        JSONObject jsonObject = new JSONObject();
        Fields fields = new Fields();
        fields.set("project", new JSONObject().put("key", project));
        fields.set("summary", summary);

        fields.set("description", description);

        IssueType value = new IssueType();
        value.set("name", issueType);
        fields.set(Fields.ISSUETYPE, value.getJSONObject());

        if (fieldsInitializer != null) {
            fieldsInitializer.init(fields);
        }

        jsonObject.put("fields", fields.getJSONObject());

        jiraRequest.setBody(jsonObject.toString());
        String post = jiraRequest.post();
        String key = new JSONObject(post).getString("key");
        log(getTicketBrowseUrl(key));
        return post;
    }

    @MCPTool(
            name = "jira_create_epic_or_find",
            description = "Create a new epic in Jira or find existing one by summary",
            integration = "jira",
            category = "ticket_management"
    )
    public String createEpicOrFind(@MCPParam(name = "project", description = "The Jira project key to create epic in", required = true) String project, 
                                 @MCPParam(name = "summary", description = "The epic summary/title", required = true) String summary, 
                                 @MCPParam(name = "description", description = "The epic description", required = true) String description) throws IOException {
        return createEpicOrFind(project, summary, description, null);
    }

    public String createTicketInEpic(String project, Ticket ticket, String issueType, FieldsInitializer fieldsInitializer) throws IOException {
        String summary = ticket.getFields().getSummary();
        String description = "<h3>Please, see <a class=\"external-link\" href=\"" + getTicketBrowseUrl(ticket.getKey()) + "\" rel=\"nofollow\">description in epic</a></h3>\n";
        return createTicketInProject(project, issueType, summary, description, new FieldsInitializer() {

            @Override
            public void init(TrackerTicketFields fields) {
                ((Fields)fields).set(getEpic(), ticket.getKey());
                if (fieldsInitializer != null) {
                    fieldsInitializer.init(fields);
                }
            }
        });
    }

    public String createEpicOrFind(String project, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        summary = summary.replaceAll("\\\\", "/").replaceAll("'", "");
        SearchResult search = searchByEpicName(project, summary);
        if (search.getTotal() > 0) {
            return search.getIssues().get(0).getKey();
        }

        String finalSummary = summary;
        return createTicketInProject(project, "Epic", summary, description, new FieldsInitializer() {
            @Override
            public void init(TrackerTicketFields fields) {
                fields.set(getEpicName(), finalSummary);
                if (fieldsInitializer != null) {
                    fieldsInitializer.init(fields);
                }
            }
        });
    }

    private SearchResult searchByEpicName(String project, String summary) throws IOException {
        try {
            return search("project = " + project + " and " + getEpicNameCf() + " = '" + StringEscapeUtils.escapeHtml4(summary) + "' and issueType = epic", 0, new String[]{Fields.SUMMARY});
        } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase(RestClientException.NO_SUCH_PARENT_EPICS)) {
                return new SearchResult();
            } else {
                throw e;
            }
        }
    }

    protected String getEpicNameCf() {
        throw new UnsupportedOperationException("please return epic field");
    }

    protected String getEpicName() {
        throw new UnsupportedOperationException("please return epic name field");
    }

    protected String getEpic() {
        throw new UnsupportedOperationException("please return epic field");
    }

    protected String getIssuesInEpicJql(String key, String projects) {
        return getEpicNameCf() + "=" + key + " and project in (" + projects + ") and type not in (Test, \"Internal Defect\", \"Automation Task\", \"Integration Defect\")";
    }

    public void issuesInEpic(String key, String projects, Performer<T> performer, String... fields) throws Exception {
        String jql = getIssuesInEpicJql(key, projects);
        searchAndPerform(performer, jql, fields);
    }

    public Map<String, ITicket> issuesInEpicMap(String key, String projects, String... fields) throws Exception {
        String jql = getIssuesInEpicJql(key, projects);
        return getAllTicketsByJQL(jql, fields);
    }

    @MCPTool(
            name = "jira_get_issues_in_epic_by_type",
            description = "Get all issues in an epic filtered by issue type",
            integration = "jira",
            category = "ticket_management"
    )
    public List<Ticket> issuesInEpicByType(@MCPParam(name = "key", description = "The epic key to get issues from", required = true) String key, 
                                          @MCPParam(name = "type", description = "The issue type to filter by", required = true) String type, 
                                          @MCPParam(name = "fields", description = "Optional array of fields to include", required = false) String... fields) throws Exception {
        List<Ticket> tickets = new ArrayList<>();
        issuesInEpicByType(key, ticket -> {
            tickets.add(ticket);
            return false;
        }, type, fields);
        return tickets;
    }

    public void issuesInEpicByType(String key, Performer<T> performer, String type, String... fields) throws Exception {
        String jql = getEpicNameCf() + "=" + key + " and type in (" + type + ")";
        searchAndPerform(performer, jql, fields);
    }

    public Map<String,ITicket> getAllTicketsByJQL(String query, String[] fields) throws Exception {
        Map<String, ITicket> result = new HashMap<>();

        // find and create all required Tickets
        searchAndPerform((Performer) ticket -> {
            result.put(ticket.getTicketKey(), ticket);
            return false;
        }, query, fields);

        return result;
    }

    @Override
    @MCPTool(
            name = "jira_update_description",
            description = "Update the description of a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateDescription(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key, 
                                  @MCPParam(name = "description", description = "The new description text", required = true) String description) throws IOException {
        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        body.put("update", new JSONObject()
                .put(Fields.DESCRIPTION, new JSONArray()
                        .put(new JSONObject()
                                .put("set", description)
                        )));
        jiraRequest.setBody(body.toString());
        String updateResult = jiraRequest.put();
        log(updateResult);
        return updateResult;
    }

    public String updateTicket2(String key, FieldsInitializer fieldsInitializer) throws IOException {
        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        JSONObject fields = new JSONObject();
        if (fieldsInitializer != null) {
            fieldsInitializer.init(new TrackerTicketFields() {
                @Override
                public void set(String key, Object object) {
                    fields.put(key, object);
                }
            });
        }
        body.put("fields", fields);
        jiraRequest.setBody(body.toString());
        String updateResult = jiraRequest.put();
        log(updateResult);
        return updateResult;
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        JSONObject fields = new JSONObject();
        if (fieldsInitializer != null) {
            fieldsInitializer.init(new TrackerTicketFields() {
                @Override
                public void set(String key, Object object) {
                    JSONObject jsonObject = null;
                    if (object instanceof JSONObject) {
//                        JSONObject objectToSet = (JSONObject) object;
                        JSONObject objectToSet = new JSONObject().put("key", ((JSONObject) object).getString("key"));
                        jsonObject = new JSONObject()
                                .put("set", objectToSet);
                    } else {
                        jsonObject = new JSONObject()
                                .put("set", object);
                    }
                    fields.put(key, new JSONArray()
                            .put(jsonObject
                            ));
                }
            });
        }
        body.put("update", fields);
        jiraRequest.setBody(body.toString());
        String updateResult = jiraRequest.put();
        log(updateResult);
        return updateResult;
    }

    @MCPTool(
            name = "jira_update_field",
            description = "Update a specific field of a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateField(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key, 
                            @MCPParam(name = "field", description = "The field name to update", required = true) String field, 
                            @MCPParam(name = "value", description = "The new value for the field", required = true) Object value) throws IOException {
        if ("".equals(value)) {
            return clearField(key, field);
        }
        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        body.put("update", new JSONObject()
                .put(field, new JSONArray()
                        .put(new JSONObject()
                                .put("set", value)
                        )));
        jiraRequest.setBody(body.toString());
        String updateResult = jiraRequest.put();
        log("params " + key + " " + field + " " + value + " " + updateResult);

        return updateResult;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", authType + " " + authorization)
                .header("X-Atlassian-Token", "nocheck")
                .header("Content-Type", "application/json");
    }

    @Override
    public String path(String path) {
        return basePath + "/rest/api/latest/" + path;
    }

    @Override
    public String execute(GenericRequest jiraRequest) throws IOException {
        String url = jiraRequest.url();
        try {
            if (!isReadCacheGetRequestsEnabled) {
                clearRequestIfExpired(jiraRequest, instanceCreationTime);
            }
            return execute(url, true, jiraRequest.isIgnoreCache());
        } catch (AtlassianRestClient.RestClientException e) {
            String body = e.getBody();
            if (body != null && body.contains("does not exist for field 'key'")) {
                Pattern pattern = Pattern.compile("'(\\w+-\\d+)'");  // matching pattern for key value
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    String key = matcher.group(1);  // extract the key value from the 1st capturing group
                    return execute(url.replaceAll(key + "%2C", "").replaceAll("%2C"+ key, "").replaceAll(key, ""), true, jiraRequest.isIgnoreCache());
                }
            }
            throw e;
        }
    }


    @Override
    public String buildUrlToSearch(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            return getBasePath() + "/issues/?jql=" + encodedQuery;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private HashMap<String, Long> timeMeasurement = new HashMap<>();


    @Override
    @MCPTool(
            name = "jira_execute_request",
            description = "Execute a custom HTTP request to Jira API",
            integration = "jira",
            category = "api_operations"
    )
    public String execute(@MCPParam(name = "url", description = "The Jira API URL to execute", required = true) String url) throws IOException {
        return execute(url, true, false);
    }

    @Override
    public void attachFileToTicket(String ticketKey, String name, String contentType, File file) throws IOException {
        if (contentType == null) {
            contentType = "image/*";
        }
        String[] fields = {Fields.ATTACHMENT, Fields.SUMMARY};
        clearCache(createPerformTicketRequest(ticketKey, fields));
        T t = performTicket(ticketKey, fields);
        List<? extends IAttachment> attachments = t.getAttachments();
        for (IAttachment attachment : attachments) {
            if (attachment.getName().equalsIgnoreCase(name)) {
                return;
            }
        }

        String url = path("issue/" + ticketKey + "/attachments");
        // Prepare the file part
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", name,
                        okhttp3.RequestBody.Companion.create(file, MediaType.parse(contentType))
                ).build();

        // Create the request
        Request request = sign(new Request.Builder()
                .url(url)
                .post(requestBody)
        )
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            logger.info(response.body().string());
            clearCache(createPerformTicketRequest(ticketKey, fields));
        }
    }

    private String execute(String url, boolean isRepeatIfFails, boolean isIgnoreCache) throws IOException {
        try {
            timeMeasurement.put(url, System.currentTimeMillis());

            if (!isIgnoreCache) {
                String value = DigestUtils.md5Hex(url);
                File cache = new File(getCacheFolderName());
                cache.mkdirs();
                File cachedFile = new File(getCacheFolderName() + "/" + value);
                if (cachedFile.exists()) {
                    return FileUtils.readFileToString(cachedFile);
                }
            }
            if (isWaitBeforePerform) {
                try {
                    Thread.currentThread().sleep(sleepTimeRequest);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                Request request = sign(new Request.Builder())
                        .url(url)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String result = response.body() != null ? response.body().string() : null;
                        String value = DigestUtils.md5Hex(url);
                        File cache = new File(getCacheFolderName());
                        cache.mkdirs();
                        File cachedFile = new File(getCacheFolderName() + "/" + value);
                        FileUtils.writeStringToFile(cachedFile, result);
                        return result;
                    } else {
                        throw AtlassianRestClient.printAndCreateException(request, response);
                    }
                }
            } catch (SocketTimeoutException | ConnectException e) {
                log(url);
                if (isRepeatIfFails) {
                    if (isWaitBeforePerform) {
                        try {
                            Thread.currentThread().sleep(sleepTimeRequest);
                        } catch (InterruptedException socketException) {
                            socketException.printStackTrace();
                        }
                    }
                    return execute(url, false, isIgnoreCache);
                } else {
                    throw e;
                }
            }
        } finally {
            Long prevTime = timeMeasurement.get(url);
            long time = System.currentTimeMillis() - 200 - prevTime;
            log(time + " " + url);
            closeAllConnections();
        }
    }

    private void closeAllConnections() {
        //client.connectionPool().evictAll();
    }

    public String getCacheFolderName() {
        return cacheFolderName;
    }

    private void setCacheFolderNameAndReinit(String cacheFolderName) throws IOException {
        this.cacheFolderName = cacheFolderName;
        initCache();
    }

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    @Override
    public String post(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        try (Response response = client.newCall(sign(
                new Request.Builder())
                .url(url)
                .post(body)
                .build()
        ).execute()) {
            if (response.isSuccessful()) {
                logger.info("Request performed successfully!");
                return response.body() != null ? response.body().string() : null;
            } else {
                int code = response.code();
                logger.info("Error creating fix version. Response code: {}", code);
                ResponseBody responseBody = response.body();
                String responseBodyAsString = responseBody != null ? responseBody.string() : "";
                if (responseBody != null) {
                    logger.error("Response body: {}", responseBodyAsString);
                }
                return responseBodyAsString;
            }
        } finally {
            closeAllConnections();
        }
    }

    @Override
    public String patch(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        try (Response response = client.newCall(sign(
                new Request.Builder())
                .url(url)
                .patch(body)
                .build()
        ).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : null;
            } else {
                logger.error("Error code {} {} {}", response.code(), genericRequest.getBody(), genericRequest.url());
                return response.body() != null ? response.body().string() : null;
            }
        } finally {
            closeAllConnections();
        }
    }

    @Override
    public String put(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        try (Response response = client.newCall(sign(
                new Request.Builder())
                .url(url)
                .put(body)
                .build()
        ).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : null;
            } else {
                logger.error("Error code {} {} {}", response.code(), genericRequest.getBody(), genericRequest.url());
                return response.body() != null ? response.body().string() : null;
            }
        } finally {
            closeAllConnections();
        }
    }

    @Override
    public String delete(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        String jiraRequestBody = genericRequest.getBody();
        RequestBody body = null;
        if (jiraRequestBody != null) {
            body = RequestBody.create(JSON, jiraRequestBody);
        }
        try (Response response = client.newCall(sign(
                new Request.Builder())
                .url(url)
                .delete(body)
                .build()
        ).execute()) {
            return response.body() != null ? response.body().string() : null;
        } finally {
            closeAllConnections();
        }
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    public GenericRequest transitions(String ticket) {
        return new GenericRequest(this, path("issue/" + ticket + "/transitions?expand=transitions.fields"));
    }

    @MCPTool(
            name = "jira_get_transitions",
            description = "Get all available transitions for a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public List<Transition> getTransitions(@MCPParam(name = "ticket", description = "The Jira ticket key to get transitions for", required = true) String ticket) throws IOException {
        return new TransitionsResult(transitions(ticket).execute()).getTransitions();
    }

    @Override
    @MCPTool(
            name = "jira_move_to_status",
            description = "Move a Jira ticket to a specific status",
            integration = "jira",
            category = "ticket_management"
    )
    public String moveToStatus(@MCPParam(name = "ticketKey", description = "The Jira ticket key to move", required = true) String ticketKey, 
                             @MCPParam(name = "statusName", description = "The target status name", required = true) String statusName) throws IOException {
        List<Transition> transitions = getTransitions(ticketKey);
        if (transitions != null) {
            for (Transition transition : transitions) {
                if (transition.getValue().equalsIgnoreCase(statusName)) {
                    return moveToTransitionId(ticketKey, transition.getId());
                }
            }
        }
        return null;
    }

    @MCPTool(
            name = "jira_move_to_status_with_resolution",
            description = "Move a Jira ticket to a specific status with resolution",
            integration = "jira",
            category = "ticket_management"
    )
    public String moveToStatus(@MCPParam(name = "ticket", description = "The Jira ticket key to move", required = true) String ticket, 
                             @MCPParam(name = "statusName", description = "The target status name", required = true) String statusName, 
                             @MCPParam(name = "resolution", description = "The resolution to set", required = true) String resolution) throws IOException {
        List<Transition> transitions = getTransitions(ticket);
        if (transitions != null) {
            for (Transition transition : transitions) {
                if (transition.getValue().equalsIgnoreCase(statusName)) {
                    return moveToTransitionId(ticket, transition.getId(), resolution);
                }
            }
        }
        return null;
    }

    @MCPTool(
            name = "jira_clear_field",
            description = "Clear a specific field value in a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String clearField(@MCPParam(name = "ticket", description = "The Jira ticket key to clear field from", required = true) String ticket, 
                           @MCPParam(name = "field", description = "The field name to clear", required = true) String field) throws IOException {
        GenericRequest request = getTicket(ticket);
        JSONObject clearedFieldJSON = new JSONObject().put(field,
                JSONObject.NULL
        );
        request.setBody(new JSONObject()
                .put("fields",
                        clearedFieldJSON)
                .toString());
        String postResult = request.put();
        clearCache(getTicket(ticket));
        return postResult;
    }

    public String moveToTransitionId(String ticket, String transitionId) throws IOException {
        GenericRequest request = transitions(ticket);
        request.setBody(new JSONObject()
                .put("transition",
                        new JSONObject().put("id", transitionId)
                )
                .toString());
        String postResult = request.post();
        clearCache(transitions(ticket));
        return postResult;
    }

    @MCPTool(
            name = "jira_set_fix_version",
            description = "Set the fix version for a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String setTicketFixVersion(@MCPParam(name = "ticket", description = "The Jira ticket key to set fix version for", required = true) String ticket, 
                                    @MCPParam(name = "fixVersion", description = "The fix version name to set", required = true) String fixVersion) throws IOException {
        GenericRequest request = getTicket(ticket);
        JSONObject jsonObject;
        jsonObject = new JSONObject()
                .put("update",
                        new JSONObject().put("fixVersions",
                                new JSONArray().put(
                                        new JSONObject()
                                                .put("set", new JSONArray().put(new JSONObject().put("name", fixVersion)))
                                )
                        )
                );
        request.setBody(jsonObject
                .toString());
        return request.put();
    }

    @MCPTool(
            name = "jira_add_fix_version",
            description = "Add a fix version to a Jira ticket (without removing existing ones)",
            integration = "jira",
            category = "ticket_management"
    )
    public String addTicketFixVersion(@MCPParam(name = "ticket", description = "The Jira ticket key to add fix version to", required = true) String ticket, 
                                    @MCPParam(name = "fixVersion", description = "The fix version name to add", required = true) String fixVersion) throws IOException {
        GenericRequest request = getTicket(ticket);
        JSONObject jsonObject;
        jsonObject = new JSONObject()
                .put("update",
                        new JSONObject().put("fixVersions",
                                new JSONArray().put(
                                        new JSONObject()
                                                .put("add", new JSONObject().put("name", fixVersion))
                                )
                        )
                );
        request.setBody(jsonObject
                .toString());
        return request.put();
    }

    @MCPTool(
            name = "jira_set_priority",
            description = "Set the priority for a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String setTicketPriority(@MCPParam(name = "ticket", description = "The Jira ticket key to set priority for", required = true) String ticket, 
                                  @MCPParam(name = "priority", description = "The priority name to set", required = true) String priority) throws IOException {
        GenericRequest request = getTicket(ticket);
        JSONObject jsonObject;
        jsonObject = new JSONObject()
                .put("update",
                        new JSONObject().put("priority",
                                new JSONArray().put(new JSONObject().put("set", new JSONObject().put("name", priority)))
                        )
                );
        request.setBody(jsonObject
                .toString());
        return request.put();
    }

    @MCPTool(
            name = "jira_remove_fix_version",
            description = "Remove a fix version from a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String removeTicketFixVersion(@MCPParam(name = "ticket", description = "The Jira ticket key to remove fix version from", required = true) String ticket, 
                                       @MCPParam(name = "fixVersion", description = "The fix version name to remove", required = true) String fixVersion) throws IOException {
        GenericRequest request = getTicket(ticket);
        JSONObject jsonObject = new JSONObject()
                .put("update",
                        new JSONObject().put("fixVersions",
                                new JSONArray().put(
                                        new JSONObject()
                                                .put("remove", new JSONObject().put("name", fixVersion))
                                )
                        )
                );
        request.setBody(jsonObject
                .toString());
        return request.put();
    }

    public String moveToTransitionId(String ticket, String transition, String resolution) throws IOException {
        GenericRequest request = transitions(ticket);
        JSONObject jsonObject = new JSONObject()
                .put("transition",
                        new JSONObject().put("id", transition)
                )
                .put("fields",
                        new JSONObject().put("resolution", new JSONObject().put("name", resolution)));
        request.setBody(jsonObject
                .toString());
        return request.post();
    }

    public static String buildJQL(Collection<String> keys) {
        StringBuilder jql = new StringBuilder("key in (");
        boolean isFirst = true;
        for (String key : keys) {
            if (isFirst) {
                isFirst = false;
            } else {
                jql.append(",");
            }
            jql.append(key);
        }
        jql.append(")");
        return jql.toString();
    }

    public static String buildNotInJQLByKeys(Collection<? extends Key> keys) {
        return buildJQLByKeys(keys).replace("key in", "key not in");
    }

    public static String buildJQLByKeys(Collection<? extends Key> keys) {
        if (keys.isEmpty()) {
            return "";
        }
        StringBuilder jql = new StringBuilder("key in (");
        boolean isFirst = true;
        for (Key key : keys) {
            if (isFirst) {
                isFirst = false;
            } else {
                jql.append(",");
            }
            jql.append(key.getKey());
        }
        jql.append(")");
        return jql.toString();
    }

    public static String buildJQLNotInProjects(Collection<? extends Key> keys) {
        if (keys.isEmpty()) {
            return "";
        }
        StringBuilder jql = new StringBuilder("project not in (");
        boolean isFirst = true;
        for (Key key : keys) {
            if (isFirst) {
                isFirst = false;
            } else {
                jql.append(",");
            }
            jql.append(key.getKey().split("-")[0]);
        }
        jql.append(")");
        return jql.toString();
    }

    public static String buildJQLUrl(String basePath, Collection<? extends Key> keys) {
        StringBuilder jqlBuilder = new StringBuilder("key in (");
        boolean isFirst = true;
        for (Key key : keys) {
            if (isFirst) {
                isFirst = false;
            } else {
                jqlBuilder.append(",");
            }
            jqlBuilder.append(key.getKey());
        }
        jqlBuilder.append(")");
        String jql = jqlBuilder.toString();
        return buildJQLUrl(basePath, jql);
    }

    public String buildJQLUrl(String jql) {
        return buildJQLUrl(basePath, jql);
    }

    public static String buildJQLUrl(String basePath, String jql) {
        StringBuilder url = new StringBuilder(basePath + "/issues/?jql=");
        try {
            return url.append(URLEncoder.encode(jql, "UTF-8")).toString();
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static String tag(String basePath, String notifierId, String notifierName) {
        return "<a class=\"user-hover\" href=\"" + basePath + "/secure/ViewProfile.jspa?name="+notifierId+"\" rel=\""+notifierId+"\">"+notifierName+"</a>";
    }

    public static String parseNotifierId(String taggedString) {
        if (taggedString == null || taggedString.isEmpty()) {
            return "";
        }

        try {
            // Pattern for extracting everything after ~accountid: and before ]
            Pattern pattern = Pattern.compile("\\[~accountid:([^\\]]+)\\]");
            Matcher matcher = pattern.matcher(taggedString);

            if (matcher.find()) {
                return matcher.group(1); // Returns everything between ~accountid: and ]
            }
        } catch (Exception e) {
            // Log error if needed
            return "";
        }

        return "";
    }

    public String tag(String notifierId) {
        if (notifierId == null) {
            return "";
        }
        if (notifierId.contains("~")) {
            return "[" + notifierId + "]";
        }
        return "[~accountid:" + notifierId + "]";
    }

    public boolean isWaitBeforePerform() {
        return isWaitBeforePerform;
    }

    public void setWaitBeforePerform(boolean waitBeforePerform) {
        isWaitBeforePerform = waitBeforePerform;
    }

    @Override
    public String getDefaultStatusField() {
        return "status";
    }

    @Override
    public boolean isValidImageUrl(String url) throws IOException {
        return url.startsWith(getBasePath()) && ((url.endsWith("png") || url.endsWith("jpg") || url.endsWith("jpeg")) || isImageAttachment(url));
    }

    public boolean isImageAttachment(String attachmentUrl) throws IOException {
        Request request = sign(new Request.Builder()
                .url(attachmentUrl)).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            // Get the content type from the HTTP header
            String contentType = response.header("Content-Type");
            // Immediately close the body since we're only interested in headers
            response.body().close();
            // Check if the content type indicates an image
            return contentType != null && contentType.startsWith("image/");
        }
    }

    @Override
    public File convertUrlToFile(String href) throws IOException {
        return Impl.downloadFile(this, new GenericRequest(this, href), getCachedFile(href));
    }

    @Override
    public OkHttpClient getClient() {
        return client;
    }

    @MCPTool(
            name = "jira_get_fields",
            description = "Get all available fields for a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public String getFields(@MCPParam(name = "project", description = "The Jira project key to get fields for", required = true) String project) throws IOException {
        try {
            GenericRequest genericRequest = new GenericRequest(this, path("issue/createmeta?projectKeys=" + project + "&expand=projects.issuetypes.fields"));
            return genericRequest.execute();
        } catch (RestClientException e) {
            GenericRequest genericRequest = new GenericRequest(this, path("field"));
            return genericRequest.execute();
        }
    }

    @MCPTool(
            name = "jira_get_field_custom_code",
            description = "Get the custom field code for a field name in a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public String getFieldCustomCode(@MCPParam(name = "project", description = "The Jira project key", required = true) String project, 
                                   @MCPParam(name = "fieldName", description = "The human-readable field name", required = true) String fieldName) throws IOException {
        String response = getFields(project);
        try {
            return parseCloudJiraResponse(fieldName, response);
        } catch (JSONException e) {
            return parseServerJiraResponse(fieldName, response);
        }
    }

    public String parseServerJiraResponse(String fieldName, String jsonResponse) {
        try {
            JSONArray fields = new JSONArray(jsonResponse);

            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (field.getString("name").equalsIgnoreCase(fieldName)) {
                    return field.getString("id");
                }
            }

            // If not found, return null or throw exception
            return null;

        } catch (JSONException e) {
            throw new RuntimeException("Error parsing JSON response: " + e.getMessage());
        }
    }

    @Nullable
    private static String parseCloudJiraResponse(String fieldName, String response) {
        JSONArray issueTypesWithFields = new JSONObject(response).getJSONArray("projects").getJSONObject(0).getJSONArray("issuetypes");
        for (int i = 0; i < issueTypesWithFields.length(); i++) {
            JSONObject issueTypeFields = issueTypesWithFields.getJSONObject(i);
            JSONObject fieldsJSONObject = issueTypeFields.getJSONObject("fields");
            Set<String> keys = fieldsJSONObject.keySet();
            for (String key : keys) {
                String humanNameOfField = fieldsJSONObject.getJSONObject(key).getString("name");
                if (humanNameOfField.equalsIgnoreCase(fieldName)) {
                    return key;
                }
            }
        }
        return null;
    }

    public Pair<String,IssueType> getRelationshipByName(String name) throws IOException {
        List<IssueType> relationships = getRelationships();
        for (IssueType issueType : relationships) {
            if (name.equalsIgnoreCase(issueType.getName())) {
                return new Pair<>("inward", issueType) ;
            } else if (name.equalsIgnoreCase(issueType.getString("inward"))) {
                return new Pair<>("inward", issueType) ;
            } else if (name.equalsIgnoreCase(issueType.getString("outward"))) {
                return new Pair<>("outward", issueType) ;
            }
        }
        return null;
    }

    @MCPTool(
            name = "jira_get_issue_link_types",
            description = "Get all available issue link types/relationships in Jira",
            integration = "jira",
            category = "ticket_management"
    )
    public List<IssueType> getRelationships() throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, path("issueLinkType"));
        genericRequest.setIgnoreCache(true);
        return JSONModel.convertToModels(IssueType.class, new JSONObject(genericRequest.execute()).getJSONArray("issueLinkTypes"));
    }

    @Override
    @MCPTool(
            name = "jira_link_issues",
            description = "Link two Jira issues with a specific relationship type",
            integration = "jira",
            category = "ticket_management"
    )
    public String linkIssueWithRelationship(@MCPParam(name = "sourceKey", description = "The source issue key", required = true) String sourceKey, 
                                          @MCPParam(name = "anotherKey", description = "The target issue key", required = true) String anotherKey, 
                                          @MCPParam(name = "relationship", description = "The relationship type name", required = true) String relationship) throws IOException {
        Pair<String, IssueType> relationshipByNameIssueType = getRelationshipByName(relationship);
        GenericRequest jiraRequest = new GenericRequest(this, path("issueLink"));
        JSONObject body = new JSONObject();

        String type = relationshipByNameIssueType.getFirst();
        body.put("type", new JSONObject().put("name", relationshipByNameIssueType.getSecond().getName()));
        if (type.equalsIgnoreCase("inward")) {
            body.put("outwardIssue", new JSONObject().put("key", sourceKey))
                    .put("inwardIssue", new JSONObject().put("key", anotherKey))
            ;
        } else {
            body.put("inwardIssue", new JSONObject().put("key", sourceKey))
                    .put("outwardIssue", new JSONObject().put("key", anotherKey))
            ;
        }
        jiraRequest.setBody(body.toString());
        return post(jiraRequest);
    }

    @Override
    public Set<String> parseUris(String object) throws Exception {
        Set<String> keys = IssuesIDsParser.extractAllJiraIDs(object);
        if (!keys.isEmpty()) {
            String keysQuery = StringUtils.concatenate(",", keys);
            try {
                extendKeys(keysQuery, keys);
            } catch (RestClientException e) {
                String body = e.getBody();
                Set<String> keysToRemove = IssuesIDsParser.extractAllJiraIDs(body);
                keys.removeAll(keysToRemove);
                if (!keys.isEmpty()) {
                    keysQuery = StringUtils.concatenate(",", keys);
                    try {
                        extendKeys(keysQuery, keys);
                    } catch (Exception ignored) {}
                }
            }
        }
        Set<String> attachmentUrls = IssuesIDsParser.extractAttachmentUrls(getBasePath(), object);
        keys.addAll(attachmentUrls);

        return keys;
    }

    private void extendKeys(String keysQuery, Set<String> keys) throws Exception {
        List<T> childTickets = searchAndPerform("parent in (" + keysQuery + ")", new String[]{"key"});
        for (T child : childTickets) {
            keys.add(child.getKey());
        }
    }

    @Override
    public Object uriToObject(String uri) throws Exception {
        if (uri.startsWith(getBasePath())) {
            return convertUrlToFile(uri);
        } else {
            try {
                T ticket = performTicket(uri, getExtendedQueryFields());
                List<? extends IComment> comments = getComments(ticket.getKey(), ticket);
                ticket.getJSONObject().put("_comments", comments);
                return ticket;
            } catch (Exception ignored) {
                //wrong uri was processed
            }
        }
        return null;
    }
}