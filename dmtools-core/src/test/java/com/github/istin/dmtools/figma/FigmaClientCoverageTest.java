// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.figma.model.FigmaFileResponse;
import com.github.istin.dmtools.figma.model.FigmaIconsResult;
import com.github.istin.dmtools.figma.model.FigmaNodeChildrenResult;
import com.github.istin.dmtools.figma.model.FigmaNodeDetails;
import com.github.istin.dmtools.figma.model.FigmaStylesResult;
import com.github.istin.dmtools.figma.model.FigmaTextContentResult;
import com.github.istin.dmtools.networking.RetryPolicy;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional unit tests for {@link FigmaClient} focused on previously uncovered
 * public methods: base path normalization, retry/recovery policy, the /me
 * connection test, OAuth2 helper error branches, image/layer/style/text parsing
 * flows and batch operations.
 *
 * <p>All HTTP transport is intercepted by stubbing {@code execute(GenericRequest)}
 * (or {@code getClient()} for direct OkHttp downloads) on a Mockito spy, so no
 * real network calls are made.</p>
 */
public class FigmaClientCoverageTest {

    private static final String BASE_PATH = "https://api.figma.com/v1";
    private static final String AUTHORIZATION = "test_token";
    private static final String FILE_URL = "https://www.figma.com/file/FILEID123/Design";
    private static final String NODE_URL = FILE_URL + "?node-id=1-2";

    /**
     * Concrete client whose cache folder is isolated per test class
     * ("cacheTestableFigmaClientCoverage").
     */
    static class TestableFigmaClient extends FigmaClient {
        TestableFigmaClient() throws IOException {
            super(BASE_PATH, AUTHORIZATION);
        }

        @Override
        protected String getCacheFolderName() {
            return "cacheTestableFigmaClientCoverage";
        }
    }

    private FigmaClient figmaClient;

    @Before
    public void setUp() throws Exception {
        figmaClient = Mockito.spy(new TestableFigmaClient());
    }

    @After
    public void tearDown() {
        PropertyReader.clearOverrides();
    }

    // ------------------------------------------------------------------
    // normalizeBasePath
    // ------------------------------------------------------------------

    @Test
    public void testNormalizeBasePath_nullReturnsDefault() {
        assertEquals("https://api.figma.com/v1", FigmaClient.normalizeBasePath(null));
    }

    @Test
    public void testNormalizeBasePath_addsV1() {
        assertEquals("https://api.figma.com/v1", FigmaClient.normalizeBasePath("https://api.figma.com"));
    }

    @Test
    public void testNormalizeBasePath_removesTrailingSlashAndAddsV1() {
        assertEquals("https://api.figma.com/v1", FigmaClient.normalizeBasePath(" https://api.figma.com/ "));
    }

    @Test
    public void testNormalizeBasePath_keepsExistingV1() {
        assertEquals("https://api.figma.com/v1", FigmaClient.normalizeBasePath("https://api.figma.com/v1"));
        assertEquals("https://api.figma.com/v1", FigmaClient.normalizeBasePath("https://api.figma.com/v1/"));
    }

    // ------------------------------------------------------------------
    // path / getTimeout
    // ------------------------------------------------------------------

    @Test
    public void testPath_baseWithoutTrailingSlash() {
        assertEquals(BASE_PATH + "/me", figmaClient.path("me"));
    }

    @Test
    public void testPath_pathStartsWithSlash() {
        assertEquals(BASE_PATH + "/me", figmaClient.path("/me"));
    }

    @Test
    public void testPath_baseWithTrailingSlash() throws Exception {
        FigmaClient client = Mockito.spy(new FigmaClient(BASE_PATH + "/", AUTHORIZATION));
        assertEquals(BASE_PATH + "/me", client.path("me"));
    }

    @Test
    public void testGetTimeout() {
        assertEquals(300, figmaClient.getTimeout());
    }

    // ------------------------------------------------------------------
    // retry policy / recoverable errors
    // ------------------------------------------------------------------

    @Test
    public void testRetryPolicy_clientErrorIsNotRetryable() {
        RetryPolicy policy = figmaClient.getRetryPolicy();
        assertNotNull(policy);
        RestClient.RestClientException clientError =
                new RestClient.RestClientException("404 Not Found", "body", 404);
        assertFalse(policy.isRetryable(clientError));
    }

    @Test
    public void testRetryPolicy_serverErrorIsRetryable() {
        RetryPolicy policy = figmaClient.getRetryPolicy();
        assertTrue(policy.isRetryable(new IOException("503 Service Unavailable")));
    }

    @Test
    public void testIsRecoverableConnectionError_clientErrorNotRecoverable() throws Exception {
        RestClient.RestClientException clientError =
                new RestClient.RestClientException("401 Unauthorized", "body", 401);
        assertFalse(figmaClient.isRecoverableConnectionError(clientError));
    }

    @Test
    public void testIsRecoverableConnectionError_brokenPipeIsRecoverable() {
        assertTrue(figmaClient.isRecoverableConnectionError(new IOException("Broken pipe")));
    }

    // ------------------------------------------------------------------
    // me / testConnection / meMCP
    // ------------------------------------------------------------------

    @Test
    public void testMe_successWithEmail() throws Exception {
        doReturn("{\"id\": \"u1\", \"handle\": \"jdoe\", \"email\": \"jdoe@example.com\"}")
                .when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.me();

        assertEquals(true, result.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertEquals("u1", user.get("id"));
        assertEquals("jdoe", user.get("handle"));
        assertEquals("jdoe@example.com", user.get("email"));
    }

    @Test
    public void testMe_successWithoutEmail() throws Exception {
        doReturn("{\"id\": \"u1\", \"handle\": \"jdoe\"}")
                .when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.me();

        assertEquals(true, result.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertFalse(user.containsKey("email"));
    }

    @Test
    public void testMe_unexpectedResponseFormat() throws Exception {
        doReturn("{\"something\": \"else\"}").when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.me();

        assertEquals(false, result.get("success"));
        assertEquals("Unexpected response format from Figma API", result.get("message"));
    }

    @Test
    public void testMe_emptyResponse() throws Exception {
        doReturn("").when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.me();

        assertEquals(false, result.get("success"));
        assertEquals("Empty response from Figma API", result.get("message"));
    }

    @Test
    public void testMe_exception() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.me();

        assertEquals(false, result.get("success"));
        assertTrue(result.get("message").toString().contains("boom"));
        assertEquals("IOException", result.get("error"));
    }

    @Test
    public void testTestConnection_delegatesToMe() throws Exception {
        doReturn("{\"id\": \"u1\"}").when(figmaClient).execute(any(GenericRequest.class));

        Map<String, Object> result = figmaClient.testConnection();
        assertEquals(true, result.get("success"));
    }

    @Test
    public void testMeMCP_returnsJsonString() throws Exception {
        doReturn("{\"id\": \"u1\", \"handle\": \"jdoe\"}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String result = figmaClient.meMCP();
        JSONObject json = new JSONObject(result);
        assertEquals(true, json.getBoolean("success"));
        assertTrue(json.has("user"));
    }

    // ------------------------------------------------------------------
    // OAuth2 helpers (error branches + URL building; no token exchange calls)
    // ------------------------------------------------------------------

    @Test
    public void testOauth2GetAuthUrl_missingClientId() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "");
        PropertyReader.setOverrides(overrides);
        String result = figmaClient.oauth2GetAuthUrl("http://localhost/cb", "state1", null);
        assertTrue(new JSONObject(result).getString("error").contains("FIGMA_CLIENT_ID"));
    }

    @Test
    public void testOauth2GetAuthUrl_missingClientSecret() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "cid");
        overrides.put("FIGMA_CLIENT_SECRET", "");
        PropertyReader.setOverrides(overrides);

        String result = figmaClient.oauth2GetAuthUrl("http://localhost/cb", "state1", null);
        assertTrue(new JSONObject(result).getString("error").contains("FIGMA_CLIENT_SECRET"));
    }

    @Test
    public void testOauth2GetAuthUrl_missingRedirectUri() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "cid");
        overrides.put("FIGMA_CLIENT_SECRET", "secret");
        overrides.put("FIGMA_REDIRECT_URI", "");
        PropertyReader.setOverrides(overrides);

        String result = figmaClient.oauth2GetAuthUrl(null, "state1", null);
        assertTrue(new JSONObject(result).getString("error").contains("redirectUri"));
    }

    @Test
    public void testOauth2GetAuthUrl_success() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "cid");
        overrides.put("FIGMA_CLIENT_SECRET", "secret");
        overrides.put("FIGMA_SCOPE", "file_content:read");
        PropertyReader.setOverrides(overrides);

        String result = figmaClient.oauth2GetAuthUrl("http://localhost/cb", "state1", null);
        JSONObject json = new JSONObject(result);
        String authUrl = json.getString("authorization_url");
        assertTrue(authUrl.startsWith("https://www.figma.com/oauth"));
        assertTrue(authUrl.contains("client_id=cid"));
        assertTrue(authUrl.contains("state=state1"));
        assertEquals("state1", json.getString("state"));
    }

    @Test
    public void testOauth2GetAuthUrl_generatesStateWhenMissing() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "cid");
        overrides.put("FIGMA_CLIENT_SECRET", "secret");
        PropertyReader.setOverrides(overrides);

        String result = figmaClient.oauth2GetAuthUrl("http://localhost/cb", null, "file_content:read");
        JSONObject json = new JSONObject(result);
        assertFalse(json.getString("state").isEmpty());
        assertTrue(json.getString("authorization_url").contains("response_type=code"));
    }

    @Test
    public void testOauth2ExchangeCode_missingConfig() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "");
        overrides.put("FIGMA_CLIENT_SECRET", "");
        PropertyReader.setOverrides(overrides);
        String result = figmaClient.oauth2ExchangeCode("code123", "http://localhost/cb");
        assertTrue(new JSONObject(result).getString("error").contains("FIGMA_CLIENT_ID"));
    }

    @Test
    public void testOauth2ExchangeCode_missingRedirectUri() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("FIGMA_CLIENT_ID", "cid");
        overrides.put("FIGMA_CLIENT_SECRET", "secret");
        overrides.put("FIGMA_REDIRECT_URI", "");
        PropertyReader.setOverrides(overrides);

        String result = figmaClient.oauth2ExchangeCode("code123", null);
        assertTrue(new JSONObject(result).getString("error").contains("redirectUri"));
    }

    // ------------------------------------------------------------------
    // getImageOfSource / convertUrlToFile / uriToObject
    // ------------------------------------------------------------------

    @Test
    public void testGetImageOfSource_success() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/img.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String imageUrl = figmaClient.getImageOfSource(NODE_URL);
        assertEquals("https://s3.example.com/img.png", imageUrl);
    }

    @Test
    public void testGetImageOfSource_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getImageOfSource(NODE_URL));
    }

    @Test
    public void testConvertUrlToFile_nullImageSource() throws Exception {
        doReturn(null).when(figmaClient).getImageOfSource(anyString());
        assertNull(figmaClient.convertUrlToFile(NODE_URL));
    }

    @Test
    public void testConvertUrlToFile_nonHttpImageSource() throws Exception {
        doReturn("not-a-url").when(figmaClient).getImageOfSource(anyString());
        assertNull(figmaClient.convertUrlToFile(NODE_URL));
    }

    @Test
    public void testConvertUrlToFile_success() throws Exception {
        File expected = new File("image.png");
        doReturn("https://s3.example.com/img.png").when(figmaClient).getImageOfSource(anyString());
        doReturn(expected).when(figmaClient).downloadImage("https://s3.example.com/img.png");

        assertEquals(expected, figmaClient.convertUrlToFile(NODE_URL));
    }

    @Test
    public void testUriToObject_validUrl() throws Exception {
        File expected = new File("image.png");
        doReturn(expected).when(figmaClient).convertUrlToFile(anyString());
        assertEquals(expected, figmaClient.uriToObject(NODE_URL));
    }

    @Test
    public void testUriToObject_invalidUrl() throws Exception {
        assertNull(figmaClient.uriToObject("https://example.com/not-figma"));
    }

    @Test
    public void testParseUris() throws Exception {
        Set<String> uris = figmaClient.parseUris("see https://www.figma.com/file/abc/Design?node-id=1:2 for details");
        assertEquals(1, uris.size());
        assertTrue(uris.iterator().next().contains("figma.com/file/abc"));
    }

    // ------------------------------------------------------------------
    // downloadNodeImage / downloadImage / getCachedFile / downloadImageAsBase64
    // ------------------------------------------------------------------

    @Test
    public void testDownloadNodeImage_defaultsAndSuccess() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/node.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));
        File expected = new File("node.png");
        doReturn(expected).when(figmaClient).downloadImage("https://s3.example.com/node.png");

        File result = figmaClient.downloadNodeImage(NODE_URL, "1:2", null, null);
        assertEquals(expected, result);
    }

    @Test
    public void testDownloadNodeImage_explicitFormatAndScale() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/node.jpg\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));
        File expected = new File("node.jpg");
        doReturn(expected).when(figmaClient).downloadImage("https://s3.example.com/node.jpg");

        File result = figmaClient.downloadNodeImage(NODE_URL, "1:2", "jpg", 4);
        assertEquals(expected, result);
    }

    @Test
    public void testDownloadNodeImage_noImagesObjectReturnsNull() throws Exception {
        doReturn("{\"err\": \"not found\"}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.downloadNodeImage(NODE_URL, "1:2", "png", 2));
    }

    @Test
    public void testDownloadNodeImage_nodeMissingReturnsNull() throws Exception {
        doReturn("{\"images\": {\"9:9\": \"https://s3.example.com/other.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.downloadNodeImage(NODE_URL, "1:2", "png", 2));
    }

    @Test
    public void testGetCachedFile_imageUrlGetsPngSuffix() {
        GenericRequest request = new GenericRequest(figmaClient, BASE_PATH + "/images/FILEID123");
        File cached = figmaClient.getCachedFile(request);
        assertTrue(cached.getName().endsWith(".png"));
        assertTrue(cached.getParentFile().exists());
    }

    @Test
    public void testGetCachedFile_nonImageUrlNoSuffix() {
        GenericRequest request = new GenericRequest(figmaClient, BASE_PATH + "/me");
        File cached = figmaClient.getCachedFile(request);
        assertFalse(cached.getName().endsWith(".png"));
    }

    @Test
    public void testDownloadImage_returnsCachedFileWhenExists() throws Exception {
        File cachedFile = Files.createTempFile("figma-cache", ".png").toFile();
        cachedFile.deleteOnExit();
        doReturn(cachedFile).when(figmaClient).getCachedFile(any(GenericRequest.class));

        File result = figmaClient.downloadImage("https://s3.example.com/img.png");
        assertEquals(cachedFile, result);
        verify(figmaClient, never()).getClient();
    }

    @Test
    public void testDownloadImage_downloadsOverHttp() throws Exception {
        File target = new File(Files.createTempDirectory("figma-dl").toFile(), "img.png");
        doReturn(target).when(figmaClient).getCachedFile(any(GenericRequest.class));

        OkHttpClient mockClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        ResponseBody body = ResponseBody.create("pngdata".getBytes(StandardCharsets.UTF_8),
                MediaType.get("image/png"));
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://s3.example.com/img.png").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(mockClient.connectionPool()).thenReturn(new ConnectionPool());
        doReturn(mockClient).when(figmaClient).getClient();

        File result = figmaClient.downloadImage("https://s3.example.com/img.png");
        assertEquals(target, result);
        assertEquals("pngdata", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void testDownloadImage_httpErrorThrows() throws Exception {
        File target = new File(Files.createTempDirectory("figma-dl-err").toFile(), "img.png");
        doReturn(target).when(figmaClient).getCachedFile(any(GenericRequest.class));

        OkHttpClient mockClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        ResponseBody body = ResponseBody.create("nope".getBytes(StandardCharsets.UTF_8),
                MediaType.get("text/plain"));
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://s3.example.com/img.png").build())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body(body)
                .build();
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(mockClient.connectionPool()).thenReturn(new ConnectionPool());
        doReturn(mockClient).when(figmaClient).getClient();

        try {
            figmaClient.downloadImage("https://s3.example.com/img.png");
            fail("Expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("404"));
        }
    }

    @Test
    public void testDownloadImageAsBase64() throws Exception {
        File png = File.createTempFile("figma-img", ".png");
        png.deleteOnExit();
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", png);
        doReturn(png).when(figmaClient).downloadImage(anyString());

        String base64 = figmaClient.downloadImageAsBase64("https://s3.example.com/img.png");
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
    }

    // ------------------------------------------------------------------
    // parseFileId / extractValueByParameter / isValidImageUrl
    // ------------------------------------------------------------------

    @Test(expected = RuntimeException.class)
    public void testParseFileId_invalidUriThrowsRuntimeException() {
        figmaClient.parseFileId("ht tp://invalid uri with spaces");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExtractValueByParameter_missingParamThrows() {
        FigmaClient.extractValueByParameter(FILE_URL + "?other=1", "node-id");
    }

    @Test
    public void testExtractValueByParameter_skipsParamsWithoutValue() {
        String url = FILE_URL + "?flag&node-id=3-4";
        assertEquals("3-4", FigmaClient.extractValueByParameter(url, "node-id"));
    }

    @Test(expected = RuntimeException.class)
    public void testExtractValueByParameter_invalidUriThrowsRuntimeException() {
        FigmaClient.extractValueByParameter("ht tp://bad uri?node-id=1", "node-id");
    }

    @Test
    public void testIsValidImageUrl_fileLink() {
        assertTrue(figmaClient.isValidImageUrl(FILE_URL + "?node-id=1:2"));
    }

    @Test
    public void testIsValidImageUrl_designLink() {
        assertTrue(figmaClient.isValidImageUrl("https://www.figma.com/design/abc/Name?node-id=1-2"));
    }

    @Test
    public void testIsValidImageUrl_figmaLinkWithoutNodeId() {
        assertFalse(figmaClient.isValidImageUrl(FILE_URL));
    }

    @Test
    public void testIsValidImageUrl_nonFigmaLink() {
        assertFalse(figmaClient.isValidImageUrl("https://example.com/image.png"));
    }

    // ------------------------------------------------------------------
    // getFileStructure / getIcons
    // ------------------------------------------------------------------

    @Test
    public void testGetFileStructure_fullFile() throws Exception {
        doReturn("{\"name\": \"Design\", \"document\": {\"id\": \"0:0\", \"type\": \"DOCUMENT\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        FigmaFileResponse response = figmaClient.getFileStructure(FILE_URL);
        assertNotNull(response);
        assertEquals("Design", response.getName());
    }

    @Test
    public void testGetFileStructure_specificNode() throws Exception {
        doReturn("{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"FRAME\", \"name\": \"Frame 1\"}}}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        FigmaFileResponse response = figmaClient.getFileStructure(NODE_URL);
        assertNotNull(response);
    }

    @Test
    public void testGetFileStructure_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getFileStructure(FILE_URL));
    }

    @Test
    public void testGetIcons_nullFileStructure() throws Exception {
        doReturn(null).when(figmaClient).getFileStructure(anyString());
        assertNull(figmaClient.getIcons(FILE_URL));
    }

    @Test
    public void testGetIcons_successWithDeduplication() throws Exception {
        String json = "{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"FRAME\", \"name\": \"Root\", "
                + "\"children\": ["
                + "{\"id\": \"1:3\", \"type\": \"VECTOR\", \"name\": \"icon-a\", \"absoluteBoundingBox\": {\"width\": 10, \"height\": 10}},"
                + "{\"id\": \"1:3\", \"type\": \"VECTOR\", \"name\": \"icon-a-dup\", \"absoluteBoundingBox\": {\"width\": 10, \"height\": 10}},"
                + "{\"id\": \"1:4\", \"type\": \"RECTANGLE\", \"name\": \"icon-b\", \"absoluteBoundingBox\": {\"width\": 20, \"height\": 20}}"
                + "]}}}}}";
        doReturn(new FigmaFileResponse(json)).when(figmaClient).getFileStructure(anyString());

        FigmaIconsResult result = figmaClient.getIcons(FILE_URL);
        assertNotNull(result);
        assertEquals("FILEID123", result.getString("fileId"));
        assertEquals(2, result.getInt("totalIcons"));
    }

    @Test
    public void testGetIcons_exceptionReturnsNull() throws Exception {
        doThrow(new RuntimeException("boom")).when(figmaClient).getFileStructure(anyString());
        assertNull(figmaClient.getIcons(FILE_URL));
    }

    // ------------------------------------------------------------------
    // getImageById (via getImageUrlForIcon)
    // ------------------------------------------------------------------

    @Test
    public void testGetImageById_pngSuccess() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/icon.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String url = figmaClient.getImageById(FILE_URL, "1-2", "png");
        assertEquals("https://s3.example.com/icon.png", url);
    }

    @Test
    public void testGetImageById_svgSuccess() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/icon.svg\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String url = figmaClient.getImageById(FILE_URL, "1:2", "svg");
        assertEquals("https://s3.example.com/icon.svg", url);
    }

    @Test
    public void testGetImageById_nodeNotInResponseReturnsNull() throws Exception {
        doReturn("{\"images\": {\"9:9\": \"https://s3.example.com/other.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getImageById(FILE_URL, "1:2", "png"));
    }

    @Test
    public void testGetImageById_executeExceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getImageById(FILE_URL, "1:2", "png"));
    }

    @Test(expected = RuntimeException.class)
    public void testGetImageById_invalidHrefThrows() throws Exception {
        figmaClient.getImageById("ht tp://bad uri", "1:2", "png");
    }

    // ------------------------------------------------------------------
    // getImageFills / renderNodes
    // ------------------------------------------------------------------

    @Test
    public void testGetImageFills_success() throws Exception {
        doReturn("{\"meta\": {\"images\": {\"ref1\": \"https://s3.example.com/fill.png\"}}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String result = figmaClient.getImageFills(FILE_URL);
        assertTrue(result.contains("fill.png"));
    }

    @Test
    public void testGetImageFills_exceptionRethrown() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        try {
            figmaClient.getImageFills(FILE_URL);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    @Test
    public void testRenderNodes_success() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/a.png\", \"3:4\": \"https://s3.example.com/b.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        String result = figmaClient.renderNodes(FILE_URL, "1:2,3:4", null);
        JSONObject json = new JSONObject(result);
        assertEquals("https://s3.example.com/a.png", json.getString("1:2"));
        assertEquals("https://s3.example.com/b.png", json.getString("3:4"));
    }

    @Test
    public void testRenderNodes_batchesOver100() throws Exception {
        doReturn("{\"images\": {\"1:2\": \"https://s3.example.com/a.png\"}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("1:").append(i);
        }
        figmaClient.renderNodes(FILE_URL, ids.toString(), "svg");
        verify(figmaClient, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testRenderNodes_exceptionRethrown() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        try {
            figmaClient.renderNodes(FILE_URL, "1:2", "png");
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // downloadIconFile / getSvgContent
    // ------------------------------------------------------------------

    @Test
    public void testDownloadIconFile_noImageUrlReturnsNull() throws Exception {
        doReturn(null).when(figmaClient).getImageById(anyString(), anyString(), anyString());
        assertNull(figmaClient.downloadIconFile(FILE_URL, "1:2", "png"));
    }

    @Test
    public void testDownloadIconFile_success() throws Exception {
        File expected = new File("icon.png");
        doReturn("https://s3.example.com/icon.png").when(figmaClient).getImageById(anyString(), anyString(), anyString());
        doReturn(expected).when(figmaClient).downloadImage("https://s3.example.com/icon.png");
        assertEquals(expected, figmaClient.downloadIconFile(FILE_URL, "1:2", "png"));
    }

    @Test
    public void testDownloadIconFile_downloadExceptionReturnsNull() throws Exception {
        doReturn("https://s3.example.com/icon.png").when(figmaClient).getImageById(anyString(), anyString(), anyString());
        doThrow(new IOException("boom")).when(figmaClient).downloadImage(anyString());
        assertNull(figmaClient.downloadIconFile(FILE_URL, "1:2", "png"));
    }

    @Test
    public void testGetSvgContent_noSvgUrlReturnsNull() throws Exception {
        doReturn(null).when(figmaClient).getImageById(anyString(), anyString(), anyString());
        assertNull(figmaClient.getSvgContent(FILE_URL, "1:2"));
    }

    @Test
    public void testGetSvgContent_success() throws Exception {
        doReturn("https://s3.example.com/icon.svg").when(figmaClient).getImageById(anyString(), anyString(), eq("svg"));
        doReturn("<svg></svg>").when(figmaClient).execute(any(GenericRequest.class));
        assertEquals("<svg></svg>", figmaClient.getSvgContent(FILE_URL, "1:2"));
    }

    @Test
    public void testGetSvgContent_executeExceptionReturnsNull() throws Exception {
        doReturn("https://s3.example.com/icon.svg").when(figmaClient).getImageById(anyString(), anyString(), eq("svg"));
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getSvgContent(FILE_URL, "1:2"));
    }

    // ------------------------------------------------------------------
    // getNodeDetails
    // ------------------------------------------------------------------

    @Test
    public void testGetNodeDetails_success() throws Exception {
        doReturn("{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"FRAME\", \"name\": \"Frame\"}}}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        FigmaNodeDetails details = figmaClient.getNodeDetails(FILE_URL, "1:2");
        assertNotNull(details);
    }

    @Test
    public void testGetNodeDetails_limitsTo10Nodes() throws Exception {
        doReturn("{\"nodes\": {\"1:0\": {\"document\": {\"id\": \"1:0\", \"type\": \"FRAME\"}}}}")
                .when(figmaClient).execute(any(GenericRequest.class));

        figmaClient.getNodeDetails(FILE_URL, " 1:0 ,1:1,1:2,1:3,1:4,1:5,1:6,1:7,1:8,1:9,1:10,1:11");
        verify(figmaClient, times(1)).execute(any(GenericRequest.class));
    }

    @Test
    public void testGetNodeDetails_noNodeDataReturnsNull() throws Exception {
        doReturn("{\"nodes\": {}}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getNodeDetails(FILE_URL, "1:2"));
    }

    @Test
    public void testGetNodeDetails_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getNodeDetails(FILE_URL, "1:2"));
    }

    // ------------------------------------------------------------------
    // getTextContent
    // ------------------------------------------------------------------

    @Test
    public void testGetTextContent_success() throws Exception {
        String response = "{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"TEXT\", "
                + "\"characters\": \"Hello\", "
                + "\"style\": {\"fontFamily\": \"Inter\", \"fontSize\": 12.0, \"fontWeight\": 400, "
                + "\"lineHeightPx\": 16.0, \"letterSpacing\": 0.5, \"textAlignHorizontal\": \"CENTER\"}, "
                + "\"characterStyleOverrides\": [0, 1], "
                + "\"styleOverrideTable\": {\"0\": {\"fontSize\": 8}}}}, "
                + "\"1:3\": {\"document\": {\"id\": \"1:3\", \"type\": \"FRAME\"}}}}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        FigmaTextContentResult result = figmaClient.getTextContent(FILE_URL, "1:2,1:3");
        assertNotNull(result);
        assertEquals(1, result.getJSONObject("textNodes").length());
    }

    @Test
    public void testGetTextContent_limitsTo20Nodes() throws Exception {
        doReturn("{\"nodes\": {}}").when(figmaClient).execute(any(GenericRequest.class));

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append(" 1:").append(i).append(" ");
        }
        FigmaTextContentResult result = figmaClient.getTextContent(FILE_URL, ids.toString());
        assertNotNull(result);
        assertEquals(0, result.getJSONObject("textNodes").length());
    }

    @Test
    public void testGetTextContent_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getTextContent(FILE_URL, "1:2"));
    }

    // ------------------------------------------------------------------
    // getStyles
    // ------------------------------------------------------------------

    @Test
    public void testGetStyles_success() throws Exception {
        doReturn("{\"meta\": {}}").when(figmaClient).execute(any(GenericRequest.class));

        FigmaStylesResult result = figmaClient.getStyles(FILE_URL);
        assertNotNull(result);
        assertEquals(0, result.getJSONArray("colorStyles").length());
        assertEquals(0, result.getJSONArray("textStyles").length());
    }

    @Test
    public void testGetStyles_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getStyles(FILE_URL));
    }

    // ------------------------------------------------------------------
    // getLayers / getLayersBatch / getNodeChildren
    // ------------------------------------------------------------------

    private static final String LAYERS_RESPONSE = "{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", "
            + "\"type\": \"FRAME\", \"children\": ["
            + "{\"id\": \"1:3\", \"name\": \"Header\", \"type\": \"FRAME\", "
            + "\"absoluteBoundingBox\": {\"width\": 100, \"height\": 50, \"x\": 0, \"y\": 0}, \"visible\": true},"
            + "{\"id\": \"1:4\", \"name\": \"Body\", \"type\": \"TEXT\", \"visible\": false}"
            + "]}}}}";

    @Test
    public void testGetLayers_success() throws Exception {
        doReturn(LAYERS_RESPONSE).when(figmaClient).execute(any(GenericRequest.class));

        FigmaNodeChildrenResult result = figmaClient.getLayers(NODE_URL);
        assertNotNull(result);
        assertEquals("1-2", result.getString("parentNodeId"));
        assertEquals(2, result.getJSONArray("children").length());
    }

    @Test
    public void testGetLayers_noNodesObjectReturnsNull() throws Exception {
        doReturn("{\"err\": \"x\"}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getLayers(NODE_URL));
    }

    @Test
    public void testGetLayers_nodeNotFoundReturnsNull() throws Exception {
        doReturn("{\"nodes\": {\"9:9\": {}}}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getLayers(NODE_URL));
    }

    @Test
    public void testGetLayers_noDocumentReturnsNull() throws Exception {
        doReturn("{\"nodes\": {\"1:2\": {}}}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getLayers(NODE_URL));
    }

    @Test
    public void testGetLayers_noChildrenReturnsNull() throws Exception {
        doReturn("{\"nodes\": {\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"FRAME\"}}}}")
                .when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getLayers(NODE_URL));
    }

    @Test
    public void testGetLayers_exceptionReturnsNull() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getLayers(NODE_URL));
    }

    @Test
    public void testGetLayersBatch_success() throws Exception {
        String response = "{\"nodes\": {"
                + "\"1:2\": {\"document\": {\"id\": \"1:2\", \"type\": \"FRAME\", \"children\": ["
                + "{\"id\": \"1:3\", \"name\": \"Child\", \"type\": \"FRAME\", "
                + "\"absoluteBoundingBox\": {\"width\": 10, \"height\": 10, \"x\": 1, \"y\": 2}}]}}, "
                + "\"5:6\": {\"document\": {\"id\": \"5:6\", \"type\": \"FRAME\"}}}}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        Map<String, FigmaNodeChildrenResult> results = figmaClient.getLayersBatch(FILE_URL, "1-2, 5-6, 7-8");
        assertEquals(1, results.size());
        assertTrue(results.containsKey("1:2"));
    }

    @Test
    public void testGetLayersBatch_limitsTo10Nodes() throws Exception {
        doReturn("{\"nodes\": {}}").when(figmaClient).execute(any(GenericRequest.class));

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("1:").append(i);
        }
        Map<String, FigmaNodeChildrenResult> results = figmaClient.getLayersBatch(FILE_URL, ids.toString());
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetLayersBatch_noNodesObjectReturnsEmptyMap() throws Exception {
        doReturn("{\"err\": \"x\"}").when(figmaClient).execute(any(GenericRequest.class));
        assertTrue(figmaClient.getLayersBatch(FILE_URL, "1:2").isEmpty());
    }

    @Test
    public void testGetLayersBatch_exceptionReturnsEmptyMap() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        assertTrue(figmaClient.getLayersBatch(FILE_URL, "1:2").isEmpty());
    }

    @Test
    public void testGetNodeChildren_success() throws Exception {
        String response = "{\"nodes\": {\"1-2\": {\"document\": {\"id\": \"1-2\", \"type\": \"FRAME\", \"children\": ["
                + "{\"id\": \"1:3\", \"name\": \"Child\", \"type\": \"FRAME\", "
                + "\"absoluteBoundingBox\": {\"width\": 10, \"height\": 10, \"x\": 1, \"y\": 2}, \"visible\": true}"
                + "]}}}}";
        doReturn(response).when(figmaClient).execute(any(GenericRequest.class));

        FigmaNodeChildrenResult result = figmaClient.getNodeChildren(NODE_URL);
        assertNotNull(result);
        assertEquals("1-2", result.getString("parentNodeId"));
        assertEquals(1, result.getJSONArray("children").length());
    }

    @Test
    public void testGetNodeChildren_noNodesReturnsNull() throws Exception {
        doReturn("{\"err\": \"x\"}").when(figmaClient).execute(any(GenericRequest.class));
        assertNull(figmaClient.getNodeChildren(NODE_URL));
    }

    @Test
    public void testGetNodeChildren_exceptionRethrown() throws Exception {
        doThrow(new IOException("boom")).when(figmaClient).execute(any(GenericRequest.class));
        try {
            figmaClient.getNodeChildren(NODE_URL);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }
}
