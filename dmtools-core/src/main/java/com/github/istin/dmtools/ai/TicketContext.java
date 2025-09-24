package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.prompt.input.TicketBasedPrompt;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TicketContext implements ToText {

    private static final Logger logger = LogManager.getLogger(TicketContext.class);

    @Getter
    private List<IComment> comments;

    public static interface OnTicketDetailsRequest {
        ITicket getTicketDetails(String key) throws IOException;
    }

    private final TrackerClient<? extends ITicket> trackerClient;
    @Getter
    private final ITicket ticket;
    @Setter
    @Getter
    private OnTicketDetailsRequest onTicketDetailsRequest;

    @Setter
    @Getter
    private List<ITicket> extraTickets = new ArrayList<>();

    public TicketContext(TrackerClient<? extends ITicket> trackerClient, ITicket ticket) {
        this.trackerClient = trackerClient;
        this.ticket = ticket;
    }

    public void prepareContext() throws IOException {
        prepareContext(false);
    }
    public void prepareContext(boolean withComments) throws IOException {

        // Step 1: Extract JIRA IDs from ticket text
        String ticketText = ticket.toText();

        Set<String> keys = IssuesIDsParser.extractAllJiraIDs(ticketText);

        // Step 2: Fetch extra tickets (parallel processing for performance)
        extraTickets = new ArrayList<>();
        if (!keys.isEmpty()) {

            // Filter out self-references and prepare list of keys to fetch
            List<String> keysToFetch = keys.stream()
                .filter(key -> !key.equalsIgnoreCase(ticket.getKey()))
                .collect(Collectors.toList());
            
            if (!keysToFetch.isEmpty()) {
                // Create executor service for parallel processing
                // Use a reasonable number of threads to avoid overwhelming the API
                int threadPoolSize = Math.min(keysToFetch.size(), 5); // Max 5 concurrent requests
                ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);

                // Create CompletableFutures for parallel ticket fetching
                List<CompletableFuture<ITicket>> futures = keysToFetch.stream()
                    .map(key -> CompletableFuture.supplyAsync(() -> fetchSingleTicket(key), executorService))
                    .collect(Collectors.toList());
                
                // Wait for all tickets to be fetched and collect results
                try {
                    List<ITicket> fetchedTickets = futures.stream()
                        .map(CompletableFuture::join)
                        .filter(t -> t != null) // Filter out failed fetches
                        .collect(Collectors.toList());
                    
                    extraTickets.addAll(fetchedTickets);

                } catch (Exception e) {
                    logger.error("TIMING: Error during parallel ticket fetching: {}", e.getMessage());
                } finally {
                    // Shutdown executor service
                    executorService.shutdown();
                    try {
                        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                            executorService.shutdownNow();
                            logger.warn("TIMING: Executor service forced shutdown after timeout");
                        }
                    } catch (InterruptedException e) {
                        executorService.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
        }
        
        // Step 3: Fetch comments if requested
        if (withComments) {
            @SuppressWarnings("unchecked")
            List<IComment> fetchedComments = (List<IComment>) trackerClient.getComments(ticket.getKey(), ticket);
            comments = fetchedComments;
        }
        
    }

    /**
     * Helper method to fetch a single ticket with proper error handling and timing
     * This method is designed to be called from parallel processing contexts
     * 
     * @param key The ticket key to fetch
     * @return The fetched ticket or null if fetching failed
     */
    private ITicket fetchSingleTicket(String key) {
        try {
            ITicket fetchedTicket = null;
            
            // Try custom details request first if available
            if (onTicketDetailsRequest != null) {
                try {
                    fetchedTicket = onTicketDetailsRequest.getTicketDetails(key);
                } catch (Exception e) {
                    logger.debug("TIMING: Custom ticket details request failed for {}: {}", key, e.getMessage());
                }
            }
            
            // Fallback to standard performTicket if custom request failed or unavailable
            if (fetchedTicket == null) {
                fetchedTicket = trackerClient.performTicket(key, trackerClient.getExtendedQueryFields());
            }
            

            return fetchedTicket;
            
        } catch (AtlassianRestClient.RestClientException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toText() throws IOException {
        StringBuilder text = new StringBuilder(new TicketBasedPrompt.TicketWrapper(trackerClient.getBasePath(), ticket).toText());
        if (comments != null) {
            text.append("<previous_discussion>").append("\n");
            for (IComment comment : comments) {
                text.append("\n");
                if (comment instanceof ToText) {
                    text.append(((ToText) comment).toText());
                }
            }
            text.append("</previous_discussion>").append("\n");
        }
        for (ITicket extraTicket : extraTickets) {
            text.append("\n").append(new TicketBasedPrompt.TicketWrapper(trackerClient.getBasePath(), extraTicket).toText());
        }
        return text.toString();
    }

}
