package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for merging multiple AnalysisResult objects
 * Uses ContentMergeAgent to intelligently combine results from chunked processing
 */
public class KBAnalysisResultMerger {
    
    private static final Logger logger = LogManager.getLogger(KBAnalysisResultMerger.class);
    private static final Gson GSON = new Gson();
    
    private final ContentMergeAgent contentMergeAgent;
    
    @Inject
    public KBAnalysisResultMerger(ContentMergeAgent contentMergeAgent) {
        this.contentMergeAgent = contentMergeAgent;
    }
    
    /**
     * Merge multiple analysis results into a single result
     * @param results List of AnalysisResult objects to merge
     * @return Single merged AnalysisResult
     * @throws Exception if merge fails
     */
    public AnalysisResult mergeResults(List<AnalysisResult> results) throws Exception {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty results list");
        }
        
        if (results.size() == 1) {
            logger.debug("Only one result to merge, returning as is");
            return results.get(0);
        }
        
        logger.info("Merging {} analysis results using AI", results.size());
        
        // Convert results to JSON strings
        List<String> jsonResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            AnalysisResult analysisResult = results.get(i);
            String json = GSON.toJson(analysisResult);
            jsonResults.add(json);
            logger.debug("Result {} - Questions: {}, Answers: {}, Notes: {}", 
                    i + 1, 
                    analysisResult.getQuestions().size(),
                    analysisResult.getAnswers().size(),
                    analysisResult.getNotes().size());
        }
        
        // Build combined JSON input for merge
        StringBuilder combinedJson = new StringBuilder("[\n");
        for (int i = 0; i < jsonResults.size(); i++) {
            if (i > 0) {
                combinedJson.append(",\n");
            }
            combinedJson.append(jsonResults.get(i));
        }
        combinedJson.append("\n]");
        
        logger.debug("Combined JSON size: {} characters", combinedJson.length());
        
        // Prepare merge task
        String mergeTask = "Merge these JSON analysis results into a single AnalysisResult. " +
                "Combine all questions, answers, and notes arrays. " +
                "Remove duplicates based on content similarity. " +
                "Preserve all unique information and ID mappings (q_1 → a_1 relationships). " +
                "Return ONLY valid JSON without markdown code blocks.";
        
        // Execute merge
        ContentMergeAgent.Params mergeParams = new ContentMergeAgent.Params(
                mergeTask,
                "", // no source content for merge
                combinedJson.toString(),
                "json"
        );
        
        logger.debug("Executing ContentMergeAgent...");
        String mergedJson = contentMergeAgent.run(mergeParams);
        
        // Parse merged result
        String cleanedResponse = AIResponseParser.parseCodeResponse(mergedJson);
        AnalysisResult mergedResult = GSON.fromJson(cleanedResponse, AnalysisResult.class);
        
        logger.info("Merge completed - Questions: {}, Answers: {}, Notes: {}", 
                mergedResult.getQuestions().size(),
                mergedResult.getAnswers().size(),
                mergedResult.getNotes().size());
        
        return mergedResult;
    }
    
    /**
     * Merge analysis results by processing them in JSON format
     * This is a convenience method that accepts JSON strings directly
     * 
     * @param jsonResults List of JSON strings representing AnalysisResult objects
     * @return Single merged AnalysisResult
     * @throws Exception if parsing or merge fails
     */
    public AnalysisResult mergeFromJson(List<String> jsonResults) throws Exception {
        if (jsonResults == null || jsonResults.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty JSON results list");
        }
        
        logger.debug("Parsing {} JSON results", jsonResults.size());
        List<AnalysisResult> results = new ArrayList<>();
        for (int i = 0; i < jsonResults.size(); i++) {
            try {
                AnalysisResult result = GSON.fromJson(jsonResults.get(i), AnalysisResult.class);
                results.add(result);
            } catch (Exception e) {
                logger.error("Failed to parse JSON result {}: {}", i + 1, e.getMessage());
                throw new Exception("Failed to parse JSON result " + (i + 1), e);
            }
        }
        
        return mergeResults(results);
    }
}

