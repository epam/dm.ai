package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.atlassian.confluence.ContentUtils;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.ImageUtils;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.figma.model.FigmaComment;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import org.json.JSONObject;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.json.JSONObject;
import java.util.Set;
import java.util.ArrayList;
import org.json.JSONArray;
import com.github.istin.dmtools.figma.model.FigmaIcon;
import com.github.istin.dmtools.figma.model.FigmaIconsResult;
import com.github.istin.dmtools.figma.model.FigmaFileResponse;

public class FigmaClient extends AbstractRestClient implements ContentUtils.UrlToImageFile, UriToObject {

    private static final Logger logger = LogManager.getLogger(FigmaClient.class);

    public FigmaClient(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @Override
    public String path(String path) {
        if (path.endsWith("/")) {
            return getBasePath() + path;
        }
        return getBasePath() + "/" + path;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("X-Figma-Token", authorization)
                .header("Content-Type", "application/json");
    }

    @Override
    public int getTimeout() {
        return 300;
    }

    /**
     * Get image URL from Figma design URL for screen source access.
     * This method provides access to the underlying screen source content.
     *
     * @param url The Figma design URL containing node-id parameter
     * @return Image URL for the specified Figma node, or null if not found
     * @throws Exception if there's an error accessing Figma API
     */
    @MCPTool(
        name = "figma_get_screen_source",
        description = "Get screen source content by URL. Returns the image URL for the specified Figma design node.",
        integration = "figma",
        category = "content_access"
    )
    public String getImageOfSource(@MCPParam(name = "url", description = "Figma design URL with node-id parameter", required = true, example = "https://www.figma.com/file/abc123/Design?node-id=1%3A2") String url) throws Exception {
        GenericRequest getRequest = new GenericRequest(this, path("images/"+ parseFileId(url)));
        String nodeId = extractValueByParameter(url, "node-id");
        getRequest.param("ids", nodeId);
        try {
            String response = execute(getRequest);
            logger.info(response);
//            TODO to think about styling
//             getRequest = new GenericRequest(this, path("files/"+ parseFileId(url)+ "/styles"));
//            getRequest.param("ids", nodeId);
//            logger.info(getRequest.execute());
            return new JSONObject(response).getJSONObject("images").optString(nodeId.replaceAll("-", ":"));
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return null;
        }
    }

    public File downloadImage(String url) throws IOException {
        GenericRequest genericRequest = new GenericRequest(this, url);
        return Impl.downloadFile(this, genericRequest, getCachedFile(genericRequest));
    }

    @Override
    public File getCachedFile(GenericRequest genericRequest) {
        String url = genericRequest.url();
        String value = DigestUtils.md5Hex(url);
        String cacheFolderName = getCacheFolderName();
        
        // Ensure cache directory exists
        File cacheDir = new File(cacheFolderName);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        return new File(cacheFolderName + "/" + value + ((url.contains("images") ? ".png" : "")));
    }

    public String downloadImageAsBase64(String path) throws IOException {
        File imageFile = downloadImage(path);
        return ImageUtils.convertToBase64(imageFile, "png");
    }

    protected String parseFileId(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();

            // Extract the file ID from the path
            String[] pathSegments = path.split("/");
            return pathSegments[2]; // Assuming the file ID is always at index 2
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String extractValueByParameter(String url, String paramName) {
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();

            // Split the query parameters
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];

                    if (key.equals(paramName)) {
                        return value;
                    }
                }
            }
            throw new UnsupportedOperationException("Invalid url");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isValidImageUrl(String url) {
        boolean isDesignUrl = url.contains("design");
        boolean isFigmaLink = url.contains("figma") && url.contains("file") || isDesignUrl;
        try {
            if (isFigmaLink) {
                if (!isDesignUrl) {
                    parseFileId(url);
                }
                extractValueByParameter(url, "node-id");
            }
        } catch (Exception ignored) {
            return false;
        }

        return isFigmaLink;
    }

    /**
     * Download image file from Figma design URL.
     * This method converts a Figma design URL to a downloadable File object.
     *
     * @param href The Figma design URL to convert to file
     * @return File object containing the downloaded image, or null if conversion fails
     * @throws Exception if there's an error during download or URL conversion
     */
    @MCPTool(
        name = "figma_download_image_of_file",
        description = "Download image by URL as File type. Converts Figma design URL to downloadable image file.",
        integration = "figma",
        category = "file_management"
    )
    @Override
    public File convertUrlToFile(@MCPParam(name = "href", description = "Figma design URL to download as image file", required = true, example = "https://www.figma.com/file/abc123/Design?node-id=1%3A2") String href) throws Exception {
        href = href.replaceAll("&amp;", "&");
        String imageOfSource = getImageOfSource(href);
        if (imageOfSource == null || !imageOfSource.startsWith("http")) {
            return null;
        }
        return downloadImage(imageOfSource);
    }

    /**
     * Get structure of Figma design file by URL.
     * This method returns the complete file structure and content as a structured model.
     * If the URL contains a node-id parameter, it returns only that specific node's structure.
     *
     * @param href The Figma design URL to get structure for
     * @return FigmaFileResponse containing the file/node structure, or null if error occurs
     * @throws Exception if there's an error accessing Figma API
     */
    @MCPTool(
        name = "figma_get_file_structure",
        description = "Get JSON structure of Figma design file by URL. Returns the complete file structure and content as JSON. If URL contains node-id, returns only that specific node's structure.",
        integration = "figma",
        category = "content_access"
    )
    public FigmaFileResponse getFileStructure(@MCPParam(name = "href", description = "Figma design URL to get structure for", required = true, example = "https://www.figma.com/file/abc123/Design") String href) throws Exception {
        href = href.replaceAll("&amp;", "&");
        String fileId = parseFileId(href);
        
        // Check if URL contains a specific node-id
        String nodeId = null;
        try {
            nodeId = extractValueByParameter(href, "node-id");
            logger.info("Found node-id in URL: {}", nodeId);
        } catch (Exception e) {
            logger.info("No node-id found in URL, will get full file structure");
        }
        
        GenericRequest getRequest;
        
        if (nodeId != null && !nodeId.isEmpty()) {
            // Get specific node structure using /files/{file_key}/nodes endpoint
            getRequest = new GenericRequest(this, path("files/" + fileId + "/nodes"));
            getRequest.param("ids", nodeId);
            logger.info("Getting structure for specific node: {}", nodeId);
        } else {
            // Get full file structure using /files/{file_key} endpoint
            getRequest = new GenericRequest(this, path("files/" + fileId));
            // Add query parameters to reduce response size and avoid "Request too large" error
            getRequest.param("geometry", "paths");  // Exclude vector data to reduce size
            getRequest.param("depth", "2");         // Limit depth to reduce response size
            logger.info("Getting full file structure");
        }
        
        try {
            String response = execute(getRequest);
            if (nodeId != null && !nodeId.isEmpty()) {
                logger.info("Figma node structure retrieved for node: {} in file: {}", nodeId, fileId);
            } else {
                logger.info("Figma file structure retrieved for file: {}", fileId);
            }
            return new FigmaFileResponse(response);
        } catch (Exception e) {
            logger.error("Failed to get Figma structure for file {}: {}", fileId, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get all icons from Figma design file by URL.
     * This method finds and extracts all icon elements from the design structure.
     *
     * @param href The Figma design URL to get icons from
     * @return FigmaIconsResult containing all found icons with their metadata, or null if error occurs
     * @throws Exception if there's an error accessing Figma API
     */
    @MCPTool(
        name = "figma_get_icons",
        description = "Find and extract all exportable visual elements (vectors, shapes, graphics, text) from Figma design by URL. Focuses on actual visual elements to avoid complex component references.",
        integration = "figma",
        category = "content_access"
    )
    public FigmaIconsResult getIcons(@MCPParam(name = "href", description = "Figma design URL to extract visual elements from", required = true, example = "https://www.figma.com/file/abc123/Design") String href) throws Exception {
        try {
            // Get the file structure using the existing method (no duplication!)
            FigmaFileResponse fileResponse = getFileStructure(href);
            if (fileResponse == null) {
                logger.error("Failed to get file structure for visual element extraction");
                return null;
            }

            // Extract fileId for the result (getFileStructure already processed the URL)
            String cleanHref = href.replaceAll("&amp;", "&");
            String fileId = parseFileId(cleanHref);

            // Find all exportable visual elements (VECTOR, RECTANGLE, etc. - avoids complex component IDs)
            List<FigmaIcon> allImages = fileResponse.findAllComponents();
            logger.info("Found {} exportable visual elements in Figma design", allImages.size());

            // Deduplicate by node ID (same image used multiple times should appear once)
            Map<String, FigmaIcon> uniqueImages = new LinkedHashMap<>();
            for (FigmaIcon image : allImages) {
                String nodeId = image.getId();
                if (nodeId != null && !uniqueImages.containsKey(nodeId)) {
                    uniqueImages.put(nodeId, image);
                }
            }
            
            List<FigmaIcon> deduplicatedImages = new ArrayList<>(uniqueImages.values());
            logger.info("After deduplication: {} unique visual elements", deduplicatedImages.size());
            
            return FigmaIconsResult.create(fileId, deduplicatedImages);

        } catch (Exception e) {
            logger.error("Failed to extract visual elements from Figma design: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    

    

    
         /**
      * Get image URL for an icon in a specific format
      */
     private String getImageUrlForIcon(String fileId, String nodeId, String format) throws Exception {
         GenericRequest getRequest = new GenericRequest(this, path("images/" + fileId));
        
        getRequest.param("ids", nodeId);
        getRequest.param("format", format);
        
        if (format.equals("png")) {
            getRequest.param("scale", "2"); // 2x resolution for crisp icons
        }
        
        try {
            String response = execute(getRequest);
            JSONObject responseJson = new JSONObject(response);
            
            if (responseJson.has("images")) {
                JSONObject images = responseJson.getJSONObject("images");
                String cleanNodeId = nodeId.replace("-", ":");
                
                if (images.has(cleanNodeId)) {
                    return images.getString(cleanNodeId);
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Failed to get {} URL for icon {}: {}", format, nodeId, e.getMessage());
            return null;
        }
    }
    


    public String getImageById(
       String href,
       String nodeId,
       String format
    ) throws Exception {
        href = href.replaceAll("&amp;", "&");
        String fileId = parseFileId(href);
        
        try {
            return getImageUrlForIcon(fileId, nodeId, format);
        } catch (Exception e) {
            logger.error("Failed to get image URL for node {} in format {}: {}", nodeId, format, e.getMessage());
            return null;
        }
    }

    /**
     * Download icon as file by node ID and format.
     * This method gets the export URL and downloads the actual file.
     *
     * @param href The Figma design URL (to extract file ID)
     * @param nodeId The specific node ID to export
     * @param format The export format (png, jpg, svg, pdf)
     * @return File containing the downloaded icon, or null if error occurs
     * @throws Exception if there's an error accessing Figma API
     */
    @MCPTool(
        name = "figma_download_image_as_file",
        description = "Download image as file by node ID and format. Use this after figma_get_icons to download actual icon files.",
        integration = "figma", 
        category = "content_access"
    )
    public File downloadIconFile(
        @MCPParam(name = "href", description = "Figma design URL to extract file ID from", required = true, example = "https://www.figma.com/file/abc123/Design") String href,
        @MCPParam(name = "nodeId", description = "Node ID to export (from figma_get_icons result)", required = true, example = "123:456") String nodeId,
        @MCPParam(name = "format", description = "Export format", required = true, example = "png") String format
    ) throws Exception {
        // First get the image URL
        String imageUrl = getImageById(href, nodeId, format);
        if (imageUrl == null || imageUrl.isEmpty()) {
            logger.error("Failed to get image URL for node {} in format {}", nodeId, format);
            return null;
        }
        
        try {
            // Download the file from the URL
            return downloadImage(imageUrl);
        } catch (Exception e) {
            logger.error("Failed to download icon file for node {} in format {}: {}", nodeId, format, e.getMessage());
            return null;
        }
    }

    /**
     * Get SVG content as text by node ID.
     * This method gets the SVG export URL and returns the SVG content as text.
     *
     * @param href The Figma design URL (to extract file ID)
     * @param nodeId The specific node ID to export as SVG
     * @return String containing the SVG content, or null if error occurs
     * @throws Exception if there's an error accessing Figma API
     */
    @MCPTool(
        name = "figma_get_svg_content",
        description = "Get SVG content as text by node ID. Use this after figma_get_icons to get SVG code for vector icons.",
        integration = "figma", 
        category = "content_access"
    )
    public String getSvgContent(
        @MCPParam(name = "href", description = "Figma design URL to extract file ID from", required = true, example = "https://www.figma.com/file/abc123/Design") String href,
        @MCPParam(name = "nodeId", description = "Node ID to export as SVG (from figma_get_icons result)", required = true, example = "123:456") String nodeId
    ) throws Exception {
        // Get the SVG URL
        String svgUrl = getImageById(href, nodeId, "svg");
        if (svgUrl == null || svgUrl.isEmpty()) {
            logger.error("Failed to get SVG URL for node {}", nodeId);
            return null;
        }
        
        try {
            // Fetch the SVG content as text using the same pattern as downloadImage
            GenericRequest getRequest = new GenericRequest(this, svgUrl);
            String svgContent = execute(getRequest);
            
            logger.info("Successfully retrieved SVG content for node: {}", nodeId);
            return svgContent;
        } catch (Exception e) {
            logger.error("Failed to get SVG content for node {}: {}", nodeId, e.getMessage());
            return null;
        }
    }



    // Mock method to get all teams
    public JSONArray getAllTeams() throws Exception {
        String url = path("teams");  // Replace with actual endpoint
        String response = execute(new GenericRequest(this, url));
        JSONObject jsonResponse = new JSONObject(response);
        return jsonResponse.getJSONArray("teams");
    }

    public void getAllCommentsForAllTeams() throws Exception {
        JSONArray teams = getAllTeams();
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.getJSONObject(i);
            String teamId = team.getString("id");
            logger.info("Processing team: " + team.getString("name"));

            // Call getAllCommentsForTeam for each team
            getAllCommentsForTeam(teamId);
        }
    }

    public void getAllCommentsForTeam(String teamId) throws Exception {
        JSONArray projects = getProjects(teamId);
        for (int i = 0; i < projects.length(); i++) {
            JSONObject project = projects.getJSONObject(i);
            String projectId = project.getString("id");
            JSONArray files = getFiles(projectId);

            for (int j = 0; j < files.length(); j++) {
                JSONObject file = files.getJSONObject(j);
                String fileKey = file.getString("key");
                List<IComment> comments = getComments(fileKey);

                for (int k = 0; k < comments.size(); k++) {
                    IComment comment = comments.get(k);
                    logger.info("Comment: " + comment.toString());
                }
            }
        }
    }

    // Get all projects within a team
    public JSONArray getProjects(String teamId) throws Exception {
        String url = path("teams/" + teamId + "/projects");
        String response = execute(new GenericRequest(this, url));
        JSONObject jsonResponse = new JSONObject(response);
        return jsonResponse.getJSONArray("projects");
    }

    // Get all files within a project
    public JSONArray getFiles(String projectId) throws Exception {
        String url = path("projects/" + projectId + "/files");
        String response = execute(new GenericRequest(this, url));
        JSONObject jsonResponse = new JSONObject(response);
        return jsonResponse.getJSONArray("files");
    }

    // Get all comments in a file
    public List<IComment> getComments(String fileKey) throws Exception {
        String url = path("files/" + fileKey + "/comments");
        String response = execute(new GenericRequest(this, url));
        JSONObject jsonResponse = new JSONObject(response);
        return JSONModel.convertToModels(FigmaComment.class, jsonResponse.getJSONArray("comments"));
    }

    @Override
    public Set<String> parseUris(String object) throws Exception {
        return IssuesIDsParser.extractFigmaUrls(getBasePath(), object);
    }

    @Override
    public Object uriToObject(String uri) throws Exception {
        if (isValidImageUrl(uri)) {
            return convertUrlToFile(uri);
        }
        return null;
    }
}
