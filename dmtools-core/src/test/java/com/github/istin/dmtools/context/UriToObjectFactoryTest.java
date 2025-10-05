package com.github.istin.dmtools.context;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.di.SourceCodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UriToObjectFactoryTest {

    private TrackerClient<? extends ITicket> mockTrackerClient;
    private Confluence mockConfluence;
    private SourceCodeFactory mockSourceCodeFactory;

    @BeforeEach
    void setUp() {
        mockTrackerClient = Mockito.mock(TrackerClient.class);
        mockConfluence = Mockito.mock(Confluence.class);
        mockSourceCodeFactory = Mockito.mock(SourceCodeFactory.class);
    }

    @Test
    void testDefaultConstructor() {
        UriToObjectFactory factory = new UriToObjectFactory();
        assertNotNull(factory);
    }

    @Test
    void testConstructorWithDependencies() {
        UriToObjectFactory factory = new UriToObjectFactory(
            mockTrackerClient,
            mockConfluence,
            mockSourceCodeFactory
        );
        assertNotNull(factory);
    }

    @Test
    void testCreateUriProcessingSources_WithMocks() throws IOException {
        when(mockSourceCodeFactory.createSourceCodesOrDefault()).thenReturn(List.of());
        
        UriToObjectFactory factory = new UriToObjectFactory(
            mockTrackerClient,
            mockConfluence,
            mockSourceCodeFactory
        );

        List<? extends UriToObject> result = factory.createUriProcessingSources();
        
        assertNotNull(result);
        verify(mockSourceCodeFactory).createSourceCodesOrDefault();
    }

    @Test
    void testCreateUriProcessingSources_DefaultConstructor() throws IOException {
        UriToObjectFactory factory = new UriToObjectFactory();
        
        List<? extends UriToObject> result = factory.createUriProcessingSources();
        
        assertNotNull(result);
        // Result may be empty or contain instances depending on environment configuration
    }

    @Test
    void testCreateUriProcessingSources_WithSourceCodeConfig() throws IOException {
        when(mockSourceCodeFactory.createSourceCodesOrDefault(any(SourceCodeConfig.class))).thenReturn(List.of());
        
        UriToObjectFactory factory = new UriToObjectFactory(
            mockTrackerClient,
            mockConfluence,
            mockSourceCodeFactory
        );

        SourceCodeConfig config = new SourceCodeConfig();
        List<? extends UriToObject> result = factory.createUriProcessingSources(config);
        
        assertNotNull(result);
        verify(mockSourceCodeFactory).createSourceCodesOrDefault(any(SourceCodeConfig.class));
    }

    @Test
    void testCreateUriProcessingSources_AddConfluence() throws IOException {
        when(mockSourceCodeFactory.createSourceCodesOrDefault()).thenReturn(List.of());
        
        UriToObjectFactory factory = new UriToObjectFactory(
            mockTrackerClient,
            mockConfluence,
            mockSourceCodeFactory
        );

        List<? extends UriToObject> result = factory.createUriProcessingSources();
        
        assertNotNull(result);
        // Should include Confluence if provided
        assertTrue(result.contains(mockConfluence));
    }
}
