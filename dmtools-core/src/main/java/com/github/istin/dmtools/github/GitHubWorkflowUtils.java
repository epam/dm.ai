package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for GitHub workflow operations using URLConnection (proven to work in Google Cloud).
 * 
 * After testing multiple HTTP clients, URLConnection was the only approach that works reliably
 * in Google Cloud environments.
 */
public class GitHubWorkflowUtils {
    
    private static final Logger logger = LogManager.getLogger(GitHubWorkflowUtils.class);
    
    /**
     * Triggers a GitHub workflow using URLConnection with retry logic.
     * This approach has been tested and proven to work in Google Cloud.
     * 
     * @param github The GitHub client instance
     * @param owner Repository owner
     * @param repo Repository name  
     * @param workflowId Workflow ID or filename
     * @param request Request payload
     * @throws IOException if the workflow trigger fails after all retries
     */
    public static void triggerWorkflow(GitHub github, String owner, String repo, String workflowId, String request) throws IOException {
        triggerWorkflowWithRetry(github, owner, repo, workflowId, request, 0);
    }
    
    /**
     * Internal method that implements URLConnection-based workflow triggering with retry logic.
     */
    private static void triggerWorkflowWithRetry(GitHub github, String owner, String repo, String workflowId, String request, int retryCount) throws IOException {
        // Prepare request data
        String processedRequest = github.processLargePayload(request);
        JSONObject requestBody = new JSONObject();
        requestBody.put("ref", "main");
        requestBody.put("inputs", new JSONObject().put("user_request", processedRequest));
        String requestBodyStr = requestBody.toString();
        String triggerUrl = String.format("https://api.github.com/repos/%s/%s/actions/workflows/%s/dispatches", owner, repo, workflowId);
        
        logger.info("🚀 URLConnection: Triggering workflow {}/{}/{}", owner, repo, workflowId);
        logger.info("📊 Request size: {} characters", requestBodyStr.length());
        
        try {
            URL url = new URL(triggerUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            try {
                // Configure connection
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000); // 30 seconds
                connection.setReadTimeout(github.getTimeout() * 1000); // Convert to milliseconds
                
                // Set headers
                connection.setRequestProperty("Authorization", "Bearer " + github.getAuthorization());
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "DMTools");
                
                logger.info("🌐 URLConnection: Sending request to {}", triggerUrl);
                
                // Write request body
                try (OutputStream outputStream = connection.getOutputStream();
                     OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    writer.write(requestBodyStr);
                    writer.flush();
                }
                
                // Get response
                int responseCode = connection.getResponseCode();
                logger.info("📨 URLConnection: Response {}", responseCode);
                
                if (responseCode < 200 || responseCode >= 300) {
                    // Read error response if available
                    String errorResponse = "";
                    try (InputStream errorStream = connection.getErrorStream()) {
                        if (errorStream != null) {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                errorResponse = sb.toString();
                            }
                        }
                    }
                    throw new IOException("URLConnection HTTP " + responseCode + ": " + errorResponse);
                }
                
                logger.info("✅ URLConnection: Workflow triggered successfully!");
                
            } finally {
                connection.disconnect();
            }
            
        } catch (IOException e) {
            // Enhanced error handling for connection issues with retry logic
            String errorMessage = e.getMessage();
            boolean isConnectionError = errorMessage != null && (
                errorMessage.toLowerCase().contains("broken pipe") ||
                errorMessage.toLowerCase().contains("connection reset") ||
                errorMessage.toLowerCase().contains("connection refused") ||
                errorMessage.toLowerCase().contains("timeout") ||
                errorMessage.toLowerCase().contains("network is unreachable") ||
                errorMessage.toLowerCase().contains("unexpected end of stream") ||
                errorMessage.toLowerCase().contains("end of stream") ||
                errorMessage.toLowerCase().contains("remote host terminated the handshake") ||
                errorMessage.toLowerCase().contains("handshake") ||
                errorMessage.toLowerCase().contains("ssl") ||
                errorMessage.toLowerCase().contains("closed")
            );
            
            // Maximum of 3 attempts (2 retries)
            final int MAX_RETRIES = 2;
            
            if (isConnectionError && retryCount < MAX_RETRIES) {
                logger.info("🔄 URLConnection: Retrying after connection error: {} (Retry {}/{})", 
                    e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                try {
                    // Exponential backoff: 200ms, 400ms, 800ms
                    long waitTime = 200L * (long) Math.pow(2, retryCount);
                    logger.debug("⏳ Waiting {}ms before retry", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("URLConnection request interrupted during retry", interruptedException);
                }
                // Retry the request
                triggerWorkflowWithRetry(github, owner, repo, workflowId, request, retryCount + 1);
                return; // Successfully completed after retry
            } else if (isConnectionError) {
                logger.error("💥 URLConnection: Max retries ({}) exceeded. Final error: {}", MAX_RETRIES, errorMessage);
            }
            
            logger.error("❌ URLConnection: Failed to trigger workflow: {}", errorMessage);
            
            // Check for specific "inputs are too large" error
            if (errorMessage != null && errorMessage.toLowerCase().contains("inputs are too large")) {
                logger.error("📏 GitHub workflow inputs exceed size limit. Original request size: {} characters, processed size: {} characters", 
                    request.length(), processedRequest.length());
                throw new IOException("GitHub workflow inputs are too large even after compression. " +
                    "Consider reducing the input data size or implementing request chunking. " +
                    "Original size: " + request.length() + " chars, processed: " + processedRequest.length() + " chars", e);
            }
            
            // Provide specific guidance for common failure reasons
            String errorDetails = getWorkflowTriggerErrorDetails(e, owner, repo, workflowId);
            throw new IOException("URLConnection workflow trigger failed: " + errorMessage + ". " + errorDetails, e);
        } catch (Exception e) {
            logger.error("💥 URLConnection: Unexpected error during workflow trigger: {}", e.getMessage(), e);
            String errorDetails = getWorkflowTriggerErrorDetails(e, owner, repo, workflowId);
            throw new IOException("URLConnection workflow trigger failed with unexpected error: " + e.getMessage() + ". " + errorDetails, e);
        }
    }
    
    /**
     * Provides detailed error information for workflow trigger failures.
     * 
     * @param exception The exception that occurred
     * @param owner Repository owner
     * @param repo Repository name
     * @param workflowId Workflow ID
     * @return Detailed error information and troubleshooting steps
     */
    private static String getWorkflowTriggerErrorDetails(Exception exception, String owner, String repo, String workflowId) {
        StringBuilder details = new StringBuilder();
        
        details.append("Troubleshooting steps:\n");
        details.append("1. Verify the workflow file exists: https://github.com/").append(owner).append("/").append(repo).append("/actions/workflows/").append(workflowId).append("\n");
        details.append("2. Check that the workflow has 'workflow_dispatch' trigger configured\n");
        details.append("3. Ensure the GitHub token has 'actions:write' permission\n");
        details.append("4. Verify the repository allows workflow dispatches\n");
        
        String message = exception.getMessage();
        if (message != null) {
            if (message.contains("404")) {
                details.append("5. ERROR: Workflow not found - check the workflow ID/filename\n");
            } else if (message.contains("401") || message.contains("403")) {
                details.append("5. ERROR: Authentication/permission issue - check token permissions\n");
            } else if (message.contains("422")) {
                details.append("5. ERROR: Invalid request - check workflow_dispatch configuration\n");
            }
        }
        
        return details.toString();
    }

    /**
     * Downloads and extracts all logs for a GitHub Actions workflow run.
     * The GitHub API returns a 302 redirect to a ZIP archive containing
     * individual log files for each job and step.
     *
     * @param github   The GitHub client instance (for auth token)
     * @param owner    Repository owner
     * @param repo     Repository name
     * @param runId    Workflow run ID
     * @return Concatenated log content from all job log files in the ZIP
     * @throws IOException if download or extraction fails
     */
    public static String downloadWorkflowRunLogs(GitHub github, String owner, String repo, String runId) throws IOException {
        // Step 1: get the redirect URL (GitHub returns 302 → pre-signed S3 URL)
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/actions/runs/%s/logs", owner, repo, runId);
        logger.info("Fetching workflow run logs redirect URL: {}", apiUrl);

        String zipUrl = resolveLogsRedirect(github, apiUrl);
        logger.info("Downloading ZIP from: {}", zipUrl);

        // Step 2: download ZIP bytes (no auth needed — pre-signed URL)
        byte[] zipBytes = downloadBytes(zipUrl);
        logger.info("Downloaded ZIP: {} bytes", zipBytes.length);

        // Step 3: extract and concatenate all .txt log files from the ZIP
        StringBuilder result = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".txt")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) > 0) {
                        baos.write(buf, 0, len);
                    }
                    if (result.length() > 0) result.append("\n\n--- ").append(name).append(" ---\n\n");
                    else result.append("--- ").append(name).append(" ---\n\n");
                    result.append(baos.toString("UTF-8"));
                }
                zis.closeEntry();
            }
        }
        return result.toString();
    }

    private static String resolveLogsRedirect(GitHub github, String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Authorization", "Bearer " + github.getAuthorization());
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "DMTools");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try {
            int code = conn.getResponseCode();
            if (code == 302 || code == 301) {
                String location = conn.getHeaderField("Location");
                if (location == null) throw new IOException("No Location header in redirect response");
                return location;
            }
            throw new IOException("Expected redirect (301/302) but got HTTP " + code);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " downloading logs ZIP");
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) baos.write(buf, 0, len);
                return baos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}