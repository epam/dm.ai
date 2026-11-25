// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIResponseParser {

    private static final Logger logger = LogManager.getLogger(AIResponseParser.class);

    private static final Pattern BOOLEAN_WITH_EXPLANATION_PATTERN = Pattern.compile(
            "^\\s*[*_`\"'\\[\\(>\\-\\s]*(true|false)(?![a-zA-Z0-9])[*_`\"'\\]\\)\\s.]*(?:[:,\\-\\s]\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern BOOLEAN_TOKEN_PATTERN = Pattern.compile(
            "(?<![a-zA-Z0-9])(true|false)(?![a-zA-Z0-9])",
            Pattern.CASE_INSENSITIVE
    );

    public static class BooleanWithExplanation {
        public final boolean value;
        public final String explanation;

        public BooleanWithExplanation(boolean value, String explanation) {
            this.value = value;
            this.explanation = explanation;
        }
    }

    public static BooleanWithExplanation parseBooleanWithExplanation(String response) {
        String trimmedResponse = stripCodeFence(response);
        if (trimmedResponse.isEmpty()) {
            logger.warn("No valid boolean value found in empty/null AI response; returning false.");
            return new BooleanWithExplanation(false, null);
        }
        Matcher matcher = BOOLEAN_WITH_EXPLANATION_PATTERN.matcher(trimmedResponse);
        if (matcher.find()) {
            if ("true".equalsIgnoreCase(matcher.group(1))) {
                String explanation = cleanExplanation(matcher.group(2));
                return new BooleanWithExplanation(true, explanation);
            } else {
                return new BooleanWithExplanation(false, null);
            }
        }

        Matcher fallbackMatcher = BOOLEAN_TOKEN_PATTERN.matcher(trimmedResponse);
        if (fallbackMatcher.find()) {
            if ("true".equalsIgnoreCase(fallbackMatcher.group(1))) {
                String remainder = trimmedResponse.substring(fallbackMatcher.end());
                String explanation = cleanExplanation(remainder.replaceFirst("^[*_`\"'\\]\\)\\s.,:\\-]+", ""));
                return new BooleanWithExplanation(true, explanation);
            } else {
                return new BooleanWithExplanation(false, null);
            }
        }

        logger.warn("No valid boolean value found in the AI response '{}'; returning false.", trimmedResponse);
        return new BooleanWithExplanation(false, null);
    }

    private static String stripCodeFence(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = cleanMarkdowns(trimmed);
        }
        return trimmed.trim();
    }

    private static String cleanExplanation(String rawExplanation) {
        if (rawExplanation == null) {
            return null;
        }
        String cleaned = rawExplanation.trim()
                .replaceFirst("^[*_`\"'\\s]+", "")
                .replaceFirst("[*_`\"'\\s]+$", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static boolean parseBooleanResponse(String response) {
        String trimmedResponse = stripCodeFence(response);
        if (trimmedResponse.isEmpty()) {
            logger.warn("No valid boolean value found in empty/null AI response; returning false.");
            return false;
        }

        Matcher matcher = BOOLEAN_TOKEN_PATTERN.matcher(trimmedResponse);
        if (matcher.find()) {
            return "true".equalsIgnoreCase(matcher.group(1));
        }

        String lowerResponse = trimmedResponse.toLowerCase();
        int trueIndex = lowerResponse.indexOf("true");
        int falseIndex = lowerResponse.indexOf("false");
        if (trueIndex != -1 && (falseIndex == -1 || trueIndex < falseIndex)) {
            return true;
        } else if (falseIndex != -1) {
            return false;
        }

        logger.warn("No valid boolean value found in the AI response '{}'; returning false.", trimmedResponse);
        return false;
    }

    public static JSONArray parseResponseAsJSONArray(String response) throws JSONException {
        if (response == null) {
            throw new JSONException("Response cannot be null");
        }
        
        // Find the start position for JSON array
        int startIndex = response.indexOf('[');
        int endIndex = response.lastIndexOf(']');

        // Check if valid indexes are found
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            String jsonString = response.substring(startIndex, endIndex + 1);
            return new JSONArray(jsonString);
        } else {
            throw new JSONException("No valid JSON array found in the response.");
        }
    }

    public static JSONObject parseResponseAsJSONObject(String response) throws JSONException {
        if (response == null) {
            throw new JSONException("Response cannot be null");
        }
        
        // Find the start position for JSON object
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');

        // Check if valid indexes are found
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            String jsonString = response.substring(startIndex, endIndex + 1);
            return new JSONObject(jsonString);
        } else {
            throw new JSONException("No valid JSON object found in the response.");
        }
    }

    public static String parseCodeResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new IllegalArgumentException("Response text cannot be null or empty.");
        }

        // Pattern to match code blocks with or without language identifier
        Pattern pattern = Pattern.compile("```(?:\\w*\\s*)?\n?(.*?)\n?```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String codeBlock = matcher.group(1).trim();

            // Remove any leading or trailing whitespace and newlines
            codeBlock = codeBlock.replaceAll("^\\s+|\\s+$", "");

            return codeBlock;
        } else {
            // If no code block markers found, return the trimmed original response
            return response.trim();
        }
    }

    public static List<String> parseCodeExamples(String response, String startDelimiter, String endDelimiter) throws IllegalArgumentException {
        List<String> codeExamples = new ArrayList<>();
        if (response == null || response.isEmpty()) {
            throw new IllegalArgumentException("Response text cannot be null or empty.");
        }
        if (startDelimiter == null || endDelimiter == null) {
            throw new IllegalArgumentException("Delimiters cannot be null.");
        }

        // Pattern to match code blocks including the language identifier
        Pattern pattern = Pattern.compile(Pattern.quote(startDelimiter) + "(?:\\w+\\s*)?(.*?)" + Pattern.quote(endDelimiter), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String codeBlock = matcher.group(1).trim();

            // Remove any leading newline if present
            if (codeBlock.startsWith("\n")) {
                codeBlock = codeBlock.substring(1);
            }

            // Remove any trailing newline if present
            if (codeBlock.endsWith("\n")) {
                codeBlock = codeBlock.substring(0, codeBlock.length() - 1);
            }

            // Clean markdowns from start and end
            codeBlock = cleanMarkdowns(codeBlock);

            if (!codeBlock.isEmpty()) {
                codeExamples.add(codeBlock);
            }
        }

        if (codeExamples.isEmpty()) {
            throw new IllegalArgumentException("No valid code blocks found between the specified delimiters.");
        }

        return codeExamples;
    }

    private static String cleanMarkdowns(String codeBlock) {
        // Remove markdown code block indicators from start and end
        codeBlock = codeBlock.replaceAll("^```\\w*\\s*", "");
        codeBlock = codeBlock.replaceAll("```\\s*$", "");

        // Remove any leading or trailing whitespace
        return codeBlock.trim();
    }

    public static List<String> parseCodeExamples(String response) {
        return parseCodeExamples(response, "@jai_generated_code", "@jai_generated_code");
    }

}
