// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

public class ImageUtils {

    public static String getExtension(File file) {
        // Get the file extension
        String extension = "";
        String fileName = file.getName();
        int i = fileName.lastIndexOf('.');
        if (i >= 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }
    public static String convertToBase64(File imageFile, String formatName) throws IOException {
        // Read the image from the file
        BufferedImage image = ImageIO.read(imageFile);
        // Convert the image to a base64 string
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    public static int[] getImageDimensions(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        return new int[] {width, height};
    }

    /**
     * Gets the MIME type for an image file based on its extension.
     * Supports JPEG, PNG, GIF, and WebP formats.
     * @param imageFile The image file
     * @return The MIME type (e.g., "image/jpeg", "image/png")
     */
    public static String getMimeType(File imageFile) {
        String extension = getExtension(imageFile).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                // Default to JPEG if unknown
                return "image/jpeg";
        }
    }


}
