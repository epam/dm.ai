package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.atlassian.confluence.model.SearchResult;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.common.utils.MarkdownToJiraConverter;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.networking.AbstractRestClient;
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
    private static final Logger logger = LogManager.getLogger(Confluence.class);
    private String graphQLPath;

    public Confluence(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
        setClearCache(true);
        setCacheGetRequestsEnabled(false);
    }

    @Override
    public String path(String path) {
        return getBasePath() + "/rest/api/" + path;
    }

    public List<Content> contentsByUrls(String ... urlStrings) throws IOException {
        List<Content> result = new ArrayList<>();
        for (String url : urlStrings) {
            if (url != null && !url.isEmpty()) {
                result.add(contentByUrl(url));
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


    public List<SearchResult> searchContentByText(String query, int limit) throws IOException {
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


    public Content contentById(String contentId) throws IOException {
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

    public ContentResult content(String title, String space) throws IOException {
        GenericRequest content = new GenericRequest(this, path("content?expand=body.storage,ancestors,version"));
        content.param("title", title);
        content.param("spaceKey", space);
        String response = execute(content);
        try {
            return new ContentResult(response);
        } catch (Exception e) {
            logger.error(response);
            throw e;
        }
    }

    public String profile() throws IOException {
        GenericRequest content = new GenericRequest(this, path("user/current"));
        return execute(content);
    }

    public String profile(String userId) throws IOException {
        GenericRequest content = new GenericRequest(this, path("user?accountId=" + userId));
        return execute(content);
    }

    public List<Attachment> getContentAttachments(String contentId) throws IOException {
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

    public Content findContent(String title, String space) throws IOException {
        List<Content> contents = content(title, space).getContents();
        if (contents.isEmpty()) {
            return null;
        } else {
            return contents.get(0);
        }
    }

    public Content createPage(String title, String parentId, String body, String space) throws IOException {
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

    public Content updatePage(String contentId, String title, String parentId, String body, String space) throws IOException {
        return updatePage(contentId, title, parentId, body, space, "");
    }

    public Content updatePage(String contentId, String title, String parentId, String body, String space, String historyComment) throws IOException {
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


    public List<Content> getChildrenOfContentByName(String spaceKey, String contentName) throws IOException {
        Content pageContent = findContent(contentName, spaceKey);
        String contentId = pageContent.getId();
        return getChildrenOfContentById(contentId);
    }

    public List<Content> getChildrenOfContentById(String contentId) throws IOException {
        return new ContentResult(execute(new GenericRequest(this, path("content/" + contentId + "/child/page?limit=100")))).getContents();
    }

    protected void setGraphQLPath(String graphQLPath) {
        this.graphQLPath = graphQLPath;
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
                return MarkdownToJiraConverter.convertToJiraMarkdown(HtmlCleaner.cleanOnlyStylesAndSizes(content.getStorage().getValue()));
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