// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pack;

import com.sun.net.httpserver.HttpServer;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgentPackResolver} — Java backport of the Dart
 * {@code agent_pack_resolver.dart} (dm.ai #579): ref parsing, registry-ref
 * gating, catalog errors, sha256 verification, zip-slip rejection, cache
 * reuse vs re-fetch, entry override, and an end-to-end run against a local
 * HTTP fixture server.
 */
class AgentPackResolverTest {

    private static final String AGENT = "demo";
    private static final String VERSION = "1.0.0";

    @TempDir
    Path tempDir;

    private Path packsRoot;
    private HttpServer server;
    private String registryUrl;

    /** path -> response bytes served by the fixture server. */
    private final Map<String, byte[]> servedFiles = new ConcurrentHashMap<>();
    /** path -> number of GETs (cache reuse assertions). */
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    /** path -> Authorization headers received (auth-header assertions). */
    private final Map<String, List<String>> authHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        packsRoot = tempDir.resolve("packs");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestCounts.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            authHeaders.computeIfAbsent(path, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = servedFiles.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        registryUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private AgentPackResolver resolver() {
        return new AgentPackResolver(packsRoot, registryUrl);
    }

    /** Builds a valid pack zip: manifest.json + listed files, deterministic order. */
    private byte[] buildPack(Map<String, String> files, String agent, String version, String defaultEntry)
            throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            entries.put(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        JSONObject manifest = new JSONObject();
        manifest.put("agent", agent);
        manifest.put("version", version);
        manifest.put("sourceCommit", "testcommit");
        manifest.put("defaultEntry", defaultEntry);
        List<JSONObject> fileEntries = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            JSONObject f = new JSONObject();
            f.put("path", e.getKey());
            f.put("sha256", AgentPackResolver.sha256Hex(e.getValue()));
            f.put("mode", e.getKey().endsWith(".sh") ? "0755" : "0644");
            fileEntries.add(f);
        }
        manifest.put("files", fileEntries);
        entries.put("manifest.json", manifest.toString(2).getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(e.getKey());
                entry.setUnixMode(e.getKey().endsWith(".sh") ? 0755 : 0644);
                zos.putArchiveEntry(entry);
                zos.write(e.getValue());
                zos.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    private byte[] buildDefaultPack() throws IOException {
        return buildPack(Map.of(
                "demo.json", "{\"name\":\"DemoJob\",\"params\":{}}",
                "js/main.js", "print('hi');\n"), AGENT, VERSION, "demo.json");
    }

    /** Publishes a pack + sha256 sidecar + a flat catalog pointing at it. */
    private void publishPack(String agent, String version, byte[] zip) {
        String base = agent + "-" + version;
        servedFiles.put("/" + base + ".zip", zip);
        servedFiles.put("/" + base + ".zip.sha256",
                (AgentPackResolver.sha256Hex(zip) + "  " + base + ".zip\n").getBytes(StandardCharsets.UTF_8));
        servedFiles.put("/catalog.json",
                ("{\"" + agent + "\": \"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private int requestCount(String path) {
        AtomicInteger c = requestCounts.get(path);
        return c == null ? 0 : c.get();
    }

    // ------------------------------------------------------------------
    // Ref parsing + isPack gating
    // ------------------------------------------------------------------

    @Test
    void isRegistryRef_matchesVersionedAndLatest() {
        AgentPackResolver r = resolver();
        assertTrue(r.isRegistryRef("demo@latest"));
        assertTrue(r.isRegistryRef("demo@1.0.0"));
        assertTrue(r.isRegistryRef("my-agent@2.0.0-rc.1"));
        assertTrue(r.isRegistryRef("demo_2@v1"));
        assertFalse(r.isRegistryRef("demo"));
        assertFalse(r.isRegistryRef("demo@"));
        assertFalse(r.isRegistryRef("@latest"));
        assertFalse(r.isRegistryRef("demo@1.0/../x"));
        assertFalse(r.isRegistryRef("agents/demo.json"));
        assertFalse(r.isRegistryRef("demo@latest#demo.json")); // gate sees entry-stripped base
    }

    @Test
    void isRegistryRef_requiresConfiguredRegistry() {
        AgentPackResolver noRegistry = new AgentPackResolver(packsRoot, "");
        assertFalse(noRegistry.isRegistryRef("demo@latest"));
        assertFalse(noRegistry.isPack("demo@latest"));
    }

    @Test
    void stripEntry_and_entryOverrideOf() {
        assertEquals("demo@latest", AgentPackResolver.stripEntry("demo@latest#alt.json"));
        assertEquals("demo@latest", AgentPackResolver.stripEntry("demo@latest"));
        assertNull(AgentPackResolver.entryOverrideOf("demo@latest"));
        assertEquals("alt.json", AgentPackResolver.entryOverrideOf("demo@latest#alt.json"));
    }

    @Test
    void isPack_gatesAllForms() throws IOException {
        AgentPackResolver r = resolver();
        assertFalse(r.isPack(null));
        assertTrue(r.isPack("demo@latest"));
        assertTrue(r.isPack("demo@1.0.0#demo.json"));
        assertTrue(r.isPack("https://example.com/dist/demo-1.0.0.zip"));
        assertFalse(r.isPack("https://example.com/dist/catalog.json"));
        assertFalse(r.isPack("plain-job-name"));
        assertFalse(r.isPack("config.json"));

        Path zip = tempDir.resolve("local.zip");
        Files.write(zip, buildDefaultPack());
        assertTrue(r.isPack(zip.toString()));
        assertFalse(r.isPack(tempDir.resolve("missing.zip").toString()));
    }

    // ------------------------------------------------------------------
    // Catalog
    // ------------------------------------------------------------------

    @Test
    void catalogHttp404_isDistinctError() {
        publishPack(AGENT, VERSION, new byte[0]);
        servedFiles.remove("/catalog.json");
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve("demo@latest", null));
        assertTrue(e.getMessage().contains("Failed to read registry catalog: HTTP 404 for "
                + registryUrl + "/catalog.json"), e.getMessage());
    }

    @Test
    void unknownAgent_errorNamesAgentAndAvailableNames() {
        publishPack(AGENT, VERSION, new byte[0]);
        servedFiles.put("/catalog.json", "{\"other\": \"2.0.0\", \"misc\": \"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve("demo@latest", null));
        assertTrue(e.getMessage().contains("Agent 'demo' not found in registry catalog"), e.getMessage());
        assertTrue(e.getMessage().contains("Available agents: misc, other"), e.getMessage());
    }

    @Test
    void nestedCatalogShape_isAccepted() throws IOException {
        byte[] zip = buildDefaultPack();
        String base = AGENT + "-" + VERSION;
        servedFiles.put("/" + base + ".zip", zip);
        servedFiles.put("/" + base + ".zip.sha256",
                (AgentPackResolver.sha256Hex(zip) + "  " + base + ".zip\n").getBytes(StandardCharsets.UTF_8));
        servedFiles.put("/catalog.json",
                ("{\"agents\": {\"" + AGENT + "\": \"" + VERSION + "\"}}").getBytes(StandardCharsets.UTF_8));
        ResolvedPack pack = resolver().resolve("demo@latest", null);        assertEquals(AGENT, pack.getAgent());
        assertEquals(VERSION, pack.getVersion());
    }

    @Test
    void malformedCatalog_isDistinctError() {
        publishPack(AGENT, VERSION, new byte[0]);
        servedFiles.put("/catalog.json", "{not json".getBytes(StandardCharsets.UTF_8));
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve("demo@latest", null));
        assertTrue(e.getMessage().startsWith("Malformed registry catalog " + registryUrl + "/catalog.json"),
                e.getMessage());
    }

    @Test
    void unsafeCatalogVersion_isRejected() {
        publishPack(AGENT, VERSION, new byte[0]);
        servedFiles.put("/catalog.json", ("{\"" + AGENT + "\": \"../evil\"}").getBytes(StandardCharsets.UTF_8));
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve("demo@latest", null));
        assertTrue(e.getMessage().contains("Unsafe catalog version for demo: \"../evil\""), e.getMessage());
    }

    @Test
    void explicitVersion_skipsCatalog() throws IOException {
        // No catalog.json served at all — explicit @version must not need it.
        byte[] zip = buildDefaultPack();
        publishPack(AGENT, VERSION, zip);
        servedFiles.remove("/catalog.json");
        ResolvedPack pack = resolver().resolve("demo@" + VERSION, null);
        assertEquals(VERSION, pack.getVersion());
        assertEquals(0, requestCount("/catalog.json"));
    }

    // ------------------------------------------------------------------
    // sha256 verification
    // ------------------------------------------------------------------

    @Test
    void sha256Mismatch_isDistinctError() throws IOException {
        byte[] zip = buildDefaultPack();
        publishPack(AGENT, VERSION, zip);
        servedFiles.put("/" + AGENT + "-" + VERSION + ".zip.sha256",
                ("0".repeat(64) + "  bad\n").getBytes(StandardCharsets.UTF_8));
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve("demo@latest", null));
        assertTrue(e.getMessage().startsWith("SHA-256 mismatch for " + registryUrl + "/"), e.getMessage());
        assertTrue(e.getMessage().contains("(expected " + "0".repeat(64) + ", got "
                + AgentPackResolver.sha256Hex(zip) + ")"), e.getMessage());
    }

    @Test
    void missingSha256Sidecar_warnsButResolves() throws IOException {
        byte[] zip = buildDefaultPack();
        publishPack(AGENT, VERSION, zip);
        servedFiles.remove("/" + AGENT + "-" + VERSION + ".zip.sha256");
        ResolvedPack pack = resolver().resolve("demo@latest", null);
        assertEquals(AGENT, pack.getAgent());
        assertTrue(Files.exists(pack.getEntryFile()));
    }

    @Test
    void githubToken_isNotSentToNonGithubHosts() throws IOException {
        byte[] zip = buildDefaultPack();
        publishPack(AGENT, VERSION, zip);
        resolver().resolve("demo@latest", "secret-token");
        for (List<String> headers : authHeaders.values()) {
            for (String h : headers) {
                assertNull(h, "Authorization must not leave github.com hosts");
            }
        }
    }

    // ------------------------------------------------------------------
    // Unpack verification (zip-slip, inventory, manifest identity)
    // ------------------------------------------------------------------

    private Path writeLocalPack(byte[] zip) throws IOException {
        Path zipPath = tempDir.resolve("demo-local.zip");
        Files.write(zipPath, zip);
        return zipPath;
    }

    @Test
    void zipSlipEntry_isRejected() throws IOException {
        byte[] zip = buildDefaultPack();
        // Sneak a traversal entry into the zip alongside the valid manifest.
        byte[] evil = addEntry(zip, "../evil.txt", "owned".getBytes(StandardCharsets.UTF_8), 0644);
        Path zipPath = writeLocalPack(evil);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Zip-slip entry rejected: ../evil.txt", e.getMessage());
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    void symlinkEntry_isRejected() throws IOException {
        byte[] zip = buildDefaultPack();
        byte[] withLink = addEntry(zip, "link.txt", "demo.json".getBytes(StandardCharsets.UTF_8), 0120777);
        Path zipPath = writeLocalPack(withLink);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Symlink entry rejected: link.txt", e.getMessage());
    }

    @Test
    void unlistedZipEntry_isRejected() throws IOException {
        byte[] zip = buildDefaultPack();
        byte[] withExtra = addEntry(zip, "extra.txt", "x".getBytes(StandardCharsets.UTF_8), 0644);
        Path zipPath = writeLocalPack(withExtra);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Zip entry not listed in manifest inventory: extra.txt", e.getMessage());
    }

    @Test
    void manifestFileMissingFromZip_isRejected() throws IOException {
        // Manifest lists js/missing.js but the zip does not contain it.
        JSONObject manifest = new JSONObject();
        manifest.put("agent", AGENT);
        manifest.put("version", VERSION);
        manifest.put("defaultEntry", "demo.json");
        JSONObject f = new JSONObject();
        f.put("path", "js/missing.js");
        f.put("sha256", AgentPackResolver.sha256Hex("x".getBytes(StandardCharsets.UTF_8)));
        JSONObject demo = new JSONObject();
        demo.put("path", "demo.json");
        demo.put("sha256", AgentPackResolver.sha256Hex("{}".getBytes(StandardCharsets.UTF_8)));
        manifest.put("files", new org.json.JSONArray().put(f).put(demo));
        byte[] zip = zipOf(Map.of(
                "manifest.json", manifest.toString(2).getBytes(StandardCharsets.UTF_8),
                "demo.json", "{}".getBytes(StandardCharsets.UTF_8)));
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Manifest files missing from the zip: js/missing.js", e.getMessage());
    }

    @Test
    void manifestShaMismatch_isRejected() throws IOException {
        Map<String, String> files = new TreeMap<>(Map.of(
                "demo.json", "{\"name\":\"DemoJob\"}",
                "js/main.js", "print('hi');\n"));
        JSONObject manifest = new JSONObject();
        manifest.put("agent", AGENT);
        manifest.put("version", VERSION);
        manifest.put("defaultEntry", "demo.json");
        JSONObject f = new JSONObject();
        f.put("path", "js/main.js");
        f.put("sha256", AgentPackResolver.sha256Hex("tampered".getBytes(StandardCharsets.UTF_8)));
        JSONObject demo = new JSONObject();
        demo.put("path", "demo.json");
        demo.put("sha256", AgentPackResolver.sha256Hex(files.get("demo.json").getBytes(StandardCharsets.UTF_8)));
        manifest.put("files", new org.json.JSONArray().put(f).put(demo));
        byte[] zip = zipOf(Map.of(
                "manifest.json", manifest.toString(2).getBytes(StandardCharsets.UTF_8),
                "demo.json", files.get("demo.json").getBytes(StandardCharsets.UTF_8),
                "js/main.js", files.get("js/main.js").getBytes(StandardCharsets.UTF_8)));
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Manifest sha256 mismatch: js/main.js", e.getMessage());
    }

    @Test
    void encryptedZip_isRejectedWithDedicatedError() throws IOException {
        byte[] zip = flipEncryptionBit(buildDefaultPack());
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertTrue(e.getMessage().startsWith("Encrypted agent packs are not supported yet: "
                + zipPath.getFileName()), e.getMessage());
    }

    @Test
    void unsafeManifestIdentity_isRejectedBeforeCachePath() throws IOException {
        byte[] zip = buildPack(Map.of("demo.json", "{}"), "..", VERSION, "demo.json");
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertTrue(e.getMessage().contains("Unsafe manifest agent: \"..\""), e.getMessage());
        // No cache directory derived from the hostile name may exist.
        assertFalse(Files.exists(tempDir.resolve("packs")));
    }

    @Test
    void entryEscape_isRejected() throws IOException {
        byte[] zip = buildPack(Map.of("demo.json", "{}"), AGENT, VERSION, "../outside.json");
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath.toString(), null));
        assertEquals("Entry escapes the pack root: ../outside.json", e.getMessage());
    }

    @Test
    void missingEntry_isRejectedNamingDefaultEntry() throws IOException {
        byte[] zip = buildPack(Map.of("demo.json", "{}"), AGENT, VERSION, "demo.json");
        Path zipPath = writeLocalPack(zip);
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> resolver().resolve(zipPath + "#alt.json", null));
        assertEquals("Entry 'alt.json' not found in pack demo-1.0.0. manifest.json defaultEntry: demo.json",
                e.getMessage());
    }

    // ------------------------------------------------------------------
    // Cache reuse vs re-fetch
    // ------------------------------------------------------------------

    @Test
    void completeCache_isReusedWithoutReUnpack() throws IOException {
        publishPack(AGENT, VERSION, buildDefaultPack());
        AgentPackResolver r = resolver();
        ResolvedPack first = r.resolve("demo@latest", null);
        // Dart parity: every resolve re-downloads the zip and re-verifies the
        // .sha256 sidecar (the manifest lives inside the zip, so the cache key
        // cannot be known earlier) — the cache short-circuits the UNPACK.
        assertEquals(1, requestCount("/" + AGENT + "-" + VERSION + ".zip"));

        // A file not in the manifest inventory survives only when no re-unpack happens.
        Path marker = first.getPackRoot().resolve("marker.txt");
        Files.writeString(marker, "keep", StandardCharsets.UTF_8);

        ResolvedPack second = r.resolve("demo@latest", null);
        assertEquals(first.getPackRoot(), second.getPackRoot());
        assertEquals(2, requestCount("/" + AGENT + "-" + VERSION + ".zip"),
                "each run re-downloads and re-verifies the zip (Dart _obtainZip parity)");
        assertEquals(2, requestCount("/catalog.json"),
                "@latest re-reads the catalog on every resolve (fresh version lookup)");
        assertTrue(Files.exists(marker), "a complete verified cache must be reused without re-unpack");
    }

    @Test
    void corruptCache_isReFetchedAndRepaired() throws IOException {
        publishPack(AGENT, VERSION, buildDefaultPack());
        AgentPackResolver r = resolver();
        ResolvedPack first = r.resolve("demo@latest", null);

        // Corrupt a cached payload file (keeps manifest.json intact).
        Files.write(first.getPackRoot().resolve("js/main.js"), "corrupted".getBytes(StandardCharsets.UTF_8));
        ResolvedPack second = r.resolve("demo@latest", null);
        assertEquals(2, requestCount("/" + AGENT + "-" + VERSION + ".zip"),
                "a corrupt cache must fall back to a fresh download");
        assertEquals("print('hi');\n",
                Files.readString(second.getPackRoot().resolve("js/main.js"), StandardCharsets.UTF_8));
    }

    @Test
    void staleCacheManifest_isReUnpacked() throws IOException {
        publishPack(AGENT, VERSION, buildDefaultPack());
        AgentPackResolver r = resolver();
        r.resolve("demo@latest", null);

        // Tamper the cached manifest — stable hash no longer matches.
        Path packDir = packsRoot.resolve(AGENT + "-" + VERSION);
        Files.writeString(packDir.resolve("manifest.json"), "{\"agent\":\"demo\"}", StandardCharsets.UTF_8);
        r.resolve("demo@latest", null);
        assertEquals(2, requestCount("/" + AGENT + "-" + VERSION + ".zip"));
    }

    // ------------------------------------------------------------------
    // End-to-end + entry override
    // ------------------------------------------------------------------

    @Test
    void endToEnd_registryLatest_resolvesVerifiesUnpacks() throws IOException {
        Map<String, String> files = new TreeMap<>(Map.of(
                "demo.json", "{\"name\":\"DemoJob\",\"params\":{}}",
                "alt.json", "{\"name\":\"AltJob\",\"params\":{}}",
                "js/main.js", "print('hi');\n",
                "run.sh", "#!/bin/sh\necho ok\n"));
        publishPack(AGENT, VERSION, buildPack(files, AGENT, VERSION, "demo.json"));

        ResolvedPack pack = resolver().resolve("demo@latest", null);
        assertEquals(packsRoot.resolve(AGENT + "-" + VERSION).toAbsolutePath().normalize(),
                pack.getPackRoot().toAbsolutePath().normalize());
        assertEquals("demo.json", pack.getEntryFile().getFileName().toString());
        assertEquals(AGENT, pack.getAgent());
        assertEquals(VERSION, pack.getVersion());
        for (String f : files.keySet()) {
            assertTrue(Files.exists(pack.getPackRoot().resolve(f)), "unpacked: " + f);
        }
        // Exec-bit restoration parity: packed .sh keeps +x.
        assertTrue(Files.isExecutable(pack.getPackRoot().resolve("run.sh")));
    }

    @Test
    void entryOverride_selectsAlternateEntry() throws IOException {
        publishPack(AGENT, VERSION, buildPack(Map.of(
                "demo.json", "{\"name\":\"DemoJob\"}",
                "alt.json", "{\"name\":\"AltJob\"}"), AGENT, VERSION, "demo.json"));
        ResolvedPack pack = resolver().resolve("demo@latest#alt.json", null);
        assertEquals("alt.json", pack.getEntryFile().getFileName().toString());
    }

    // ------------------------------------------------------------------
    // Path rewriting (path duality)
    // ------------------------------------------------------------------

    @Test
    void rewritePaths_prefixAliasAndGuards() throws IOException {
        publishPack(AGENT, VERSION, buildPack(Map.of(
                "demo.json", "{}",
                "js/main.js", "x\n",
                "prompts/p.txt", "p\n"), AGENT, VERSION, "demo.json"));
        ResolvedPack pack = resolver().resolve("demo@latest", null);

        JSONObject config = new JSONObject("{\"params\":{" +
                "\"jsPath\": \"agents/js/main.js\"," +
                "\"plain\": \"js/main.js\"," +
                "\"url\": \"https://example.com/x.js\"," +
                "\"classpath\": \"classpath:builtin.js\"," +
                "\"absolute\": \"/etc/hosts\"," +
                "\"missing\": \"js/nope.js\"," +
                "\"role\": \"QA Engineer\"," +
                "\"cliPrompts\": [\"prompts/p.txt\", \"literal role\"]" +
                "}}");
        resolver().rewritePathsToPackRoot(config, pack.getPackRoot());

        JSONObject params = config.getJSONObject("params");
        assertEquals(pack.getPackRoot().resolve("js/main.js").toAbsolutePath().normalize().toString(),
                params.getString("jsPath"));
        // Strings under non-path keys and non-path refs stay untouched.
        assertEquals("js/main.js", params.getString("plain"));
        assertEquals("https://example.com/x.js", params.getString("url"));
        assertEquals("classpath:builtin.js", params.getString("classpath"));
        assertEquals("/etc/hosts", params.getString("absolute"));
        assertEquals("js/nope.js", params.getString("missing"));
        assertEquals("QA Engineer", params.getString("role"));
        // List items that name existing pack files are rewritten.
        assertEquals(pack.getPackRoot().resolve("prompts/p.txt").toAbsolutePath().normalize().toString(),
                params.getJSONArray("cliPrompts").getString(0));
        assertEquals("literal role", params.getJSONArray("cliPrompts").getString(1));
    }

    // ------------------------------------------------------------------
    // Raw-zip helpers
    // ------------------------------------------------------------------

    private static byte[] zipOf(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(entries).entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(e.getKey());
                zos.putArchiveEntry(entry);
                zos.write(e.getValue());
                zos.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    /** Appends a raw entry (optionally with a unix mode) to an existing zip. */
    private static byte[] addEntry(byte[] zip, String name, byte[] content, int unixMode) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (org.apache.commons.compress.archivers.zip.ZipArchiveInputStream zis =
                     new org.apache.commons.compress.archivers.zip.ZipArchiveInputStream(
                             new java.io.ByteArrayInputStream(zip))) {
            ZipArchiveEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.put(e.getName(), zis.readAllBytes());
            }
        }
        entries.put(name, content);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(en.getKey());
                entry.setUnixMode(en.getKey().equals(name) ? unixMode : 0644);
                zos.putArchiveEntry(entry);
                zos.write(en.getValue());
                zos.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    /** Sets the general-purpose encryption flag (bit 0) on every zip header. */
    private static byte[] flipEncryptionBit(byte[] zip) {
        byte[] copy = zip.clone();
        for (int i = 0; i + 4 <= copy.length; i++) {
            boolean localHeader = copy[i] == 'P' && copy[i + 1] == 'K' && copy[i + 2] == 3 && copy[i + 3] == 4;
            boolean centralHeader = copy[i] == 'P' && copy[i + 1] == 'K' && copy[i + 2] == 1 && copy[i + 3] == 2;
            if (localHeader) {
                copy[i + 6] |= 1; // general-purpose flag low byte
            } else if (centralHeader) {
                copy[i + 8] |= 1;
            }
        }
        return copy;
    }
}
