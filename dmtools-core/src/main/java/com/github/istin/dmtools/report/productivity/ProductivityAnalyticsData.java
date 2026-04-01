// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import lombok.Data;

import java.util.*;

@Data
public class ProductivityAnalyticsData {
    private Map<String, Set<String>> usersPerPattern = new HashMap<>();
    private Map<String, Map<String, Integer>> interactionsPerPatternPerUser = new HashMap<>();
    private Map<String, Map<String, List<InteractionInfo>>> userInteractions = new HashMap<>();
    private Set<String> allUsers = new HashSet<>();
    private Map<String, String> patternNames = new LinkedHashMap<>();
    private List<String> requests = new ArrayList<>();

    public ProductivityAnalyticsData() {
    }
    
    /**
     * @deprecated Use {@link #getUserInteractions()} instead.
     * This method is kept for backward compatibility but returns only dates.
     */
    @Deprecated
    public Map<String, Map<String, List<Date>>> getUserInteractionDates() {
        Map<String, Map<String, List<Date>>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, List<InteractionInfo>>> patternEntry : userInteractions.entrySet()) {
            Map<String, List<Date>> userDates = new HashMap<>();
            for (Map.Entry<String, List<InteractionInfo>> userEntry : patternEntry.getValue().entrySet()) {
                List<Date> dates = new ArrayList<>();
                for (InteractionInfo info : userEntry.getValue()) {
                    dates.add(info.getDate());
                }
                userDates.put(userEntry.getKey(), dates);
            }
            result.put(patternEntry.getKey(), userDates);
        }
        return result;
    }
}

