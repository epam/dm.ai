// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.Config;
import com.thedeanda.lorem.LoremIpsum;

import java.util.HashMap;
import java.util.Map;

public class MockedNames {
    private static MockedNames mockedNames;

    private Map<String, String> mockedNamesMap = new HashMap<>();

    private MockedNames() {

    }

    public static MockedNames getInstance() {
        if (mockedNames == null) {
            mockedNames = new MockedNames();
        }
        return mockedNames;
    }

    public String mock(String name) {
        if (Config.DEMO_SITE) {
            String lowerCaseName = name.toLowerCase();
            if (lowerCaseName.contains("android") || lowerCaseName.contains("ios") || lowerCaseName.contains("web") || lowerCaseName.contains("qa")) {
                return name;
            }
            String mockedName = mockedNamesMap.get(name);
            if (mockedName == null) {
                mockedName = LoremIpsum.getInstance().getFirstName() + " " + LoremIpsum.getInstance().getLastName();
                mockedNamesMap.put(name, mockedName);
            }
            return mockedName;
        }
        return name;
    }
}
