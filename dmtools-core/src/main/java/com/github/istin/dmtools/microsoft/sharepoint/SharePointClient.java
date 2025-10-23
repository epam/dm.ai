package com.github.istin.dmtools.microsoft.sharepoint;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.microsoft.common.networking.MicrosoftGraphRestClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Microsoft SharePoint REST client implementation.
 * Allows downloading files from SharePoint sharing URLs using Microsoft Graph API.
 */
public class SharePointClient extends MicrosoftGraphRestClient {
    private static final Logger logger = LogManager.getLogger(SharePointClient.class);
    
    /**
     * Creates a SharePoint client with configuration.
     * Uses the same authentication as Teams/OneDrive.
     * 
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID (use "common" for multi-tenant)
     * @param scopes OAuth 2.0 scopes (space-separated, should include Files.Read or Files.Read.All)
     * @param authMethod Authentication method: "browser", "device", or "refresh_token"
     * @param authPort Port for localhost redirect (browser flow)
     * @param tokenCachePath Path to token cache file
     * @param preConfiguredRefreshToken Optional pre-configured refresh token
     * @throws IOException if initialization fails
     */
    public SharePointClient(
            String clientId,
            String tenantId,
            String scopes,
            String authMethod,
            int authPort,
            String tokenCachePath,
            String preConfiguredRefreshToken) throws IOException {
        super(
                "https://graph.microsoft.com/v1.0",
                clientId,
                tenantId,
                scopes,
                authMethod,
                authPort,
                tokenCachePath,
                preConfiguredRefreshToken
        );
    }
    
    /**
     * Encodes a SharePoint sharing URL to base64url format for Graph API.
     * Format: u!{base64url-encoded-url}
     * 
     * @param sharingUrl The SharePoint sharing URL
     * @return Encoded sharing URL for Graph API
     */
    private String encodeSharingUrl(String sharingUrl) {
        // Encode URL to base64
        byte[] urlBytes = sharingUrl.getBytes(StandardCharsets.UTF_8);
        String base64String = Base64.getEncoder().encodeToString(urlBytes);
        
        // Convert to base64url format (replace / with _, + with -, remove padding =)
        String base64url = base64String
            .replace('/', '_')
            .replace('+', '-')
            .replaceAll("=+$", "");
        
        // Add required prefix
        return "u!" + base64url;
    }
    
    /**
     * Gets DriveItem information from a SharePoint sharing URL.
     * This converts the sharing URL to a driveItem that can be used to download the file.
     * 
     * @param sharingUrl The SharePoint sharing URL
     * @return JSON object with driveItem information
     * @throws IOException if request fails
     */
    @MCPTool(
        name = "sharepoint_get_drive_item",
        description = "Get DriveItem information from a SharePoint sharing URL. Returns metadata about the shared file including download URLs.",
        integration = "sharepoint",
        category = "storage"
    )
    public String getDriveItem(
            @MCPParam(name = "sharingUrl", description = "SharePoint sharing URL", required = true, 
                example = "https://company-my.sharepoint.com/:v:/p/user/EabcdefGHIJ...") String sharingUrl) throws IOException {
        
        logger.info("Getting driveItem for sharing URL: {}", sharingUrl);
        
        // Encode the sharing URL
        String encodedUrl = encodeSharingUrl(sharingUrl);
        
        // Get driveItem from sharing URL
        String url = path(String.format("/shares/%s/driveItem", encodedUrl));
        GenericRequest request = new GenericRequest(this, url);
        
        String response = execute(request);
        logger.info("Retrieved driveItem information");
        
        // Pretty-print the JSON response
        JSONObject json = new JSONObject(response);
        return json.toString(2);
    }
    
    /**
     * Downloads a file from a SharePoint sharing URL.
     * Supports both direct download URLs and drive/item ID-based downloads.
     * 
     * @param sharingUrl The SharePoint sharing URL
     * @param outputPath The local file path to save to
     * @return Status message with download info
     * @throws IOException if download fails
     */
    @MCPTool(
        name = "sharepoint_download_file",
        description = "Download a file from a SharePoint sharing URL to a local file path. Works with OneDrive and SharePoint sharing links.",
        integration = "sharepoint",
        category = "storage"
    )
    public String downloadFile(
            @MCPParam(name = "sharingUrl", description = "SharePoint sharing URL", required = true,
                example = "https://company-my.sharepoint.com/:v:/p/user/EabcdefGHIJ...") String sharingUrl,
            @MCPParam(name = "outputPath", description = "Local file path to save to", required = true,
                example = "/tmp/recording.mp4") String outputPath) throws IOException {
        
        logger.info("Downloading file from SharePoint: {}", sharingUrl);
        
        try {
            // Step 1: Get driveItem information
            String encodedUrl = encodeSharingUrl(sharingUrl);
            String driveItemUrl = path(String.format("/shares/%s/driveItem", encodedUrl));
            GenericRequest driveItemRequest = new GenericRequest(this, driveItemUrl);
            String driveItemResponse = execute(driveItemRequest);
            JSONObject driveItem = new JSONObject(driveItemResponse);
            
            String filename = driveItem.optString("name", "downloaded_file");
            long fileSize = driveItem.optLong("size", 0);
            
            logger.info("File info: name={}, size={} bytes", filename, fileSize);
            
            // Step 2: Get download URL
            String downloadUrl = driveItem.optString("@microsoft.graph.downloadUrl", null);
            
            java.io.File outputFile = new java.io.File(outputPath);
            java.io.File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                // Method 1: Use direct download URL (doesn't require auth)
                logger.info("Using direct download URL");
                GenericRequest downloadRequest = new GenericRequest(this, downloadUrl);
                java.io.File downloadedFile = RestClient.Impl.downloadFile(this, downloadRequest, outputFile);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("outputPath", downloadedFile.getAbsolutePath());
                result.put("fileSize", downloadedFile.length());
                result.put("fileName", filename);
                result.put("sharingUrl", sharingUrl);
                result.put("method", "directUrl");
                
                logger.info("Downloaded {} bytes to {}", downloadedFile.length(), downloadedFile.getAbsolutePath());
                return result.toString(2);
                
            } else {
                // Method 2: Use drive and item IDs
                logger.info("Using drive/item ID method");
                JSONObject parentRef = driveItem.optJSONObject("parentReference");
                if (parentRef == null) {
                    throw new IOException("No parentReference found in driveItem");
                }
                
                String driveId = parentRef.optString("driveId");
                String itemId = driveItem.optString("id");
                
                if (driveId == null || driveId.isEmpty() || itemId == null || itemId.isEmpty()) {
                    throw new IOException("Missing driveId or itemId");
                }
                
                String contentUrl = path(String.format("/drives/%s/items/%s/content", driveId, itemId));
                GenericRequest contentRequest = new GenericRequest(this, contentUrl);
                java.io.File downloadedFile = RestClient.Impl.downloadFile(this, contentRequest, outputFile);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("outputPath", downloadedFile.getAbsolutePath());
                result.put("fileSize", downloadedFile.length());
                result.put("fileName", filename);
                result.put("sharingUrl", sharingUrl);
                result.put("method", "driveItemContent");
                
                logger.info("Downloaded {} bytes to {}", downloadedFile.length(), downloadedFile.getAbsolutePath());
                return result.toString(2);
            }
            
        } catch (Exception e) {
            logger.error("Failed to download file from SharePoint: {}", e.getMessage(), e);
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("sharingUrl", sharingUrl);
            error.put("outputPath", outputPath);
            return error.toString(2);
        }
    }
}



