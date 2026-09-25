// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link AgentPackCompiler} (dm.ai #595): closure walking, path
 * normalization, transitive JS scanning, deterministic zip output, manifest schema.
 */
public class AgentPackCompilerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File agentRoot;
    private File outDir;

    @Before
    public void setUp() throws IOException {
        agentRoot = tmp.newFolder("agents");
        outDir = tmp.newFolder("dist");
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

    /** Reads all zip entries into a path->bytes map. */
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
    public void testClosureIncludesEntryAndTransitiveJs() throws IOException {
        File entry = writeSimpleAgent();
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc123", outDir);

        assertTrue(result.zipFile.isFile());
        assertTrue(result.manifestFile.isFile());
        assertTrue(result.shaFile.isFile());

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue(zip.containsKey("my_agent.json"));
        assertTrue(zip.containsKey("js/main.js"));
        assertTrue(zip.containsKey("js/common/util.js")); // transitive require
        assertTrue(zip.containsKey("manifest.json"));
        assertEquals("agents/ prefix must be stripped", false, zip.containsKey("agents/js/main.js"));
    }

    @Test
    public void testManifestSchema() throws IOException {
        File entry = writeSimpleAgent();
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.2.0", "deadbeef", outDir);

        JSONObject manifest = new JSONObject(Files.readString(result.manifestFile.toPath(), StandardCharsets.UTF_8));
        assertEquals("my_agent", manifest.getString("agent"));
        assertEquals("1.2.0", manifest.getString("version"));
        assertEquals("deadbeef", manifest.getString("sourceCommit"));
        assertEquals("my_agent.json", manifest.getString("defaultEntry"));
        assertTrue(manifest.has("minDmtoolsVersion"));

        JSONArray files = manifest.getJSONArray("files");
        assertNotNull(files);
        boolean foundMain = false;
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            assertTrue(f.has("path"));
            assertTrue(f.has("sha256"));
            assertTrue(f.has("mode"));
            if ("js/main.js".equals(f.getString("path"))) {
                foundMain = true;
                assertEquals(64, f.getString("sha256").length());
            }
        }
        assertTrue("manifest must list js/main.js", foundMain);
    }

    @Test
    public void testDeterministicByteIdenticalBuilds() throws IOException {
        File entry = writeSimpleAgent();
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        File out1 = tmp.newFolder("dist1");
        File out2 = tmp.newFolder("dist2");
        AgentPackCompiler.PackResult r1 = compiler.compile(entry, "1.0.0", "abc", out1);
        AgentPackCompiler.PackResult r2 = compiler.compile(entry, "1.0.0", "abc", out2);

        byte[] zip1 = Files.readAllBytes(r1.zipFile.toPath());
        byte[] zip2 = Files.readAllBytes(r2.zipFile.toPath());
        org.junit.Assert.assertArrayEquals("repeated builds must be byte-identical", zip1, zip2);
    }

    @Test
    public void testMissingReferencedFileFailsWithExactPath() throws IOException {
        File entry = write("broken.json", "{\n" +
                "  \"name\": \"X\",\n" +
                "  \"params\": { \"jsPath\": \"agents/js/does_not_exist.js\" }\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        try {
            compiler.compile(entry, "1.0.0", "abc", outDir);
            fail("expected IOException for missing referenced file");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does_not_exist.js"));
        }
        // Nothing written on failure.
        assertEquals(0, outDir.listFiles().length);
    }

    @Test
    public void testScriptsSubtreeIncludedWhole() throws IOException {
        write("scripts/run-agent.sh", "#!/bin/bash\necho hi\n");
        write("scripts/providers/_common.sh", "# common\n");
        write("scripts/providers/claude.sh", "# claude\n");
        File entry = write("with_scripts.json", "{\n" +
                "  \"name\": \"X\",\n" +
                "  \"params\": { \"cliCommands\": [\"./agents/scripts/run-agent.sh\"] }\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);

        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);
        Map<String, byte[]> zip = readZip(result.zipFile);

        assertTrue(zip.containsKey("scripts/run-agent.sh"));
        assertTrue(zip.containsKey("scripts/providers/_common.sh")); // whole subtree
        assertTrue(zip.containsKey("scripts/providers/claude.sh"));
    }

    @Test
    public void testShellScriptsCarry0755Mode() throws IOException {
        write("scripts/run-agent.sh", "#!/bin/bash\necho hi\n");
        File entry = write("mode_agent.json", "{\n" +
                "  \"name\": \"X\",\n" +
                "  \"params\": { \"cliCommands\": [\"./agents/scripts/run-agent.sh\"] }\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);

        JSONObject manifest = new JSONObject(Files.readString(result.manifestFile.toPath(), StandardCharsets.UTF_8));
        JSONArray files = manifest.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if ("scripts/run-agent.sh".equals(f.getString("path"))) {
                assertEquals("0755", f.getString("mode"));
                return;
            }
        }
        fail("manifest must list scripts/run-agent.sh");
    }

    @Test
    public void testParentPathChainIncluded() throws IOException {
        write("js/base.js", "// base\n");
        write("agents/../base_config.json".replace("agents/../", ""), "{\n" +
                "  \"name\": \"Base\",\n" +
                "  \"params\": { \"jsPath\": \"agents/js/base.js\" }\n" +
                "}");
        File entry = write("child_config.json", "{\n" +
                "  \"name\": \"Child\",\n" +
                "  \"parent\": { \"path\": \"agents/base_config.json\", \"override\": [\"params.agentParams\"] },\n" +
                "  \"params\": {}\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue("parent config must be in the pack", zip.containsKey("base_config.json"));
        assertTrue(zip.containsKey("js/base.js"));
    }

    @Test
    public void testAgentsPrefixAndPlainPrefixBothNormalize() throws IOException {
        write("js/a.js", "// a\n");
        write("js/b.js", "// b\n");
        // One config uses agents/js/..., the other's jsPath uses js/...
        File entry = write("dual.json", "{\n" +
                "  \"name\": \"Dual\",\n" +
                "  \"params\": {\n" +
                "    \"preJSAction\": \"agents/js/a.js\",\n" +
                "    \"postJSAction\": \"js/b.js\"\n" +
                "  }\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue(zip.containsKey("js/a.js"));
        assertTrue(zip.containsKey("js/b.js"));
        assertFalse(zip.containsKey("agents/js/a.js"));
    }

    @Test
    public void testAgentsMdAndLicenseIncludedWhenPresent() throws IOException {
        File entry = writeSimpleAgent();
        write("AGENTS.md", "# Agents\n");
        write("LICENSE", "Apache-2.0\n");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);

        Map<String, byte[]> zip = readZip(result.zipFile);
        assertTrue(zip.containsKey("AGENTS.md"));
        assertTrue(zip.containsKey("LICENSE"));
    }

    @Test
    public void testUrlAndClasspathReferencesIgnored() throws IOException {
        write("js/main.js", "// main\n");
        File entry = write("mixed.json", "{\n" +
                "  \"name\": \"X\",\n" +
                "  \"params\": {\n" +
                "    \"preJSAction\": \"js/main.js\",\n" +
                "    \"postJSAction\": \"https://github.com/user/repo/blob/main/x.js\",\n" +
                "    \"timerJSAction\": \"classpath:js/timer.js\"\n" +
                "  }\n" +
                "}");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        // Must not fail on the URL/classpath refs.
        AgentPackCompiler.PackResult result = compiler.compile(entry, "1.0.0", "abc", outDir);
        assertNotNull(result.zipFile);
    }
}
