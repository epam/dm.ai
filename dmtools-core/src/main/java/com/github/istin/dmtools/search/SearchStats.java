// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.search;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class SearchStats {
    final List<IterationStats> iterations = new ArrayList<>();
    final Set<String> processedKeys = new HashSet<>();
    int totalItemsProcessed = 0;

    public static class IterationStats {
        final int iterationNumber;
        final List<String> keywords;
        final Map<String, IterationStats.KeywordStats> keywordStats = new HashMap<>();
        private Set<String> relevantKeys;

        public IterationStats(int iterationNumber, List<String> keywords) {
            this.iterationNumber = iterationNumber;
            this.keywords = keywords;
        }

        public void addRelevantKeys(Set<String> relevantKeys) {
            this.relevantKeys = relevantKeys;
        }

        public static class KeywordStats {
            private final int itemsFound;

            public KeywordStats(int itemsFound) {
                this.itemsFound = itemsFound;
            }

            public JSONObject toJson() {
                JSONObject json = new JSONObject();
                json.put("itemsFound", itemsFound);
                return json;
            }
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("iterationNumber", iterationNumber);
            json.put("keywords", new JSONArray(keywords));
            if (relevantKeys != null) {
                json.put("relevantKeys", new JSONArray(relevantKeys));
            }
            JSONObject keywordStatsJson = new JSONObject();
            keywordStats.forEach((keyword, stats) -> keywordStatsJson.put(keyword, stats.toJson()));
            json.put("keywordStats", keywordStatsJson);
            return json;
        }
    }

    public void addIterationStats(IterationStats stats) {
        iterations.add(stats);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("totalItemsProcessed", totalItemsProcessed);
        json.put("totalProcessedKeys", new JSONArray(processedKeys));
        JSONArray iterationsJson = new JSONArray();
        iterations.forEach(iteration -> iterationsJson.put(iteration.toJson()));
        json.put("iterations", iterationsJson);
        return json;
    }
}
