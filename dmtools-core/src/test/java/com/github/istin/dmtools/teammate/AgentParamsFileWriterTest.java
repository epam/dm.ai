// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgentParamsFileWriter}.
 */
class AgentParamsFileWriterTest {

    @TempDir
    Path inputFolder;

    private InstructionProcessor processor;
    private AgentParamsFileWriter writer;

    @BeforeEach
    void setUp() {
        processor = new InstructionProcessor((Confluence) null, inputFolder.toString());
        writer = new AgentParamsFileWriter(processor);
    }

    // -----------------------------------------------------------------------
    // writeInstructions
    // -----------------------------------------------------------------------

    @Test
    void testWriteInstructions_PlainText_WritesToCombinedFile() throws Exception {
        String[] instructions = {"First instruction", "Second instruction", "Third instruction"};
        writer.writeInstructions(inputFolder, instructions);

        Path textFile = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER)
                                    .resolve(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE);
        assertTrue(Files.exists(textFile));
        String content = Files.readString(textFile);
        assertTrue(content.contains("First instruction"));
        assertTrue(content.contains("Second instruction"));
        assertTrue(content.contains("Third instruction"));
    }

    @Test
    void testWriteInstructions_FilePath_WritesToSeparateFile() throws Exception {
        Path srcFile = inputFolder.resolve("source.md");
        Files.writeString(srcFile, "File content here");

        // Use a temp directory alongside inputFolder for the source file
        Path subInput = inputFolder.resolve("sub");
        Files.createDirectories(subInput);
        InstructionProcessor procWithDir = new InstructionProcessor((Confluence) null, inputFolder.toString());
        AgentParamsFileWriter writerWithDir = new AgentParamsFileWriter(procWithDir);

        String[] instructions = {"./" + srcFile.getFileName()};
        writerWithDir.writeInstructions(inputFolder, instructions);

        Path instrFolder = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER);
        assertTrue(Files.exists(instrFolder));
        // At least one file was written (filename derived from "source.md")
        long fileCount = Files.list(instrFolder).filter(p -> !p.getFileName().toString().equals(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE)).count();
        assertEquals(1, fileCount);
        Path written = Files.list(instrFolder).findFirst().get();
        assertEquals("File content here", Files.readString(written));
    }

    @Test
    void testWriteInstructions_Mixed_PlainAndUrl_SeparatesCorrectly() throws Exception {
        Path srcFile = inputFolder.resolve("ctx.md");
        Files.writeString(srcFile, "Context from file");

        String[] instructions = {
                "Plain instruction A",
                "./" + srcFile.getFileName(),
                "Plain instruction B"
        };
        writer.writeInstructions(inputFolder, instructions);

        Path instrFolder = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER);
        // One URL/file → one separate file; two plain text → one combined file
        Path textFile = instrFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE);
        assertTrue(Files.exists(textFile));
        String combined = Files.readString(textFile);
        assertTrue(combined.contains("Plain instruction A"));
        assertTrue(combined.contains("Plain instruction B"));
        assertFalse(combined.contains("Context from file")); // not in text file

        long separateFiles = Files.list(instrFolder)
                .filter(p -> !p.getFileName().toString().equals(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE))
                .count();
        assertEquals(1, separateFiles);
    }

    @Test
    void testWriteInstructions_NullOrEmpty_DoesNothing() throws Exception {
        writer.writeInstructions(inputFolder, null);
        writer.writeInstructions(inputFolder, new String[0]);

        Path instrFolder = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER);
        assertFalse(Files.exists(instrFolder));
    }

    @Test
    void testWriteInstructions_BlankEntries_Skipped() throws Exception {
        String[] instructions = {"", "  ", null, "Valid instruction"};
        writer.writeInstructions(inputFolder, instructions);

        Path instrFolder = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER);
        Path textFile = instrFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE);
        assertTrue(Files.exists(textFile));
        assertEquals("Valid instruction", Files.readString(textFile).trim());
    }

    // -----------------------------------------------------------------------
    // writeSingleFieldIfExtractable
    // -----------------------------------------------------------------------

    @Test
    void testWriteSingleField_PlainText_NotWritten() throws Exception {
        writer.writeSingleFieldIfExtractable(inputFolder, "known_info.md", "Just plain text");
        assertFalse(Files.exists(inputFolder.resolve("known_info.md")));
    }

    @Test
    void testWriteSingleField_FilePath_ContentWritten() throws Exception {
        Path srcFile = inputFolder.resolve("role.md");
        Files.writeString(srcFile, "AI role content");

        writer.writeSingleFieldIfExtractable(inputFolder, "ai_role.md", "./" + srcFile.getFileName());

        Path target = inputFolder.resolve("ai_role.md");
        assertTrue(Files.exists(target));
        assertEquals("AI role content", Files.readString(target));
    }

    @Test
    void testWriteSingleField_NullOrEmpty_DoesNothing() throws Exception {
        writer.writeSingleFieldIfExtractable(inputFolder, "known_info.md", null);
        writer.writeSingleFieldIfExtractable(inputFolder, "known_info.md", "");
        assertFalse(Files.exists(inputFolder.resolve("known_info.md")));
    }

    // -----------------------------------------------------------------------
    // writeToInputFolder — full integration
    // -----------------------------------------------------------------------

    @Test
    void testWriteToInputFolder_AllFields() throws Exception {
        // Create source files for file-path entries
        Path roleFile    = inputFolder.resolve("role.md");
        Path knowFile    = inputFolder.resolve("know.md");
        Path fmtFile     = inputFolder.resolve("fmt.md");
        Path shotsFile   = inputFolder.resolve("shots.md");
        Path instr1File  = inputFolder.resolve("instr1.md");
        Files.writeString(roleFile,   "Role content");
        Files.writeString(knowFile,   "Known info content");
        Files.writeString(fmtFile,    "Formatting rules content");
        Files.writeString(shotsFile,  "Few shots content");
        Files.writeString(instr1File, "Instruction from file");

        RequestDecompositionAgent.Result params = new Gson().fromJson("{}", RequestDecompositionAgent.Result.class);
        params.setAiRole("./" + roleFile.getFileName());
        params.setKnownInfo("./" + knowFile.getFileName());
        params.setFormattingRules("./" + fmtFile.getFileName());
        params.setFewShots("./" + shotsFile.getFileName());
        params.setInstructions(new String[]{
                "./" + instr1File.getFileName(),
                "Plain text instruction"
        });

        writer.writeToInputFolder(inputFolder, params);

        // Single-value fields should be written as files
        assertTrue(Files.exists(inputFolder.resolve("ai_role.md")));
        assertTrue(Files.exists(inputFolder.resolve("known_info.md")));
        assertTrue(Files.exists(inputFolder.resolve("formatting_rules.md")));
        assertTrue(Files.exists(inputFolder.resolve("few_shots.md")));

        // Instructions: one file-path entry + one text entry
        Path instrFolder = inputFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_SUBFOLDER);
        assertTrue(Files.exists(instrFolder));
        assertTrue(Files.exists(instrFolder.resolve(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE)));
        long fileInstructions = Files.list(instrFolder)
                .filter(p -> !p.getFileName().toString().equals(AgentParamsFileWriter.INSTRUCTIONS_TEXT_FILE))
                .count();
        assertEquals(1, fileInstructions);
    }

    @Test
    void testWriteToInputFolder_NullParams_DoesNothing() throws Exception {
        writer.writeToInputFolder(inputFolder, null);
        // no exception, no files
        assertEquals(0, Files.list(inputFolder).count());
    }

    // -----------------------------------------------------------------------
    // deriveFilename helper
    // -----------------------------------------------------------------------

    @Test
    void testDeriveFilename_HttpsUrl_UsesLastSegment() {
        String name = AgentParamsFileWriter.deriveFilename(
                "https://company.atlassian.net/wiki/spaces/SC/pages/123/My+Page+Title", 1);
        // '+' is not alphanumeric/.-_ so it gets replaced with '_'
        assertEquals("My_Page_Title.md", name);
    }

    @Test
    void testDeriveFilename_FilePath_UsesFilename() {
        String name = AgentParamsFileWriter.deriveFilename("./agents/instructions/bash_tools.md", 1);
        assertEquals("bash_tools.md", name);
    }

    @Test
    void testDeriveFilename_Fallback_UsesCounter() {
        String name = AgentParamsFileWriter.deriveFilename("https://host/", 7);
        // Last segment is empty → fallback
        assertEquals("instruction_007.md", name);
    }

    @Test
    void testDeriveFilename_SpecialChars_Sanitised() {
        String name = AgentParamsFileWriter.deriveFilename(
                "https://host/path/My File & More.md", 1);
        assertFalse(name.contains(" "), "Spaces should be replaced");
        assertFalse(name.contains("&"), "& should be replaced");
    }
}
