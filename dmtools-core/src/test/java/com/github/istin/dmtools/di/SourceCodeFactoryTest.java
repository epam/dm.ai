package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.bitbucket.BasicBitbucket;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.gitlab.BasicGitLab;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceCodeFactoryTest {

    private SourceCodeFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SourceCodeFactory();
    }

    @Test
    void testCreateSourceCodes_Github() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITHUB)
                .workspaceName("test-owner")
                .repoName("test-repo")
                .auth("test-token")
                .path("https://api.github.com")
                .build();

        List<SourceCode> result = factory.createSourceCodes(config);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof BasicGithub);
    }

    @Test
    void testCreateSourceCodes_Bitbucket() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.BITBUCKET)
                .workspaceName("test-owner")
                .repoName("test-repo")
                .auth("test-token")
                .path("https://api.bitbucket.org/2.0")
                .build();

        List<SourceCode> result = factory.createSourceCodes(config);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof BasicBitbucket);
    }

    @Test
    void testCreateSourceCodes_GitLab() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITLAB)
                .workspaceName("test-owner")
                .repoName("test-repo")
                .auth("test-token")
                .path("https://gitlab.com/api/v4")
                .build();

        List<SourceCode> result = factory.createSourceCodes(config);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof BasicGitLab);
    }

    @Test
    void testCreateSourceCodes_MultipleConfigs() throws IOException {
        SourceCodeConfig config1 = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITHUB)
                .workspaceName("test-owner")
                .repoName("test-repo")
                .auth("test-token")
                .path("https://api.github.com")
                .build();

        SourceCodeConfig config2 = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITLAB)
                .workspaceName("test-owner2")
                .repoName("test-repo2")
                .auth("test-token2")
                .path("https://gitlab.com/api/v4")
                .build();

        List<SourceCode> result = factory.createSourceCodes(config1, config2);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof BasicGithub);
        assertTrue(result.get(1) instanceof BasicGitLab);
    }

    @Test
    void testCreateSourceCodesOrDefault_WithNullConfig() throws IOException {
        List<SourceCode> result = factory.createSourceCodesOrDefault((SourceCodeConfig[]) null);
        
        assertNotNull(result);
        // Result depends on configuration, could be empty or have configured sources
    }

    @Test
    void testCreateSourceCodesOrDefault_WithConfigs() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .type(SourceCodeConfig.Type.GITHUB)
                .workspaceName("test-owner")
                .repoName("test-repo")
                .auth("test-token")
                .path("https://api.github.com")
                .build();

        List<SourceCode> result = factory.createSourceCodesOrDefault(config);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof BasicGithub);
    }

    @Test
    void testCreateSourceCodes_EmptyArray() throws IOException {
        List<SourceCode> result = factory.createSourceCodes(new SourceCodeConfig[0]);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
