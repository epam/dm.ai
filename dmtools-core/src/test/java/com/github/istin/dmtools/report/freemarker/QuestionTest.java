// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QuestionTest {

    private Question question;

    @Before
    public void setUp() {
        question = new Question();
    }

    @Test
    public void testGetAndSetRelease() {
        int release = 2023;
        question.setRelease(release);
        assertEquals(release, question.getRelease());
    }

    @Test
    public void testGetAndSetName() {
        String name = "Sample Name";
        question.setName(name);
        assertEquals(name, question.getName());
    }

    @Test
    public void testGetAndSetKey() {
        String key = "Q123";
        question.setKey(key);
        assertEquals(key, question.getKey());
    }

    @Test
    public void testGetAndSetStatus() {
        String status = "Open";
        question.setStatus(status);
        assertEquals(status, question.getStatus());
    }

    @Test
    public void testGetAndSetAssignee() {
        Assignee assignee = new Assignee("Name", "Email");
        question.setAssignee(assignee);
        assertEquals(assignee, question.getAssignee());
    }

    @Test
    public void testGetAndSetUrl() {
        String url = "http://example.com";
        question.setUrl(url);
        assertEquals(url, question.getUrl());
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, question.getRelease());
        assertNull(question.getName());
        assertNull(question.getKey());
        assertNull(question.getStatus());
        assertNull(question.getAssignee());
        assertNull(question.getUrl());
    }
}