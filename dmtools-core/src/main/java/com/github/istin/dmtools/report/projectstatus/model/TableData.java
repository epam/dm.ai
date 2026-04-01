// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TableData {
    private final String title;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final String description;

    @Setter
    private String footnote;  // New field for footnote

    public TableData(String title, List<String> headers) {
        this(title, headers, null);
    }

    public TableData(String title, List<String> headers, String description) {
        this.title = title;
        this.headers = headers;
        this.description = description;
        this.rows = new ArrayList<>();
        this.footnote = null;  // Initialize footnote as null
    }

    public void addRow(List<String> row) {
        if (row.size() != headers.size()) {
            throw new IllegalArgumentException("Row size must match header size");
        }
        rows.add(row);
    }

    /**
     * Checks if the table has a footnote
     * @return true if a footnote is set, false otherwise
     */
    public boolean hasFootnote() {
        return footnote != null && !footnote.isEmpty();
    }
}