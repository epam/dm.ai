// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bridge;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.DateUtils;
import com.github.istin.dmtools.presentation.HTMLPresentationDrawer;
import com.github.istin.dmtools.presentation.PresentationMakerOrchestrator;
import com.github.istin.dmtools.report.projectstatus.BugsReportFacade;
import com.github.istin.dmtools.report.projectstatus.ProjectReportFacade;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.model.TableType;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import freemarker.template.TemplateException;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central bridge class for JavaScript-Java interaction with permission-based access control.
 * This class consolidates all methods that can be called from JavaScript code.
 */
@Singleton
public class DMToolsBridge {

    private static final Logger logger = LogManager.getLogger(DMToolsBridge.class);
    private final Gson gson = new Gson();
    private final String clientName;
    private final Set<Permission> allowedPermissions;
    @Inject
    @Setter
    PresentationMakerOrchestrator presentationMakerOrchestrator;

    /**
     * Permissions that control what functionality is available to JavaScript clients
     */
    public enum Permission {
        // Logging permissions
        LOGGING_INFO("logging:info", "Allows logging informational messages."),
        LOGGING_WARN("logging:warn", "Allows logging warning messages."),
        LOGGING_ERROR("logging:error", "Allows logging error messages."),
        
        // Presentation permissions
        PRESENTATION_HTML_GENERATION("presentation:html", "Allows generating HTML for presentations."),
        PRESENTATION_ORCHESTRATOR("presentation:orchestrator", "Allows running the PresentationMakerOrchestrator."),
        PRESENTATION_REQUEST_DATA("presentation:requestData", "Allows creating RequestData JSON for orchestrator."),
        
        // Project reporting permissions
        REPORT_CUSTOM_PROJECT("report:customProject", "Allows generating custom project reports."),
        REPORT_PROJECT_TIMELINE("report:projectTimeline", "Allows generating project timeline reports."),
        REPORT_PROJECT_BUG("report:projectBug", "Allows generating project bug reports."),
        REPORT_BUGS_WITH_TYPES("report:bugsWithTypes", "Allows generating bugs reports with specified types."),
        
        // Tracker client permissions
        TRACKER_CLIENT_ACCESS("tracker:clientAccess", "Allows accessing the tracker client."),
        
        // HTTP client permissions
        HTTP_POST_REQUESTS("http:postRequests", "Allows making POST HTTP requests."),
        HTTP_GET_REQUESTS("http:getRequests", "Allows making GET HTTP requests."),
        HTTP_BASE_PATH_ACCESS("http:basePathAccess", "Allows accessing the base path of the HTTP handler."),
        
        // All permissions (for admin/unrestricted access)
        ALL("all:all", "Allows all operations.");

        private final String key;
        private final String description;

        Permission(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }
    }

    @Inject
    public DMToolsBridge() {
        this("DMToolsBridge", EnumSet.of(Permission.ALL));
    }

    public DMToolsBridge(String clientName, Set<Permission> allowedPermissions) {
        this.clientName = clientName;
        this.allowedPermissions = new HashSet<>(allowedPermissions);
        if (this.allowedPermissions.contains(Permission.ALL)) {
            this.allowedPermissions.addAll(EnumSet.allOf(Permission.class));
        }
        logger.info("DMToolsBridge initialized for client '{}' with permissions: {}", clientName, allowedPermissions);
    }

    /**
     * Creates a bridge with specific permissions for a client
     */
    public static DMToolsBridge withPermissions(String clientName, Permission... permissions) {
        return new DMToolsBridge(clientName, EnumSet.copyOf(Arrays.asList(permissions)));
    }

    /**
     * Creates a bridge with all permissions (unrestricted access)
     */
    public static DMToolsBridge withAllPermissions(String clientName) {
        return new DMToolsBridge(clientName, EnumSet.of(Permission.ALL));
    }

    private void checkPermission(Permission permission) {
        if (!allowedPermissions.contains(permission)) {
            throw new SecurityException("Client '" + clientName + "' does not have permission: " + permission);
        }
    }

    // ==================== LOGGING METHODS ====================

    @HostAccess.Export
    public void jsLogInfo(String message) {
        checkPermission(Permission.LOGGING_INFO);
        logger.info("[JS:{}] {}", clientName, message);
    }

    @HostAccess.Export
    public void jsLogWarn(String message) {
        checkPermission(Permission.LOGGING_WARN);
        logger.warn("[JS:{}] {}", clientName, message);
    }

    @HostAccess.Export
    public void jsLogError(String message) {
        checkPermission(Permission.LOGGING_ERROR);
        logger.error("[JS:{}] {}", clientName, message);
    }

    @HostAccess.Export
    public void jsLogErrorWithException(String message, String exceptionStackTrace) {
        checkPermission(Permission.LOGGING_ERROR);
        logger.error("[JS:{}] {} \nStack: {}", clientName, message, exceptionStackTrace);
    }

    // ==================== TRACKER CLIENT METHODS ====================

    @HostAccess.Export
    public TrackerClient getTrackerClientInstance() throws IOException {
        checkPermission(Permission.TRACKER_CLIENT_ACCESS);
        return BasicJiraClient.getInstance();
    }

    // ==================== PROJECT REPORTING METHODS ====================

    @HostAccess.Export
    public String generateCustomProjectReport(TrackerClient trackerClient, String reportConfigJsonString, String jql, String startDateStr, String tableTypesJsonList) throws Exception {
        checkPermission(Permission.REPORT_CUSTOM_PROJECT);
        ReportConfiguration reportConfig = gson.fromJson(reportConfigJsonString, ReportConfiguration.class);
        ProjectReportFacade facade = new ProjectReportFacade(trackerClient, reportConfig);
        Calendar cal = parseCalendarFromString(startDateStr);
        List<String> tableTypeNames = gson.fromJson(tableTypesJsonList, new TypeToken<List<String>>(){}.getType());
        List<TableType> tableTypes = tableTypeNames.stream().map(s -> TableType.valueOf(s.toUpperCase())).collect(Collectors.toList());
        
        String htmlReportContent = facade.generateCustomReport(jql, cal, tableTypes);

        JSONObject resultJson = new JSONObject();
        resultJson.put("htmlContent", htmlReportContent != null ? htmlReportContent : "");
        // Provide default empty structures for keys the JS might expect for a presentation
        resultJson.put("slides", new org.json.JSONArray()); 
        resultJson.put("metrics", new JSONObject());
        resultJson.put("summary", ""); // Default empty summary string
        resultJson.put("charts", new org.json.JSONArray());
        resultJson.put("title", "Report Data"); // Default title

        return resultJson.toString();
    }

    @HostAccess.Export
    public String generateProjectTimelineReport(TrackerClient trackerClient, ReportConfiguration reportConfig, String jql, String startDateStr, String timelinePeriodStr) throws Exception {
        checkPermission(Permission.REPORT_PROJECT_TIMELINE);
        ProjectReportFacade facade = new ProjectReportFacade(trackerClient, reportConfig);
        Calendar cal = parseCalendarFromString(startDateStr);
        TimelinePeriod period = (timelinePeriodStr == null || timelinePeriodStr.isEmpty()) ? TimelinePeriod.WEEK : TimelinePeriod.valueOf(timelinePeriodStr.toUpperCase());
        return facade.generateTimelineReport(jql, cal, period);
    }

    @HostAccess.Export
    public String generateProjectBugReport(TrackerClient trackerClient, ReportConfiguration reportConfig, String jql, String startDateStr) throws Exception {
        checkPermission(Permission.REPORT_PROJECT_BUG);
        ProjectReportFacade facade = new ProjectReportFacade(trackerClient, reportConfig);
        Calendar cal = parseCalendarFromString(startDateStr);
        return facade.generateBugReport(jql, cal);
    }

    @HostAccess.Export
    public String generateBugsReportWithTypes(TrackerClient trackerClient, String reportConfigJson, String jql, String startDateStr, String timelinePeriodStr, boolean usePeriodForTimeline, String reportTypesJsonList) throws Exception {
        checkPermission(Permission.REPORT_BUGS_WITH_TYPES);
        ReportConfiguration reportConfig = gson.fromJson(reportConfigJson, ReportConfiguration.class);
        BugsReportFacade facade = new BugsReportFacade(trackerClient, reportConfig);
        Calendar cal = parseCalendarFromString(startDateStr);
        TimelinePeriod period = (timelinePeriodStr == null || timelinePeriodStr.isEmpty()) ? null : TimelinePeriod.valueOf(timelinePeriodStr.toUpperCase());
        
        BugsReportFacade.ReportType[] reportTypes;
        if (reportTypesJsonList == null || reportTypesJsonList.trim().isEmpty() || reportTypesJsonList.trim().equals("[]")) {
            reportTypes = BugsReportFacade.ALL_TYPES_EXCEPT_DETAILS; 
        } else {
            List<String> reportTypeNames = gson.fromJson(reportTypesJsonList, new TypeToken<List<String>>(){}.getType());
            reportTypes = reportTypeNames.stream()
                .map(s -> BugsReportFacade.ReportType.valueOf(s.toUpperCase()))
                .toArray(BugsReportFacade.ReportType[]::new);
        }
        return facade.generateBugReport(jql, cal, period, usePeriodForTimeline, reportTypes);
    }

    // ==================== PRESENTATION METHODS ====================

    @HostAccess.Export
    public String invokePresentationOrchestrator(String paramsJson) throws Exception {
        checkPermission(Permission.PRESENTATION_ORCHESTRATOR);
        PresentationMakerOrchestrator.Params params = gson.fromJson(paramsJson, PresentationMakerOrchestrator.Params.class);
        PresentationMakerOrchestrator orchestrator = new PresentationMakerOrchestrator();
        JSONObject presentationOutput = orchestrator.createPresentation(params);
        return presentationOutput.toString();
    }

    @HostAccess.Export
    public void drawHtmlPresentation(String topic, String presentationJsonString) throws IOException, TemplateException {
        checkPermission(Permission.PRESENTATION_HTML_GENERATION);
        JSONObject presentationJo = new JSONObject(presentationJsonString);

        // Check for "generatedSlides" and rename to "slides" if "slides" isn't already present
        if (presentationJo.has("generatedSlides") && !presentationJo.has("slides")) {
            org.json.JSONArray slidesArray = presentationJo.getJSONArray("generatedSlides");
            presentationJo.remove("generatedSlides");
            presentationJo.put("slides", slidesArray);
            jsLogInfo("Adjusted presentation JSON: moved 'generatedSlides' to 'slides' for topic: " + topic);
        }

        HTMLPresentationDrawer drawer = new HTMLPresentationDrawer();
        drawer.printPresentation(topic, presentationJo);
        jsLogInfo("HTML presentation for topic '" + topic + "' processed by HTMLPresentationDrawer. Check console for output/file path details.");
    }

    @HostAccess.Export
    public String createRequestDataJson(String request, String additionalData) {
        checkPermission(Permission.PRESENTATION_REQUEST_DATA);
        PresentationMakerOrchestrator.RequestData data = new PresentationMakerOrchestrator.RequestData(request, additionalData);
        return gson.toJson(data);
    }

    @HostAccess.Export
    public String createListOfRequestDataJson(String requestDataArrayJson) {
        checkPermission(Permission.PRESENTATION_REQUEST_DATA);
        java.lang.reflect.Type requestDataListType = new TypeToken<ArrayList<PresentationMakerOrchestrator.RequestData>>() {}.getType();
        List<PresentationMakerOrchestrator.RequestData> list = gson.fromJson(requestDataArrayJson, requestDataListType);
        return gson.toJson(list); 
    }

    // ==================== HTTP CLIENT METHODS ====================

    // HTTP handler delegate interface
    public interface HttpHandler {
        String executePost(String pathOrUrl, String bodyJson, Map<String, Object> headersMap) throws IOException;
        String executeGet(String pathOrUrl, Map<String, Object> headersMap) throws IOException;
        String getBasePath();
    }

    @Setter
    private HttpHandler httpHandler;

    @HostAccess.Export
    public String executePost(String pathOrUrl, String bodyJson, Map<String, Object> headersMap) throws IOException {
        checkPermission(Permission.HTTP_POST_REQUESTS);
        if (httpHandler == null) {
            throw new UnsupportedOperationException("HTTP handler not configured for this bridge");
        }
        return httpHandler.executePost(pathOrUrl, bodyJson, headersMap);
    }

    @HostAccess.Export
    public String executeGet(String pathOrUrl, Map<String, Object> headersMap) throws IOException {
        checkPermission(Permission.HTTP_GET_REQUESTS);
        if (httpHandler == null) {
            throw new UnsupportedOperationException("HTTP handler not configured for this bridge");
        }
        return httpHandler.executeGet(pathOrUrl, headersMap);
    }

    @HostAccess.Export
    public String getJsBasePath() {
        checkPermission(Permission.HTTP_BASE_PATH_ACCESS);
        if (httpHandler == null) {
            throw new UnsupportedOperationException("HTTP handler not configured for this bridge");
        }
        return httpHandler.getBasePath();
    }

    // ==================== UTILITY METHODS ====================

    private Calendar parseCalendarFromString(String dateString) throws ParseException {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        
        Calendar cal = Calendar.getInstance();
        
        // Handle special keywords
        if (dateString.equalsIgnoreCase("last_month")) {
            cal.add(Calendar.MONTH, -1);
            return cal;
        } else if (dateString.equalsIgnoreCase("today")) {
            return cal; // Current date
        } else if (dateString.equalsIgnoreCase("yesterday")) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
            return cal;
        } else if (dateString.equalsIgnoreCase("tomorrow")) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            return cal;
        }

        // Handle relative dates like "-15d", "+1m", "-2y"
        if (dateString.matches("[\\+\\-]\\d+[dMyY]")) {
            char unit = dateString.charAt(dateString.length() - 1);
            int amount = Integer.parseInt(dateString.substring(1, dateString.length() - 1));
            if (dateString.startsWith("-")) {
                amount = -amount;
            }

            switch (Character.toLowerCase(unit)) {
                case 'd':
                    cal.add(Calendar.DAY_OF_MONTH, amount);
                    break;
                case 'm':
                    cal.add(Calendar.MONTH, amount);
                    break;
                case 'y':
                    cal.add(Calendar.YEAR, amount);
                    break;
                default:
                    throw new ParseException("Invalid unit for relative date: " + unit + " in " + dateString, dateString.length() -1);
            }
            return cal;
        }
        
        // Try parsing as epoch milliseconds first
        try {
            long epochMillis = Long.parseLong(dateString);
            cal.setTimeInMillis(epochMillis);
            return cal;
        } catch (NumberFormatException e) {
            // Not an epoch time, continue with date parsing
        }
        
        // Use DateUtils for smart date parsing
        Date parsedDate = DateUtils.smartParseDate(dateString);
        if (parsedDate != null) {
            cal.setTime(parsedDate);
            return cal;
        }
        
        // If DateUtils couldn't parse it, throw an exception
        throw new ParseException("Invalid date format for string: " + dateString + 
            ". Expected 'YYYY-MM-DD', 'last_month', epoch milliseconds, or any supported ISO/standard date format.", 0);
    }

    // ==================== BRIDGE INFO METHODS ====================

    @HostAccess.Export
    public String getBridgeInfo() {
        return "DMToolsBridge for client '" + clientName + "' with " + allowedPermissions.size() + " permissions";
    }

    @HostAccess.Export
    public boolean hasPermission(String permissionName) {
        try {
            Permission permission = Permission.valueOf(permissionName.toUpperCase());
            return allowedPermissions.contains(permission);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @HostAccess.Export
    public String runPresentationOrchestrator(String orchestratorParamsJson) throws Exception {
        checkPermission(Permission.PRESENTATION_ORCHESTRATOR);
        if (this.presentationMakerOrchestrator == null) {
            // Initialize if not injected or available. This might need adjustment based on actual DI.
            // For now, direct instantiation. If Dagger/Spring is used, it should be @Inject'd.
            this.presentationMakerOrchestrator = new PresentationMakerOrchestrator();
        }
        PresentationMakerOrchestrator.Params params = gson.fromJson(orchestratorParamsJson, PresentationMakerOrchestrator.Params.class);
        JSONObject presentationJson = this.presentationMakerOrchestrator.createPresentation(params);
        return presentationJson.toString();
    }
} 