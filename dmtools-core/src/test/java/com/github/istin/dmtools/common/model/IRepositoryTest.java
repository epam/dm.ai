// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;

public class IRepositoryTest {

    @Test
    public void testGetName() {
        // Create a mock of IRepository
        IRepository repositoryMock = Mockito.mock(IRepository.class);

        // Define the behavior of getName() method
        Mockito.when(repositoryMock.getName()).thenReturn("Test Repository");

        // Verify the behavior
        String name = repositoryMock.getName();
        assertEquals("Test Repository", name);

        // Verify that getName() was called
        Mockito.verify(repositoryMock).getName();
    }
}