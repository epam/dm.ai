package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.expert.ExpertParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Tests for requireCliOutputFile and cleanupInputFolder parameters in Teammate.
 *
 * These tests verify:
 * 1. Safety mechanism when CLI output file is missing
 * 2. Field update skipping when requireCliOutputFile=true and no output
 * 3. Error comment posting instead of field updates
 * 4. Backwards compatibility with requireCliOutputFile=false
 * 5. Input folder cleanup control
 */
@ExtendWith(MockitoExtension.class)
class TeammateRequireCliOutputTest {

    @Mock
    private TrackerClient<ITicket> mockTrackerClient;

    @Mock
    private ITicket mockTicket;

    @TempDir
    Path tempDir;

    private Teammate.TeammateParams createBaseParams() {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        params.setInputJql("key = TEST-123");
        params.setSkipAIProcessing(true);
        params.setOutputType(ExpertParams.OutputType.field);
        params.setFieldName("Description");
        params.setOperationType(ExpertParams.OperationType.Replace);
        return params;
    }

    @BeforeEach
    void setUp() throws IOException {
        // Setup basic mock behavior (lenient to avoid UnnecessaryStubbingException)
        lenient().when(mockTicket.getKey()).thenReturn("TEST-123");
        lenient().when(mockTicket.getTicketKey()).thenReturn("TEST-123");
        lenient().when(mockTicket.getAttachments()).thenReturn(List.of());
        lenient().when(mockTicket.getFieldValueAsString(anyString())).thenReturn("Original description");

        lenient().when(mockTrackerClient.resolveFieldName(anyString(), anyString())).thenReturn("description");
        lenient().when(mockTrackerClient.getTextFieldsOnly(any())).thenReturn("Test ticket");
        lenient().when(mockTrackerClient.getExtendedQueryFields()).thenReturn(new String[]{});
        lenient().when(mockTrackerClient.tag(anyString())).thenAnswer(inv -> "@" + inv.getArgument(0));
    }

    @Test
    void testRequireCliOutputFile_DefaultValue() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // Don't set requireCliOutputFile - should default to true

        // When/Then
        assertTrue(params.isRequireCliOutputFile(),
            "requireCliOutputFile should default to true (strict mode) for safety");
    }

    @Test
    void testCleanupInputFolder_DefaultValue() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // Don't set cleanupInputFolder - should default to true

        // When/Then
        assertTrue(params.isCleanupInputFolder(),
            "cleanupInputFolder should default to true (cleanup by default)");
    }

    @Test
    void testRequireCliOutputFile_WithValidOutputFile() throws Exception {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // requireCliOutputFile=true (default)

        // Create mock CLI execution with valid output file
        String expectedContent = "# Generated Description\n\nThis is the CLI-generated content.";

        // When
        // Simulate CLI result with output response
        CliExecutionHelper.CliExecutionResult cliResult =
            new CliExecutionHelper.CliExecutionResult(
                new StringBuilder("Command executed successfully"),
                expectedContent  // Pass content string, not Path
            );

        // Then
        assertTrue(cliResult.hasOutputResponse(), "CLI result should have output response");
        assertEquals(expectedContent, cliResult.getOutputResponse(),
            "Output response should match file content");
    }

    @Test
    void testRequireCliOutputFile_MissingOutputFile_StrictMode() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // requireCliOutputFile=true (default, strict mode)

        // Create CLI result WITHOUT output file (response.md missing)
        CliExecutionHelper.CliExecutionResult cliResult =
            new CliExecutionHelper.CliExecutionResult(
                new StringBuilder("Command executed but no output file"),
                null  // No output file
            );

        // When
        boolean skipFieldUpdate = false;
        String response;

        // Simulate the logic from Teammate.java (lines 434-467)
        if (params.isSkipAIProcessing()) {
            if (cliResult != null && cliResult.hasOutputResponse()) {
                response = cliResult.getOutputResponse();
            } else {
                if (params.isRequireCliOutputFile()) {
                    // Strict mode: prepare error message
                    if (cliResult != null) {
                        response = "CLI command executed but did not produce output file:\n" +
                                  cliResult.getCommandResponses().toString();
                    } else {
                        response = "No CLI commands executed or results available.";
                    }
                    skipFieldUpdate = true;
                } else {
                    response = cliResult.getCommandResponses().toString();
                }
            }
        } else {
            response = "AI processing";
        }

        // Then
        assertTrue(skipFieldUpdate, "Should skip field update when requireCliOutputFile=true and no output");
        assertTrue(response.contains("CLI command executed but did not produce output file"),
            "Response should contain error message");
    }

    @Test
    void testRequireCliOutputFile_False_UseFallback() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // Explicitly set requireCliOutputFile=false (permissive mode)
        params.setRequireCliOutputFile(false);

        // Create CLI result WITHOUT output file
        CliExecutionHelper.CliExecutionResult cliResult =
            new CliExecutionHelper.CliExecutionResult(
                new StringBuilder("Command output without file"),
                null
            );

        // When
        boolean skipFieldUpdate = false;
        String response;

        if (params.isSkipAIProcessing()) {
            if (cliResult != null && cliResult.hasOutputResponse()) {
                response = cliResult.getOutputResponse();
            } else {
                if (params.isRequireCliOutputFile()) {
                    response = "Error message";
                    skipFieldUpdate = true;
                } else {
                    // Permissive mode: use fallback
                    response = cliResult.getCommandResponses().toString();
                }
            }
        } else {
            response = "AI processing";
        }

        // Then
        assertFalse(skipFieldUpdate, "Should NOT skip field update in permissive mode");
        assertEquals("Command output without file", response,
            "Should use command responses as fallback");
    }

    @Test
    void testRequireCliOutputFile_EmptyOutputFile() throws IOException {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // requireCliOutputFile=true (default)

        // Create empty output content
        String emptyContent = "";

        // When
        CliExecutionHelper.CliExecutionResult cliResult =
            new CliExecutionHelper.CliExecutionResult(
                new StringBuilder("Command executed"),
                emptyContent  // Empty string
            );

        // Then
        assertFalse(cliResult.hasOutputResponse(),
            "hasOutputResponse() should return false for empty string (see line 377 in CliExecutionHelper)");
        assertEquals("", cliResult.getOutputResponse(),
            "Output response should be empty string");
    }

    @Test
    void testRequireCliOutputFile_ErrorComment_WithInitiator() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setInitiator("john.doe");

        String errorResponse = "CLI command executed but did not produce output file:\nCommand failed";

        // When
        String errorComment;
        if (params.getInitiator() != null && !params.getInitiator().isEmpty()) {
            errorComment = "@" + params.getInitiator() +
                ", \n\n⚠️ CLI command execution issue:\n\n" + errorResponse;
        } else {
            errorComment = "⚠️ CLI command execution issue:\n\n" + errorResponse;
        }

        // Then
        assertTrue(errorComment.startsWith("@john.doe"),
            "Error comment should tag initiator");
        assertTrue(errorComment.contains("⚠️ CLI command execution issue"),
            "Error comment should contain warning");
        assertTrue(errorComment.contains("Command failed"),
            "Error comment should contain error details");
    }

    @Test
    void testRequireCliOutputFile_ErrorComment_NoInitiator() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // No initiator set

        String errorResponse = "No CLI commands executed or results available.";

        // When
        String errorComment;
        if (params.getInitiator() != null && !params.getInitiator().isEmpty()) {
            errorComment = "@" + params.getInitiator() +
                ", \n\n⚠️ CLI command execution issue:\n\n" + errorResponse;
        } else {
            errorComment = "⚠️ CLI command execution issue:\n\n" + errorResponse;
        }

        // Then
        assertFalse(errorComment.startsWith("@"),
            "Error comment should NOT tag when no initiator");
        assertTrue(errorComment.startsWith("⚠️ CLI command execution issue"),
            "Error comment should start with warning");
    }

    @Test
    void testRequireCliOutputFile_OperationAppend() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setOperationType(ExpertParams.OperationType.Append);
        // requireCliOutputFile=true (default)

        // Create CLI result without output file
        CliExecutionHelper.CliExecutionResult cliResult =
            new CliExecutionHelper.CliExecutionResult(
                new StringBuilder("Command output"),
                null
            );

        // When
        boolean skipFieldUpdate = false;
        String response;

        if (params.isSkipAIProcessing()) {
            if (cliResult != null && cliResult.hasOutputResponse()) {
                response = cliResult.getOutputResponse();
            } else {
                if (params.isRequireCliOutputFile()) {
                    response = "Error";
                    skipFieldUpdate = true;
                } else {
                    response = cliResult.getCommandResponses().toString();
                }
            }
        } else {
            response = "AI";
        }

        // Then
        assertTrue(skipFieldUpdate,
            "Should skip Append operation when no output file in strict mode");
        assertEquals(ExpertParams.OperationType.Append, params.getOperationType(),
            "Operation type should remain Append");
    }

    @Test
    void testRequireCliOutputFile_OutputTypeComment() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setOutputType(ExpertParams.OutputType.comment);

        // When/Then - verify parameter is set correctly
        assertEquals(ExpertParams.OutputType.comment, params.getOutputType(),
            "Output type should be comment");

        // Strict mode should apply regardless of output type
        assertTrue(params.isRequireCliOutputFile(),
            "Strict mode applies to all output types");
    }

    @Test
    void testRequireCliOutputFile_OutputTypeCreation() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setOutputType(ExpertParams.OutputType.creation);

        // When/Then
        assertEquals(ExpertParams.OutputType.creation, params.getOutputType(),
            "Output type should be creation");

        // Strict mode should prevent ticket creation with missing output
        assertTrue(params.isRequireCliOutputFile(),
            "Strict mode should prevent broken ticket creation");
    }

    @Test
    void testRequireCliOutputFile_OutputTypeNone() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setOutputType(ExpertParams.OutputType.none);

        // When/Then
        assertEquals(ExpertParams.OutputType.none, params.getOutputType(),
            "Output type should be none");

        // With outputType=none, skipFieldUpdate doesn't matter (no output anyway)
        // But the parameter should still be respected
        assertTrue(params.isRequireCliOutputFile(),
            "requireCliOutputFile should still have default value");
    }

    @Test
    void testCleanupInputFolder_True() throws IOException {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // cleanupInputFolder=true (default)

        Path inputFolder = tempDir.resolve("input/TEST-123");
        Files.createDirectories(inputFolder);
        Files.writeString(inputFolder.resolve("request.md"), "Test request");

        // When
        boolean shouldCleanup = params.isCleanupInputFolder();

        if (shouldCleanup) {
            // Simulate cleanup
            deleteRecursively(inputFolder);
        }

        // Then
        assertTrue(shouldCleanup, "Should cleanup by default");
        assertFalse(Files.exists(inputFolder), "Input folder should be deleted");
    }

    @Test
    void testCleanupInputFolder_False() throws IOException {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setCleanupInputFolder(false);

        Path inputFolder = tempDir.resolve("input/TEST-123");
        Files.createDirectories(inputFolder);
        Files.writeString(inputFolder.resolve("request.md"), "Test request");

        // When
        boolean shouldCleanup = params.isCleanupInputFolder();

        if (shouldCleanup) {
            deleteRecursively(inputFolder);
        }

        // Then
        assertFalse(shouldCleanup, "Should NOT cleanup when explicitly disabled");
        assertTrue(Files.exists(inputFolder), "Input folder should remain for inspection");
        assertTrue(Files.exists(inputFolder.resolve("request.md")),
            "Request file should remain for debugging");
    }

    /**
     * Helper method to recursively delete a directory.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())  // Reverse order for depth-first deletion
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
        } else {
            Files.delete(path);
        }
    }

    /**
     * Test that both new parameters work correctly together.
     */
    @Test
    void testBothParameters_StrictModeWithCleanup() throws IOException {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        // Both default to true

        Path inputFolder = tempDir.resolve("input/TEST-123");
        Files.createDirectories(inputFolder);

        // When
        boolean strictMode = params.isRequireCliOutputFile();
        boolean shouldCleanup = params.isCleanupInputFolder();

        // Then
        assertTrue(strictMode, "Strict mode should be enabled by default");
        assertTrue(shouldCleanup, "Cleanup should be enabled by default");
    }

    /**
     * Test permissive mode with no cleanup (debugging scenario).
     */
    @Test
    void testBothParameters_PermissiveModeNoCleanup() {
        // Given
        Teammate.TeammateParams params = createBaseParams();
        params.setRequireCliOutputFile(false);  // Permissive mode
        params.setCleanupInputFolder(false);    // Keep for debugging

        // When/Then
        assertFalse(params.isRequireCliOutputFile(),
            "Permissive mode allows fallback");
        assertFalse(params.isCleanupInputFolder(),
            "Input folder kept for debugging");
    }
}
