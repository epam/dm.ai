package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.RelatedTestCaseAgent;
import com.github.istin.dmtools.ai.agent.RelatedTestCasesAgent;
import com.github.istin.dmtools.ai.agent.TestCaseDeduplicationAgent;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.microsoft.ado.model.WorkItem;
import com.github.istin.dmtools.microsoft.ado.AzureDevOpsClient;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.di.DaggerTestCasesGeneratorComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.TrackerParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Component;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestCasesGenerator extends AbstractJob<TestCasesGeneratorParams, List<TestCasesGenerator.TestCasesResult>> {

    private static final String DEFAULT_EXISTING_RELATIONSHIP = Relationship.RELATES_TO;

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
            try {
                List<? extends ITicket> listOfAllTestCases = trackerClient.searchAndPerform(params.getExistingTestCasesJql(), params.getTestCasesRelatedFields());
                TicketContext ticketContext = new TicketContext(trackerClient, ticket);
                ticketContext.prepareContext(false, params.isIncludeOtherTicketReferences());
                String additionalRules = extractFromConfluence(params.getConfluencePages());
                result.add(generateTestCases(ticketContext, additionalRules, listOfAllTestCases, params));
                trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(params.getInitiator()) + ", similar test cases are linked and new test cases are generated.");
            } catch (Exception e) {
                String errorMessage = String.format("%s, test case generation failed with error: %s\n\nStack trace:\n%s", 
                    trackerClient.tag(params.getInitiator()),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    getStackTraceAsString(e));
                try {
                    trackerClient.postComment(ticket.getTicketKey(), errorMessage);
                } catch (Exception commentException) {
                    System.err.println("Failed to post error comment to ticket " + ticket.getTicketKey() + ": " + commentException.getMessage());
                }
                throw new RuntimeException("Test case generation failed for ticket " + ticket.getTicketKey(), e);
            }
            return false;
        }, params.getInputJql(), trackerClient.getExtendedQueryFields());
        return result;
    }

    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTrace = e.getStackTrace();
        int maxLines = 10;
        int linesToShow = Math.min(maxLines, stackTrace.length);
        for (int i = 0; i < linesToShow; i++) {
            sb.append(stackTrace[i].toString()).append("\n");
        }
        if (stackTrace.length > maxLines) {
            sb.append("... (").append(stackTrace.length - maxLines).append(" more lines)");
        }
        return sb.toString();
    }

    public TestCasesResult generateTestCases(TicketContext ticketContext, String extraRules, List<? extends ITicket> listOfAllTestCases, TestCasesGeneratorParams params) throws Exception {
        ITicket mainTicket = ticketContext.getTicket();
        String key = mainTicket.getTicketKey();
        String ticketText = ticketContext.toText();
        String existingRelationship = resolveRelationshipForExisting(params);
        List<ITicket> finaResults = params.isFindRelated()
                ? findAndLinkSimilarTestCasesBySummary(ticketContext.getTicket().getTicketKey(), ticketText, listOfAllTestCases, params.isLinkRelated(), params.getRelatedTestCasesRules(), existingRelationship)
                : Collections.emptyList();

        // Initialize accumulator for all generated test cases
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases = new ArrayList<>();

        // Calculate token limits (same pattern as findAndLinkSimilarTestCasesBySummary)
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int storyTokens = new Claude35TokenCounter().countTokens(ticketContext.toText());
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        int tokenLimit = (systemTokenLimits - storyTokens)/2;
        System.out.println("GENERATION TOKEN LIMIT: " + tokenLimit);
        List<TestCaseGeneratorAgent.TestCase> newTestCases;
        String examples = unpackExamples(params.getExamples(), params.getTestCasesExampleFields());

        if (!finaResults.isEmpty()) {
            // Chunk existing test cases for generation
            List<ChunkPreparation.Chunk> testCaseChunks = chunkPreparation.prepareChunks(finaResults, tokenLimit);
            System.out.println("TEST CASE CHUNKS FOR GENERATION: " + testCaseChunks.size());

            // Generate test cases per chunk
            for (ChunkPreparation.Chunk chunk : testCaseChunks) {
                List<TestCaseGeneratorAgent.TestCase> chunkTestCases = testCaseGeneratorAgent.run(
                        new TestCaseGeneratorAgent.Params(
                                params.getTestCasesPriorities(),
                                chunk.getText(), // Chunked test cases instead of all finaResults
                                ticketText,
                                extraRules,
                                params.isOverridePromptExamples(),
                                examples
                        )
                );
                allGeneratedTestCases.addAll(chunkTestCases);
                System.out.println("Generated " + chunkTestCases.size() + " test cases from chunk");
            }

            if (testCaseChunks.size() > 1) {
                // Deduplicate results - reuse testCaseChunks instead of converting to text again
                newTestCases = deduplicateInChunks(
                        allGeneratedTestCases,
                        testCaseChunks
                );
            } else {
                newTestCases = allGeneratedTestCases;
            }
            System.out.println("Final deduplicated test cases: " + newTestCases.size());
        } else {
            newTestCases = testCaseGeneratorAgent.run(
                    new TestCaseGeneratorAgent.Params(
                            params.getTestCasesPriorities(),
                            "", // Chunked test cases instead of all finaResults
                            ticketContext.toText(),
                            extraRules,
                            params.isOverridePromptExamples(),
                            examples
                    )
            );
        }
        TestCasesResult testCasesResult = new TestCasesResult(ticketContext.getTicket().getKey(), finaResults, newTestCases);

        if (params.getOutputType().equals(TrackerParams.OutputType.comment)) {
            StringBuilder result = new StringBuilder();
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                result.append("Summary: ").append(testCase.getSummary()).append("<br>");
                result.append("Priority: ").append(testCase.getPriority()).append("<br>");
                result.append("Description: ").append(StringUtils.convertToMarkdown(testCase.getDescription())).append("<br>");
            }
            trackerClient.postComment(key, result.toString());
        } else {
            String newTestCaseRelationship = resolveRelationshipForNew(params);
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                // Get project code: for ADO use getProject(), for Jira use key.split("-")[0]
                String projectCode;
                if (mainTicket instanceof WorkItem) {
                    projectCode = ((WorkItem) mainTicket).getProject();
                    if (projectCode == null || projectCode.isEmpty()) {
                        throw new IOException("Unable to determine project from ADO work item " + key);
                    }
                } else {
                    // Jira format: PROJ-123 -> PROJ
                    projectCode = key.split("-")[0];
                }
                String description = testCase.getDescription();
                if (params.isConvertToJiraMarkdown()) {
                    description = StringUtils.convertToMarkdown(description);
                }
                
                // Create FieldsInitializer with tracker-specific field formats
                final boolean isAdo = trackerClient instanceof AzureDevOpsClient;
                ITicket createdTestCase = trackerClient.createTicket(trackerClient.createTicketInProject(projectCode, params.getTestCaseIssueType(), testCase.getSummary(), description, new TrackerClient.FieldsInitializer() {
                    @Override
                    public void init(TrackerClient.TrackerTicketFields fields) {
                        if (isAdo) {
                            // ADO: priority is numeric (1, 2, 3, 4), tags are semicolon-separated string
                            try {
                                int priority = Integer.parseInt(testCase.getPriority());
                                fields.set("Microsoft.VSTS.Common.Priority", priority);
                            } catch (NumberFormatException e) {
                                // If priority is not a number, default to 2 (Medium)
                                fields.set("Microsoft.VSTS.Common.Priority", 2);
                            }
                            fields.set("System.Tags", "ai_generated");
                        } else {
                            // Jira: priority is object with "name", labels are array
                            fields.set("priority",
                                    new JSONObject().put("name", testCase.getPriority())
                            );
                            fields.set("labels", new JSONArray().put("ai_generated"));
                        }
                    }
                }));

                trackerClient.linkIssueWithRelationship(mainTicket.getTicketKey(), createdTestCase.getKey(), newTestCaseRelationship);
            }
        }
        js(params.getPostJSAction())
                .mcp(trackerClient, ai, confluence, null)
                .withJobContext(params, ticketContext.getTicket(), testCasesResult)
                .with(TrackerParams.INITIATOR, params.getInitiator())
                .execute();

        return testCasesResult;
    }

    public String unpackExamples(String examples, String[] testCasesExamplesFields) throws Exception {
        if (examples == null) {
            return "";
        }
        String unpackedExamples;
        if (examples.startsWith("https://")) {
            unpackedExamples = extractFromConfluence(examples);
        } else if (examples.startsWith("ql(")) {
            String ql = examples.substring(3, examples.length() - 1);
            List<? extends ITicket> tickets = trackerClient.searchAndPerform(ql, testCasesExamplesFields);

            JSONArray examplesArray = new JSONArray();
            for (ITicket ticket : tickets) {
                examplesArray.put(TestCaseGeneratorAgent.createTestCase(ticket.getPriority(), ticket.getTicketTitle(), ticket.getTicketDescription()));
            }
            unpackedExamples = examplesArray.toString();
        } else {
            unpackedExamples = examples;
        }
        return unpackedExamples;
    }

    private String extractFromConfluence(String... urls) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String url : urls) {
            String value = confluence.contentByUrl(url).getStorage().getValue();
            if (StringUtils.isConfluenceYamlFormat(value)) {
                value = StringUtils.extractYamlContentFromConfluence(value);
            }
            if (!content.isEmpty()) {
                content.append("\n");
            }
            content.append(value);
        }
        return content.toString();
    }

    private List<TestCaseGeneratorAgent.TestCase> deduplicateInChunks(
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases,
        List<ChunkPreparation.Chunk> existingTestCaseChunks
    ) throws Exception {
        // Step 1: Deduplicate allGeneratedTestCases against itself to remove internal duplicates
        System.out.println("Step 1: Deduplicating generated test cases against themselves");
        List<TestCaseGeneratorAgent.TestCase> selfDeduplicated = deduplicateSelfInChunks(allGeneratedTestCases);
        System.out.println("After self-deduplication: " + selfDeduplicated.size() + " unique test cases");
        
        // Step 2: Deduplicate against existing test cases - use already prepared chunks
        System.out.println("Step 2: Deduplicating against existing test cases");
        List<TestCaseGeneratorAgent.TestCase> finalDeduplicated = deduplicateAgainstExistingInChunks(selfDeduplicated, existingTestCaseChunks);
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
                    allGeneratedText,
                    "", // No existing test cases for self-deduplication
                    "" // No previous deduplicated results
                )
            );
        }
        
        // Split into chunks and deduplicate iteratively against accumulated unique results
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(allGeneratedTestCases, tokenLimit);
        List<TestCaseGeneratorAgent.TestCase> uniqueResults = new ArrayList<>();
        
        for (ChunkPreparation.Chunk chunk : chunks) {
            // Deduplicate against accumulated unique results from previous chunks
            List<TestCaseGeneratorAgent.TestCase> deduplicatedChunk = testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    chunk.getText(), // Pass string directly
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
        List<ChunkPreparation.Chunk> existingTestCaseChunks
    ) throws Exception {
        // Deduplicate new test cases against each chunk of existing test cases
        for (ChunkPreparation.Chunk existingChunk : existingTestCaseChunks) {
            String newTestCasesText = ToText.Utils.toText(newTestCases);
            String existingTestCasesText = existingChunk.getText();
            
            List<TestCaseGeneratorAgent.TestCase> deduplicatedAgainstChunk = testCaseDeduplicationAgent.run(
                new TestCaseDeduplicationAgent.Params(
                    newTestCasesText, // Pass string directly
                    existingTestCasesText,
                    "" // No previous deduplicated results
                )
            );
            
            // Update newTestCases to only include the ones that are not duplicates
            // This way, the next chunk will check a smaller list
            newTestCases = deduplicatedAgainstChunk;
            System.out.println("After checking against existing chunk: " + newTestCases.size() + " test cases remain");
        }
        
        return newTestCases;
    }

    private String resolveRelationshipForNew(TestCasesGeneratorParams params) {
        return resolveRelationship(params.getTestCaseLinkRelationshipForNew(), params.getTestCaseLinkRelationship(), Relationship.IS_TESTED_BY);
    }

    private String resolveRelationshipForExisting(TestCasesGeneratorParams params) {
        return resolveRelationship(params.getTestCaseLinkRelationshipForExisting(), params.getTestCaseLinkRelationship(), DEFAULT_EXISTING_RELATIONSHIP);
    }

    private String resolveRelationship(String primary, String secondary, String defaultValue) {
        if (isNotBlank(primary)) {
            return primary.trim();
        }
        if (isNotBlank(secondary)) {
            return secondary.trim();
        }
        return defaultValue;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @NotNull
    public List<ITicket> findAndLinkSimilarTestCasesBySummary(String ticketKey, String ticketText, List<? extends ITicket> listOfAllTestCases, boolean isLink, String relatedTestCasesRulesLink, String relationship) throws Exception {
        List<ITicket> finaResults = new ArrayList<>();
        String value = confluence.contentByUrl(relatedTestCasesRulesLink).getStorage().getValue();
        String extraRelatedTestCaseRulesFromConfluence;
        if (StringUtils.isConfluenceYamlFormat(value)) {
            extraRelatedTestCaseRulesFromConfluence  = StringUtils.extractYamlContentFromConfluence(value);
        } else {
            extraRelatedTestCaseRulesFromConfluence = value;
        }
        ChunkPreparation chunkPreparation = new ChunkPreparation();

        int storyTokens = new Claude35TokenCounter().countTokens(ticketText);
        System.out.println("STORY TOKENS: " + storyTokens);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        System.out.println("SYSTEM TOKEN LIMITS: " + systemTokenLimits);
        int tokenLimit = (systemTokenLimits - storyTokens)/2;
        System.out.println("TESTCASES TOKEN LIMITS: " + tokenLimit);
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(listOfAllTestCases, tokenLimit);
        for (ChunkPreparation.Chunk chunk : chunks) {
            JSONArray testCaseKeys = relatedTestCasesAgent.run(new RelatedTestCasesAgent.Params(ticketText, chunk.getText(), extraRelatedTestCaseRulesFromConfluence));
            //find relevant test case from batch
            for (int j = 0; j < testCaseKeys.length(); j++) {
                String testCaseKey = testCaseKeys.getString(j);
                ITicket testCase = listOfAllTestCases.stream().filter(t -> t.getKey().equals(testCaseKey)).findFirst().orElse(null);
                if (testCase != null && !finaResults.contains(testCase)) {
                    boolean isConfirmed = relatedTestCaseAgent.run(new RelatedTestCaseAgent.Params(ticketText, testCase.toText(), extraRelatedTestCaseRulesFromConfluence));
                    if (isConfirmed) {
                        finaResults.add(testCase);
                        if (isLink) {
                            trackerClient.linkIssueWithRelationship(ticketKey, testCase.getKey(), relationship);
                        }
                    }
                }
            }
        }
        return finaResults;
    }
}
