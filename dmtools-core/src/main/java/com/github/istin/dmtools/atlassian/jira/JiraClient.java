// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.model.*;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.atlassian.jira.utils.JiraResponseUtils;
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
import com.github.istin.dmtools.networking.RetryPolicy;
import com.github.istin.dmtools.networking.RetryPolicyConfig;
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
import org.json.JSONTokener;

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
    public static final String PARAM_EXPAND = "expand";
    private static final List<String> EXPAND_ONLY_FIELDS = java.util.Arrays.asList("changelog", "renderedFields", "names", "schema", "transitions", "editmeta", "versionedRepresentations");

    /**
     * Resolves an array of field names, converting user-friendly names to custom field IDs where possible
     * Supports mixing regular field names with user-friendly custom field names
     *
     * @param fields Array of field names (mix of user-friendly names and custom field IDs)
     * @param projectKey The project key for field resolution context
     * @return Array of resolved field names (preserves original names if resolution fails)
     */
    // Known system fields in Jira that should not be resolved to custom fields
    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "summary", "description", "status", "assignee", "reporter", "creator",
            "created", "updated", "resolution", "priority", "issuetype", "project",
            "labels", "comment", "attachment", "worklog", "timetracking",
            "aggregatetimeestimate", "aggregatetimespent", "aggregateprogress", "workratio",
            "security", "issuerestriction", "thumbnail", "timespent", "timeestimate",
            "duedate", "environment", "components", "versions", "fixversions",
            "subtasks", "parent", "issuelinks", "watches", "votes"
    );

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
    @Getter
    private boolean isLogEnabled = true;

    @Setter
    @Getter
    private boolean isTransformCustomFieldsToNames = false;

    @Setter
    @Getter
    private String projectContext;
    
    // Cache manager for keys logic only (field mappings, Cloud/Server detection, etc.)
    private final CacheManager cacheManager;

    // Field mapping cache TTL in milliseconds (24 hours)
    private static final long FIELD_MAPPING_CACHE_TTL = 24 * 60 * 60 * 1000L;

    // Cache for negative field resolution results (fields that don't exist) - 1 hour TTL
    private static final long NEGATIVE_FIELD_CACHE_TTL = 60 * 60 * 1000L;

    // Special marker for cached negative results
    private static final String FIELD_NOT_FOUND_MARKER = "__FIELD_NOT_FOUND__";
    private final int maxResults;

    // Retry policy for handling rate limits and transient failures
    private RetryPolicy retryPolicy;

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

        // Initialize retry policy - check if this is cloud Jira and use appropriate settings
        String cloudIndicator = System.getenv("JIRA_CLOUD");
        if ("true".equalsIgnoreCase(cloudIndicator) || (basePath != null && basePath.contains(".atlassian.net"))) {
            this.retryPolicy = RetryPolicyConfig.forJiraCloud(this.logger);
        } else {
            // Use environment-based configuration or defaults
            this.retryPolicy = RetryPolicyConfig.fromEnvironment(this.logger);
        }

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
            category = "information",
            aliases = {"tracker_get_user_by_email"}
    )
    public Assignee getAccountByEmail(
            @MCPParam(name = "email", description = "The Jira Email", required = true, example = "email@email.com")
            String email
    ) throws IOException {
        GenericRequest jiraRequest = new GenericRequest(this, path("user/search?query=" + email));
        List<Assignee> result = JSONModel.convertToModels(Assignee.class, new JSONArray(jiraRequest.execute()));
        if (!result.isEmpty()) {
            return result.get(0);
        } else {
            return null;
        }
    }

    @Override
    @MCPTool(
            name = "jira_assign_ticket_to",
            description = "Assigns a Jira ticket to user",
            integration = "jira",
            category = "ticket_management",
            aliases = {"tracker_assign_ticket"}
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
        if (ticket instanceof Ticket) {
            Changelog preloaded = ((Ticket) ticket).getChangelog();
            if (preloaded != null) {
                return preloaded;
            }
        }
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
        clearCache(createPerformTicketRequest(key, new String[]{Fields.LABELS}));
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
    public List<? extends ITicket> getTestCases(ITicket ticket, String testCaseIssueType) throws IOException {
        Fields fields = ticket.getFields();
        if (fields == null) {
            return Collections.emptyList();
        }
        List<IssueLink> issueLinks = fields.getIssueLinks();
        if (issueLinks == null || issueLinks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ITicket> testCases = new ArrayList<>();
        for (IssueLink issueLink : issueLinks) {
            ITicket relatedTicket = issueLink.getRelatedTicket();
            if (relatedTicket != null && relatedTicket.getIssueType().equalsIgnoreCase(testCaseIssueType)) {
                testCases.add(relatedTicket);
            }
        }
        return testCases;
    }

    @Override
    public void deleteLabelInTicket(T ticket, String label) throws IOException {
        JSONArray jsonArray = ticket.getTicketLabels();
        if (jsonArray == null) {
            return;
        }
        JSONArray updatedLabels = new JSONArray();
        for (int i = 0; i < jsonArray.length(); i++) {
            String existing = jsonArray.optString(i);
            if (!label.equalsIgnoreCase(existing)) {
                updatedLabels.put(existing);
            }
        }
        log(updateField(ticket.getKey(), Fields.LABELS, updatedLabels));
    }

    @MCPTool(
            name = "jira_remove_label",
            description = "Remove a label from a specific Jira ticket. Fetches current labels and removes the specified one.",
            integration = "jira",
            category = "ticket_management"
    )
    public void removeLabel(
            @MCPParam(name = "key", description = "The Jira ticket key to remove label from", required = true, example = "PRJ-123")
            String key,
            @MCPParam(name = "label", description = "The label to be removed from the ticket", required = true, example = "custom_label")
            String label
    ) throws IOException {
        T ticket = performTicket(key, new String[]{Fields.LABELS});
        deleteLabelInTicket(ticket, label);
        clearCache(createPerformTicketRequest(key, new String[]{Fields.LABELS}));
    }

    @NotNull
    public GenericRequest createChangelogRequest(String key) {
        return new GenericRequest(this, path("issue/" + key + "?expand=changelog&fields=summary"));
    }

    @Override
    public T createTicket(String body) {
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
            category = "search",
            aliases = {"tracker_search"}
    )
    public List<T> searchAndPerform(
            @MCPParam(name = "jql", description = "JQL query string to search tickets", required = true, example = "project = DEMO AND status = Open", aliases = {"searchQueryJQL", "query"})
            String jql,
            @MCPParam(name = "fields", description = "Optional array of field names to include in response", required = false, example = "[\"summary\", \"status\", \"assignee\"]")
            String[] fields
    ) throws Exception {
        List<T> tickets = new ArrayList<>();
        searchAndPerform(ticket -> {
            tickets.add(ticket);
            return false;
        }, jql, fields);
        return tickets;
    }


    @Override
    public void searchAndPerform(Performer<T> performer, String searchQueryJQL, String[] fields) throws Exception {
        // Resolve field names to support user-friendly custom field names
        List<String> resolvedFields = resolveFieldNamesForSearch(searchQueryJQL, fields);
        if (isCloudJira()) {
            SearchResult searchResults = searchByPage(searchQueryJQL, null, resolvedFields);
            if (searchResults == null) {
                logger.error("Received null search results for JQL: {}", searchQueryJQL);
                throw new RestClient.RestClientException("Search returned null results", "null", -1);
            }
            
            JSONArray errorMessages = searchResults.getErrorMessages();
            if (errorMessages != null && errorMessages.length() > 0) {
                logger.error("Search failed with errors: {}", errorMessages);
                throw new RestClient.RestClientException("Search failed: " + errorMessages.toString(), errorMessages.toString(), -1);
            }
            
            boolean isBreak = false;
            int ticketIndex = 0;
            while (true) {
                List<Ticket> tickets = searchResults.getIssues();
                if (tickets == null || tickets.isEmpty()) {
                    //log("No more tickets to process");
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
                    throw new RestClient.RestClientException("Pagination failed: " + e.getMessage(), e.toString(), -1);
                }
            }
        } else {
            legacyServerJiraSearch(performer, searchQueryJQL, resolvedFields);
        }
    }

    private void legacyServerJiraSearch(Performer<T> performer, String searchQueryJQL, List<String> resolvedFields) throws Exception {
        int startAt = 0;
        SearchResult searchResults = search(searchQueryJQL, startAt, resolvedFields);
        JSONArray errorMessages = searchResults.getErrorMessages();
        if (errorMessages != null) {
            logger.error("Error", errorMessages);
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
        List<String> fields
    ) throws IOException {
        String[] separated = separateExpandFields(fields);
        String fieldsParam = separated[0];
        String expandParam = separated[1];

        GenericRequest jqlSearchRequest = new GenericRequest(this, path("search")).
                param(PARAM_JQL, jql)
                .param(PARAM_FIELDS, fieldsParam)
                .param(PARAM_START_AT, String.valueOf(startAt));
        if (expandParam != null) {
            jqlSearchRequest.param(PARAM_EXPAND, expandParam);
        }

        Integer expired = jqlExpirationInHours.get(jql);
        if (expired != null) {
            clearRequestIfExpired(jqlSearchRequest, System.currentTimeMillis() - expired*60*60*1000);
        }

        try {
            String body = jqlSearchRequest.execute();
            String projectKey = extractProjectKeyFromJQL(jql);
            body = transformResponse(body, projectKey);
            return new SearchResult(body);
        } catch (JSONException e) {
            clearCache(jqlSearchRequest);
            String body = jqlSearchRequest.execute();
            String projectKey = extractProjectKeyFromJQL(jql);
            body = transformResponse(body, projectKey);
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
            List<String> fields
    ) throws IOException {
        String[] separated = separateExpandFields(fields);
        String fieldsParam = separated[0];
        String expandParam = separated[1];

        GenericRequest jqlSearchRequest = new GenericRequest(this, path("search/jql")).
                param(PARAM_JQL, jql)
                .param(PARAM_FIELDS, fieldsParam);
        if (expandParam != null) {
            jqlSearchRequest.param(PARAM_EXPAND, expandParam);
        }
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
            //log("Jira search API response received, length: " + body.length());
            // Log the response structure to debug missing fields
            try {
                JSONObject jsonResponse = new JSONObject(body);
                if (jsonResponse.has("issues")) {
                    JSONArray issues = jsonResponse.getJSONArray("issues");
                    if (issues.length() > 0) {
                        JSONObject firstIssue = issues.getJSONObject(0);
                        if (firstIssue.has("fields")) {
                            JSONObject fieldsObj = firstIssue.getJSONObject("fields");
                            log("Fields returned by Jira API: " + fieldsObj.keySet().toString());
                        }
                    }
                }
            } catch (Exception logException) {
                log("Could not parse Jira response for logging: " + logException.getMessage());
            }
            String projectKey = extractProjectKeyFromJQL(jql);
            body = transformResponse(body, projectKey);
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
            category = "user_management",
            aliases = {"tracker_get_my_profile"}
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
            category = "ticket_management",
            aliases = {"tracker_get_ticket"}
    )
    public T performTicket(@MCPParam(name = "key", description = "The Jira ticket key to retrieve", required = true) String ticketKey, 
                          @MCPParam(name = "fields", description = "Optional array of fields to include in the response", required = false) String[] fields) throws IOException {
        GenericRequest jiraRequest = createPerformTicketRequest(ticketKey, fields);
        String response = jiraRequest.execute();
        if (response.contains("errorMessages")) {
            return null;
        }
        String projectKey = extractProjectKeyFromTicketKey(ticketKey);
        response = transformResponse(response, projectKey);
        return createTicket(response);
    }

    protected GenericRequest createPerformTicketRequest(String ticketKey, String[] fields) {
        GenericRequest jiraRequest = getTicket(ticketKey);
        if (fields != null && fields.length > 0) {
            // Extract project key from ticket key for field resolution
            String projectKey = extractProjectKeyFromTicketKey(ticketKey);
            
            // Resolve field names to support user-friendly custom field names
            List<String> resolvedFields = resolveFieldNames(fields, projectKey);

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
            category = "comment_management",
            aliases = {"tracker_get_comments"}
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
            category = "comment_management",
            aliases = {"tracker_post_comment"}
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

        if (fieldsInitializer != null) {
            fieldsInitializer.init(fields);
        }

        // Resolve field names to custom field IDs before creating the ticket
        resolveFieldsForProject(fields, project);

        fields.set("project", new JSONObject().put("key", project));
        fields.set("summary", summary);

        fields.set("description", description);

        IssueType value = new IssueType();
        value.set("name", issueType);
        fields.set(Fields.ISSUETYPE, value.getJSONObject());



        jsonObject.put("fields", fields.getJSONObject());

        jiraRequest.setBody(jsonObject.toString());
        String post = jiraRequest.post();

        // Check for errors in the response before extracting key
        JSONObject responseJson = new JSONObject(post);
        checkJiraResponseForErrors(responseJson, "Failed to create ticket");

        String key = responseJson.getString("key");
        log(getTicketBrowseUrl(key));
        return post;
    }

    /**
     * Resolves field names to custom field IDs in a Fields object for a specific project.
     * This ensures that user-friendly field names are properly converted to custom field IDs
     * before ticket creation, similar to how updateField works.
     *
     * @param fields The Fields object containing the ticket fields
     * @param projectKey The Jira project key for field resolution context
     * @throws IOException if field resolution fails
     */
    private void resolveFieldsForProject(Fields fields, String projectKey) throws IOException {
        JSONObject fieldsJson = fields.getJSONObject();
        JSONObject resolvedFieldsJson = new JSONObject();
        log(fieldsJson.toString());
        // Copy all existing fields to the resolved fields object
        for (String fieldName : fieldsJson.keySet()) {
            String customField = null;
            if (!SYSTEM_FIELDS.contains(fieldName.toLowerCase())) {
                customField = resolveFieldNameToCustomFieldId(projectKey, fieldName);
            }

            if (customField == null) {
                customField = fieldName;
            }
            log(fieldName + " -> " + customField);
            resolvedFieldsJson.put(customField, fieldsJson.get(fieldName));
        }
        log(resolvedFieldsJson.toString());
        fields.setJO(resolvedFieldsJson);
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
            category = "ticket_management",
            aliases = {"tracker_create_ticket"}
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
        
        // Check for errors in the response before extracting key
        JSONObject responseJson = new JSONObject(post);
        checkJiraResponseForErrors(responseJson, "Failed to create ticket");
        
        String key = responseJson.getString("key");
        log(getTicketBrowseUrl(key));
        return post;
    }
    
    /**
     * Checks Jira API response for errors and throws a descriptive exception if found.
     * 
     * @param responseJson The JSON response from Jira API
     * @param defaultMessage Default error message if no specific errors found
     * @throws IOException if errors are found in the response
     */
    private void checkJiraResponseForErrors(JSONObject responseJson, String defaultMessage) throws IOException {
        List<String> allErrors = new ArrayList<>();
        
        // Check for errorMessages array
        if (responseJson.has("errorMessages")) {
            JSONArray errorMessages = responseJson.optJSONArray("errorMessages");
            if (errorMessages != null && errorMessages.length() > 0) {
                for (int i = 0; i < errorMessages.length(); i++) {
                    allErrors.add(errorMessages.getString(i));
                }
            }
        }
        
        // Check for errors object (field-level errors)
        if (responseJson.has("errors")) {
            JSONObject errors = responseJson.optJSONObject("errors");
            if (errors != null && errors.length() > 0) {
                for (String fieldName : errors.keySet()) {
                    String errorMessage = errors.getString(fieldName);
                    // Try to resolve field name to human-readable format
                    String readableFieldName = resolveFieldNameToReadable(fieldName);
                    if (readableFieldName != null && !readableFieldName.equals(fieldName)) {
                        allErrors.add(readableFieldName + " (" + fieldName + "): " + errorMessage);
                    } else {
                        // For custom fields, try to make the message more user-friendly
                        if (fieldName.startsWith("customfield_")) {
                            allErrors.add("Field " + fieldName + ": " + errorMessage);
                        } else {
                            allErrors.add(fieldName + ": " + errorMessage);
                        }
                    }
                }
            }
        }
        
        // If we found any errors, throw an exception with a clear message
        if (!allErrors.isEmpty()) {
            StringBuilder errorBuilder = new StringBuilder(defaultMessage);
            if (allErrors.size() == 1) {
                errorBuilder.append(" - ").append(allErrors.get(0));
            } else {
                errorBuilder.append(":\n");
                for (int i = 0; i < allErrors.size(); i++) {
                    errorBuilder.append("  ").append(i + 1).append(". ").append(allErrors.get(i));
                    if (i < allErrors.size() - 1) {
                        errorBuilder.append("\n");
                    }
                }
            }
            throw new IOException(errorBuilder.toString());
        }
    }
    
    /**
     * Attempts to resolve a field ID or custom field code to a human-readable field name.
     * 
     * @param fieldIdentifier Field ID (e.g., "customfield_10238") or field code
     * @return Human-readable field name if found, or null
     */
    private String resolveFieldNameToReadable(String fieldIdentifier) {
        // Check if it's a standard field
        if (fieldIdentifier.equals("summary")) return "Summary";
        if (fieldIdentifier.equals("description")) return "Description";
        if (fieldIdentifier.equals("issuetype")) return "Issue Type";
        if (fieldIdentifier.equals("project")) return "Project";
        if (fieldIdentifier.equals("priority")) return "Priority";
        if (fieldIdentifier.equals("assignee")) return "Assignee";
        if (fieldIdentifier.equals("reporter")) return "Reporter";
        if (fieldIdentifier.equals("status")) return "Status";
        
        // For custom fields, try to get the field name from cache or API
        // This is a best-effort attempt - if field mapping is not available, return null
        try {
            // Try to get field name from field mapping cache if available
            // Note: This requires field mapping to be populated, which may not always be available
            return null; // Could be enhanced to check field mapping cache
        } catch (Exception e) {
            // If we can't resolve, return null and use the field identifier as-is
            return null;
        }
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
                
                // Parse all fields and build mapping (same format for both Cloud and Server)
                populateFieldMappingFromServerResponse(projectFieldMapping, response);
                
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
     * Gets a reverse mapping (ID to human name) for fields in a project
     * 
     * @param projectKey The project key
     * @return Map of custom field ID to human-friendly name
     * @throws IOException if field loading fails
     */
    private Map<String, String> getReverseFieldMappingForProject(String projectKey) throws IOException {
        String cacheKey = "reverseFieldMapping_" + projectKey;

        return cacheManager.getOrComputeWithTTL(cacheKey, () -> {
            try {
                String response = getFields(projectKey);
                Map<String, String> reverseMapping = new ConcurrentHashMap<>();
                // Track field names to detect duplicates
                Map<String, Integer> fieldNameCounts = new ConcurrentHashMap<>();
                Map<String, List<String>> fieldNameToIds = new ConcurrentHashMap<>();

                JSONArray fields = new JSONArray(response);

                // First pass: collect all field IDs grouped by name
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    String fieldId = field.optString("id");
                    String fieldName = field.optString("name");
                    if (fieldId != null && fieldId.startsWith("customfield_") && fieldName != null) {
                        fieldNameCounts.merge(fieldName, 1, Integer::sum);
                        fieldNameToIds.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(fieldId);
                    }
                }

                // Second pass: create mapping with postfix for duplicates
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    String fieldId = field.optString("id");
                    String fieldName = field.optString("name");
                    if (fieldId != null && fieldId.startsWith("customfield_") && fieldName != null) {
                        String mappedName = fieldName;
                        // If there are multiple fields with the same name, add field ID postfix
                        if (fieldNameCounts.get(fieldName) > 1) {
                            mappedName = fieldName + " (" + fieldId + ")";
                        }
                        reverseMapping.put(fieldId, mappedName);
                    }
                }

                // Log duplicate fields
                for (Map.Entry<String, Integer> entry : fieldNameCounts.entrySet()) {
                    if (entry.getValue() > 1) {
                        log("Found " + entry.getValue() + " fields with name '" + entry.getKey() +
                            "', adding field IDs as postfix: " + fieldNameToIds.get(entry.getKey()));
                    }
                }

                return reverseMapping;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, FIELD_MAPPING_CACHE_TTL);
    }

    private String transformResponse(String response, String projectKey) throws IOException {
        if (response == null || response.isEmpty() || !isTransformCustomFieldsToNames()) {
            return response;
        }

        String projectToUse = projectKey;
        if (projectToUse == null || projectToUse.isEmpty()) {
            projectToUse = getProjectContext();
        }

        if (projectToUse == null || projectToUse.isEmpty()) {
            log("No project context available for field transformation, skipping.");
            return response;
        }

        try {
            Map<String, String> reverseMapping = getReverseFieldMappingForProject(projectToUse);
            if (reverseMapping.isEmpty()) {
                return response;
            }

            if (response.trim().startsWith("{")) {
                JSONObject jsonObject = new JSONObject(response);
                JiraResponseUtils.transformJson(jsonObject, reverseMapping);
                return jsonObject.toString();
            } else if (response.trim().startsWith("[")) {
                JSONArray jsonArray = new JSONArray(response);
                for (int i = 0; i < jsonArray.length(); i++) {
                    Object item = jsonArray.get(i);
                    if (item instanceof JSONObject) {
                        JiraResponseUtils.transformJson((JSONObject) item, reverseMapping);
                    }
                }
                return jsonArray.toString();
            }
        } catch (JSONException e) {
            log("Failed to parse JSON for transformation: " + e.getMessage());
        }
        return response;
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

                    // Only map custom fields, not system fields
                    // System fields have IDs like "summary", "description", "status", etc.
                    // Custom fields have IDs like "customfield_xxxxx"
                    if (fieldId.startsWith("customfield_")) {
                        mapping.put(fieldName.toLowerCase(), fieldId);
                        log("Mapped custom field '" + fieldName + "' to '" + fieldId + "'");
                    } else {
                        log("Skipping system field '" + fieldName + "' with ID '" + fieldId + "'");
                    }
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

        // Skip resolution for system fields - they should never be resolved to custom fields
        if (SYSTEM_FIELDS.contains(fieldName.toLowerCase())) {
            return null;
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
            //log("Resolved field '" + fieldName + "' to '" + customFieldId + "' for project " + projectKey);
            return customFieldId;
        }
        
        // Fallback: try using the existing getFieldCustomCode method for single field resolution
        // But only if we haven't already cached this as a negative result
        try {
            //log("Attempting fallback field resolution for '" + fieldName + "' in project " + projectKey);
            String resolvedFieldId = getFieldCustomCode(projectKey, fieldName);
            if (resolvedFieldId != null) {
                // Cache this mapping for future use
                projectMapping.put(fieldName.toLowerCase(), resolvedFieldId);
                //log("Resolved and cached field '" + fieldName + "' to '" + resolvedFieldId + "' for project " + projectKey);
                return resolvedFieldId;
            } else {
                // Cache negative result to avoid future API calls
                cacheFieldResolutionAsNotFound(projectKey, fieldName);
            }
        } catch (Exception e) {
            //log("Fallback field resolution failed for '" + fieldName + "': " + e.getMessage());
            // Cache negative result to avoid repeating this failed attempt
            cacheFieldResolutionAsNotFound(projectKey, fieldName);
        }
        
        //log("Could not resolve field '" + fieldName + "' for project " + projectKey);
        return null;
    }
    

    /**
     * Separates expand-only fields (e.g. "changelog") from regular fields.
     * Returns a two-element array: [0] = regular fields joined by comma, [1] = expand value or null.
     */
    private String[] separateExpandFields(List<String> fields) {
        List<String> regularFields = new ArrayList<>();
        List<String> expandFields = new ArrayList<>();
        for (String field : fields) {
            if (EXPAND_ONLY_FIELDS.contains(field.toLowerCase())) {
                expandFields.add(field.toLowerCase());
            } else {
                regularFields.add(field);
            }
        }
        String fieldsParam = StringUtils.concatenate(",", regularFields);
        String expandParam = expandFields.isEmpty() ? null : String.join(",", expandFields);
        return new String[]{fieldsParam, expandParam};
    }

    private List<String> resolveFieldNames(String[] fields, String projectKey) {
        if (fields == null || fields.length == 0) {
            return Collections.emptyList();
        }

        List<String> resolvedFields = new ArrayList<>();
        for (String field : fields) {
            try {
                String fieldName = field.toLowerCase();

                // Check if this field (case-insensitive) is already in resolvedFields
                boolean alreadyExists = resolvedFields.stream()
                    .anyMatch(existing -> existing.equalsIgnoreCase(field));
                if (alreadyExists) {
                    //log("Field '" + field + "' already exists in resolved fields (case-insensitive), skipping");
                    continue;
                }

                // For system fields, keep them as-is AND also try to resolve to custom fields with same name
                if (SYSTEM_FIELDS.contains(fieldName)) {
                    resolvedFields.add(field);
                    //log("Field '" + field + "' is a system field, keeping original name");

                    // Also try to find ALL custom fields with the same name (case-insensitive)
                    try {
                        List<String> customFieldIds = getAllFieldCustomCodes(projectKey, field);
                        for (String customFieldId : customFieldIds) {
                            // Check if custom field already exists (case-insensitive)
                            boolean customAlreadyExists = resolvedFields.stream()
                                .anyMatch(existing -> existing.equalsIgnoreCase(customFieldId));
                            if (!customAlreadyExists) {
                                resolvedFields.add(customFieldId);
                                //log("Also found custom field '" + customFieldId + "' for system field '" + field + "'");
                            }
                        }
                    } catch (Exception e) {
                        //log("Error finding custom fields for system field '" + field + "': " + e.getMessage());
                    }
                } else {
                    // Check if field is already a custom field ID (starts with "customfield_")
                    if (field.startsWith("customfield_")) {
                        // This is already a custom field ID, keep it as-is
                        resolvedFields.add(field);
                        log("Field '" + field + "' is already a custom field ID, keeping as-is");
                    } else {
                        // For non-system fields, try to resolve to ALL custom fields with this name
                        log("Attempting to resolve field '" + field + "' for project '" + projectKey + "'");
                        try {
                            List<String> customFieldIds = getAllFieldCustomCodes(projectKey, field);
                            log("Found " + customFieldIds.size() + " custom fields for '" + field + "': " + customFieldIds);
                            if (!customFieldIds.isEmpty()) {
                                // Add ALL matching custom fields
                                for (String customFieldId : customFieldIds) {
                                    // Check if resolved field already exists (case-insensitive)
                                    boolean resolvedAlreadyExists = resolvedFields.stream()
                                        .anyMatch(existing -> existing.equalsIgnoreCase(customFieldId));
                                    if (!resolvedAlreadyExists) {
                                        resolvedFields.add(customFieldId);
                                        log("Successfully resolved field '" + field + "' to '" + customFieldId + "'");
                                    } else {
                                        log("Resolved field '" + customFieldId + "' already exists, skipping");
                                    }
                                }
                            } else {
                                resolvedFields.add(field);
                                log("Field '" + field + "' not found in custom fields, keeping original name");
                            }
                        } catch (Exception e) {
                            resolvedFields.add(field);
                            log("Error resolving field '" + field + "': " + e.getMessage() + ". Using original name.");
                        }
                    }
                }
            } catch (Exception e) {
                //log("Error resolving field '" + field + "' for project " + projectKey + ": " + e.getMessage() + ". Using original name.");
                // Field is already added, keep it as-is
            }
        }

        log("Resolved " + fields.length + " field names for project " + projectKey + " -> " + resolvedFields.size() + " total fields");
        log("Original fields: " + String.join(", ", fields));
        log("Resolved fields: " + String.join(", ", resolvedFields));

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
     * Fetches all project keys from this Jira instance and caches them.
     * Used by extractProjectKeyFromJQL to identify project context without fragile regex parsing.
     */
    protected List<String> getKnownProjectKeys() {
        List<String> cached = cacheManager.getOrComputeSimple("allProjectKeys", () -> {
            try {
                GenericRequest req = new GenericRequest(this, path("project"));
                req.setIgnoreCache(true);
                String response = req.execute();
                JSONArray projects = new JSONArray(response);
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < projects.length(); i++) {
                    keys.add(projects.getJSONObject(i).getString("key"));
                }
                logger.debug("Loaded {} Jira project keys", keys.size());
                return keys;
            } catch (Exception e) {
                logger.warn("Failed to fetch project list: {}", e.getMessage());
                return new ArrayList<>();
            }
        });
        return cached != null ? cached : new ArrayList<>();
    }

    private String extractProjectKeyFromJQL(String jql) {
        if (jql == null || jql.trim().isEmpty()) {
            return "";
        }

        String upperJql = jql.toUpperCase();
        // Sort by length descending so longer keys (e.g. "MYPROJ") are checked before shorter ones (e.g. "MY")
        Optional<String> match = getKnownProjectKeys().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(key -> {
                    String upperKey = key.toUpperCase();
                    return upperJql.contains(upperKey + "-")
                            || Pattern.compile("\\b" + Pattern.quote(upperKey) + "\\b").matcher(upperJql).find();
                })
                .findFirst();

        if (match.isPresent()) {
            logger.debug("Extracted project key '{}' from JQL: {}", match.get(), jql);
            return match.get();
        }

        // Fallback: extract project key directly from JQL syntax (e.g. "project = MYTUBE" or "KEY-123")
        // This handles cases where the user account is not a member of the project
        java.util.regex.Matcher projectMatcher = Pattern.compile(
                "\\bproject\\s*(?:=|in)\\s*[\"'(]?([A-Z][A-Z0-9_]+)[\"')]?", Pattern.CASE_INSENSITIVE
        ).matcher(jql);
        if (projectMatcher.find()) {
            String extracted = projectMatcher.group(1).toUpperCase();
            logger.debug("Extracted project key '{}' from JQL via pattern (not in member list): {}", extracted, jql);
            return extracted;
        }
        // Also try to extract from a ticket key pattern like "MYTUBE-123"
        java.util.regex.Matcher keyMatcher = Pattern.compile("\\b([A-Z][A-Z0-9_]+)-\\d+\\b").matcher(upperJql);
        if (keyMatcher.find()) {
            String extracted = keyMatcher.group(1);
            logger.debug("Extracted project key '{}' from ticket key pattern in JQL: {}", extracted, jql);
            return extracted;
        }

        return "";
    }

    /**
     * Resolves field names for search operations by extracting project context from JQL
     * 
     * @param searchQueryJQL The JQL query (used to extract project context)
     * @param fields Array of field names to resolve
     * @return Array of resolved field names
     */
    private List<String> resolveFieldNamesForSearch(String searchQueryJQL, String[] fields) {
        if (fields == null || fields.length == 0) {
            return Collections.emptyList();
        }

        // Flatten any comma-separated values inside individual array elements.
        // This handles the common CLI case where the user passes a single quoted
        // string like "key,summary,Acceptance Criterias" instead of multiple args.
        String[] flatFields = Arrays.stream(fields)
                .flatMap(f -> Arrays.stream(f.split(",")))
                .map(String::trim)
                .filter(f -> !f.isEmpty())
                .toArray(String[]::new);

        // Try to extract project key from JQL query
        String projectKey = extractProjectKeyFromJQL(searchQueryJQL);

        // If we still couldn't extract a project key, use the first known project key
        // (getFields calls /field which is a global endpoint, any project key works)
        if (projectKey.isEmpty()) {
            List<String> knownKeys = getKnownProjectKeys();
            if (!knownKeys.isEmpty()) {
                projectKey = knownKeys.get(0);
                logger.debug("Could not extract project key from JQL, using fallback: {}", projectKey);
            }
        }

        if (projectKey.isEmpty()) {
            return Arrays.asList(flatFields);
        }

        // Resolve field names using the extracted project key
        List<String> resolvedFields = resolveFieldNames(flatFields, projectKey);

        return resolvedFields;
    }

    /**
     * Coerces a field value received as a String (e.g. from CLI positional args) to the
     * appropriate JSON-compatible type.  Without this, numeric fields like Story Points
     * receive "8" (JSON string) and Jira responds with HTTP 400.
     *
     * IMPORTANT: JSON object/array detection uses JSONTokener to verify the ENTIRE string
     * is consumed, preventing Jira wiki macros like "{code:mermaid}\n...\n{code}" from
     * being mis-parsed as {"code":"mermaid"} (org.json accepts unquoted keys and stops
     * at the first closing brace, silently ignoring the rest of the content).
     */
    static Object coerceFieldValue(Object value) {
        if (!(value instanceof String)) {
            return value;
        }
        String str = ((String) value).trim();
        if (str.isEmpty()) {
            return value;
        }
        // Boolean
        if ("true".equalsIgnoreCase(str)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(str)) return Boolean.FALSE;
        // Integer
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {}
        // Long (for values that don't fit in int)
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException ignored) {}
        // Double
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException ignored) {}
        // JSON object — require the FULL string to be consumed (nextClean()==0 means end-of-input)
        if (str.startsWith("{") && str.endsWith("}")) {
            try {
                JSONTokener tokener = new JSONTokener(str);
                JSONObject obj = new JSONObject(tokener);
                if (tokener.nextClean() == 0) {
                    return obj;
                }
            } catch (Exception ignored) {}
        }
        // JSON array — same strict full-consumption check
        if (str.startsWith("[") && str.endsWith("]")) {
            try {
                JSONTokener tokener = new JSONTokener(str);
                JSONArray arr = new JSONArray(tokener);
                if (tokener.nextClean() == 0) {
                    return arr;
                }
            } catch (Exception ignored) {}
        }
        return value;
    }

    private String performFieldUpdate(String key, String projectKey, String fieldIdentifier, Object value, String errorMessage) throws IOException {
        Object normalizedValue = normalizeFieldUpdateValue(projectKey, fieldIdentifier, value);

        GenericRequest jiraRequest = getTicket(key);
        JSONObject body = new JSONObject();
        body.put("update", new JSONObject()
                .put(fieldIdentifier, new JSONArray()
                        .put(new JSONObject()
                                .put("set", normalizedValue)
                        )));
        jiraRequest.setBody(body.toString());

        String updateResult = jiraRequest.put();
        validateFieldUpdateResponse(updateResult, errorMessage);
        return updateResult;
    }

    private void validateFieldUpdateResponse(String updateResult, String defaultMessage) throws IOException {
        if (updateResult == null || updateResult.trim().isEmpty()) {
            return;
        }

        String trimmedResult = updateResult.trim();
        if (!trimmedResult.startsWith("{")) {
            return;
        }

        try {
            checkJiraResponseForErrors(new JSONObject(trimmedResult), defaultMessage);
        } catch (JSONException ignored) {
            // Non-JSON success bodies should pass through unchanged.
        }
    }

    private Object normalizeFieldUpdateValue(String projectKey, String fieldIdentifier, Object value) throws IOException {
        if (fieldIdentifier == null || !fieldIdentifier.startsWith("customfield_")) {
            return value;
        }

        JSONObject fieldDefinition = findFieldDefinition(projectKey, fieldIdentifier);
        if (!isMultiOptionField(fieldDefinition)) {
            return value;
        }
        return normalizeMultiOptionValue(value);
    }

    private JSONObject findFieldDefinition(String projectKey, String fieldIdentifier) throws IOException {
        if (projectKey == null || projectKey.isEmpty() || fieldIdentifier == null || fieldIdentifier.isEmpty()) {
            return null;
        }

        String response = getFields(projectKey);
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        String trimmedResponse = response.trim();
        try {
            if (trimmedResponse.startsWith("[")) {
                return findFieldDefinitionInArray(new JSONArray(trimmedResponse), fieldIdentifier);
            }
            if (trimmedResponse.startsWith("{")) {
                return findFieldDefinitionInCreateMeta(new JSONObject(trimmedResponse), fieldIdentifier);
            }
        } catch (JSONException e) {
            log("Failed to inspect field metadata for '" + fieldIdentifier + "': " + e.getMessage());
        }
        return null;
    }

    private JSONObject findFieldDefinitionInArray(JSONArray fields, String fieldIdentifier) {
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field != null && fieldMatches(field, fieldIdentifier)) {
                return field;
            }
        }
        return null;
    }

    private JSONObject findFieldDefinitionInCreateMeta(JSONObject createMetaResponse, String fieldIdentifier) {
        JSONArray projects = createMetaResponse.optJSONArray("projects");
        if (projects == null) {
            return null;
        }

        for (int i = 0; i < projects.length(); i++) {
            JSONObject project = projects.optJSONObject(i);
            if (project == null) {
                continue;
            }

            JSONArray issueTypes = project.optJSONArray("issuetypes");
            if (issueTypes == null) {
                continue;
            }

            for (int j = 0; j < issueTypes.length(); j++) {
                JSONObject issueType = issueTypes.optJSONObject(j);
                if (issueType == null) {
                    continue;
                }

                JSONObject fields = issueType.optJSONObject("fields");
                if (fields == null) {
                    continue;
                }

                JSONObject fieldById = fields.optJSONObject(fieldIdentifier);
                if (fieldById != null) {
                    return fieldById;
                }

                for (String key : fields.keySet()) {
                    JSONObject field = fields.optJSONObject(key);
                    if (field != null && fieldMatches(field, fieldIdentifier)) {
                        return field;
                    }
                }
            }
        }
        return null;
    }

    private boolean fieldMatches(JSONObject field, String fieldIdentifier) {
        return fieldIdentifier.equals(field.optString("id"))
                || fieldIdentifier.equalsIgnoreCase(field.optString("name"));
    }

    private boolean isMultiOptionField(JSONObject fieldDefinition) {
        if (fieldDefinition == null) {
            return false;
        }

        JSONObject schema = fieldDefinition.optJSONObject("schema");
        if (schema == null) {
            return false;
        }

        String custom = schema.optString("custom", "").toLowerCase(Locale.ROOT);
        String type = schema.optString("type", "").toLowerCase(Locale.ROOT);
        String items = schema.optString("items", "").toLowerCase(Locale.ROOT);

        return custom.contains(":multiselect")
                || custom.contains(":checkboxes")
                || ("array".equals(type) && "option".equals(items));
    }

    private Object normalizeMultiOptionValue(Object value) {
        if (value instanceof JSONArray) {
            JSONArray input = (JSONArray) value;
            JSONArray normalized = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                normalized.put(normalizeMultiOptionEntry(input.get(i)));
            }
            return normalized;
        }

        if (value instanceof Collection<?>) {
            JSONArray normalized = new JSONArray();
            for (Object entry : (Collection<?>) value) {
                normalized.put(normalizeMultiOptionEntry(entry));
            }
            return normalized;
        }

        return new JSONArray().put(normalizeMultiOptionEntry(value));
    }

    private Object normalizeMultiOptionEntry(Object value) {
        if (value instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) value;
            if (jsonObject.has("id") || jsonObject.has("value") || jsonObject.has("name")) {
                return jsonObject;
            }
        }

        return new JSONObject().put("value", value);
    }

    @MCPTool(
            name = "jira_update_field",
            description = "Update field(s) in a Jira ticket. When using field names (e.g., 'Dependencies'), updates ALL fields with that name. When using custom field IDs (e.g., 'customfield_10091'), updates only that specific field.",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateField(@MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key,
                            @MCPParam(name = "field", description = "The field to update. Use field name (e.g., 'Dependencies') to update ALL fields with that name, or custom field ID (e.g., 'customfield_10091') to update specific field", required = true) String field,
                            @MCPParam(name = "value", description = "The new value for the field(s)", required = true) Object value) throws IOException {
        if ("".equals(value)) {
            return clearField(key, field);
        }
        value = coerceFieldValue(value);

        // Check if field is a custom field ID or a field name
        boolean isCustomFieldId = field.startsWith("customfield_");

        // Extract project key from ticket key for field resolution
        String projectKey = parseJiraProject(key);

        // Skip resolution for system fields and custom field IDs
        if (isCustomFieldId || SYSTEM_FIELDS.contains(field.toLowerCase())) {
            // Update single field - either system field or specific custom field ID
            String updateResult = performFieldUpdate(
                    key,
                    projectKey,
                    field,
                    value,
                    "Failed to update field '" + field + "' on ticket " + key
            );

            if (updateResult == null || updateResult.trim().isEmpty()) {
                log("Updated field '" + field + "' on ticket " + key + " with value: " + value);
                return "Field '" + field + "' updated successfully on ticket " + key;
            }
            log("Updated field '" + field + "' on ticket " + key + " with value: " + value);
            return updateResult;
        } else {
            // Field is a name - update ALL fields with this name
            List<String> fieldIds = getAllFieldCustomCodes(projectKey, field);

            if (fieldIds.isEmpty()) {
                // Try to resolve as single field for backward compatibility
                try {
                    String customFieldId = resolveFieldNameToCustomFieldId(projectKey, field);
                    if (customFieldId != null) {
                        fieldIds = java.util.Collections.singletonList(customFieldId);
                    } else {
                        return "No fields found with name '" + field + "'";
                    }
                } catch (Exception e) {
                    return "Failed to resolve field '" + field + "': " + e.getMessage();
                }
            }

            // Update all matching fields
            StringBuilder results = new StringBuilder();
            int successCount = 0;
            int failureCount = 0;

            for (String fieldId : fieldIds) {
                try {
                    performFieldUpdate(
                            key,
                            projectKey,
                            fieldId,
                            value,
                            "Failed to update field '" + fieldId + "' (name: '" + field + "') on ticket " + key
                    );

                    log("Updated field '" + fieldId + "' (name: '" + field + "') on ticket " + key);
                    if (fieldIds.size() > 1) {
                        results.append("✅ Updated ").append(fieldId).append("\n");
                    }
                    successCount++;
                } catch (Exception e) {
                    log("Failed to update field '" + fieldId + "': " + e.getMessage());
                    if (fieldIds.size() > 1) {
                        results.append("❌ Failed ").append(fieldId).append(": ").append(e.getMessage()).append("\n");
                    }
                    failureCount++;
                }
            }

            // Build response message
            if (fieldIds.size() == 1) {
                // Single field - simple message
                if (successCount == 1) {
                    return "Field '" + field + "' updated successfully on ticket " + key;
                } else {
                    return "Failed to update field '" + field + "' on ticket " + key;
                }
            } else {
                // Multiple fields - detailed message
                results.append("\nUpdated ").append(successCount).append(" of ").append(fieldIds.size())
                       .append(" fields with name '").append(field).append("' for ticket ").append(key);
                if (failureCount > 0) {
                    results.append(" (").append(failureCount).append(" failed)");
                }
                return results.toString();
            }
        }
    }

    @MCPTool(
            name = "jira_update_all_fields_with_name",
            description = "Update ALL fields with the same name in a Jira ticket. Useful when there are multiple custom fields with the same display name.",
            integration = "jira",
            category = "ticket_management"
    )
    public String updateAllFieldsWithName(
            @MCPParam(name = "key", description = "The Jira ticket key to update", required = true) String key,
            @MCPParam(name = "fieldName", description = "The user-friendly field name (e.g., 'Dependencies')", required = true) String fieldName,
            @MCPParam(name = "value", description = "The new value for the fields", required = true) Object value) throws IOException {

        value = coerceFieldValue(value);

        // Extract project key from ticket key for field resolution
        String projectKey = parseJiraProject(key);

        // Get all fields with this name
        List<String> fieldIds = getAllFieldCustomCodes(projectKey, fieldName);

        if (fieldIds.isEmpty()) {
            return "No fields found with name '" + fieldName + "'";
        }

        StringBuilder results = new StringBuilder();
        int successCount = 0;
        int failureCount = 0;

        // Update each field
        for (String fieldId : fieldIds) {
            try {
                performFieldUpdate(
                        key,
                        projectKey,
                        fieldId,
                        value,
                        "Failed to update field '" + fieldId + "' (name: '" + fieldName + "') on ticket " + key
                );

                log("Updated field '" + fieldId + "' (name: '" + fieldName + "') on ticket " + key);
                results.append("✅ Updated ").append(fieldId).append("\n");
                successCount++;
            } catch (Exception e) {
                log("Failed to update field '" + fieldId + "': " + e.getMessage());
                results.append("❌ Failed ").append(fieldId).append(": ").append(e.getMessage()).append("\n");
                failureCount++;
            }
        }

        results.append("\nSummary: Updated ").append(successCount).append(" of ").append(fieldIds.size())
               .append(" fields with name '").append(fieldName).append("'");

        if (failureCount > 0) {
            results.append(" (").append(failureCount).append(" failed)");
        }

        return results.toString();
    }

    @MCPTool(
            name = "jira_get_all_fields_with_name",
            description = "Get all custom field IDs that have the same display name in a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public String getAllFieldsWithName(
            @MCPParam(name = "project", description = "The Jira project key", required = true) String project,
            @MCPParam(name = "fieldName", description = "The user-friendly field name", required = true) String fieldName) throws IOException {

        List<String> fieldIds = getAllFieldCustomCodes(project, fieldName);

        if (fieldIds.isEmpty()) {
            return "No fields found with name '" + fieldName + "'";
        }

        JSONObject result = new JSONObject();
        result.put("fieldName", fieldName);
        result.put("fieldIds", new JSONArray(fieldIds));
        result.put("count", fieldIds.size());

        if (fieldIds.size() > 1) {
            result.put("warning", "Multiple fields found with the same name. Consider using jira_update_all_fields_with_name to update all of them.");
        }

        return result.toString(2);
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

    @MCPTool(
            name = "jira_attach_file_to_ticket",
            description = "Attach a file to a Jira ticket from a local file path. The file will only be attached if a file with the same name doesn't already exist",
            integration = "jira",
            category = "file_management"
    )
    public JSONObject attachFileToTicket(@MCPParam(name = "ticketKey", description = "The Jira ticket key to attach the file to", required = true, example = "PRJ-123") String ticketKey,
                                   @MCPParam(name = "name", description = "The name of the file to attach", required = true, example = "document.pdf") String name,
                                   @MCPParam(name = "contentType", description = "The content type of the file (e.g., 'application/pdf', 'image/png'). If not provided, defaults to 'image/*'", required = false, example = "application/pdf") String contentType,
                                   @MCPParam(name = "filePath", description = "Absolute path to the file on disk", required = true, example = "/tmp/document.pdf") String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filePath);
        }
        attachFileToTicket(ticketKey, name, contentType, file);
        return new JSONObject()
                .put("status", SUCCESS)
                .put("message", "File '" + name + "' attached to ticket " + ticketKey)
                .put("ticket", ticketKey)
                .put("fileName", name);
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
                    Thread.currentThread().interrupt();
                    logger.error("Unexpected error", e);
                }
            }

            int attemptNumber = 1;
            IOException lastException = null;
            Response lastResponse = null;

            while (attemptNumber <= retryPolicy.getMaxRetries()) {
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
                            lastResponse = response;
                            IOException exception = AtlassianRestClient.printAndCreateException(request, response);

                            // Check if it's a rate limit or retryable error
                            if (retryPolicy.isRetryable(exception) && attemptNumber < retryPolicy.getMaxRetries()) {
                                retryPolicy.logRetryAttempt(attemptNumber, url, exception);
                                long delayMs = retryPolicy.calculateDelayMs(attemptNumber, response);
                                try {
                                    retryPolicy.executeDelay(delayMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("Request interrupted during retry", e);
                                }
                                attemptNumber++;
                                continue;
                            } else {
                                throw exception;
                            }
                        }
                    }
                } catch (SocketTimeoutException | ConnectException e) {
                    lastException = e;
                    log("Connection error for URL: " + url + " - " + e.getMessage());

                    if (isRepeatIfFails && attemptNumber < retryPolicy.getMaxRetries()) {
                        retryPolicy.logRetryAttempt(attemptNumber, url, new IOException("Connection error: " + e.getMessage()));

                        // For connection errors, use exponential backoff
                        long waitTime = 200L * (long) Math.pow(2, attemptNumber - 1);
                        try {
                            Thread.sleep(Math.min(waitTime, 5000)); // Cap at 5 seconds for connection errors
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Request interrupted during retry", interruptedException);
                        }
                        attemptNumber++;
                    } else {
                        throw e;
                    }
                } catch (IOException e) {
                    lastException = e;

                    // Check if it's a retryable error (rate limit, service unavailable, etc.)
                    if (retryPolicy.isRetryable(e) && isRepeatIfFails && attemptNumber < retryPolicy.getMaxRetries()) {
                        retryPolicy.logRetryAttempt(attemptNumber, url, e);

                        // For rate limits, use retry policy delay calculation
                        Response response = (e instanceof RestClient.RateLimitException) ?
                            ((RestClient.RateLimitException)e).getResponse() : null;
                        long delayMs = retryPolicy.calculateDelayMs(attemptNumber, response);

                        try {
                            retryPolicy.executeDelay(delayMs);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Request interrupted during retry", interruptedException);
                        }
                        attemptNumber++;
                    } else {
                        throw e;
                    }
                }
            }

            // If we've exhausted all retries
            if (lastException != null) {
                retryPolicy.logMaxRetriesExceeded(url, lastException);
                throw lastException;
            }

            // This shouldn't be reached, but just in case
            throw new IOException("Unexpected error in request execution");

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
                Thread.currentThread().interrupt();
                logger.error("Unexpected error", e);
            }
        }

        int attemptNumber = 1;
        IOException lastException = null;

        while (attemptNumber <= retryPolicy.getMaxRetries()) {
            RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
            Request request = sign(new Request.Builder())
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("Request performed successfully!");
                    return response.body() != null ? response.body().string() : null;
                } else {
                    int code = response.code();

                    // Check if it's a rate limit error
                    if (code == 429 || code == 503) {
                        String responseBodyAsString = response.body() != null ? response.body().string() : "";
                        IOException exception = new RestClient.RateLimitException(
                            "Rate limit or service unavailable: " + code,
                            responseBodyAsString,
                            response,
                            code
                        );

                        if (attemptNumber < retryPolicy.getMaxRetries()) {
                            retryPolicy.logRetryAttempt(attemptNumber, url, exception);
                            long delayMs = retryPolicy.calculateDelayMs(attemptNumber, response);
                            try {
                                retryPolicy.executeDelay(delayMs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException("POST request interrupted during retry", e);
                            }
                            attemptNumber++;
                            continue;
                        } else {
                            retryPolicy.logMaxRetriesExceeded(url, exception);
                            throw exception;
                        }
                    }

                    // For non-retryable errors, log and return the response
                    logger.info("Error in POST request. Response code: {}", code);
                    ResponseBody responseBody = response.body();
                    String responseBodyAsString = responseBody != null ? responseBody.string() : "";
                    if (responseBody != null) {
                        logger.error("Response body: {}", responseBodyAsString);
                    }
                    return responseBodyAsString;
                }
            } catch (IOException e) {
                lastException = e;

                // Check if it's a retryable error
                if (retryPolicy.isRetryable(e) && attemptNumber < retryPolicy.getMaxRetries()) {
                    retryPolicy.logRetryAttempt(attemptNumber, url, e);

                    Response response = (e instanceof RestClient.RateLimitException) ?
                        ((RestClient.RateLimitException)e).getResponse() : null;
                    long delayMs = retryPolicy.calculateDelayMs(attemptNumber, response);

                    try {
                        retryPolicy.executeDelay(delayMs);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IOException("POST request interrupted during retry", interruptedException);
                    }
                    attemptNumber++;
                } else {
                    throw e;
                }
            } finally {
                closeAllConnections();
            }
        }

        // If we've exhausted all retries
        if (lastException != null) {
            retryPolicy.logMaxRetriesExceeded(url, lastException);
            throw lastException;
        }

        // This shouldn't be reached, but just in case
        throw new IOException("Unexpected error in POST request execution");
    }

    @Override
    public String patch(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Unexpected error", e);
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
                String sanitizedBody = sanitizeRequestBodyForLogging(genericRequest.getBody());
                logger.error("Error code {} {} {}", response.code(), sanitizedBody, genericRequest.url());
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
                Thread.currentThread().interrupt();
                logger.error("Unexpected error", e);
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
                String sanitizedBody = sanitizeRequestBodyForLogging(genericRequest.getBody());
                logger.error("Error code {} {} {}", response.code(), sanitizedBody, genericRequest.url());
                return response.body() != null ? response.body().string() : null;
            }
        } finally {
            closeAllConnections();
        }
    }
    
    /**
     * Sanitizes request body for logging by filtering out base64-encoded images.
     * Prevents large base64 strings from cluttering logs.
     * 
     * @param body the request body to sanitize
     * @return sanitized body with base64 images replaced with placeholders
     */
    private String sanitizeRequestBodyForLogging(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        // Filter base64 image data from JSON
        // Pattern 1: "data": "base64string..." (Anthropic format)
        String sanitized = body.replaceAll("\"data\"\\s*:\\s*\"[a-zA-Z0-9+/=]{100,}\"", "\"data\": \"[Base64 image data redacted]\"");
        // Pattern 2: "url": "data:image/...;base64,..." (Ollama/Dial format)
        sanitized = sanitized.replaceAll("\"url\"\\s*:\\s*\"data:image/[^;]+;base64,[a-zA-Z0-9+/=]+\"", "\"url\": \"data:image/[redacted];base64,[Base64 image data redacted]\"");
        // Pattern 3: "image_url": {"url": "data:image/...;base64,..."} (OpenAI format)
        sanitized = sanitized.replaceAll("\"image_url\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"data:image/[^;]+;base64,[a-zA-Z0-9+/=]+\"[^}]*\\}", "\"image_url\": {\"url\": \"data:image/[redacted];base64,[Base64 image data redacted]\"}");
        // Pattern 4: Standalone data:image URLs
        sanitized = sanitized.replaceAll("data:image/[^;]+;base64,[a-zA-Z0-9+/=]{100,}", "data:image/[redacted];base64,[Base64 image data redacted]");
        return sanitized;
    }

    @Override
    public String delete(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(sleepTimeRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Unexpected error", e);
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
            category = "ticket_management",
            aliases = {"tracker_move_to_status"}
    )
    public String moveToStatus(@MCPParam(name = "key", description = "The Jira ticket key to move", required = true) String ticketKey, 
                             @MCPParam(name = "statusName", description = "The target status name", required = true) String statusName) throws IOException {
        List<Transition> transitions = getTransitions(ticketKey);
        if (transitions != null) {
            for (Transition transition : transitions) {
                if (transition.getValue().equalsIgnoreCase(statusName)
                        || statusName.equalsIgnoreCase(transition.getToStatusName())) {
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
                if (transition.getValue().equalsIgnoreCase(statusName)
                        || statusName.equalsIgnoreCase(transition.getToStatusName())) {
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
        logger.info("key: " + key);
        logger.info("priority: " + priority);
        String response = updateTicket(key, params);
        logger.info("response: " + response);
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
            category = "file_management",
            aliases = {"tracker_download_attachment"}
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
                genericRequest.setIgnoreCache(true);
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

        // The /field endpoint returns JSON array format for both Cloud and Server Jira
        log("Using Server Jira parsing for field custom code (works for both Cloud and Server)");
        return parseServerJiraResponse(fieldName, response);
    }

    @Override
    public String resolveFieldName(String ticketKey, String fieldName) throws IOException {
        // Extract project key from ticket key (e.g., "PROJ-123" -> "PROJ")
        String projectKey = parseJiraProject(ticketKey);
        // Use existing method to resolve field name to custom field ID
        String resolvedField = getFieldCustomCode(projectKey, fieldName);
        // If resolution failed, return original field name as fallback
        return resolvedField != null ? resolvedField : fieldName;
    }

    public String parseServerJiraResponse(String fieldName, String jsonResponse) {
        try {
            // Use the new strategy to handle multiple fields with the same name
            List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> matchingFields =
                com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName(fieldName, jsonResponse);

            if (matchingFields.isEmpty()) {
                return null;
            }

            // Select the best field using the strategy
            // Note: This method is kept for backward compatibility
            // The updateField method now uses getAllFieldCustomCodes to update ALL matching fields
            // And resolveFieldNames now uses getAllFieldCustomCodes to include ALL matching fields in JQL
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField bestField =
                com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.selectBestField(matchingFields);

            // Don't log selection here - it's misleading since we now handle multiple fields in other places
            // Logging happens in updateField which updates ALL fields

            return bestField != null ? bestField.getId() : null;

        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON response: " + e.getMessage());
        }
    }

    /**
     * Get ALL custom field IDs that match the given field name
     * This is useful when you need to update all instances of a field with the same name
     *
     * @param project The Jira project key
     * @param fieldName The human-readable field name
     * @return List of all matching custom field IDs
     */
    public List<String> getAllFieldCustomCodes(String project, String fieldName) throws IOException {
        String response = getFields(project);
        List<com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField> matchingFields =
            com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.findAllFieldsByName(fieldName, response);

        List<String> fieldIds = new java.util.ArrayList<>();
        for (com.github.istin.dmtools.atlassian.jira.strategy.MultiFieldUpdateStrategy.CustomField field : matchingFields) {
            if (field.isActive()) {
                fieldIds.add(field.getId());
            }
        }

        return fieldIds;
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
            category = "ticket_management",
            aliases = {"tracker_link_tickets"}
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

    /**
     * Sets a custom retry policy for handling rate limits and transient failures.
     * @param retryPolicy The retry policy to use, or null to use default policy
     */
    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicyConfig.fromEnvironment(this.logger);
    }

    /**
     * Gets the current retry policy.
     * @return The current retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    // ─── Project structure / scheme API ───────────────────────────────────────

    /**
     * Executes a GET request against a full URL and returns the response body.
     * Extracted as a protected method to allow overriding in tests.
     */
    protected String executeGet(String url) throws IOException {
        GenericRequest request = new GenericRequest(this, url);
        return request.execute();
    }

    /**
     * Executes a PUT request against a full URL with the given JSON body.
     * Extracted as a protected method to allow overriding in tests.
     */
    protected void executePut(String url, String body) throws IOException {
        GenericRequest request = new GenericRequest(this, url);
        request.setBody(body);
        request.put();
    }

    /**
     * Executes a POST request against a full URL with the given JSON body and returns the response body.
     * Throws IOException if the response contains Jira error messages.
     * Extracted as a protected method to allow overriding in tests.
     */
    protected String executePost(String url, String body) throws IOException {
        GenericRequest request = new GenericRequest(this, url);
        request.setBody(body);
        String response = request.post();
        if (response != null && (response.contains("errorMessages") || response.contains("\"errors\":"))) {
            try {
                JSONObject errorJson = new JSONObject(response);
                JSONArray errorMessages = errorJson.optJSONArray("errorMessages");
                if (errorMessages != null && !errorMessages.isEmpty()) {
                    throw new IOException(errorMessages.getString(0));
                }
                JSONObject errorsObj = errorJson.optJSONObject("errors");
                if (errorsObj != null && errorsObj.length() > 0) {
                    // Collect all error values into one message
                    java.util.StringJoiner sj = new java.util.StringJoiner("; ");
                    for (String errKey : errorsObj.keySet()) {
                        sj.add(errorsObj.getString(errKey));
                    }
                    throw new IOException(sj.toString());
                }
            } catch (IOException rethrow) {
                throw rethrow;
            } catch (Exception ignore) {
                // not a well-formed error response — let caller decide
            }
        }
        return response;
    }

    /**
     * Returns full project details (id, key, name, style, issueTypes) from the Jira project endpoint.
     */
    @MCPTool(
            name = "jira_get_project_details",
            description = "Get full details of a Jira project including its numeric ID, key, name, style and issue types",
            integration = "jira",
            category = "project_management"
    )
    public Project getProjectDetails(
            @MCPParam(name = "projectKey", description = "The Jira project key, e.g. TP", required = true) String projectKey) throws IOException {
        String response = executeGet(path("project/" + projectKey));
        return new Project(new JSONObject(response));
    }

    /**
     * Returns the issue type scheme for a project.
     * For classic (company-managed) projects uses the admin scheme API.
     * For next-gen (team-managed) projects falls back to the issue types already embedded
     * in the project details response — no admin permission required.
     */
    @MCPTool(
            name = "jira_get_project_issue_type_scheme",
            description = "Get the issue type scheme (or equivalent) assigned to a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public IssueTypeScheme getProjectIssueTypeScheme(
            @MCPParam(name = "projectKey", description = "The Jira project key, e.g. TP", required = true) String projectKey) throws IOException {
        Project project = getProjectDetails(projectKey);
        String projectId = project.getId();

        // Classic projects: use the admin scheme API
        if (!project.isNextGen()) {
            String url = getBasePath() + "/rest/api/3/issuetypescheme/project?projectId=" + projectId;
            String response = executeGet(url);
            JSONObject json = new JSONObject(response);
            JSONArray values = json.optJSONArray("values");
            if (values != null && !values.isEmpty()) {
                return new IssueTypeScheme(values.getJSONObject(0).optJSONObject("issueTypeScheme"));
            }
            throw new IOException("No issue type scheme found for project: " + projectKey);
        }

        // Next-gen projects: build a pseudo-scheme from the issue types in project details
        log("Next-gen project detected — building issue type scheme from project details for " + projectKey);
        JSONArray issueTypesJson = project.getIssueTypesJson();
        JSONArray ids = new JSONArray();
        if (issueTypesJson != null) {
            for (int i = 0; i < issueTypesJson.length(); i++) {
                ids.put(issueTypesJson.getJSONObject(i).getString("id"));
            }
        }
        JSONObject schemeJson = new JSONObject()
                .put("id", projectId)
                .put("name", project.getName() + " Issue Type Scheme")
                .put("issueTypeIds", ids);
        return new IssueTypeScheme(schemeJson);
    }

    /**
     * Returns the workflow scheme for a project.
     * For classic (company-managed) projects uses the admin scheme API.
     * For next-gen (team-managed) projects falls back to reading the project statuses
     * endpoint — no admin permission required.
     */
    @MCPTool(
            name = "jira_get_project_workflow_scheme",
            description = "Get the workflow scheme (or equivalent) assigned to a Jira project",
            integration = "jira",
            category = "project_management"
    )
    public WorkflowScheme getProjectWorkflowScheme(
            @MCPParam(name = "projectKey", description = "The Jira project key, e.g. TP", required = true) String projectKey) throws IOException {
        Project project = getProjectDetails(projectKey);
        String projectId = project.getId();

        // Classic projects: use the admin scheme API
        if (!project.isNextGen()) {
            String url = getBasePath() + "/rest/api/3/workflowscheme/project?projectId=" + projectId;
            String response = executeGet(url);
            JSONObject json = new JSONObject(response);
            JSONArray values = json.optJSONArray("values");
            if (values != null && !values.isEmpty()) {
                return new WorkflowScheme(values.getJSONObject(0).optJSONObject("workflowScheme"));
            }
            throw new IOException("No workflow scheme found for project: " + projectKey);
        }

        // Next-gen projects: derive a pseudo workflow scheme from the project statuses
        log("Next-gen project detected — building workflow scheme from project statuses for " + projectKey);
        String statusesUrl = getBasePath() + "/rest/api/3/project/" + projectKey + "/statuses";
        String response = executeGet(statusesUrl);
        JSONArray statusesArray = new JSONArray(response);
        JSONObject mappings = new JSONObject();
        for (int i = 0; i < statusesArray.length(); i++) {
            JSONObject issueTypeEntry = statusesArray.getJSONObject(i);
            String issueTypeId = issueTypeEntry.optString("id");
            JSONArray statuses = issueTypeEntry.optJSONArray("statuses");
            if (statuses != null && !statuses.isEmpty()) {
                mappings.put(issueTypeId, statuses.getJSONObject(0).optString("name"));
            }
        }
        JSONObject schemeJson = new JSONObject()
                .put("id", projectId)
                .put("name", project.getName() + " Workflow")
                .put("issueTypeMappings", mappings);
        return new WorkflowScheme(schemeJson);
    }

    /**
     * Assigns an existing issue type scheme to a classic (company-managed) target project.
     * Requires Jira admin permission.
     */
    @MCPTool(
            name = "jira_assign_issue_type_scheme",
            description = "Assign an issue type scheme (by its numeric ID) to a classic Jira project (by its numeric project ID). Requires Jira admin.",
            integration = "jira",
            category = "project_management"
    )
    public void assignIssueTypeSchemeToProject(
            @MCPParam(name = "issueTypeSchemeId", description = "Numeric ID of the issue type scheme", required = true) String issueTypeSchemeId,
            @MCPParam(name = "projectId", description = "Numeric ID of the target Jira project", required = true) String projectId) throws IOException {
        String url = getBasePath() + "/rest/api/3/issuetypescheme/" + issueTypeSchemeId + "/project";
        JSONObject body = new JSONObject();
        body.put("projectIds", new JSONArray().put(projectId));
        executePut(url, body.toString());
    }

    /**
     * Assigns an existing workflow scheme to a classic (company-managed) target project.
     * Requires Jira admin permission.
     */
    @MCPTool(
            name = "jira_assign_workflow_scheme",
            description = "Assign a workflow scheme (by its numeric ID) to a classic Jira project (by its numeric project ID). Requires Jira admin.",
            integration = "jira",
            category = "project_management"
    )
    public void assignWorkflowSchemeToProject(
            @MCPParam(name = "workflowSchemeId", description = "Numeric ID of the workflow scheme", required = true) String workflowSchemeId,
            @MCPParam(name = "projectId", description = "Numeric ID of the target Jira project", required = true) String projectId) throws IOException {
        String url = getBasePath() + "/rest/api/3/workflowscheme/project";
        JSONObject body = new JSONObject()
                .put("workflowSchemeId", workflowSchemeId)
                .put("projectId", projectId);
        executePut(url, body.toString());
    }

    /**
     * Creates a single project-scoped issue type in a Jira next-gen project.
     * Skips silently if an issue type with the same name already exists.
     */
    @MCPTool(
            name = "jira_create_project_issue_type",
            description = "Create a project-scoped issue type in a next-gen Jira project. Use type 'subtask' for subtasks and 'standard' for all others. Skips if the name already exists. Requires Jira admin.",
            integration = "jira",
            category = "project_management"
    )
    public String createProjectIssueType(
            @MCPParam(name = "projectKey", description = "Key of the target Jira project, e.g. TP", required = true) String projectKey,
            @MCPParam(name = "name", description = "Name of the issue type, e.g. Story", required = true) String name,
            @MCPParam(name = "type", description = "Issue type kind: 'standard' or 'subtask'. Defaults to 'standard'.", required = false) String type,
            @MCPParam(name = "description", description = "Optional description", required = false) String description) throws IOException {
        Project project = getProjectDetails(projectKey);
        String projectId = project.getId();

        JSONArray existing = project.getIssueTypesJson();
        if (existing != null) {
            for (int i = 0; i < existing.length(); i++) {
                if (name.equalsIgnoreCase(existing.getJSONObject(i).optString("name"))) {
                    return new JSONObject().put("name", name).put("status", "exists").toString();
                }
            }
        }

        String issueTypeKind = (type != null && "subtask".equalsIgnoreCase(type.trim())) ? "subtask" : "standard";
        JSONObject body = new JSONObject()
                .put("name", name)
                .put("description", description != null ? description : "")
                .put("type", issueTypeKind)
                .put("scope", new JSONObject()
                        .put("type", "PROJECT")
                        .put("project", new JSONObject().put("id", projectId)));
        try {
            String response = executePost(getBasePath() + "/rest/api/3/issuetype", body.toString());
            String newId = new JSONObject(response).optString("id", "");
            log("Created issue type '" + name + "' (id=" + newId + ") in " + projectKey);
            return new JSONObject().put("name", name).put("status", "created").put("id", newId).toString();
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errMsg.toLowerCase().contains("already exists") || errMsg.toLowerCase().contains("already in use")) {
                return new JSONObject().put("name", name).put("status", "exists").toString();
            }
            throw new IOException("Failed to create issue type '" + name + "': " + errMsg);
        }
    }

    /**
     * Copies issue types from source project to a target next-gen project by creating
     * project-scoped issue types for any names that are missing in the target.
     * Uses {@code POST /rest/api/3/issuetype} with project scope — requires Jira admin.
     *
     * @return JSONArray with one entry per source issue type: name, status (created/exists/failed), id
     */
    private JSONArray copyIssueTypesToNextGenProject(JSONArray sourceIssueTypes, String targetProjectKey, String targetProjectId) {
        JSONArray targetIssueTypes;
        try {
            targetIssueTypes = getProjectDetails(targetProjectKey).getIssueTypesJson();
        } catch (Exception e) {
            targetIssueTypes = new JSONArray();
        }

        java.util.Set<String> existingNames = new java.util.HashSet<>();
        if (targetIssueTypes != null) {
            for (int i = 0; i < targetIssueTypes.length(); i++) {
                existingNames.add(targetIssueTypes.getJSONObject(i).optString("name").toLowerCase());
            }
        }

        JSONArray results = new JSONArray();
        for (int i = 0; i < sourceIssueTypes.length(); i++) {
            JSONObject srcType = sourceIssueTypes.getJSONObject(i);
            String name = srcType.optString("name");
            JSONObject entry = new JSONObject().put("name", name);

            if (existingNames.contains(name.toLowerCase())) {
                entry.put("status", "exists");
                log("Issue type '" + name + "' already exists in target — skipping");
            } else {
                try {
                    JSONObject body = new JSONObject()
                            .put("name", name)
                            .put("description", srcType.optString("description", ""))
                            .put("type", srcType.optBoolean("subtask") ? "subtask" : "standard")
                            .put("scope", new JSONObject()
                                    .put("type", "PROJECT")
                                    .put("project", new JSONObject().put("id", targetProjectId)));
                    String response = executePost(getBasePath() + "/rest/api/3/issuetype", body.toString());
                    String newId = new JSONObject(response).optString("id", "");
                    entry.put("status", "created").put("id", newId);
                    log("Created issue type '" + name + "' with id=" + newId);
                } catch (Exception e) {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "";
                    if (errMsg.toLowerCase().contains("already exists") || errMsg.toLowerCase().contains("already in use")) {
                        entry.put("status", "exists");
                        log("Issue type '" + name + "' already exists (different scope) — treating as exists");
                    } else {
                        entry.put("status", "failed").put("error", errMsg);
                        log("Failed to create issue type '" + name + "': " + errMsg);
                    }
                }
            }
            results.put(entry);
        }
        return results;
    }

    /**
     * Copies the issue type setup and workflow from a source project to a target project.
     *
     * <ul>
     *   <li><b>Next-gen (team-managed)</b>: creates missing project-scoped issue types in target
     *       using {@code POST /rest/api/3/issuetype} — requires Jira global admin.
     *       Already-existing issue types (by name) are skipped.</li>
     *   <li><b>Classic (company-managed)</b>: reassigns issue type scheme and workflow scheme —
     *       also requires Jira global admin.</li>
     * </ul>
     *
     * Returns a JSON summary including per-type status (created / exists / failed).
     */
    @MCPTool(
            name = "jira_copy_project_structure",
            description = "Copy issue types and workflow setup from a source Jira project to a target Jira project (e.g. MYTUBE → TP)",
            integration = "jira",
            category = "project_management"
    )
    public String copyProjectStructure(
            @MCPParam(name = "sourceProjectKey", description = "Key of the source Jira project to copy structure from, e.g. MYTUBE", required = true) String sourceProjectKey,
            @MCPParam(name = "targetProjectKey", description = "Key of the target Jira project to apply structure to, e.g. TP", required = true) String targetProjectKey) throws IOException {
        log("Copying project structure from " + sourceProjectKey + " to " + targetProjectKey);

        Project sourceProject = getProjectDetails(sourceProjectKey);
        Project targetProject = getProjectDetails(targetProjectKey);
        String targetProjectId = targetProject.getId();

        JSONObject result = new JSONObject()
                .put("sourceProject", sourceProjectKey)
                .put("targetProject", targetProjectKey)
                .put("sourceStyle", sourceProject.getStyle())
                .put("targetStyle", targetProject.getStyle());

        if (sourceProject.isNextGen()) {
            // ── Next-gen path: create project-scoped issue types for missing ones ─
            JSONArray sourceIssueTypes = sourceProject.getIssueTypesJson();
            if (sourceIssueTypes == null || sourceIssueTypes.isEmpty()) {
                throw new IOException("Source project " + sourceProjectKey + " has no issue types");
            }
            log("Source has " + sourceIssueTypes.length() + " issue types; copying to " + targetProjectKey);
            JSONArray typeResults = copyIssueTypesToNextGenProject(sourceIssueTypes, targetProjectKey, targetProjectId);
            result.put("method", "next-gen-create-scoped")
                  .put("issueTypes", typeResults);

        } else {
            // ── Classic path: copy schemes (requires Jira admin) ──────────────
            IssueTypeScheme issueTypeScheme = getProjectIssueTypeScheme(sourceProjectKey);
            log("Source issue type scheme: " + issueTypeScheme);
            assignIssueTypeSchemeToProject(issueTypeScheme.getId(), targetProjectId);
            log("Assigned issue type scheme '" + issueTypeScheme.getName() + "' to " + targetProjectKey);

            WorkflowScheme workflowScheme = getProjectWorkflowScheme(sourceProjectKey);
            log("Source workflow scheme: " + workflowScheme);
            assignWorkflowSchemeToProject(workflowScheme.getId(), targetProjectId);
            log("Assigned workflow scheme '" + workflowScheme.getName() + "' to " + targetProjectKey);

            result.put("method", "classic-scheme")
                  .put("issueTypeSchemeId", issueTypeScheme.getId())
                  .put("issueTypeSchemeName", issueTypeScheme.getName())
                  .put("workflowSchemeId", workflowScheme.getId())
                  .put("workflowSchemeName", workflowScheme.getName());
        }

        return result.toString();
    }

    /**
     * Executes a DELETE request against a full URL and returns the response body (may be empty on 204).
     * Throws IOException if the response contains Jira error messages.
     * Extracted as a protected method to allow overriding in tests.
     */
    protected String executeDelete(String url) throws IOException {
        GenericRequest request = new GenericRequest(this, url);
        String response = request.delete();
        if (response != null && !response.isEmpty() && (response.contains("errorMessages") || response.contains("\"errors\":"))) {
            try {
                JSONObject errorJson = new JSONObject(response);
                JSONArray errorMessages = errorJson.optJSONArray("errorMessages");
                if (errorMessages != null && !errorMessages.isEmpty()) {
                    throw new IOException(errorMessages.getString(0));
                }
                JSONObject errorsObj = errorJson.optJSONObject("errors");
                if (errorsObj != null && errorsObj.length() > 0) {
                    java.util.StringJoiner sj = new java.util.StringJoiner("; ");
                    for (String errKey : errorsObj.keySet()) {
                        sj.add(errorsObj.getString(errKey));
                    }
                    throw new IOException(sj.toString());
                }
            } catch (IOException rethrow) {
                throw rethrow;
            } catch (Exception ignore) {
                // not a well-formed error response — let caller decide
            }
        }
        return response;
    }

    /**
     * Deletes a Jira project (moves it to trash). Set confirmDelete to "true" to proceed.
     * The project can be restored using jira_restore_project.
     * Note: Jira Cloud reserves the project key even after deletion — to permanently free the key,
     * go to Jira Admin → System → Trash and permanently delete from there.
     */
    @MCPTool(
            name = "jira_delete_project",
            description = "Delete a Jira project by moving it to trash. The project key remains reserved until permanently deleted from Jira Admin > System > Trash. Set confirmDelete to 'true' to proceed.",
            integration = "jira",
            category = "project_management"
    )
    public String deleteProject(
            @MCPParam(name = "projectKey", description = "Key of the Jira project to delete, e.g. TP", required = true) String projectKey,
            @MCPParam(name = "confirmDelete", description = "Must be 'true' to confirm deletion", required = true) String confirmDelete) throws IOException {
        if (!"true".equalsIgnoreCase(confirmDelete)) {
            return new JSONObject()
                    .put("success", false)
                    .put("message", "Deletion cancelled — set confirmDelete='true' to proceed")
                    .toString();
        }
        log("Deleting project " + projectKey);
        String deleteResponse = executeDelete(getBasePath() + "/rest/api/3/project/" + projectKey + "?enableUndo=true");
        // 204 returns empty body; any error would throw IOException from restClient
        return new JSONObject()
                .put("success", true)
                .put("projectKey", projectKey)
                .put("message", "Project moved to trash. Key '" + projectKey + "' is reserved until permanently deleted via Jira Admin > System > Trash. Use jira_restore_project to undo.")
                .toString();
    }

    /**
     * Restores a Jira project from trash.
     */
    @MCPTool(
            name = "jira_restore_project",
            description = "Restore a Jira project that was previously moved to trash using jira_delete_project",
            integration = "jira",
            category = "project_management"
    )
    public String restoreProject(
            @MCPParam(name = "projectKey", description = "Key of the trashed Jira project to restore, e.g. TP", required = true) String projectKey) throws IOException {
        log("Restoring project " + projectKey);
        String response = executePost(getBasePath() + "/rest/api/3/project/" + projectKey + "/restore", "");
        JSONObject result = new JSONObject(response);
        return new JSONObject()
                .put("success", true)
                .put("projectKey", result.optString("key", projectKey))
                .put("projectId", result.optString("id", ""))
                .put("projectName", result.optString("name", ""))
                .put("message", "Project restored successfully")
                .toString();
    }

    /**
     * Creates a fresh team-managed (next-gen) Jira project with the same structure as the source.
     * Default issue types (Task, Story, Bug, Epic, Subtask) are automatically created by Jira.
     * Custom types such as "Test Case" must be added manually via Project Settings > Issue Types.
     * <p>
     * To reuse an existing project key: first permanently delete the old project from
     * Jira Admin → System → Trash, then run this tool.
     */
    @MCPTool(
            name = "jira_clone_project",
            description = "Create a brand-new team-managed (next-gen) Jira project that mirrors the source project. Default issue types are auto-created; custom types must be added manually. To reuse an existing key, permanently delete the old project from Jira Admin > System > Trash first.",
            integration = "jira",
            category = "project_management"
    )
    public String cloneProject(
            @MCPParam(name = "sourceProjectKey", description = "Key of the source project to mirror, e.g. MYTUBE", required = true) String sourceProjectKey,
            @MCPParam(name = "newProjectKey", description = "Key for the new project, e.g. TP2", required = true) String newProjectKey,
            @MCPParam(name = "newProjectName", description = "Name for the new project. If empty, uses source project name", required = false) String newProjectName) throws IOException {
        log("Cloning project structure from " + sourceProjectKey + " to new project " + newProjectKey);

        Project source = getProjectDetails(sourceProjectKey);
        String targetName = (newProjectName != null && !newProjectName.isEmpty())
                ? newProjectName : source.getName() + " (Clone)";
        String leadAccountId = source.getLeadAccountId();

        JSONObject body = new JSONObject()
                .put("key", newProjectKey)
                .put("name", targetName)
                .put("projectTypeKey", source.getProjectTypeKey())
                .put("assigneeType", "UNASSIGNED");

        if (source.isNextGen()) {
            body.put("projectTemplateKey", "com.pyxis.greenhopper.jira:gh-simplified-agility-scrum");
        }
        if (leadAccountId != null && !leadAccountId.isEmpty()) {
            body.put("leadAccountId", leadAccountId);
        }

        String response = executePost(getBasePath() + "/rest/api/3/project", body.toString());
        JSONObject created = new JSONObject(response);

        Project newProject = getProjectDetails(newProjectKey);
        JSONArray newTypes = newProject.getIssueTypesJson();
        java.util.List<String> autoCreated = new java.util.ArrayList<>();
        if (newTypes != null) {
            for (int i = 0; i < newTypes.length(); i++) {
                autoCreated.add(newTypes.getJSONObject(i).optString("name"));
            }
        }

        JSONArray sourceTypes = source.getIssueTypesJson();
        java.util.List<String> missingTypes = new java.util.ArrayList<>();
        if (sourceTypes != null) {
            for (int i = 0; i < sourceTypes.length(); i++) {
                String name = sourceTypes.getJSONObject(i).optString("name");
                if (!autoCreated.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                    missingTypes.add(name);
                }
            }
        }

        JSONObject result = new JSONObject()
                .put("success", true)
                .put("projectKey", created.optString("key", newProjectKey))
                .put("projectId", created.optString("id", ""))
                .put("projectName", targetName)
                .put("style", source.isNextGen() ? "next-gen" : "classic")
                .put("autoCreatedIssueTypes", new JSONArray(autoCreated));

        if (!missingTypes.isEmpty()) {
            result.put("missingIssueTypes", new JSONArray(missingTypes))
                  .put("manualSteps", "Add missing issue types manually: Project Settings > Issue Types > Add issue type. Missing: " + String.join(", ", missingTypes));
        } else {
            result.put("message", "Project created successfully — all issue types match source");
        }

        log("Created project " + newProjectKey + " with auto-created types: " + autoCreated);
        if (!missingTypes.isEmpty()) {
            log("Missing types (need manual addition): " + missingTypes);
        }
        return result.toString();
    }

    /**
     * Reads the board column configuration (workflow layout) of a Jira project.
     * Returns columns with their associated status names and categories.
     * Works for both team-managed (next-gen) and classic projects.
     */
    @MCPTool(
            name = "jira_get_project_board_config",
            description = "Read the board column configuration (workflow) of a Jira project — returns columns with status names and categories, useful for comparing or replicating project workflow",
            integration = "jira",
            category = "project_management"
    )
    public String getProjectBoardConfig(
            @MCPParam(name = "projectKey", description = "Key of the Jira project, e.g. MYTUBE", required = true) String projectKey) throws IOException {
        log("Getting board config for project " + projectKey);

        // Find the board for this project
        String boardsUrl = getBasePath() + "/rest/agile/1.0/board?projectKeyOrId=" + projectKey;
        String boardsResponse = executeGet(boardsUrl);
        JSONObject boardsJson = new JSONObject(boardsResponse);
        JSONArray boards = boardsJson.optJSONArray("values");
        if (boards == null || boards.isEmpty()) {
            throw new IOException("No board found for project " + projectKey);
        }
        JSONObject board = boards.getJSONObject(0);
        String boardId = String.valueOf(board.opt("id"));
        String boardName = board.optString("name", "");
        String boardType = board.optString("type", "");

        // Get statuses per issue type (the true workflow)
        String statusesUrl = path("project/" + projectKey + "/statuses");
        String statusesResponse = executeGet(statusesUrl);

        // Parse unique statuses with their categories (from v3 API for scope info)
        java.util.Map<String, String> statusCategoryById = new java.util.LinkedHashMap<>();
        try {
            String v3StatusesUrl = getBasePath() + "/rest/api/3/project/" + projectKey + "/statuses";
            String v3Response = executeGet(v3StatusesUrl);
            JSONArray v3Types = new JSONArray(v3Response);
            if (!v3Types.isEmpty()) {
                JSONArray firstTypeStatuses = v3Types.getJSONObject(0).optJSONArray("statuses");
                if (firstTypeStatuses != null) {
                    for (int i = 0; i < firstTypeStatuses.length(); i++) {
                        JSONObject s = firstTypeStatuses.getJSONObject(i);
                        String sid = s.optString("id");
                        Object catObj = s.opt("statusCategory");
                        String category = "TODO";
                        if (catObj instanceof JSONObject) {
                            String key = ((JSONObject) catObj).optString("key", "new");
                            if ("indeterminate".equals(key)) category = "IN_PROGRESS";
                            else if ("done".equals(key)) category = "DONE";
                        } else if (catObj instanceof String) {
                            category = (String) catObj;
                        }
                        statusCategoryById.put(sid, category);
                    }
                }
            }
        } catch (Exception e) {
            log("Could not enrich statuses with categories: " + e.getMessage());
        }

        // Try to get board columns (works for classic boards, not simple boards)
        JSONArray columns = new JSONArray();
        try {
            String configUrl = getBasePath() + "/rest/agile/1.0/board/" + boardId + "/configuration";
            String configResponse = executeGet(configUrl);
            JSONObject config = new JSONObject(configResponse);
            JSONArray rawCols = config.optJSONObject("columnConfig") != null
                    ? config.getJSONObject("columnConfig").optJSONArray("columns") : null;
            if (rawCols != null) {
                for (int i = 0; i < rawCols.length(); i++) {
                    JSONObject col = rawCols.getJSONObject(i);
                    JSONArray colStatuses = col.optJSONArray("statuses");
                    JSONArray statusNames = new JSONArray();
                    if (colStatuses != null) {
                        for (int j = 0; j < colStatuses.length(); j++) {
                            String sid = colStatuses.getJSONObject(j).optString("id");
                            statusNames.put(sid);
                        }
                    }
                    columns.put(new JSONObject()
                            .put("name", col.optString("name"))
                            .put("statusIds", statusNames));
                }
            }
        } catch (Exception e) {
            log("Could not get board column config: " + e.getMessage());
        }

        // Build per-status list from project statuses API
        JSONArray v2Types = new JSONArray(statusesResponse);
        java.util.LinkedHashMap<String, JSONObject> uniqueStatuses = new java.util.LinkedHashMap<>();
        for (int i = 0; i < v2Types.length(); i++) {
            JSONArray typeStatuses = v2Types.getJSONObject(i).optJSONArray("statuses");
            if (typeStatuses != null) {
                for (int j = 0; j < typeStatuses.length(); j++) {
                    JSONObject s = typeStatuses.getJSONObject(j);
                    String sid = s.optString("id");
                    if (!uniqueStatuses.containsKey(sid)) {
                        JSONObject scope = s.optJSONObject("scope");
                        String category = statusCategoryById.getOrDefault(sid, "TODO");
                        uniqueStatuses.put(sid, new JSONObject()
                                .put("id", sid)
                                .put("name", s.optString("name"))
                                .put("statusCategory", category)
                                .put("scope", scope != null ? scope.optString("type", "GLOBAL") : "GLOBAL"));
                    }
                }
            }
        }

        JSONArray statusList = new JSONArray(uniqueStatuses.values());
        return new JSONObject()
                .put("projectKey", projectKey)
                .put("boardId", boardId)
                .put("boardName", boardName)
                .put("boardType", boardType)
                .put("columns", columns)
                .put("statuses", statusList)
                .put("totalStatuses", statusList.length())
                .toString();
    }

    /**
     * Sets up the workflow of a target project using an explicit list of status definitions —
     * no source project required. Creates missing project-scoped statuses, then applies them
     * to the project's workflow via {@code POST /rest/api/3/workflows/update}.
     *
     * <p>The {@code statusDefinitions} parameter is a JSON array string, e.g.:
     * <pre>[{"name":"Backlog","category":"TODO"},{"name":"In Development","category":"IN_PROGRESS"},{"name":"Done","category":"DONE"}]</pre>
     * Valid category values: {@code TODO}, {@code IN_PROGRESS}, {@code DONE}.
     */
    @MCPTool(
            name = "jira_setup_project_workflow",
            description = "Set up a project's workflow using an explicit list of statuses (no source project needed). Accepts a JSON array of {name, category} objects and applies them to the target project's workflow. Valid categories: TODO, IN_PROGRESS, DONE. Works with team-managed (next-gen) Jira projects.",
            integration = "jira",
            category = "project_management"
    )
    public String setupProjectWorkflow(
            @MCPParam(name = "projectKey", description = "Key of the target Jira project, e.g. TP", required = true) String projectKey,
            @MCPParam(name = "statusDefinitions", description = "JSON array of status definitions, e.g. [{\"name\":\"Backlog\",\"category\":\"TODO\"},...]. Valid categories: TODO, IN_PROGRESS, DONE.", required = true) String statusDefinitions) throws IOException {
        log("Setting up workflow for " + projectKey + " with explicit status list");

        JSONArray desiredStatuses = new JSONArray(statusDefinitions);
        // Defensive fix: if elements arrived as Strings (double-encoded JSON), re-parse them
        if (!desiredStatuses.isEmpty() && desiredStatuses.get(0) instanceof String) {
            JSONArray fixed = new JSONArray();
            for (int i = 0; i < desiredStatuses.length(); i++) {
                fixed.put(new JSONObject(desiredStatuses.getString(i)));
            }
            desiredStatuses = fixed;
        }

        Project project = getProjectDetails(projectKey);
        String projectId = project.getId();

        // 1. Read current project-scoped statuses and build name→id map
        java.util.LinkedHashMap<String, JSONObject> existingStatuses = readProjectStatuses(projectKey);
        java.util.Map<String, String> nameToId = new java.util.LinkedHashMap<>();
        for (JSONObject s : existingStatuses.values()) {
            nameToId.put(s.optString("name").toLowerCase(), s.optString("id"));
        }

        // 2. Create any missing statuses
        JSONArray created = new JSONArray();
        for (int i = 0; i < desiredStatuses.length(); i++) {
            JSONObject def = desiredStatuses.getJSONObject(i);
            String name = def.optString("name");
            String category = def.optString("category", "TODO");
            if (!nameToId.containsKey(name.toLowerCase())) {
                try {
                    String body = new JSONObject()
                            .put("statuses", new JSONArray().put(new JSONObject()
                                    .put("name", name).put("statusCategory", category)))
                            .put("scope", new JSONObject()
                                    .put("type", "PROJECT")
                                    .put("project", new JSONObject().put("id", projectId)))
                            .toString();
                    String response = executePost(getBasePath() + "/rest/api/3/statuses", body);
                    JSONArray resp = new JSONArray(response);
                    String newId = resp.isEmpty() ? "" : resp.getJSONObject(0).optString("id", "");
                    nameToId.put(name.toLowerCase(), newId);
                    created.put(new JSONObject().put("name", name).put("id", newId));
                    log("Created status '" + name + "' (id=" + newId + ")");
                } catch (Exception e) {
                    log("Warning: could not create status '" + name + "': " + e.getMessage());
                }
            }
        }

        // 3. Reload to get complete id map after creations
        existingStatuses = readProjectStatuses(projectKey);
        nameToId.clear();
        for (JSONObject s : existingStatuses.values()) {
            nameToId.put(s.optString("name").toLowerCase(), s.optString("id"));
        }

        // 4. Get current workflow version
        String workflowsUrl = getBasePath() + "/rest/api/3/workflows";
        String workflowName = "Software workflow for project " + projectId;
        String wfBody = new JSONObject().put("workflowNames", new JSONArray().put(workflowName)).toString();
        String wfResp = executePost(workflowsUrl, wfBody);
        JSONObject wfData = new JSONObject(wfResp);
        JSONArray workflowsArr = wfData.optJSONArray("workflows");
        if (workflowsArr == null || workflowsArr.isEmpty()) {
            throw new IOException("No workflow found for project " + projectKey + " (name: " + workflowName + ")");
        }
        JSONObject currentWf = workflowsArr.getJSONObject(0);
        String workflowId = currentWf.optString("id");
        JSONObject versionObj = currentWf.optJSONObject("version");
        String versionId = versionObj != null ? versionObj.optString("id") : "";
        int versionNumber = versionObj != null ? versionObj.optInt("versionNumber", 1) : 1;

        // 5. Collect current workflow status refs (for removal mapping)
        java.util.List<String> currentRefs = new java.util.ArrayList<>();
        JSONArray currentWfStatuses = currentWf.optJSONArray("statuses");
        if (currentWfStatuses != null) {
            for (int i = 0; i < currentWfStatuses.length(); i++) {
                currentRefs.add(currentWfStatuses.getJSONObject(i).optString("statusReference"));
            }
        }

        // 6. Build new workflow statuses + transitions
        java.util.List<JSONObject> newWfStatuses = new java.util.ArrayList<>();
        java.util.Set<String> usedIds = new java.util.LinkedHashSet<>();
        String initialId = null;
        double x = 180.0;
        for (int i = 0; i < desiredStatuses.length(); i++) {
            String name = desiredStatuses.getJSONObject(i).optString("name");
            String id = nameToId.get(name.toLowerCase());
            if (id == null || id.isEmpty()) { log("Warning: no ID for status '" + name + "', skipping"); continue; }
            usedIds.add(id);
            newWfStatuses.add(new JSONObject()
                    .put("statusReference", id)
                    .put("layout", new JSONObject().put("x", x).put("y", -16.0))
                    .put("properties", new JSONObject()));
            x += 180.0;
            if (initialId == null) initialId = id;
        }

        JSONArray transitions = new JSONArray();
        transitions.put(new JSONObject()
                .put("id", "1").put("type", "INITIAL").put("name", "Create").put("description", "")
                .put("toStatusReference", initialId)
                .put("links", new JSONArray())
                .put("properties", new JSONObject().put("jira.i18n.title", "common.forms.create"))
                .put("actions", new JSONArray()).put("validators", new JSONArray()).put("triggers", new JSONArray()));
        int transId = 11;
        for (int i = 0; i < desiredStatuses.length(); i++) {
            String name = desiredStatuses.getJSONObject(i).optString("name");
            String id = nameToId.get(name.toLowerCase());
            if (id == null || id.isEmpty()) continue;
            transitions.put(new JSONObject()
                    .put("id", String.valueOf(transId++)).put("type", "GLOBAL").put("name", name)
                    .put("description", "").put("toStatusReference", id)
                    .put("links", new JSONArray()).put("properties", new JSONObject())
                    .put("actions", new JSONArray()).put("validators", new JSONArray()).put("triggers", new JSONArray()));
        }

        // 7. Build status mappings for removed statuses
        java.util.Map<String, String> categoryFallback = new java.util.HashMap<>();
        for (JSONObject s : existingStatuses.values()) {
            if (usedIds.contains(s.optString("id"))) {
                String cat = s.optString("statusCategory", "TODO");
                if (!categoryFallback.containsKey(cat)) categoryFallback.put(cat, s.optString("id"));
            }
        }
        for (String cat : new String[]{"TODO","IN_PROGRESS","DONE"}) {
            if (!categoryFallback.containsKey(cat)) categoryFallback.put(cat, initialId);
        }

        JSONArray statusMigrations = new JSONArray();
        for (String ref : currentRefs) {
            if (!usedIds.contains(ref)) {
                String cat = "TODO";
                for (JSONObject s : existingStatuses.values()) {
                    if (ref.equals(s.optString("id"))) { cat = s.optString("statusCategory", "TODO"); break; }
                }
                statusMigrations.put(new JSONObject()
                        .put("oldStatusReference", ref)
                        .put("newStatusReference", categoryFallback.getOrDefault(cat, initialId)));
            }
        }

        JSONArray statusMappings = new JSONArray();
        JSONArray issueTypes = project.getIssueTypesJson();
        if (issueTypes != null && !statusMigrations.isEmpty()) {
            for (int i = 0; i < issueTypes.length(); i++) {
                String itId = issueTypes.getJSONObject(i).optString("id");
                statusMappings.put(new JSONObject()
                        .put("projectId", projectId).put("issueTypeId", itId)
                        .put("statusMigrations", statusMigrations));
            }
        }

        // 8. Build all-statuses catalog
        JSONArray catalog = new JSONArray();
        for (JSONObject s : existingStatuses.values()) {
            catalog.put(new JSONObject()
                    .put("id", s.optString("id")).put("name", s.optString("name"))
                    .put("statusCategory", s.optString("statusCategory", "TODO"))
                    .put("statusReference", s.optString("id")).put("description", ""));
        }

        // 9. Build workflow statuses array
        JSONArray wfStatusesArr = new JSONArray();
        for (JSONObject ws : newWfStatuses) wfStatusesArr.put(ws);

        JSONObject workflowUpdate = new JSONObject()
                .put("id", workflowId).put("description", "")
                .put("version", new JSONObject().put("id", versionId).put("versionNumber", versionNumber))
                .put("statuses", wfStatusesArr).put("transitions", transitions)
                .put("startPointLayout", new JSONObject().put("x", 60.0).put("y", -70.0))
                .put("loopedTransitionContainerLayout", new JSONObject())
                .put("statusMappings", statusMappings).put("defaultStatusMappings", statusMigrations);

        JSONObject updateBody = new JSONObject()
                .put("statuses", catalog)
                .put("workflows", new JSONArray().put(workflowUpdate));

        // 10. Apply
        executePost(getBasePath() + "/rest/api/3/workflows/update", updateBody.toString());

        return new JSONObject()
                .put("projectKey", projectKey)
                .put("workflowId", workflowId)
                .put("statusesSynced", usedIds.size())
                .put("statusesCreated", created.length())
                .put("removedStatuses", statusMigrations.length())
                .put("result", "success")
                .toString();
    }

    @MCPTool(
            name = "jira_sync_project_workflow",
            description = "Sync the workflow (statuses) of a target project to exactly match the source project using Jira's bulk workflow update API. Replaces the target workflow's statuses and transitions with those from the source project. Existing issues with removed statuses are automatically migrated to the closest matching new status. Works with team-managed (next-gen) Jira projects.",
            integration = "jira",
            category = "project_management"
    )
    public String syncProjectWorkflow(
            @MCPParam(name = "sourceProjectKey", description = "Key of the source project to copy workflow from, e.g. MYTUBE", required = true) String sourceProjectKey,
            @MCPParam(name = "targetProjectKey", description = "Key of the target project to update workflow for, e.g. TP", required = true) String targetProjectKey) throws IOException {
        log("Syncing workflow from " + sourceProjectKey + " to " + targetProjectKey);

        // 1. Read source workflow statuses (with categories)
        java.util.LinkedHashMap<String, JSONObject> sourceStatuses = readProjectStatuses(sourceProjectKey);
        log("Source " + sourceProjectKey + " has " + sourceStatuses.size() + " statuses");

        // 2. Read target project info
        Project targetProject = getProjectDetails(targetProjectKey);
        String targetProjectId = targetProject.getId();

        // 3. Ensure all source statuses exist as project-scoped statuses in target
        java.util.LinkedHashMap<String, JSONObject> targetStatuses = readProjectStatuses(targetProjectKey);
        java.util.Map<String, String> nameToTargetId = new java.util.HashMap<>();
        for (JSONObject s : targetStatuses.values()) {
            nameToTargetId.put(s.optString("name").toLowerCase(), s.optString("id"));
        }

        JSONArray statusCreationResults = new JSONArray();
        for (JSONObject srcStatus : sourceStatuses.values()) {
            String name = srcStatus.optString("name");
            String category = srcStatus.optString("statusCategory", "TODO");
            if (!nameToTargetId.containsKey(name.toLowerCase())) {
                try {
                    String body = new JSONObject()
                            .put("statuses", new JSONArray().put(new JSONObject()
                                    .put("name", name)
                                    .put("statusCategory", category)))
                            .put("scope", new JSONObject()
                                    .put("type", "PROJECT")
                                    .put("project", new JSONObject().put("id", targetProjectId)))
                            .toString();
                    String response = executePost(getBasePath() + "/rest/api/3/statuses", body);
                    JSONArray resp = new JSONArray(response);
                    String newId = resp.isEmpty() ? "" : resp.getJSONObject(0).optString("id", "");
                    nameToTargetId.put(name.toLowerCase(), newId);
                    statusCreationResults.put(new JSONObject().put("name", name).put("id", newId).put("action", "created"));
                    log("Created status '" + name + "' (id=" + newId + ") in " + targetProjectKey);
                } catch (Exception e) {
                    log("Warning: failed to create status '" + name + "': " + e.getMessage());
                }
            }
        }

        // 4. Reload target statuses after creation
        targetStatuses = readProjectStatuses(targetProjectKey);
        nameToTargetId.clear();
        for (JSONObject s : targetStatuses.values()) {
            nameToTargetId.put(s.optString("name").toLowerCase(), s.optString("id"));
        }

        // 5. Read target current workflow to get version info
        String workflowsUrl = getBasePath() + "/rest/api/3/workflows";
        String workflowName = "Software workflow for project " + targetProjectId;
        String workflowBody = new JSONObject()
                .put("workflowNames", new JSONArray().put(workflowName))
                .toString();
        String workflowResp = executePost(workflowsUrl, workflowBody);
        JSONObject workflowData = new JSONObject(workflowResp);
        JSONArray workflowsArr = workflowData.optJSONArray("workflows");
        if (workflowsArr == null || workflowsArr.isEmpty()) {
            throw new IOException("No workflow found for target project " + targetProjectKey + " (name: " + workflowName + ")");
        }
        JSONObject currentWorkflow = workflowsArr.getJSONObject(0);
        String workflowId = currentWorkflow.optString("id");
        JSONObject versionObj = currentWorkflow.optJSONObject("version");
        String versionId = versionObj != null ? versionObj.optString("id") : "";
        int versionNumber = versionObj != null ? versionObj.optInt("versionNumber", 1) : 1;
        log("Target workflow: " + workflowId + " version " + versionNumber);

        // 6. Collect current target workflow status references (to find removed ones)
        java.util.List<String> currentWorkflowStatusRefs = new java.util.ArrayList<>();
        JSONArray currentWfStatuses = currentWorkflow.optJSONArray("statuses");
        if (currentWfStatuses != null) {
            for (int i = 0; i < currentWfStatuses.length(); i++) {
                currentWorkflowStatusRefs.add(currentWfStatuses.getJSONObject(i).optString("statusReference"));
            }
        }

        // 7. Map source status names to target status IDs
        java.util.List<JSONObject> newWorkflowStatuses = new java.util.ArrayList<>();
        java.util.List<JSONObject> newTransitions = new java.util.ArrayList<>();
        java.util.Set<String> usedTargetIds = new java.util.LinkedHashSet<>();
        String initialStatusId = null;
        double x = 180.0;
        for (JSONObject srcStatus : sourceStatuses.values()) {
            String name = srcStatus.optString("name");
            String targetId = nameToTargetId.get(name.toLowerCase());
            if (targetId == null || targetId.isEmpty()) {
                log("Warning: no target ID found for status '" + name + "' — skipping");
                continue;
            }
            usedTargetIds.add(targetId);
            newWorkflowStatuses.add(new JSONObject()
                    .put("statusReference", targetId)
                    .put("layout", new JSONObject().put("x", x).put("y", -16.0))
                    .put("properties", new JSONObject()));
            x += 180.0;
            if (initialStatusId == null) initialStatusId = targetId;
        }

        // INITIAL transition
        newTransitions.add(new JSONObject()
                .put("id", "1")
                .put("type", "INITIAL")
                .put("name", "Create")
                .put("description", "")
                .put("toStatusReference", initialStatusId)
                .put("links", new JSONArray())
                .put("properties", new JSONObject().put("jira.i18n.title", "common.forms.create"))
                .put("actions", new JSONArray())
                .put("validators", new JSONArray())
                .put("triggers", new JSONArray()));

        // GLOBAL transition for each status
        int transId = 11;
        for (JSONObject srcStatus : sourceStatuses.values()) {
            String name = srcStatus.optString("name");
            String targetId = nameToTargetId.get(name.toLowerCase());
            if (targetId == null || targetId.isEmpty()) continue;
            newTransitions.add(new JSONObject()
                    .put("id", String.valueOf(transId++))
                    .put("type", "GLOBAL")
                    .put("name", name)
                    .put("description", "")
                    .put("toStatusReference", targetId)
                    .put("links", new JSONArray())
                    .put("properties", new JSONObject())
                    .put("actions", new JSONArray())
                    .put("validators", new JSONArray())
                    .put("triggers", new JSONArray()));
        }

        // 8. Build statusMappings for REMOVED statuses
        java.util.List<String> removedStatusRefs = new java.util.ArrayList<>();
        for (String ref : currentWorkflowStatusRefs) {
            if (!usedTargetIds.contains(ref)) removedStatusRefs.add(ref);
        }

        // Map removed status categories to replacement by category similarity
        java.util.Map<String, String> categoryFallback = new java.util.HashMap<>();
        categoryFallback.put("TODO", initialStatusId);
        categoryFallback.put("IN_PROGRESS", null);
        categoryFallback.put("DONE", null);
        for (String tid : usedTargetIds) {
            for (JSONObject s : targetStatuses.values()) {
                if (tid.equals(s.optString("id"))) {
                    String cat = s.optString("statusCategory", "TODO");
                    if (categoryFallback.get(cat) == null) categoryFallback.put(cat, tid);
                    break;
                }
            }
        }
        // Ensure all categories have a fallback
        for (String cat : new String[]{"TODO", "IN_PROGRESS", "DONE"}) {
            if (categoryFallback.get(cat) == null) categoryFallback.put(cat, initialStatusId);
        }

        JSONArray statusMigrations = new JSONArray();
        for (String removedRef : removedStatusRefs) {
            String removedCat = "TODO";
            for (JSONObject s : targetStatuses.values()) {
                if (removedRef.equals(s.optString("id"))) { removedCat = s.optString("statusCategory", "TODO"); break; }
            }
            String replacement = categoryFallback.getOrDefault(removedCat, initialStatusId);
            statusMigrations.put(new JSONObject()
                    .put("oldStatusReference", removedRef)
                    .put("newStatusReference", replacement));
        }

        // Build per-issue-type status mappings
        JSONArray issueTypes = targetProject.getIssueTypesJson();
        JSONArray statusMappings = new JSONArray();
        if (issueTypes != null && !statusMigrations.isEmpty()) {
            for (int i = 0; i < issueTypes.length(); i++) {
                String issueTypeId = issueTypes.getJSONObject(i).optString("id");
                statusMappings.put(new JSONObject()
                        .put("projectId", targetProjectId)
                        .put("issueTypeId", issueTypeId)
                        .put("statusMigrations", statusMigrations));
            }
        }

        // 9. Build all-statuses catalog for the request
        JSONArray allStatusesCatalog = new JSONArray();
        for (JSONObject s : targetStatuses.values()) {
            allStatusesCatalog.put(new JSONObject()
                    .put("id", s.optString("id"))
                    .put("name", s.optString("name"))
                    .put("statusCategory", s.optString("statusCategory", "TODO"))
                    .put("statusReference", s.optString("id"))
                    .put("description", ""));
        }

        // 10. Build workflow statuses as JSONArray
        JSONArray wfStatusesArr = new JSONArray();
        for (JSONObject ws : newWorkflowStatuses) wfStatusesArr.put(ws);
        JSONArray wfTransitionsArr = new JSONArray();
        for (JSONObject wt : newTransitions) wfTransitionsArr.put(wt);

        JSONObject workflowUpdate = new JSONObject()
                .put("id", workflowId)
                .put("description", "")
                .put("version", new JSONObject().put("id", versionId).put("versionNumber", versionNumber))
                .put("statuses", wfStatusesArr)
                .put("transitions", wfTransitionsArr)
                .put("startPointLayout", new JSONObject().put("x", 60.0).put("y", -70.0))
                .put("loopedTransitionContainerLayout", new JSONObject())
                .put("statusMappings", statusMappings)
                .put("defaultStatusMappings", statusMigrations);

        JSONObject updateBody = new JSONObject()
                .put("statuses", allStatusesCatalog)
                .put("workflows", new JSONArray().put(workflowUpdate));

        // 11. Call the bulk workflow update API
        String updateUrl = getBasePath() + "/rest/api/3/workflows/update";
        log("Calling " + updateUrl + " with workflow " + workflowId + " version " + versionNumber);
        String updateResp = executePost(updateUrl, updateBody.toString());

        return new JSONObject()
                .put("sourceProject", sourceProjectKey)
                .put("targetProject", targetProjectKey)
                .put("workflowId", workflowId)
                .put("statusesSynced", usedTargetIds.size())
                .put("statusesCreated", statusCreationResults)
                .put("removedStatuses", removedStatusRefs.size())
                .put("statusMigrations", statusMigrations)
                .put("result", "success")
                .toString();
    }

    /**
     * Reads unique statuses for a project (with categories) from both v2 and v3 APIs.
     */
    private java.util.LinkedHashMap<String, JSONObject> readProjectStatuses(String projectKey) throws IOException {
        java.util.LinkedHashMap<String, JSONObject> result = new java.util.LinkedHashMap<>();

        // v3 for status categories
        java.util.Map<String, String> categoryById = new java.util.HashMap<>();
        try {
            String v3Url = getBasePath() + "/rest/api/3/project/" + projectKey + "/statuses";
            JSONArray v3Types = new JSONArray(executeGet(v3Url));
            if (!v3Types.isEmpty()) {
                JSONArray firstStatuses = v3Types.getJSONObject(0).optJSONArray("statuses");
                if (firstStatuses != null) {
                    for (int i = 0; i < firstStatuses.length(); i++) {
                        JSONObject s = firstStatuses.getJSONObject(i);
                        String sid = s.optString("id");
                        // statusCategory is an object {key: "new"|"indeterminate"|"done"}
                        // Map to the POST API's expected values: TODO, IN_PROGRESS, DONE
                        String category = "TODO";
                        Object catObj = s.opt("statusCategory");
                        if (catObj instanceof JSONObject) {
                            String key = ((JSONObject) catObj).optString("key", "new");
                            if ("indeterminate".equals(key)) category = "IN_PROGRESS";
                            else if ("done".equals(key)) category = "DONE";
                            else category = "TODO";
                        } else if (catObj instanceof String) {
                            category = (String) catObj;
                        }
                        categoryById.put(sid, category);
                    }
                }
            }
        } catch (Exception ignored) {}

        // v2 for full status list
        String v2Url = path("project/" + projectKey + "/statuses");
        JSONArray types = new JSONArray(executeGet(v2Url));
        for (int i = 0; i < types.length(); i++) {
            JSONArray statuses = types.getJSONObject(i).optJSONArray("statuses");
            if (statuses != null) {
                for (int j = 0; j < statuses.length(); j++) {
                    JSONObject s = statuses.getJSONObject(j);
                    String sid = s.optString("id");
                    if (!result.containsKey(sid)) {
                        String category = categoryById.getOrDefault(sid, "TODO");
                        result.put(sid, new JSONObject()
                                .put("id", sid)
                                .put("name", s.optString("name"))
                                .put("statusCategory", category));
                    }
                }
            }
        }
        return result;
    }
}
