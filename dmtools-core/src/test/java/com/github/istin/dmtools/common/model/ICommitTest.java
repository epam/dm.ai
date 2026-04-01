// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class ICommitTest {

    @Test
    public void testGetId() {
        ICommit commit = Mockito.mock(ICommit.class);
        when(commit.getId()).thenReturn("12345");
        assertEquals("12345", commit.getId());
    }

    @Test
    public void testGetHash() {
        ICommit commit = Mockito.mock(ICommit.class);
        when(commit.getHash()).thenReturn("abcde12345");
        assertEquals("abcde12345", commit.getHash());
    }

    @Test
    public void testGetMessage() {
        ICommit commit = Mockito.mock(ICommit.class);
        when(commit.getMessage()).thenReturn("Initial commit");
        assertEquals("Initial commit", commit.getMessage());
    }

    @Test
    public void testGetAuthor() {
        ICommit commit = Mockito.mock(ICommit.class);
        IUser author = Mockito.mock(IUser.class);
        when(commit.getAuthor()).thenReturn(author);
        assertEquals(author, commit.getAuthor());
    }

    @Test
    public void testGetCommiterTimestamp() {
        ICommit commit = Mockito.mock(ICommit.class);
        when(commit.getCommiterTimestamp()).thenReturn(1622548800000L);
        assertEquals(Long.valueOf(1622548800000L), commit.getCommiterTimestamp());
    }

    @Test
    public void testGetCommitterDate() {
        ICommit commit = Mockito.mock(ICommit.class);
        Calendar calendar = Calendar.getInstance();
        when(commit.getCommitterDate()).thenReturn(calendar);
        assertEquals(calendar, commit.getCommitterDate());
    }

    @Test
    public void testUtilsGetComitterDate() {
        ICommit commit = Mockito.mock(ICommit.class);
        long timestamp = 1622548800000L;
        when(commit.getCommiterTimestamp()).thenReturn(timestamp);

        Calendar expectedCalendar = Calendar.getInstance();
        expectedCalendar.setTimeInMillis(timestamp);

        Calendar actualCalendar = ICommit.Utils.getComitterDate(commit);
        assertEquals(expectedCalendar.getTimeInMillis(), actualCalendar.getTimeInMillis());
    }
}