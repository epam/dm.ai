// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

public class RallyIssueTypeTest {

    @Test
    public void testQueryFilterByType() {
        String query = "SELECT * FROM issues";
        String[] types = {"Defect", "Task"};
        String expected = "SELECT * FROM issues _byTypes=Defect,Task";
        
        String result = RallyIssueType.queryFilterByType(query, types);
        
        assertEquals(expected, result);
    }

    @Test
    public void testParseQueryWithTypes() {
        String query = "SELECT * FROM issues _byTypes=Defect,Task";
        
        RallyIssueType.QueryAndTypes result = RallyIssueType.parseQuery(query);
        
        assertEquals("SELECT * FROM issues", result.getQuery());
        List<String> expectedTypes = Arrays.asList("Defect", "Task");
        assertEquals(expectedTypes, result.getTypes());
    }

    @Test
    public void testParseQueryWithoutTypes() {
        String query = "SELECT * FROM issues";
        
        RallyIssueType.QueryAndTypes result = RallyIssueType.parseQuery(query);
        
        assertEquals("SELECT * FROM issues", result.getQuery());
        assertNull(result.getTypes());
    }
}