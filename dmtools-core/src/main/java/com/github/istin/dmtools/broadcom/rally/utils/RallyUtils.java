// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.utils;

import com.github.istin.dmtools.common.model.IHistoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RallyUtils {
    public static List<IHistoryItem> convertRevisionDescriptionToHistoryItems(String inputString) {
//        Pattern pattern = Pattern.compile("(\\w+(\\s\\w+)*)\\schanged\\sfrom\\s\\[(.*?)\\]\\sto\\s\\[(.*?)\\]|(\\w+(\\s\\w+)*)\\sadded\\s\\[(.*?)\\]");
//        Pattern pattern = Pattern.compile("([^\\[]+)\\schanged\\sfrom\\s\\[(.*?)\\]\\sto\\s\\[(.*?)\\]");

        Pattern pattern = Pattern.compile("([A-Z\\s]+)\\schanged\\sfrom\\s\\[(.*?)\\]\\sto\\s\\[(.*?)\\]");

        Matcher matcher = pattern.matcher(inputString);

        List<IHistoryItem> result = new ArrayList<>();

        while (matcher.find()) {
            result.add(new IHistoryItem.Impl(matcher.group(1).trim(), matcher.group(2), matcher.group(3)));
        }
        return result;
    }
}
