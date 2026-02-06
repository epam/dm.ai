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
import com.github.istin.dmtools.atlassian.jira.model.Ticket;
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
import com.github.istin.dmtools.teammate.InstructionProcessor;
import dagger.Component;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class TestCasesGenerator extends AbstractJob<TestCasesGeneratorParams, List<TestCasesGenerator.TestCasesResult>> {

    private static final Logger logger = LogManager.getLogger(TestCasesGenerator.class);
    private static final String DEFAULT_EXISTING_RELATIONSHIP = Relationship.RELATES_TO;

    InstructionProcessor instructionProcessor;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class TestCasesResult {
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

        // Initialize instruction processor after dependencies are injected
        this.instructionProcessor = new InstructionProcessor(confluence);
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

            // Initialize instruction processor after dependencies are injected
            this.instructionProcessor = new InstructionProcessor(confluence);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TestCasesGenerator in server-managed mode", e);
        }
    }

    @Override
    public List<TestCasesResult> runJob(TestCasesGeneratorParams params) throws Exception {
        final List<TestCasesResult> result = new ArrayList<>();
        trackerClient.searchAndPerform(ticket -> {
            try {
                // Combine related fields with custom fields
                String[] relatedFields = combineFieldsWithCustomFields(params.getTestCasesRelatedFields(), params.getTestCasesCustomFields());

                // Apply JQL modifier if provided
                String effectiveJql = params.getExistingTestCasesJql();
                if (params.getJqlModifierJSAction() != null && !params.getJqlModifierJSAction().trim().isEmpty()) {
                    effectiveJql = applyJqlModifier(ticket, params);
                }

                List<? extends ITicket> listOfAllTestCases = trackerClient.searchAndPerform(effectiveJql, relatedFields);
                TicketContext ticketContext = new TicketContext(trackerClient, ticket);
                ticketContext.prepareContext(false, params.isIncludeOtherTicketReferences());
                String[] additionalRulesArray = instructionProcessor.extractIfNeeded(params.getConfluencePages());
                String additionalRules = additionalRulesArray.length > 0 ? additionalRulesArray[0] : "";
                result.add(generateTestCases(ticketContext, additionalRules, listOfAllTestCases, params));
                TrackerParams.OutputType outputType = getOutputTypeSafe(params);
                if (!outputType.equals(TrackerParams.OutputType.none)) {
                    trackerClient.postCommentIfNotExists(ticket.getTicketKey(), trackerClient.tag(params.getInitiator()) + ", similar test cases are linked and new test cases are generated.");
                }
            } catch (Exception e) {
                String errorMessage = String.format("%s, test case generation failed with error: %s\n\nStack trace:\n%s", 
                    trackerClient.tag(params.getInitiator()),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    getStackTraceAsString(e));
                try {
                    TrackerParams.OutputType outputType = getOutputTypeSafe(params);
                    if (!outputType.equals(TrackerParams.OutputType.none)) {
                        trackerClient.postComment(ticket.getTicketKey(), errorMessage);
                    }
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
        List<? extends ITicket> currentlyLinked = trackerClient.getTestCases(mainTicket, params.getTestCaseIssueType());
        List<ITicket> finaResults = params.isFindRelated()
                ? findAndLinkSimilarTestCasesBySummary(ticketContext.getTicket().getTicketKey(), ticketText, listOfAllTestCases, params.isLinkRelated(), params.getRelatedTestCasesRules(), existingRelationship, currentlyLinked, params)
                : Collections.emptyList();

        // Initialize accumulator for all generated test cases
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases = new ArrayList<>();
        List<TestCaseGeneratorAgent.TestCase> newTestCases = new ArrayList<>();

        if (params.isGenerateNew()) {
            // Calculate token limits (same pattern as findAndLinkSimilarTestCasesBySummary)
            ChunkPreparation chunkPreparation = new ChunkPreparation();
            int storyTokens = new Claude35TokenCounter().countTokens(ticketContext.toText());
            int systemTokenLimits = chunkPreparation.getTokenLimit();
            int tokenLimit = (systemTokenLimits - storyTokens) / 2;
            System.out.println("GENERATION TOKEN LIMIT: " + tokenLimit);

            // Extract customFieldsRules (may be Confluence URL or file path)
            String customFieldsRules = params.getCustomFieldsRules();
            if (customFieldsRules != null && !customFieldsRules.trim().isEmpty()) {
                String[] customFieldsRulesArray = instructionProcessor.extractIfNeeded(customFieldsRules);
                customFieldsRules = customFieldsRulesArray.length > 0 ? customFieldsRulesArray[0] : "";
            }

            // Combine example fields with custom fields
            String[] exampleFields = combineFieldsWithCustomFields(params.getTestCasesExampleFields(), params.getTestCasesCustomFields());
            String examples = unpackExamples(params.getExamples(), exampleFields, params.getTestCasesCustomFields());

            if (!finaResults.isEmpty()) {
                // Chunk existing test cases for generation
                List<ChunkPreparation.Chunk> testCaseChunks = chunkPreparation.prepareChunks(finaResults, tokenLimit);
                System.out.println("TEST CASE CHUNKS FOR GENERATION: " + testCaseChunks.size());

                // Generate test cases per chunk
                for (ChunkPreparation.Chunk chunk : testCaseChunks) {
                    List<TestCaseGeneratorAgent.TestCase> chunkTestCases = testCaseGeneratorAgent.run(params.getModelTestCasesCreation(),
                            new TestCaseGeneratorAgent.Params(
                                    params.getTestCasesPriorities(),
                                    chunk.getText(), // Chunked test cases instead of all finaResults
                                    ticketText,
                                    extraRules,
                                    params.isOverridePromptExamples(),
                                    examples,
                                    customFieldsRules != null ? customFieldsRules : ""
                            )
                    );
                    allGeneratedTestCases.addAll(chunkTestCases);
                    System.out.println("Generated " + chunkTestCases.size() + " test cases from chunk");
                }

                if (testCaseChunks.size() > 1) {
                    // Deduplicate results - reuse testCaseChunks instead of converting to text again
                    newTestCases = deduplicateInChunks(
                            allGeneratedTestCases,
                            testCaseChunks,
                            params
                    );
                } else {
                    newTestCases = allGeneratedTestCases;
                }
                System.out.println("Final deduplicated test cases: " + newTestCases.size());
            } else {
                newTestCases = testCaseGeneratorAgent.run(params.getModelTestCasesCreation(),
                        new TestCaseGeneratorAgent.Params(
                                params.getTestCasesPriorities(),
                                "", // Chunked test cases instead of all finaResults
                                ticketContext.toText(),
                                extraRules,
                                params.isOverridePromptExamples(),
                                examples,
                                customFieldsRules != null ? customFieldsRules : ""
                        )
                );
            }
        }
        
        // Preprocess test cases to handle preconditions with temporary IDs
        if (params.getPreprocessJSAction() != null && !params.getPreprocessJSAction().trim().isEmpty()) {
            newTestCases = preprocessTestCases(newTestCases, params, ticketContext);
        }
        
        TestCasesResult testCasesResult = new TestCasesResult(ticketContext.getTicket().getKey(), finaResults, newTestCases);

        TrackerParams.OutputType outputType = getOutputTypeSafe(params);
        
        if (outputType.equals(TrackerParams.OutputType.comment)) {
            StringBuilder result = new StringBuilder();
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                result.append("Summary: ").append(testCase.getSummary()).append("<br>");
                result.append("Priority: ").append(testCase.getPriority()).append("<br>");
                result.append("Description: ").append(StringUtils.convertToMarkdown(testCase.getDescription())).append("<br>");
            }
            trackerClient.postComment(key, result.toString());
        } else if (outputType.equals(TrackerParams.OutputType.creation)) {
            String newTestCaseRelationship = resolveRelationshipForNew(params);
            for (TestCaseGeneratorAgent.TestCase testCase : newTestCases) {
                // Get project code: use targetProject if provided, otherwise extract from mainTicket
                String projectCode = params.getTargetProject();
                if (!isNotBlank(projectCode)) {
                    if (mainTicket instanceof WorkItem) {
                        projectCode = ((WorkItem) mainTicket).getProject();
                        if (projectCode == null || projectCode.isEmpty()) {
                            throw new IOException("Unable to determine project from ADO work item " + key);
                        }
                    } else {
                        // Jira format: PROJ-123 -> PROJ
                        projectCode = key.split("-")[0];
                    }
                }
                String description = testCase.getDescription();
                if (params.isConvertToJiraMarkdown()) {
                    description = StringUtils.convertToMarkdown(description);
                }
                // Create FieldsInitializer with tracker-specific field formats
                final boolean isAdo = trackerClient instanceof AzureDevOpsClient;
                final JSONObject customFields = testCase.getCustomFields() != null ? testCase.getCustomFields() : new JSONObject();
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
                        
                        // Set custom fields from customFields object
                        if (!customFields.isEmpty()) {
                            for (String fieldKey : customFields.keySet()) {
                                Object fieldValue = customFields.get(fieldKey);
                                if (fieldValue != null) {
                                    fields.set(fieldKey, fieldValue);
                                }
                            }
                        }
                    }
                }));
                testCase.setKey(createdTestCase.getKey());
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

    public String unpackExamples(String examples, String[] testCasesExamplesFields, String[] testCasesCustomFields) throws Exception {
        if (examples == null) {
            return "";
        }
        String unpackedExamples;
        if (examples.startsWith("https://") || examples.startsWith("/") || examples.startsWith("./") || examples.startsWith("../")) {
            String[] unpackedExamplesArray = instructionProcessor.extractIfNeeded(examples);
            unpackedExamples = unpackedExamplesArray.length > 0 ? unpackedExamplesArray[0] : "";
        } else if (examples.startsWith("ql(")) {
            String ql = examples.substring(3, examples.length() - 1);
            
            // testCasesExamplesFields already includes custom fields (combined in generateTestCases)
            // So we can use it directly
            List<? extends ITicket> tickets = trackerClient.searchAndPerform(ql, testCasesExamplesFields);

            JSONArray examplesArray = new JSONArray();
            for (ITicket ticket : tickets) {
                // Extract custom fields if specified
                JSONObject customFields = new JSONObject();
                if (testCasesCustomFields != null && testCasesCustomFields.length > 0) {
                    JSONObject fieldsJson = ticket.getFieldsAsJSON();
                    for (String customFieldName : testCasesCustomFields) {
                        // Check if field exists in JSON first
                        if (fieldsJson != null && fieldsJson.has(customFieldName)) {
                            // Extract directly from fields JSONObject to preserve type
                            // (works for String, JSONArray, JSONObject - xrayDataset, xrayTestSteps, xrayPreconditions, xrayGherkin)
                            Object fieldObj = fieldsJson.get(customFieldName);
                            if (fieldObj != null && !JSONObject.NULL.equals(fieldObj)) {
                                customFields.put(customFieldName, fieldObj);
                            }
                        } else {
                            // Fallback: try standard field access for simple string fields
                            String fieldValue = ticket.getFieldValueAsString(customFieldName);
                            if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                                customFields.put(customFieldName, fieldValue);
                            }
                        }
                    }
                }
                
                // Create test case with custom fields (if any)
                JSONObject testCaseJson = TestCaseGeneratorAgent.createTestCase(
                    ticket.getPriority(), 
                    ticket.getTicketTitle(), 
                    ticket.getTicketDescription(),
                    customFields.length() > 0 ? customFields : null
                );
                examplesArray.put(testCaseJson);
            }
            unpackedExamples = examplesArray.toString();
        } else {
            unpackedExamples = examples;
        }
        return unpackedExamples;
    }

    private List<TestCaseGeneratorAgent.TestCase> deduplicateInChunks(
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases,
        List<ChunkPreparation.Chunk> existingTestCaseChunks,
        TestCasesGeneratorParams params
    ) throws Exception {
        // Step 1: Deduplicate allGeneratedTestCases against itself to remove internal duplicates
        System.out.println("Step 1: Deduplicating generated test cases against themselves");
        List<TestCaseGeneratorAgent.TestCase> selfDeduplicated = deduplicateSelfInChunks(allGeneratedTestCases, params);
        System.out.println("After self-deduplication: " + selfDeduplicated.size() + " unique test cases");
        
        // Step 2: Deduplicate against existing test cases - use already prepared chunks
        System.out.println("Step 2: Deduplicating against existing test cases");
        List<TestCaseGeneratorAgent.TestCase> finalDeduplicated = deduplicateAgainstExistingInChunks(selfDeduplicated, existingTestCaseChunks, params);
        System.out.println("After deduplication against existing: " + finalDeduplicated.size() + " test cases");
        
        return finalDeduplicated;
    }
    
    private List<TestCaseGeneratorAgent.TestCase> deduplicateSelfInChunks(
        List<TestCaseGeneratorAgent.TestCase> allGeneratedTestCases,
        TestCasesGeneratorParams params
    ) throws Exception {
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        int tokenLimit = systemTokenLimits / 2; // Use half the system limit for self-deduplication
        
        String allGeneratedText = ToText.Utils.toText(allGeneratedTestCases);
        int generatedTokens = new Claude35TokenCounter().countTokens(allGeneratedText);
        
        // Single deduplication call if small enough
        if (generatedTokens <= tokenLimit) {
            return testCaseDeduplicationAgent.run(params.getModelTestCaseDeduplication(),
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
            List<TestCaseGeneratorAgent.TestCase> deduplicatedChunk = testCaseDeduplicationAgent.run(params.getModelTestCaseDeduplication(),
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
        List<ChunkPreparation.Chunk> existingTestCaseChunks,
        TestCasesGeneratorParams params
    ) throws Exception {
        // Deduplicate new test cases against each chunk of existing test cases
        for (ChunkPreparation.Chunk existingChunk : existingTestCaseChunks) {
            String newTestCasesText = ToText.Utils.toText(newTestCases);
            String existingTestCasesText = existingChunk.getText();
            
            List<TestCaseGeneratorAgent.TestCase> deduplicatedAgainstChunk = testCaseDeduplicationAgent.run(params.getModelTestCaseDeduplication(),
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

    /**
     * Safely gets the output type from params.
     * For TestCasesGenerator, if the output type is null or not specified,
     * we default to 'creation' because its primary purpose is to create new test cases.
     */
    private TrackerParams.OutputType getOutputTypeSafe(TestCasesGeneratorParams params) {
        TrackerParams.OutputType outputType = params.getOutputType();
        if (outputType == null) {
            return TrackerParams.OutputType.creation;
        }
        return outputType;
    }

    /**
     * Combines base fields with custom fields, ensuring no duplicates.
     * If customFields is null or empty, returns only baseFields.
     * 
     * @param baseFields Base fields array (e.g., testCasesRelatedFields or testCasesExampleFields)
     * @param customFields Custom fields array (testCasesCustomFields)
     * @return Combined array with unique fields
     */
    private String[] combineFieldsWithCustomFields(String[] baseFields, String[] customFields) {
        if (customFields == null || customFields.length == 0) {
            // Backward compatibility: if customFields not specified, return baseFields as-is
            return baseFields != null ? baseFields : new String[0];
        }
        
        if (baseFields == null || baseFields.length == 0) {
            return customFields;
        }
        
        // Combine fields, avoiding duplicates
        List<String> combinedList = new ArrayList<>();
        
        // Add base fields first
        for (String field : baseFields) {
            if (field != null && !field.trim().isEmpty() && !combinedList.contains(field)) {
                combinedList.add(field);
            }
        }
        
        // Add custom fields
        for (String customField : customFields) {
            if (customField != null && !customField.trim().isEmpty() && !combinedList.contains(customField)) {
                combinedList.add(customField);
            }
        }
        
        return combinedList.toArray(new String[0]);
    }

    /**
     * Verifies if a test case is related to the story and optionally links it.
     * @return the test case if confirmed, null otherwise
     */
    private ITicket verifyAndLinkTestCase(
            ITicket testCase,
            String ticketKey,
            String ticketText,
            boolean isLink,
            String relationship,
            List<? extends ITicket> currentlyLinkedTestCases,
            String extraRelatedTestCaseRulesFromConfluence,
            TestCasesGeneratorParams params,
            boolean needSync) throws Exception {

        boolean isConfirmed = relatedTestCaseAgent.run(
            params.getModelTestCaseRelation(),
            new RelatedTestCaseAgent.Params(ticketText, testCase.toText(), extraRelatedTestCaseRulesFromConfluence)
        );

        if (isConfirmed) {
            if (isLink) {
                boolean isAlreadyLinked = currentlyLinkedTestCases != null &&
                    currentlyLinkedTestCases.stream().anyMatch(t -> t.getTicketKey().equals(testCase.getTicketKey()));
                if (!isAlreadyLinked) {
                    if (needSync) {
                        synchronized (trackerClient) {
                            trackerClient.linkIssueWithRelationship(ticketKey, testCase.getKey(), relationship);
                        }
                    } else {
                        trackerClient.linkIssueWithRelationship(ticketKey, testCase.getKey(), relationship);
                    }
                }
            }
            return testCase;
        }
        return null;
    }

    /**
     * Processes a single chunk to find related test cases and optionally verify them.
     * This method eliminates code duplication between parallel and sequential processing.
     */
    private List<ITicket> processChunk(
            ChunkPreparation.Chunk chunk,
            String ticketKey,
            String ticketText,
            List<? extends ITicket> listOfAllTestCases,
            boolean isLink,
            String relationship,
            List<? extends ITicket> currentlyLinkedTestCases,
            String extraRelatedTestCaseRulesFromConfluence,
            TestCasesGeneratorParams params) throws Exception {

        List<ITicket> chunkResults = new ArrayList<>();

        // Get potential test cases from the chunk
        JSONArray testCaseKeys = relatedTestCasesAgent.run(
            params.getModelTestCasesRelation(),
            new RelatedTestCasesAgent.Params(ticketText, chunk.getText(), extraRelatedTestCaseRulesFromConfluence)
        );

        // Prepare list of test cases to verify
        List<ITicket> testCasesToVerify = new ArrayList<>();
        for (int j = 0; j < testCaseKeys.length(); j++) {
            String testCaseKey = testCaseKeys.getString(j);
            ITicket testCase = listOfAllTestCases.stream()
                .filter(t -> t.getKey().equals(testCaseKey))
                .findFirst()
                .orElse(null);
            if (testCase != null) {
                testCasesToVerify.add(testCase);
            }
        }

        // Process post-verification either in parallel or sequentially
        if (params.isEnableParallelPostVerification() && testCasesToVerify.size() > 1) {
            ExecutorService postVerificationExecutor = Executors.newFixedThreadPool(
                Math.min(params.getParallelPostVerificationThreads(), testCasesToVerify.size())
            );
            try {
                List<Future<ITicket>> verificationFutures = new ArrayList<>();

                for (ITicket testCase : testCasesToVerify) {
                    verificationFutures.add(postVerificationExecutor.submit(() ->
                        verifyAndLinkTestCase(testCase, ticketKey, ticketText, isLink, relationship,
                                            currentlyLinkedTestCases, extraRelatedTestCaseRulesFromConfluence,
                                            params, true)
                    ));
                }

                // Collect verified results
                for (Future<ITicket> future : verificationFutures) {
                    ITicket verifiedTestCase = future.get();
                    if (verifiedTestCase != null) {
                        chunkResults.add(verifiedTestCase);
                    }
                }
            } finally {
                postVerificationExecutor.shutdown();
                // Wait for all verifications to complete without timeout - LLM requests can take time
                try {
                    postVerificationExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    postVerificationExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // Sequential post-verification
            for (ITicket testCase : testCasesToVerify) {
                ITicket verifiedTestCase = verifyAndLinkTestCase(testCase, ticketKey, ticketText, isLink,
                                                                 relationship, currentlyLinkedTestCases,
                                                                 extraRelatedTestCaseRulesFromConfluence, params, false);
                if (verifiedTestCase != null) {
                    chunkResults.add(verifiedTestCase);
                }
            }
        }

        return chunkResults;
    }

    @NotNull
    public List<ITicket> findAndLinkSimilarTestCasesBySummary(String ticketKey, String ticketText, List<? extends ITicket> listOfAllTestCases, boolean isLink, String relatedTestCasesRulesLink, String relationship, List<? extends ITicket> currentlyLinkedTestCases, TestCasesGeneratorParams params) throws Exception {
        List<ITicket> finaResults = new ArrayList<>();
        String[] extraRelatedTestCaseRulesArray = instructionProcessor.extractIfNeeded(relatedTestCasesRulesLink);
        String extraRelatedTestCaseRulesFromConfluence = extraRelatedTestCaseRulesArray.length > 0 ? extraRelatedTestCaseRulesArray[0] : "";
        ChunkPreparation chunkPreparation = new ChunkPreparation();

        int storyTokens = new Claude35TokenCounter().countTokens(ticketText);
        System.out.println("STORY TOKENS: " + storyTokens);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        System.out.println("SYSTEM TOKEN LIMITS: " + systemTokenLimits);
        int tokenLimit = (systemTokenLimits - storyTokens)/2;
        System.out.println("TESTCASES TOKEN LIMITS: " + tokenLimit);
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(listOfAllTestCases, tokenLimit);

        if (params.isEnableParallelTestCaseCheck()) {
            // Parallel chunk processing
            ExecutorService executorService = Executors.newFixedThreadPool(params.getParallelTestCaseCheckThreads());
            try {
                List<Future<List<ITicket>>> futures = new ArrayList<>();

                for (ChunkPreparation.Chunk chunk : chunks) {
                    futures.add(executorService.submit(() ->
                        processChunk(chunk, ticketKey, ticketText, listOfAllTestCases, isLink,
                                   relationship, currentlyLinkedTestCases, extraRelatedTestCaseRulesFromConfluence, params)
                    ));
                }

                // Collect results from all chunks
                for (Future<List<ITicket>> future : futures) {
                    List<ITicket> chunkResults = future.get();
                    for (ITicket result : chunkResults) {
                        if (!finaResults.contains(result)) {
                            finaResults.add(result);
                        }
                    }
                }
            } finally {
                executorService.shutdown();
                // Wait for all tasks to complete without timeout - LLM requests can take time
                try {
                    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // Sequential chunk processing
            for (ChunkPreparation.Chunk chunk : chunks) {
                List<ITicket> chunkResults = processChunk(chunk, ticketKey, ticketText, listOfAllTestCases,
                                                          isLink, relationship, currentlyLinkedTestCases,
                                                          extraRelatedTestCaseRulesFromConfluence, params);
                for (ITicket result : chunkResults) {
                    if (!finaResults.contains(result)) {
                        finaResults.add(result);
                    }
                }
            }
        }
        return finaResults;
    }
    
    /**
     * Preprocess test cases using JavaScript function to handle preconditions with temporary IDs.
     * The JS function should create Precondition issues and replace temporary IDs with real keys.
     * 
     * @param testCases List of test cases to preprocess
     * @param params Test cases generator parameters
     * @param ticketContext Ticket context for the main ticket
     * @return Preprocessed list of test cases with temporary IDs replaced
     * @throws Exception if preprocessing fails
     */
    private List<TestCaseGeneratorAgent.TestCase> preprocessTestCases(
            List<TestCaseGeneratorAgent.TestCase> testCases,
            TestCasesGeneratorParams params,
            TicketContext ticketContext
    ) throws Exception {
        if (testCases == null || testCases.isEmpty()) {
            return testCases;
        }
        
        // Convert test cases to JSON array
        JSONArray testCasesJson = new JSONArray();
        for (TestCaseGeneratorAgent.TestCase testCase : testCases) {
            JSONObject testCaseJson = new JSONObject();
            testCaseJson.put("priority", testCase.getPriority());
            testCaseJson.put("summary", testCase.getSummary());
            testCaseJson.put("description", testCase.getDescription());
            if (testCase.getCustomFields() != null && testCase.getCustomFields().length() > 0) {
                testCaseJson.put("customFields", testCase.getCustomFields());
            }
            testCasesJson.put(testCaseJson);
        }
        
        // Execute JavaScript preprocessing
        Object result = js(params.getPreprocessJSAction())
                .mcp(trackerClient, ai, confluence, null)
                .withJobContext(params, ticketContext.getTicket(), null)
                .with(TrackerParams.INITIATOR, params.getInitiator())
                .with("newTestCases", testCasesJson)
                .execute();
        
        // Convert result back to List<TestCase>
        if (result == null) {
            logger.warn("JavaScript preprocessing returned null, using original test cases");
            return testCases;
        }
        
        JSONArray preprocessedJson;
        if (result instanceof JSONArray) {
            preprocessedJson = (JSONArray) result;
        } else if (result instanceof String) {
            try {
                preprocessedJson = new JSONArray((String) result);
            } catch (Exception e) {
                logger.error("Failed to parse JavaScript preprocessing result as JSON array: {}", e.getMessage());
                return testCases;
            }
        } else {
            // Handle PolyglotList or other collection types
            try {
                // Try to convert using reflection to detect PolyglotList
                String className = result.getClass().getName();
                if (className.contains("PolyglotList") || className.contains("List")) {
                    // Convert to JSONArray by iterating
                    preprocessedJson = new JSONArray();
                    if (result instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) result;
                        for (Object item : list) {
                            if (item instanceof JSONObject) {
                                preprocessedJson.put(item);
                            } else if (item instanceof java.util.Map) {
                                preprocessedJson.put(new JSONObject((java.util.Map<?, ?>) item));
                            } else {
                                // Try to convert using Gson
                                try {
                                    com.google.gson.Gson gson = new com.google.gson.Gson();
                                    String jsonStr = gson.toJson(item);
                                    preprocessedJson.put(new JSONObject(jsonStr));
                                } catch (Exception e) {
                                    logger.warn("Failed to convert item to JSONObject: {}", e.getMessage());
                                }
                            }
                        }
                    } else {
                        // Try to use toString() and parse as JSON
                        String resultStr = result.toString();
                        preprocessedJson = new JSONArray(resultStr);
                    }
                } else {
                    // Try to parse as JSON string
                    String resultStr = result.toString();
                    preprocessedJson = new JSONArray(resultStr);
                }
            } catch (Exception e) {
                logger.error("Failed to convert JavaScript preprocessing result to JSONArray (type: {}): {}", 
                        result.getClass().getName(), e.getMessage());
                return testCases;
            }
        }
        
        // Convert JSON array back to List<TestCase>
        List<TestCaseGeneratorAgent.TestCase> preprocessedTestCases = new ArrayList<>();
        for (int i = 0; i < preprocessedJson.length(); i++) {
            JSONObject testCaseJson = preprocessedJson.getJSONObject(i);
            String priority = testCaseJson.optString("priority", "");
            String summary = testCaseJson.getString("summary");
            String description = testCaseJson.getString("description");
            JSONObject customFields = testCaseJson.optJSONObject("customFields");
            if (customFields == null) {
                customFields = new JSONObject();
            }
            preprocessedTestCases.add(new TestCaseGeneratorAgent.TestCase(priority, summary, description, customFields));
        }
        
        logger.info("Preprocessed {} test cases via JavaScript", preprocessedTestCases.size());
        return preprocessedTestCases;
    }

    /**
     * Apply JavaScript-based JQL modification to filter existing test cases dynamically.
     * Allows filtering test cases based on story ticket properties (labels, priority, etc.)
     * Falls back to original JQL on any error (fail-safe behavior).
     */
    private String applyJqlModifier(ITicket ticket, TestCasesGeneratorParams params) {
        String originalJql = params.getExistingTestCasesJql();

        if (originalJql == null || originalJql.trim().isEmpty()) {
            logger.warn("Original existingTestCasesJql is empty, skipping JQL modification");
            return originalJql;
        }

        try {
            logger.info("Applying JQL modifier for ticket: {}", ticket.getTicketKey());

            // Execute JavaScript modifier
            Object result = js(params.getJqlModifierJSAction())
                    .mcp(trackerClient, ai, confluence, null)
                    .with("ticket", ticket)
                    .with("jobParams", params)
                    .with("existingTestCasesJql", originalJql)
                    .execute();

            // Parse result - supports multiple return types
            String modifiedJql = extractJqlFromResult(result, originalJql);

            if (modifiedJql != null && !modifiedJql.trim().isEmpty() && !modifiedJql.equals(originalJql)) {
                logger.info("JQL modified from '{}' to '{}'", originalJql, modifiedJql);
                return modifiedJql.trim();
            } else {
                logger.info("JQL modifier did not change JQL, using original");
                return originalJql;
            }

        } catch (Exception e) {
            logger.error("Failed to apply JQL modifier for ticket {}: {}",
                    ticket.getTicketKey(), e.getMessage(), e);
            logger.warn("Falling back to original JQL due to error");
            return originalJql;
        }
    }

    /**
     * Extract JQL string from JavaScript result (handles multiple return types).
     * Supports: JSONObject with field, String, Map (PolyglotMap from GraalJS)
     */
    private String extractJqlFromResult(Object result, String fallback) {
        if (result == null) {
            return fallback;
        }

        // Handle JSONObject
        if (result instanceof JSONObject) {
            JSONObject resultJson = (JSONObject) result;
            if (resultJson.has("existingTestCasesJql")) {
                return resultJson.getString("existingTestCasesJql");
            }
        }

        // Handle String (try parsing as JSON first, then use as-is)
        if (result instanceof String) {
            String resultStr = (String) result;
            try {
                JSONObject resultJson = new JSONObject(resultStr);
                if (resultJson.has("existingTestCasesJql")) {
                    return resultJson.getString("existingTestCasesJql");
                }
            } catch (Exception e) {
                // Not JSON - assume string is the JQL itself
                return resultStr;
            }
        }

        // Handle Map (PolyglotMap from GraalJS)
        if (result instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resultMap = (java.util.Map<String, Object>) result;
            if (resultMap.containsKey("existingTestCasesJql")) {
                Object jqlValue = resultMap.get("existingTestCasesJql");
                if (jqlValue != null) {
                    return jqlValue.toString();
                }
            }
        }

        return fallback;
    }

}
