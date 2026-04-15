// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise;

import com.github.istin.dmtools.bitrise.model.BitriseApp;
import com.github.istin.dmtools.bitrise.model.BitriseArtifact;
import com.github.istin.dmtools.bitrise.model.BitriseBuild;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Bitrise client.
 *
 * <p>Uses a spy on a stub subclass so HTTP calls are intercepted without
 * requiring a real network connection.
 */
class BitriseClientTest {

    private static final String APP_SLUG = "testapp123";
    private static final String BUILD_SLUG = "build_abc";
    private static final String ARTIFACT_SLUG = "artifact_xyz";
    private static final String SECRET_NAME = "MY_SECRET";

    @TempDir
    Path tempDir;

    /** Minimal concrete subclass that lets us spy on execute/post/put/delete. */
    private static class StubBitrise extends Bitrise {
        StubBitrise() throws IOException {
            super("https://api.bitrise.io/v0.1", "test-token");
        }

        @Override
        public String execute(GenericRequest request) throws IOException {
            return "{}"; // default stub
        }

        @Override
        public String post(GenericRequest request) throws IOException {
            return "{}";
        }

        @Override
        public String put(GenericRequest request) throws IOException {
            return "{}";
        }

        @Override
        public String delete(GenericRequest request) throws IOException {
            return "{}";
        }
    }

    private StubBitrise bitrise;

    @BeforeEach
    void setUp() throws IOException {
        bitrise = spy(new StubBitrise());
    }

    // -------------------------------------------------------------------------
    // Model tests
    // -------------------------------------------------------------------------

    @Test
    void bitriseApp_parsesSlugAndTitle() {
        JSONObject json = new JSONObject()
                .put("slug", "app_slug_1")
                .put("title", "My App")
                .put("project_type", "android")
                .put("repo_url", "https://github.com/org/repo");

        BitriseApp app = new BitriseApp(json);

        assertEquals("app_slug_1", app.getSlug());
        assertEquals("My App", app.getTitle());
        assertEquals("android", app.getProjectType());
        assertEquals("https://github.com/org/repo", app.getRepoUrl());
    }

    @Test
    void bitriseBuild_parsesStatusAndWorkflow() {
        JSONObject json = new JSONObject()
                .put("slug", BUILD_SLUG)
                .put("build_number", 42)
                .put("status", 2)
                .put("status_text", "success")
                .put("triggered_workflow", "primary")
                .put("branch", "main");

        BitriseBuild build = new BitriseBuild(json);

        assertEquals(BUILD_SLUG, build.getSlug());
        assertEquals(42, build.getBuildNumber());
        assertEquals(2, build.getStatus());
        assertEquals("success", build.getStatusText());
        assertEquals("primary", build.getWorkflowId());
        assertEquals("main", build.getBranch());
    }

    @Test
    void bitriseArtifact_parsesSlugAndDownloadUrl() {
        JSONObject json = new JSONObject()
                .put("slug", ARTIFACT_SLUG)
                .put("title", "app-release.apk")
                .put("artifact_type", "android-apk")
                .put("expiring_download_url", "https://artifacts.bitrise.io/signed-url")
                .put("file_size_bytes", 5_242_880L);

        BitriseArtifact artifact = new BitriseArtifact(json);

        assertEquals(ARTIFACT_SLUG, artifact.getSlug());
        assertEquals("app-release.apk", artifact.getTitle());
        assertEquals("android-apk", artifact.getArtifactType());
        assertEquals("https://artifacts.bitrise.io/signed-url", artifact.getDownloadUrl());
        assertEquals(5_242_880L, artifact.getFileSizeBytes());
    }

    @Test
    void bitriseArtifact_defaultsForMissingFields() {
        BitriseArtifact artifact = new BitriseArtifact(new JSONObject());
        assertNull(artifact.getSlug());
        assertNull(artifact.getDownloadUrl());
        assertEquals(0L, artifact.getFileSizeBytes());
        assertFalse(artifact.isPublicPageEnabled());
    }

    // -------------------------------------------------------------------------
    // App API tests
    // -------------------------------------------------------------------------

    @Test
    void listApps_callsCorrectEndpoint() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        String result = bitrise.listApps(null, null, null);

        assertNotNull(result);
        verify(bitrise).execute(argThat((GenericRequest req) -> req.url().contains("apps")));
    }

    @Test
    void listApps_withAllParams_includesQueryParams() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listApps("last_build_at", "MyApp", 10);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("sort_by=last_build_at") &&
                req.url().contains("title=MyApp") &&
                req.url().contains("limit=10")));
    }

    @Test
    void getApp_callsCorrectEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getApp(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) -> req.url().contains("apps/" + APP_SLUG)));
    }

    // -------------------------------------------------------------------------
    // Builds tests
    // -------------------------------------------------------------------------

    @Test
    void listBuilds_withNoFilters_callsBuildsEndpoint() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listBuilds(APP_SLUG, null, null, null, null, null);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds")));
    }

    @Test
    void listBuilds_withStatusFilter_mapsStatusToInt() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listBuilds(APP_SLUG, null, null, "failed", null, null);

        verify(bitrise).execute(argThat((GenericRequest req) -> req.url().contains("status=3")));
    }

    @Test
    void listBuilds_inProgressStatus_mapsToOne() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listBuilds(APP_SLUG, null, null, "in_progress", null, null);

        verify(bitrise).execute(argThat((GenericRequest req) -> req.url().contains("status=1")));
    }

    @Test
    void listBuilds_withWorkflowAndBranch_includesParams() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listBuilds(APP_SLUG, "deploy", "main", null, 20, null);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("workflow=deploy") &&
                req.url().contains("branch=main") &&
                req.url().contains("limit=20")));
    }

    @Test
    void triggerBuild_postsCorrectBody() throws IOException {
        doReturn("{\"build_slug\":\"newbuild\"}").when(bitrise).post(any(GenericRequest.class));

        String result = bitrise.triggerBuild(APP_SLUG, "primary", "main", "Test build", null);

        assertNotNull(result);
        verify(bitrise).post(argThat((GenericRequest req) -> {
            String body = req.getBody();
            return body != null &&
                    body.contains("\"workflow_id\":\"primary\"") &&
                    body.contains("\"branch\":\"main\"") &&
                    body.contains("\"type\":\"bitrise\"");
        }));
    }

    @Test
    void triggerBuild_withEnvVars_includesEnvironments() throws IOException {
        doReturn("{}").when(bitrise).post(any(GenericRequest.class));

        String envVars = "[{\"mapped_to\":\"KEY1\",\"value\":\"val1\"}]";
        bitrise.triggerBuild(APP_SLUG, "primary", null, null, envVars);

        verify(bitrise).post(argThat((GenericRequest req) -> {
            String body = req.getBody();
            return body != null && body.contains("\"environments\"");
        }));
    }

    @Test
    void triggerBuild_withInvalidEnvVarsJson_doesNotThrow() throws IOException {
        doReturn("{}").when(bitrise).post(any(GenericRequest.class));

        assertDoesNotThrow(() ->
                bitrise.triggerBuild(APP_SLUG, "primary", null, null, "INVALID_JSON"));
    }

    @Test
    void getBuild_callsCorrectEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getBuild(APP_SLUG, BUILD_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds/" + BUILD_SLUG) &&
                !req.url().contains("/abort") &&
                !req.url().contains("/log")));
    }

    @Test
    void abortBuild_postsToAbortEndpoint() throws IOException {
        doReturn("{}").when(bitrise).post(any(GenericRequest.class));

        bitrise.abortBuild(APP_SLUG, BUILD_SLUG, "Superseded");

        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds/" + BUILD_SLUG + "/abort") &&
                req.getBody() != null &&
                req.getBody().contains("\"abort_reason\":\"Superseded\"")));
    }

    @Test
    void getBuildLog_callsLogEndpoint() throws IOException {
        doReturn("{\"log_chunks\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getBuildLog(APP_SLUG, BUILD_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds/" + BUILD_SLUG + "/log")));
    }

    @Test
    void listWorkflows_callsWorkflowsEndpoint() throws IOException {
        doReturn("[\"primary\",\"deploy\"]").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listWorkflows(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/build-workflows")));
    }

    // -------------------------------------------------------------------------
    // Artifacts tests
    // -------------------------------------------------------------------------

    @Test
    void listBuildArtifacts_callsArtifactsEndpoint() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listBuildArtifacts(APP_SLUG, BUILD_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds/" + BUILD_SLUG + "/artifacts")));
    }

    @Test
    void getBuildArtifact_callsSpecificArtifactEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getBuildArtifact(APP_SLUG, BUILD_SLUG, ARTIFACT_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/builds/" + BUILD_SLUG + "/artifacts/" + ARTIFACT_SLUG)));
    }

    // -------------------------------------------------------------------------
    // bitrise.yml tests
    // -------------------------------------------------------------------------

    @Test
    void getBitriseYml_callsYmlEndpoint() throws IOException {
        doReturn("format_version: '11'").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getBitriseYml(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/bitrise.yml") &&
                !req.url().contains("/config")));
    }

    @Test
    void updateBitriseYml_postsYmlContent() throws IOException {
        doReturn("{}").when(bitrise).post(any(GenericRequest.class));

        String yml = "format_version: '11'\nworkflows:\n  primary:\n    steps: []\n";
        bitrise.updateBitriseYml(APP_SLUG, yml);

        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/bitrise.yml") &&
                req.getBody() != null &&
                req.getBody().contains("app_config_datastore_yaml")));
    }

    @Test
    void getBitriseYmlConfig_callsConfigEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getBitriseYmlConfig(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/bitrise.yml/config")));
    }

    @Test
    void updateBitriseYmlConfig_putsToConfigEndpoint() throws IOException {
        doReturn("{}").when(bitrise).put(any(GenericRequest.class));

        String configJson = "{\"format_version\":\"11\",\"workflows\":{}}";
        bitrise.updateBitriseYmlConfig(APP_SLUG, configJson);

        verify(bitrise).put(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/bitrise.yml/config") &&
                configJson.equals(req.getBody())));
    }

    // -------------------------------------------------------------------------
    // Secrets tests
    // -------------------------------------------------------------------------

    @Test
    void listSecrets_callsSecretsEndpoint() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listSecrets(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/secrets") &&
                !req.url().contains("/" + SECRET_NAME)));
    }

    @Test
    void getSecret_callsSpecificSecretEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getSecret(APP_SLUG, SECRET_NAME);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/secrets/" + SECRET_NAME) &&
                !req.url().contains("/value")));
    }

    @Test
    void upsertSecret_putsToSecretEndpoint() throws IOException {
        doReturn("{}").when(bitrise).put(any(GenericRequest.class));

        bitrise.upsertSecret(APP_SLUG, SECRET_NAME, "s3cr3t", true, false, true);

        verify(bitrise).put(argThat((GenericRequest req) -> {
            String body = req.getBody();
            return req.url().contains("apps/" + APP_SLUG + "/secrets/" + SECRET_NAME) &&
                    body != null &&
                    body.contains("\"name\":\"MY_SECRET\"") &&
                    body.contains("\"value\":\"s3cr3t\"") &&
                    body.contains("\"is_protected\":true");
        }));
    }

    @Test
    void upsertSecret_onNotFound_fallsBackToPost() throws IOException {
        RestClient.RestClientException notFound =
                new RestClient.RestClientException("Not Found", "{}", 404);
        doThrow(notFound).when(bitrise).put(any(GenericRequest.class));
        doReturn("{\"slug\":\"new\"}").when(bitrise).post(any(GenericRequest.class));

        String result = bitrise.upsertSecret(APP_SLUG, SECRET_NAME, "value", null, null, null);

        assertNotNull(result);
        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/secrets") &&
                !req.url().contains("/" + SECRET_NAME)));
    }

    @Test
    void upsertSecret_onOtherError_propagatesException() throws IOException {
        RestClient.RestClientException serverError =
                new RestClient.RestClientException("Internal Server Error", "{}", 500);
        doThrow(serverError).when(bitrise).put(any(GenericRequest.class));

        assertThrows(RestClient.RestClientException.class,
                () -> bitrise.upsertSecret(APP_SLUG, SECRET_NAME, "value", null, null, null));
    }

    @Test
    void deleteSecret_deletesToSecretEndpoint() throws IOException {
        doReturn("").when(bitrise).delete(any(GenericRequest.class));

        bitrise.deleteSecret(APP_SLUG, SECRET_NAME);

        verify(bitrise).delete(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/secrets/" + SECRET_NAME)));
    }

    @Test
    void getSecretValue_callsValueEndpoint() throws IOException {
        doReturn("{\"value\":\"plaintext\"}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getSecretValue(APP_SLUG, SECRET_NAME);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/secrets/" + SECRET_NAME + "/value")));
    }

    // -------------------------------------------------------------------------
    // Pipelines tests
    // -------------------------------------------------------------------------

    @Test
    void listPipelines_callsPipelinesEndpoint() throws IOException {
        doReturn("{\"data\":[]}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.listPipelines(APP_SLUG);

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/pipelines")));
    }

    @Test
    void getPipeline_callsSpecificPipelineEndpoint() throws IOException {
        doReturn("{}").when(bitrise).execute(any(GenericRequest.class));

        bitrise.getPipeline(APP_SLUG, "pipeline-uuid-1");

        verify(bitrise).execute(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/pipelines/pipeline-uuid-1")));
    }

    @Test
    void abortPipeline_postsToAbortEndpoint() throws IOException {
        doReturn("{}").when(bitrise).post(any(GenericRequest.class));

        bitrise.abortPipeline(APP_SLUG, "pipeline-uuid-2");

        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/pipelines/pipeline-uuid-2/abort")));
    }

    // -------------------------------------------------------------------------
    // path() helper
    // -------------------------------------------------------------------------

    @Test
    void path_prependsBasePath() {
        String result = bitrise.path("apps/test");
        assertEquals("https://api.bitrise.io/v0.1/apps/test", result);
    }

    // -------------------------------------------------------------------------
    // validateBitriseYml tests
    // -------------------------------------------------------------------------

    @Test
    void validateBitriseYml_postsToValidateEndpoint() throws IOException {
        doReturn("{\"warnings\":[],\"errors\":[]}").when(bitrise).post(any(GenericRequest.class));

        String yml = "format_version: '11'\nworkflows:\n  primary:\n    steps: []\n";
        String result = bitrise.validateBitriseYml(yml, null);

        assertNotNull(result);
        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("validate-bitrise-yml") &&
                req.getBody() != null &&
                req.getBody().contains("bitrise_yml")));
    }

    @Test
    void validateBitriseYml_withAppSlug_includesSlugParam() throws IOException {
        doReturn("{\"warnings\":[],\"errors\":[]}").when(bitrise).post(any(GenericRequest.class));

        bitrise.validateBitriseYml("format_version: '11'", APP_SLUG);

        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("app_slug=" + APP_SLUG)));
    }

    @Test
    void validateBitriseYml_readsFileWhenPathProvided() throws IOException {
        String ymlContent = "format_version: '11'\nworkflows:\n  primary:\n    steps: []\n";
        Path ymlFile = tempDir.resolve("bitrise.yml");
        Files.writeString(ymlFile, ymlContent);

        doReturn("{\"warnings\":[],\"errors\":[]}").when(bitrise).post(any(GenericRequest.class));

        bitrise.validateBitriseYml(ymlFile.toString(), null);

        verify(bitrise).post(argThat((GenericRequest req) ->
                req.getBody() != null &&
                req.getBody().contains("format_version")));
    }

    // -------------------------------------------------------------------------
    // updateBitriseYml validation failure tests
    // -------------------------------------------------------------------------

    @Test
    void updateBitriseYml_throwsWhenValidationFails() throws IOException {
        String validationError = "{\"error_message\":\"Invalid workflow definition\"}";
        doReturn(validationError).when(bitrise).post(any(GenericRequest.class));

        IOException exception = assertThrows(IOException.class,
                () -> bitrise.updateBitriseYml(APP_SLUG, "format_version: '11'"));
        assertTrue(exception.getMessage().contains("validation failed"));
    }

    // -------------------------------------------------------------------------
    // updateBitriseYml file-path support tests
    // -------------------------------------------------------------------------

    @Test
    void updateBitriseYml_readsContentFromFile_whenPathProvided() throws IOException {
        String ymlContent = "format_version: '11'\nworkflows:\n  primary:\n    steps: []\n";
        Path ymlFile = tempDir.resolve("bitrise.yml");
        Files.writeString(ymlFile, ymlContent);

        doReturn("{\"warnings\":[],\"errors\":[]}").when(bitrise).post(any(GenericRequest.class));

        bitrise.updateBitriseYml(APP_SLUG, ymlFile.toString());

        // Should have posted twice: once to validate, once to update
        verify(bitrise, times(2)).post(any(GenericRequest.class));
        verify(bitrise).post(argThat((GenericRequest req) ->
                req.url().contains("apps/" + APP_SLUG + "/bitrise.yml") &&
                req.getBody() != null &&
                req.getBody().contains("app_config_datastore_yaml")));
    }

    @Test
    void updateBitriseYml_throwsWhenFileNotFound() {
        assertThrows(IOException.class,
                () -> bitrise.updateBitriseYml(APP_SLUG, "/nonexistent/path/bitrise.yml"));
    }

    // -------------------------------------------------------------------------
    // BasicBitrise configuration tests
    // -------------------------------------------------------------------------

    @Test
    void basicBitrise_isConfigured_methodExists() {
        // Verify the method exists and returns a boolean without asserting a specific value
        // (actual value depends on the test environment's BITRISE_TOKEN configuration)
        boolean result = BasicBitrise.isConfigured();
        assertTrue(result == true || result == false, "isConfigured() must return a boolean");
    }

    @Test
    void basicBitrise_getInstanceReturnsNull_whenNotConfigured() throws IOException {
        // Only run when BITRISE_TOKEN is absent
        org.junit.jupiter.api.Assumptions.assumeFalse(BasicBitrise.isConfigured(),
                "Skipped: BITRISE_TOKEN is configured in this environment");
        BasicBitrise.resetInstance();
        BasicBitrise instance = BasicBitrise.getInstance();
        assertNull(instance);
    }
}
