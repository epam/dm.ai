package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConfluencePagesContext;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.di.DaggerTestCasesGeneratorComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ResultItem;
import com.github.istin.dmtools.job.ExecutionMode;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.Component;
import java.util.ArrayList;
import java.util.List;

public class TestCasesGenerator extends AbstractJob<TestCasesGeneratorParams, List<TestCasesGenerator.TestCasesResult>> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public class TestCasesResult {
        private String key;
        private List<ITicket> similarTestCases;
        private List<TestCaseGeneratorAgent.TestCase> newTestCases;
    }

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Inject
    Confluence confluence;

    @Inject
    @Getter
    AI ai;

    @Inject
    IPromptTemplateReader promptTemplateReader;

    @Inject
    TestCaseGeneratorAgent testCaseGeneratorAgent;

    @Inject
    RelatedTestCasesAgent relatedTestCasesAgent;

    @Inject
    RelatedTestCaseAgent relatedTestCaseAgent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     * Only includes ServerManagedIntegrationsModule to avoid duplicate bindings
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class, com.github.istin.dmtools.di.AIAgentsModule.class})
    public interface ServerManagedTestCasesGeneratorComponent {
        void inject(TestCasesGenerator testCasesGenerator);
    }

    public TestCasesGenerator() {
        // Don't initialize here - will be done in initializeForMode based on execution mode
    }

    @Override
    protected void initializeStandalone() {
        // Use existing Dagger component for standalone mode
        DaggerTestCasesGeneratorComponent.create().inject(this);
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        // Create dynamic component with pre-resolved integrations
        try {
            ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
            ServerManagedTestCasesGeneratorComponent component = com.github.istin.dmtools.qa.DaggerTestCasesGenerator_ServerManagedTestCasesGeneratorComponent.builder()
                .serverManagedIntegrationsModule(module)
                .build();
            component.inject(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TestCasesGenerator in server-managed mode", e);
        }
    }

    @Override
    public List<TestCasesResult> runJob(TestCasesGeneratorParams params) throws Exception {
        final List<TestCasesResult> result = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            List<? extends ITicket> listOfAllTestCases = trackerClient.searchAndPerform(params.getExistingTestCasesJql(), new String[]{Fields.SUMMARY, Fields.DESCRIPTION});
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext();
            String additionalRules = new ConfluencePagesContext(params.getConfluencePages(), confluence, false).toText();
            result.add(generateTestCases(ticketContext, additionalRules, listOfAllTestCases, params));
            trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(params.getInitiator()) + ", similar test cases are linked and new test cases are generated.");
            return false;
        }, params.getInputJql(), trackerClient.getExtendedQueryFields());
        return result;
    }

    public TestCasesResult generateTestCases(TicketContext ticketContext, String extraRules, List<? extends ITicket> listOfAllTestCases, TestCasesGeneratorParams params) throws Exception {
        ITicket mainTicket = ticketContext.getTicket();
        String key = mainTicket.getTicketKey();

        List<ITicket> finaResults = findAndLinkSimilarTestCasesBySummary(ticketContext, listOfAllTestCases, true, params.getRelatedTestCasesRules(), params.getTestCaseLinkRelationship());

        List<TestCaseGeneratorAgent.TestCase> newTestCases = testCaseGeneratorAgent.run(new TestCaseGeneratorAgent.Params(params.getTestCasesPriorities(), finaResults.toString(), ticketContext.toText(), extraRules));

        if (params.getOutputType().equals(TestCasesGeneratorParams.OutputType.comment)) {
            StringBuilder result = new StringBuilder();
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                result.append("Summary: ").append(testCase.getSummary()).append("<br>");
                result.append("Priority: ").append(testCase.getPriority()).append("<br>");
                result.append("Description: ").append(testCase.getDescription()).append("<br>");
            }
            trackerClient.postComment(key, result.toString());
        } else {
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                String projectCode = key.split("-")[0];
                String description = testCase.getDescription();
                if (trackerClient.getTextType() == TrackerClient.TextType.MARKDOWN) {
                    description = StringUtils.convertToMarkdown(description);
                }
                Ticket createdTestCase = new Ticket(trackerClient.createTicketInProject(projectCode, params.getTestCaseIssueType(), testCase.getSummary(), description, new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        fields.set("priority",
                                new JSONObject().put("name", testCase.getPriority())
                        );
                        fields.set("labels", new JSONArray().put("ai_generated"));
                    }
                }));

                trackerClient.linkIssueWithRelationship(mainTicket.getTicketKey(), createdTestCase.getKey(), params.getTestCaseLinkRelationship());
            }
        }
        return new TestCasesResult(ticketContext.getTicket().getKey(), finaResults, newTestCases);
    }

    @NotNull
    public List<ITicket> findAndLinkSimilarTestCasesBySummary(TicketContext ticketContext, List<? extends ITicket> listOfAllTestCases, boolean isLink, String relatedTestCasesRulesLink, String relationship) throws Exception {
        List<ITicket> finaResults = new ArrayList<>();
        String extraRelatedTestCaseRulesFromConfluence = new ConfluencePagesContext(new String[]{relatedTestCasesRulesLink}, confluence, false).toText();
        int batchSize = 50;
        for (int i = 0; i < listOfAllTestCases.size(); i += batchSize) {
            List<? extends ITicket> batch = listOfAllTestCases.subList(i, Math.min(i + batchSize, listOfAllTestCases.size()));

            JSONArray testCaseKeys = relatedTestCasesAgent.run(new RelatedTestCasesAgent.Params(ticketContext.toText(), batch.toString(), extraRelatedTestCaseRulesFromConfluence));
            //find relevant test case from batch
            for (int j = 0; j < testCaseKeys.length(); j++) {
                String testCaseKey = testCaseKeys.getString(j);
                ITicket testCase = batch.stream().filter(t -> t.getKey().equals(testCaseKey)).findFirst().orElse(null);
                if (testCase != null) {
                    boolean isConfirmed = relatedTestCaseAgent.run(new RelatedTestCaseAgent.Params(ticketContext.toText(), testCase.toText(), extraRelatedTestCaseRulesFromConfluence));
                    if (isConfirmed) {
                        finaResults.add(testCase);
                        if (isLink) {
                            trackerClient.linkIssueWithRelationship(ticketContext.getTicket().getTicketKey(), testCase.getKey(), relationship);
                        }
                    }
                }
            }
        }
        return finaResults;
    }
}
