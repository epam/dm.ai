// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ICommentTest {

    @Test
    public void testCheckCommentStartedWith() throws IOException {
        IBody body1 = Mockito.mock(IBody.class);
        IBody body2 = Mockito.mock(IBody.class);
        IBody body3 = Mockito.mock(IBody.class);

        Mockito.when(body1.getBody()).thenReturn("This is a comment");
        Mockito.when(body2.getBody()).thenReturn("<p>This is another comment</p>");
        Mockito.when(body3.getBody()).thenReturn("Yet another comment");

        List<IBody> comments = Arrays.asList(body1, body2, body3);

        String result = IComment.Impl.checkCommentStartedWith(comments, "This is");
        assertEquals("This is a comment", result);

        result = IComment.Impl.checkCommentStartedWith(comments, "Yet another");
        assertEquals("Yet another comment", result);

        result = IComment.Impl.checkCommentStartedWith(comments, "Non-existent");
        assertNull(result);
    }
}