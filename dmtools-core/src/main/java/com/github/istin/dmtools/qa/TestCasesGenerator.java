package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.*;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.ai.agent.TestCaseDeduplicationAgent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.di.DaggerTestCasesGeneratorComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Component;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    @Inject
    TestCaseDeduplicationAgent testCaseDeduplicationAgent;

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

        // Initialize accumulator for all generated test cases
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases = new ArrayList<>();

        // Calculate token limits (same pattern as findAndLinkSimilarTestCasesBySummary)
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int storyTokens = new Claude35TokenCounter().countTokens(ticketContext.toText());
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        int tokenLimit = (systemTokenLimits - storyTokens)/2;
        System.out.println("GENERATION TOKEN LIMIT: " + tokenLimit);

        // Chunk existing test cases for generation
        List<ChunkPreparation.Chunk> testCaseChunks = chunkPreparation.prepareChunks(finaResults, tokenLimit);
        System.out.println("TEST CASE CHUNKS FOR GENERATION: " + testCaseChunks.size());

        // Generate test cases per chunk
        for (ChunkPreparation.Chunk chunk : testCaseChunks) {
            List<TestCaseGeneratorAgent.TestCase> chunkTestCases = testCaseGeneratorAgent.run(
                new TestCaseGeneratorAgent.Params(
                    params.getTestCasesPriorities(),
                    chunk.getText(), // Chunked test cases instead of all finaResults
                    ticketContext.toText(),
                    extraRules
                )
            );
            allGeneratedTestCases.addAll(chunkTestCases);
            System.out.println("Generated " + chunkTestCases.size() + " test cases from chunk");
        }

        // Deduplicate results
        List<TestCaseGeneratorAgent.TestCase> newTestCases = deduplicateInChunks(
            allGeneratedTestCases,
            ToText.Utils.toText(finaResults)
        );
        System.out.println("Final deduplicated test cases: " + newTestCases.size());

        if (params.getOutputType().equals(TestCasesGeneratorParams.OutputType.comment)) {
            StringBuilder result = new StringBuilder();
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                result.append("Summary: ").append(testCase.getSummary()).append("<br>");
                result.append("Priority: ").append(testCase.getPriority()).append("<br>");
                result.append("Description: ").append(StringUtils.convertToMarkdown(testCase.getDescription())).append("<br>");
            }
            trackerClient.postComment(key, result.toString());
        } else {
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                String projectCode = key.split("-")[0];
                String description = testCase.getDescription();
                //if (trackerClient.getTextType() == TrackerClient.TextType.MARKDOWN) {
                    description = StringUtils.convertToMarkdown(description);
                //}
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

    private List<TestCaseGeneratorAgent.TestCase> deduplicateInChunks(
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases,
        String existingTestCases
    ) throws Exception {
        // Step 1: Deduplicate allGeneratedTestCases against itself to remove internal duplicates
        System.out.println("Step 1: Deduplicating generated test cases against themselves");
        List<TestCaseGeneratorAgent.TestCase> selfDeduplicated = deduplicateSelfInChunks(allGeneratedTestCases);
        System.out.println("After self-deduplication: " + selfDeduplicated.size() + " unique test cases");
        
        // Step 2: Deduplicate against existing test cases
        System.out.println("Step 2: Deduplicating against existing test cases");
        List<TestCaseGeneratorAgent.TestCase> finalDeduplicated = deduplicateAgainstExistingInChunks(selfDeduplicated, existingTestCases);
        System.out.println("After deduplication against existing: " + finalDeduplicated.size() + " test cases");
        
        return finalDeduplicated;
    }
    
    private List<TestCaseGeneratorAgent.TestCase> deduplicateSelfInChunks(
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases
    ) throws Exception {
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        int tokenLimit = systemTokenLimits / 2; // Use half the system limit for self-deduplication
        
        String allGeneratedText = ToText.Utils.toText(allGeneratedTestCases);
        int generatedTokens = new Claude35TokenCounter().countTokens(allGeneratedText);
        
        // Single deduplication call if small enough
        if (generatedTokens <= tokenLimit) {
            return testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    allGeneratedTestCases,
                    "", // No existing test cases for self-deduplication
                    "" // No previous deduplicated results
                )
            );
        }
        
        // Split into chunks and deduplicate iteratively against accumulated unique results
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(allGeneratedTestCases, tokenLimit);
        List<TestCaseGeneratorAgent.TestCase> uniqueResults = new ArrayList<>();
        
        for (ChunkPreparation.Chunk chunk : chunks) {
            List<TestCaseGeneratorAgent.TestCase> chunkTestCases = parseTestCasesFromChunk(chunk);
            
            // Deduplicate against accumulated unique results from previous chunks
            List<TestCaseGeneratorAgent.TestCase> deduplicatedChunk = testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    chunkTestCases,
                    "", // No existing test cases
                    ToText.Utils.toText(uniqueResults) // Previous unique results from earlier chunks
                )
            );
            
            uniqueResults.addAll(deduplicatedChunk);
        }
        
        return uniqueResults;
    }
    
    private List<TestCaseGeneratorAgent.TestCase> deduplicateAgainstExistingInChunks(
        List<TestCaseGeneratorAgent.TestCase> newTestCases,
        String existingTestCases
    ) throws Exception {
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int existingTestCasesTokens = new Claude35TokenCounter().countTokens(existingTestCases);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        int tokenLimit = (systemTokenLimits - existingTestCasesTokens) / 2;
        
        String newTestCasesText = ToText.Utils.toText(newTestCases);
        int newTestCasesTokens = new Claude35TokenCounter().countTokens(newTestCasesText);
        
        // Single deduplication call if small enough
        if (newTestCasesTokens <= tokenLimit) {
            return testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    newTestCases,
                    existingTestCases,
                    "" // No previous deduplicated results
                )
            );
        }
        
        // Split new test cases into chunks and deduplicate each against existing test cases
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(newTestCases, tokenLimit);
        List<TestCaseGeneratorAgent.TestCase> uniqueResults = new ArrayList<>();
        
        for (ChunkPreparation.Chunk chunk : chunks) {
            List<TestCaseGeneratorAgent.TestCase> chunkTestCases = parseTestCasesFromChunk(chunk);
            
            // Deduplicate against existing test cases only (not against accumulated results)
            // because all chunks need to be checked against the same existing test cases
            List<TestCaseGeneratorAgent.TestCase> deduplicatedChunk = testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    chunkTestCases,
                    existingTestCases,
                    "" // Don't use accumulated results here
                )
            );
            
            uniqueResults.addAll(deduplicatedChunk);
        }
        
        return uniqueResults;
    }

    private List<TestCaseGeneratorAgent.TestCase> parseTestCasesFromChunk(ChunkPreparation.Chunk chunk) throws Exception {
        JSONArray jsonArray = AIResponseParser.parseResponseAsJSONArray(chunk.getText());
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();
        
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            testCases.add(new TestCaseGeneratorAgent.TestCase(
                jsonObject.getString("priority"),
                jsonObject.getString("summary"),
                jsonObject.getString("description")
            ));
        }
        
        return testCases;
    }

    @NotNull
    public List<ITicket> findAndLinkSimilarTestCasesBySummary(TicketContext ticketContext, List<? extends ITicket> listOfAllTestCases, boolean isLink, String relatedTestCasesRulesLink, String relationship) throws Exception {
        List<ITicket> finaResults = new ArrayList<>();
        String extraRelatedTestCaseRulesFromConfluence = new ConfluencePagesContext(new String[]{relatedTestCasesRulesLink}, confluence, false).toText();
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int storyTokens = new Claude35TokenCounter().countTokens(ticketContext.toText());
        System.out.println("STORY TOKENS: " + storyTokens);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        System.out.println("SYSTEM TOKEN LIMITS: " + systemTokenLimits);
        int tokenLimit = (systemTokenLimits - storyTokens)/2;
        System.out.println("TESTCASES TOKEN LIMITS: " + tokenLimit);
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(listOfAllTestCases, tokenLimit);
        for (ChunkPreparation.Chunk chunk : chunks) {
            JSONArray testCaseKeys = relatedTestCasesAgent.run(new RelatedTestCasesAgent.Params(ticketContext.toText(), chunk.getText(), extraRelatedTestCaseRulesFromConfluence));
            //find relevant test case from batch
            for (int j = 0; j < testCaseKeys.length(); j++) {
                String testCaseKey = testCaseKeys.getString(j);
                ITicket testCase = listOfAllTestCases.stream().filter(t -> t.getKey().equals(testCaseKey)).findFirst().orElse(null);
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
