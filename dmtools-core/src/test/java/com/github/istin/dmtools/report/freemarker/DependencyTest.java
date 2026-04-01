// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DependencyTest {

    private Dependency dependency;

    @Before
    public void setUp() {
        dependency = new Dependency();
    }

    @Test
    public void testGetAndSetKey() {
        String key = "key123";
        dependency.setKey(key);
        assertEquals(key, dependency.getKey());
    }

    @Test
    public void testGetAndSetUrl() {
        String url = "http://example.com";
        dependency.setUrl(url);
        assertEquals(url, dependency.getUrl());
    }

    @Test
    public void testGetAndSetStatus() {
        String status = "active";
        dependency.setStatus(status);
        assertEquals(status, dependency.getStatus());
    }

    @Test
    public void testGetAndSetCreated() {
        String created = "2023-10-01";
        dependency.setCreated(created);
        assertEquals(created, dependency.getCreated());
    }

    @Test
    public void testGetAndSetAssignee() {
        Assignee assignee = mock(Assignee.class);
        dependency.setAssignee(assignee);
        assertEquals(assignee, dependency.getAssignee());
    }

    @Test
    public void testGetAndSetPriority() {
        int priority = 5;
        dependency.setPriority(priority);
        assertEquals(priority, dependency.getPriority());
    }

    @Test
    public void testGetAndSetDueDate() {
        String dueDate = "2023-12-31";
        dependency.setDueDate(dueDate);
        assertEquals(dueDate, dependency.getDueDate());
    }

    @Test
    public void testGetAndSetTitle() {
        String title = "Dependency Title";
        dependency.setTitle(title);
        assertEquals(title, dependency.getTitle());
    }

    @Test
    public void testGetAndSetType() {
        String type = "bug";
        dependency.setType(type);
        assertEquals(type, dependency.getType());
    }

    @Test
    public void testGetBlockedItems() {
        List<Ticket> blockedItems = new ArrayList<>();
        dependency.getBlockedItems().addAll(blockedItems);
        assertEquals(blockedItems, dependency.getBlockedItems());
    }

    @Test
    public void testCompareTo() {
        Dependency otherDependency = new Dependency();
        otherDependency.setPriority(10);
        dependency.setPriority(5);
        assertEquals(-5, dependency.compareTo(otherDependency));
    }

    @Test
    public void testConstructorWithParameters() {
        String key = "key123";
        String url = "http://example.com";
        String status = "active";
        String type = "bug";

        Dependency dependencyWithParams = new Dependency(key, url, status, type);

        assertEquals(key, dependencyWithParams.getKey());
        assertEquals(url, dependencyWithParams.getUrl());
        assertEquals(status, dependencyWithParams.getStatus());
        assertEquals(type, dependencyWithParams.getType());
    }
}