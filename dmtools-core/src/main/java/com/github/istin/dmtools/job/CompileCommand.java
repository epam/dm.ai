// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI handler for {@code dmtools compile} — builds a versioned, self-contained
 * agent pack from an agent entry config. Implements the command surface of
 * dm.ai #595; the heavy lifting lives in {@link AgentPackCompiler}.
 *
 * <pre>
 * dmtools compile &lt;entry.json&gt; \
 *     [--agent-root &lt;dir&gt;]           # default: directory of entry.json
 *     [--version &lt;semver&gt;]           # required unless --versions-file supplies it
 *     [--versions-file versions.json]  # per-agent versions map (agents repo)
 *     [--out &lt;dir&gt;]                  # default: ./dist
 *     [--source-commit &lt;sha&gt;]        # default: git rev-parse HEAD of agent-root
 * </pre>
 */
public class CompileCommand {

    private static final Logger logger = LogManager.getLogger(CompileCommand.class);

    /**
     * Runs the compile command.
     *
     * @param args command arguments (without the leading {@code compile})
     * @throws IOException if the build fails
     */
    public void run(String[] args) throws IOException {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        File entryJson = new File(args[0]);
        if (!entryJson.isFile()) {
            throw new IOException("Entry config not found: " + entryJson.getPath());
        }

        File agentRoot = optionValue(args, "--agent-root") != null
                ? new File(optionValue(args, "--agent-root"))
                : entryJson.getAbsoluteFile().getParentFile();
        String version = optionValue(args, "--version");
        String versionsFile = optionValue(args, "--versions-file");
        File outDir = optionValue(args, "--out") != null
                ? new File(optionValue(args, "--out"))
                : new File("dist");
        String sourceCommit = optionValue(args, "--source-commit");
        if (sourceCommit == null) {
            sourceCommit = detectSourceCommit(agentRoot);
        }

        String agentName = stripJsonExtension(entryJson.getName());

        // Version precedence: explicit --version, else the agent's entry in versions.json.
        if (version == null && versionsFile != null) {
            version = readVersionFromFile(new File(versionsFile), agentName);
        }
        if (version == null) {
            throw new IOException("Version is required: pass --version <semver> or --versions-file versions.json");
        }

        AgentPackCompiler compiler = new AgentPackCompiler(agentRoot);
        AgentPackCompiler.PackResult result = compiler.compile(entryJson, version, sourceCommit, outDir);

        System.out.println("Agent pack built successfully:");
        System.out.println("  agent:    " + agentName);
        System.out.println("  version:  " + version);
        System.out.println("  files:    " + result.fileCount);
        System.out.println("  zip:      " + result.zipFile.getPath());
        System.out.println("  manifest: " + result.manifestFile.getPath());
        System.out.println("  sha256:   " + result.shaFile.getPath());
    }

    /** Reads the agent's version from a {@code versions.json} map. */
    private String readVersionFromFile(File versionsFile, String agentName) throws IOException {
        if (!versionsFile.isFile()) {
            throw new IOException("versions file not found: " + versionsFile.getPath());
        }
        String content = Files.readString(versionsFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        JSONObject versions = new JSONObject(content);
        String version = versions.optString(agentName, null);
        if (version == null) {
            throw new IOException("Agent '" + agentName + "' not found in " + versionsFile.getPath());
        }
        return version;
    }

    /** Best-effort source commit: {@code git rev-parse HEAD} in the agent root. */
    private String detectSourceCommit(File agentRoot) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            pb.directory(agentRoot);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && !output.isEmpty()) {
                return output;
            }
        } catch (Exception e) {
            logger.debug("Could not detect source commit via git: {}", e.getMessage());
        }
        return "unknown";
    }

    /** Returns the value following {@code flag}, or {@code null} when absent. */
    private String optionValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }

    private void printUsage() {
        System.out.println("Usage: dmtools compile <entry.json> [options]");
        System.out.println();
        System.out.println("Build a versioned, self-contained agent pack (zip + manifest + sha256).");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --agent-root <dir>           Agents checkout root (default: entry.json's directory)");
        System.out.println("  --version <semver>           Pack version (required unless --versions-file)");
        System.out.println("  --versions-file versions.json  Per-agent versions map");
        System.out.println("  --out <dir>                  Output directory (default: ./dist)");
        System.out.println("  --source-commit <sha>        Source commit (default: git rev-parse HEAD)");
    }
}
