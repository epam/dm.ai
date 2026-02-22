package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.github.GitHub;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InstructionProcessor
 * Tests file path expansion, Confluence URL handling, and plain text pass-through
 */
class InstructionProcessorTest {

    @Mock
    private Confluence confluence;

    private InstructionProcessor processor;
    private Gson gson;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Test
    void testPlainTextPassthrough() throws IOException {
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {"Plain instruction text", "Another plain text"};
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(2, result.length);
        assertEquals("Plain instruction text", result[0]);
        assertEquals("Another plain text", result[1]);
    }

    @Test
    void testFilePathExpansion() throws IOException {
        // Create test file
        Path testFile = tempDir.resolve("test_instruction.md");
        String fileContent = "This is test instruction content\nLine 2\nLine 3";
        Files.writeString(testFile, fileContent);
        
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {"./test_instruction.md"};
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(1, result.length);
        assertTrue(result[0].contains(fileContent));
    }

    @Test
    void testAbsoluteFilePath() throws IOException {
        // Create test file
        Path testFile = tempDir.resolve("absolute_test.md");
        String fileContent = "Absolute path content";
        Files.writeString(testFile, fileContent);
        
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {testFile.toString()};
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(1, result.length);
        assertTrue(result[0].contains(fileContent));
    }

    @Test
    void testMissingFileFallback() throws IOException {
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {"./missing_file.md"};
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(1, result.length);
        assertEquals("./missing_file.md", result[0]);
    }

    @Test
    void testMixedInstructions() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("common/media_handling.md");
        Files.createDirectories(file1.getParent());
        Files.writeString(file1, "Media handling instructions");
        
        Path file2 = tempDir.resolve("common/jira_context.md");
        Files.writeString(file2, "Jira context instructions");
        
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {
            "https://confluence.example.com/page",
            "./common/media_handling.md",
            "Plain text instruction",
            "./common/jira_context.md",
            "**IMPORTANT** Another plain instruction"
        };
        
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(5, result.length);
        // Confluence URL (will be mocked, but structure preserved)
        assertTrue(result[0].contains("https://"));
        // File references expanded
        assertTrue(result[1].contains("Media handling instructions"));
        // Plain text preserved
        assertEquals("Plain text instruction", result[2]);
        assertTrue(result[3].contains("Jira context instructions"));
        assertEquals("**IMPORTANT** Another plain instruction", result[4]);
    }

    @Test
    void testNullInputArray() throws IOException {
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] result = processor.extractIfNeeded((String[]) null);
        
        assertEquals(0, result.length);
    }

    @Test
    void testEmptyInstructions() throws IOException {
        processor = new InstructionProcessor(null, tempDir.toString());
        
        String[] input = {};
        String[] result = processor.extractIfNeeded(input);
        
        assertEquals(0, result.length);
    }

    @Test
    void testExpandTeammateConfig() throws IOException {
        // Create instruction files
        Path commonDir = tempDir.resolve("instructions/common");
        Files.createDirectories(commonDir);
        
        Files.writeString(commonDir.resolve("response_output.md"),
            "**IMPORTANT** You must write response to the request to output/response.md according to formatting rules");
        Files.writeString(commonDir.resolve("no_development.md"), 
            "**IMPORTANT** Your task is not development and implementation, only assessment/analysis/enhancement of the description.");
        Files.writeString(commonDir.resolve("preserve_references.md"), 
            "**IMPORTANT** You must keep exact syntax and references to attachments if there are any in description of the ticket.");
        Files.writeString(commonDir.resolve("media_handling.md"), 
            "if you can't read file yourself for instance images you must use the terminal (CLI) command");
        Files.writeString(commonDir.resolve("jira_context.md"), 
            "**IMPORTANT** You must check child tickets and parent story via following command to get better context");
        
        processor = new InstructionProcessor(null, tempDir.toString());
        
        // Simulate config instructions
        String[] instructions = {
            "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11403369/Template+SD+API",
            "./instructions/common/response_output.md",
            "**IMPORTANT** your role is to enhance SD API ticket description with comprehensive technical details!",
            "./instructions/common/no_development.md",
            "./instructions/common/preserve_references.md",
            "./instructions/common/media_handling.md",
            "./instructions/common/jira_context.md",
            "Content from the response.md file will be parsed via JSON Parser for automated description update",
            "**IMPORTANT** Return JSON format: {\"description\": \"enhanced description\", \"apiSubtaskCreation\": boolean}"
        };
        
        String[] expanded = processor.extractIfNeeded(instructions);
        
        // Verify expansion
        assertEquals(9, expanded.length);
        assertTrue(expanded[0].contains("https://"));
        assertTrue(expanded[1].contains("output/response.md"));
        assertEquals(instructions[2], expanded[2]); // Plain text unchanged
        assertTrue(expanded[3].contains("not development"));
        assertTrue(expanded[4].contains("keep exact syntax"));
        assertTrue(expanded[5].contains("terminal (CLI)"));
        assertTrue(expanded[6].contains("check child tickets"));
        assertEquals(instructions[7], expanded[7]); // Plain text unchanged
        assertEquals(instructions[8], expanded[8]); // Plain text unchanged
    }

    @Test
    void testPrintExpandedConfig() throws IOException {
        // Create instruction files
        Path commonDir = tempDir.resolve("instructions/common");
        Files.createDirectories(commonDir);
        
        Files.writeString(commonDir.resolve("response_output.md"),
            "Write response to output/response.md");
        Files.writeString(commonDir.resolve("json_validation.md"), 
            "Response must be valid JSON");
        
        processor = new InstructionProcessor(null, tempDir.toString());
        
        // Create a simple config structure using all-args constructor
        RequestDecompositionAgent.Result agentParams = new RequestDecompositionAgent.Result(
            "Senior Software Architect",                    // aiRole
            "Enhance ticket description",                   // request
            null,                                           // questions
            null,                                           // tasks
            new String[]{                                   // instructions
                "./instructions/common/response_output.md",
                "Custom instruction for this job",
                "./instructions/common/json_validation.md"
            },
            "Context about the project",                    // knownInfo
            "Output must follow template format",           // formattingRules
            ""                                              // fewShots
        );
        
        // Expand instructions
        String[] expandedInstructions = processor.extractIfNeeded(agentParams.getInstructions());
        agentParams.setInstructions(expandedInstructions);
        
        // Print as JSON
        String json = gson.toJson(agentParams);
        System.out.println("\n" + "=".repeat(70));
        System.out.println("EXPANDED TEAMMATE CONFIG (agentParams)");
        System.out.println("=".repeat(70));
        System.out.println(json);
        System.out.println("=".repeat(70) + "\n");
        
        // Verify expansion
        assertNotNull(json);
        assertTrue(json.contains("Write response to output/response.md"));
        assertTrue(json.contains("Custom instruction for this job"));
        assertTrue(json.contains("Response must be valid JSON"));
    }

    @Test
    void testRelativePathResolution() throws IOException {
        // Create nested directory structure
        Path subDir = tempDir.resolve("agents/instructions/common");
        Files.createDirectories(subDir);
        Path testFile = subDir.resolve("test.md");
        Files.writeString(testFile, "Test content");
        
        processor = new InstructionProcessor(null, tempDir.resolve("agents").toString());
        
        String[] input = {"./instructions/common/test.md"};
        String[] result = processor.extractIfNeeded(input);
        
        assertTrue(result[0].contains("Test content"));
    }

    @Test
    void testParentDirectoryPath() throws IOException {
        // Create parent directory structure
        Path parentFile = tempDir.resolve("parent_instruction.md");
        Files.writeString(parentFile, "Parent directory content");
        
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        
        processor = new InstructionProcessor(null, subDir.toString());
        
        String[] input = {"../parent_instruction.md"};
        String[] result = processor.extractIfNeeded(input);
        
        assertTrue(result[0].contains("Parent directory content"));
    }

    @Test
    void testFormattingRulesAndFewShotsExpansion() throws IOException {
        // Create instruction files for development config
        Path devDir = tempDir.resolve("instructions/development");
        Files.createDirectories(devDir);
        
        Files.writeString(devDir.resolve("formatting_rules.md"),
            "output/response.md must be a markdown document with sections: ## Issues/Notes (if any), ## Approach, ## Files Modified, ## Test Coverage");
        
        Files.writeString(devDir.resolve("few_shots.md"), 
            "## Issues/Notes\nAll acceptance criteria implemented successfully.\n\n## Approach\nImplemented the feature...");

        processor = new InstructionProcessor(null, tempDir.toString());

        // Simulate what Teammate does - expand formattingRules and fewShots
        String formattingRules = "./instructions/development/formatting_rules.md";
        String fewShots = "./instructions/development/few_shots.md";

        String[] expandedFormatting = processor.extractIfNeeded(formattingRules);
        String[] expandedFewShots = processor.extractIfNeeded(fewShots);

        // Verify expansion
        assertEquals(1, expandedFormatting.length);
        assertTrue(expandedFormatting[0].contains("output/response.md must be a markdown document"));
        assertTrue(expandedFormatting[0].contains("## Issues/Notes (if any)"));
        
        assertEquals(1, expandedFewShots.length);
        assertTrue(expandedFewShots[0].contains("## Issues/Notes"));
        assertTrue(expandedFewShots[0].contains("## Approach"));
        assertTrue(expandedFewShots[0].contains("Implemented the feature"));

        // Print the expanded config
        System.out.println("\n" + "=".repeat(70));
        System.out.println("EXPANDED FORMATTING RULES AND FEW SHOTS");
        System.out.println("=".repeat(70));
        System.out.println("FormattingRules: " + expandedFormatting[0]);
        System.out.println("\nFewShots: " + expandedFewShots[0]);
        System.out.println("=".repeat(70) + "\n");
    }


    // ---- GitHub URL support ----

    @Test
    void testIsGithubUrl_BlobUrl() {
        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString());
        assertTrue(p.isGithubUrl("https://github.com/IstiN/dmtools/blob/main/CLAUDE.md"));
    }

    @Test
    void testIsGithubUrl_RawUrl() {
        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString());
        assertTrue(p.isGithubUrl("https://raw.githubusercontent.com/IstiN/dmtools/main/CLAUDE.md"));
    }

    @Test
    void testIsGithubUrl_ConfluenceUrl() {
        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString());
        assertFalse(p.isGithubUrl("https://company.atlassian.net/wiki/spaces/DEV/pages/123"));
    }

    @Test
    void testGithubUrlFetchesContent() throws IOException {
        String expectedContent = "# CLAUDE.md content here";
        GitHub mockGithub = mock(GitHub.class);
        when(mockGithub.getFileContent("https://github.com/IstiN/dmtools/blob/main/CLAUDE.md"))
                .thenReturn(expectedContent);

        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString()) {
            @Override
            protected GitHub createGithubClient() {
                return mockGithub;
            }
        };

        String[] result = p.extractIfNeeded("https://github.com/IstiN/dmtools/blob/main/CLAUDE.md");

        assertEquals(1, result.length);
        assertEquals(expectedContent, result[0]);
    }

    @Test
    void testGithubUrlFetchFailureFallsBackToOriginalUrl() throws IOException {
        String originalUrl = "https://github.com/IstiN/dmtools/blob/main/CLAUDE.md";
        GitHub mockGithub = mock(GitHub.class);
        when(mockGithub.getFileContent(originalUrl)).thenThrow(new IOException("Network error"));

        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString()) {
            @Override
            protected GitHub createGithubClient() {
                return mockGithub;
            }
        };

        String[] result = p.extractIfNeeded(originalUrl);

        assertEquals(1, result.length);
        assertEquals(originalUrl, result[0]);
    }

    @Test
    void testGithubUrlNullContentFallsBackToOriginalUrl() throws IOException {
        String originalUrl = "https://github.com/IstiN/dmtools/blob/main/CLAUDE.md";
        GitHub mockGithub = mock(GitHub.class);
        when(mockGithub.getFileContent(originalUrl)).thenReturn(null);

        InstructionProcessor p = new InstructionProcessor(null, tempDir.toString()) {
            @Override
            protected GitHub createGithubClient() {
                return mockGithub;
            }
        };

        String[] result = p.extractIfNeeded(originalUrl);

        assertEquals(1, result.length);
        assertEquals(originalUrl, result[0]);
    }

    @Test
    void testGithubUrlNotRoutedToConfluence() throws IOException {
        GitHub mockGithub = mock(GitHub.class);
        when(mockGithub.getFileContent(anyString())).thenReturn("github file content");

        InstructionProcessor p = new InstructionProcessor(confluence, tempDir.toString()) {
            @Override
            protected GitHub createGithubClient() {
                return mockGithub;
            }
        };

        p.extractIfNeeded("https://github.com/IstiN/dmtools/blob/main/CLAUDE.md");

        verifyNoInteractions(confluence);
    }
}
