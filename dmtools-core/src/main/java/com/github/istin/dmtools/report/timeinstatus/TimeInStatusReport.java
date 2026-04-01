// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.timeinstatus;

import com.github.istin.dmtools.report.freemarker.GenericRow;
import com.github.istin.dmtools.report.freemarker.SimpleReport;

import java.util.List;

public class TimeInStatusReport extends SimpleReport {

    public List<GenericRow> getRows() {
        return rows;
    }

    public void setRows(List<GenericRow> rows) {
        this.rows = rows;
    }

    private List<GenericRow> rows;

    private boolean isData = true;

    public String getTableHeight() {
        return tableHeight;
    }

    public void setTableHeight(String tableHeight) {
        this.tableHeight = tableHeight;
    }

    private String tableHeight;

    public List<TimeInStatus.Item> getItems() {
        return items;
    }

    public String getItemsAsString() {
        return items.toString();
    }

    public void setItems(List<TimeInStatus.Item> items) {
        this.items = items;
    }

    private List<TimeInStatus.Item> items;

    public boolean getIsData() {
        return isData;
    }

    public void setIsData(boolean data) {
        isData = data;
    }
}
