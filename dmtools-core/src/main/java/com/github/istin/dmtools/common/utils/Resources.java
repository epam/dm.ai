// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Resources {
    private static final Logger logger = LogManager.getLogger(Resources.class);

    public static String readSpecificResource(String resourceName) {
        try {
            InputStream is = Resources.class.getClassLoader().getResourceAsStream(resourceName);
            if (is == null) {
                logger.error("Resource not found: {}", resourceName);
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (Exception e) {
            logger.error("Failed to read resource: {}", resourceName, e);
            throw new RuntimeException("Failed to read resource: " + resourceName, e);
        }
    }
} 