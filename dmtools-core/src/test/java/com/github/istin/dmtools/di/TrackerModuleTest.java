// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.atlassian.jira.xray.XrayClient;
import com.github.istin.dmtools.broadcom.rally.BasicRallyClient;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.NoOpTrackerClient;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Coverage tests for TrackerModule#provideTrackerClient, exercising all
 * DEFAULT_TRACKER branches (jira, ado, rally, jira_xray, unknown) and the
 * auto-detection fallback chain. Static getInstance() factories are mocked
 * so no real environment configuration or network access is required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TrackerModuleTest {

    private TrackerModule module;
    private ApplicationConfiguration configuration;

    @BeforeEach
    void setUp() {
        module = new TrackerModule();
        configuration = mock(ApplicationConfiguration.class);
    }

    @SuppressWarnings("unchecked")
    private static TrackerClient<? extends ITicket> trackerMock() {
        return mock(TrackerClient.class);
    }

    // ---------- DEFAULT_TRACKER=jira ----------

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraSuccess() {
        when(configuration.getDefaultTracker()).thenReturn("jira");
        TrackerClient<? extends ITicket> jiraClient = trackerMock();

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(jiraClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(jiraClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraCaseInsensitiveWithWhitespace() {
        when(configuration.getDefaultTracker()).thenReturn("  JIRA  ");
        TrackerClient<? extends ITicket> jiraClient = trackerMock();

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(jiraClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(jiraClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraThrowsJiraNotRetriedAdoUsed() {
        when(configuration.getDefaultTracker()).thenReturn("jira");
        BasicAzureDevOpsClient adoClient = mock(BasicAzureDevOpsClient.class);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenThrow(new IOException("jira down"));
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(adoClient);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            // Jira was already attempted (and failed), so it must be skipped in auto-detection
            assertSame(adoClient, result);
            jira.verify(BasicJiraClient::getInstance, org.mockito.Mockito.times(1));
        }
    }

    // ---------- DEFAULT_TRACKER=ado ----------

    @Test
    void testProvideTrackerClient_DefaultTrackerAdoSuccess() {
        when(configuration.getDefaultTracker()).thenReturn("ado");
        BasicAzureDevOpsClient adoClient = mock(BasicAzureDevOpsClient.class);

        try (MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class)) {
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(adoClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(adoClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerAdoThrowsAdoNotRetriedRallyUsed() {
        when(configuration.getDefaultTracker()).thenReturn("ado");
        BasicRallyClient rallyClient = mock(BasicRallyClient.class);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(null);
            ado.when(BasicAzureDevOpsClient::getInstance).thenThrow(new IOException("ado down"));
            rally.when(BasicRallyClient::getInstance).thenReturn(rallyClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            // ADO was already attempted (and failed), so it must be skipped in auto-detection
            assertSame(rallyClient, result);
            ado.verify(BasicAzureDevOpsClient::getInstance, org.mockito.Mockito.times(1));
        }
    }

    // ---------- DEFAULT_TRACKER=rally ----------

    @Test
    void testProvideTrackerClient_DefaultTrackerRallySuccess() {
        when(configuration.getDefaultTracker()).thenReturn("rally");
        BasicRallyClient rallyClient = mock(BasicRallyClient.class);

        try (MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            rally.when(BasicRallyClient::getInstance).thenReturn(rallyClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(rallyClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerRallyThrowsAllFailReturnsNoOpInTestEnv() {
        when(configuration.getDefaultTracker()).thenReturn("rally");

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(null);
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(null);
            rally.when(BasicRallyClient::getInstance).thenThrow(new IOException("rally down"));

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            // No tracker configured, but running under a test environment -> no-op client
            assertNotNull(result);
            assertTrue(result instanceof NoOpTrackerClient);
            rally.verify(BasicRallyClient::getInstance, org.mockito.Mockito.times(1));
        }
    }

    // ---------- DEFAULT_TRACKER=jira_xray ----------

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraXraySuccess() {
        when(configuration.getDefaultTracker()).thenReturn("jira_xray");
        TrackerClient<? extends ITicket> xrayClient = trackerMock();

        try (MockedStatic<XrayClient> xray = mockStatic(XrayClient.class)) {
            xray.when(XrayClient::getInstance).thenReturn(xrayClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(xrayClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_DefaultTrackerJiraXrayThrowsJiraMarkedAttempted() {
        when(configuration.getDefaultTracker()).thenReturn("jira_xray");
        BasicAzureDevOpsClient adoClient = mock(BasicAzureDevOpsClient.class);

        try (MockedStatic<XrayClient> xray = mockStatic(XrayClient.class);
             MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            xray.when(XrayClient::getInstance).thenThrow(new IOException("xray down"));
            jira.when(BasicJiraClient::getInstance).thenReturn(trackerMock());
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(adoClient);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            // X-ray failure marks Jira as attempted, so Jira must NOT be auto-detected
            assertSame(adoClient, result);
            jira.verifyNoInteractions();
        }
    }

    // ---------- DEFAULT_TRACKER unknown / empty / null ----------

    @Test
    void testProvideTrackerClient_UnknownDefaultTrackerFallsBackToAutoDetection() {
        when(configuration.getDefaultTracker()).thenReturn("unknown_tracker");
        TrackerClient<? extends ITicket> jiraClient = trackerMock();

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(jiraClient);
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(null);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(jiraClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_NullDefaultTrackerAutoDetectsJira() {
        when(configuration.getDefaultTracker()).thenReturn(null);
        TrackerClient<? extends ITicket> jiraClient = trackerMock();

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(jiraClient);
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(null);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(jiraClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_EmptyDefaultTrackerAutoDetectsAdo() {
        when(configuration.getDefaultTracker()).thenReturn("");
        BasicAzureDevOpsClient adoClient = mock(BasicAzureDevOpsClient.class);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(null);
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(adoClient);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(adoClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_WhitespaceDefaultTrackerAutoDetectsRally() {
        when(configuration.getDefaultTracker()).thenReturn("   ");
        BasicRallyClient rallyClient = mock(BasicRallyClient.class);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenReturn(null);
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(null);
            rally.when(BasicRallyClient::getInstance).thenReturn(rallyClient);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(rallyClient, result);
        }
    }

    // ---------- Auto-detection failure chains ----------

    @Test
    void testProvideTrackerClient_AutoDetectJiraThrowsAdoUsed() {
        when(configuration.getDefaultTracker()).thenReturn(null);
        BasicAzureDevOpsClient adoClient = mock(BasicAzureDevOpsClient.class);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenThrow(new IOException("jira down"));
            ado.when(BasicAzureDevOpsClient::getInstance).thenReturn(adoClient);
            rally.when(BasicRallyClient::getInstance).thenReturn(null);

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertSame(adoClient, result);
        }
    }

    @Test
    void testProvideTrackerClient_AutoDetectAllThrowReturnsNoOpInTestEnv() {
        when(configuration.getDefaultTracker()).thenReturn(null);

        try (MockedStatic<BasicJiraClient> jira = mockStatic(BasicJiraClient.class);
             MockedStatic<BasicAzureDevOpsClient> ado = mockStatic(BasicAzureDevOpsClient.class);
             MockedStatic<BasicRallyClient> rally = mockStatic(BasicRallyClient.class)) {
            jira.when(BasicJiraClient::getInstance).thenThrow(new IOException("jira down"));
            ado.when(BasicAzureDevOpsClient::getInstance).thenThrow(new IOException("ado down"));
            rally.when(BasicRallyClient::getInstance).thenThrow(new IOException("rally down"));

            TrackerClient<? extends ITicket> result = module.provideTrackerClient(configuration);

            assertNotNull(result);
            assertTrue(result instanceof NoOpTrackerClient);
        }
    }
}
