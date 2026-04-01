// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

/**
 * Example implementation of TokenCounter for Claude 3.5
 * You would need to implement actual token counting logic here
 */
public class Claude35TokenCounter implements TokenCounter {
    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Minimum token count for any non-empty string
        int baseTokenCount = 8;

        if (text.length() == 1) {
            return baseTokenCount;
        }

        // First, handle JSON structure if present
        int jsonSyntaxTokens = 0;
        jsonSyntaxTokens += countOccurrences(text, "{");
        jsonSyntaxTokens += countOccurrences(text, "}");
        jsonSyntaxTokens += countOccurrences(text, ":");
        jsonSyntaxTokens += countOccurrences(text, "\"");
        jsonSyntaxTokens += countOccurrences(text, "\\");

        int totalTokens = baseTokenCount + jsonSyntaxTokens;

        // Process the text character by character
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Special characters count as 1 token each
            if (c == '/' || c == '-' || c == '_' || c == '.' || c == ' ') {
                // If we have a word before this special character, count it
                if (currentWord.length() > 0) {
                    totalTokens += 1;  // Each word is 1 token
                    currentWord = new StringBuilder();
                }
                totalTokens += 1;  // Special character is 1 token
            }
            // Each uppercase letter adds an extra token
            else if (Character.isUpperCase(c)) {
                currentWord.append(c);
                totalTokens += 1;  // Extra token for uppercase letter
            }
            // Regular character, add to current word
            else {
                currentWord.append(c);
            }
        }

        // Don't forget the last word if there is one
        if (currentWord.length() > 0) {
            totalTokens += 1;
        }

        return totalTokens;
    }

    private int countOccurrences(String text, String substring) {
        return (text.length() - text.replace(substring, "").length()) / substring.length();
    }
}
