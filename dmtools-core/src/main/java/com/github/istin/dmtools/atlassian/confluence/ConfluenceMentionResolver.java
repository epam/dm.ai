// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces user mentions ({@code <ac:link><ri:user ri:account-id="..."/></ac:link>}) in storage
 * format with {@code @Display Name}. Storage only carries the account id, so without this step
 * mentions end up as an anonymous "link" in the converted text.
 */
public class ConfluenceMentionResolver {

    private static final Logger logger = LogManager.getLogger(ConfluenceMentionResolver.class);

    private static final Pattern USER_MENTION = Pattern.compile(
            "<ac:link[^>]*>\\s*<ri:user\\b[^>]*?ri:account-id=\"([^\"]+)\"[^>]*?/?>\\s*(?:</ri:user>)?\\s*</ac:link>",
            Pattern.DOTALL);

    private final Confluence confluence;
    private final Map<String, String> nameCache = new HashMap<>();

    public ConfluenceMentionResolver(Confluence confluence) {
        this.confluence = confluence;
    }

    public String resolve(String storageHtml) {
        if (confluence == null || storageHtml == null || !storageHtml.contains("ri:user")) {
            return storageHtml;
        }
        Matcher matcher = USER_MENTION.matcher(storageHtml);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(storageHtml, last, matcher.start());
            String name = displayName(matcher.group(1));
            result.append(name != null ? escape("@" + name) : matcher.group());
            last = matcher.end();
        }
        result.append(storageHtml, last, storageHtml.length());
        return result.toString();
    }

    private String displayName(String accountId) {
        if (nameCache.containsKey(accountId)) {
            return nameCache.get(accountId);
        }
        String name = null;
        try {
            String profile = confluence.profile(accountId);
            if (profile != null && !profile.isBlank()) {
                String value = new JSONObject(profile).optString("displayName", "").trim();
                name = value.isEmpty() ? null : value;
            }
        } catch (Exception e) {
            logger.debug("Could not resolve Confluence user {}: {}", accountId, e.getMessage());
        }
        nameCache.put(accountId, name);
        return name;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
