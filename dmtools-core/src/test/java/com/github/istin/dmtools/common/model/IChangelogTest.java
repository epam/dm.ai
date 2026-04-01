// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class IChangelogTest {

    @Test
    public void testGetHistories() {
        // Create a mock of IChangelog
        IChangelog changelog = mock(IChangelog.class);

        // Create a concrete list of IHistory and add a mock IHistory to it
        List<IHistory> mockHistories = new ArrayList<>();
        mockHistories.add(mock(IHistory.class));

        // Define behavior for getHistories method
        doReturn(mockHistories).when(changelog).getHistories();

        // Call the method and assert the result
        List<? extends IHistory> histories = changelog.getHistories();
        assertNotNull("Histories should not be null", histories);
        assertEquals("Histories should match the mock list", mockHistories, histories);
    }
}