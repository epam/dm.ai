package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.agent.KBAnalysisAgent;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles chunk analysis delegation to the KBAnalysisAgent and merges partial results.
 */
public class KBChunkAnalyzer {

    private final KBAnalysisAgent analysisAgent;
    private final KBAnalysisResultMerger resultMerger;

    public KBChunkAnalyzer(KBAnalysisAgent analysisAgent, KBAnalysisResultMerger resultMerger) {
        this.analysisAgent = analysisAgent;
        this.resultMerger = resultMerger;
    }

    public AnalysisResult analyzeChunk(String text,
                                       String sourceName,
                                       KBContext context,
                                       String extraInstructions) throws Exception {
        AnalysisParams params = new AnalysisParams();
        params.setInputText(text);
        params.setSourceName(sourceName);
        params.setContext(context);
        params.setExtraInstructions(extraInstructions);
        return analysisAgent.run(params);
    }

    public AnalysisResult analyzeAndMergeChunks(List<ChunkPreparation.Chunk> chunks,
                                                String sourceName,
                                                KBContext context,
                                                String extraInstructions,
                                                Logger logger) throws Exception {
        List<AnalysisResult> chunkResults = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (logger != null) {
                logger.info("Processing chunk {} of {}", i + 1, chunks.size());
            }
            chunkResults.add(analyzeChunk(chunks.get(i).getText(), sourceName, context, extraInstructions));
        }
        if (logger != null) {
            logger.info("Merging {} chunk results", chunkResults.size());
        }
        return resultMerger.mergeResults(chunkResults);
    }
}
