package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.job.Params;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for file path injection functionality in Teammate.
 * Tests the extractIfNeeded() method with file path support.
 */
public class TeammateFilePathTest {

    @TempDir
    Path tempDir;

    private Teammate teammate;
    private Teammate.TeammateParams params;

    @Mock
    private TrackerClient<ITicket> trackerClient;

    @Mock
    private AI ai;

    @Mock
    private GenericRequestAgent genericRequestAgent;

    @Mock
    private ContextOrchestrator contextOrchestrator;

    @Mock
    private UriToObjectFactory uriToObjectFactory;

    @Mock
    private Confluence confluence;

    @Mock
    private ITicket ticket;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Create a TrackerClient mock that also implements UriToObject
        trackerClient = mock(TrackerClient.class, withSettings().extraInterfaces(UriToObject.class));
        UriToObject uriToObjectClient = (UriToObject) trackerClient;
        when(uriToObjectClient.parseUris(any())).thenReturn(Set.of());
        when(uriToObjectClient.uriToObject(any())).thenReturn(null);

        // Create teammate instance and inject mocked dependencies
        teammate = new Teammate();
        teammate.trackerClient = trackerClient;
        teammate.ai = ai;
        teammate.genericRequestAgent = genericRequestAgent;
        teammate.contextOrchestrator = contextOrchestrator;
        teammate.uriToObjectFactory = uriToObjectFactory;
        teammate.confluence = confluence;

        // Set up test parameters
        params = new Teammate.TeammateParams();
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
                "Test role",
                "Test request",
                new String[]{"Test question"},
                new String[]{"Test task"},
                new String[]{"Test instructions"},
                "Test known info",
                "Test formatting rules",
                "Test few shots"
        );
        params.setAgentParams(agentParams);
        params.setInputJql("key = TEST-123");
        params.setOutputType(Params.OutputType.none);

        // Mock ticket
        when(ticket.getKey()).thenReturn("TEST-123");
        when(ticket.getTicketKey()).thenReturn("TEST-123");
        when(ticket.toText()).thenReturn("Mock ticket text");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());

        // Mock trackerClient
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(invocation -> {
            // Don't execute the performer to avoid full job execution
            return null;
        }).when(trackerClient).searchAndPerform(any(), anyString(), any());

        // Mock context orchestrator
        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());

        // Mock AI agent
        when(genericRequestAgent.run(any())).thenReturn("AI response");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testAbsoluteFilePathDetectionAndLoading() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test-instructions.txt");
        Files.writeString(testFile, "Absolute path file content", StandardCharsets.UTF_8);

        // Change working directory context for the test
        String originalUserDir = System.getProperty("user.dir");
        try {
            // Set instructions to absolute file path
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{testFile.toString()});

            // Act - trigger extractIfNeeded through runJobImpl
            teammate.runJobImpl(params);

            // Assert - verify that file content was loaded
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("Absolute path file content"),
                    "Instructions should contain file content");
            assertTrue(processedInstructions[0].contains(testFile.toString()),
                    "Instructions should contain original file path");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testRelativeFilePathDetectionAndLoading() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("relative-instructions.md");
        Files.writeString(testFile, "Relative path file content", StandardCharsets.UTF_8);

        // Change working directory to tempDir
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to relative file path
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./relative-instructions.md"});

            // Act
            teammate.runJobImpl(params);

            // Assert
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("Relative path file content"),
                    "Instructions should contain file content");
            assertTrue(processedInstructions[0].contains("./relative-instructions.md"),
                    "Instructions should contain original file path");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testParentDirectoryFilePathDetectionAndLoading() throws Exception {
        // Arrange
        Path parentFile = tempDir.resolve("parent-file.txt");
        Files.writeString(parentFile, "Parent directory file content", StandardCharsets.UTF_8);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);

        // Change working directory to subdir
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", subDir.toString());

            // Set instructions to parent directory file path
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"../parent-file.txt"});

            // Act
            teammate.runJobImpl(params);

            // Assert
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("Parent directory file content"),
                    "Instructions should contain file content");
            assertTrue(processedInstructions[0].contains("../parent-file.txt"),
                    "Instructions should contain original file path");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testMissingFileHandling() throws Exception {
        // Arrange
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to non-existent file path
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./non-existent-file.txt"});

            // Act - should not throw exception
            assertDoesNotThrow(() -> teammate.runJobImpl(params));

            // Assert - should use original value as fallback
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertEquals("./non-existent-file.txt", processedInstructions[0],
                    "Should use original value when file not found");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testInvalidPathHandling() throws Exception {
        // Arrange
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to invalid path (contains null character on some systems)
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            // Use a path that might be invalid but won't crash
            agentParams.setInstructions(new String[]{"./invalid\u0000path.txt"});

            // Act - should not throw exception
            assertDoesNotThrow(() -> teammate.runJobImpl(params));

            // Assert - should use original value as fallback
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            // Original value should be preserved
            assertTrue(processedInstructions[0].contains("invalid"),
                    "Should use original value when path is invalid");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testMixedArrayWithUrlsFilePathsAndPlainText() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("mixed-test.txt");
        Files.writeString(testFile, "File content from mixed array", StandardCharsets.UTF_8);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Mock Confluence URL
            Content mockContent = mock(Content.class);
            Storage mockStorage = mock(Storage.class);
            when(mockStorage.getValue()).thenReturn("Confluence content");
            when(mockContent.getStorage()).thenReturn(mockStorage);
            when(confluence.contentByUrl(anyString())).thenReturn(mockContent);

            // Set instructions with mixed sources
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{
                    "https://confluence.example.com/page",
                    "./mixed-test.txt",
                    "Plain text instruction"
            });

            // Act
            teammate.runJobImpl(params);

            // Assert
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertEquals(3, processedInstructions.length);

            // Check Confluence URL was processed
            assertTrue(processedInstructions[0].contains("Confluence content"),
                    "First element should contain Confluence content");

            // Check file path was processed
            assertTrue(processedInstructions[1].contains("File content from mixed array"),
                    "Second element should contain file content");

            // Check plain text was not modified
            assertEquals("Plain text instruction", processedInstructions[2],
                    "Third element should remain as plain text");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testFilePathInAllSupportedFields() throws Exception {
        // Arrange
        Path roleFile = tempDir.resolve("role.txt");
        Path instructionsFile = tempDir.resolve("instructions.txt");
        Path formattingFile = tempDir.resolve("formatting.txt");
        Path fewShotsFile = tempDir.resolve("fewshots.txt");
        Path questionsFile = tempDir.resolve("questions.txt");
        Path tasksFile = tempDir.resolve("tasks.txt");

        Files.writeString(roleFile, "Role content", StandardCharsets.UTF_8);
        Files.writeString(instructionsFile, "Instructions content", StandardCharsets.UTF_8);
        Files.writeString(formattingFile, "Formatting content", StandardCharsets.UTF_8);
        Files.writeString(fewShotsFile, "Few shots content", StandardCharsets.UTF_8);
        Files.writeString(questionsFile, "Questions content", StandardCharsets.UTF_8);
        Files.writeString(tasksFile, "Tasks content", StandardCharsets.UTF_8);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set all fields to file paths
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setAiRole("./role.txt");
            agentParams.setInstructions(new String[]{"./instructions.txt"});
            agentParams.setFormattingRules("./formatting.txt");
            agentParams.setFewShots("./fewshots.txt");
            agentParams.setQuestions(new String[]{"./questions.txt"});
            agentParams.setTasks(new String[]{"./tasks.txt"});

            // Act
            teammate.runJobImpl(params);

            // Assert
            assertTrue(agentParams.getAiRole().contains("Role content"),
                    "aiRole should contain file content");
            assertTrue(agentParams.getInstructions()[0].contains("Instructions content"),
                    "instructions should contain file content");
            assertTrue(agentParams.getFormattingRules().contains("Formatting content"),
                    "formattingRules should contain file content");
            assertTrue(agentParams.getFewShots().contains("Few shots content"),
                    "fewShots should contain file content");
            assertTrue(agentParams.getQuestions()[0].contains("Questions content"),
                    "questions should contain file content");
            assertTrue(agentParams.getTasks()[0].contains("Tasks content"),
                    "tasks should contain file content");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testConfluenceUrlExtractionStillWorks() throws Exception {
        // Arrange
        Content mockContent = mock(Content.class);
        Storage mockStorage = mock(Storage.class);
        when(mockStorage.getValue()).thenReturn("Confluence page content");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(confluence.contentByUrl(anyString())).thenReturn(mockContent);

        // Set instructions to Confluence URL
        RequestDecompositionAgent.Result agentParams = params.getAgentParams();
        agentParams.setInstructions(new String[]{"https://confluence.example.com/page"});

        // Act
        teammate.runJobImpl(params);

        // Assert
        String[] processedInstructions = agentParams.getInstructions();
        assertNotNull(processedInstructions);
        assertTrue(processedInstructions[0].contains("Confluence page content"),
                "Instructions should contain Confluence content");
        assertTrue(processedInstructions[0].contains("https://confluence.example.com/page"),
                "Instructions should contain original URL");

        // Verify Confluence was called
        verify(confluence).contentByUrl("https://confluence.example.com/page");
    }

    @Test
    void testNullInputArrayHandling() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result agentParams = params.getAgentParams();
        agentParams.setInstructions(null);

        // Act - should not throw exception
        assertDoesNotThrow(() -> teammate.runJobImpl(params));

        // Assert - should return empty string array
        String[] processedInstructions = agentParams.getInstructions();
        assertNotNull(processedInstructions);
        assertEquals(1, processedInstructions.length);
        assertEquals("", processedInstructions[0]);
    }

    @Test
    void testEmptyStringInArrayHandling() throws Exception {
        // Arrange
        RequestDecompositionAgent.Result agentParams = params.getAgentParams();
        agentParams.setInstructions(new String[]{""});

        // Act
        teammate.runJobImpl(params);

        // Assert
        String[] processedInstructions = agentParams.getInstructions();
        assertNotNull(processedInstructions);
        assertEquals(1, processedInstructions.length);
        assertEquals("", processedInstructions[0]);
    }

    @Test
    void testPathNormalization() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("normalized.txt");
        Files.writeString(testFile, "Normalized content", StandardCharsets.UTF_8);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to path with redundant segments
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./subdir/../normalized.txt"});

            // Act
            teammate.runJobImpl(params);

            // Assert - path should be normalized and file found
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("Normalized content"),
                    "Instructions should contain file content after path normalization");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testUtf8EncodingSupport() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("utf8-test.txt");
        String utf8Content = "UTF-8 content: 你好世界 🌍 Привет мир";
        Files.writeString(testFile, utf8Content, StandardCharsets.UTF_8);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to UTF-8 file
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./utf8-test.txt"});

            // Act
            teammate.runJobImpl(params);

            // Assert
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains(utf8Content),
                    "Instructions should contain UTF-8 content correctly");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testMultilineFileContent() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("multiline.txt");
        String multilineContent = "Line 1\nLine 2\nLine 3\n\nLine 5 with blank line above";
        Files.writeString(testFile, multilineContent, StandardCharsets.UTF_8);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Set instructions to multiline file
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./multiline.txt"});

            // Act
            teammate.runJobImpl(params);

            // Assert
            String[] processedInstructions = agentParams.getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("Line 1"),
                    "Instructions should contain first line");
            assertTrue(processedInstructions[0].contains("Line 5 with blank line above"),
                    "Instructions should contain last line");
            assertTrue(processedInstructions[0].contains("\n"),
                    "Instructions should preserve newlines");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void testDifferentFileExtensions() throws Exception {
        // Arrange
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Create files with different extensions
            Path txtFile = tempDir.resolve("test.txt");
            Path mdFile = tempDir.resolve("test.md");
            Path yamlFile = tempDir.resolve("test.yaml");
            Path jsonFile = tempDir.resolve("test.json");

            Files.writeString(txtFile, "TXT content", StandardCharsets.UTF_8);
            Files.writeString(mdFile, "# Markdown content", StandardCharsets.UTF_8);
            Files.writeString(yamlFile, "key: value", StandardCharsets.UTF_8);
            Files.writeString(jsonFile, "{\"key\": \"value\"}", StandardCharsets.UTF_8);

            // Test each file type
            String[] filePaths = {"./test.txt", "./test.md", "./test.yaml", "./test.json"};
            String[] expectedContents = {"TXT content", "# Markdown content", "key: value", "{\"key\": \"value\"}"};

            for (int i = 0; i < filePaths.length; i++) {
                RequestDecompositionAgent.Result agentParams = params.getAgentParams();
                agentParams.setInstructions(new String[]{filePaths[i]});

                // Act
                teammate.runJobImpl(params);

                // Assert
                String[] processedInstructions = agentParams.getInstructions();
                assertNotNull(processedInstructions);
                assertTrue(processedInstructions[0].contains(expectedContents[i]),
                        "File " + filePaths[i] + " should contain expected content");
            }
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}