// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class FigmaCommentsMetricSourceTest {

    private IEmployees employeesMock;
    private FigmaClient figmaClientMock;
    private List<String> peopleToFilterOut;
    private String[] files;
    private FigmaCommentsMetricSource figmaCommentsMetricSource;

    @Before
    public void setUp() {
        employeesMock = mock(IEmployees.class);
        figmaClientMock = mock(FigmaClient.class);
        peopleToFilterOut = new ArrayList<>();
        files = new String[]{"file1", "file2"};
        figmaCommentsMetricSource = new FigmaCommentsMetricSource(employeesMock, peopleToFilterOut, figmaClientMock, files);
    }


    @Test
    public void testPerformSourceCollectionWithNoComments() throws Exception {
        when(figmaClientMock.getComments("file1")).thenReturn(new ArrayList<>());
        when(figmaClientMock.getComments("file2")).thenReturn(new ArrayList<>());

        List<KeyTime> result = figmaCommentsMetricSource.performSourceCollection(true, "metricName");

        assertEquals(0, result.size());
        verify(figmaClientMock, times(1)).getComments("file1");
        verify(figmaClientMock, times(1)).getComments("file2");
    }
}