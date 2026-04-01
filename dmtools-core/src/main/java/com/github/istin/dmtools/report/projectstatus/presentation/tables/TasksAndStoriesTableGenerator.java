// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TasksAndStoriesTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(TasksAndStoriesTableGenerator.class);
    private final TableGenerator baseTableGenerator;

    public TasksAndStoriesTableGenerator(TableGenerator baseTableGenerator) {
        this.baseTableGenerator = baseTableGenerator;
    }

    @Override
    public String generateTable(TableData tableData) {
        return baseTableGenerator.generateTable(tableData);
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        return generateTasksAndStoriesTable(tickets);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Default implementation
        return generateTable(tickets);
    }

    public String generateTasksAndStoriesTable(List<ITicket> tasksAndStories) {
        // Create table headers
        List<String> headers = Arrays.asList(
                "Key", "Type", "Priority", "Story Points", "Closed Date", "Labels", "Summary", "Description"
        );
        TableData tableData = new TableData("Tasks And Stories Work Items", headers);

        for (ITicket ticket : tasksAndStories) {
            try {
                List<String> row = Arrays.asList(
                        ticket.getKey(),
                        ticket.getIssueType(),
                        ticket.getPriority(),
                        String.valueOf(ticket.getWeight()),
                        ticket.getFieldsAsJSON().getString("dateClosed"),
                        StringUtils.cleanTextForMarkdown(ticket.getTicketLabels().toString()),
                        ticket.getTicketTitle(),
                        StringUtils.removeUrls(StringUtils.cleanTextForMarkdown(ticket.getTicketDescription()))
                );
                tableData.addRow(row);
            } catch (IOException e) {
                logger.error("Error adding task/story to table: " + e.getMessage());
            }
        }

        return baseTableGenerator.generateTable(tableData);
    }
}