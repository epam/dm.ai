// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

public class RelatedTestCasesTest {

    RelatedTestCasesAgent agent;

    @Before
    public void setUp() throws Exception {
        agent = new RelatedTestCasesAgent();
    }

    @Test
    public void runRelatedTestCasesAgent() throws Exception {
        RelatedTestCasesAgent agent = new RelatedTestCasesAgent();

        // Prepare the input data
        String newStory = "PROJ-123\n" +
                "Title: Implement user login functionality\n" +
                "Description: As a registered user, I want to be able to log in to the application using my username and password.";

        String existingTestCases = "TEST-001 User Registration Test\n" +
                "TEST-002 Password Reset Test\n" +
                "TEST-003 User Profile Update Test\n" +
                "TEST-004 Product Search Test";

        // Create the input parameters
        RelatedTestCasesAgent.Params params = new RelatedTestCasesAgent.Params(newStory, existingTestCases, "");

        // Run the agent
        JSONArray result = agent.run(params);

        // Process the results
        System.out.println("Related Test Cases:");
        if (result.isEmpty()) {
            System.out.println("No related test cases found.");
        } else {
            for (int i = 0; i < result.length(); i++) {
                System.out.println("- " + result.getString(i));
            }
        }
    }
}
