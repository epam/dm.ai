// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import junit.framework.TestCase;

public class DevProductivityReportTest extends TestCase {

    String regex = "Merge request by \\*([\\w\\s-]+)\\*";

    public void testFindNameInComment() {
        String commentText = "Merge request by *user-name*  \n" +
                "https://github.com/workspace/repo/pull/101";
        String nameInComment = DevProductivityReport.findNameInComment(regex, commentText);
        assertEquals("user-name", nameInComment);
    }

    public void testFindNameInComment2() {
        String commentText = "Merge request by *user-name*\n" +
                "    https://bitbucket.org/workspace/repo/pull-requests/794";
        String nameInComment = DevProductivityReport.findNameInComment(regex, commentText);
        assertEquals("user-name", nameInComment);
    }

}