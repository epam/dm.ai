package com.github.istin.dmtools.diagram;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.Diagram;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.di.DaggerDiagramsCreatorComponent;
import com.github.istin.dmtools.di.DiagramsCreatorComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import dagger.Component;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DiagramsCreator extends AbstractJob<DiagramsCreatorParams, List<DiagramsCreator.Result>> {

    private static final Logger logger = LogManager.getLogger(DiagramsCreator.class);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Result {
        private String key;
        private List<Diagram> diagrams;
    }

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Inject
    @Getter
    AI ai;

    @Inject
    IPromptTemplateReader promptTemplateReader;

    private static DiagramsCreatorComponent diagramsCreatorComponent;

    /**
     * Server-managed Dagger component that uses pre-resolved integrations
     */
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class})
    public interface ServerManagedDiagramsCreatorComponent {
        void inject(DiagramsCreator diagramsCreator);
    }

    public DiagramsCreator() {
        // Don't initialize here - will be done in initializeForMode based on execution mode
    }

    @Override
    protected void initializeStandalone() {
        logger.info("Initializing DiagramsCreator in STANDALONE mode using DiagramsCreatorComponent");
        
        // Use existing Dagger component for standalone mode
        if (diagramsCreatorComponent == null) {
            logger.info("Creating new DaggerDiagramsCreatorComponent for standalone mode");
            diagramsCreatorComponent = DaggerDiagramsCreatorComponent.create();
        }
        
        logger.info("Injecting dependencies using DiagramsCreatorComponent");
        diagramsCreatorComponent.inject(this);
        
        logger.info("DiagramsCreator standalone initialization completed - AI type: {}", 
                   (ai != null ? ai.getClass().getSimpleName() : "null"));
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        logger.info("Initializing DiagramsCreator in SERVER_MANAGED mode using ServerManagedIntegrationsModule");
        logger.info("Resolved integrations: {}", 
                   (resolvedIntegrations != null ? resolvedIntegrations.length() + " integrations" : "null"));
        
        // Create dynamic component with pre-resolved integrations
        try {
            logger.info("Creating ServerManagedIntegrationsModule with resolved credentials");
            ServerManagedIntegrationsModule module = new ServerManagedIntegrationsModule(resolvedIntegrations);
            
            logger.info("Building ServerManagedDiagramsCreatorComponent for DiagramsCreator");
            ServerManagedDiagramsCreatorComponent component = com.github.istin.dmtools.diagram.DaggerDiagramsCreator_ServerManagedDiagramsCreatorComponent.builder()
                    .serverManagedIntegrationsModule(module)
                    .build();
            
            logger.info("Injecting dependencies using ServerManagedDiagramsCreatorComponent");
            component.inject(this);
            
            logger.info("DiagramsCreator server-managed initialization completed - AI type: {}", 
                       (ai != null ? ai.getClass().getSimpleName() : "null"));
            
        } catch (Exception e) {
            logger.error("Failed to initialize DiagramsCreator in server-managed mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize DiagramsCreator in server-managed mode", e);
        }
    }

    @Override
    public List<Result> runJob(DiagramsCreatorParams params) throws Exception {
        return runJobImpl(params);
    }

    @Override
    protected List<Result> runJobImpl(DiagramsCreatorParams params) throws Exception {
        String roleSpecific = params.getRoleSpecific();
        String projectSpecific = params.getProjectSpecific();
        String storiesJql = params.getStoriesJql();
        String labelNameToMarkAsReviewed = params.getLabelNameToMarkAsReviewed();
        
        logger.info("Running DiagramsCreator with JQL: {}", storiesJql);
        
        // Use injected dependencies instead of manual instantiation
        JAssistant jAssistant = new JAssistant(trackerClient, null, ai, promptTemplateReader);
        DiagramsDrawer diagramsDrawer = new DiagramsDrawer();
        List<Result> resultItems = new ArrayList<>();
        
        trackerClient.searchAndPerform(ticket -> {
            TicketContext ticketContext = new TicketContext(trackerClient, ticket);
            ticketContext.prepareContext();

            List<Diagram> diagrams = jAssistant.createDiagrams(ticketContext, roleSpecific, projectSpecific);
            for (Diagram diagram : diagrams) {
                File screenshot = diagramsDrawer.draw(ticket.getTicketTitle() + "_" + diagram.getType(), diagram);
                trackerClient.attachFileToTicket(ticket.getTicketKey(), screenshot.getName(), null, screenshot);
                trackerClient.postCommentIfNotExists(ticket.getKey(), diagram.getType() + " \n " + diagram.getCode());
            }
            trackerClient.addLabelIfNotExists(ticket, labelNameToMarkAsReviewed);
            resultItems.add(new Result(ticket.getKey(), diagrams));
            return false;
        }, storiesJql, trackerClient.getExtendedQueryFields());
        
        logger.info("DiagramsCreator completed processing {} tickets", resultItems.size());
        return resultItems;
    }

}
