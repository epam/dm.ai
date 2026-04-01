// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket;

import com.github.istin.dmtools.common.code.SourceCode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BasicBitbucketTest {

    private BasicBitbucket basicBitbucket;

    @Before
    public void setUp() throws IOException {
        basicBitbucket = Mockito.spy(new BasicBitbucket());
    }


    @Test
    public void testGetInstance() throws IOException {
        SourceCode instance1 = BasicBitbucket.getInstance();
        SourceCode instance2 = BasicBitbucket.getInstance();
        assertSame(instance1, instance2);
    }

    private void setPrivateStaticField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = BasicBitbucket.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            fail("Failed to set private static field: " + e.getMessage());
        }
    }
}