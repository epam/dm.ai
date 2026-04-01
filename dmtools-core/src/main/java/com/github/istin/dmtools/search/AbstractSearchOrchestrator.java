// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.search;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.KeywordGeneratorAgent;
import com.github.istin.dmtools.ai.agent.SearchResultsAssessmentAgent;
import com.github.istin.dmtools.ai.agent.SnippetExtensionAgent;
import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractSearchOrchestrator {

    protected KeywordGeneratorAgent keywordGeneratorAgent;

    protected SnippetExtensionAgent snippetExtensionAgent;

    protected SummaryContextAgent summaryContextAgent;

    protected SearchResultsAssessmentAgent searchResultsAssessmentAgent;

    protected ContextOrchestrator contextOrchestrator;

    protected final TrackerClient trackerClient;
    
    protected SearchStats searchStats;

    public AbstractSearchOrchestrator(TrackerClient trackerClient) {
        this.trackerClient = trackerClient;
    }
    
    // Constructor for orchestrators that don't need TrackerClient
    public AbstractSearchOrchestrator() {
        this.trackerClient = null;
    }

    public TrackerClient getTrackerClient() {
        return trackerClient;
    }

    /**
     * Wires agent dependencies that cannot be injected by Dagger because orchestrators
     * are created via {@code new} inside {@code @Provides} methods. Must be called
     * immediately after construction before {@link #run} is invoked.
     */
    public void initAgents(KeywordGeneratorAgent keywordGeneratorAgent,
                           SnippetExtensionAgent snippetExtensionAgent,
                           SummaryContextAgent summaryContextAgent,
                           SearchResultsAssessmentAgent searchResultsAssessmentAgent,
                           ContextOrchestrator contextOrchestrator) {
        this.keywordGeneratorAgent = keywordGeneratorAgent;
        this.snippetExtensionAgent = snippetExtensionAgent;
        this.summaryContextAgent = summaryContextAgent;
        this.searchResultsAssessmentAgent = searchResultsAssessmentAgent;
        this.contextOrchestrator = contextOrchestrator;
    }

    public SearchStats getSearchStats() {
        return searchStats;
    }

    protected abstract Object getItemByKey(Object key, List<?> items);
    protected abstract String getKeyFieldValue();
    protected abstract String getSourceType();
    protected abstract String getFullItemContent(Object item, Object platformContext) throws Exception;
    protected abstract String getItemResourceKey(Object item);
    public abstract List<?> searchItemsWithKeywords(String keyword, Object platformContext, int itemsLimit) throws Exception;
    public abstract Object createInitialPlatformContext();

    protected List<Object> preprocessing(SearchStats searchStats, String fullTask, Object platformContext) throws Exception {
        return null;
    }


    protected JSONArray generateKeywords(String fullTask, String blacklist) throws Exception {
        return keywordGeneratorAgent.run(new KeywordGeneratorAgent.Params(getSourceType() + "_search", fullTask, blacklist));
    }

    protected String updateBlacklist(String currentBlacklist, JSONArray keywords) {
        StringBuilder updatedBlacklist = new StringBuilder(currentBlacklist);
        for (int i = 0; i < keywords.length(); i++) {
            if (!updatedBlacklist.isEmpty()) {
                updatedBlacklist.append(",");
            }
            updatedBlacklist.append(keywords.getString(i));
        }
        return updatedBlacklist.toString();
    }

    public List<ChunkPreparation.Chunk> run(String fullTask, String keywordsBlacklist, int itemsLimit, int iterations) throws Exception {
        this.searchStats = new SearchStats();
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Object platformContext = createInitialPlatformContext();
        List<Object> items = new ArrayList<>();
        List<Object> initialList = preprocessing(this.searchStats, fullTask, platformContext);
        if (initialList != null) {
            items.addAll(initialList);
        }
        for (int iteration = 0; iteration < iterations; iteration++) {
            JSONArray keywords = generateKeywords(fullTask, keywordsBlacklist);
            List<String> keywordsList = new ArrayList<>();
            for (int i = 0; i < keywords.length(); i++) {
                keywordsList.add(keywords.getString(i));
            }

            SearchStats.IterationStats stats = new SearchStats.IterationStats(iteration, keywordsList);
            for (String keyword : keywordsList) {
                List<?> itemsFound = searchItemsWithKeywords(keyword, platformContext, itemsLimit);
                stats.keywordStats.put(keyword, new SearchStats.IterationStats.KeywordStats(itemsFound.size()));
                items.addAll(itemsFound);
            }

            this.searchStats.addIterationStats(stats);
            this.searchStats.totalItemsProcessed += items.size();
            if (!items.isEmpty()) {
                List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(items);
                Set<String> relevantKeys = new HashSet<>();
                for (ChunkPreparation.Chunk chunk : chunks) {
                    SearchResultsAssessmentAgent.Params params = new SearchResultsAssessmentAgent.Params(
                            getSourceType(),
                            getKeyFieldValue(),
                            fullTask,
                            chunk.getText()
                    );
                    JSONArray newRelevantKeys = searchResultsAssessmentAgent.run(params);
                    for (int i = 0; i < newRelevantKeys.length(); i++) {
                        relevantKeys.add(newRelevantKeys.getString(i));
                    }
                }
                stats.addRelevantKeys(relevantKeys);

                for (String key : relevantKeys) {
                    Object itemByKey = getItemByKey(key, items);
                    if (itemByKey != null) {
                        String fullContent = getFullItemContent(itemByKey, platformContext);
                        contextOrchestrator.processFullContent(key, fullContent, (UriToObject) platformContext, null, 0);
                    }
                }
            }
            keywordsBlacklist = updateBlacklist(keywordsBlacklist, keywords);
        }

         return contextOrchestrator.summarize();
    }

}