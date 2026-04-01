// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LinkCellTest {

    @Test
    public void testLinkCellConstructor() {
        String text = "Click here";
        String link = "http://example.com";
        LinkCell linkCell = new LinkCell(text, link);

        String expectedHtml = "<a href=\"" + link + "\">" + text + "</a>";
        assertEquals(expectedHtml, linkCell.getText());
    }
}