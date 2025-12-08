package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.atlassian.confluence.model.SearchResult;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.common.utils.MarkdownToJiraConverter;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import dagger.multibindings.StringKey;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Confluence extends AtlassianRestClient implements UriToObject {
    private final Logger logger;  // Changed from static to instance member
    private String graphQLPath;

    @Getter
    @Setter
    private String defaultSpace; // Added defaultSpace field
    
    // Default constructor - backward compatibility
    public Confluence(String basePath, String authorization) throws IOException {
        this(basePath, authorization, LogManager.getLogger(Confluence.class));
    }
    
    // Constructor with logger injection for server-managed mode
    public Confluence(String basePath, String authorization, Logger logger) throws IOException {
        this(basePath, authorization, logger, null);
    }
    
    // NEW: Constructor with defaultSpace support
    public Confluence(String basePath, String authorization, Logger logger, String defaultSpace) throws IOException {
        super(basePath, authorization);
        this.logger = logger != null ? logger : LogManager.getLogger(Confluence.class);
        this.defaultSpace = defaultSpace;
        setClearCache(true);
        setCacheGetRequestsEnabled(false);
    }

    @Override
    public String path(String path) {
        return getBasePath() + "/rest/api/" + path;
    }

    @MCPTool(
        name = "confluence_contents_by_urls",
        description = "Get Confluence content by multiple URLs. Returns a list of content objects for each valid URL.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public List<Content> contentsByUrls(
        @MCPParam(name = "urlStrings", description = "Array of Confluence URLs to retrieve content from", required = true, example = "['https://confluence.example.com/wiki/spaces/SPACE/pages/123/Page+Title']")
        String ... urlStrings
    ) throws IOException {
        List<Content> result = new ArrayList<>();
        for (String url : urlStrings) {
            if (url != null && !url.isEmpty()) {
                try {
                    result.add(contentByUrl(url));
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        }
        return result;
    }

    public Content contentByUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        String[] pathSegments = url.getPath().split("/");

        // Remove empty segments if path starts with /
        List<String> segments = Arrays.stream(pathSegments)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (segments.isEmpty()) {
            throw new UnsupportedOperationException("Invalid URL format");
        }

        // Handle different URL patterns
        switch (segments.get(0)) {
            case "wiki": {
                if (segments.get(1).equalsIgnoreCase("x")) {
                    return contentByUrl(AbstractRestClient.resolveRedirect(this, urlString));
                } else{
                    return handleWikiUrls(segments);
                }
            }
            case "spaces": {
                return handleWikiUrls(segments);
            }
            case "l":
                return contentByUrl(AbstractRestClient.resolveRedirect(this, urlString));
            default:
                throw new UnsupportedOperationException("Unknown URL format");
        }
    }

    private Content handleWikiUrls(List<String> segments) throws IOException {
        if (segments.size() < 2) {
            throw new UnsupportedOperationException("Invalid wiki URL format");
        }

        try {
            return checkBaseIndex(segments, 1);
        } catch (Exception e) {
            return checkBaseIndex(segments, 0);
        }
    }

    private Content checkBaseIndex(List<String> segments, int baseIndex) throws IOException {
        switch (segments.get(baseIndex)) {
            case "spaces":
                // Handle /wiki/spaces/{spaceKey}/pages/{pageId}/{title}
                // [wiki, spaces, spaceKey, pages, pageId, title]
                //   0      1        2        3       4      5
                if (segments.size() > baseIndex + 3 && "pages".equals(segments.get(baseIndex + 2))) {
                    String contentId = segments.get(baseIndex + 3); // Use dynamic index instead of hardcoded 4
                    return contentById(contentId);
                }
                break;
            case "x":
                // Handle /wiki/x/{id}
                // [wiki, x, id]
                //   0    1   2
                if (segments.size() > baseIndex + 1) {
                    String contentId = segments.get(baseIndex + 1);
                    return contentById(contentId);
                }
                break;
            case "display":
                // Handle /wiki/display/~{userIdentifier}/{pageName}
                // [wiki, display, userIdentifier, pageName]
                //   0      1          2            3
                if (segments.size() > baseIndex + 2) {
                    String userIdentifier = segments.get(baseIndex + 1);
                    String pageName = URLDecoder.decode(segments.get(baseIndex + 2), StandardCharsets.UTF_8);
                    return content(pageName, userIdentifier).getContents().get(0);
                }
                break;
        }

        throw new UnsupportedOperationException("Invalid wiki URL format");
    }


    @MCPTool(
        name = "confluence_search_content_by_text",
        description = "Search Confluence content by text query using CQL (Confluence Query Language). Returns search results with content excerpts.",
        integration = "confluence",
        category = "search"
    )
    public List<SearchResult> searchContentByText(
        @MCPParam(name = "query", description = "Search query text to find in Confluence content", required = true, example = "project documentation")
        String query,
        @MCPParam(name = "limit", description = "Maximum number of search results to return", required = true, example = "10")
        int limit
    ) throws IOException {
        if (graphQLPath != null) {
            JSONArray results = new ConfluenceGraphQLClient(graphQLPath, authorization).search(query, limit);
            List<SearchResult> searchResults = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject node = results.getJSONObject(i).getJSONObject("node");
                searchResults.add(new SearchResult(node));
            }
            return searchResults;
        }
        // Use Confluence Search API with enhanced query logic
        GenericRequest search = new GenericRequest(this, path("content/search"));

        // Design a more comprehensive query
        String cqlQuery = String.format("(title ~ \"%s\" OR text ~ \"%s\") ORDER BY lastModified ASC", query, query);
        search.param("cql", cqlQuery);
        search.param("limit", String.valueOf(limit));
        search.param("expand", "title,body.excerpt,history,space,body.storage"); // Include additional fields

//        GenericRequest searchRequest = new GenericRequest(this, path("content"))
//                .param("body.storage", query)  // Search by exact title match
//                .param("expand", "title,body.excerpt,body.storage");
//
//        String response = searchRequest.execute();
        // Execute the search request
        String response = search.execute();

        // Parse response and return the results array
        return JSONModel.convertToModels(SearchResult.class, new JSONObject(response).getJSONArray("results"));
    }

    @MCPTool(
        name = "confluence_content_by_id",
        description = "Get Confluence content by its unique content ID. Returns detailed content information including body, version, and metadata.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public Content contentById(
        @MCPParam(name = "contentId", description = "The unique content ID of the Confluence page", required = true, example = "123456")
        String contentId
    ) throws IOException {
        // Construct the path using the content ID and expand needed fields
        GenericRequest content = new GenericRequest(this, path("content/" + contentId + "?expand=body.storage,ancestors,version"));

        // Execute the request
        String response = execute(content);

        try {
            // Parse and return the result
            return new Content(response);
        } catch (Exception e) {
            // Log any exceptions and the response for easier debugging
            logger.error(response);
            throw e;
        }
    }

    @MCPTool(
        name = "confluence_content_by_title_and_space",
        description = "Get Confluence content by title and space key. Returns content result with metadata and body information.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public ContentResult content(
        @MCPParam(name = "title", description = "The title of the Confluence page", required = true, example = "Project Documentation")
        String title,
        @MCPParam(name = "space", description = "The space key where the content is located", required = true, example = "PROJ")
        String space
    ) throws IOException {
        GenericRequest content = new GenericRequest(this, path("content?expand=body.storage,ancestors,version"));
        content.param("title", title);
        if (space != null && !space.isEmpty()) {
            content.param("spaceKey", space);
        }
        String response = execute(content);
        try {
            return new ContentResult(response);
        } catch (Exception e) {
            logger.error(response);
            throw e;
        }
    }

    @MCPTool(
        name = "confluence_get_current_user_profile",
        description = "Get the current user's profile information from Confluence. Returns user details for the authenticated user.",
        integration = "confluence",
        category = "user_management"
    )
    public String profile() throws IOException {
        GenericRequest content = new GenericRequest(this, path("user/current"));
        return execute(content);
    }

    @MCPTool(
        name = "confluence_get_user_profile_by_id",
        description = "Get a specific user's profile information from Confluence by user ID. Returns user details for the specified user.",
        integration = "confluence",
        category = "user_management"
    )
    public String profile(
        @MCPParam(name = "userId", description = "The account ID of the user to get profile for", required = true, example = "123456:abcdef-1234-5678-90ab-cdef12345678")
        String userId
    ) throws IOException {
        GenericRequest content = new GenericRequest(this, path("user?accountId=" + userId));
        return execute(content);
    }

    @MCPTool(
        name = "confluence_get_content_attachments",
        description = "Get all attachments for a specific Confluence content. Returns a list of attachment objects with metadata.",
        integration = "confluence",
        category = "content_management"
    )
    public List<Attachment> getContentAttachments(
        @MCPParam(name = "contentId", description = "The content ID to get attachments for", required = true, example = "123456")
        String contentId
    ) throws IOException {
        GenericRequest content = new GenericRequest(this, path("content/" + contentId + "/child/attachment"));

        String response = execute(content);
        try {
            return new ContentResult(response).getAttachments();
        } catch (Exception e) {
            logger.error(response);
            throw e;
        }
    }

    public ContentResult getContentVersions(String contentId) throws IOException {
//        GenericRequest content = new GenericRequest(this, path("content/" + contentId + "/history"));
//        GenericRequest content = new GenericRequest(this, path("content/" + contentId + "/version"))
        GenericRequest content = new GenericRequest(this, path("content/" + contentId + "/history"));
        String response = execute(content);
        try {
            return new ContentResult(response);
        } catch (Exception e) {
            logger.error(response);
            throw e;
        }
    }

    @MCPTool(
        name = "confluence_find_content_by_title_and_space",
        description = "Find Confluence content by title and space key. Returns the first matching content or null if not found.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public Content findContent(
        @MCPParam(name = "title", description = "The title of the content to find", required = true, example = "Project Documentation")
        String title,
        @MCPParam(name = "space", description = "The space key where to search for the content", required = true, example = "PROJ")
        String space
    ) throws IOException {
        List<Content> contents = content(title, space).getContents();
        if (contents.isEmpty()) {
            return null;
        } else {
            return contents.get(0);
        }
    }

    @MCPTool(
        name = "confluence_create_page",
        description = "Create a new Confluence page with specified title, parent, body content, and space. Returns the created content object.",
        integration = "confluence",
        category = "content_management"
    )
    public Content createPage(
        @MCPParam(name = "title", description = "The title of the new page", required = true, example = "New Project Page")
        String title,
        @MCPParam(name = "parentId", description = "The ID of the parent page", required = true, example = "123456")
        String parentId,
        @MCPParam(name = "body", description = "The body content of the page in Confluence storage format", required = true, example = "<p>This is the page content.</p>")
        String body,
        @MCPParam(name = "space", description = "The space key where to create the page", required = true, example = "PROJ")
        String space
    ) throws IOException {
        GenericRequest content = new GenericRequest(this, path("content"));
        content.setBody(new JSONObject()
                .put("type", "page")
                .put("title", title)
                .put("ancestors", new JSONArray().put(new JSONObject().put("id", parentId)))
                .put("space", new JSONObject().put("key", space))
                .put("body", new JSONObject().put("storage", new JSONObject()
                        .put("value", body)
                        .put("representation", "storage"))).toString());
        return new Content(content.post());
    }

    @MCPTool(
        name = "confluence_update_page",
        description = "Update an existing Confluence page with new title, parent, body content, and space. Returns the updated content object.",
        integration = "confluence",
        category = "content_management"
    )
    public Content updatePage(
        @MCPParam(name = "contentId", description = "The ID of the page to update", required = true, example = "123456")
        String contentId,
        @MCPParam(name = "title", description = "The new title for the page", required = true, example = "Updated Project Page")
        String title,
        @MCPParam(name = "parentId", description = "The ID of the new parent page", required = true, example = "123456")
        String parentId,
        @MCPParam(name = "body", description = "The new body content of the page in Confluence storage format", required = true, example = "<p>This is the updated page content.</p>")
        String body,
        @MCPParam(name = "space", description = "The space key where the page is located", required = true, example = "PROJ")
        String space
    ) throws IOException {
        return updatePage(contentId, title, parentId, body, space, "");
    }

    @MCPTool(
        name = "confluence_update_page_with_history",
        description = "Update an existing Confluence page with new content and add a history comment. Returns the updated content object.",
        integration = "confluence",
        category = "content_management"
    )
    public Content updatePage(
        @MCPParam(name = "contentId", description = "The ID of the page to update", required = true, example = "123456")
        String contentId,
        @MCPParam(name = "title", description = "The new title for the page", required = true, example = "Updated Project Page")
        String title,
        @MCPParam(name = "parentId", description = "The ID of the new parent page", required = true, example = "123456")
        String parentId,
        @MCPParam(name = "body", description = "The new body content of the page in Confluence storage format", required = true, example = "<p>This is the updated page content.</p>")
        String body,
        @MCPParam(name = "space", description = "The space key where the page is located", required = true, example = "PROJ")
        String space,
        @MCPParam(name = "historyComment", description = "Comment to add to the page history", required = true, example = "Updated content based on user feedback")
        String historyComment
    ) throws IOException {
        body = prepareBodyForConfluence(body);
        logger.info("{} {} {} {} {} {}", contentId, title, parentId, body, space, historyComment);
        Content oldContent = new Content(new GenericRequest(this, path("content/" + contentId + "?expand=version")).execute());

        logger.info("{}, {}, {}, {}, {}, {}", contentId, title, parentId, body, space, historyComment);

        GenericRequest content = new GenericRequest(this, path("content/"+contentId));

        String value = body;

        content.setBody(new JSONObject()
                .put("id", contentId)
                .put("type", "page")
                .put("title", title)
                .put("ancestors", new JSONArray().put(new JSONObject().put("id", parentId)))
                .put("space", new JSONObject().put("key", space))
                .put("version",
                        new JSONObject()
                                .put("number", oldContent.getVersionNumber() + 1)
                                .put("message", historyComment) // Add history comment here
                )
                .put("body", new JSONObject().put("storage", new JSONObject()
                        .put("value", value)
                        .put("representation", "storage"))).toString());
        String putResponse = content.put();
        logger.info(putResponse);
        return new Content(putResponse);
    }

    @NotNull
    public static String prepareBodyForConfluence(String body) {
        body = body.replaceAll("<br>", "\n").replaceAll("<br/>", "\n");
        body = HtmlCleaner.convertLinksUrlsToConfluenceFormat(body);
        return body;
    }

    public static String macroHTML(String body) {
        return "<ac:structured-macro ac:macro-id=\""+System.currentTimeMillis()+"\" ac:name=\"html\" ac:schema-version=\"1\">\n" +
                "  <ac:plain-text-body><![CDATA["+body+"]]></ac:plain-text-body>\n" +
                "</ac:structured-macro>";
    }

    public static String macroCloudHTML(String body) {
        return  "<ac:structured-macro ac:name=\"swc-macro-html-input\" ac:schema-version=\"1\" data-layout=\"default\" ac:local-id=\"cbe31bea-aec4-454a-ac9d-7c0611b882d0\" ac:macro-id=\"8c4516f5-9f3d-4ff9-830d-ab2747611451\"><ac:rich-text-body><ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\" ac:macro-id=\"da0a0dc5-5e02-40cc-9acc-303202ad6165\"><ac:plain-text-body><![CDATA[<body>"
 +body+
        "</body>]]></ac:plain-text-body></ac:structured-macro></ac:rich-text-body></ac:structured-macro><p />";
    }

    public static String macroBase64Image(String imageBase64) {
        return  "<div>\n" +
                "    <img src=\"data:image/png;base64,"+imageBase64+"\" alt=\"Embedded Image\">\n" +
                "</div>\n";
    }

    public void attachFileToPage(String contentId, File file) throws IOException {
        List<Attachment> contentAttachments = getContentAttachments(contentId);
        for (Attachment attachment : contentAttachments) {
            if (attachment.getTitle().equalsIgnoreCase(file.getName())) {
                return;
            }
        }
        String url = path("content/" + contentId + "/child/attachment");
        // Prepare the file part
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        okhttp3.RequestBody.Companion.create(file, MediaType.parse("image/*"))
                ).build();

        if (true) {
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Create the request
        Request request = sign(new Request.Builder()
                .url(url)
                .post(requestBody)
        )
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            logger.info(response.body().string());
        }
    }

    public void insertImageInPageBody(String spaceKey, String contentId, String fileName) throws IOException {
        // Get the current content
        Content content = new Content(new GenericRequest(this, path("content/" + contentId + "?expand=body.storage")).execute());

        // Prepare the new body with the image
        String newBody = content.getStorage().getValue() + "<ac:image><ri:attachment ri:filename=\"" + fileName + "\" /></ac:image>";

        // Update the page with the new body
        updatePage(contentId, content.getTitle(), content.getParentId(), newBody, spaceKey);
    }

    @MCPTool(
        name = "confluence_get_children_by_name",
        description = "Get child pages of a Confluence page by space key and content name. Returns a list of child content objects.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public List<Content> getChildrenOfContentByName(
        @MCPParam(name = "spaceKey", description = "The space key where the parent page is located", required = true, example = "PROJ")
        String spaceKey,
        @MCPParam(name = "contentName", description = "The name/title of the parent page", required = true, example = "Project Documentation")
        String contentName
    ) throws IOException {
        Content pageContent = findContent(contentName, spaceKey);
        String contentId = pageContent.getId();
        return getChildrenOfContentById(contentId);
    }

    @MCPTool(
        name = "confluence_get_children_by_id",
        description = "Get child pages of a Confluence page by content ID. Returns a list of child content objects.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public List<Content> getChildrenOfContentById(
        @MCPParam(name = "contentId", description = "The content ID of the parent page", required = true, example = "123456")
        String contentId
    ) throws IOException {
        return new ContentResult(execute(new GenericRequest(this, path("content/" + contentId + "/child/page?limit=100&expand=body.storage,ancestors,version")))).getContents();
    }

    /**
     * Downloads an attachment file to the specified target directory.
     * @param attachment the attachment to download
     * @param targetDir the target directory to save the file
     * @return the downloaded file, or null if download failed
     * @throws IOException if an error occurs during download
     */
    @MCPTool(
        name = "confluence_download_attachment",
        description = "Download an attachment file from Confluence to a specified directory.",
        integration = "confluence",
        category = "content_management"
    )
    public File downloadAttachment(
        @MCPParam(name = "attachment", description = "The attachment object to download", required = true)
        Attachment attachment,
        @MCPParam(name = "targetDir", description = "The target directory to save the file", required = true, example = "/path/to/directory")
        File targetDir
    ) throws IOException {
        String downloadLink = attachment.getDownloadLink();
        if (downloadLink == null || downloadLink.isEmpty()) {
            logger.warn("No download link available for attachment: {}", attachment.getTitle());
            return null;
        }
        
        // Ensure the target directory exists using atomic method
        java.nio.file.Files.createDirectories(targetDir.toPath());
        
        // Build the full download URL - ensure proper path joining
        String basePath = getBasePath();
        String fullUrl;
        if (basePath.endsWith("/") && downloadLink.startsWith("/")) {
            fullUrl = basePath + downloadLink.substring(1);
        } else if (!basePath.endsWith("/") && !downloadLink.startsWith("/")) {
            fullUrl = basePath + "/" + downloadLink;
        } else {
            fullUrl = basePath + downloadLink;
        }
        
        // Sanitize filename to prevent directory traversal attacks
        // Uses shared utility with 200 character limit
        String safeFileName = StringUtils.sanitizeFileName(attachment.getTitle(), "unnamed_attachment", 200);
        
        // Check if filename has extension, if not, try to get it from mediaType
        if (!safeFileName.contains(".")) {
            String mediaType = attachment.getMediaType();
            if (mediaType != null && !mediaType.isEmpty()) {
                String extension = getExtensionFromMediaType(mediaType);
                if (extension != null && !extension.isEmpty()) {
                    safeFileName = safeFileName + "." + extension;
                    logger.debug("Added extension '{}' from mediaType '{}' to filename '{}'", extension, mediaType, safeFileName);
                }
            }
        }
        
        // Create target file
        File targetFile = new File(targetDir, safeFileName);
        
        // Download the file
        return RestClient.Impl.downloadFile(this, new GenericRequest(this, fullUrl), targetFile);
    }

    protected void setGraphQLPath(String graphQLPath) {
        this.graphQLPath = graphQLPath;
    }
    
    /**
     * Converts a MIME media type to a file extension.
     * @param mediaType the MIME type (e.g., "image/jpeg", "image/png")
     * @return the file extension (e.g., "jpg", "png") or null if not recognized
     */
    private String getExtensionFromMediaType(String mediaType) {
        if (mediaType == null || mediaType.isEmpty()) {
            return null;
        }
        
        // Normalize media type (remove parameters, convert to lowercase)
        String normalized = mediaType.toLowerCase().split(";")[0].trim();
        
        // Map common MIME types to extensions
        switch (normalized) {
            // Images (only supported formats: gif, jpeg, png, webp)
            case "image/jpeg":
            case "image/jpg":
                return "jpeg";  // Use "jpeg" instead of "jpg" to match supported format
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            
            // Documents
            case "application/pdf":
                return "pdf";
            case "application/msword":
                return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            case "application/vnd.ms-excel":
                return "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return "xlsx";
            case "application/vnd.ms-powerpoint":
                return "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                return "pptx";
            
            // Text
            case "text/plain":
                return "txt";
            case "text/html":
                return "html";
            case "text/css":
                return "css";
            case "text/javascript":
            case "application/javascript":
                return "js";
            case "application/json":
                return "json";
            case "text/xml":
            case "application/xml":
                return "xml";
            
            // Archives
            case "application/zip":
                return "zip";
            case "application/x-tar":
                return "tar";
            case "application/gzip":
                return "gz";
            
            default:
                // Try to extract extension from media type if it follows pattern like "image/x-png"
                if (normalized.contains("/")) {
                    String subtype = normalized.substring(normalized.indexOf('/') + 1);
                    // Remove "x-" prefix if present
                    if (subtype.startsWith("x-")) {
                        subtype = subtype.substring(2);
                    }
                    // If subtype looks like an extension (short, no special chars), use it
                    if (subtype.length() <= 5 && subtype.matches("^[a-z0-9]+$")) {
                        return subtype;
                    }
                }
                logger.debug("Unknown media type: {}, cannot determine extension", mediaType);
                return null;
        }
    }

    /**
     * Find content by title in the default space.
     * @param title the title to search for
     * @return the content if found, null otherwise
     * @throws IOException if an error occurs
     */
    @MCPTool(
        name = "confluence_find_content",
        description = "Find a Confluence page by title in the default space. Returns the page content if found.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public Content findContent(
        @MCPParam(name = "title", description = "Title of the Confluence page to find", required = true, example = "Project Documentation")
        String title
    ) throws IOException {
        if (defaultSpace == null) {
            throw new IllegalStateException("Default space not set. Use findContent(String title, String space) instead.");
        }
        return findContent(title, defaultSpace);
    }
    
    /**
     * Create or find content by title in the default space.
     * @param title the title to search for or create
     * @param parentId the parent content ID
     * @param body the content body
     * @return the content (created or found)
     * @throws IOException if an error occurs
     */
    @MCPTool(
        name = "confluence_find_or_create",
        description = "Find a Confluence page by title in the default space, or create it if it doesn't exist. Returns the found or created content.",
        integration = "confluence",
        category = "content_management"
    )
    public Content findOrCreate(
        @MCPParam(name = "title", description = "Title of the page to find or create", required = true, example = "Project Documentation")
        String title,
        @MCPParam(name = "parentId", description = "ID of the parent page for creation", required = true, example = "123456")
        String parentId,
        @MCPParam(name = "body", description = "Body content for the new page (if creation is needed)", required = true, example = "<p>This is the page content.</p>")
        String body
    ) throws IOException {
        if (defaultSpace == null) {
            throw new IllegalStateException("Default space not set. Use findOrCreate(String title, String parentId, String body, String space) instead.");
        }
        Content content = findContent(title, defaultSpace);
        if (content == null) {
            content = createPage(title, parentId, body, defaultSpace);
        }
        return content;
    }
    
    /**
     * Get content by title in the default space.
     * @param title the title to search for
     * @return the content result
     * @throws IOException if an error occurs
     */
    @MCPTool(
        name = "confluence_content_by_title",
        description = "Get Confluence content by title in the default space. Returns content result with metadata and body information.",
        integration = "confluence",
        category = "content_retrieval"
    )
    public ContentResult content(
        @MCPParam(name = "title", description = "Title of the Confluence page to get", required = true, example = "Project Documentation")
        String title
    ) throws IOException {
        if (defaultSpace == null) {
            throw new IllegalStateException("Default space not set. Use content(String title, String space) instead.");
        }
        return content(title, defaultSpace);
    }

    @Override
    public Set<String> parseUris(String object) throws Exception {
        return IssuesIDsParser.extractConfluenceUrls(getBasePath(), object);
    }

    @Override
    public Object uriToObject(String uri) throws Exception {
        try {
            Content content = contentByUrl(uri);
            if (content != null) {
                return HtmlCleaner.cleanOnlyStylesAndSizes(content.getStorage().getValue());
            }
            return null;
        } catch (Exception ignored){
            return null;
        }
    }

    public String encodeContent(String content) {
        try {
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8.toString());
            return "body=" + encodedContent;
        } catch (Exception e) {
            return "body=" + content;
        }
    }
}