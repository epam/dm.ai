// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.atlassian.common.model.Assignee;

public class AssigneeLinkCell extends GenericCell {

    public AssigneeLinkCell(Assignee assignee) {
        super(assignee == null ? "&nbsp;" : "<a href=\"mailto:"+ assignee.getEmailAddress() +"\">" + assignee.getDisplayName() + "</a>");
    }
}
