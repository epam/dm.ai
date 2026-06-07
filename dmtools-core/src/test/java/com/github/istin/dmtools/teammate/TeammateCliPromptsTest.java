// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.google.gson.Gson;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code cliPrompts} — the array-based CLI prompt field in {@link Teammate.TeammateParams}.
 * <p>
 * Each element of {@code cliPrompts} is processed via {@link InstructionProcessor}
 * (supporting URLs, file paths, and plain text). All processed parts are joined with a
 * double newline into a single prompt that is passed to CLI commands, exactly like {@code cliPrompt}.
 */
class TeammateCliPromptsTest {

    @TempDir
    Path tempDir;

    private InstructionProcessor processor;

    @BeforeEach
    void setUp() {
        // No Confluence needed for these tests
        processor = new InstructionProcessor((Confluence) null, tempDir.toString());
    }

    // -------------------------------------------------------------------------
    // TeammateParams Gson deserialization
    // -------------------------------------------------------------------------

    @Test
    void testTeammateParams_DeserializesCliPromptsArray() {
        String json = """
                {
                  "cliPrompts": ["prompt one", "prompt two", "prompt three"],
                  "cliPrompt": "base prompt"
                }
                """;
        Teammate.TeammateParams params = new Gson().fromJson(json, Teammate.TeammateParams.class);
        assertNotNull(params.getCliPrompts());
        assertEquals(3, params.getCliPrompts().length);
        assertEquals("prompt one",   params.getCliPrompts()[0]);
        assertEquals("prompt two",   params.getCliPrompts()[1]);
        assertEquals("prompt three", params.getCliPrompts()[2]);
        assertEquals("base prompt",  params.getCliPrompt());
    }

    @Test
    void testTeammateParams_DeserializesCliPrompts_WhenCliPromptAbsent() {
        String json = """
                {"cliPrompts": ["only from array"]}
                """;
        Teammate.TeammateParams params = new Gson().fromJson(json, Teammate.TeammateParams.class);
        assertNotNull(params.getCliPrompts());
        assertEquals(1, params.getCliPrompts().length);
        assertNull(params.getCliPrompt());
    }

    @Test
    void testTeammateParams_CliPromptsDefaultsToNull() {
        Teammate.TeammateParams params = new Gson().fromJson("{}", Teammate.TeammateParams.class);
        assertNull(params.getCliPrompts());
    }

    // -------------------------------------------------------------------------
    // InstructionProcessor processes all elements
    // -------------------------------------------------------------------------

    @Test
    void testInstructionProcessor_ExtractsAllArrayElements_PlainText() throws Exception {
        String[] input = {"first instruction", "second instruction", "third instruction"};
        String[] result = processor.extractIfNeeded(input);

        assertEquals(3, result.length);
        assertEquals("first instruction",  result[0]);
        assertEquals("second instruction", result[1]);
        assertEquals("third instruction",  result[2]);
    }

    @Test
    void testInstructionProcessor_ExtractsFileContent_FromArray() throws Exception {
        Path file1 = tempDir.resolve("part1.md");
        Path file2 = tempDir.resolve("part2.md");
        Files.writeString(file1, "Content from part 1");
        Files.writeString(file2, "Content from part 2");

        // Use relative paths (starts with "./")
        String[] input = {
                "./" + file1.getFileName(),
                "./" + file2.getFileName()
        };
        String[] result = processor.extractIfNeeded(input);

        assertEquals(2, result.length);
        assertEquals("Content from part 1", result[0]);
        assertEquals("Content from part 2", result[1]);
    }

    @Test
    void testInstructionProcessor_MixedSourcesInArray() throws Exception {
        Path file = tempDir.resolve("context.md");
        Files.writeString(file, "File-based context");

        String[] input = {
                "Plain text preamble",
                "./" + file.getFileName()
        };
        String[] result = processor.extractIfNeeded(input);

        assertEquals(2, result.length);
        assertEquals("Plain text preamble",  result[0]);
        assertEquals("File-based context",   result[1]);
    }

    // -------------------------------------------------------------------------
    // Joining logic — via InstructionProcessor.buildCombinedPrompt
    // -------------------------------------------------------------------------

    @Test
    void testJoiningLogic_AllPartsJoined() throws Exception {
        String result = processor.buildCombinedPrompt(null, new String[]{"Part A", "Part B", "Part C"});
        assertEquals("Part A\n\nPart B\n\nPart C", result);
    }

    @Test
    void testJoiningLogic_CombinedWithCliPrompt() throws Exception {
        String result = processor.buildCombinedPrompt("Base prompt",
                new String[]{"Extra context A", "Extra context B"});
        assertEquals("Base prompt\n\nExtra context A\n\nExtra context B", result);
    }

    @Test
    void testJoiningLogic_SkipsBlankEntries() throws Exception {
        String result = processor.buildCombinedPrompt(null, new String[]{"First", "", "  ", "Last"});
        assertEquals("First\n\nLast", result);
    }

    @Test
    void testJoiningLogic_EmptyArray_ProducesNoOutput() throws Exception {
        assertNull(processor.buildCombinedPrompt(null, new String[0]));
    }

    // -------------------------------------------------------------------------
    // cliPromptsByTracker deserialization
    // -------------------------------------------------------------------------

    @Test
    void testTeammateParams_DeserializesCliPromptsByTracker() {
        String json = """
                {
                  "cliPrompts": ["base prompt"],
                  "cliPromptsByTracker": {
                    "jira": ["jira specific"],
                    "ado": ["ado specific"]
                  }
                }
                """;
        Teammate.TeammateParams params = new Gson().fromJson(json, Teammate.TeammateParams.class);
        assertNotNull(params.getCliPromptsByTracker());
        assertEquals(2, params.getCliPromptsByTracker().size());
        assertArrayEquals(new String[]{"jira specific"}, params.getCliPromptsByTracker().get("jira"));
        assertArrayEquals(new String[]{"ado specific"}, params.getCliPromptsByTracker().get("ado"));
    }

    @Test
    void testTeammateParams_CliPromptsByTrackerDefaultsToNull() {
        Teammate.TeammateParams params = new Gson().fromJson("{}", Teammate.TeammateParams.class);
        assertNull(params.getCliPromptsByTracker());
    }

    // -------------------------------------------------------------------------
    // resolveCliPrompts merging logic
    // -------------------------------------------------------------------------

    @Test
    void testResolveCliPrompts_MergesTrackerSpecificPrompts() {
        String[] base = {"base1", "base2"};
        Map<String, String[]> byTracker = Map.of("jira", new String[]{"jira1"});
        String[] result = Teammate.resolveCliPrompts(base, byTracker, "jira");
        assertArrayEquals(new String[]{"base1", "base2", "jira1"}, result);
    }

    @Test
    void testResolveCliPrompts_ReturnsBaseWhenNoMatch() {
        String[] base = {"base1"};
        Map<String, String[]> byTracker = Map.of("ado", new String[]{"ado1"});
        String[] result = Teammate.resolveCliPrompts(base, byTracker, "jira");
        assertSame(base, result);
    }

    @Test
    void testResolveCliPrompts_ReturnsBaseWhenTrackerTypeIsNull() {
        String[] base = {"base1"};
        Map<String, String[]> byTracker = Map.of("jira", new String[]{"jira1"});
        String[] result = Teammate.resolveCliPrompts(base, byTracker, null);
        assertSame(base, result);
    }

    @Test
    void testResolveCliPrompts_ReturnsBaseWhenMapIsNull() {
        String[] base = {"base1"};
        String[] result = Teammate.resolveCliPrompts(base, null, "jira");
        assertSame(base, result);
    }

    @Test
    void testResolveCliPrompts_ReturnsTrackerOnlyWhenBaseIsNull() {
        Map<String, String[]> byTracker = Map.of("jira", new String[]{"jira1", "jira2"});
        String[] result = Teammate.resolveCliPrompts(null, byTracker, "jira");
        assertArrayEquals(new String[]{"jira1", "jira2"}, result);
    }

    @Test
    void testResolveCliPrompts_ReturnsBaseWhenTrackerPromptsEmpty() {
        String[] base = {"base1"};
        Map<String, String[]> byTracker = Map.of("jira", new String[0]);
        String[] result = Teammate.resolveCliPrompts(base, byTracker, "jira");
        assertSame(base, result);
    }
}
