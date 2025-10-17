package com.github.istin.dmtools.kb;

import com.github.istin.dmtools.common.kb.agent.KBOrchestrator;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.di.DaggerKnowledgeBaseComponent;
import com.github.istin.dmtools.di.KnowledgeBaseComponent;
import com.github.istin.dmtools.job.AbstractJob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Job for Knowledge Base processing.
 * Coordinates KB building: Analysis → Structure → Aggregation → Statistics
 */
public class KBProcessingJob extends AbstractJob<KBOrchestratorParams, KBResult> {
    
    private static final Logger logger = LogManager.getLogger(KBProcessingJob.class);
    
    private KBOrchestrator orchestrator;
    
    public KBProcessingJob() {
        super();
    }
    
    @Override
    protected void initializeStandalone() {
        logger.info("Initializing KBProcessingJob in standalone mode");
        KnowledgeBaseComponent component = DaggerKnowledgeBaseComponent.create();
        this.orchestrator = component.kbOrchestrator();
        logger.info("KBProcessingJob standalone initialization complete");
    }
    
    @Override
    protected KBResult runJobImpl(KBOrchestratorParams params) throws Exception {
        logger.info("Running KB Processing Job for source: {}", params.getSourceName());
        
        if (orchestrator == null) {
            throw new IllegalStateException("KBOrchestrator not initialized. Call initializeStandalone() first.");
        }
        
        return orchestrator.run(params);
    }
    
    @Override
    public String getName() {
        return "KBProcessing";
    }
}

