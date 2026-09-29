// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pack;

import com.github.istin.dmtools.job.ConfigurationMerger;
import com.github.istin.dmtools.job.EncodingDetector;
import com.github.istin.dmtools.job.JobParams;
import com.github.istin.dmtools.job.ParentConfigResolver;
import com.github.istin.dmtools.job.RunCommandProcessor;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring tests: the agent-pack gate in {@link RunCommandProcessor} (dm.ai #579)
 * — a local .zip or a {@code <agent>@<version|latest>} registry ref resolves to
 * the cached pack and runs the entry config, ahead of the .js / known-job /
 * .json handling, with repo-relative paths rewritten into the pack cache.
 */
class RunCommandProcessorPackTest {

    private static final String AGENT = "demo";
    private static final String VERSION = "1.0.0";

    @TempDir
    Path tempDir;

    private Path packsRoot;
    private HttpServer server;
    private String registryUrl;

    private final Map<String, byte[]> servedFiles = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        packsRoot = tempDir.resolve("packs");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = servedFiles.get(exchange.getRequestURI().getPath());
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

    private RunCommandProcessor processor(AgentPackResolver packResolver) {
        return new RunCommandProcessor(new EncodingDetector(), new ConfigurationMerger(),
                new ParentConfigResolver(), packResolver);
    }

    private byte[] buildPack() throws IOException {
        Map<String, byte[]> entries = new java.util.TreeMap<>(Map.of(
                "demo.json", ("{\"name\":\"DemoJob\",\"params\":{" +
                        "\"jsPath\": \"agents/js/main.js\"}}").getBytes(StandardCharsets.UTF_8),
                "js/main.js", "print('hi');\n".getBytes(StandardCharsets.UTF_8)));
        JSONObject manifest = new JSONObject();
        manifest.put("agent", AGENT);
        manifest.put("version", VERSION);
        manifest.put("defaultEntry", "demo.json");
        List<JSONObject> fileEntries = new java.util.ArrayList<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            fileEntries.add(new JSONObject()
                    .put("path", e.getKey())
                    .put("sha256", AgentPackResolver.sha256Hex(e.getValue())));
        }
        manifest.put("files", fileEntries);
        entries.put("manifest.json", manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putArchiveEntry(new ZipArchiveEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    private void publishPack() throws IOException {
        byte[] zip = buildPack();
        String base = AGENT + "-" + VERSION;
        servedFiles.put("/" + base + ".zip", zip);
        servedFiles.put("/" + base + ".zip.sha256",
                (AgentPackResolver.sha256Hex(zip) + "  " + base + ".zip\n").getBytes(StandardCharsets.UTF_8));
        servedFiles.put("/catalog.json",
                ("{\"" + AGENT + "\": \"" + VERSION + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void runCommand_withLocalZip_resolvesPackAndRunsEntryConfig() throws IOException {
        Path zip = tempDir.resolve("demo-1.0.0.zip");
        Files.write(zip, buildPack());

        JobParams jobParams = processor(new AgentPackResolver(packsRoot, ""))
                .processRunCommand(new String[]{"run", zip.toString()});

        assertEquals("DemoJob", jobParams.getName());
        Path packRoot = packsRoot.resolve(AGENT + "-" + VERSION).toAbsolutePath().normalize();
        assertTrue(Files.exists(packRoot.resolve("demo.json")), "pack unpacked into the cache");
        // Repo-relative jsPath rewritten to an absolute path inside the pack cache.
        assertEquals(packRoot.resolve("js/main.js").toString(),
                jobParams.getParams().getString("jsPath"));
    }

    @Test
    void runCommand_withRegistryRef_resolvesViaCatalog() throws IOException {
        publishPack();
        JobParams jobParams = processor(new AgentPackResolver(packsRoot, registryUrl))
                .processRunCommand(new String[]{"run", "demo@latest"});

        assertEquals("DemoJob", jobParams.getName());
        assertTrue(Files.exists(packsRoot.resolve(AGENT + "-" + VERSION).resolve("js/main.js")));
    }

    @Test
    void runCommand_withRegistryRef_andCliOverrides() throws IOException {
        publishPack();
        JobParams jobParams = processor(new AgentPackResolver(packsRoot, registryUrl))
                .processRunCommand(new String[]{"run", "demo@latest", "--inputJql", "key = DEMO-1"});

        assertEquals("DemoJob", jobParams.getName());
        assertEquals("key = DEMO-1", jobParams.getParams().getString("inputJql"));
    }

    @Test
    void runCommand_packErrors_preserveAgentPackTaxonomy() throws IOException {
        publishPack();
        // Corrupt the sidecar: resolution must fail with the pack error, not a
        // generic "Run command processing failed" wrap.
        servedFiles.put("/" + AGENT + "-" + VERSION + ".zip.sha256",
                ("0".repeat(64) + "  bad\n").getBytes(StandardCharsets.UTF_8));
        AgentPackException e = assertThrows(AgentPackException.class,
                () -> processor(new AgentPackResolver(packsRoot, registryUrl))
                        .processRunCommand(new String[]{"run", "demo@latest"}));
        assertTrue(e.getMessage().startsWith("SHA-256 mismatch for " + registryUrl + "/"), e.getMessage());
    }

    @Test
    void runCommand_nonPackTargets_unaffectedByTheGate() {
        RunCommandProcessor processor = processor(new AgentPackResolver(packsRoot, registryUrl));
        // .js target → JSRunner config (pack gate must not claim it).
        JobParams js = processor.processRunCommand(new String[]{"run", "script.js"});
        assertEquals("JSRunner", js.getName());
        assertEquals("script.js", js.getParams().getString("jsPath"));
    }
}
