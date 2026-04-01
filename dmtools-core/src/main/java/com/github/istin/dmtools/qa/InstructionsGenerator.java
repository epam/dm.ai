// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.agent.InstructionGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.teammate.InstructionProcessor;
import com.github.istin.dmtools.di.DaggerInstructionsGeneratorComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Component;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Job for generating instructions from tracker tickets.
 * Fetches tickets, processes them in chunks based on token limits,
 * generates instructions for specified fields, and outputs to Confluence or local file.
 */
public class InstructionsGenerator extends AbstractJob<InstructionsGeneratorParams, InstructionsGenerator.InstructionsResult> {

    private static final Logger logger = LogManager.getLogger(InstructionsGenerator.class);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    public static class InstructionsResult {
        private int ticketsProcessed;
        private int chunksProcessed;
        private String outputLocation;
        private String generatedInstructions;
        private boolean success;
        private String errorMessage;
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
    InstructionGeneratorAgent instructionGeneratorAgent;

    @Inject
    ContentMergeAgent contentMergeAgent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class, com.github.istin.dmtools.di.AIAgentsModule.class})
    public interface ServerManagedInstructionsGeneratorComponent {
        void inject(InstructionsGenerator instructionsGenerator);
    }

    public InstructionsGenerator() {
        // Don't initialize here - will be done in initializeForMode based on execution mode
    }

    @Override
    protected void initializeStandalone() {
        // Use existing Dagger component for standalone mode
        DaggerInstructionsGeneratorComponent.create().inject(this);
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        // Create dynamic component with pre-resolved integrations
        try {
            ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
            ServerManagedInstructionsGeneratorComponent component = com.github.istin.dmtools.qa.DaggerInstructionsGenerator_ServerManagedInstructionsGeneratorComponent.builder()
                .serverManagedIntegrationsModule(module)
                .build();
            component.inject(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize InstructionsGenerator in server-managed mode", e);
        }
    }

    @Override
    public InstructionsResult runJob(InstructionsGeneratorParams params) throws Exception {
        logger.info("Starting instructions generation with params: {}", params);

        InstructionsResult result = new InstructionsResult();
        result.setSuccess(false);

        try {
            // Collect all tickets using the common TrackerClient interface
            List<ITicket> allTickets = new ArrayList<>();
            trackerClient.searchAndPerform(ticket -> {
                allTickets.add(ticket);
                return false; // Continue processing all tickets
            }, params.getInputJql(), trackerClient.getExtendedQueryFields());

            logger.info("Fetched {} tickets", allTickets.size());
            result.setTicketsProcessed(allTickets.size());

            if (allTickets.isEmpty()) {
                result.setSuccess(true);
                result.setChunksProcessed(0);
                result.setErrorMessage("No tickets found matching the query");
                return result;
            }

            // Process any additional context from Confluence pages
            String additionalContext = extractAdditionalContext(params);

            // Prepare chunks based on token limits (like TestCasesGenerator)
            ChunkPreparation chunkPreparation = new ChunkPreparation();

            // Calculate token limit for chunks
            // Reserve tokens for the instruction generation prompt
            int systemTokenLimits = chunkPreparation.getTokenLimit();
            int reservedTokens = 2000; // Reserve for prompt and fields list
            int tokenLimit = (systemTokenLimits - reservedTokens) / 2;
            logger.info("INSTRUCTION GENERATION TOKEN LIMIT: {}", tokenLimit);

            // Prepare chunks of tickets
            List<ChunkPreparation.Chunk> ticketChunks = chunkPreparation.prepareChunks(allTickets, tokenLimit);
            logger.info("TICKET CHUNKS FOR INSTRUCTION GENERATION: {}", ticketChunks.size());
            result.setChunksProcessed(ticketChunks.size());

            // Generate instructions for each chunk using parallel processing
            List<String> chunkInstructions = generateInstructionsInParallel(
                ticketChunks,
                params,
                additionalContext
            );

            // Merge all chunk instructions
            String mergedInstructions = mergeInstructions(chunkInstructions, params);

            // Handle existing content if needed
            if (params.isMergeWithExisting()) {
                String existingContent = loadExistingContent(params);
                if (existingContent != null && !existingContent.isEmpty()) {
                    mergedInstructions = mergeWithExisting(existingContent, mergedInstructions, params);
                }
            }

            // Output the results
            String outputLocation = outputInstructions(mergedInstructions, params);

            result.setGeneratedInstructions(mergedInstructions);
            result.setOutputLocation(outputLocation);
            result.setSuccess(true);

            logger.info("Successfully generated instructions. Output: {}", outputLocation);

        } catch (Exception e) {
            logger.error("Error generating instructions", e);
            result.setErrorMessage(e.getMessage());
            result.setSuccess(false);
        }

        return result;
    }

    private String generateInstructionsForChunk(String ticketsText, InstructionsGeneratorParams params, String additionalContext) throws Exception {
        // Combine user-provided context with extracted Confluence context
        String combinedContext = combineContext(params.getAdditionalContext(), additionalContext);

        // Prepare agent parameters - send tickets text and fields directly
        InstructionGeneratorAgent.Params agentParams = new InstructionGeneratorAgent.Params();
        agentParams.setInstructionType(params.getInstructionType());
        agentParams.setTargetFields(params.getFields());
        agentParams.setTicketsText(ticketsText);
        agentParams.setAdditionalContext(combinedContext);
        agentParams.setPlatform(params.getPlatform());

        // Generate instructions for this chunk
        return instructionGeneratorAgent.run(params.getModel(), agentParams);
    }

    private String extractAdditionalContext(InstructionsGeneratorParams params) throws IOException {
        if (params.getConfluencePages() == null || params.getConfluencePages().length == 0) {
            return null;
        }

        InstructionProcessor processor = new InstructionProcessor(confluence);
        String[] extractedPages = processor.extractIfNeeded(params.getConfluencePages());

        // Combine all extracted content
        StringBuilder context = new StringBuilder();
        for (String page : extractedPages) {
            if (page != null && !page.isEmpty()) {
                context.append(page).append("\n\n");
            }
        }

        return !context.isEmpty() ? context.toString() : null;
    }

    private String combineContext(String userContext, String extractedContext) {
        if (userContext == null && extractedContext == null) {
            return null;
        }

        StringBuilder combined = new StringBuilder();
        if (userContext != null && !userContext.isEmpty()) {
            combined.append(userContext);
        }
        if (extractedContext != null && !extractedContext.isEmpty()) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append("=== Additional Context from Documentation ===\n");
            combined.append(extractedContext);
        }

        return combined.toString();
    }

    private String mergeInstructions(List<String> chunkInstructions, InstructionsGeneratorParams params) throws Exception {
        if (chunkInstructions.size() == 1) {
            return chunkInstructions.get(0);
        }

        // IMPORTANT: Force actual merging by using smaller token limits
        // This ensures ContentMergeAgent is called to deduplicate and consolidate
        ChunkPreparation chunkPreparation = new ChunkPreparation();

        // Use a reasonable limit that forces merging pairs of instructions
        // This should be roughly 2x the size of a typical instruction chunk
        int maxInstructionChunkSize = 15000; // Typical instruction chunk is 6-7k tokens
        int tokenLimit = maxInstructionChunkSize * 2; // Allow merging 2 chunks at a time

        logger.info("INSTRUCTION MERGING TOKEN LIMIT: {} (forcing pairwise merging)", tokenLimit);

        // Combine all instruction chunks into a single string with separators
        StringBuilder allInstructions = new StringBuilder();
        for (int i = 0; i < chunkInstructions.size(); i++) {
            if (i > 0) {
                allInstructions.append("\n\n---\n\n");
            }
            allInstructions.append(chunkInstructions.get(i));
        }

        // Use ChunkPreparation to split into properly sized chunks
        List<ChunkPreparation.Chunk> instructionChunks = chunkPreparation.prepareChunks(
            Collections.singletonList(allInstructions.toString()),
            tokenLimit
        );

        logger.info("Prepared {} chunks for merging from {} original instruction chunks",
                    instructionChunks.size(), chunkInstructions.size());

        // If we only have one chunk after preparation, return it
        if (instructionChunks.size() == 1) {
            return instructionChunks.get(0).getText();
        }

        // Use parallel merging if we have multiple chunks and threads configured
        if (instructionChunks.size() > 2 && params.getMergingThreads() > 1) {
            logger.info("Using parallel merging with {} threads for {} chunks",
                       params.getMergingThreads(), instructionChunks.size());
            return mergeInstructionsInParallel(instructionChunks, params);
        }

        // Sequential merging for small number of chunks
        logger.info("Using sequential merging for {} chunks", instructionChunks.size());
        String merged = instructionChunks.get(0).getText();

        for (int i = 1; i < instructionChunks.size(); i++) {
            ContentMergeAgent.Params mergeParams = new ContentMergeAgent.Params(
                "Merge instructions for " + params.getInstructionType() + " fields: " + String.join(", ", params.getFields()),
                merged,
                instructionChunks.get(i).getText(),
                "text"
            );

            merged = contentMergeAgent.run(params.getModel(), mergeParams);
            logger.info("Merged chunk {} of {} into accumulated instructions", i + 1, instructionChunks.size());
        }

        return merged;
    }

    private String loadExistingContent(InstructionsGeneratorParams params) throws IOException {
        if ("file".equalsIgnoreCase(params.getOutputDestination())) {
            File file = new File(params.getOutputPath());
            if (file.exists()) {
                return new String(Files.readAllBytes(file.toPath()));
            }
        } else if ("confluence".equalsIgnoreCase(params.getOutputDestination())) {
            try {
                // Use URL to get content
                Content content = confluence.contentByUrl(params.getOutputPath());
                if (content != null) {
                    return content.getStorage().getValue();
                }
            } catch (Exception e) {
                logger.warn("Could not load existing Confluence content from URL: {}", params.getOutputPath(), e);
            }
        }

        return null;
    }

    private String mergeWithExisting(String existingContent, String newContent, InstructionsGeneratorParams params) throws Exception {
        logger.info("Merging with existing content");

        ContentMergeAgent.Params mergeParams = new ContentMergeAgent.Params(
            "Update instructions for " + params.getInstructionType() +
            " by merging new instructions with existing ones. Preserve valuable content from both versions.",
            existingContent,
            newContent,
            "confluence".equalsIgnoreCase(params.getOutputDestination()) ? "html" : "text"
        );

        return contentMergeAgent.run(params.getModel(), mergeParams);
    }

    private String outputInstructions(String instructions, InstructionsGeneratorParams params) throws IOException {
        if ("file".equalsIgnoreCase(params.getOutputDestination())) {
            // Create parent directories if they don't exist
            Path outputPath = Paths.get(params.getOutputPath());
            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("Created directory: {}", parentDir);
            }

            // Write to file
            Files.write(
                outputPath,
                instructions.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            return params.getOutputPath();

        } else if ("confluence".equalsIgnoreCase(params.getOutputDestination())) {
            // Update Confluence page by URL
            try {
                Content existingContent = confluence.contentByUrl(params.getOutputPath());

                if (existingContent != null) {
                    // Update existing page
                    String parentId = existingContent.getParentId();
                    // Extract space key from the Content's key (format: SPACE-ID)
                    String contentKey = existingContent.getKey();
                    String spaceKey = contentKey.substring(0, contentKey.lastIndexOf("-"));

                    confluence.updatePage(
                        existingContent.getId(),
                        existingContent.getTitle(),  // Keep original title
                        parentId != null ? parentId : "",  // parentId
                        instructions,  // new body
                        spaceKey  // space
                    );
                    return "Confluence page updated: " + params.getOutputPath();
                } else {
                    throw new IOException("Confluence page not found at URL: " + params.getOutputPath() +
                        ". Please create the page first or provide an existing page URL.");
                }
            } catch (Exception e) {
                throw new IOException("Failed to write to Confluence URL: " + params.getOutputPath(), e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported output destination: " + params.getOutputDestination());
        }
    }

    /**
     * Generate instructions for chunks in parallel using ExecutorService
     */
    private List<String> generateInstructionsInParallel(
            List<ChunkPreparation.Chunk> chunks,
            InstructionsGeneratorParams params,
            String additionalContext) throws Exception {

        int threadCount = params.getGenerationThreads();
        logger.info("Starting parallel instruction generation with {} threads for {} chunks",
                    threadCount, chunks.size());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();

        try {
            // Submit tasks for parallel execution
            for (int i = 0; i < chunks.size(); i++) {
                final int chunkIndex = i;
                final ChunkPreparation.Chunk chunk = chunks.get(i);

                Future<String> future = executor.submit(() -> {
                    try {
                        logger.info("Thread {} processing chunk {}/{}",
                                   Thread.currentThread().getName(),
                                   chunkIndex + 1, chunks.size());

                        String instructions = generateInstructionsForChunk(
                            chunk.getText(),
                            params,
                            additionalContext
                        );

                        logger.info("Thread {} completed chunk {}/{}",
                                   Thread.currentThread().getName(),
                                   chunkIndex + 1, chunks.size());
                        return instructions;
                    } catch (Exception e) {
                        logger.error("Error processing chunk " + (chunkIndex + 1), e);
                        throw new RuntimeException("Failed to process chunk " + (chunkIndex + 1), e);
                    }
                });

                futures.add(future);
            }

            // Collect results in order
            List<String> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    String result = futures.get(i).get(5, TimeUnit.MINUTES); // 5 min timeout per chunk
                    results.add(result);
                    logger.info("Collected result for chunk {}/{}", i + 1, futures.size());
                } catch (TimeoutException e) {
                    throw new RuntimeException("Timeout processing chunk " + (i + 1), e);
                } catch (Exception e) {
                    throw new RuntimeException("Error getting result for chunk " + (i + 1), e);
                }
            }

            logger.info("Successfully generated instructions for all {} chunks", chunks.size());
            return results;

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                    logger.warn("Executor did not terminate in time, forcing shutdown");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Merge instructions in parallel when there are multiple chunks to merge
     */
    private String mergeInstructionsInParallel(
            List<ChunkPreparation.Chunk> instructionChunks,
            InstructionsGeneratorParams params) throws Exception {

        if (instructionChunks.size() <= 1) {
            return instructionChunks.isEmpty() ? "" : instructionChunks.get(0).getText();
        }

        int threadCount = Math.min(params.getMergingThreads(), instructionChunks.size() / 2);

        // For merging, we use a strategy of merging pairs in parallel
        List<String> currentLevel = instructionChunks.stream()
            .map(ChunkPreparation.Chunk::getText)
            .collect(Collectors.toList());

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<String>> futures = new ArrayList<>();

            try {
                logger.info("Merging {} chunks in parallel with {} threads",
                           currentLevel.size(), threadCount);

                // Process pairs in parallel
                for (int i = 0; i < currentLevel.size(); i += 2) {
                    if (i + 1 < currentLevel.size()) {
                        // Merge pair
                        final String first = currentLevel.get(i);
                        final String second = currentLevel.get(i + 1);
                        final int pairIndex = i / 2;

                        Future<String> future = executor.submit(() -> {
                            try {
                                logger.info("Thread {} merging pair {}",
                                           Thread.currentThread().getName(), pairIndex + 1);

                                ContentMergeAgent.Params mergeParams = new ContentMergeAgent.Params(
                                    "Merge instructions for " + params.getInstructionType() +
                                    " fields: " + String.join(", ", params.getFields()),
                                    first,
                                    second,
                                    "text"
                                );

                                String merged = contentMergeAgent.run(params.getModel(), mergeParams);
                                logger.info("Thread {} completed merging pair {}",
                                           Thread.currentThread().getName(), pairIndex + 1);
                                return merged;
                            } catch (Exception e) {
                                logger.error("Error merging pair " + (pairIndex + 1), e);
                                throw new RuntimeException("Failed to merge pair " + (pairIndex + 1), e);
                            }
                        });

                        futures.add(future);
                    } else {
                        // Odd element, carry forward to next level
                        nextLevel.add(currentLevel.get(i));
                    }
                }

                // Collect merged results
                for (Future<String> future : futures) {
                    try {
                        String result = future.get(5, TimeUnit.MINUTES);
                        nextLevel.add(result);
                    } catch (TimeoutException e) {
                        throw new RuntimeException("Timeout during merging", e);
                    }
                }

                currentLevel = nextLevel;
                logger.info("Completed merge level, {} chunks remaining", currentLevel.size());

            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        return currentLevel.isEmpty() ? "" : currentLevel.get(0);
    }
}