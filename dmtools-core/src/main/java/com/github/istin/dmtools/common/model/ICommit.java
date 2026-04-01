// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import java.util.Calendar;

public interface ICommit {

    String getId();

    String getHash();

    String getMessage();

    IUser getAuthor();

    Long getCommiterTimestamp();

    Calendar getCommitterDate();

    String getUrl();

    class Utils {

        public static Calendar getComitterDate(ICommit commit) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(commit.getCommiterTimestamp());
            return calendar;
        }

    }
}