package com.github.istin.dmtools.broadcom.rally;

import com.github.istin.dmtools.broadcom.rally.model.RallyFields;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Basic implementation of RallyClient with property-based configuration and singleton pattern.
 *
 * Configuration via environment variables or config.properties:
 * - RALLY_PATH: Rally server base path
 * - RALLY_TOKEN: API token for authentication
 */
public class BasicRallyClient extends RallyClient {

    private static final Logger logger = LogManager.getLogger(BasicRallyClient.class);

    public static final String RALLY_PATH;
    public static final String RALLY_TOKEN;

    static {
        PropertyReader propertyReader = new PropertyReader();
        RALLY_PATH = propertyReader.getRallyPath();
        RALLY_TOKEN = propertyReader.getRallyToken();
    }

    private static BasicRallyClient instance;

    /**
     * Private constructor for singleton pattern.
     */
    private BasicRallyClient(String basePath, String token) throws IOException {
        super(basePath, token);
        setLogEnabled(true);
        setClearCache(true);
        setCacheGetRequestsEnabled(true);
    }

    /**
     * Get singleton instance of BasicRallyClient.
     * Reads configuration from PropertyReader (environment variables or config.properties).
     *
     * @return singleton instance or null if Rally is not configured
     * @throws IOException if initialization fails
     */
    public static synchronized BasicRallyClient getInstance() throws IOException {
        if (instance == null) {
            if (RALLY_PATH == null || RALLY_PATH.isEmpty() ||
                RALLY_TOKEN == null || RALLY_TOKEN.isEmpty()) {
                logger.debug("Rally configuration not found. Set RALLY_PATH and RALLY_TOKEN.");
                return null;
            }

            logger.info("Initializing BasicRallyClient for path: {}", RALLY_PATH);

            instance = new BasicRallyClient(RALLY_PATH, RALLY_TOKEN);
        }
        return instance;
    }

    /**
     * Reset singleton instance (useful for testing).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * Create a new instance with custom configuration (for testing or multi-org scenarios).
     */
    public static BasicRallyClient createInstance(String basePath, String token) throws IOException {
        return new BasicRallyClient(basePath, token);
    }

    @Override
    public TrackerClient.TextType getTextType() {
        return TrackerClient.TextType.HTML;
    }

    @Override
    public void deleteCommentIfExists(String ticketKey, String comment) throws IOException {
        // Implement comment deletion if needed
    }

    @Override
    public String[] getDefaultQueryFields() {
        return RallyFields.DEFAULT;
    }

    @Override
    public String getTextFieldsOnly(ITicket ticket) {
        try {
            return ticket.getTicketTitle() + "\n" + ticket.getTicketDescription();
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public List<? extends ITicket> getTestCases(ITicket ticket) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public String buildUrlToSearch(String query) {
        return getBasePath() + "/#/?keywords=" + query;
    }

    @Override
    public String createTicketInProject(String project, String issueType, String summary, String description, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException("Creation not supported in basic Rally client yet");
    }

    @Override
    public String updateTicket(String key, FieldsInitializer fieldsInitializer) throws IOException {
        throw new UnsupportedOperationException("Update not supported in basic Rally client yet");
    }
}



