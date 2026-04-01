// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import java.util.ArrayList;
import java.util.List;

public class GenericRow {

    private boolean isHeader = false;

    public GenericRow() {
    }

    public GenericRow(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public GenericRow(boolean isHeader, String text, int span) {
        this.isHeader = isHeader;
        cells.add(new GenericCell(text).setDuration(span));
    }

    public List<GenericCell> getCells() {
        return cells;
    }

    public void setCells(List<GenericCell> cells) {
        this.cells = cells;
    }

    private List<GenericCell> cells = new ArrayList<>();

    public boolean getIsHeader() {
        return isHeader;
    }

    public void setHeader(boolean header) {
        isHeader = header;
    }
}
