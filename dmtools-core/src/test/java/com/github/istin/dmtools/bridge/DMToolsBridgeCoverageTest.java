// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bridge;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.presentation.HTMLPresentationDrawer;
import com.github.istin.dmtools.presentation.PresentationMakerOrchestrator;
import com.github.istin.dmtools.report.projectstatus.BugsReportFacade;
import com.github.istin.dmtools.report.projectstatus.ProjectReportFacade;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional coverage tests for DMToolsBridge targeting permission enum accessors,
 * the default constructor, report/presentation/HTTP bridge methods, and the
 * remaining parseCalendarFromString branches.
 */
class DMToolsBridgeCoverageTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private DMToolsBridge.HttpHandler mockHttpHandler;

    @Mock
    private PresentationMakerOrchestrator mockOrchestrator;

    private DMToolsBridge bridge;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = DMToolsBridge.withAllPermissions("CoverageClient");
    }

    @Test
    void testPermissionEnumAccessors() {
        assertEquals("logging:info", DMToolsBridge.Permission.LOGGING_INFO.getKey());
        assertEquals("Allows logging informational messages.", DMToolsBridge.Permission.LOGGING_INFO.getDescription());
        assertEquals("all:all", DMToolsBridge.Permission.ALL.getKey());
        assertEquals("Allows all operations.", DMToolsBridge.Permission.ALL.getDescription());
        assertEquals("http:getRequests", DMToolsBridge.Permission.HTTP_GET_REQUESTS.getKey());
    }

    @Test
    void testDefaultConstructorGrantsAllPermissions() {
        DMToolsBridge defaultBridge = new DMToolsBridge();
        assertTrue(defaultBridge.getBridgeInfo().contains("DMToolsBridge"));
        for (DMToolsBridge.Permission permission : DMToolsBridge.Permission.values()) {
            assertTrue(defaultBridge.hasPermission(permission.name()),
                    "Default bridge should have permission " + permission);
        }
        assertFalse(defaultBridge.hasPermission("no_such_permission"));
    }

    @Test
    void testJsLogErrorWithException() {
        assertDoesNotThrow(() -> bridge.jsLogErrorWithException("something failed", "java.lang.RuntimeException: boom"));
        assertDoesNotThrow(() -> {
            bridge.jsLogInfo("info");
            bridge.jsLogWarn("warn");
            bridge.jsLogError("error");
        });

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoLogs", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class,
                () -> limited.jsLogErrorWithException("denied", "stack"));
    }

    @Test
    void testGetTrackerClientInstance() throws Exception {
        try (MockedStatic<BasicJiraClient> mockedStatic = mockStatic(BasicJiraClient.class)) {
            mockedStatic.when(BasicJiraClient::getInstance).thenReturn(mockTrackerClient);
            assertSame(mockTrackerClient, bridge.getTrackerClientInstance());
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoTracker", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, limited::getTrackerClientInstance);
    }

    @Test
    void testGenerateCustomProjectReport() throws Exception {
        try (MockedConstruction<ProjectReportFacade> construction = mockConstruction(ProjectReportFacade.class,
                (mock, context) -> when(mock.generateCustomReport(anyString(), any(), anyList()))
                        .thenReturn("<html>report</html>"))) {
            String result = bridge.generateCustomProjectReport(mockTrackerClient, "{}", "project = X",
                    "2023-12-25", "[\"summary\", \"bugs_table\"]");

            JSONObject json = new JSONObject(result);
            assertEquals("<html>report</html>", json.getString("htmlContent"));
            assertEquals(0, json.getJSONArray("slides").length());
            assertEquals("", json.getString("summary"));
            assertEquals("Report Data", json.getString("title"));
            assertEquals(1, construction.constructed().size());
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoReport", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.generateCustomProjectReport(
                mockTrackerClient, "{}", "jql", "today", "[]"));
    }

    @Test
    void testGenerateProjectTimelineReport() throws Exception {
        try (MockedConstruction<ProjectReportFacade> construction = mockConstruction(ProjectReportFacade.class,
                (mock, context) -> when(mock.generateTimelineReport(anyString(), any(), any()))
                        .thenReturn("timeline-report"))) {
            // null period defaults to WEEK
            assertEquals("timeline-report",
                    bridge.generateProjectTimelineReport(mockTrackerClient, null, "jql", "last_month", null));
            // empty period defaults to WEEK
            assertEquals("timeline-report",
                    bridge.generateProjectTimelineReport(mockTrackerClient, null, "jql", "today", ""));
            // explicit period
            assertEquals("timeline-report",
                    bridge.generateProjectTimelineReport(mockTrackerClient, null, "jql", "yesterday", "month"));
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoReport", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.generateProjectTimelineReport(
                mockTrackerClient, null, "jql", "today", "week"));
    }

    @Test
    void testGenerateProjectBugReport() throws Exception {
        try (MockedConstruction<ProjectReportFacade> construction = mockConstruction(ProjectReportFacade.class,
                (mock, context) -> when(mock.generateBugReport(anyString(), any()))
                        .thenReturn("bug-report"))) {
            assertEquals("bug-report",
                    bridge.generateProjectBugReport(mockTrackerClient, null, "jql", "tomorrow"));
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoReport", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.generateProjectBugReport(
                mockTrackerClient, null, "jql", "today"));
    }

    @Test
    void testGenerateBugsReportWithTypes() throws Exception {
        try (MockedConstruction<BugsReportFacade> construction = mockConstruction(BugsReportFacade.class,
                (mock, context) -> when(mock.generateBugReport(anyString(), any(), any(), anyBoolean(),
                        any(BugsReportFacade.ReportType[].class)))
                        .thenReturn("bugs-with-types"))) {
            // null report types -> ALL_TYPES_EXCEPT_DETAILS
            assertEquals("bugs-with-types", bridge.generateBugsReportWithTypes(
                    mockTrackerClient, "{}", "jql", "-15d", "week", true, null));
            // empty report types -> ALL_TYPES_EXCEPT_DETAILS
            assertEquals("bugs-with-types", bridge.generateBugsReportWithTypes(
                    mockTrackerClient, "{}", "jql", "-15d", null, false, "[]"));
            // explicit report types
            assertEquals("bugs-with-types", bridge.generateBugsReportWithTypes(
                    mockTrackerClient, "{}", "jql", "1640995200000", "month", true,
                    "[\"completed_overview\", \"created_details\"]"));
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoReport", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.generateBugsReportWithTypes(
                mockTrackerClient, "{}", "jql", "today", "week", true, "[]"));
    }

    @Test
    void testInvokePresentationOrchestrator() throws Exception {
        try (MockedConstruction<PresentationMakerOrchestrator> construction = mockConstruction(
                PresentationMakerOrchestrator.class,
                (mock, context) -> when(mock.createPresentation(any()))
                        .thenReturn(new JSONObject().put("status", "ok")))) {
            String result = bridge.invokePresentationOrchestrator(
                    "{\"topic\":\"T\",\"audience\":\"A\",\"requestDataList\":[]}");
            assertEquals("ok", new JSONObject(result).getString("status"));
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoPresentation", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.invokePresentationOrchestrator("{}"));
    }

    @Test
    void testDrawHtmlPresentation() throws Exception {
        try (MockedConstruction<HTMLPresentationDrawer> construction = mockConstruction(HTMLPresentationDrawer.class)) {
            // "generatedSlides" renamed to "slides"
            assertDoesNotThrow(() -> bridge.drawHtmlPresentation("topic1",
                    "{\"generatedSlides\":[{\"type\":\"title\"}]}"));
            // plain "slides" stays untouched
            assertDoesNotThrow(() -> bridge.drawHtmlPresentation("topic2",
                    "{\"slides\":[{\"type\":\"title\"}]}"));
            assertEquals(2, construction.constructed().size());
            HTMLPresentationDrawer drawer = construction.constructed().get(0);
            verify(drawer).printPresentation(eq("topic1"), argThat(jo -> jo.has("slides") && !jo.has("generatedSlides")));
        }

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoPresentation", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.drawHtmlPresentation("t", "{}"));
    }

    @Test
    void testCreateRequestDataJson() {
        String json = bridge.createRequestDataJson("make a slide", "extra context");
        JSONObject parsed = new JSONObject(json);
        assertEquals("make a slide", parsed.getString("userRequest"));
        assertEquals("extra context", parsed.getString("additionalData"));

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoPresentation", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.createRequestDataJson("r", "d"));
    }

    @Test
    void testCreateListOfRequestDataJson() {
        String input = "[{\"userRequest\":\"r1\",\"additionalData\":\"d1\"}," +
                "{\"userRequest\":\"r2\",\"additionalData\":null}]";
        String output = bridge.createListOfRequestDataJson(input);
        org.json.JSONArray parsed = new org.json.JSONArray(output);
        assertEquals(2, parsed.length());
        assertEquals("r1", parsed.getJSONObject(0).getString("userRequest"));
        assertEquals("d1", parsed.getJSONObject(0).getString("additionalData"));
        assertEquals("r2", parsed.getJSONObject(1).getString("userRequest"));

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoPresentation", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.createListOfRequestDataJson("[]"));
    }

    @Test
    void testHttpMethodsWithoutHandler() {
        assertThrows(UnsupportedOperationException.class,
                () -> bridge.executePost("/path", "{}", Collections.emptyMap()));
        assertThrows(UnsupportedOperationException.class,
                () -> bridge.executeGet("/path", Collections.emptyMap()));
        assertThrows(UnsupportedOperationException.class, bridge::getJsBasePath);
    }

    @Test
    void testHttpMethodsWithHandler() throws Exception {
        bridge.setHttpHandler(mockHttpHandler);
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token");

        when(mockHttpHandler.executePost("/api", "{\"a\":1}", headers)).thenReturn("post-result");
        when(mockHttpHandler.executeGet("/api", headers)).thenReturn("get-result");
        when(mockHttpHandler.getBasePath()).thenReturn("https://example.com");

        assertEquals("post-result", bridge.executePost("/api", "{\"a\":1}", headers));
        assertEquals("get-result", bridge.executeGet("/api", headers));
        assertEquals("https://example.com", bridge.getJsBasePath());

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoHttp", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.executePost("/p", "{}", headers));
        assertThrows(SecurityException.class, () -> limited.executeGet("/p", headers));
        assertThrows(SecurityException.class, limited::getJsBasePath);
    }

    @Test
    void testParseCalendarKeywordsAndRelativeDates() throws Exception {
        Method parseMethod = DMToolsBridge.class.getDeclaredMethod("parseCalendarFromString", String.class);
        parseMethod.setAccessible(true);

        // "today" keyword
        Calendar today = (Calendar) parseMethod.invoke(bridge, "today");
        assertNotNull(today);
        assertEquals(Calendar.getInstance().get(Calendar.DAY_OF_YEAR), today.get(Calendar.DAY_OF_YEAR));

        // "yesterday" keyword
        Calendar yesterday = (Calendar) parseMethod.invoke(bridge, "yesterday");
        Calendar expectedYesterday = Calendar.getInstance();
        expectedYesterday.add(Calendar.DAY_OF_MONTH, -1);
        assertEquals(expectedYesterday.get(Calendar.DAY_OF_YEAR), yesterday.get(Calendar.DAY_OF_YEAR));

        // "tomorrow" keyword
        Calendar tomorrow = (Calendar) parseMethod.invoke(bridge, "tomorrow");
        Calendar expectedTomorrow = Calendar.getInstance();
        expectedTomorrow.add(Calendar.DAY_OF_MONTH, 1);
        assertEquals(expectedTomorrow.get(Calendar.DAY_OF_YEAR), tomorrow.get(Calendar.DAY_OF_YEAR));

        // relative days "-15d"
        Calendar minus15d = (Calendar) parseMethod.invoke(bridge, "-15d");
        Calendar expectedMinus15d = Calendar.getInstance();
        expectedMinus15d.add(Calendar.DAY_OF_MONTH, -15);
        assertEquals(expectedMinus15d.get(Calendar.DAY_OF_YEAR), minus15d.get(Calendar.DAY_OF_YEAR));

        // relative months "+1M"
        Calendar plus1m = (Calendar) parseMethod.invoke(bridge, "+1M");
        Calendar expectedPlus1m = Calendar.getInstance();
        expectedPlus1m.add(Calendar.MONTH, 1);
        assertEquals(expectedPlus1m.get(Calendar.MONTH), plus1m.get(Calendar.MONTH));
        assertEquals(expectedPlus1m.get(Calendar.YEAR), plus1m.get(Calendar.YEAR));

        // relative years "-2y"
        Calendar minus2y = (Calendar) parseMethod.invoke(bridge, "-2y");
        Calendar expectedMinus2y = Calendar.getInstance();
        expectedMinus2y.add(Calendar.YEAR, -2);
        assertEquals(expectedMinus2y.get(Calendar.YEAR), minus2y.get(Calendar.YEAR));

        // blank input -> null
        assertNull(parseMethod.invoke(bridge, "   "));

        // unparseable input -> ParseException
        assertThrows(Exception.class, () -> parseMethod.invoke(bridge, "not-a-date"));
    }

    @Test
    void testRunPresentationOrchestratorWithInjectedOrchestrator() throws Exception {
        bridge.setPresentationMakerOrchestrator(mockOrchestrator);
        when(mockOrchestrator.createPresentation(any())).thenReturn(new JSONObject().put("slides", 3));

        String result = bridge.runPresentationOrchestrator(
                "{\"topic\":\"T\",\"audience\":\"A\",\"requestDataList\":[]}");
        assertEquals(3, new JSONObject(result).getInt("slides"));

        DMToolsBridge limited = DMToolsBridge.withPermissions("NoPresentation", DMToolsBridge.Permission.LOGGING_INFO);
        assertThrows(SecurityException.class, () -> limited.runPresentationOrchestrator("{}"));
    }

    @Test
    void testRunPresentationOrchestratorLazyInit() throws Exception {
        // presentationMakerOrchestrator is null -> instantiated on demand
        try (MockedConstruction<PresentationMakerOrchestrator> construction = mockConstruction(
                PresentationMakerOrchestrator.class,
                (mock, context) -> when(mock.createPresentation(any()))
                        .thenReturn(new JSONObject().put("lazy", true)))) {
            String result = bridge.runPresentationOrchestrator(
                    "{\"topic\":\"T\",\"audience\":\"A\",\"requestDataList\":[]}");
            assertTrue(new JSONObject(result).getBoolean("lazy"));
            assertEquals(1, construction.constructed().size());
        }
    }
}
