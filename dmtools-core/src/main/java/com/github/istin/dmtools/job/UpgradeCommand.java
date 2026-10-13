// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code dmtools upgrade [version]} / {@code dmtools update [version]} handled inside the JAR (epam/dm.ai#701).
 *
 * <p>The wrapper script ({@code dmtools.sh}) handles the command itself since v1.7.275, but an installation made
 * before that keeps its OLD wrapper, which hands {@code upgrade} to the JAR as if it were a tool name and answered
 * "Unknown tool: upgrade" — the very machines that need the upgrade most. This runs the same installer the wrapper
 * would: it downloads the release's {@code install.sh} and executes it with bash, which replaces the jar AND the
 * wrapper, so the next call goes through the new wrapper. Nothing is changed when the download fails, and
 * {@code DMTOOLS_UPGRADE_DRY_RUN=true} only prints what would happen.
 */
public class UpgradeCommand {

    static final String LATEST_URL = "https://github.com/epam/dm.ai/releases/latest/download/install.sh";
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+\\.\\d+\\.\\d+)$");

    /** Runs a process: installer script path + environment overrides. Replaceable in tests. */
    public interface ProcessRunner {
        int run(List<String> command, Map<String, String> envOverrides) throws IOException, InterruptedException;
    }

    /** Downloads a URL to a file. Replaceable in tests. */
    public interface Downloader {
        void download(String url, Path target) throws IOException, InterruptedException;
    }

    private final Map<String, String> env;
    private final PrintStream err;
    private final Downloader downloader;
    private final ProcessRunner runner;
    private final Path installDir;

    public UpgradeCommand() {
        this(System.getenv(), System.err, UpgradeCommand::httpDownload, UpgradeCommand::runProcess, defaultInstallDir(System.getenv()));
    }

    public UpgradeCommand(Map<String, String> env, PrintStream err, Downloader downloader, ProcessRunner runner, Path installDir) {
        this.env = env;
        this.err = err;
        this.downloader = downloader;
        this.runner = runner;
        this.installDir = installDir;
    }

    /** True for the argument that selects this command. */
    public static boolean handles(String firstArg) {
        return "upgrade".equals(firstArg) || "update".equals(firstArg);
    }

    /**
     * @param args the command line, args[0] being upgrade|update, optional args[1] a version
     * @return process exit code (0 = success or dry run)
     */
    public int run(String[] args) {
        String requested = args.length > 1 ? args[1].trim() : "";
        String url = installerUrl(requested);
        if (url == null) {
            err.println("Error: '" + requested + "' is not a valid version (expected e.g. 1.7.276).");
            return 2;
        }
        String before = installedVersion();
        err.println("DMTools upgrade: installed version " + before + "; installer: " + url);
        if ("true".equalsIgnoreCase(env.get("DMTOOLS_UPGRADE_DRY_RUN"))) {
            err.println("DMTools upgrade (dry run): would download and run the installer; nothing changed.");
            return 0;
        }
        Path script = null;
        try {
            script = Files.createTempFile("dmtools-upgrade", ".sh");
            try {
                downloader.download(url, script);
            } catch (IOException | RuntimeException e) {
                err.println("Error: could not download the installer from " + url + " (" + e.getMessage() + ")");
                err.println("Workaround: curl -fsSL " + LATEST_URL + " | bash");
                return 1;
            }
            List<String> command = new ArrayList<>(List.of("bash", script.toString()));
            Map<String, String> overrides = new java.util.HashMap<>();
            if (!requested.isEmpty()) {
                overrides.put("DMTOOLS_VERSION", "v" + normalize(requested));
            }
            int rc = runner.run(command, overrides);
            if (rc != 0) {
                err.println("Error: the installer failed (exit " + rc + "); the previous installation was left as it was.");
                return rc;
            }
            err.println("DMTools upgrade: " + before + " -> " + installedVersion());
            return 0;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("Error: upgrade interrupted");
            return 130;
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                    // temp file cleanup is best effort
                }
            }
        }
    }

    /** Installer URL for the request, or null when the version is malformed. DMTOOLS_INSTALL_SCRIPT_URL overrides. */
    String installerUrl(String requested) {
        String override = env.get("DMTOOLS_INSTALL_SCRIPT_URL");
        if (override != null && !override.trim().isEmpty()) {
            return override.trim();
        }
        if (requested.isEmpty()) {
            return LATEST_URL;
        }
        Matcher m = VERSION.matcher(requested);
        if (!m.matches()) {
            return null;
        }
        return "https://github.com/epam/dm.ai/releases/download/v" + m.group(1) + "/install.sh";
    }

    private static String normalize(String requested) {
        Matcher m = VERSION.matcher(requested);
        return m.matches() ? m.group(1) : requested;
    }

    /** Version recorded by the installer (installed-skills.json), or "unknown". */
    String installedVersion() {
        Path meta = installDir.resolve("installed-skills.json");
        try {
            if (Files.isRegularFile(meta)) {
                Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(meta, StandardCharsets.UTF_8));
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "unknown";
    }

    static Path defaultInstallDir(Map<String, String> env) {
        String configured = env.get("DMTOOLS_INSTALL_DIR");
        if (configured != null && !configured.trim().isEmpty()) {
            return Path.of(configured.trim());
        }
        return Path.of(System.getProperty("user.home"), ".dmtools");
    }

    static void httpDownload(String url, Path target) throws IOException, InterruptedException {
        if (url.startsWith("file:")) {
            Files.copy(Path.of(URI.create(url)), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
    }

    static int runProcess(List<String> command, Map<String, String> envOverrides) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
        pb.environment().putAll(envOverrides);
        return pb.start().waitFor();
    }
}
