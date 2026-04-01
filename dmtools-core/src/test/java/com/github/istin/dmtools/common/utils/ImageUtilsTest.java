// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Test;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class ImageUtilsTest {

    @Test
    public void testGetExtension() {
        File mockFile = mock(File.class);
        when(mockFile.getName()).thenReturn("image.png");
        String extension = ImageUtils.getExtension(mockFile);
        assertEquals("png", extension);

        when(mockFile.getName()).thenReturn("document.pdf");
        extension = ImageUtils.getExtension(mockFile);
        assertEquals("pdf", extension);

        when(mockFile.getName()).thenReturn("noextension");
        extension = ImageUtils.getExtension(mockFile);
        assertEquals("", extension);
    }

}