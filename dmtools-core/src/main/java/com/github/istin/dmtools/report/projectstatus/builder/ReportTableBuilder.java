// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.builder;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;

import java.util.Comparator;
import java.util.List;

public class ReportTableBuilder {
    private final List<ITicket> tickets;
    private final TableGenerator generator;
    private boolean includeDescription = false;
    private boolean includeStoryPoints = true;
    private String[] columns;
    private Comparator<ITicket> sortOrder;
    private String title;

    public ReportTableBuilder(List<ITicket> tickets, TableGenerator generator) {
        this.tickets = tickets;
        this.generator = generator;
    }

    public ReportTableBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public ReportTableBuilder withColumns(String... columns) {
        this.columns = columns;
        return this;
    }

    public ReportTableBuilder includeDescription(boolean include) {
        this.includeDescription = include;
        return this;
    }

    public ReportTableBuilder includeStoryPoints(boolean include) {
        this.includeStoryPoints = include;
        return this;
    }

    public ReportTableBuilder sortBy(Comparator<ITicket> comparator) {
        this.sortOrder = comparator;
        return this;
    }

    public String build() {
        // Sort tickets if a sort order is specified
        if (sortOrder != null) {
            tickets.sort(sortOrder);
        }

        // Use default columns if none specified
        if (columns == null) {
            if (includeDescription) {
                columns = new String[]{"Key", "Type", "Priority", "Story Points", "Closed Date", "Summary", "Description"};
            } else {
                columns = new String[]{"Key", "Type", "Priority", "Story Points", "Closed Date", "Summary"};
            }
        }

        // Generate the table
        return generator.generateTable(tickets, columns, includeDescription, includeStoryPoints);
    }
}