// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.file;

import org.junit.Test;
import org.mockito.Mockito;

public class FileContentListenerTest {

    @Test
    public void testOnFileRead() throws Exception {
        // Create a mock of the FileContentListener
        FileContentListener listener = Mockito.mock(FileContentListener.class);

        // Define test parameters
        String folderPath = "test/folder";
        String packageName = "com.example";
        String fileName = "TestFile.java";
        String fileContent = "public class TestFile {}";

        // Call the method
        listener.onFileRead(folderPath, packageName, fileName, fileContent);

        // Verify that the method was called with the correct parameters
        Mockito.verify(listener).onFileRead(folderPath, packageName, fileName, fileContent);
    }
}