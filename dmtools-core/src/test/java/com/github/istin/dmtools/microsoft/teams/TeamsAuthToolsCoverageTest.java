// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.utils.PropertyReader;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TeamsAuthTools.
 *
 * TeamsAuthTools persists pending device-code auth state under
 * {@code user.home/.dmtools/.teams-pending-auth.json} in a static final field
 * initialized at class-load time, and builds an OkHttp client inline. To keep
 * tests hermetic without ever touching the real home directory:
 *
 * <ul>
 *   <li>{@code user.home} is redirected to a temp directory, and TeamsAuthTools
 *       is loaded through an isolating child class loader that re-defines only
 *       that one class. Its static PENDING_AUTH_FILE is therefore computed
 *       from the temp home, even if another test already initialized
 *       TeamsAuthTools in the system class loader. A CodeSource is supplied
 *       to defineClass so the JaCoCo agent instruments the re-defined class
 *       (classes without source location are skipped by default).</li>
 *   <li>All other classes (PropertyReader, okhttp3, JSONObject,
 *       BasicTeamsClient) are delegated to the parent loader, so
 *       {@link MockedConstruction} of {@link PropertyReader} and
 *       {@link OkHttpClient.Builder} applies to the isolated class as usual.</li>
 * </ul>
 */
public class TeamsAuthToolsCoverageTest {

    private static final String TEAMS_AUTH_TOOLS_CLASS =
        "com.github.istin.dmtools.microsoft.teams.TeamsAuthTools";

    private static File tempHome;
    private static String originalUserHome;
    private static Class<?> teamsAuthToolsClass;
    private static String pendingAuthFilePath;

    private Object tools;
    private MockedConstruction<PropertyReader> mockedPropertyReader;
    private MockedConstruction<OkHttpClient.Builder> mockedHttpBuilder;

    /**
     * Re-defines TeamsAuthTools itself so its static state (PENDING_AUTH_FILE)
     * is initialized under the redirected user.home; delegates every other
     * class to the parent loader.
     */
    private static final class IsolatingClassLoader extends ClassLoader {
        IsolatingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (TEAMS_AUTH_TOOLS_CLASS.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String resource = name.replace('.', '/') + ".class";
                        URL url = getParent().getResource(resource);
                        if (url == null) {
                            throw new ClassNotFoundException(name);
                        }
                        try (InputStream in = url.openStream()) {
                            byte[] bytes = in.readAllBytes();
                            // A CodeSource is required so JaCoCo instruments the
                            // re-defined class (classes without source location
                            // are skipped by the agent by default).
                            CodeSource codeSource = new CodeSource(url, (CodeSigner[]) null);
                            loaded = defineClass(name, bytes, 0, bytes.length,
                                new ProtectionDomain(codeSource, null));
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        originalUserHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("teams-auth-tools-test").toFile();
        System.setProperty("user.home", tempHome.getAbsolutePath());
        // Load TeamsAuthTools through the isolating loader while user.home
        // points at the temp dir; its static PENDING_AUTH_FILE is computed
        // from user.home at class initialization.
        IsolatingClassLoader loader =
            new IsolatingClassLoader(TeamsAuthToolsCoverageTest.class.getClassLoader());
        teamsAuthToolsClass = Class.forName(TEAMS_AUTH_TOOLS_CLASS, true, loader);
        Field field = teamsAuthToolsClass.getDeclaredField("PENDING_AUTH_FILE");
        field.setAccessible(true);
        pendingAuthFilePath = (String) field.get(null);
    }

    @AfterClass
    public static void tearDownClass() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
        deleteRecursively(tempHome);
    }

    @Before
    public void setUp() throws Exception {
        // Safety guard: never read/write/delete files outside the temp dir.
        Assume.assumeTrue("PENDING_AUTH_FILE was not redirected to the temp home",
            pendingAuthFilePath.startsWith(tempHome.getAbsolutePath()));
        deletePendingFile();
        tools = teamsAuthToolsClass.getDeclaredConstructor().newInstance();
    }

    @After
    public void tearDown() {
        if (mockedPropertyReader != null) {
            mockedPropertyReader.close();
        }
        if (mockedHttpBuilder != null) {
            mockedHttpBuilder.close();
        }
        deletePendingFile();
        // Clear any interrupt flag set by interruption tests
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Invokes a no-arg tool method on the isolated TeamsAuthTools instance. */
    private String invokeTool(String methodName) throws IOException {
        try {
            return (String) teamsAuthToolsClass.getMethod(methodName).invoke(tools);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void mockPropertyReader(String clientId, String tenantId, String refreshToken,
                                    String tokenCachePath) {
        mockedPropertyReader = Mockito.mockConstruction(PropertyReader.class, (mock, context) -> {
            when(mock.getTeamsClientId()).thenReturn(clientId);
            when(mock.getTeamsTenantId()).thenReturn(tenantId);
            when(mock.getTeamsScopes()).thenReturn("User.Read Chat.Read");
            when(mock.getTeamsRefreshToken()).thenReturn(refreshToken);
            when(mock.getTeamsTokenCachePath()).thenReturn(tokenCachePath);
        });
    }

    private void mockHttpClient(boolean successful, String responseBody, int code, String message)
            throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response response = mock(Response.class);
        ResponseBody body = mock(ResponseBody.class);

        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(successful);
        when(response.code()).thenReturn(code);
        when(response.message()).thenReturn(message);
        when(response.body()).thenReturn(body);
        when(body.string()).thenReturn(responseBody);

        mockedHttpBuilder = Mockito.mockConstruction(OkHttpClient.Builder.class, (mock, context) -> {
            when(mock.connectTimeout(anyLong(), any(TimeUnit.class))).thenReturn(mock);
            when(mock.readTimeout(anyLong(), any(TimeUnit.class))).thenReturn(mock);
            when(mock.build()).thenReturn(client);
        });
    }

    private void writePendingAuth(String deviceCode, long timestamp) throws IOException {
        JSONObject state = new JSONObject();
        state.put("device_code", deviceCode);
        state.put("client_id", "client-1");
        state.put("tenant_id", "tenant-1");
        state.put("timestamp", timestamp);
        File file = new File(pendingAuthFilePath);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), state.toString().getBytes());
    }

    private void deletePendingFile() {
        new File(pendingAuthFilePath).delete();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    // ------------------------------------------------------------------
    // checkAuthStatus
    // ------------------------------------------------------------------

    @Test
    public void testCheckAuthStatus_NothingConfigured() throws IOException {
        mockPropertyReader(null, null, null, null);

        JSONObject result = new JSONObject(invokeTool("checkAuthStatus"));

        assertFalse(result.getBoolean("client_id_configured"));
        assertFalse(result.getBoolean("refresh_token_configured"));
        assertFalse(result.getBoolean("pending_device_auth"));
        assertTrue(result.getString("message").contains("TEAMS_CLIENT_ID not configured"));
    }

    @Test
    public void testCheckAuthStatus_BlankClientId() throws IOException {
        mockPropertyReader("   ", null, "  ", null);

        JSONObject result = new JSONObject(invokeTool("checkAuthStatus"));

        assertFalse(result.getBoolean("client_id_configured"));
        assertFalse(result.getBoolean("refresh_token_configured"));
        assertTrue(result.getString("message").contains("TEAMS_CLIENT_ID not configured"));
    }

    @Test
    public void testCheckAuthStatus_ClientIdWithoutRefreshToken() throws IOException {
        mockPropertyReader("client-1", "tenant-1", null, "./teams.token");

        JSONObject result = new JSONObject(invokeTool("checkAuthStatus"));

        assertTrue(result.getBoolean("client_id_configured"));
        assertFalse(result.getBoolean("refresh_token_configured"));
        assertEquals("./teams.token", result.getString("token_cache_path"));
        assertTrue(result.getString("message").contains("No refresh token configured"));
    }

    @Test
    public void testCheckAuthStatus_FullyConfigured() throws IOException {
        mockPropertyReader("client-1", "tenant-1", "refresh-1", "./teams.token");

        JSONObject result = new JSONObject(invokeTool("checkAuthStatus"));

        assertTrue(result.getBoolean("client_id_configured"));
        assertTrue(result.getBoolean("refresh_token_configured"));
        assertTrue(result.getString("message").contains("Authentication configured"));
    }

    @Test
    public void testCheckAuthStatus_PendingDeviceAuthPresent() throws IOException {
        writePendingAuth("dc-123", System.currentTimeMillis());
        mockPropertyReader("client-1", "tenant-1", "refresh-1", "./teams.token");

        JSONObject result = new JSONObject(invokeTool("checkAuthStatus"));

        assertTrue(result.getBoolean("pending_device_auth"));
    }

    // ------------------------------------------------------------------
    // startDeviceCodeAuth
    // ------------------------------------------------------------------

    @Test
    public void testStartDeviceCodeAuth_MissingClientId() {
        mockPropertyReader(null, "tenant-1", null, null);

        try {
            invokeTool("startDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("TEAMS_CLIENT_ID not configured"));
        }
    }

    @Test
    public void testStartDeviceCodeAuth_BlankClientId() {
        mockPropertyReader("  ", "tenant-1", null, null);

        try {
            invokeTool("startDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("TEAMS_CLIENT_ID not configured"));
        }
    }

    @Test
    public void testStartDeviceCodeAuth_HttpFailure() throws IOException {
        mockPropertyReader("client-1", "tenant-1", null, null);
        mockHttpClient(false, "{\"error\":\"invalid_client\"}", 400, "Bad Request");

        try {
            invokeTool("startDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Device code request failed: 400 Bad Request"));
        }
    }

    @Test
    public void testStartDeviceCodeAuth_Success() throws IOException {
        mockPropertyReader("client-1", "tenant-1", null, null);
        String deviceCodeResponse = new JSONObject()
            .put("device_code", "dc-123")
            .put("user_code", "ABCD-EFGH")
            .put("verification_uri", "https://microsoft.com/devicelogin")
            .put("expires_in", 900)
            .put("interval", 7)
            .toString();
        mockHttpClient(true, deviceCodeResponse, 200, "OK");

        JSONObject result = new JSONObject(invokeTool("startDeviceCodeAuth"));

        assertEquals("https://microsoft.com/devicelogin", result.getString("verification_url"));
        assertEquals("ABCD-EFGH", result.getString("user_code"));
        assertEquals(900, result.getInt("expires_in"));
        assertEquals(7, result.getInt("interval"));
        assertTrue(result.getString("message").contains("ABCD-EFGH"));

        // Pending auth state must be persisted for teams_auth_complete
        File pendingFile = new File(pendingAuthFilePath);
        assertTrue("Pending auth file should be saved", pendingFile.exists());
        JSONObject saved = new JSONObject(new String(Files.readAllBytes(pendingFile.toPath())));
        assertEquals("dc-123", saved.getString("device_code"));
        assertEquals("client-1", saved.getString("client_id"));
        assertEquals("tenant-1", saved.getString("tenant_id"));
        assertTrue(saved.getLong("timestamp") > 0);
    }

    // ------------------------------------------------------------------
    // completeDeviceCodeAuth
    // ------------------------------------------------------------------

    @Test
    public void testCompleteDeviceCodeAuth_NoPendingAuth() {
        try {
            invokeTool("completeDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("No pending authentication"));
        }
    }

    @Test
    public void testCompleteDeviceCodeAuth_ExpiredPendingAuth() throws IOException {
        // Older than 15 minutes -> state is discarded and file deleted
        writePendingAuth("dc-old", System.currentTimeMillis() - 16 * 60 * 1000L);

        try {
            invokeTool("completeDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("No pending authentication"));
        }
        assertFalse("Expired pending auth file should be deleted",
            new File(pendingAuthFilePath).exists());
    }

    @Test
    public void testCompleteDeviceCodeAuth_CorruptPendingFile() throws IOException {
        File file = new File(pendingAuthFilePath);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "not a json".getBytes());

        try {
            invokeTool("completeDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("No pending authentication"));
        }
    }

    @Test
    public void testCompleteDeviceCodeAuth_SuccessWithRefreshToken() throws IOException {
        writePendingAuth("dc-123", System.currentTimeMillis());
        String tokenResponse = new JSONObject()
            .put("access_token", "at-1")
            .put("refresh_token", "rt-456")
            .put("expires_in", 3600)
            .toString();
        mockHttpClient(true, tokenResponse, 200, "OK");

        JSONObject result = new JSONObject(invokeTool("completeDeviceCodeAuth"));

        assertEquals("success", result.getString("status"));
        assertEquals(3600, result.getInt("expires_in"));
        assertEquals("rt-456", result.getString("refresh_token"));
        assertTrue(result.getString("instruction").contains("TEAMS_REFRESH_TOKEN"));
        assertFalse("Pending auth file should be cleared after success",
            new File(pendingAuthFilePath).exists());
    }

    @Test
    public void testCompleteDeviceCodeAuth_SuccessWithoutRefreshToken() throws IOException {
        writePendingAuth("dc-123", System.currentTimeMillis());
        String tokenResponse = new JSONObject()
            .put("access_token", "at-1")
            .put("expires_in", 3600)
            .toString();
        mockHttpClient(true, tokenResponse, 200, "OK");

        JSONObject result = new JSONObject(invokeTool("completeDeviceCodeAuth"));

        assertEquals("success", result.getString("status"));
        assertFalse(result.has("refresh_token"));
        assertFalse(result.has("instruction"));
    }

    @Test
    public void testCompleteDeviceCodeAuth_AuthenticationError() throws IOException {
        writePendingAuth("dc-123", System.currentTimeMillis());
        String errorResponse = new JSONObject()
            .put("error", "expired_token")
            .put("error_description", "The device code expired")
            .toString();
        mockHttpClient(false, errorResponse, 400, "Bad Request");

        try {
            invokeTool("completeDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("Authentication failed"));
            assertTrue(e.getMessage().contains("expired_token"));
        }
    }

    @Test
    public void testCompleteDeviceCodeAuth_AuthorizationPendingInterrupted() throws IOException {
        writePendingAuth("dc-123", System.currentTimeMillis());
        String pendingResponse = new JSONObject()
            .put("error", "authorization_pending")
            .toString();
        mockHttpClient(false, pendingResponse, 400, "Bad Request");

        // Pre-interrupt the current thread so the 5-second wait between
        // polling attempts throws immediately instead of actually sleeping.
        Thread.currentThread().interrupt();
        try {
            invokeTool("completeDeviceCodeAuth");
            fail("Expected IOException");
        } catch (IOException e) {
            assertEquals("Authentication interrupted", e.getMessage());
        } finally {
            Thread.interrupted();
        }
    }
}
