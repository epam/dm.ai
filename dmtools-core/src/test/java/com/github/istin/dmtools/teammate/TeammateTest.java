// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.TrackerParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TeammateTest {

    private Teammate teammate;
    private TrackerClient trackerClient;

    @Before
    public void setUp() {
        teammate = new Teammate();
        trackerClient = mock(TrackerClient.class);
        when(trackerClient.tag("initiator-123")).thenReturn("[~initiator-123]");
    }

    @Test
    public void testBuildCompletionComment_WithInitiator_TagsInitiator() {
        String result = Teammate.buildCompletionComment("initiator-123", "https://ci.example.com/run/42", trackerClient);

        assertEquals("[~initiator-123], \n\n✅ Teammate run completed. CI Run: https://ci.example.com/run/42", result);
        verify(trackerClient).tag("initiator-123");
    }

    @Test
    public void testBuildCompletionComment_WithoutInitiator_NoTag() {
        String result = Teammate.buildCompletionComment(null, "https://ci.example.com/run/42", trackerClient);

        assertEquals("✅ Teammate run completed. CI Run: https://ci.example.com/run/42", result);
        verify(trackerClient, never()).tag(anyString());
    }

    @Test
    public void testBuildCompletionComment_EmptyInitiator_NoTag() {
        String result = Teammate.buildCompletionComment("", "https://ci.example.com/run/42", trackerClient);

        assertEquals("✅ Teammate run completed. CI Run: https://ci.example.com/run/42", result);
        verify(trackerClient, never()).tag(anyString());
    }

    @Test
    public void testPostCompletionComment_WithCiRunUrl_PostsComment() throws IOException {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        params.setOutputType(TrackerParams.OutputType.comment);

        teammate.postCompletionComment(trackerClient, "PROJ-123", "https://ci.example.com/run/42", "initiator-123", params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("PROJ-123"), commentCaptor.capture());
        String postedComment = commentCaptor.getValue();
        assertTrue(postedComment.contains("Teammate run completed"));
        assertTrue(postedComment.contains("https://ci.example.com/run/42"));
        assertTrue(postedComment.contains("[~initiator-123]"));
    }

    @Test
    public void testPostCompletionComment_NoCiRunUrl_DoesNothing() throws IOException {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        params.setOutputType(TrackerParams.OutputType.comment);

        teammate.postCompletionComment(trackerClient, "PROJ-123", null, "initiator-123", params);
        teammate.postCompletionComment(trackerClient, "PROJ-123", "", "initiator-123", params);

        verify(trackerClient, never()).postComment(anyString(), anyString());
    }

    @Test
    public void testPostCompletionComment_OutputTypeNone_DoesNotPost() throws IOException {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        params.setOutputType(TrackerParams.OutputType.none);

        teammate.postCompletionComment(trackerClient, "PROJ-123", "https://ci.example.com/run/42", "initiator-123", params);

        verify(trackerClient, never()).postComment(anyString(), anyString());
    }

    @Test
    public void testPostCompletionComment_TagException_PostsWithoutTag() throws IOException {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        params.setOutputType(TrackerParams.OutputType.comment);
        doThrow(new RuntimeException("tag failed")).when(trackerClient).tag(anyString());

        teammate.postCompletionComment(trackerClient, "PROJ-123", "https://ci.example.com/run/42", "initiator-123", params);

        ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
        verify(trackerClient).postComment(eq("PROJ-123"), commentCaptor.capture());
        String postedComment = commentCaptor.getValue();
        assertTrue(postedComment.contains("Teammate run completed"));
        assertTrue(postedComment.contains("https://ci.example.com/run/42"));
        assertFalse(postedComment.contains("[~initiator-123]"));
    }
}
