// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.report.projectstatus.model.TableData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MarkdownTableGenerator implements TableGenerator {

    private static final Logger logger = LogManager.getLogger(MarkdownTableGenerator.class);

    @Override
    public String generateTable(TableData tableData) {
        StringBuilder sb = new StringBuilder();

        // Add title if present
        if (tableData.getTitle() != null && !tableData.getTitle().isEmpty()) {
            sb.append("### ").append(tableData.getTitle()).append("\n\n");
        }

        // Add description if present
        if (tableData.getDescription() != null && !tableData.getDescription().isEmpty()) {
            sb.append(tableData.getDescription()).append("\n\n");
        }

        // Generate header row
        sb.append("| ");
        for (String header : tableData.getHeaders()) {
            sb.append(header).append(" | ");
        }
        sb.append("\n");

        // Generate separator row
        sb.append("| ");
        for (int i = 0; i < tableData.getHeaders().size(); i++) {
            sb.append("--- | ");
        }
        sb.append("\n");

        // Generate data rows
        for (List<String> row : tableData.getRows()) {
            sb.append("| ");
            for (String cell : row) {
                sb.append(cell).append(" | ");
            }
            sb.append("\n");
        }

        // Add footnote if present
        if (tableData.hasFootnote()) {
            sb.append("\n*").append(tableData.getFootnote()).append("*\n");
        }

        return sb.toString();
    }

    @Override
    public String generateTable(List<ITicket> tickets) {
        // Default implementation - create a simple table with key, type, and summary
        String[] defaultColumns = {"Key", "Type", "Priority", "Summary"};
        return generateTable(tickets, defaultColumns, false, false);
    }

    @Override
    public String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints) {
        // Create TableData from tickets and columns
        List<String> headers = new ArrayList<>(Arrays.asList(columns));

        TableData tableData = new TableData("Tickets", headers);

        for (ITicket ticket : tickets) {
            List<String> row = new ArrayList<>();
            try {
                for (String column : columns) {
                    switch (column.toLowerCase()) {
                        case "key":
                            row.add(ticket.getKey());
                            break;
                        case "type":
                            row.add(ticket.getIssueType());
                            break;
                        case "priority":
                            row.add(ticket.getPriority());
                            break;
                        case "story points":
                            row.add(String.valueOf(ticket.getWeight()));
                            break;
                        case "summary":
                            row.add(ticket.getTicketTitle());
                            break;
                        case "description":
                            if (includeDescription) {
                                row.add(StringUtils.removeUrls(StringUtils.cleanTextForMarkdown(ticket.getTicketDescription())));
                            } else {
                                row.add("");
                            }
                            break;
                        case "closed date":
                            row.add(ticket.getFieldsAsJSON().getString("dateClosed"));
                            break;
                        default:
                            row.add("");
                    }
                }
                tableData.addRow(row);
            } catch (IOException e) {
                logger.error("Error generating table row: " + e.getMessage());
            }
        }

        return generateTable(tableData);
    }
}