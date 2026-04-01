// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class BusinessAreaAssessmentAgentIntegrationTest {

    @Test
    public void testUserAuthenticationStory() throws Exception {
        // Arrange
        BusinessAreaAssessmentAgent agent = new BusinessAreaAssessmentAgent();
        String storyDescription = "As a user, I want to be able to authenticate using my social media accounts so that I don't need to create a new account.";
        BusinessAreaAssessmentAgent.Params params = new BusinessAreaAssessmentAgent.Params(storyDescription);

        // Act
        String result = agent.run(params);

        // Assert
        assertNotNull("Result should not be null", result);
        assertTrue("Result should not be empty", !result.isEmpty());
        assertTrue("Result should not contain more than 3 words",
                result.split("\\s+").length <= 3);
        assertNoForbiddenWords(result);
        assertNoForbiddenCharacters(result);
    }

    @Test
    public void testDataExportStory() throws Exception {
        // Arrange
        BusinessAreaAssessmentAgent agent = new BusinessAreaAssessmentAgent();
        String storyDescription = "As a premium user, I need to export my data in various formats (CSV, PDF, Excel) so that I can use it in other applications.";
        BusinessAreaAssessmentAgent.Params params = new BusinessAreaAssessmentAgent.Params(storyDescription);

        // Act
        String result = agent.run(params);

        // Assert
        assertNotNull("Result should not be null", result);
        assertTrue("Result should not be empty", !result.isEmpty());
        assertTrue("Result should not contain more than 3 words",
                result.split("\\s+").length <= 3);
        assertNoForbiddenWords(result);
        assertNoForbiddenCharacters(result);
    }

    @Test
    public void testNotificationPreferencesStory() throws Exception {
        // Arrange
        BusinessAreaAssessmentAgent agent = new BusinessAreaAssessmentAgent();
        String storyDescription = "As a user, I want to customize my notification preferences so that I only receive alerts for events that are important to me.";
        BusinessAreaAssessmentAgent.Params params = new BusinessAreaAssessmentAgent.Params(storyDescription);

        // Act
        String result = agent.run(params);

        // Assert
        assertNotNull("Result should not be null", result);
        assertTrue("Result should not be empty", !result.isEmpty());
        assertTrue("Result should not contain more than 3 words",
                result.split("\\s+").length <= 3);
        assertNoForbiddenWords(result);
        assertNoForbiddenCharacters(result);
    }

    private void assertNoForbiddenWords(String result) {
        String[] forbiddenWords = {"Implementation", "Development", "Functionality", "Integration",
                "New Feature Area"};
        for (String word : forbiddenWords) {
            assertTrue("Result should not contain '" + word + "'",
                    !result.toLowerCase().contains(word.toLowerCase()));
        }
    }

    private void assertNoForbiddenCharacters(String result) {
        assertTrue("Result should not contain commas", !result.contains(","));
        assertTrue("Result should not contain periods", !result.contains("."));
        assertTrue("Result should not contain markdown code blocks", !result.contains("```"));
    }
}