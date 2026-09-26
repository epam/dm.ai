// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link AgentPackResolver} (dm.ai #579): pack detection, zip-slip
 * rejection, encrypted-zip rejection, manifest verification, entry override, and
 * the agents/→pack-root path duality.
 */
public class AgentPackResolverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File agentRoot;
    private Path packsRoot;
    private AgentPackResolver resolver;

    @Before
    public void setUp() throws IOException {
        agentRoot = tmp.newFolder("agents");
        packsRoot = tmp.newFolder("packs").toPath();
        resolver = new AgentPackResolver(packsRoot);
    }

    // ------------------------------------------------------------------
    // Fixture: build a real pack with the production compiler
    // ------------------------------------------------------------------

    private File write(String relativePath, String content) throws IOException {
        Path p = agentRoot.toPath().resolve(relativePath);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    /** Builds a real pack zip via {@link AgentPackCompiler} and returns it. */
    private File buildPack(String agentName, String version) throws IOException {
        write("js/common/util.js", "// util\n");
        write("js/main.js", "var u = require('./common/util.js');\n");
        write("instructions/common/guide.md", "# Guide\n");
        File entry = write(agentName + ".json", "{\n" +
                "  \"name\": \"TestAgent\",\n" +
                "  \"params\": {\n" +
                "    \"jsPath\": \"agents/js/main.js\",\n" +
                "    \"cliPrompts\": [\"agents/instructions/common/guide.md\"]\n" +
                "  }\n" +
                "}");
        File outDir = tmp.newFolder("dist");
        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entry, version, "abc123", outDir);
        return result.zipFile;
    }

    // ------------------------------------------------------------------
    // isPack detection
    // ------------------------------------------------------------------

    @Test
    public void testIsPackForLocalZip() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        assertTrue(resolver.isPack(zip.getAbsolutePath()));
    }

    @Test
    public void testIsPackForHttpUrl() {
        assertTrue(resolver.isPack("https://example.com/packs/story_development-1.2.0.zip"));
        assertTrue(resolver.isPack("https://example.com/packs/x.zip#custom.json"));
    }

    @Test
    public void testIsPackRejectsNonZipAndMissingFile() {
        assertFalse(resolver.isPack("story_development.json"));
        assertFalse(resolver.isPack("/nonexistent/pack.zip"));
        assertFalse(resolver.isPack(null));
        assertFalse(resolver.isPack("https://example.com/file.json"));
    }

    // ------------------------------------------------------------------
    // Local-file resolution + path duality (AC1, AC5)
    // ------------------------------------------------------------------

    @Test
    public void testResolveLocalPackUnpacksToCache() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        AgentPackResolver.ResolvedPack pack = resolver.resolve(zip.getAbsolutePath());

        assertEquals("my_agent", pack.agent);
        assertEquals("1.0.0", pack.version);
        assertTrue(pack.entryFile.isFile());
        assertEquals("my_agent.json", pack.entryFile.getName());

        // Unpacked into the cache with the agents/ prefix stripped.
        Path root = pack.packRoot;
        assertTrue(root.resolve("js/main.js").toFile().isFile());
        assertTrue(root.resolve("js/common/util.js").toFile().isFile());
        assertTrue(root.resolve("instructions/common/guide.md").toFile().isFile());
        assertTrue(root.resolve("manifest.json").toFile().isFile());
        assertFalse(root.resolve("agents/js/main.js").toFile().exists());
    }

    @Test
    public void testSecondResolveIsCacheHit() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        AgentPackResolver.ResolvedPack first = resolver.resolve(zip.getAbsolutePath());
        // Modify the zip on disk — a cache hit must NOT re-unpack.
        File marker = first.packRoot.resolve("MARKER.txt").toFile();
        Files.write(marker.toPath(), "cached".getBytes(StandardCharsets.UTF_8));
        AgentPackResolver.ResolvedPack second = resolver.resolve(zip.getAbsolutePath());
        assertTrue("cache hit keeps the unpacked dir", marker.isFile());
        assertEquals(first.packRoot, second.packRoot);
    }

    @Test
    public void testEntryOverrideViaHash() throws IOException {
        write("js/main.js", "// main\n");
        // custom.json is the parent of my_agent.json, so it lands in the pack closure.
        write("custom.json", "{\"name\":\"X\",\"params\":{\"jsPath\":\"agents/js/main.js\"}}");
        File entry = write("my_agent.json", "{\"name\":\"X\",\"parent\":{\"path\":\"agents/custom.json\"},\"params\":{}}");
        File outDir = tmp.newFolder("dist2");
        File zip = new AgentPackCompiler(agentRoot).compile(entry, "1.0.0", "abc", outDir).zipFile;

        AgentPackResolver.ResolvedPack pack = resolver.resolve(zip.getAbsolutePath() + "#custom.json");
        assertEquals("custom.json", pack.entryFile.getName());
    }

    @Test
    public void testMissingEntryOverrideFailsListingDefault() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        try {
            resolver.resolve(zip.getAbsolutePath() + "#missing.json");
            fail("expected IOException for a missing entry override");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("missing.json"));
            assertTrue(e.getMessage().contains("defaultEntry"));
        }
    }

    // ------------------------------------------------------------------
    // Zip-slip protection (AC4)
    // ------------------------------------------------------------------

    /** Builds a zip with a hostile entry (../ escape). */
    private File buildMaliciousZip(String evilEntryName) throws IOException {
        File zip = new File(tmp.newFolder("evil"), "evil.zip");
        JSONObject manifest = new JSONObject()
                .put("agent", "evil").put("version", "1.0.0").put("defaultEntry", "evil.json")
                .put("files", new org.json.JSONArray());
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
        entries.put(evilEntryName, "bad".getBytes(StandardCharsets.UTF_8));
        try (OutputStream fos = Files.newOutputStream(zip.toPath());
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(fos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipArchiveEntry ze = new ZipArchiveEntry(e.getKey());
                zos.putArchiveEntry(ze);
                zos.write(e.getValue());
                zos.closeArchiveEntry();
            }
        }
        return zip;
    }

    @Test
    public void testZipSlipDotDotRejected() throws IOException {
        File zip = buildMaliciousZip("../evil.sh");
        try {
            resolver.resolve(zip.getAbsolutePath());
            fail("expected zip-slip rejection");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("evil.sh") || e.getMessage().contains("escapes") || e.getMessage().contains("Zip-slip"));
        }
        // Nothing cached.
        assertFalse(packsRoot.resolve("evil-1.0.0").toFile().exists());
    }

    @Test
    public void testZipSlipAbsolutePathRejected() throws IOException {
        File zip = buildMaliciousZip("/abs/evil.sh");
        try {
            resolver.resolve(zip.getAbsolutePath());
            fail("expected zip-slip rejection for absolute path");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        }
        assertFalse(packsRoot.resolve("evil-1.0.0").toFile().exists());
    }

    // ------------------------------------------------------------------
    // Manifest / integrity
    // ------------------------------------------------------------------

    @Test
    public void testMissingManifestFails() throws IOException {
        File zip = new File(tmp.newFolder("nomani"), "nomani.zip");
        try (OutputStream fos = Files.newOutputStream(zip.toPath());
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(fos)) {
            ZipArchiveEntry ze = new ZipArchiveEntry("x.txt");
            zos.putArchiveEntry(ze);
            zos.write("hi".getBytes(StandardCharsets.UTF_8));
            zos.closeArchiveEntry();
        }
        try {
            resolver.resolve(zip.getAbsolutePath());
            fail("expected IOException for missing manifest.json");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("manifest.json"));
        }
    }

    // ------------------------------------------------------------------
    // Path rewriting (path duality, AC5)
    // ------------------------------------------------------------------

    @Test
    public void testRewritePathsToPackRoot() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        AgentPackResolver.ResolvedPack pack = resolver.resolve(zip.getAbsolutePath());

        JSONObject config = new JSONObject()
                .put("params", new JSONObject()
                        .put("jsPath", "agents/js/main.js")
                        .put("cliPrompts", new org.json.JSONArray()
                                .put("agents/instructions/common/guide.md")
                                .put("Senior Developer Engineer"))); // literal role — not a path

        resolver.rewritePathsToPackRoot(config, pack.packRoot);

        JSONObject params = config.getJSONObject("params");
        assertEquals(pack.packRoot.resolve("js/main.js").toAbsolutePath().toString(), params.getString("jsPath"));
        assertEquals(pack.packRoot.resolve("instructions/common/guide.md").toAbsolutePath().toString(),
                params.getJSONArray("cliPrompts").getString(0));
        // Literal role string is left untouched.
        assertEquals("Senior Developer Engineer", params.getJSONArray("cliPrompts").getString(1));
    }

    @Test
    public void testRewriteLeavesUrlsAndClasspathAlone() throws IOException {
        File zip = buildPack("my_agent", "1.0.0");
        AgentPackResolver.ResolvedPack pack = resolver.resolve(zip.getAbsolutePath());
        JSONObject config = new JSONObject()
                .put("params", new JSONObject()
                        .put("postJSAction", "https://github.com/u/r/blob/main/x.js")
                        .put("timerJSAction", "classpath:js/timer.js"));
        resolver.rewritePathsToPackRoot(config, pack.packRoot);
        JSONObject params = config.getJSONObject("params");
        assertEquals("https://github.com/u/r/blob/main/x.js", params.getString("postJSAction"));
        assertEquals("classpath:js/timer.js", params.getString("timerJSAction"));
    }
}
