package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.agent.KBAnalysisAgent;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.KBContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KBChunkAnalyzerTest {

    private KBAnalysisAgent analysisAgent;
    private KBAnalysisResultMerger resultMerger;
    private KBChunkAnalyzer chunkAnalyzer;
    private KBContext context;
    private Logger logger;

    @BeforeEach
    void setUp() {
        analysisAgent = mock(KBAnalysisAgent.class);
        resultMerger = mock(KBAnalysisResultMerger.class);
        chunkAnalyzer = new KBChunkAnalyzer(analysisAgent, resultMerger);
        context = new KBContext();
        logger = LogManager.getLogger(KBChunkAnalyzerTest.class);
    }

    @Test
    void analyzeChunkDelegatesToAgent() throws Exception {
        AnalysisResult expected = new AnalysisResult();
        when(analysisAgent.run(any())).thenReturn(expected);

        AnalysisResult result = chunkAnalyzer.analyzeChunk("text", "source", context, "extra");

        assertSame(expected, result);
        ArgumentCaptor<com.github.istin.dmtools.common.kb.params.AnalysisParams> captor = ArgumentCaptor.forClass(com.github.istin.dmtools.common.kb.params.AnalysisParams.class);
        verify(analysisAgent).run(captor.capture());
        com.github.istin.dmtools.common.kb.params.AnalysisParams params = captor.getValue();
        assertEquals("text", params.getInputText());
        assertEquals("source", params.getSourceName());
        assertEquals(context, params.getContext());
        assertEquals("extra", params.getExtraInstructions());
    }

    @Test
    void analyzeAndMergeChunksProcessesAllChunks() throws Exception {
        ChunkPreparation.Chunk chunk1 = new ChunkPreparation.Chunk("chunk1", Collections.<File>emptyList(), 0);
        ChunkPreparation.Chunk chunk2 = new ChunkPreparation.Chunk("chunk2", Collections.<File>emptyList(), 0);
        AnalysisResult result1 = new AnalysisResult();
        AnalysisResult result2 = new AnalysisResult();
        AnalysisResult merged = new AnalysisResult();

        when(analysisAgent.run(any())).thenReturn(result1, result2);
        when(resultMerger.mergeResults(any())).thenReturn(merged);

        AnalysisResult result = chunkAnalyzer.analyzeAndMergeChunks(List.of(chunk1, chunk2),
                "source", context, null, logger);

        assertSame(merged, result);
        verify(analysisAgent, times(2)).run(any());
        ArgumentCaptor<List<AnalysisResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultMerger).mergeResults(captor.capture());
        assertEquals(List.of(result1, result2), captor.getValue());
    }
}
