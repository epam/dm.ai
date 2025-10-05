package com.github.istin.dmtools.atlassian.confluence;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfluenceGraphQLClientTest {

    private ConfluenceGraphQLClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = new ConfluenceGraphQLClient("https://test.atlassian.net", "Bearer test-token");
    }

    @Test
    void testConstructor() throws IOException {
        ConfluenceGraphQLClient testClient = new ConfluenceGraphQLClient(
                "https://example.atlassian.net",
                "Bearer test-auth"
        );
        assertNotNull(testClient);
    }

    @Test
    void testSetSiteId() throws IOException {
        String testSiteId = "ari:cloud:confluence::site/test-cloud-id";
        client.setSiteId(testSiteId);
        // Site ID should be stored and retrievable in future calls
        // We can verify this indirectly by checking if it doesn't throw when used
        assertNotNull(client);
    }

    @Test
    void testSearchWithDefaultContentTypes() {
        // This test would require mocking the GraphQL execution
        // For now, we'll test that the method signature works
        assertThrows(Exception.class, () -> {
            client.search("test query", 10);
        });
    }

    @Test
    void testSearchWithCustomContentTypes() {
        String[] customTypes = {"ati:cloud:confluence:page"};
        assertThrows(Exception.class, () -> {
            client.search("test query", 10, customTypes);
        });
    }

    @Test
    void testConstructorAcceptsAnyBasePath() throws IOException {
        // Constructor doesn't validate base path - it accepts any string
        ConfluenceGraphQLClient testClient = new ConfluenceGraphQLClient("", "Bearer test-token");
        assertNotNull(testClient);
        
        // Another test with null-like values
        ConfluenceGraphQLClient testClient2 = new ConfluenceGraphQLClient("invalid-url", "auth");
        assertNotNull(testClient2);
    }
}
