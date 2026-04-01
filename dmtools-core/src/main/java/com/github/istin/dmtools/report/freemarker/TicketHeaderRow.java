// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

public class TicketHeaderRow extends GenericRow {

    public TicketHeaderRow() {
        super(true);
        getCells().add(new GenericCell("Key"));
        getCells().add(new GenericCell("Priority"));
        getCells().add(new GenericCell("Type"));
        getCells().add(new GenericCell("Summary"));
        getCells().add(new GenericCell("SPs"));
        getCells().add(new GenericCell("Progress"));
        getCells().add(new GenericCell("Status"));
    }

}
