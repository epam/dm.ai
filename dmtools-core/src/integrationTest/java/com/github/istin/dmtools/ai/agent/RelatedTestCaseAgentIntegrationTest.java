// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelatedTestCaseAgentIntegrationTest {

    private RelatedTestCaseAgent agent;

    @Before
    public void setUp() {
        agent = new RelatedTestCaseAgent();
    }

    @Test
    public void testRelatedTestCases() throws Exception {
        String newStory = "Implement a shopping cart functionality\n" +
                "Add items to cart, update quantities, and remove items";
        String existingTestCase = "Test case for checkout process\n" +
                "Verify that users can complete the purchase with items in their cart";

        RelatedTestCaseAgent.Params params = new RelatedTestCaseAgent.Params(newStory, existingTestCase, "");
        Boolean result = agent.run(params);

        assertTrue(result);
    }

    @Test
    public void testUnrelatedTestCases() throws Exception {
        String newStory = "Implement user registration\n" +
                "Allow new users to create an account with email and password";
        String existingTestCase = "Test case for product reviews\n" +
                "Verify that users can leave reviews and ratings for products";

        RelatedTestCaseAgent.Params params = new RelatedTestCaseAgent.Params(newStory, existingTestCase, "");
        Boolean result = agent.run(params);

        assertFalse(result);
    }

    @Test
    public void testPartiallyRelatedTestCases() throws Exception {
        String newStory = "Implement order tracking\n" +
                "Allow users to track their order status and shipping information";
        String existingTestCase = "Test case for email notifications\n" +
                "Verify that users receive email notifications for order updates";

        RelatedTestCaseAgent.Params params = new RelatedTestCaseAgent.Params(newStory, existingTestCase, "");
        Boolean result = agent.run(params);

        assertTrue(result);
    }
}
