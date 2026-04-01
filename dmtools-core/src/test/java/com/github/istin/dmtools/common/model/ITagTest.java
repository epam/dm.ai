// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ITagTest {

    @Test
    public void testGetName() {
        // Create a mock instance of ITag
        ITag tag = mock(ITag.class);

        // Define the behavior of getName() method
        when(tag.getName()).thenReturn("SampleTagName");

        // Verify the getName() method
        assertEquals("SampleTagName", tag.getName());
    }
}