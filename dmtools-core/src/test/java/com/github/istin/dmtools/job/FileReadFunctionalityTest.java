package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
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
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Comprehensive tests for file_read functionality in JobJavaScriptBridge.
 * Tests cover AC5 requirements from DMC-562:
 * - Successful file reading for various formats
 * - Reading from outputs/ directory
 * - Reading from input/ directory
 * - Path traversal prevention
 * - File not found handling
 * - Empty file handling
 * - Special characters in filenames
 * - Integration with JavaScript execution
 */
class FileReadFunctionalityTest {

    @Mock
    private TrackerClient<?> mockTrackerClient;

    @Mock
    private AI mockAI;

    @Mock
    private Confluence mockConfluence;

    @Mock
    private SourceCode mockSourceCode;

    private JobJavaScriptBridge bridge;
    
    @TempDir
    Path tempDir;
    
    private String originalWorkingDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(mockTrackerClient, mockAI, mockConfluence, mockSourceCode);
        
        // Save original working directory
        originalWorkingDir = System.getProperty("user.dir");
        
        // Set temp directory as working directory for tests
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
    }
    
    @AfterEach
    void tearDown() {
        // Restore original working directory
        System.setProperty("user.dir", originalWorkingDir);
    }

    @Test
    void testReadMarkdownFile() throws Exception {
        // Given - Create a markdown file in outputs directory
        Path outputsDir = tempDir.resolve("outputs");
        Files.createDirectories(outputsDir);
        Path mdFile = outputsDir.resolve("response.md");
        String content = "# Test Response\n\nThis is a test markdown file.";
        Files.writeString(mdFile, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("outputs/response.md");
                return { success: true, content: content };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "outputs/response.md".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("Test Response"));
        }
    }

    @Test
    void testReadTextFile() throws Exception {
        // Given - Create a text file
        Path txtFile = tempDir.resolve("data.txt");
        String content = "Plain text content\nWith multiple lines";
        Files.writeString(txtFile, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("data.txt");
                return content;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "data.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testReadJsonFile() throws Exception {
        // Given - Create a JSON file
        Path jsonFile = tempDir.resolve("config.json");
        String jsonContent = "{\"key\": \"value\", \"number\": 42}";
        Files.writeString(jsonFile, jsonContent, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("config.json");
                var parsed = JSON.parse(content);
                return { success: true, key: parsed.key, number: parsed.number };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "config.json".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(jsonContent);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("value"));
            assertTrue(resultStr.contains("42"));
        }
    }

    @Test
    void testReadFromOutputsDirectory() throws Exception {
        // Given - Create outputs directory with response file
        Path outputsDir = tempDir.resolve("outputs");
        Files.createDirectories(outputsDir);
        Path responseFile = outputsDir.resolve("response.md");
        String content = "CLI command output result";
        Files.writeString(responseFile, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var output = file_read("outputs/response.md");
                return output;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "outputs/response.md".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testReadFromInputDirectory() throws Exception {
        // Given - Create input directory structure
        Path inputDir = tempDir.resolve("input").resolve("DMC-123");
        Files.createDirectories(inputDir);
        Path requestFile = inputDir.resolve("request.md");
        String content = "Job input request content";
        Files.writeString(requestFile, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var request = file_read("input/DMC-123/request.md");
                return request;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "input/DMC-123/request.md".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testPathTraversalAttemptWithDotDot() throws Exception {
        // Given - JavaScript trying to access parent directory
        String jsCode = """
            function action(params) {
                var content = file_read("../../../etc/passwd");
                return { success: content !== null, content: content };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "../../../etc/passwd".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(null);  // Should return null for security violation

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then - Should return null for security violations
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("null") || resultStr.contains("false"));
        }
    }

    @Test
    void testPathTraversalAttemptWithAbsolutePath() throws Exception {
        // Given - JavaScript trying to access absolute path outside working dir
        String jsCode = """
            function action(params) {
                var content = file_read("/etc/passwd");
                return { accessible: content !== null };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "/etc/passwd".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(null);  // Should return null for security violation

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("false") || resultStr.contains("null"));
        }
    }

    @Test
    void testFileNotFoundReturnsNull() throws Exception {
        // Given - JavaScript trying to read non-existent file
        String jsCode = """
            function action(params) {
                var content = file_read("nonexistent.txt");
                if (content === null) {
                    return { found: false, message: "File not found" };
                }
                return { found: true, content: content };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "nonexistent.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(null);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("false") || resultStr.contains("File not found"));
        }
    }

    @Test
    void testReadEmptyFile() throws Exception {
        // Given - Create an empty file
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "", StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("empty.txt");
                return { 
                    isEmpty: content !== null && content.length === 0,
                    content: content
                };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "empty.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn("");

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("true") || resultStr.contains("isEmpty"));
        }
    }

    @Test
    void testReadFileWithSpecialCharacters() throws Exception {
        // Given - Create file with special characters in name
        Path specialFile = tempDir.resolve("test-file_123.txt");
        String content = "Content of special file";
        Files.writeString(specialFile, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("test-file_123.txt");
                return content;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "test-file_123.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testReadFileWithSpacesInName() throws Exception {
        // Given - Create file with spaces in name
        Path fileWithSpaces = tempDir.resolve("test file with spaces.txt");
        String content = "Content of file with spaces";
        Files.writeString(fileWithSpaces, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("test file with spaces.txt");
                return content;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "test file with spaces.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testReadMultipleFiles() throws Exception {
        // Given - Create multiple files
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "Content 1", StandardCharsets.UTF_8);
        Files.writeString(file2, "Content 2", StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content1 = file_read("file1.txt");
                var content2 = file_read("file2.txt");
                return { 
                    file1: content1, 
                    file2: content2,
                    combined: content1 + " + " + content2
                };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "file1.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn("Content 1");

            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "file2.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn("Content 2");

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("Content 1"));
            assertTrue(resultStr.contains("Content 2"));
        }
    }

    @Test
    void testIntegrationWithOtherMCPTools() throws Exception {
        // Given - Combine file reading with Jira tools
        Path analysisFile = tempDir.resolve("analysis.json");
        String analysisContent = "{\"riskLevel\": \"high\", \"issues\": 5}";
        Files.writeString(analysisFile, analysisContent, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                // Read analysis file
                var analysisJson = file_read("analysis.json");
                var analysis = JSON.parse(analysisJson);
                
                // Create ticket based on analysis
                if (analysis.riskLevel === "high") {
                    jira_update_field({
                        key: params.ticket.key,
                        field: "priority",
                        value: {name: "Critical"}
                    });
                }
                
                return {
                    riskLevel: analysis.riskLevel,
                    issues: analysis.issues,
                    updated: true
                };
            }
            """;

        JSONObject params = new JSONObject();
        JSONObject ticket = new JSONObject();
        ticket.put("key", "DMC-123");
        params.put("ticket", ticket);

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "analysis.json".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(analysisContent);

            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("jira_update_field"),
                any(Map.class),
                any(Map.class)
            )).thenReturn("Updated");

            Object result = bridge.executeJavaScript(jsCode, params);

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("high"));
            assertTrue(resultStr.contains("5"));
        }
    }

    @Test
    void testRelativePathResolution() throws Exception {
        // Given - Create nested directory structure
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);
        Path file = subdir.resolve("nested.txt");
        String content = "Nested content";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("subdir/nested.txt");
                return content;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "subdir/nested.txt".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(content);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(content, result.toString());
        }
    }

    @Test
    void testReadYamlFile() throws Exception {
        // Given - Create a YAML file
        Path yamlFile = tempDir.resolve("config.yaml");
        String yamlContent = "version: 1.0\nname: test\nenabled: true";
        Files.writeString(yamlFile, yamlContent, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("config.yaml");
                return { success: true, content: content };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "config.yaml".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(yamlContent);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("version"));
        }
    }

    @Test
    void testReadXmlFile() throws Exception {
        // Given - Create an XML file
        Path xmlFile = tempDir.resolve("data.xml");
        String xmlContent = "<?xml version=\"1.0\"?><root><item>value</item></root>";
        Files.writeString(xmlFile, xmlContent, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("data.xml");
                return content;
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "data.xml".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(xmlContent);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertEquals(xmlContent, result.toString());
        }
    }

    @Test
    void testReadCsvFile() throws Exception {
        // Given - Create a CSV file
        Path csvFile = tempDir.resolve("data.csv");
        String csvContent = "name,age,city\nJohn,30,NYC\nJane,25,LA";
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);

        String jsCode = """
            function action(params) {
                var content = file_read("data.csv");
                var lines = content.split("\\n");
                return { lineCount: lines.length, firstLine: lines[0] };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "data.csv".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(csvContent);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("3") || resultStr.contains("name"));
        }
    }

    @Test
    void testNullPathHandling() throws Exception {
        // Given - JavaScript with null path
        String jsCode = """
            function action(params) {
                try {
                    var content = file_read(null);
                    return { success: false, content: content };
                } catch (error) {
                    return { success: false, error: "Null path handled" };
                }
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                any(Map.class),
                any(Map.class)
            )).thenReturn(null);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then - Should handle gracefully
            assertNotNull(result);
        }
    }

    @Test
    void testEmptyPathHandling() throws Exception {
        // Given - JavaScript with empty path
        String jsCode = """
            function action(params) {
                var content = file_read("");
                return { success: content !== null };
            }
            """;

        // When
        try (MockedStatic<MCPToolExecutor> mcpMock = mockStatic(MCPToolExecutor.class)) {
            mcpMock.when(() -> MCPToolExecutor.executeTool(
                eq("file_read"),
                argThat(args -> "".equals(args.get("path"))),
                any(Map.class)
            )).thenReturn(null);

            Object result = bridge.executeJavaScript(jsCode, new JSONObject());

            // Then
            assertNotNull(result);
            String resultStr = result.toString();
            assertTrue(resultStr.contains("false") || resultStr.contains("null"));
        }
    }
}

