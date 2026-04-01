// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation.area;

import com.github.istin.dmtools.common.model.Key;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class KeyAreaMapperTest {

    private KeyAreaMapper keyAreaMapper;
    private Key key;

    @Before
    public void setUp() {
        keyAreaMapper = mock(KeyAreaMapper.class);
        key = mock(Key.class);
    }

    @Test
    public void testGetAreaForTicket() throws IOException {
        String expectedArea = "TestArea";
        when(keyAreaMapper.getAreaForTicket(key)).thenReturn(expectedArea);

        String actualArea = keyAreaMapper.getAreaForTicket(key);
        assertEquals(expectedArea, actualArea);

        verify(keyAreaMapper, times(1)).getAreaForTicket(key);
    }

    @Test
    public void testSetAreaForTicket() throws IOException {
        String area = "NewArea";

        doNothing().when(keyAreaMapper).setAreaForTicket(key, area);

        keyAreaMapper.setAreaForTicket(key, area);

        verify(keyAreaMapper, times(1)).setAreaForTicket(key, area);
    }
}