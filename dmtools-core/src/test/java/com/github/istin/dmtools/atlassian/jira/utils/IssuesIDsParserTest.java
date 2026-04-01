// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import org.junit.Test;
import org.mockito.Mockito;
import org.apache.logging.log4j.Logger;

import java.util.List;
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
}