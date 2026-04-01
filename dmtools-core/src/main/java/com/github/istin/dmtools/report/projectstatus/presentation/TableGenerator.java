// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.model.TableData;

import java.util.List;

public interface TableGenerator {
    String generateTable(TableData tableData);
    String generateTable(List<ITicket> tickets);
    String generateTable(List<ITicket> tickets, String[] columns, boolean includeDescription, boolean includeStoryPoints);
}