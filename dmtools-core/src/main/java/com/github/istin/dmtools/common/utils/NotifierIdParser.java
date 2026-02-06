package com.github.istin.dmtools.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotifierIdParser {

    /**
     * Parses notifier ID from Jira comment format.
     * Extracts account ID from format: [~accountid:...]
     * 
     * @param taggedString The comment body containing the tagged user
     * @return The account ID if found, empty string otherwise
     */
    public static String parseNotifierId(String taggedString) {
        if (taggedString == null || taggedString.isEmpty()) {
            return "";
        }

        try {
            // Pattern for extracting everything after ~accountid: and before ]
            Pattern pattern = Pattern.compile("\\[~accountid:([^\\]]+)\\]");
            Matcher matcher = pattern.matcher(taggedString);

            if (matcher.find()) {
                return matcher.group(1); // Returns everything between ~accountid: and ]
            }
        } catch (Exception e) {
            // Log error if needed
            return "";
        }

        return "";
    }
}

