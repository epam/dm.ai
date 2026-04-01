// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JSONResourceReaderTest {

    private JSONResourceReader jsonResourceReader;
    private static final String TEST_FILE = "test.json";

    @Before
    public void setUp() {
        jsonResourceReader = JSONResourceReader.getInstance(TEST_FILE);
    }

    @Test
    public void testGetInstance() {
        JSONResourceReader instance1 = JSONResourceReader.getInstance(TEST_FILE);
        JSONResourceReader instance2 = JSONResourceReader.getInstance(TEST_FILE);
        assertSame(instance1, instance2);
    }


    @Test
    public void testConvertInputStreamToString() throws IOException {
        String expected = "test string";
        InputStream inputStream = new ByteArrayInputStream(expected.getBytes());
        String result = JSONResourceReader.convertInputStreamToString(inputStream);
        assertEquals(expected, result);
    }

}