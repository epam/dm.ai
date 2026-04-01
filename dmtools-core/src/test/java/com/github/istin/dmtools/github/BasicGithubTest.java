// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.SourceCode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class BasicGithubTest {

    private BasicGithub basicGithub;

    @Before
    public void setUp() throws IOException {
        basicGithub = new BasicGithub();
    }


    @Test
    public void testGetInstance() throws IOException {
        try (MockedStatic<BasicGithub> mockedStatic = mockStatic(BasicGithub.class)) {
            mockedStatic.when(BasicGithub::getInstance).thenReturn(mock(SourceCode.class));
            SourceCode instance = BasicGithub.getInstance();
            assertNotNull(instance);
        }
    }

}