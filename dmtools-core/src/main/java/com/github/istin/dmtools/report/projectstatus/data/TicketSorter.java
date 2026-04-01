// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.data;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class TicketSorter {

    private final ReportConfiguration config;

    public TicketSorter(ReportConfiguration config) {
        this.config = config;
    }

    public void sortTickets(List<ITicket> tickets) {
        tickets.sort(byTypeAndPriorityAndTitle());
    }

    public Comparator<ITicket> byTypeAndPriorityAndTitle() {
        return Comparator.comparing((ITicket t) -> {
                    try {
                        return TicketStatisticsCalculator.nullToEmpty(t.getIssueType(), "Task");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenComparing((ITicket t) -> {
                    try {
                        return TicketStatisticsCalculator.nullToEmpty(t.getPriority(), "Trivial");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenComparing((ITicket t) -> {
                    try {
                        return TicketStatisticsCalculator.nullToEmpty(t.getTicketTitle(), "");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public static Comparator<ITicket> byPriorityThenType() {
        return Comparator.comparing((ITicket t) -> {
                    try {
                        return TicketStatisticsCalculator.nullToEmpty(t.getPriority(), "Trivial");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenComparing((ITicket t) -> {
                    try {
                        return TicketStatisticsCalculator.nullToEmpty(t.getIssueType(), "Task");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public List<String> sortIssueTypes(Set<String> types) {
        List<String> issueTypeOrder = config.getIssueTypeOrder();
        List<String> sortedTypes = new ArrayList<>(types);
        if (issueTypeOrder != null && !issueTypeOrder.isEmpty()) {
            sortedTypes.sort((a, b) -> {
                int aIndex = issueTypeOrder.indexOf(a);
                int bIndex = issueTypeOrder.indexOf(b);

                if (aIndex >= 0 && bIndex >= 0) {
                    return Integer.compare(aIndex, bIndex);
                } else if (aIndex >= 0) {
                    return -1;
                } else if (bIndex >= 0) {
                    return 1;
                } else {
                    return a.compareTo(b);
                }
            });
        } else {
            java.util.Collections.sort(sortedTypes);
        }
        return sortedTypes;
    }

    public List<String> sortPriorities(Set<String> priorities) {
        List<String> priorityOrder = config.getPriorityOrder();
        List<String> sortedPriorities = new ArrayList<>(priorities);
        if (priorityOrder != null && !priorityOrder.isEmpty()) {
            sortedPriorities.sort((a, b) -> {
                int aIndex = priorityOrder.indexOf(a);
                int bIndex = priorityOrder.indexOf(b);

                if (aIndex >= 0 && bIndex >= 0) {
                    return Integer.compare(aIndex, bIndex);
                } else if (aIndex >= 0) {
                    return -1;
                } else if (bIndex >= 0) {
                    return 1;
                } else {
                    return a.compareTo(b);
                }
            });
        } else {
            java.util.Collections.sort(sortedPriorities);
        }
        return sortedPriorities;
    }

    public List<String> sortRoles(Set<String> roles) {
        List<String> sortedRoles = new ArrayList<>(roles);
        sortedRoles.sort((a, b) -> {
            // Development should be last
            if (a.equals("Development") && !b.equals("Development")) {
                return 1;
            }
            if (!a.equals("Development") && b.equals("Development")) {
                return -1;
            }
            // Sort other roles alphabetically
            return a.compareTo(b);
        });

        return sortedRoles;
    }
}