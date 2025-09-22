package com.github.istin.dmtools.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RunCommandProcessorTest {

    @TempDir
    Path tempDir;

    @Mock
    private EncodingDetector mockEncodingDetector;

    @Mock
    private ConfigurationMerger mockConfigurationMerger;

    private RunCommandProcessor runCommandProcessor;
    private RunCommandProcessor runCommandProcessorWithMocks;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runCommandProcessor = new RunCommandProcessor();
        runCommandProcessorWithMocks = new RunCommandProcessor(mockEncodingDetector, mockConfigurationMerger);
    }

    @Test
    void testLoadJsonFromFile_validFile() throws IOException {
        String jsonContent = "{\"name\":\"test\",\"version\":\"1.0\"}";
        Path jsonFile = tempDir.resolve("test.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String result = runCommandProcessor.loadJsonFromFile(jsonFile.toString());
        assertEquals(jsonContent, result);
    }

    @Test
    void testLoadJsonFromFile_fileNotExists() {
        String nonExistentFile = tempDir.resolve("nonexistent.json").toString();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.loadJsonFromFile(nonExistentFile);
        });
        
        assertTrue(exception.getMessage().contains("Configuration file does not exist"));
    }

    @Test
    void testLoadJsonFromFile_emptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.json");
        Files.write(emptyFile, "".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.loadJsonFromFile(emptyFile.toString());
        });
        
        assertTrue(exception.getMessage().contains("Configuration file is empty"));
    }

    @Test
    void testLoadJsonFromFile_nullPath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.loadJsonFromFile(null);
        });
        
        assertTrue(exception.getMessage().contains("File path cannot be null or empty"));
    }

    @Test
    void testLoadJsonFromFile_emptyPath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.loadJsonFromFile("");
        });
        
        assertTrue(exception.getMessage().contains("File path cannot be null or empty"));
    }

    @Test
    void testProcessRunCommand_fileOnly() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String[] args = {"run", jsonFile.toString()};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
    }

    @Test
    void testProcessRunCommand_fileWithEncodedParam() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"original\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String encodedOverride = "{\"params\":{\"question\":\"updated\"}}";
        String base64Encoded = Base64.getEncoder().encodeToString(encodedOverride.getBytes());
        String mergedJson = "{\"name\":\"expert\",\"params\":{\"question\":\"updated\"}}";

        // Mock the dependencies
        when(mockEncodingDetector.autoDetectAndDecode(base64Encoded)).thenReturn(encodedOverride);
        when(mockConfigurationMerger.mergeConfigurations(jsonContent, encodedOverride)).thenReturn(mergedJson);

        String[] args = {"run", jsonFile.toString(), base64Encoded};
        
        JobParams result = runCommandProcessorWithMocks.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
        
        // Verify interactions
        verify(mockEncodingDetector).autoDetectAndDecode(base64Encoded);
        verify(mockConfigurationMerger).mergeConfigurations(jsonContent, encodedOverride);
    }

    @Test
    void testProcessRunCommand_invalidArguments() {
        // Test null arguments
        assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.processRunCommand(null);
        });

        // Test too few arguments
        String[] tooFewArgs = {"run"};
        assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.processRunCommand(tooFewArgs);
        });

        // Test wrong first argument
        String[] wrongCommand = {"execute", "file.json"};
        assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessor.processRunCommand(wrongCommand);
        });
    }

    @Test
    void testProcessRunCommand_fileProcessingError() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String encodedParam = "encoded-data";
        
        // Mock encoding detector to throw exception
        when(mockEncodingDetector.autoDetectAndDecode(encodedParam))
            .thenThrow(new IllegalArgumentException("Invalid encoding"));

        String[] args = {"run", jsonFile.toString(), encodedParam};
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessorWithMocks.processRunCommand(args);
        });
        
        assertTrue(exception.getMessage().contains("Run command processing failed"));
        assertTrue(exception.getCause().getMessage().contains("Invalid encoding"));
    }

    @Test
    void testProcessRunCommand_mergeError() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String encodedParam = "encoded-data";
        String decodedParam = "{\"invalid\": json}";
        
        // Mock successful decoding but failed merging
        when(mockEncodingDetector.autoDetectAndDecode(encodedParam)).thenReturn(decodedParam);
        when(mockConfigurationMerger.mergeConfigurations(jsonContent, decodedParam))
            .thenThrow(new IllegalArgumentException("Invalid JSON format"));

        String[] args = {"run", jsonFile.toString(), encodedParam};
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            runCommandProcessorWithMocks.processRunCommand(args);
        });
        
        assertTrue(exception.getMessage().contains("Run command processing failed"));
        assertTrue(exception.getCause().getMessage().contains("Invalid JSON format"));
    }

    @Test
    void testProcessRunCommand_emptyEncodedParam() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String[] args = {"run", jsonFile.toString(), ""};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
        // Should use file content only, no encoding/merging should occur
    }

    @Test
    void testProcessRunCommand_whitespaceEncodedParam() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("job.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String[] args = {"run", jsonFile.toString(), "   "};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
        // Should use file content only, no encoding/merging should occur
    }

    @Test
    void testProcessRunCommand_complexJobConfig() throws IOException {
        String complexJson = "{" +
            "\"name\":\"documentationgenerator\"," +
            "\"params\":{" +
                "\"outputPath\":\"docs/\"," +
                "\"includeTests\":true," +
                "\"format\":\"markdown\"" +
            "}" +
        "}";
        Path jsonFile = tempDir.resolve("complex-job.json");
        Files.write(jsonFile, complexJson.getBytes());

        String[] args = {"run", jsonFile.toString()};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("documentationgenerator", result.getName());
    }

    @Test
    void testProcessRunCommand_integrationWithRealComponents() throws IOException {
        // Test with real components (not mocked) for integration testing
        String fileJson = "{\"name\":\"expert\",\"timeout\":30,\"features\":[\"logging\"]}";
        Path jsonFile = tempDir.resolve("integration-test.json");
        Files.write(jsonFile, fileJson.getBytes());

        String overrideJson = "{\"timeout\":60,\"debug\":true}";
        String base64Encoded = Base64.getEncoder().encodeToString(overrideJson.getBytes());

        String[] args = {"run", jsonFile.toString(), base64Encoded};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
        
        // The merged configuration should reflect the override
        // This is testing the actual integration of all components
    }

    @Test
    void testLoadJsonFromFile_largeFile() throws IOException {
        // Test with a reasonably large JSON file
        StringBuilder largeJson = new StringBuilder("{\"name\":\"test\",\"data\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append("{\"id\":").append(i).append(",\"value\":\"item").append(i).append("\"}");
        }
        largeJson.append("]}");

        Path largeFile = tempDir.resolve("large.json");
        Files.write(largeFile, largeJson.toString().getBytes());

        String result = runCommandProcessor.loadJsonFromFile(largeFile.toString());
        assertEquals(largeJson.toString(), result);
    }

    @Test
    void testProcessRunCommand_pathWithSpaces() throws IOException {
        String jsonContent = "{\"name\":\"expert\",\"params\":{\"question\":\"test\"}}";
        Path jsonFile = tempDir.resolve("file with spaces.json");
        Files.write(jsonFile, jsonContent.getBytes());

        String[] args = {"run", jsonFile.toString()};
        
        JobParams result = runCommandProcessor.processRunCommand(args);
        
        assertNotNull(result);
        assertEquals("expert", result.getName());
    }
}
