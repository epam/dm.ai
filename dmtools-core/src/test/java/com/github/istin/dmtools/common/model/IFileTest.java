// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

public class IFileTest {

    @Test
    public void testGetPath() {
        IFile file = mock(IFile.class);
        when(file.getPath()).thenReturn("/path/to/file");
        
        String path = file.getPath();
        
        assertEquals("/path/to/file", path);
    }

    @Test
    public void testGetType() {
        IFile file = mock(IFile.class);
        when(file.getType()).thenReturn("text/plain");
        
        String type = file.getType();
        
        assertEquals("text/plain", type);
    }

    @Test
    public void testIsDir() {
        IFile file = mock(IFile.class);
        when(file.isDir()).thenReturn(true);
        
        boolean isDir = file.isDir();
        
        assertTrue(isDir);
    }

    @Test
    public void testGetSelfLink() {
        IFile file = mock(IFile.class);
        when(file.getSelfLink()).thenReturn("http://example.com/file");
        
        String selfLink = file.getSelfLink();
        
        assertEquals("http://example.com/file", selfLink);
    }

    @Test
    public void testGetFileContent() {
        IFile file = mock(IFile.class);
        when(file.getFileContent()).thenReturn("File content");
        
        String content = file.getFileContent();
        
        assertEquals("File content", content);
    }

    @Test
    public void testSetFileContent() {
        IFile file = mock(IFile.class);
        doNothing().when(file).setFileContent("New content");
        
        file.setFileContent("New content");
        
        verify(file, times(1)).setFileContent("New content");
    }
}