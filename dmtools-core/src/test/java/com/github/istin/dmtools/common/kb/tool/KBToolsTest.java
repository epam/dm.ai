package com.github.istin.dmtools.common.kb.tool;

import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.agent.KBOrchestrator;
import com.github.istin.dmtools.common.kb.model.KBResult;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KBToolsTest {

    private KBTools kbTools;
    private KBOrchestrator mockOrchestrator;
    private PropertyReader mockPropertyReader;
    private SourceConfigManager mockSourceConfigManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockOrchestrator = mock(KBOrchestrator.class);
        mockPropertyReader = mock(PropertyReader.class);
        mockSourceConfigManager = mock(SourceConfigManager.class);
        
        kbTools = new KBTools(mockOrchestrator, mockPropertyReader, mockSourceConfigManager);
        
        // Mock property reader to return temp directory
        when(mockPropertyReader.getValue("DMTOOLS_KB_OUTPUT_PATH"))
            .thenReturn(tempDir.toString());
    }

    // ========== kbProcessInbox Tests ==========

    @Test
    void testProcessInbox_NoInboxDirectory() throws Exception {
        // Test when inbox/raw doesn't exist
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("No inbox/raw directory found"));
        assertTrue(result.contains("\"processed\": []"));
        assertTrue(result.contains("\"skipped\": []"));
    }

    @Test
    void testProcessInbox_EmptyInbox() throws Exception {
        // Create empty inbox/raw
        Files.createDirectories(tempDir.resolve("inbox/raw"));
        
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"processed\": []"));
        assertTrue(result.contains("\"skipped\": []"));
    }

    @Test
    void testProcessInbox_ProcessNewFile() throws Exception {
        // Setup: Create inbox structure with one unprocessed file
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        
        Path testFile = inboxRaw.resolve("1234567890-messages.json");
        Files.writeString(testFile, "{\"messages\": []}");
        
        // Mock successful processing
        KBResult mockResult = createMockResult(true, "Success", 5, 3, 2);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"source\": \"teams_messages\""));
        assertTrue(result.contains("\"file\": \"1234567890-messages.json\""));
        assertTrue(result.contains("\"questions\": 5"));
        assertTrue(result.contains("\"answers\": 3"));
        assertTrue(result.contains("\"notes\": 2"));
        assertTrue(result.contains("Processed 1 files"));
        
        // Verify orchestrator was called twice (PROCESS_ONLY + AGGREGATE_ONLY)
        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator, times(2)).run(paramsCaptor.capture());
        
        // Check the first call (PROCESS_ONLY)
        KBOrchestratorParams firstCall = paramsCaptor.getAllValues().get(0);
        assertEquals("teams_messages", firstCall.getSourceName());
        assertTrue(firstCall.getInputFile().contains("1234567890-messages.json"));
        assertNotNull(firstCall.getDateTime());
    }

    @Test
    void testProcessInbox_SkipAlreadyProcessedFile() throws Exception {
        // Setup: Create inbox structure with processed file
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Path inboxAnalyzed = tempDir.resolve("inbox/analyzed/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.createDirectories(inboxAnalyzed);
        
        Path testFile = inboxRaw.resolve("1234567890-messages.json");
        Files.writeString(testFile, "{\"messages\": []}");
        
        // Create analyzed tracking file
        Path analyzedFile = inboxAnalyzed.resolve("1234567890-messages_analyzed.json");
        Files.writeString(analyzedFile, "{\"processed\": true}");
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"skipped\""));
        assertTrue(result.contains("\"source\": \"teams_messages\""));
        assertTrue(result.contains("\"file\": \"1234567890-messages.json\""));
        assertTrue(result.contains("Already processed"));
        
        // Verify orchestrator was NOT called
        verify(mockOrchestrator, never()).run(any());
    }

    @Test
    void testProcessInbox_MultipleSources() throws Exception {
        // Setup: Create multiple source folders
        Path teamsFolder = tempDir.resolve("inbox/raw/teams_messages");
        Path meetingFolder = tempDir.resolve("inbox/raw/meeting_notes");
        Files.createDirectories(teamsFolder);
        Files.createDirectories(meetingFolder);
        
        Files.writeString(teamsFolder.resolve("msg1.json"), "{}");
        Files.writeString(meetingFolder.resolve("notes.txt"), "Meeting notes");
        
        // Mock successful processing
        KBResult mockResult1 = createMockResult(true, "Success", 3, 2, 1);
        KBResult mockResult2 = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenReturn(mockResult1)
            .thenReturn(mockResult2);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 2 files"));
        assertTrue(result.contains("teams_messages"));
        assertTrue(result.contains("meeting_notes"));
        
        // Verify orchestrator called 3 times (2 PROCESS_ONLY + 1 AGGREGATE_ONLY)
        verify(mockOrchestrator, times(3)).run(any(KBOrchestratorParams.class));
    }

    @Test
    void testProcessInbox_ProcessingError() throws Exception {
        // Setup: Create file that will fail processing
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        
        Path testFile = inboxRaw.resolve("bad-file.json");
        Files.writeString(testFile, "{}");
        
        // Mock failed processing
        KBResult mockResult = createMockResult(false, "Processing failed", 0, 0, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true")); // Overall success
        assertTrue(result.contains("\"skipped\""));
        assertTrue(result.contains("\"source\": \"teams_messages\""));
        assertTrue(result.contains("\"file\": \"bad-file.json\""));
        assertTrue(result.contains("Processing failed"));
    }

    @Test
    void testProcessInbox_OrchestratorException() throws Exception {
        // Setup: Create file that will throw exception
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        
        Path testFile = inboxRaw.resolve("exception-file.json");
        Files.writeString(testFile, "{}");
        
        // Mock exception
        when(mockOrchestrator.run(any(KBOrchestratorParams.class)))
            .thenThrow(new RuntimeException("Test exception"));
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true")); // Overall success despite individual failure
        assertTrue(result.contains("\"skipped\""));
        assertTrue(result.contains("\"source\": \"teams_messages\""));
        assertTrue(result.contains("\"file\": \"exception-file.json\""));
        assertTrue(result.contains("Error"));
    }

    @Test
    void testProcessInbox_MixedProcessedAndUnprocessed() throws Exception {
        // Setup: Create mix of processed and unprocessed files
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Path inboxAnalyzed = tempDir.resolve("inbox/analyzed/teams_messages");
        Files.createDirectories(inboxRaw);
        Files.createDirectories(inboxAnalyzed);
        
        // Unprocessed file
        Files.writeString(inboxRaw.resolve("new-file.json"), "{}");
        
        // Already processed file
        Files.writeString(inboxRaw.resolve("old-file.json"), "{}");
        Files.writeString(inboxAnalyzed.resolve("old-file_analyzed.json"), "{}");
        
        // Mock successful processing for new file
        KBResult mockResult = createMockResult(true, "Success", 2, 1, 1);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 1 files, skipped 1 files"));
        assertTrue(result.contains("new-file.json"));
        assertTrue(result.contains("old-file.json"));
        
        // Verify orchestrator called twice (PROCESS_ONLY + AGGREGATE_ONLY)
        verify(mockOrchestrator, times(2)).run(any());
    }

    @Test
    void testProcessInbox_FileWithoutExtension() throws Exception {
        // Setup: Create file without extension
        Path inboxRaw = tempDir.resolve("inbox/raw/docs");
        Files.createDirectories(inboxRaw);
        
        Path testFile = inboxRaw.resolve("README");
        Files.writeString(testFile, "Documentation");
        
        // Mock successful processing
        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"file\": \"README\""));
        
        verify(mockOrchestrator, times(2)).run(any());
    }

    @Test
    void testProcessInbox_MultipleFilesInOneSource() throws Exception {
        // Setup: Create multiple files in one source
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        
        Files.writeString(inboxRaw.resolve("file1.json"), "{}");
        Files.writeString(inboxRaw.resolve("file2.json"), "{}");
        Files.writeString(inboxRaw.resolve("file3.json"), "{}");
        
        // Mock successful processing
        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 3 files"));
        assertTrue(result.contains("file1.json"));
        assertTrue(result.contains("file2.json"));
        assertTrue(result.contains("file3.json"));
        
        // Verify orchestrator called 4 times (3 PROCESS_ONLY + 1 AGGREGATE_ONLY)
        verify(mockOrchestrator, times(4)).run(any());
    }

    @Test
    void testProcessInbox_EmptySourceFolder() throws Exception {
        // Setup: Create empty source folder
        Path inboxRaw = tempDir.resolve("inbox/raw/empty_source");
        Files.createDirectories(inboxRaw);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"processed\": []"));
        assertTrue(result.contains("\"skipped\": []"));
        
        // Verify orchestrator was NOT called
        verify(mockOrchestrator, never()).run(any());
    }

    @Test
    void testProcessInbox_SubdirectoriesIgnored() throws Exception {
        // Setup: Create subdirectories (should be ignored)
        Path inboxRaw = tempDir.resolve("inbox/raw/teams_messages");
        Files.createDirectories(inboxRaw);
        
        // Create a subdirectory
        Files.createDirectories(inboxRaw.resolve("subdir"));
        Files.writeString(inboxRaw.resolve("subdir/nested.json"), "{}");
        
        // Create a regular file
        Files.writeString(inboxRaw.resolve("regular.json"), "{}");
        
        // Mock successful processing
        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        String result = kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("Processed 1 files")); // Only regular file
        assertTrue(result.contains("regular.json"));
        assertFalse(result.contains("nested.json")); // Nested file ignored
        
        // Verify orchestrator called twice (PROCESS_ONLY + AGGREGATE_ONLY)
        verify(mockOrchestrator, times(2)).run(any());
    }

    @Test
    void testProcessInbox_NullOutputPath() throws Exception {
        // Test with null output path (should use default from property reader)
        String result = kbTools.kbProcessInbox(null, null, null);
        
        assertNotNull(result);
        // Should still work with default path from property reader
        verify(mockPropertyReader).getValue("DMTOOLS_KB_OUTPUT_PATH");
    }

    @Test
    void testProcessInbox_EmptyOutputPath() throws Exception {
        // Test with empty output path (should use default)
        String result = kbTools.kbProcessInbox("", null, null);
        
        assertNotNull(result);
        verify(mockPropertyReader).getValue("DMTOOLS_KB_OUTPUT_PATH");
    }

    @Test
    void testProcessInbox_SourceNameSanitization() throws Exception {
        // Test that source names are used as-is (sanitization happens in JS)
        Path inboxRaw = tempDir.resolve("inbox/raw/project_team");
        Files.createDirectories(inboxRaw);
        
        Files.writeString(inboxRaw.resolve("msg.json"), "{}");
        
        // Mock successful processing
        KBResult mockResult = createMockResult(true, "Success", 1, 1, 0);
        when(mockOrchestrator.run(any(KBOrchestratorParams.class))).thenReturn(mockResult);
        
        // Execute
        kbTools.kbProcessInbox(tempDir.toString(), null, null);
        
        // Verify source name is preserved (check first call - PROCESS_ONLY)
        ArgumentCaptor<KBOrchestratorParams> paramsCaptor = ArgumentCaptor.forClass(KBOrchestratorParams.class);
        verify(mockOrchestrator, times(2)).run(paramsCaptor.capture());
        
        assertEquals("project_team", paramsCaptor.getAllValues().get(0).getSourceName());
    }

    // ========== Helper Methods ==========

    /**
     * Create a mock KBResult for testing
     */
    private KBResult createMockResult(boolean success, String message, int questions, int answers, int notes) {
        KBResult result = mock(KBResult.class);
        when(result.isSuccess()).thenReturn(success);
        when(result.getMessage()).thenReturn(message);
        when(result.getQuestionsCount()).thenReturn(questions);
        when(result.getAnswersCount()).thenReturn(answers);
        when(result.getNotesCount()).thenReturn(notes);
        when(result.getTopicsCount()).thenReturn(0);
        when(result.getAreasCount()).thenReturn(0);
        when(result.getPeopleCount()).thenReturn(0);
        return result;
    }
}

