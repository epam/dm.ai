// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.pack.AgentPackException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code extraDirs} ({@code --include}) whole-subtree embedding (zip flow):
 * files that only {@code pack:}-consuming child configs reference are embedded
 * even when the entry's computed closure does not reach them. Mirrors the Dart
 * {@code AgentPackCompiler extraDirs (--include)} group.
 */
class AgentPackCompilerExtraDirsTest {

    @TempDir
    Path tmp;

    private File agentRoot;
    private File outDir;

    @BeforeEach
    void setUp() throws IOException {
        agentRoot = Files.createDirectory(tmp.resolve("agents")).toFile();
        outDir = Files.createDirectory(tmp.resolve("dist")).toFile();
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private File write(String relativePath, String content) throws IOException {
        Path p = agentRoot.toPath().resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    /** A minimal agent referencing one JS file with a relative require. */
    private File writeSimpleAgent() throws IOException {
        write("js/common/util.js", "// util\n");
        write("js/main.js", "var u = require('./common/util.js');\n");
        return write("my_agent.json", "{\n" +
                "  \"name\": \"Teammate\",\n" +
                "  \"params\": {\n" +
                "    \"jsPath\": \"agents/js/main.js\"\n" +
                "  }\n" +
                "}");
    }

    /** Reads all zip entry names into a set (names are unique in these packs). */
    private Map<String, byte[]> readZip(File zipFile) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream fis = Files.newInputStream(zipFile.toPath());
             ZipArchiveInputStream zis = new ZipArchiveInputStream(fis)) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void embedsEveryFileUnderListedDirReferencedOrNot() throws IOException {
        File entry = writeSimpleAgent();
        write("instructions/pr_review/rules.md", "# rules\n");
        write("instructions/common/format.md", "# format\n");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        AgentPackCompiler.PackResult result =
                compiler.compile(entry, "1.0.0", "abc", outDir, List.of("instructions"));

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue(zip.containsKey("instructions/pr_review/rules.md"));
        assertTrue(zip.containsKey("instructions/common/format.md"));
        assertFalse(zip.containsKey("agents/instructions/pr_review/rules.md"),
                "the agents/ prefix is stripped per the path duality");
    }

    @Test
    void stripsLeadingAgentsPrefixFromIncludeDir() throws IOException {
        File entry = writeSimpleAgent();
        write("prompts/bash_tools.md", "# tools\n");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        AgentPackCompiler.PackResult result =
                compiler.compile(entry, "1.0.0", "abc", outDir, List.of("agents/prompts"));

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue(zip.containsKey("prompts/bash_tools.md"));
    }

    @Test
    void missingIncludeDirThrowsAgentPackException() throws IOException {
        File entry = writeSimpleAgent();
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        assertThrows(AgentPackException.class,
                () -> compiler.compile(entry, "1.0.0", "abc", outDir, List.of("nope")));
    }

    @Test
    void includeDirEscapingAgentsRootIsRejected() throws IOException {
        File entry = writeSimpleAgent();
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        assertThrows(AgentPackException.class,
                () -> compiler.compile(entry, "1.0.0", "abc", outDir, List.of("../outside")));
    }
}
