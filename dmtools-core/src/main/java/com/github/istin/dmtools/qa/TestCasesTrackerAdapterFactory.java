// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.testrail.TestRailClient;
import com.github.istin.dmtools.testrail.TestRailTestCasesAdapter;

import java.io.IOException;

/**
 * Factory for creating {@link TestCasesTrackerAdapter} instances based on the configured tracker type.
 *
 * <p>To add a new custom tracker (e.g., Zephyr):</p>
 * <ol>
 *   <li>Create {@code ZephyrAdapterParams} accessor with Zephyr-specific key constants</li>
 *   <li>Implement {@code ZephyrTestCasesAdapter implements TestCasesTrackerAdapter}</li>
 *   <li>Add {@code case "zephyr"} in this factory</li>
 * </ol>
 * No changes needed to {@code TestCasesGenerator}, {@code TestCasesGeneratorParams}, or
 * {@code CustomTestCasesTrackerParams}.
 */
public class TestCasesTrackerAdapterFactory {

    private TestCasesTrackerAdapterFactory() {
    }

    /**
     * Creates a {@link TestCasesTrackerAdapter} for the given configuration.
     *
     * @param config the custom test cases tracker configuration
     * @return a ready-to-use adapter
     * @throws IOException              if the client cannot be initialised
     * @throws IllegalArgumentException if the tracker type is not recognised
     */
    public static TestCasesTrackerAdapter create(CustomTestCasesTrackerParams config) throws IOException {
        String type = config.getType();
        if ("testrail".equalsIgnoreCase(type)) {
            TestRailClient client = new TestRailClient();
            return new TestRailTestCasesAdapter(client, config);
        }
        throw new IllegalArgumentException("Unknown customTestCasesTracker type: " + type);
    }
}
