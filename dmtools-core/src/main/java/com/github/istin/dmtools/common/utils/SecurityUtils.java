// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * Utility class for security-related operations, particularly for protecting sensitive information in logs
 */
public class SecurityUtils {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(token[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(password[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_PATTERN = Pattern.compile("(key[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("(GITHUB_TOKEN[=:\\s]+)([^\\s]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Masks sensitive values (tokens, passwords, keys) inside a command string for safe logging.
     * Replaces matched values with *** while preserving the rest of the command.
     *
     * @param command the raw command string
     * @return the command with sensitive values replaced by ***
     */
    public static String maskCommand(String command) {
        if (command == null) {
            return null;
        }
        String masked = command;
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1***");
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll("$1***");
        masked = KEY_PATTERN.matcher(masked).replaceAll("$1***");
        masked = GITHUB_TOKEN_PATTERN.matcher(masked).replaceAll("$1***");
        return masked;
    }

    /**
     * Creates a masked version of the config JSON for safe logging by replacing sensitive values with asterisks
     * @param originalConfig The original configuration JSON that may contain sensitive information
     * @return A deep copy of the configuration with sensitive values masked
     */
    public static JSONObject maskSensitiveInformation(JSONObject originalConfig) {
        if (originalConfig == null) {
            return null;
        }
        
        JSONObject maskedConfig = new JSONObject(originalConfig.toString()); // Deep copy
        
        if (maskedConfig.has("secrets")) {
            JSONObject secrets = maskedConfig.getJSONObject("secrets");
            // Mask all secret keys
            for (String key : secrets.keySet()) {
                if (isSensitiveKey(key)) {
                    String originalValue = secrets.optString(key, "");
                    if (!originalValue.isEmpty()) {
                        secrets.put(key, maskValue(originalValue));
                    }
                }
            }
        }
        
        // Also check for any top-level keys that might be sensitive
        for (String key : maskedConfig.keySet()) {
            if (isSensitiveKey(key) && !key.equals("secrets")) {
                String originalValue = maskedConfig.optString(key, "");
                if (!originalValue.isEmpty()) {
                    maskedConfig.put(key, maskValue(originalValue));
                }
            }
        }
        
        return maskedConfig;
    }

    /**
     * Determines if a key name indicates it contains sensitive information
     * @param key The key name to check
     * @return true if the key is likely to contain sensitive information
     */
    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        
        String upperKey = key.toUpperCase();
        return upperKey.contains("KEY") || 
               upperKey.contains("TOKEN") || 
               upperKey.contains("PASSWORD") || 
               upperKey.contains("SECRET") ||
               upperKey.contains("AUTH") ||
               upperKey.contains("CREDENTIAL") ||
               upperKey.contains("PASS");
    }

    /**
     * Masks a sensitive value by showing only the first few characters
     * @param value The value to mask
     * @return A masked version of the value
     */
    private static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "****";
        }
        
        // For very short values, mask completely
        if (value.length() <= 4) {
            return "****";
        }
        
        // For longer values, show first 2 characters only (enough for prefix identification,
        // insufficient for brute-force discrimination) and always append a fixed-width mask.
        return value.substring(0, Math.min(2, value.length())) + "****";
    }

    /**
     * Masks a plain string value for safe logging
     * @param value The value to mask
     * @return A masked version of the value
     */
    public static String maskSensitiveValue(String value) {
        return maskValue(value);
    }
} 