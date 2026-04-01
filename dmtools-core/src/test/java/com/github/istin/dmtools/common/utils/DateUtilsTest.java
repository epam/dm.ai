// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import junit.framework.TestCase;

import java.util.Calendar;
import java.util.Date;

public class DateUtilsTest extends TestCase {

    public void testParseRallyDate() {
        Calendar date = DateUtils.parseRallyCalendar("2023-01-03T23:00:00.000Z");
        System.out.println(DateUtils.formatToRallyDate(date));

    }

    public void testParseBitbucketCloudDate() {
        Date date = DateUtils.parseCloudBitbucketDate("2024-03-20T14:22:41.123832+00:00");
        System.out.println(date);

    }

    public void testParseJiraDateNegativeOffset() {
        Date date = DateUtils.parseJiraDate2("2025-04-05T03:04:28.313-0400");
        System.out.println(date);

    }

}