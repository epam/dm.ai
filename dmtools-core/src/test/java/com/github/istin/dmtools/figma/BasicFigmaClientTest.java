// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class BasicFigmaClientTest {

    private PropertyReader propertyReaderMock;

    @Before
    public void setUp() {
        propertyReaderMock = mock(PropertyReader.class);
        doReturn("mockBasePath").when(propertyReaderMock).getFigmaBasePath();
        doReturn("mockApiKey").when(propertyReaderMock).getFigmaApiKey();
    }


}