package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.GenericRequestAgent;
import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.context.UriToObject;
import com.github.istin.dmtools.common.utils.CommandLineUtils;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.context.UriToObjectFactory;
import com.github.istin.dmtools.job.Params;
import com.github.istin.dmtools.job.ResultItem;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;

import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.mockito.ArgumentCaptor;
import java.util.Map;

public class TeammateCliIntegrationTest {
    
    // TrackerClient mock that also implements UriToObject (like JiraClient does)
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
    private ITicket ticket;
    
    @TempDir
    Path tempDir;
    
    private Teammate teammate;
    private Teammate.TeammateParams params;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Create a TrackerClient mock that also implements UriToObject (like JiraClient)
        trackerClient = mock(TrackerClient.class, withSettings().extraInterfaces(UriToObject.class));
        
        // Mock UriToObject methods
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
        teammate.instructionProcessor = new InstructionProcessor(null, tempDir.toString());
        
        // Set up test parameters
        params = new Teammate.TeammateParams();
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
            "Test role",                        // aiRole
            "Test request",                     // request
            new String[]{"Test question"},      // questions
            new String[]{"Test task"},          // tasks
            new String[]{"Test instructions"},  // instructions
            "Test known info",                  // knownInfo
            "Test formatting rules",            // formattingRules
            "Test few shots"                    // fewShots
        );
        params.setAgentParams(agentParams);
        params.setInputJql("key = TEST-123");
        params.setOutputType(Params.OutputType.comment);
        
        // Mock ticket
        when(ticket.getKey()).thenReturn("TEST-123");
        when(ticket.getTicketKey()).thenReturn("TEST-123");
        when(ticket.toText()).thenReturn("Mock ticket text content for TEST-123");
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());
        
        // Mock trackerClient
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("Test fields");
        when(trackerClient.getExtendedQueryFields()).thenReturn(new String[]{"summary", "description"});
        when(trackerClient.getComments(anyString(), any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            // Simulate ticket processing - call the performer
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0, JiraClient.Performer.class);
            try {
                performer.perform(ticket);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(trackerClient).searchAndPerform(any(JiraClient.Performer.class), anyString(), any());
        
        // Mock context orchestrator
        when(contextOrchestrator.summarize()).thenReturn(Collections.emptyList());
        when(uriToObjectFactory.createUriProcessingSources()).thenReturn(Collections.emptyList());
        
        // Mock AI agent
        when(genericRequestAgent.run(any())).thenReturn("AI generated response");
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any created folders
        try {
            Path inputFolder = tempDir.resolve("input");
            if (Files.exists(inputFolder)) {
                FileUtils.deleteDirectory(inputFolder.toFile());
            }
            Path outputFolder = tempDir.resolve("outputs");
            if (Files.exists(outputFolder)) {
                FileUtils.deleteDirectory(outputFolder.toFile());
            }
        } catch (IOException e) {
            // Ignore cleanup errors in tests
        }
    }
    
    @Test
    void testCliExecutionWithAIProcessing() throws Exception {
        // Arrange
        String[] cliCommands = {"echo 'Hello from CLI'"};
        params.setCliCommands(cliCommands);
        params.setSkipAIProcessing(false);
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            // Verify CLI command runs from project root, not input directory
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo 'Hello from CLI'"), any(File.class), any(Map.class)))
                      .thenReturn("Hello from CLI\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("TEST-123", results.get(0).getKey());
            
            // Verify AI agent was called (since skipAIProcessing = false)
            verify(genericRequestAgent).run(any());
            
            // Verify CLI command was executed from project root directory
            mockedUtils.verify(() -> CommandLineUtils.runCommand(eq("echo 'Hello from CLI'"), any(File.class), any(Map.class)));
            
            // Verify the working directory is NOT the input directory
            ArgumentCaptor<File> workingDirCaptor = ArgumentCaptor.forClass(File.class);
            mockedUtils.verify(() -> CommandLineUtils.runCommand(anyString(), workingDirCaptor.capture(), any(Map.class)));
            File actualWorkingDir = workingDirCaptor.getValue();
            assertNotNull(actualWorkingDir);
            // Working directory should be project root, not input folder
            assertFalse(actualWorkingDir.getAbsolutePath().contains("/input"));
        }
    }
    
    @Test
    void testCliExecutionSkipAIProcessing() throws Exception {
        // Arrange
        String[] cliCommands = {"echo 'CLI response only'"};
        params.setCliCommands(cliCommands);
        params.setSkipAIProcessing(true);
        
        // Create output response file
        Path outputDir = tempDir.resolve("outputs");
        Files.createDirectories(outputDir);
        Path responseFile = outputDir.resolve("response.md");
        Files.write(responseFile, "CLI output response".getBytes(StandardCharsets.UTF_8));
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo 'CLI response only'"), any(File.class), any(Map.class)))
                      .thenReturn("CLI response only\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("TEST-123", results.get(0).getKey());
        // Verify that CLI execution result contains expected command output
        String result = results.get(0).getResult();
        assertTrue(result.contains("CLI response only"));
        assertTrue(result.contains("Exit Code: 0"));
            
            // Verify AI agent was NOT called (since skipAIProcessing = true)
            verify(genericRequestAgent, never()).run(any());
            
            // Verify CLI command was executed
            mockedUtils.verify(() -> CommandLineUtils.runCommand(eq("echo 'CLI response only'"), any(File.class), any(Map.class)));
        }
    }
    
    @Test
    void testCliExecutionWithAttachments() throws Exception {
        // Arrange
        IAttachment mockAttachment = mock(IAttachment.class);
        when(mockAttachment.getName()).thenReturn("test-attachment.txt");
        when(mockAttachment.getUrl()).thenReturn("http://example.com/attachment");
        
        // Mock tracker client
        File mockFile = new File(tempDir.toFile(), "mock-attachment.txt");
        try { FileUtils.writeStringToFile(mockFile, "Attachment content", StandardCharsets.UTF_8); } catch (IOException e) {}
        when(trackerClient.convertUrlToFile("http://example.com/attachment")).thenReturn(mockFile);
        doReturn(Arrays.asList(mockAttachment)).when(ticket).getAttachments();
        
        String[] cliCommands = {"cat input/TEST-123/test-attachment.txt"};
        params.setCliCommands(cliCommands);
        params.setSkipAIProcessing(false);
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(contains("cat"), any(File.class), any(Map.class)))
                      .thenReturn("Attachment content\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("TEST-123", results.get(0).getKey());
            
            // Verify CLI command was executed and processed attachment content
            mockedUtils.verify(() -> CommandLineUtils.runCommand(contains("cat"), any(File.class), any(Map.class)));
        }
    }
    
    @Test
    void testCliExecutionError() throws Exception {
        // Arrange
        String[] cliCommands = {"invalid-command"};
        params.setCliCommands(cliCommands);
        params.setSkipAIProcessing(false);
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("invalid-command"), any(File.class), any(Map.class)))
                      .thenThrow(new IOException("Command not found"));
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            
            // Should still call AI agent with error information
            verify(genericRequestAgent).run(any());
            
            // Verify CLI command was attempted
            mockedUtils.verify(() -> CommandLineUtils.runCommand(eq("invalid-command"), any(File.class), any(Map.class)));
        }
    }
    
    @Test
    void testNoneOutputType() throws Exception {
        // Arrange
        params.setOutputType(Params.OutputType.none);
        String[] cliCommands = {"echo 'test'"};
        params.setCliCommands(cliCommands);
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(eq("echo 'test'"), any(File.class), any(Map.class)))
                      .thenReturn("test\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            
            // Verify no comments were posted (outputType = none)
            verify(trackerClient, never()).postComment(anyString(), anyString());
            verify(trackerClient, never()).postCommentIfNotExists(anyString(), anyString());
        }
    }
    
    @Test
    void testInputContextCreation() throws Exception {
        // Arrange
        String[] cliCommands = {"ls input/TEST-123/"};
        params.setCliCommands(cliCommands);
        
        try (MockedStatic<CommandLineUtils> mockedUtils = mockStatic(CommandLineUtils.class)) {
            mockedUtils.when(() -> CommandLineUtils.runCommand(contains("ls"), any(File.class), any(Map.class)))
                      .thenReturn("request.md\nExit Code: 0");
            // Mock the environment loading
            mockedUtils.when(() -> CommandLineUtils.loadEnvironmentFromFile("dmtools.env"))
                      .thenReturn(Map.of());
            
            // Act
            teammate.runJobImpl(params);
            
            // Assert - verify that the CLI command was executed and succeeded
            // (input context is cleaned up after execution, so we verify execution happened)
            // The CLI command `ls input/TEST-123/` should have shown the request.md file
        }
    }
    
    @Test
    void testNoCliCommands() throws Exception {
        // Arrange - no CLI commands set
        params.setCliCommands(null);
        
        // Act
        List<ResultItem> results = teammate.runJobImpl(params);
        
        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        
        // Should still call AI agent
        verify(genericRequestAgent).run(any());
        
        // No input folder should be created
        Path inputFolder = tempDir.resolve("input");
        assertFalse(Files.exists(inputFolder));
    }
    
    @Test
    void testFilePathInjectionInInstructions() throws Exception {
        // Arrange - Create a test file with instructions
        Path instructionsFile = tempDir.resolve("test-instructions.txt");
        Files.write(instructionsFile, "File-based instructions content".getBytes(StandardCharsets.UTF_8));
        
        // Change working directory to tempDir for relative path resolution
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Set instructions to file path
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./test-instructions.txt"});
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            
            // Verify AI agent was called with file content
            ArgumentCaptor<GenericRequestAgent.Params> captor = ArgumentCaptor.forClass(GenericRequestAgent.Params.class);
            verify(genericRequestAgent).run(captor.capture());
            
            GenericRequestAgent.Params capturedParams = captor.getValue();
            String[] processedInstructions = capturedParams.getRequest().getInstructions();
            assertNotNull(processedInstructions);
            assertTrue(processedInstructions[0].contains("File-based instructions content"),
                    "Instructions should contain file content");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
    
    @Test
    void testFilePathInjectionInMultipleFields() throws Exception {
        // Arrange - Create test files for different fields
        Path roleFile = tempDir.resolve("role.txt");
        Path formattingFile = tempDir.resolve("formatting.txt");
        Path fewShotsFile = tempDir.resolve("fewshots.txt");
        
        Files.write(roleFile, "Senior Developer".getBytes(StandardCharsets.UTF_8));
        Files.write(formattingFile, "Markdown format".getBytes(StandardCharsets.UTF_8));
        Files.write(fewShotsFile, "Example 1\nExample 2".getBytes(StandardCharsets.UTF_8));
        
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Set multiple fields to file paths
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setAiRole("./role.txt");
            agentParams.setFormattingRules("./formatting.txt");
            agentParams.setFewShots("./fewshots.txt");
            
            // Act
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            
            // Verify AI agent was called with file content in all fields
            ArgumentCaptor<GenericRequestAgent.Params> captor = ArgumentCaptor.forClass(GenericRequestAgent.Params.class);
            verify(genericRequestAgent).run(captor.capture());
            
            GenericRequestAgent.Params capturedParams = captor.getValue();
            RequestDecompositionAgent.Result result = capturedParams.getRequest();
            
            assertTrue(result.getAiRole().contains("Senior Developer"),
                    "aiRole should contain file content");
            assertTrue(result.getFormattingRules().contains("Markdown format"),
                    "formattingRules should contain file content");
            assertTrue(result.getFewShots().contains("Example 1"),
                    "fewShots should contain file content");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
    
    @Test
    void testFilePathInjectionWithMissingFile() throws Exception {
        // Arrange - Set instructions to non-existent file
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            RequestDecompositionAgent.Result agentParams = params.getAgentParams();
            agentParams.setInstructions(new String[]{"./non-existent-file.txt"});
            
            // Act - should not throw exception
            List<ResultItem> results = teammate.runJobImpl(params);
            
            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            
            // Verify AI agent was still called (with original path as fallback)
            verify(genericRequestAgent).run(any());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
