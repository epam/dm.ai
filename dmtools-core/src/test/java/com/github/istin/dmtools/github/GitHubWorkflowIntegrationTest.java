package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * Integration test for GitHub workflow summary extraction.
 * This test uses real GitHub API calls to verify the workflow summary functionality.
 */
public class GitHubWorkflowIntegrationTest {

    @Test
    @Disabled("Enable for manual testing with real GitHub credentials")
    public void testWorkflowSummaryExtraction() throws Exception {
        // Test with the actual workflow run: https://github.com/IstiN/dmtools/actions/runs/16858480819
        String owner = "IstiN";
        String repo = "dmtools";
        Long runId = 16858480819L;
        
        // Create GitHub client - you need to set GITHUB_TOKEN environment variable
        String token = System.getenv("GITHUB_TOKEN");
        assertNotNull(token, "GITHUB_TOKEN environment variable must be set for integration test");
        
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth(token)
                .workspaceName("IstiN")
                .repoName("dmtools")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        BasicGithub github = new BasicGithub(config);
        
        // Test the workflow summary extraction
        String summary = github.callHookAndWaitResponse(
            "https://github.com/IstiN/dmtools/actions/workflows/aider-gemini-assist.yml",
            "Test request"
        );
        
        assertNotNull(summary, "Workflow summary should not be null");
        assertTrue(summary.contains("Aider Analysis Response"), 
                   "Summary should contain the actual Aider response content");
        
        System.out.println("=== EXTRACTED WORKFLOW SUMMARY ===");
        System.out.println(summary);
        System.out.println("=== END SUMMARY ===");
    }
    
    @Test
    @Disabled("Enable for manual testing with real GitHub credentials")
    public void testSpecificWorkflowRunSummary() throws Exception {
        // Test with specific workflow run ID: https://github.com/IstiN/dmtools/actions/runs/16858480819
        String owner = "IstiN";
        String repo = "dmtools";
        Long runId = 16859458667L;
        
        String token = System.getenv("GITHUB_TOKEN");
        assertNotNull(token, "GITHUB_TOKEN environment variable must be set for integration test");
        
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth(token)
                .workspaceName("IstiN")
                .repoName("dmtools")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        BasicGithub github = new BasicGithub(config);

        String summary = github.getWorkflowSummary(owner, repo, runId);
        
        assertNotNull(summary, "Workflow summary should not be null");
        
        System.out.println("=== DIRECT WORKFLOW SUMMARY TEST ===");
        System.out.println(summary);
        System.out.println("=== END SUMMARY ===");
        
        // This is what we expect to see from the response:
        // "[SD API] ***-server template (Think MVP all time)"
        // "Existing API Infrastructure:"
        // And other detailed analysis content
        // Let's check if our new artifact extraction is working
        boolean hasAnalysisResponse = summary.contains("[SD API]") || 
                                    summary.contains("Existing API Infrastructure") ||
                                    summary.contains("Analysis Response") ||
                                    summary.contains("response-") ||
                                    summary.contains("Download URL");
        
        if (!hasAnalysisResponse) {
            System.out.println("⚠️  Analysis response content not found in summary");
            System.out.println("This indicates the artifact extraction needs improvement");
        } else {
            System.out.println("✅ Found analysis response content in summary!");
        }
    }
    
    @Test
    @Disabled("Enable for manual testing with real GitHub credentials")
    public void testArtifactExtraction() throws Exception {
        // Test the artifact extraction specifically
        String owner = "IstiN";
        String repo = "dmtools";
        Long runId = 16854284909L; // Use the new run ID with generic artifacts
        
        String token = System.getenv("GITHUB_TOKEN");
        assertNotNull(token, "GITHUB_TOKEN environment variable must be set for integration test");
        
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth(token)
                .workspaceName("IstiN")
                .repoName("dmtools")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        BasicGithub github = new BasicGithub(config);
        
        // Use reflection to test private method
        java.lang.reflect.Method method = GitHub.class.getDeclaredMethod("getAnalysisResponseFromArtifacts", String.class, String.class, Long.class);
        method.setAccessible(true);
        
        String artifactResponse = (String) method.invoke(github, owner, repo, runId);
        
        System.out.println("=== ARTIFACT EXTRACTION TEST ===");
        if (artifactResponse != null) {
            System.out.println("Artifact response length: " + artifactResponse.length());
            System.out.println("Response content preview (first 500 chars):");
            System.out.println(artifactResponse.substring(0, Math.min(500, artifactResponse.length())));
            
            // Check if we got actual response content (not just download URLs)
            boolean hasActualContent = artifactResponse.contains("# Analysis Response") || 
                                     artifactResponse.contains("[SD API]") ||
                                     artifactResponse.contains("Existing API Infrastructure") ||
                                     artifactResponse.length() > 1000; // Actual content should be substantial
            
            boolean hasDownloadInfo = artifactResponse.contains("Download URL") ||
                                    artifactResponse.contains("extraction failed");
            
            if (hasActualContent) {
                System.out.println("✅ Successfully extracted actual analysis response content!");
                System.out.println("Response contains actual analysis data, not just download info");
                
                // Check for duplicate content (which was fixed in the workflow)
                String[] lines = artifactResponse.split("\n");
                long analysisResponseHeaderCount = Arrays.stream(lines)
                    .filter(line -> line.trim().equals("# Analysis Response"))
                    .count();
                
                if (analysisResponseHeaderCount <= 1) {
                    System.out.println("✅ No duplicate content detected in response");
                } else {
                    System.out.println("⚠️  Found " + analysisResponseHeaderCount + " Analysis Response headers - possible duplication");
                }
            } else if (hasDownloadInfo) {
                System.out.println("⚠️  Got download information but no actual content");
                System.out.println("This might indicate ZIP extraction issues or artifact format changes");
            } else {
                System.out.println("⚠️  No analysis response artifact found");
            }
        } else {
            System.out.println("❌ No artifact response retrieved");
        }
        System.out.println("=== END ARTIFACT TEST ===");
    }
}
