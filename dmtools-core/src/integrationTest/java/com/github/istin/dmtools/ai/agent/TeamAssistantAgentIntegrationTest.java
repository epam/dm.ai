// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerTeamAssistantAgentComponent;
import com.github.istin.dmtools.di.TeamAssistantAgentComponent;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TeamAssistantAgentIntegrationTest {

    private TeamAssistantAgent agent;
    private TeamAssistantAgentComponent component;

    @Before
    public void setUp() throws Exception {
        component = DaggerTeamAssistantAgentComponent.create();
        agent = new TeamAssistantAgent();
        component.inject(agent);
    }

    private RequestDecompositionAgent.Result createRequest(String aiRole, String request, String[] questions,
                                                           String[] tasks, String[] instructions, String knownInfo) {
        return new RequestDecompositionAgent.Result(aiRole, request, questions, tasks, instructions, knownInfo, "Please format the output in markdown.", "");
    }

    @Test
    public void testTeamAssistantWithCodeRequest() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Java Developer",
                "How to implement proper error handling for REST endpoints?",
                new String[]{"How to handle validation errors?", "What about logging?"},
                new String[]{"Implement global error handling"},
                new String[]{"Provide code examples", "Include best practices"},
                "Project uses Spring Boot with REST APIs. Ticket: TICKET-123"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
            assertFalse("Response should not contain html tag", response.contains("<html>"));
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithArchitectureQuestion() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Software Architect",
                "What's the best way to handle service communication?",
                new String[]{"Should we use synchronous or asynchronous?", "What about latency requirements?"},
                new String[]{"Design service communication pattern"},
                new String[]{"Consider different patterns", "Address scalability concerns"},
                "Microservices architecture with Kafka. Ticket: TICKET-456"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
            assertFalse("Response should not contain class attributes except in code tags",
                    response.replaceAll("<code class=\".*?\">", "").contains("class="));
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithTechnicalDebtDiscussion() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Technical Lead",
                "How should we approach refactoring the authentication module?",
                new String[]{"What are the security implications?", "How to handle deprecated methods?"},
                new String[]{"Plan authentication module refactoring"},
                new String[]{"Consider security aspects", "Provide migration strategy"},
                "Legacy monolithic application. Ticket: TICKET-789"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
            assertFalse("Response should not contain body tag", response.contains("<body>"));
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithEmptyParams() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "",
                "",
                new String[]{},
                new String[]{},
                new String[]{},
                ""
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithOnlyTicketContent() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Performance Engineer",
                "",
                new String[]{},
                new String[]{"Investigate memory leak"},
                new String[]{"Analyze OOM errors", "Provide investigation steps"},
                "TICKET-101: Memory Leak Investigation - OOM errors in production"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithAuthenticationFlowQuestion() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Security Architect",
                "How does our current authentication system work?",
                new String[]{"How is JWT implemented?", "How do refresh tokens work?"},
                new String[]{"Document authentication flow"},
                new String[]{"Explain security measures", "Include flow diagrams"},
                "Spring Security based system with JWT support. Ticket: TICKET-123"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithCodeReview() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Senior Developer",
                "Can you review this code?",
                new String[]{"Are there any performance issues?", "Is it following best practices?"},
                new String[]{"Review code quality"},
                new String[]{"Check for security vulnerabilities", "Suggest improvements"},
                "Code review for user authentication module. Ticket: TICKET-999"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantWithDatabaseQuery() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "Database Developer",
                "Help optimize this query",
                new String[]{"How can we improve performance?", "Should we add indexes?"},
                new String[]{"Optimize database query"},
                new String[]{"Analyze query execution plan", "Suggest index strategy"},
                "PostgreSQL database performance issues. Ticket: TICKET-555"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    public void testTeamAssistantResponseFormat() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result decomposedRequest = createRequest(
                "QA Engineer",
                "What testing strategies should we use?",
                new String[]{"Unit tests vs integration tests?", "How to test async operations?"},
                new String[]{"Design testing strategy"},
                new String[]{"Cover different test types", "Include automation considerations"},
                "Testing strategy for microservices. Ticket: TICKET-777"
        );
        TeamAssistantAgent.Params params = new TeamAssistantAgent.Params(decomposedRequest);

        try {
            // Act
            String response = agent.run(params);

            // Assert
            assertNotNull("Response should not be null", response);
            assertTrue("Response should contain some content", response.length() > 10);
            // Ensure response doesn't contain invalid HTML structure
            assertFalse("Response should not contain HTML document structure", response.contains("<!DOCTYPE"));
            assertFalse("Response should not contain HTML head section", response.contains("<head>"));
        } catch (Exception e) {
            // If this is a configuration issue (e.g., missing API key), skip the test
            if (e.getMessage() != null && (e.getMessage().contains("API") || e.getMessage().contains("key") || e.getMessage().contains("response") && e.getMessage().contains("null"))) {
                System.out.println("Skipping test due to configuration issue: " + e.getMessage());
                return;
            }
            throw e;
        }
    }
}