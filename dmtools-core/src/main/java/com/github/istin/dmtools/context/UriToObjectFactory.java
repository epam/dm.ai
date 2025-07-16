package com.github.istin.dmtools.context;

import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.di.SourceCodeFactory;
import com.github.istin.dmtools.figma.BasicFigmaClient;
import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.github.BasicGithub;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating UriToObject instances for URI processing.
 * Supports both standalone and server-managed modes.
 */
public class UriToObjectFactory {

    // Injected instances for server-managed mode
    private final TrackerClient<? extends ITicket> trackerClient;
    private final Confluence confluence;
    private final SourceCodeFactory sourceCodeFactory;

    /**
     * Constructor for dependency injection (server-managed mode)
     */
    @Inject
    public UriToObjectFactory(TrackerClient<? extends ITicket> trackerClient, 
                             Confluence confluence, 
                             SourceCodeFactory sourceCodeFactory) {
        this.trackerClient = trackerClient;
        this.confluence = confluence;
        this.sourceCodeFactory = sourceCodeFactory;
    }

    /**
     * Default constructor for standalone mode (backward compatibility)
     */
    public UriToObjectFactory() {
        this.trackerClient = null;
        this.confluence = null;
        this.sourceCodeFactory = new SourceCodeFactory();
    }

    /**
     * Creates a list of UriToObject instances for processing URIs.
     * Uses injected instances in server-managed mode, falls back to Basic* instances in standalone mode.
     */
    public List<? extends UriToObject> createUriProcessingSources(SourceCodeConfig... sourceCodeConfigs) throws IOException {
        List<UriToObject> result = new ArrayList<>();

        // Add source code instances
        List<SourceCode> sourceCodesOrDefault = sourceCodeFactory.createSourceCodesOrDefault(sourceCodeConfigs);
        for (SourceCode sourceCode : sourceCodesOrDefault) {
            if (sourceCode instanceof UriToObject) {
                result.add((UriToObject) sourceCode);
            }
        }

        // Add tracker client (Jira)
        TrackerClient<? extends ITicket> jiraClient = getTrackerClient();
        if (jiraClient != null && jiraClient instanceof UriToObject) {
            result.add((UriToObject) jiraClient);
        }

        // Add Confluence
        Confluence confluenceClient = getConfluence();
        if (confluenceClient != null) {
            result.add(confluenceClient);
        }

        // Add Figma (still using Basic instance as it's not yet in server-managed)
        try {
            FigmaClient figmaClient = BasicFigmaClient.getInstance();
            if (figmaClient != null) {
                result.add(figmaClient);
            }
        } catch (Exception e) {
            // Ignore if not available
        }

        // Add GitHub (still using Basic instance as it's not yet in server-managed)
        try {
            SourceCode gitHub = BasicGithub.getInstance();
            if (gitHub != null) {
                result.add((UriToObject) gitHub);
            }
        } catch (Exception e) {
            // Ignore if not available
        }

        return result;
    }

    /**
     * Gets the TrackerClient instance.
     * Uses injected instance in server-managed mode, falls back to BasicJiraClient in standalone mode.
     */
    private TrackerClient<? extends ITicket> getTrackerClient() {
        if (trackerClient != null) {
            // Server-managed mode: use injected instance
            return trackerClient;
        } else {
            // Standalone mode: use Basic instance
            try {
                return BasicJiraClient.getInstance();
            } catch (IOException e) {
                System.err.println("Failed to get BasicJiraClient instance: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Gets the Confluence instance.
     * Uses injected instance in server-managed mode, falls back to BasicConfluence in standalone mode.
     */
    private Confluence getConfluence() {
        if (confluence != null) {
            // Server-managed mode: use injected instance
            return confluence;
        } else {
            // Standalone mode: use Basic instance
            try {
                return BasicConfluence.getInstance();
            } catch (IOException e) {
                System.err.println("Failed to get BasicConfluence instance: " + e.getMessage());
                return null;
            }
        }
    }
}