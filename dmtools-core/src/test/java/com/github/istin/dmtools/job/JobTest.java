// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobTest {

    @Test
    public void testRunJob() throws Exception {
        // Create a mock of the Job interface
        Job<Object, Object> jobMock = Mockito.mock(Job.class);

        // Define a parameter object
        Object params = new Object();

        // Call the runJob method
        jobMock.runJob(params);

        // Verify that runJob was called with the correct parameters
        verify(jobMock).runJob(params);
    }

    @Test
    public void testGetName() {
        // Create a mock of the Job interface
        Job<Object, Object> jobMock = Mockito.mock(Job.class);

        // Define the expected name
        String expectedName = "TestJob";

        // Stub the getName method
        when(jobMock.getName()).thenReturn(expectedName);

        // Call the getName method
        String actualName = jobMock.getName();

        // Verify the result
        assertEquals(expectedName, actualName);
    }

    @Test
    public void testGetParamsClass() {
        // Create a mock of the Job interface
        Job<Object, Object> jobMock = Mockito.mock(Job.class);

        // Define the expected class
        Class<Object> expectedClass = Object.class;

        // Stub the getParamsClass method
        when(jobMock.getParamsClass()).thenReturn(expectedClass);

        // Call the getParamsClass method
        Class<Object> actualClass = jobMock.getParamsClass();

        // Verify the result
        assertEquals(expectedClass, actualClass);
    }
}