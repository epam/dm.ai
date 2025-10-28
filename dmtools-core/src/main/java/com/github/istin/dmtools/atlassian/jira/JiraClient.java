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
import com.github.istin.dmtools.common.utils.CacheManager;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import okhttp3.OkHttpClient.Builder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class JiraClient<T extends Ticket> implements RestClient, TrackerClient<T>, UriToObject {
    public static final String SUCCESS = "Success";
    private final Logger logger;  // Changed from static to instance member
    public static final String PARAM_JQL = "jql";
    public static final String PARAM_MAX_RESULTS = "maxResults";
    public static final String PARAM_FIELDS = "fields";
    public static final String PARAM_START_AT = "startAt";
    public static final String NEXT_PAGE_TOKEN = "nextPageToken";
    
    // Constants
    private static final int UNLIMITED_RESULTS = -1;

    private final OkHttpClient client;
    private final String basePath;
    private boolean isReadCacheGetRequestsEnabled = true;
    @Setter
    @Getter
    private boolean isWaitBeforePerform = false;
    @Setter
    @Getter
    private long sleepTimeRequest;
    private final String authorization;
    @Getter
    private String cacheFolderName;
    private boolean isClearCache = false;
    @Setter
    @Getter
    private String authType = "Basic";
    private final Long instanceCreationTime = System.currentTimeMillis();

    @Setter
    private boolean isLogEnabled = true;
    
    // Cache manager for keys logic only (field mappings, Cloud/Server detection, etc.)
    private final CacheManager cacheManager;
    
    // Field mapping cache TTL in milliseconds (24 hours)
    private static final long FIELD_MAPPING_CACHE_TTL = 24 * 60 * 60 * 1000L;
    
    // Cache for negative field resolution results (fields that don't exist) - 1 hour TTL
    private static final long NEGATIVE_FIELD_CACHE_TTL = 60 * 60 * 1000L;
    
    // Special marker for cached negative results
    private static final String FIELD_NOT_FOUND_MARKER = "__FIELD_NOT_FOUND__";
    private final int maxResults;

    public void setClearCache(boolean clearCache) throws IOException {
        isClearCache = clearCache;
        initCache();
        
        // Also clear the CacheManager memory caches for keys and field resolution caches
        if (clearCache) {
            cacheManager.clearAllMemoryCache();
            log("Cleared all memory caches including field resolution and negative field caches");
        }
    }
    public static String parseJiraProject(String key) {
        return key.split("-")[0].toUpperCase();
    }

    public JiraClient(String basePath, String authorization) throws IOException {
        this(basePath, authorization, LogManager.getLogger(JiraClient.class), -1);
    }

    // Default constructor - backward compatibility
    public JiraClient(String basePath, String authorization, int maxResults) throws IOException {
        this(basePath, authorization, LogManager.getLogger(JiraClient.class), maxResults);
    }

    public JiraClient(String basePath, String authorization, Logger logger) throws IOException {
        this(basePath, authorization, logger, -1);
    }

    // NEW: Constructor with logger injection for server-managed mode
    public JiraClient(String basePath, String authorization, Logger logger, int maxResults) throws IOException {
        this.basePath = basePath;
        this.authorization = authorization;
        this.logger = logger != null ? logger : LogManager.getLogger(JiraClient.class);
        this.maxResults = maxResults;
        Builder builder = new Builder();
        builder.connectTimeout(60, TimeUnit.SECONDS);
        builder.writeTimeout(60, TimeUnit.SECONDS);
        builder.readTimeout(60, TimeUnit.SECONDS);
        this.client = builder.build();

        // Initialize cache manager for keys logic only (memory caching for field mappings, cloud detection, etc.)
        this.cacheManager = new CacheManager(this.logger);
        
        // Initialize GET requests cache with original logic
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

    private void setCacheFolderNameAndReinit(String cacheFolderName) throws IOException {
        this.cacheFolderName = cacheFolderName;
        initCache();
    }

    public String getTicketBrowseUrl(String ticketKey) {
        return basePath + "/browse/" + ticketKey;
    }

    public void setCacheGetRequestsEnabled(boolean cacheGetRequestsEnabled) {
        isReadCacheGetRequestsEnabled = cacheGetRequestsEnabled;
    }

    public void deleteIssueLink(String id) throws IOException {
        new GenericRequest(this, path("issueLink/" + id)).delete();
    }

    @MCPTool(
            name = "jira_delete_ticket",
            description = "Delete a Jira ticket by key",
            integration = "jira",
            category = "ticket_management"
    )
    public String deleteTicket(@MCPParam(name = "key", description = "The Jira ticket key to delete", required = true, example = "PRJ-123") String ticketKey) throws IOException {
        GenericRequest deleteRequest = new GenericRequest(this, path("issue/" + ticketKey));
        String response = deleteRequest.delete();
        log("Ticket deleted: " + ticketKey);
        if (response == null || response.isEmpty()) {
            return SUCCESS;
        }
        return response;
    }

    @MCPTool(
            name = "jira_get_account_by_email",
            description = "Gets account details by email",
            integration = "jira",
            category = "information"
    )
    public Assignee getAccountByEmail(
            @MCPParam(name = "email", description = "The Jira Email", required = true, example = "email@email.com")
            String email
    ) throws IOException {
        GenericRequest jiraRequest = new GenericRequest(this, path("user/search?query=" + email));
        List<Assignee> result = JSONModel.convertToModels(Assignee.class, new JSONArray(jiraRequest.execute()));
        if (!result.isEmpty()) {
            return result.getFirst();
        } else {
            return null;
        }
    }

    @Override
    @MCPTool(
            name = "jira_assign_ticket_to",
            description = "Assigns a Jira ticket to user",
            integration = "jira",
            category = "ticket_management"
    )
    public String assignTo(
            @MCPParam(name = "key", description = "The Jira ticket key to assign", required = true, example = "PRJ-123")
            String ticketKey,
            @MCPParam(name = "accountId", description = "The Jira account ID to assign to. If you know email use first jira_get_account_by_email tools to get account ID", required = true, example = "123457:2a123456-40e8-49d6-8ddc-6852e518451f")
            String accountId
    ) throws IOException {
        GenericRequest jiraRequest = new GenericRequest(this, path("issue/" + ticketKey + "/assignee"));
        jiraRequest.setBody(new JSONObject().put("accountId", accountId).toString());
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

    @MCPTool(
            name = "jira_add_label",
            description = "Adding label to specific ticket key",
            integration = "jira",
            category = "ticket_management"
    )
    public void addLabel(
            @MCPParam(name = "key", description = "The Jira ticket key to assign", required = true, example = "PRJ-123")
            String key,
            @MCPParam(name = "label", description = "The label to be added to ticket", required = true, example = "custom_label")
            String label
    ) throws IOException {
        T ticket = performTicket(key, new String[]{Fields.LABELS});
        addLabelIfNotExists(ticket, label);
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

        @Deprecated
        public abstract boolean perform(Ticket ticket, int index, int start, int end) throws Exception;

        @Override
        public boolean perform(Ticket ticket) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    @MCPTool(
            name = "jira_search_by_jql",
            description = "Search for Jira tickets using JQL and returns all results",
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
        // Resolve field names to support user-friendly custom field names
        String[] resolvedFields = resolveFieldNamesForSearch(searchQueryJQL, fields);
        if (isCloudJira()) {
            SearchResult searchResults = searchByPage(searchQueryJQL, null, resolvedFields);
            if (searchResults == null) {
                logger.error("Received null search results for JQL: {}", searchQueryJQL);
                throw new RestClient.RestClientException("Search returned null results", "null");
            }
            
            JSONArray errorMessages = searchResults.getErrorMessages();
            if (errorMessages != null && errorMessages.length() > 0) {
                logger.error("Search failed with errors: {}", errorMessages);
                throw new RestClient.RestClientException("Search failed: " + errorMessages.toString(), errorMessages.toString());
            }
            
            boolean isBreak = false;
            int ticketIndex = 0;
            while (true) {
                List<Ticket> tickets = searchResults.getIssues();
                if (tickets == null || tickets.isEmpty()) {
                    log("No more tickets to process");
                    break;
                }
                
                for (Ticket ticket : tickets) {
                    if (performer instanceof ProgressPerformer) {
                        isBreak = ((ProgressPerformer) performer).perform(createTicket(ticket), ticketIndex, -1, -1);
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
                if (searchResults.isLast()) {
                    break;
                }
                
                String nextToken = searchResults.getNextPageToken();
                if (nextToken == null || nextToken.isEmpty()) {
                    log("No next page token available, ending pagination");
                    break;
                }
                
                log("current index : " + ticketIndex);
                try {
                    searchResults = searchByPage(searchQueryJQL, nextToken, resolvedFields);
                    if (searchResults == null) {
                        log("Received null search results during pagination, ending");
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error during pagination at token {}: {}", nextToken, e.getMessage());
                    throw new RestClient.RestClientException("Pagination failed: " + e.getMessage(), e.toString());
                }
            }
        } else {
            legacyServerJiraSearch(performer, searchQueryJQL, resolvedFields);
        }
    }

    private void legacyServerJiraSearch(Performer<T> performer, String searchQueryJQL, String[] resolvedFields) throws Exception {
        int startAt = 0;
        SearchResult searchResults = search(searchQueryJQL, startAt, resolvedFields);
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
            searchResults = search(searchQueryJQL, startAt, resolvedFields);
            maxResults = searchResults.getMaxResults();
            total = searchResults.getTotal();
        }
    }

    @MCPTool(
            name = "jira_search_with_pagination",
            description = "[Deprecated] Search for Jira tickets using JQL with pagination support",
            integration = "jira",
            category = "search"
    )
    @Deprecated
    public SearchResult search(
        @MCPParam(name = "jql", description = "JQL query string to search for tickets", required = true, example = "project = PROJ AND status = Open")
        String jql,
        @MCPParam(name = "startAt", description = "Starting index for pagination (0-based)", required = true, example = "0")
        int startAt,
        @MCPParam(name = "fields", description = "Array of field names to include in the response", required = true, example = "['summary', 'status', 'assignee']")
        String[] fields
    ) throws IOException {
        // Resolve field names to support user-friendly custom field names
        String[] resolvedFields = resolveFieldNamesForSearch(jql, fields);
        
        GenericRequest jqlSearchRequest = new GenericRequest(this, path("search")).
                param(PARAM_JQL, jql)
                .param(PARAM_FIELDS, StringUtils.concatenate(",", resolvedFields))
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

    @MCPTool(
            name = "jira_search_by_page",
            description = "Search for Jira tickets using JQL with paging support",
            integration = "jira",
            category = "search"
    )
    public SearchResult searchByPage(
            @MCPParam(name = "jql", description = "JQL query string to search for tickets", required = true, example = "project = PROJ AND status = Open")
            String jql,
            @MCPParam(name = "nextPageToken", description = "Next Page Token from previous response, empty by default for 1 page", required = true, example = "AasvvasasaSASdada")
            String nextPageToken,
            @MCPParam(name = "fields", description = "Array of field names to include in the response", required = true, example = "['summary', 'status', 'assignee']")
            String[] fields
    ) throws IOException {
        // Resolve field names to support user-friendly custom field names
        String[] resolvedFields = resolveFieldNamesForSearch(jql, fields);

        GenericRequest jqlSearchRequest = new GenericRequest(this, path("search/jql")).
                param(PARAM_JQL, jql)
                .param(PARAM_FIELDS, StringUtils.concatenate(",", resolvedFields))
                ;
        if (nextPageToken != null && !nextPageToken.isEmpty()) {
            jqlSearchRequest.param(NEXT_PAGE_TOKEN, nextPageToken);
        }
        if (maxResults != UNLIMITED_RESULTS) {
            jqlSearchRequest.param(PARAM_MAX_RESULTS, String.valueOf(maxResults));
        }

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
    public T performTicket(@MCPParam(name = "key", description = "The Jira ticket key to retrieve", required = true) String ticketKey, 
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
            // Extract project key from ticket key for field resolution
            String projectKey = extractProjectKeyFromTicketKey(ticketKey);
            
            // Resolve field names to support user-friendly custom field names
            String[] resolvedFields = resolveFieldNames(fields, projectKey);
            
            jiraRequest.param("fields", StringUtils.concatenate(",", resolvedFields));
        }
        return jiraRequest;
    }

    public List<RemoteLink> performGettingRemoteLinks(String ticket) throws IOException {
        return JSONModel.convertToModels(RemoteLink.class, new JSONArray(getRemoteLinks(ticket).execute()));
    }

    private boolean subtasksCallIsNotSupported = false;

    @MCPTool(
            name = "jira_get_subtasks",
            description = "Get all subtasks of a specific Jira ticket using jql: parent = PRJ-123 and issueType in (subtask, sub-task, 'sub task')",
            integration = "jira",
            category = "ticket_management"
    )
    public List<T> performGettingSubtask(@MCPParam(name = "key", description = "The parent ticket key to get subtasks for", required = true) String ticketKey) throws Exception {
        if (subtasksCallIsNotSupported) {
            return Collections.emptyList();
        }
        
        // Get subtask issue types for the project
        List<IssueType> issueTypes = getIssueTypes(ticketKey.split("-")[0]);
        List<String> subtaskTypeNames = issueTypes.stream()
                .filter(new Predicate<IssueType>() {
                    @Override
                    public boolean test(IssueType issueType) {
                        String lowerCase = issueType.getName().toLowerCase();
                        return lowerCase.equalsIgnoreCase("subtask")
                                || lowerCase.equalsIgnoreCase("sub task")
                                || lowerCase.equalsIgnoreCase("sub-task");
                    }
                })
                .map(IssueType::getName)
                .toList();

        if (subtaskTypeNames.isEmpty()) {
            subtasksCallIsNotSupported = true;
            log("No subtask types found for project " + ticketKey.split("-")[0] + ", returning empty list");
            return Collections.emptyList();
        }

        // Use different approach based on Jira type (Cloud vs Server)
        if (isCloudJira()) {
            // For Cloud Jira, use JQL parent query directly (more reliable)
            log("Using JQL parent query for Cloud Jira instance");
            try {
                String[] quotedNames = subtaskTypeNames.stream()
                    .map(name -> "'" + name + "'")
                    .toArray(String[]::new);
                String issueTypeList = StringUtils.concatenate(", ", quotedNames);
                String jql = "parent = " + ticketKey + " and issueType in (" + issueTypeList + ")";
                
                return searchAndPerform(jql, getDefaultQueryFields());
            } catch (Exception e) {
                log("JQL parent query failed for Cloud Jira: " + e.getMessage());
                subtasksCallIsNotSupported = true;
                return Collections.emptyList();
            }
        } else {
            // For Server Jira, try the dedicated subtasks API endpoint first
            log("Using dedicated subtasks API endpoint for Server Jira instance");
            GenericRequest subtasks = getSubtasks(ticketKey);
            try {
                return JSONModel.convertToModels(getTicketClass(), new JSONArray(subtasks.execute()));
            } catch (Exception e) {
                log("Dedicated subtasks API failed for Server Jira, falling back to JQL: " + e.getMessage());
                clearCache(subtasks);
                try {
                    // Fallback to JQL query with the actual subtask type names from the project
                    String[] quotedNames = subtaskTypeNames.stream()
                        .map(name -> "'" + name + "'")
                        .toArray(String[]::new);
                    String issueTypeList = StringUtils.concatenate(", ", quotedNames);
                    String jql = "parent = " + ticketKey + " and issueType in (" + issueTypeList + ")";
                    
                    return searchAndPerform(jql, getDefaultQueryFields());
                } catch (Exception ex) {
                    log("JQL fallback also failed for Server Jira: " + ex.getMessage());
                    subtasksCallIsNotSupported = true;
                    return Collections.emptyList();
                }
            }
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
            description = "Post a comment to a Jira ticket only if it doesn't already exist. Supports Jira markup syntax: h2. for headings, *text* for bold, {code}text{code} for inline code, * for bullet lists",
            integration = "jira",
            category = "comment_management"
    )
    public void postCommentIfNotExists(@MCPParam(name = "key", description = "The Jira ticket key to post comment to", required = true) String ticketKey, 
                                     @MCPParam(name = "comment", description = "The comment text to post (supports Jira markup: h2. headings, *bold*, {code}code{code}, * lists)", required = true) String comment) throws IOException {
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
            description = "Post a comment to a Jira ticket. Supports Jira markup syntax: h2. for headings, *text* for bold, {code}text{code} for inline code, * for bullet lists",
            integration = "jira",
            category = "comment_management"
    )
    public void postComment(@MCPParam(name = "key", description = "The Jira ticket key to post comment to", required = true) String ticketKey, 
                          @MCPParam(name = "comment", description = "The comment text to post (supports Jira markup: h2. headings, *bold*, {code}code{code}, * lists)", required = true) String comment) throws IOException {
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

    /**
     * Create a Jira ticket with a parent relationship
     * This method creates a ticket and sets it as a child of the specified parent ticket.
     *
     * @param project The Jira project key
     * @param issueType The type of issue to create (e.g., "Bug", "Story", "Task")
     * @param summary The ticket summary/title
     * @param description The ticket description
     * @param parentKey The key of the parent ticket
     * @return JSON response from Jira API containing the created ticket information
     * @throws IOException if there's an error communicating with Jira
     */
    @MCPTool(
            name = "jira_create_ticket_with_parent",
            description = "Create a new Jira ticket with a parent relationship",
            integration = "jira",
            category = "ticket_management"
    )
    public String createTicketInProjectWithParent(@MCPParam(name = "project", description = "The Jira project key to create the ticket in", required = true) String project,
                                                 @MCPParam(name = "issueType", description = "The type of issue to create (e.g., Bug, Story, Task)", required = true) String issueType,
                                                 @MCPParam(name = "summary", description = "The ticket summary/title", required = true) String summary,
                                                 @MCPParam(name = "description", description = "The ticket description", required = true) String description,
                                                 @MCPParam(name = "parentKey", description = "The key of the parent ticket", required = true) String parentKey) throws IOException {
        T ticket = performTicket(parentKey, new String[]{Fields.SUMMARY});
        return createTicketInProject(project, issueType, summary, description, new TrackerClient.FieldsInitializer() {
            @Override
            public void init(TrackerClient.TrackerTicketFields fields) {
                fields.set("parent", ticket.getJSONObject());
            }
        });
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

    /**
     * Create a Jira ticket with basic fields (MCP-compatible version without FieldsInitializer)
     * This method provides a simplified interface for MCP tools that don't need custom field initialization.
     *
     * @param project The Jira project key
     * @param issueType The type of issue to create (e.g., "Bug", "Story", "Task")
     * @param summary The ticket summary/title
     * @param description The ticket description
     * @return JSON response from Jira API containing the created ticket information
     * @throws IOException if there's an error communicating with Jira
     */
    @MCPTool(
            name = "jira_create_ticket_basic",
            description = "Create a new Jira ticket with basic fields (project, issue type, summary, description)",
            integration = "jira",
            category = "ticket_management"
    )
    public String createTicketInProjectMcp(@MCPParam(name = "project", description = "The Jira project key to create the ticket in (e.g., PROJ)", required = true) String project,
                                         @MCPParam(name = "issueType", description = "The type of issue to create (e.g., Bug, Story, Task)", required = true) String issueType,
                                         @MCPParam(name = "summary", description = "The ticket summary/title (e.g., Fix login issue)", required = true) String summary,
                                         @MCPParam(name = "description", description = "The ticket description. Supports Jira markup syntax: h2. for headings, *text* for bold, {code}text{code} for inline code, * for bullet lists", required = true) String description) throws IOException {
        return createTicketInProject(project, issueType, summary, description, null);
    }

    /**
     * Create a Jira ticket with custom fields via JSONObject
     * This method allows MCP tools to create tickets with custom field configurations using a JSON structure.
     *
     * @param project The Jira project key
     * @param fieldsJson JSONObject containing the ticket fields in Jira format
     * @return JSON response from Jira API containing the created ticket information
     * @throws IOException if there's an error communicating with Jira
     */
    @MCPTool(
            name = "jira_create_ticket_with_json",
            description = "Create a new Jira ticket with custom fields using JSON configuration",
            integration = "jira",
            category = "ticket_management"
    )
    public String createTicketInProjectWithJson(@MCPParam(name = "project", description = "The Jira project key to create the ticket in (e.g., PROJ)", required = true) String project,
                                              @MCPParam(name = "fieldsJson", description = "JSON object containing ticket fields in Jira format (e.g., {\"summary\": \"Ticket Summary\", \"description\": \"Ticket Description\", \"issuetype\": {\"name\": \"Task\"}, \"priority\": {\"name\": \"High\"}}), Supports Jira markup syntax: h2. for headings, *text* for bold, {code}text{code} for inline code, * for bullet lists", required = true) JSONObject fieldsJson) throws IOException {
        GenericRequest jiraRequest = createTicket();

        JSONObject jsonObject = new JSONObject();
        
        // Create the main fields object
        JSONObject fields = new JSONObject();
        
        // Set required project field
        fields.put("project", new JSONObject().put("key", project));
        
        // Copy all fields from the provided JSONObject
        if (fieldsJson != null) {
            for (String key : fieldsJson.keySet()) {
                fields.put(key, fieldsJson.get(key));
            }
        }
        
        jsonObject.put("fields", fields);

        jiraRequest.setBody(jsonObject.toString());
        String post = jiraRequest.post();
        String key = new JSONObject(post).getString("key");
        log(getTicketBrowseUrl(key));
        return post;
    }


    public List<Ticket> issuesInParentByType(String key,
                                          String type,
                                          String... fields) throws Exception {
        List<Ticket> tickets = new ArrayList<>();
        issuesInParentByType(key, ticket -> {
            tickets.add(ticket);
            return false;
        }, type, fields);
        return tickets;
    }

    public void issuesInParentByType(String key, Performer<T> performer, String type, String... fields) throws Exception {
        String jql = "parent = " + key + " and type in (" + type + ")";
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
            description = "Update the description of a Jira ticket. Supports Jira markup syntax: h2. for headings, *text* for bold, {code}text{code} for inline code, * for bullet lists",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateDescription(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key, 
                                  @MCPParam(name = "description", description = "The new description text (supports Jira markup: h2. headings, *bold*, {code}code{code}, * lists)", required = true) String description) throws IOException {
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

    /**
     * Update the parent of a Jira ticket
     * This method can be used for setting up epic relationships and parent-child relationships for subtasks.
     * It allows updating the parent field of any ticket to establish hierarchical relationships.
     *
     * @param key The Jira ticket key to update
     * @param parentKey The key of the new parent ticket
     * @return JSON response from Jira API containing the update result
     * @throws IOException if there's an error communicating with Jira
     */
    @MCPTool(
            name = "jira_update_ticket_parent",
            description = "Update the parent of a Jira ticket. Can be used for setting up epic relationships and parent-child relationships for subtasks",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateTicketParent(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key,
                                   @MCPParam(name = "parentKey", description = "The key of the new parent ticket", required = true) String parentKey) throws IOException {
        // Create the JSON parameters following the standard Jira REST API format
        JSONObject params = new JSONObject();
        JSONObject fields = new JSONObject();
        JSONObject parent = new JSONObject();
        parent.put("key", parentKey);
        fields.put("parent", parent);
        params.put("fields", fields);

        String response = updateTicket(key, params);
        clearCache(getTicket(key));
        return response;
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

    /**
     * Update a Jira ticket using JSON parameters following the standard Jira REST API format
     * This method uses the PUT /rest/api/latest/issue/{issueIdOrKey} endpoint
     * 
     * @param ticketKey The ticket key to update
     * @param params JSONObject containing the update parameters in Jira format
     * @return The response from the update operation
     * @throws IOException if the update fails
     */
    @MCPTool(
            name = "jira_update_ticket",
            description = "Update a Jira ticket using JSON parameters following the standard Jira REST API format",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateTicket(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String ticketKey,
                             @MCPParam(name = "params", description = "JSON object containing update parameters in Jira format (e.g., {\"fields\": {\"summary\": \"New Summary\", \"parent\": {\"key\": \"PROJ-123\"}}})", required = true) JSONObject params) throws IOException {
        GenericRequest jiraRequest = getTicket(ticketKey);
        jiraRequest.setBody(params.toString());
        String updateResult = jiraRequest.put();
        log("Updated ticket " + ticketKey + " with params: " + params.toString());
        return updateResult;
    }

    /**
     * Checks if a field name is already a custom field ID (format: customfield_XXXXX)
     * 
     * @param fieldName The field name to check
     * @return true if it's a custom field ID, false if it's a user-friendly name
     */
    private boolean isCustomFieldId(String fieldName) {
        return fieldName != null && fieldName.matches("^customfield_\\d+$");
    }
    
    /**
     * Gets cached field mapping for a project or loads it if not cached/expired
     * 
     * @param projectKey The project key to get field mappings for
     * @return Map of field name to custom field ID mappings for the project
     * @throws IOException if field loading fails
     */
    private Map<String, String> getFieldMappingForProject(String projectKey) throws IOException {
        String cacheKey = "fieldMapping_" + projectKey;
        
        return cacheManager.getOrComputeWithTTL(cacheKey, () -> {
            log("Loading field mappings for project: " + projectKey);
            Map<String, String> projectFieldMapping = new ConcurrentHashMap<>();
            
            try {
                String response = getFields(projectKey);
                
                // Parse all fields and build mapping
                if (isCloudJira()) {
                    populateFieldMappingFromCloudResponse(projectFieldMapping, response);
                } else {
                    populateFieldMappingFromServerResponse(projectFieldMapping, response);
                }
                
                log("Cached " + projectFieldMapping.size() + " field mappings for project: " + projectKey);
                return projectFieldMapping;
                
            } catch (Exception e) {
                log("Failed to load field mappings for project " + projectKey + ": " + e.getMessage());
                // Return empty mapping to avoid repeated failures
                return new ConcurrentHashMap<>();
            }
        }, FIELD_MAPPING_CACHE_TTL);
    }
    
    /**
     * Populates field mapping from Cloud Jira response
     */
    private void populateFieldMappingFromCloudResponse(Map<String, String> mapping, String response) {
        try {
            JSONArray issueTypesWithFields = new JSONObject(response).getJSONArray("projects").getJSONObject(0).getJSONArray("issuetypes");
            Set<String> processedFields = new HashSet<>();
            
            for (int i = 0; i < issueTypesWithFields.length(); i++) {
                JSONObject issueTypeFields = issueTypesWithFields.getJSONObject(i);
                JSONObject fieldsJSONObject = issueTypeFields.getJSONObject("fields");
                Set<String> keys = fieldsJSONObject.keySet();
                
                for (String key : keys) {
                    if (!processedFields.contains(key)) {
                        try {
                            String humanNameOfField = fieldsJSONObject.getJSONObject(key).getString("name");
                            mapping.put(humanNameOfField.toLowerCase(), key);
                            processedFields.add(key);
                        } catch (JSONException e) {
                            // Skip malformed field entries
                        }
                    }
                }
            }
        } catch (JSONException e) {
            log("Error parsing Cloud Jira field response: " + e.getMessage());
        }
    }
    
    /**
     * Populates field mapping from Server Jira response
     */
    private void populateFieldMappingFromServerResponse(Map<String, String> mapping, String response) {
        try {
            JSONArray fields = new JSONArray(response);
            
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                try {
                    String fieldName = field.getString("name");
                    String fieldId = field.getString("id");
                    mapping.put(fieldName.toLowerCase(), fieldId);
                } catch (JSONException e) {
                    // Skip malformed field entries
                }
            }
        } catch (JSONException e) {
            log("Error parsing Server Jira field response: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a field resolution failure is cached to avoid repeated API calls
     * 
     * @param projectKey The project key
     * @param fieldName The field name
     * @return true if this field is known to not exist
     */
    private boolean isFieldResolutionCachedAsNotFound(String projectKey, String fieldName) {
        String negativeKey = "negativeField_" + projectKey + "_" + fieldName.toLowerCase();
        
        // Use getOrComputeWithTTL with a supplier that returns null to just check the cache
        String cached = cacheManager.getOrComputeWithTTL(negativeKey, () -> null, NEGATIVE_FIELD_CACHE_TTL);
        return FIELD_NOT_FOUND_MARKER.equals(cached);
    }
    
    /**
     * Caches a field resolution failure to avoid repeated API calls
     * 
     * @param projectKey The project key
     * @param fieldName The field name that was not found
     */
    private void cacheFieldResolutionAsNotFound(String projectKey, String fieldName) {
        String negativeKey = "negativeField_" + projectKey + "_" + fieldName.toLowerCase();
        
        // Use getOrComputeWithTTL to store the negative result
        cacheManager.getOrComputeWithTTL(negativeKey, () -> FIELD_NOT_FOUND_MARKER, NEGATIVE_FIELD_CACHE_TTL);
        log("Cached negative result for field '" + fieldName + "' in project " + projectKey);
    }

    /**
     * Resolves a field name to its custom field ID using cached mappings
     * Enhanced with negative result caching to avoid repeated API calls
     * 
     * @param projectKey The project key
     * @param fieldName The user-friendly field name
     * @return The custom field ID or null if not found
     * @throws IOException if field resolution fails
     */
    private String resolveFieldNameToCustomFieldId(String projectKey, String fieldName) throws IOException {
        // If it's already a custom field ID, return as-is
        if (isCustomFieldId(fieldName)) {
            return fieldName;
        }
        
        // Check if this field is already known to not exist (negative cache)
        if (isFieldResolutionCachedAsNotFound(projectKey, fieldName)) {
            log("Field '" + fieldName + "' is cached as not found for project " + projectKey + " - skipping API call");
            return null;
        }
        
        // Get field mapping for project
        Map<String, String> projectMapping = getFieldMappingForProject(projectKey);
        
        // Try case-insensitive lookup
        String customFieldId = projectMapping.get(fieldName.toLowerCase());
        
        if (customFieldId != null) {
            log("Resolved field '" + fieldName + "' to '" + customFieldId + "' for project " + projectKey);
            return customFieldId;
        }
        
        // Fallback: try using the existing getFieldCustomCode method for single field resolution
        // But only if we haven't already cached this as a negative result
        try {
            log("Attempting fallback field resolution for '" + fieldName + "' in project " + projectKey);
            String resolvedFieldId = getFieldCustomCode(projectKey, fieldName);
            if (resolvedFieldId != null) {
                // Cache this mapping for future use
                projectMapping.put(fieldName.toLowerCase(), resolvedFieldId);
                log("Resolved and cached field '" + fieldName + "' to '" + resolvedFieldId + "' for project " + projectKey);
                return resolvedFieldId;
            } else {
                // Cache negative result to avoid future API calls
                cacheFieldResolutionAsNotFound(projectKey, fieldName);
            }
        } catch (Exception e) {
            log("Fallback field resolution failed for '" + fieldName + "': " + e.getMessage());
            // Cache negative result to avoid repeating this failed attempt
            cacheFieldResolutionAsNotFound(projectKey, fieldName);
        }
        
        log("Could not resolve field '" + fieldName + "' for project " + projectKey);
        return null;
    }
    
    /**
     * Resolves an array of field names, converting user-friendly names to custom field IDs where possible
     * Supports mixing regular field names with user-friendly custom field names
     * 
     * @param fields Array of field names (mix of user-friendly names and custom field IDs)
     * @param projectKey The project key for field resolution context
     * @return Array of resolved field names (preserves original names if resolution fails)
     */
    private String[] resolveFieldNames(String[] fields, String projectKey) {
        if (fields == null || fields.length == 0) {
            return fields;
        }
        
        String[] resolvedFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try {
                String resolvedField = resolveFieldNameToCustomFieldId(projectKey, fields[i]);
                // If resolution returns null, keep the original field name
                resolvedFields[i] = (resolvedField != null) ? resolvedField : fields[i];
            } catch (Exception e) {
                log("Error resolving field '" + fields[i] + "' for project " + projectKey + ": " + e.getMessage() + ". Using original name.");
                resolvedFields[i] = fields[i]; // Fallback to original field name
            }
        }
        
        if (logger != null) {
            logger.debug("Resolved {} field names for project {}", fields.length, projectKey);
        }
        
        return resolvedFields;
    }
    
    /**
     * Extracts the project key from a Jira ticket key
     * Ticket keys are in format "PROJECT-123", this method returns "PROJECT"
     * 
     * @param ticketKey The Jira ticket key (e.g., "TP-123")
     * @return The project key (e.g., "TP")
     */
    private String extractProjectKeyFromTicketKey(String ticketKey) {
        if (ticketKey == null || !ticketKey.contains("-")) {
            return ""; // Return empty string if invalid format
        }
        return ticketKey.substring(0, ticketKey.indexOf("-"));
    }
    
    /**
     * Extracts project key from JQL query for field resolution
     * Looks for "project = PROJECTKEY" or "project in (PROJECTKEY)" patterns
     * 
     * @param jql The JQL query string
     * @return The project key if found, empty string otherwise
     */
    private String extractProjectKeyFromJQL(String jql) {
        if (jql == null) {
            return "";
        }
        
        // Convert to lowercase for pattern matching, but keep original for extraction
        String lowerJql = jql.toLowerCase();
        
        // Pattern 1: "project = PROJECTKEY" (with or without spaces)
        String[] patterns = {"project=", "project =", "project in ("};
        
        for (String pattern : patterns) {
            int patternIndex = lowerJql.indexOf(pattern);
            if (patternIndex >= 0) {
                int startIndex = patternIndex + pattern.length();
                
                // Skip any additional whitespace after the pattern
                while (startIndex < jql.length() && Character.isWhitespace(jql.charAt(startIndex))) {
                    startIndex++;
                }
                
                if (startIndex < jql.length()) {
                    // Extract from the original JQL (preserve case) starting from startIndex
                    String remainder = jql.substring(startIndex);
                    
                    // Find the end of the project key (stop at whitespace, AND, OR, etc.)
                    int endIndex = 0;
                    for (int i = 0; i < remainder.length(); i++) {
                        char c = remainder.charAt(i);
                        if (Character.isWhitespace(c) || c == '(' || c == ')') {
                            break;
                        }
                        endIndex = i + 1;
                    }
                    
                    if (endIndex > 0) {
                        String projectKey = remainder.substring(0, endIndex).trim();
                        if (logger != null) {
                            logger.debug("Extracted project key '{}' from JQL: {}", projectKey, jql);
                        }
                        return projectKey;
                    }
                }
            }
        }
        
        if (logger != null) {
            logger.debug("Could not extract project key from JQL: {}", jql);
        }
        return ""; // Return empty string if no project found
    }
    
    /**
     * Resolves field names for search operations by extracting project context from JQL
     * 
     * @param searchQueryJQL The JQL query (used to extract project context)
     * @param fields Array of field names to resolve
     * @return Array of resolved field names
     */
    private String[] resolveFieldNamesForSearch(String searchQueryJQL, String[] fields) {
        if (fields == null || fields.length == 0) {
            return fields;
        }
        
        log("Attempting to resolve fields for search. JQL: " + searchQueryJQL);
        
        // Try to extract project key from JQL query
        String projectKey = extractProjectKeyFromJQL(searchQueryJQL);
        
        if (projectKey.isEmpty()) {
            // If we can't determine project, return original fields
            log("Could not extract project key from JQL for field resolution: " + searchQueryJQL);
            return fields;
        }
        
        log("Extracted project key '" + projectKey + "' from JQL. Resolving " + fields.length + " fields.");
        
        // Resolve field names using the extracted project key
        String[] resolvedFields = resolveFieldNames(fields, projectKey);
        
        log("Field resolution completed. Original fields: " + String.join(", ", fields) + 
            " -> Resolved fields: " + String.join(", ", resolvedFields));
        
        return resolvedFields;
    }

    @MCPTool(
            name = "jira_update_field",
            description = "Update a specific field of a Jira ticket. Supports both custom field IDs (e.g., description, 'customfield_10091') and user-friendly field names (e.g., 'Diagram')",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateField(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key, 
                            @MCPParam(name = "field", description = "The field name to update (supports both custom field IDs like 'customfield_10091' and user-friendly names like 'Diagram')", required = true) String field, 
                            @MCPParam(name = "value", description = "The new value for the field", required = true) Object value) throws IOException {
        if ("".equals(value)) {
            return clearField(key, field);
        }
        
        // Extract project key from ticket key for field resolution
        String projectKey = parseJiraProject(key);
        
        // Resolve field name to custom field ID if needed
        String resolvedFieldName = field;
        try {
            String customFieldId = resolveFieldNameToCustomFieldId(projectKey, field);
            if (customFieldId != null) {
                resolvedFieldName = customFieldId;
                log("Field resolution: '" + field + "' resolved to '" + resolvedFieldName + "' for ticket " + key);
            } else {
                log("Field resolution: Using original field name '" + field + "' for ticket " + key + " (no custom field mapping found)");
            }
        } catch (Exception e) {
            log("Field resolution failed for '" + field + "', using original field name: " + e.getMessage());
            // Continue with original field name as fallback
        }
        
        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        body.put("update", new JSONObject()
                .put(resolvedFieldName, new JSONArray()
                        .put(new JSONObject()
                                .put("set", value)
                        )));
        jiraRequest.setBody(body.toString());
        String updateResult = jiraRequest.put();
        log("Updated field '" + resolvedFieldName + "' (originally '" + field + "') on ticket " + key + " with value: " + value);

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
            description = "Execute a custom HTTP GET request to Jira API with auth. Can be used to perform any jira get requests which are required auth.",
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

            if (!isIgnoreCache && isReadCacheGetRequestsEnabled) {
                String value = DigestUtils.md5Hex(url);
                File cache = new File(getCacheFolderName());
                cache.mkdirs();
                File cachedFile = new File(getCacheFolderName() + "/" + value);
                if (cachedFile.exists()) {
                    return FileUtils.readFileToString(cachedFile, "UTF-8");
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
                        if (isReadCacheGetRequestsEnabled) {
                            String value = DigestUtils.md5Hex(url);
                            File cache = new File(getCacheFolderName());
                            cache.mkdirs();
                            File cachedFile = new File(getCacheFolderName() + "/" + value);
                            FileUtils.writeStringToFile(cachedFile, result, "UTF-8");
                        }
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
            description = "Get all available transitions(statuses, workflows) for a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public List<Transition> getTransitions(@MCPParam(name = "key", description = "The Jira ticket key to get transitions for", required = true) String ticket) throws IOException {
        return new TransitionsResult(transitions(ticket).execute()).getTransitions();
    }

    @Override
    @MCPTool(
            name = "jira_move_to_status",
            description = "Move a Jira ticket to a specific status (workflow, transition)",
            integration = "jira",
            category = "ticket_management"
    )
    public String moveToStatus(@MCPParam(name = "key", description = "The Jira ticket key to move", required = true) String ticketKey, 
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
            description = "Move a Jira ticket to a specific status (workflow, transition) with resolution",
            integration = "jira",
            category = "ticket_management"
    )
    public String moveToStatus(@MCPParam(name = "key", description = "The Jira ticket key to move", required = true) String ticket, 
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
            description = "Clear (delete value) a specific field value in a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String clearField(@MCPParam(name = "key", description = "The Jira ticket key to clear field from", required = true) String ticket,
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
    public String setTicketFixVersion(@MCPParam(name = "key", description = "The Jira ticket key to set fix version for", required = true) String ticket,
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
    public String addTicketFixVersion(@MCPParam(name = "key", description = "The Jira ticket key to add fix version to", required = true) String ticket, 
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
    public String setTicketPriority(@MCPParam(name = "key", description = "The Jira ticket key to set priority for", required = true) String key,
                                  @MCPParam(name = "priority", description = "The priority name to set", required = true) String priority) throws IOException {
        GenericRequest request = getTicket(key);
        JSONObject params = new JSONObject();
        JSONObject fields = new JSONObject();
        JSONObject priorityObject = new JSONObject();
        priorityObject.put("name", priority);
        fields.put("priority", priorityObject);
        params.put("fields", fields);
        System.out.println("key: " + key);
        System.out.println("priority: " + priority);
        String response = updateTicket(key, params);
        System.out.println("response: " + response);
        clearCache(request);
        return  response;
    }

    @MCPTool(
            name = "jira_remove_fix_version",
            description = "Remove a fix version from a Jira ticket",
            integration = "jira",
            category = "ticket_management"
    )
    public String removeTicketFixVersion(@MCPParam(name = "key", description = "The Jira ticket key to remove fix version from", required = true) String ticket, 
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
    @MCPTool(
            name = "jira_download_attachment",
            description = "Download a Jira attachment by URL and save it as a file",
            integration = "jira",
            category = "file_management"
    )
    public File convertUrlToFile(@MCPParam(name = "href", description = "The attachment URL to download", required = true) String href) throws IOException {
        File targetFile = getCachedFile(href);
        
        // Ensure the parent directory exists before downloading
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created && !parentDir.exists()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
            log("Created directory for download: " + parentDir.getAbsolutePath());
        }
        
        return Impl.downloadFile(this, new GenericRequest(this, href), targetFile);
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
        RestClient client = this;
        return cacheManager.getOrComputeSimple("getFields_" + project, () -> {
            try {
                GenericRequest genericRequest = new GenericRequest(client, path("field"));
                return genericRequest.execute();
            } catch (Exception e) {
                GenericRequest genericRequest = new GenericRequest(client, path("issue/createmeta?projectKeys=" + project + "&expand=projects.issuetypes.fields"));
                try {
                    return  genericRequest.execute();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    /**
     * Clears the cached Cloud/Server Jira detection result
     * This forces a new API call on the next detection request
     */
    public void clearCloudJiraDetectionCache() {
        cacheManager.removeFromCache("isCloudJira");
        log("Cleared Cloud/Server Jira detection cache");
    }

    /**
     * Detects whether this Jira instance is Cloud or Server using the official REST API
     * Results are cached to avoid repeated API calls
     */
    private boolean isCloudJira() {
        String cacheKey = "isCloudJira";
        
        return cacheManager.getOrComputeSimple(cacheKey, () -> {
            try {
                // Use the official REST API endpoint to determine deployment type
                GenericRequest genericRequest = new GenericRequest(this, path("serverInfo"));
                String response = genericRequest.execute();
                
                JSONObject serverInfo = new JSONObject(response);
                
                // Check for deploymentType field - Cloud instances have "Cloud" value
                if (serverInfo.has("deploymentType")) {
                    String deploymentType = serverInfo.getString("deploymentType");
                    log("Detected Jira deployment type: " + deploymentType);
                    return "Cloud".equalsIgnoreCase(deploymentType);
                }
                
                // Fallback: Check URL pattern for atlassian.net (Cloud indicator)
                boolean isAtlassianNet = getBasePath().contains("atlassian.net");
                log("No deploymentType field found, using URL pattern. atlassian.net detected: " + isAtlassianNet);
                return isAtlassianNet;
                
            } catch (Exception e) {
                log("Error detecting Jira type via serverInfo API: " + e.getMessage());
                // Fallback: Check URL pattern for atlassian.net (Cloud indicator)
                boolean isAtlassianNet = getBasePath().contains("atlassian.net");
                log("Using URL pattern fallback. atlassian.net detected: " + isAtlassianNet);
                return isAtlassianNet;
            }
        });
    }

    @MCPTool(
            name = "jira_get_issue_types",
            description = "Get all available issue types for a specific Jira project",
            integration = "jira",
            category = "project_management"
    )
    public List<IssueType> getIssueTypes(@MCPParam(name = "project", description = "The Jira project key to get issue types for", required = true) String project) throws IOException {
        try {
            // Use the same endpoint as getFields but extract only issue types
            GenericRequest genericRequest = new GenericRequest(this, path("issue/createmeta?projectKeys=" + project + "&expand=projects.issuetypes.fields"));
            String response = genericRequest.execute();
            
            // Detect if this is Cloud or Server Jira and parse accordingly
            if (isCloudJira()) {
                log("Detected Cloud Jira instance for project " + project);
                return parseCloudJiraIssueTypes(response);
            } else {
                log("Detected Server Jira instance for project " + project);
                return parseServerJiraIssueTypes(response, project);
            }
        } catch (Exception e) {
            log("Error getting issue types: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parse issue types from Cloud Jira response format
     */
    private List<IssueType> parseCloudJiraIssueTypes(String response) {
        List<IssueType> result = new ArrayList<>();
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray projects = jsonResponse.getJSONArray("projects");
            
            if (!projects.isEmpty()) {
                JSONObject projectObj = projects.getJSONObject(0);
                JSONArray issueTypes = projectObj.getJSONArray("issuetypes");
                return JSONModel.convertToModels(IssueType.class, issueTypes);
            }
        } catch (Exception e) {
            log("Error parsing Cloud Jira issue types: " + e.getMessage());
        }
        return result;
    }

    /**
     * Parse issue types from Server Jira response format
     */
    private List<IssueType> parseServerJiraIssueTypes(String response, String project) {
        List<IssueType> result = new ArrayList<>();
        try {
            // First try to parse the response as if it contains issue types directly
            JSONArray issueTypes = new JSONArray(response);
            return JSONModel.convertToModels(IssueType.class, issueTypes);
        } catch (Exception e) {
            log("Direct parsing failed, trying alternative Server Jira endpoints");
            // If direct parsing fails, try alternative Server Jira endpoints
            try {
                return getServerJiraIssueTypesAlternative(project);
            } catch (Exception ex) {
                log("Alternative Server Jira issue types endpoint also failed: " + ex.getMessage());
            }
        }
        return result;
    }

    /**
     * Alternative method to get issue types for Server Jira
     */
    private List<IssueType> getServerJiraIssueTypesAlternative(String project) throws IOException {
        List<IssueType> result = new ArrayList<>();
        try {
            // Try the direct issue types endpoint for Server Jira
            GenericRequest genericRequest = new GenericRequest(this, path("issuetype"));
            String response = genericRequest.execute();
            
            JSONArray issueTypes = new JSONArray(response);
            return JSONModel.convertToModels(IssueType.class, issueTypes);
        } catch (Exception e) {
            log("Error getting Server Jira issue types from alternative endpoint: " + e.getMessage());
            // Final fallback: try project-specific endpoint
            try {
                GenericRequest genericRequest = new GenericRequest(this, path("project/" + project));
                String response = genericRequest.execute();
                JSONObject projectObj = new JSONObject(response);
                JSONArray issueTypes = projectObj.getJSONArray("issueTypes");
                return JSONModel.convertToModels(IssueType.class, issueTypes);
            } catch (Exception fallbackException) {
                log("Failed to get issue types for project " + project + ": " + fallbackException.getMessage());
            }
        }
        return result;
    }

    @MCPTool(
            name = "jira_get_field_custom_code",
            description = "Get the custom field code for a human friendly field name in a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public String getFieldCustomCode(@MCPParam(name = "project", description = "The Jira project key", required = true) String project, 
                                   @MCPParam(name = "fieldName", description = "The human-readable field name", required = true) String fieldName) throws IOException {
        String response = getFields(project);
        
        // Use the same detection logic as getIssueTypes for consistency
        if (isCloudJira()) {
            log("Using Cloud Jira parsing for field custom code");
            try {
                return parseCloudJiraResponse(fieldName, response);
            } catch (JSONException e) {
                log("Cloud Jira parsing failed, falling back to Server parsing: " + e.getMessage());
                return parseServerJiraResponse(fieldName, response);
            }
        } else {
            log("Using Server Jira parsing for field custom code");
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