// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class FileConfig {

    /**
     * Read file content from either file system or resources
     * First checks if file exists in file system, if not then checks resources
     *
     * @param filePath file path or name
     * @return content as String
     * @throws RuntimeException if file not found in both locations or error reading file
     */
    public String readFile(String filePath) {
        // First try to read from file system
        if (Files.exists(Path.of(filePath))) {
            return readFromFileSystem(filePath);
        }

        // If not found in file system, try resources
        // Remove any leading slashes or path separators for resource loading
        String resourcePath = filePath.replaceFirst("^/+", "");
        if (resourceExists(resourcePath)) {
            return readFromResources(resourcePath);
        }

        return null;
    }

    private String readFromFileSystem(String filePath) {
        try {
            return Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file from file system: " + filePath, e);
        }
    }

    private String readFromResources(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream("/" + resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    private boolean resourceExists(String resourcePath) {
        return getClass().getResource("/" + resourcePath) != null;
    }
}
