// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Test;
import org.mockito.Mockito;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class IssuesIDsParserTest {

    @Test
    public void testParseIssues() {
        // Mocking the logger to avoid actual logging during tests
        Logger mockLogger = mock(Logger.class);

        IssuesIDsParser parser = new IssuesIDsParser("ABC", "XYZ");
        String[] texts = {
            "This is a test ABC123",
            "Another line with XYZ456",
            "Duplicate ABC123",
            "No match here"
        };

        List<String> result = parser.parseIssues(texts);

        assertEquals(2, result.size());
        assertTrue(result.contains("ABC123"));
        assertTrue(result.contains("XYZ456"));

    }

    @Test
    public void testParseIssuesWithNullText() {
        IssuesIDsParser parser = new IssuesIDsParser("ABC", "XYZ");
        String[] texts = {
            null,
            "Valid line ABC123"
        };

        List<String> result = parser.parseIssues(texts);

        assertEquals(1, result.size());
        assertTrue(result.contains("ABC123"));
    }

    @Test
    public void testExtractAllJiraIDs() {
        String text = "Here is a JIRA key ABC-123 and another one XYZ-456. Also, check this URL: https://example.com/browse/DEF-789";
        
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(text);

        assertEquals(3, result.size());
        assertTrue(result.contains("ABC-123"));
        assertTrue(result.contains("XYZ-456"));
        assertTrue(result.contains("DEF-789"));
    }

    @Test
    public void testExtractAllJiraIDsWithNullText() {
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractAllJiraIDsWithIgnoreList() {
        String text = "PROJ-860 uses PSR-18 and PSR-17 factories, see RFC-6749";
        Set<String> ignorePrefixes = new HashSet<>(Collections.singletonList("PSR"));
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(text, ignorePrefixes, Collections.emptySet());

        assertEquals(2, result.size());
        assertTrue(result.contains("PROJ-860"));
        assertTrue(result.contains("RFC-6749"));
    }

    @Test
    public void testExtractAllJiraIDsWithAllowedList() {
        String text = "PROJ-860 uses PSR-18 and TEAM-123 factories, see RFC-6749";
        Set<String> allowedPrefixes = new HashSet<>(Collections.singletonList("PROJ"));
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(text, Collections.emptySet(), allowedPrefixes);

        assertEquals(1, result.size());
        assertTrue(result.contains("PROJ-860"));
    }

    @Test
    public void testExtractAllJiraIDsWithBothLists() {
        String text = "PROJ-860 uses PSR-18 and TEAM-123 factories";
        Set<String> ignorePrefixes = new HashSet<>(Collections.singletonList("TEAM"));
        Set<String> allowedPrefixes = new HashSet<>(Collections.singletonList("PROJ"));
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(text, ignorePrefixes, allowedPrefixes);

        assertEquals(1, result.size());
        assertTrue(result.contains("PROJ-860"));
    }

    @Test
    public void testExtractAllJiraIDsWithCaseInsensitivePrefixes() {
        String text = "PROJ-860 uses PSR-18 factories";
        Set<String> ignorePrefixes = new HashSet<>(Collections.singletonList("psr"));
        Set<String> allowedPrefixes = new HashSet<>(Collections.singletonList("proj"));
        Set<String> result = IssuesIDsParser.extractAllJiraIDs(text, ignorePrefixes, allowedPrefixes);

        assertEquals(1, result.size());
        assertTrue(result.contains("PROJ-860"));
    }

    @Test
    public void testExtractAllJiraIDsReadsPropertyReaderConfig() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(PropertyReader.JIRA_ISSUE_IGNORE_PREFIXES, "PSR,RFC");
        PropertyReader.setOverrides(overrides);
        try {
            String text = "PROJ-860 uses PSR-18 and RFC-6749";
            Set<String> result = IssuesIDsParser.extractAllJiraIDs(text);

            assertEquals(1, result.size());
            assertTrue(result.contains("PROJ-860"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    public void testExtractAllJiraIDsAllowedListFromPropertyReaderConfig() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(PropertyReader.JIRA_ISSUE_ALLOWED_PREFIXES, "TEAM");
        PropertyReader.setOverrides(overrides);
        try {
            String text = "PROJ-860 and TEAM-123";
            Set<String> result = IssuesIDsParser.extractAllJiraIDs(text);

            assertEquals(1, result.size());
            assertTrue(result.contains("TEAM-123"));
        } finally {
            PropertyReader.clearOverrides();
        }
    }
}