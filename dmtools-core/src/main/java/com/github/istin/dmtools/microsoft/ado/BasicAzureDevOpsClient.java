package com.github.istin.dmtools.microsoft.ado;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Basic implementation of AzureDevOpsClient with property-based configuration and singleton pattern.
 *
 * Configuration via environment variables or config.properties:
 * - ADO_ORGANIZATION: Azure DevOps organization name
 * - ADO_PROJECT: Default project name
 * - ADO_PAT_TOKEN: Personal Access Token for authentication
 */
public class BasicAzureDevOpsClient extends AzureDevOpsClient {

    private static final Logger logger = LogManager.getLogger(BasicAzureDevOpsClient.class);

    public static final String ORGANIZATION;
    public static final String PROJECT;
    public static final String PAT_TOKEN;
    public static final String BASE_PATH;

    static {
        PropertyReader propertyReader = new PropertyReader();
        ORGANIZATION = propertyReader.getAdoOrganization();
        PROJECT = propertyReader.getAdoProject();
        PAT_TOKEN = propertyReader.getAdoPatToken();
        BASE_PATH = propertyReader.getAdoBasePath();
    }

    private static BasicAzureDevOpsClient instance;

    /**
     * Private constructor for singleton pattern.
     */
    private BasicAzureDevOpsClient(String organization, String project, String patToken) throws IOException {
        super(organization, project, patToken);
    }

    /**
     * Get singleton instance of BasicAzureDevOpsClient.
     * Reads configuration from PropertyReader (environment variables or config.properties).
     *
     * @return singleton instance or null if ADO is not configured
     * @throws IOException if initialization fails
     */
    public static synchronized BasicAzureDevOpsClient getInstance() throws IOException {
        if (instance == null) {
            if (ORGANIZATION == null || ORGANIZATION.isEmpty() ||
                PROJECT == null || PROJECT.isEmpty() ||
                PAT_TOKEN == null || PAT_TOKEN.isEmpty()) {
                logger.debug("ADO configuration not found. Set ADO_ORGANIZATION, ADO_PROJECT, and ADO_PAT_TOKEN.");
                return null;
            }

            logger.info("Initializing BasicAzureDevOpsClient for organization: {}, project: {}",
                       ORGANIZATION, PROJECT);

            instance = new BasicAzureDevOpsClient(ORGANIZATION, PROJECT, PAT_TOKEN);
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
    public static BasicAzureDevOpsClient createInstance(String organization, String project, String patToken) throws IOException {
        return new BasicAzureDevOpsClient(organization, project, patToken);
    }
}

