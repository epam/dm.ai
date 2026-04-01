// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation.dto;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for ReportConfiguration to be easily (de)serialized from/to JSON from JavaScript.
 */
public class ReportConfigurationDto {
    public List<String> completedStatuses;
    public List<String> rolePrefixes;
    public List<String> priorityOrder;
    public List<String> issueTypeOrder;
    public Map<String, String> roleDescriptions;
    public String customProjectStatusField;
    public String storyPointsField;
    public String defaultTicketType;
    public Boolean ignoreTicketsWithoutAssignee;
    public Boolean countSubTasks;
    public String parentTicketField;
    public String epicLinkField;
    public List<String> subTaskTypes;
    public List<String> epicTypes;
    public String ticketLinkField;

    // Add getters and setters if needed, or keep as public fields for simplicity with Gson.
} 