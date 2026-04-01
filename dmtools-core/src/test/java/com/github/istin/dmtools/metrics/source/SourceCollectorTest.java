// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class SourceCollectorTest {

    @Test
    public void testPerformSourceCollection() throws Exception {
        // Create a mock of the SourceCollector interface
        SourceCollector sourceCollector = Mockito.mock(SourceCollector.class);

        // Define the behavior of the mock for the performSourceCollection method
        when(sourceCollector.performSourceCollection(anyBoolean(), anyString())).thenReturn(mock(List.class));

        // Call the method under test
        List<KeyTime> result = sourceCollector.performSourceCollection(true, "metricName");

        // Verify the method was called with the expected parameters
        verify(sourceCollector).performSourceCollection(true, "metricName");

        // Assert that the result is not null
        assertNotNull(result);
    }
}