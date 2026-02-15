package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CliExecutionHelperTest {
    
    private CliExecutionHelper cliHelper;
    private ITicket mockTicket;
    private IAttachment mockAttachment;
    private TrackerClient<?> mockTrackerClient;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        cliHelper = new CliExecutionHelper();
        mockTicket = mock(ITicket.class);
        mockAttachment = mock(IAttachment.class);
        mockTrackerClient = mock(TrackerClient.class);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any created folders
        Path inputFolder = tempDir.resolve("input");
        if (Files.exists(inputFolder)) {
            try {
                FileUtils.deleteDirectory(inputFolder.toFile());
            } catch (IOException e) {
                // Ignore cleanup errors in tests
            }
        }
    }
    
    @Test
    void testCreateInputContext_Success() throws IOException {
        // Arrange
        String ticketKey = "TEST-123";
        String inputParams = "Test input parameters";
        when(mockTicket.getTicketKey()).thenReturn(ticketKey);
        when(mockTicket.getAttachments()).thenReturn(Collections.emptyList());
        
        // Act
        Path result = cliHelper.createInputContext(mockTicket, inputParams, mockTrackerClient);
        
        // Assert
        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertEquals("input/" + ticketKey, result.toString());
        
        // Check request.md file was created
        Path requestFile = result.resolve("request.md");
        assertTrue(Files.exists(requestFile));
        String fileContent = Files.readString(requestFile, StandardCharsets.UTF_8);
        assertEquals(inputParams, fileContent);
    }
    
    @Test
    void testCreateInputContext_WithAttachments() throws IOException, Exception {
        // Arrange
        String ticketKey = "TEST-456";
        String inputParams = "Test input";
        byte[] attachmentContent = "Test attachment content".getBytes();
        
        when(mockTicket.getTicketKey()).thenReturn(ticketKey);
        when(mockAttachment.getName()).thenReturn("test-file.txt");
        when(mockAttachment.getUrl()).thenReturn("http://example.com/attachment");
        
        // Mock tracker client download
        File mockFile = new File(tempDir.toFile(), "temp-attachment.txt");
        try { FileUtils.writeByteArrayToFile(mockFile, attachmentContent); } catch (IOException e) {}
        when(mockTrackerClient.convertUrlToFile("http://example.com/attachment")).thenReturn(mockFile);
        doReturn(Arrays.asList(mockAttachment)).when(mockTicket).getAttachments();
        
        // Act
        Path result = cliHelper.createInputContext(mockTicket, inputParams, mockTrackerClient);
        
        // Assert
        Path attachmentFile = result.resolve("test-file.txt");
        assertTrue(Files.exists(attachmentFile));
        byte[] savedContent = Files.readAllBytes(attachmentFile);
        assertArrayEquals(attachmentContent, savedContent);
    }
    
    @Test
    void testCreateInputContext_NullTicket() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> cliHelper.createInputContext(null, "test", mockTrackerClient)
        );
        assertEquals("Ticket cannot be null", exception.getMessage());
    }
    
    @Test
    void testCreateInputContext_EmptyTicketKey() {
        // Arrange
        when(mockTicket.getTicketKey()).thenReturn("");
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> cliHelper.createInputContext(mockTicket, "test", mockTrackerClient)
        );
        assertEquals("Ticket key cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testCreateInputContext_EmptyInputParams() throws IOException {
        // Arrange
        String ticketKey = "TEST-789";
        when(mockTicket.getTicketKey()).thenReturn(ticketKey);
        when(mockTicket.getAttachments()).thenReturn(Collections.emptyList());
        
        // Act
        Path result = cliHelper.createInputContext(mockTicket, "", mockTrackerClient);
        
        // Assert
        assertTrue(Files.exists(result));
        Path requestFile = result.resolve("request.md");
        assertFalse(Files.exists(requestFile)); // Should not create empty file
    }
    
    @Test
    void testExecuteCliCommands_Success() {
        // Arrange
        String[] commands = {"echo hello", "echo world"};
        
        try (MockedStatic<CommandLineUtils> mockedUtils = Mockito.mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo hello"), isNull(), any(Map.class)))
                      .thenReturn("hello\nExit Code: 0");
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo world"), isNull(), any(Map.class)))
                      .thenReturn("world\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            StringBuilder result = cliHelper.executeCliCommands(commands, null, "dmtools.env");
            
            // Assert
            String resultString = result.toString();
            assertTrue(resultString.contains("CLI Command: echo hello"));
            assertTrue(resultString.contains("hello"));
            assertTrue(resultString.contains("CLI Command: echo world"));
            assertTrue(resultString.contains("world"));
        }
    }
    
    @Test
    void testExecuteCliCommands_WithError() {
        // Arrange
        String[] commands = {"invalid-command"};
        
        try (MockedStatic<CommandLineUtils> mockedUtils = Mockito.mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("invalid-command"), isNull(), any(Map.class)))
                      .thenThrow(new IOException("Command not found"));
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            StringBuilder result = cliHelper.executeCliCommands(commands, null, "dmtools.env");
            
            // Assert
            String resultString = result.toString();
            assertTrue(resultString.contains("CLI Command: invalid-command"));
            assertTrue(resultString.contains("Error:"));
            assertTrue(resultString.contains("Command not found"));
        }
    }
    
    @Test
    void testExecuteCliCommands_EmptyCommands() {
        // Act
        StringBuilder result = cliHelper.executeCliCommands(new String[0], null, "dmtools.env");
        
        // Assert
        assertEquals(0, result.length());
    }
    
    @Test
    void testExecuteCliCommands_NullCommands() {
        // Act
        StringBuilder result = cliHelper.executeCliCommands(null, null, "dmtools.env");
        
        // Assert
        assertEquals(0, result.length());
    }
    
    @Test
    void testExecuteCliCommands_WithWorkingDirectory() throws IOException {
        // Arrange
        Path workingDir = Files.createTempDirectory(tempDir, "working");
        String[] commands = {"echo test"};
        
        try (MockedStatic<CommandLineUtils> mockedUtils = Mockito.mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo test"), any(File.class), any(Map.class)))
                      .thenReturn("test\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());

            // Act
            StringBuilder result = cliHelper.executeCliCommands(commands, workingDir, "dmtools.env");

            // Assert
            assertTrue(result.toString().contains("test"));
            // Verify that CommandLineUtils was called with the correct working directory
            mockedUtils.verify(() -> CommandLineUtils.runCommand(eq("echo test"), eq(workingDir.toFile()), any(Map.class)));
        }
    }
    
    @Test
    void testProcessOutputResponse_FileExists() throws IOException {
        // Arrange - use new "output" folder
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        String expectedContent = "CLI response content";
        Files.write(responseFile, expectedContent.getBytes(StandardCharsets.UTF_8));

        // Act
        String result = cliHelper.processOutputResponse(tempDir);

        // Assert
        assertEquals(expectedContent, result);
    }

    @Test
    void testProcessOutputResponse_LegacyFolder() throws IOException {
        // Arrange - test backward compatibility with "outputs" folder
        Path outputDir = tempDir.resolve("outputs");
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        String expectedContent = "Legacy CLI response content";
        Files.write(responseFile, expectedContent.getBytes(StandardCharsets.UTF_8));

        // Act
        String result = cliHelper.processOutputResponse(tempDir);

        // Assert
        assertEquals(expectedContent, result, "Should support legacy 'outputs/' folder for backward compatibility");
    }

    @Test
    void testProcessOutputResponse_PreferNewOverLegacy() throws IOException {
        // Arrange - both folders exist, new should take precedence
        Path newOutputDir = tempDir.resolve("output");
        Files.createDirectories(newOutputDir);
        Path newResponseFile = newOutputDir.resolve("response.md");
        String newContent = "New output folder content";
        Files.write(newResponseFile, newContent.getBytes(StandardCharsets.UTF_8));

        Path legacyOutputDir = tempDir.resolve("outputs");
        Files.createDirectories(legacyOutputDir);
        Path legacyResponseFile = legacyOutputDir.resolve("response.md");
        String legacyContent = "Legacy outputs folder content";
        Files.write(legacyResponseFile, legacyContent.getBytes(StandardCharsets.UTF_8));

        // Act
        String result = cliHelper.processOutputResponse(tempDir);

        // Assert
        assertEquals(newContent, result, "Should prefer new 'output/' folder over legacy 'outputs/' folder");
    }

    @Test
    void testProcessOutputResponse_FileNotExists() {
        // Act - test with a directory that doesn't have output/response.md
        String result = cliHelper.processOutputResponse(tempDir);

        // Assert
        assertNull(result);
    }

    @Test
    void testProcessOutputResponse_EmptyFile() throws IOException {
        // Arrange - use new "output" folder
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        Files.write(responseFile, "".getBytes(StandardCharsets.UTF_8));

        // Act
        String result = cliHelper.processOutputResponse(tempDir);

        // Assert
        assertNull(result);
    }
    
    @Test
    void testCleanupInputContext_Success() throws IOException {
        // Arrange
        Path inputFolder = tempDir.resolve("input/TEST-123");
        Files.createDirectories(inputFolder);
        Files.write(inputFolder.resolve("test.txt"), "test".getBytes());
        assertTrue(Files.exists(inputFolder));
        
        // Act
        cliHelper.cleanupInputContext(inputFolder);
        
        // Assert
        assertFalse(Files.exists(inputFolder));
    }
    
    @Test
    void testCleanupInputContext_NonExistentFolder() {
        // Arrange
        Path nonExistentFolder = tempDir.resolve("non-existent");
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cliHelper.cleanupInputContext(nonExistentFolder));
    }
    
    @Test
    void testCleanupInputContext_NullPath() {
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cliHelper.cleanupInputContext(null));
    }
    
    @Test
    void testAttachmentFilenameSanitization() throws IOException, Exception {
        // Arrange
        String ticketKey = "TEST-SANITIZE";
        when(mockTicket.getTicketKey()).thenReturn(ticketKey);
        when(mockAttachment.getName()).thenReturn("path/to/file.txt");
        when(mockAttachment.getUrl()).thenReturn("http://example.com/path-file");
        
        // Mock tracker client download
        File mockFile = new File(tempDir.toFile(), "temp-path-file.txt");
        try { FileUtils.writeStringToFile(mockFile, "content", StandardCharsets.UTF_8); } catch (IOException e) {}
        when(mockTrackerClient.convertUrlToFile("http://example.com/path-file")).thenReturn(mockFile);
        doReturn(Arrays.asList(mockAttachment)).when(mockTicket).getAttachments();
        
        // Act
        Path result = cliHelper.createInputContext(mockTicket, "test", mockTrackerClient);
        
        // Assert
        Path sanitizedFile = result.resolve("path_to_file.txt");
        assertTrue(Files.exists(sanitizedFile));
        assertEquals("content", Files.readString(sanitizedFile));
    }
    
    @Test
    void testExecuteCliCommandsWithResult() throws IOException {
        // Arrange
        Path workingDir = Files.createTempDirectory(tempDir, "working");
        String[] commands = {"echo hello", "echo world"};
        
        try (MockedStatic<CommandLineUtils> mockedUtils = Mockito.mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo hello"), any(File.class), any(Map.class)))
                      .thenReturn("hello\nExit Code: 0");
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo world"), any(File.class), any(Map.class)))
                      .thenReturn("world\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            CliExecutionHelper.CliExecutionResult result = cliHelper.executeCliCommandsWithResult(commands, workingDir, "dmtools.env");
            
            // Assert
            assertNotNull(result);
            assertTrue(result.getCommandResponses().toString().contains("hello"));
            assertTrue(result.getCommandResponses().toString().contains("world"));
            assertFalse(result.hasOutputResponse()); // No output/response.md file created
            assertNull(result.getOutputResponse());
        }
    }

    @Test
    void testExecuteCliCommandsWithResult_WithOutputFile() throws IOException {
        // Arrange
        Path workingDir = Files.createTempDirectory(tempDir, "working");
        Path outputDir = workingDir.resolve("output");  // Changed from "outputs" to "output"
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        String outputContent = "CLI generated response content";
        Files.write(responseFile, outputContent.getBytes(StandardCharsets.UTF_8));
        
        String[] commands = {"echo test"};
        
        try (MockedStatic<CommandLineUtils> mockedUtils = Mockito.mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo test"), any(File.class), any(Map.class)))
                      .thenReturn("test\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            CliExecutionHelper.CliExecutionResult result = cliHelper.executeCliCommandsWithResult(commands, workingDir, "dmtools.env");
            
            // Assert
            assertNotNull(result);
            assertTrue(result.getCommandResponses().toString().contains("test"));
            assertTrue(result.hasOutputResponse());
            assertEquals(outputContent, result.getOutputResponse());
        }
    }
    
    @Test
    void testProcessOutputResponse_NoConcurrencyIssues() throws IOException {
        // This test verifies that processOutputResponse doesn't rely on global working directory
        // and can work correctly even when the JVM working directory is different

        // Arrange
        Path workingDir = Files.createTempDirectory(tempDir, "working");
        Path outputDir = workingDir.resolve("outputs");
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        String expectedContent = "Thread-safe CLI response";
        Files.write(responseFile, expectedContent.getBytes(StandardCharsets.UTF_8));

        // Act - call with specific working directory (thread-safe approach)
        String result = cliHelper.processOutputResponse(workingDir);

        // Assert
        assertEquals(expectedContent, result);

        // Verify that the parameterless version doesn't find the file
        // (since it looks in the current JVM working directory, not our test directory)
        String resultWithoutParam = cliHelper.processOutputResponse();
        assertNull(resultWithoutParam);
    }

    // ========================================================================
    // Tests for appendPromptToCommands (cliPrompt feature)
    // ========================================================================

    @Test
    void testAppendPromptToCommands_PlainText() throws IOException {
        String[] commands = {"echo", "./script.sh"};
        String prompt = "This is a test prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(2, result.length);

        // Both commands should have file path appended
        assertTrue(result[0].startsWith("echo \""));
        assertTrue(result[0].endsWith("\""));
        assertTrue(result[1].startsWith("./script.sh \""));
        assertTrue(result[1].endsWith("\""));

        // Extract file path from first command
        String filePath = result[0].substring("echo \"".length(), result[0].length() - 1);
        File promptFile = new File(filePath);

        // Verify file exists and contains correct content
        assertTrue(promptFile.exists(), "Temporary prompt file should exist");
        String fileContent = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(prompt, fileContent, "File should contain the prompt");

        // Both commands should reference the same file
        String filePath2 = result[1].substring("./script.sh \"".length(), result[1].length() - 1);
        assertEquals(filePath, filePath2, "Both commands should use same prompt file");
    }

    @Test
    void testAppendPromptToCommands_NullPrompt() {
        String[] commands = {"echo", "./script.sh"};
        String prompt = null;

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertSame(commands, result, "Should return same array reference when prompt is null");
    }

    @Test
    void testAppendPromptToCommands_EmptyPrompt() {
        String[] commands = {"echo", "./script.sh"};
        String prompt = "";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertSame(commands, result, "Should return same array reference when prompt is empty");
    }

    @Test
    void testAppendPromptToCommands_WhitespacePrompt() {
        String[] commands = {"echo", "./script.sh"};
        String prompt = "   \t\n  ";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertSame(commands, result, "Should return same array reference when prompt is whitespace");
    }

    @Test
    void testAppendPromptToCommands_EmptyArray() {
        String[] commands = {};
        String prompt = "Test prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertSame(commands, result, "Should return same array reference when commands are empty");
    }

    @Test
    void testAppendPromptToCommands_NullCommands() {
        String[] commands = null;
        String prompt = "Test prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNull(result, "Should return null when commands are null");
    }

    @Test
    void testAppendPromptToCommands_SpecialCharsInPrompt() throws IOException {
        // Test that special characters are handled correctly via file (no escaping needed)
        String[] commands = {"./script.sh"};
        String prompt = "Text with \"quotes\", $vars, `commands`, and \\backslashes";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(1, result.length);

        // Extract file path
        String filePath = result[0].substring("./script.sh \"".length(), result[0].length() - 1);
        File promptFile = new File(filePath);

        // Verify file contains exact prompt (no escaping needed!)
        assertTrue(promptFile.exists());
        String fileContent = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(prompt, fileContent, "File should contain prompt exactly as-is (no escaping)");
    }

    @Test
    void testAppendPromptToCommands_WindowsPathInPrompt() throws IOException {
        // Test Windows-style paths in prompt (backslashes)
        String[] commands = {"echo"};
        String prompt = "Path: C:\\Users\\test\\Documents";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(1, result.length);

        // Extract file path
        String filePath = result[0].substring("echo \"".length(), result[0].length() - 1);
        File promptFile = new File(filePath);

        // Verify file contains exact Windows path (no escaping)
        assertTrue(promptFile.exists());
        String fileContent = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(prompt, fileContent);
    }

    @Test
    void testAppendPromptToCommands_MultipleCommands() throws IOException {
        String[] commands = {
            "./script1.sh",
            "python script2.py",
            "node script3.js"
        };
        String prompt = "Execute with this prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(3, result.length);

        // All commands should have same file path appended
        assertTrue(result[0].startsWith("./script1.sh \""));
        assertTrue(result[1].startsWith("python script2.py \""));
        assertTrue(result[2].startsWith("node script3.js \""));

        // Extract and verify all use same file
        String filePath1 = result[0].substring("./script1.sh \"".length(), result[0].length() - 1);
        String filePath2 = result[1].substring("python script2.py \"".length(), result[1].length() - 1);
        String filePath3 = result[2].substring("node script3.js \"".length(), result[2].length() - 1);

        assertEquals(filePath1, filePath2);
        assertEquals(filePath1, filePath3);

        // Verify file content
        File promptFile = new File(filePath1);
        assertTrue(promptFile.exists());
        String fileContent = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(prompt, fileContent);
    }

    @Test
    void testAppendPromptToCommands_NullCommandElement() {
        String[] commands = {"echo", null, "./script.sh"};
        String prompt = "Test prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(3, result.length);
        assertTrue(result[0].startsWith("echo \""));
        assertNull(result[1], "Null command element should remain null");
        assertTrue(result[2].startsWith("./script.sh \""));
    }

    @Test
    void testAppendPromptToCommands_EmptyCommandElement() {
        String[] commands = {"echo", "", "./script.sh"};
        String prompt = "Test prompt";

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(3, result.length);
        assertTrue(result[0].startsWith("echo \""));
        assertEquals("", result[1], "Empty command element should remain empty");
        assertTrue(result[2].startsWith("./script.sh \""));
    }

    @Test
    void testAppendPromptToCommands_LongPrompt() throws IOException {
        String[] commands = {"echo"};
        // Create a 10000 character prompt (no problem with temp file approach!)
        StringBuilder longPrompt = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longPrompt.append("This is a long prompt text. ");
        }
        String prompt = longPrompt.toString();
        assertTrue(prompt.length() > 10000, "Prompt should be over 10000 chars");

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertTrue(result[0].startsWith("echo \""));
        assertTrue(result[0].endsWith("\""));

        // Extract file path and verify full content is in file
        String filePath = result[0].substring("echo \"".length(), result[0].length() - 1);
        File promptFile = new File(filePath);
        assertTrue(promptFile.exists());

        String fileContent = Files.readString(promptFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(prompt, fileContent, "File should contain entire long prompt");
        assertTrue(fileContent.length() > 10000, "File content should be over 10000 chars");
    }

    @Test
    void testAppendPromptToCommands_BackwardsCompatibility() {
        String[] commands = {"./existing-script.sh arg1 arg2"};
        String prompt = null; // cliPrompt not set in existing configs

        String[] result = CliExecutionHelper.appendPromptToCommands(commands, prompt);

        assertNotNull(result);
        assertSame(commands, result, "Commands should remain unchanged for backwards compatibility");
    }
}
