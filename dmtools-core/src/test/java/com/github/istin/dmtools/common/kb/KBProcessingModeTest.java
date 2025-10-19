package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.KBProcessingMode;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBProcessingMode functionality
 */
public class KBProcessingModeTest {

    @Test
    public void testDefaultModeIsFull() {
        KBOrchestratorParams params = new KBOrchestratorParams();
        assertEquals(KBProcessingMode.FULL, params.getProcessingMode(), 
                "Default processing mode should be FULL");
    }

    @Test
    public void testSetProcessOnlyMode() {
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setProcessingMode(KBProcessingMode.PROCESS_ONLY);
        
        assertEquals(KBProcessingMode.PROCESS_ONLY, params.getProcessingMode());
        assertNotEquals(KBProcessingMode.FULL, params.getProcessingMode());
        assertNotEquals(KBProcessingMode.AGGREGATE_ONLY, params.getProcessingMode());
    }

    @Test
    public void testSetAggregateOnlyMode() {
        KBOrchestratorParams params = new KBOrchestratorParams();
        params.setProcessingMode(KBProcessingMode.AGGREGATE_ONLY);
        
        assertEquals(KBProcessingMode.AGGREGATE_ONLY, params.getProcessingMode());
        assertNotEquals(KBProcessingMode.FULL, params.getProcessingMode());
        assertNotEquals(KBProcessingMode.PROCESS_ONLY, params.getProcessingMode());
    }

    @Test
    public void testAllModesHaveValues() {
        assertNotNull(KBProcessingMode.FULL);
        assertNotNull(KBProcessingMode.PROCESS_ONLY);
        assertNotNull(KBProcessingMode.AGGREGATE_ONLY);
        
        assertEquals(3, KBProcessingMode.values().length, 
                "Should have exactly 3 processing modes");
    }

    @Test
    public void testModeNames() {
        assertEquals("FULL", KBProcessingMode.FULL.name());
        assertEquals("PROCESS_ONLY", KBProcessingMode.PROCESS_ONLY.name());
        assertEquals("AGGREGATE_ONLY", KBProcessingMode.AGGREGATE_ONLY.name());
    }

    @Test
    public void testParamsWithAllModes() {
        // Test FULL mode
        KBOrchestratorParams fullParams = new KBOrchestratorParams();
        fullParams.setSourceName("source_test");
        fullParams.setInputFile("test.json");
        fullParams.setDateTime("2024-10-10T12:00:00Z");
        fullParams.setOutputPath("./kb");
        fullParams.setProcessingMode(KBProcessingMode.FULL);
        
        assertEquals("source_test", fullParams.getSourceName());
        assertEquals("test.json", fullParams.getInputFile());
        assertEquals("2024-10-10T12:00:00Z", fullParams.getDateTime());
        assertEquals("./kb", fullParams.getOutputPath());
        assertEquals(KBProcessingMode.FULL, fullParams.getProcessingMode());
        
        // Test PROCESS_ONLY mode
        KBOrchestratorParams processParams = new KBOrchestratorParams();
        processParams.setSourceName("source_test");
        processParams.setInputFile("test.json");
        processParams.setDateTime("2024-10-10T12:00:00Z");
        processParams.setOutputPath("./kb");
        processParams.setProcessingMode(KBProcessingMode.PROCESS_ONLY);
        
        assertEquals(KBProcessingMode.PROCESS_ONLY, processParams.getProcessingMode());
        
        // Test AGGREGATE_ONLY mode
        KBOrchestratorParams aggregateParams = new KBOrchestratorParams();
        aggregateParams.setSourceName("source_test");
        aggregateParams.setOutputPath("./kb");
        aggregateParams.setProcessingMode(KBProcessingMode.AGGREGATE_ONLY);
        
        assertEquals(KBProcessingMode.AGGREGATE_ONLY, aggregateParams.getProcessingMode());
        assertNull(aggregateParams.getInputFile(), 
                "AGGREGATE_ONLY mode should not require input file");
        assertNull(aggregateParams.getDateTime(), 
                "AGGREGATE_ONLY mode should not require date time");
    }
}

