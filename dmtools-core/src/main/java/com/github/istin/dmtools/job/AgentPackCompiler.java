// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/**
 * Builds a versioned, self-contained agent pack ({@code <agent>-<version>.zip}
 * + {@code manifest.json} + {@code .sha256}) from an agent entry config in a
 * dmtools-agents checkout. Implements dm.ai #595; the pack format is the shared
 * contract consumed by the agent-pack runtime (#579).
 *
 * <p>The closure is computed, never hard-coded: the entry config is parsed for
 * referenced paths ({@code jsPath}, {@code preJSAction}, {@code preCliJSAction},
 * {@code postJSAction}, {@code timerJSAction}, {@code preprocessJSAction},
 * {@code cliPrompts}, {@code cliCommands}, {@code metadata.descriptionPath},
 * {@code parent.path} chains), each referenced JS file is transitively scanned
 * for {@code require(...)}/{@code loadModule(...)}, and the whole
 * {@code scripts/} subtree is embedded when any script is referenced.</p>
 *
 * <p>Path duality: references {@code agents/js/x.js} and {@code js/x.js} both
 * normalize to the pack-relative {@code js/x.js}. The zip is deterministic
 * (sorted entries, fixed timestamps, {@code 0755} on {@code *.sh}).</p>
 */
public class AgentPackCompiler {

    private static final Logger logger = LogManager.getLogger(AgentPackCompiler.class);

    /** Fixed zip entry timestamp for reproducible builds (1980-01-01, the zip epoch). */
    private static final long FIXED_TIMESTAMP = 315532800000L;

    /** JSON fields (at any nesting depth under {@code params}) holding a single path. */
    private static final String[] SINGLE_PATH_KEYS = {
        "jsPath", "preJSAction", "preCliJSAction", "postJSAction",
        "timerJSAction", "preprocessJSAction", "preAction", "postAction"
    };

    /** Matches a JS module reference: {@code require('./x.js')} or {@code loadModule('./x.js')}. */
    private static final Pattern JS_MODULE_REF =
            Pattern.compile("(?:require|loadModule)\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    /** A string that references a repo file (has an extension and a path separator). */
    private static final Pattern PATH_LIKE =
            Pattern.compile("^[./]*[\\w./-]+\\.(js|json|md|sh|py|txt|yaml|yml|properties)$");

    private final Path agentRoot;

    /**
     * Creates a compiler rooted at the agents checkout.
     *
     * @param agentRoot directory containing {@code js/}, {@code instructions/}, etc.
     */
    public AgentPackCompiler(File agentRoot) {
        this.agentRoot = agentRoot.toPath().toAbsolutePath().normalize();
    }

    /** The result of a compile: the produced artifacts. */
    public static class PackResult {
        public final File zipFile;
        public final File manifestFile;
        public final File shaFile;
        public final int fileCount;

        PackResult(File zipFile, File manifestFile, File shaFile, int fileCount) {
            this.zipFile = zipFile;
            this.manifestFile = manifestFile;
            this.shaFile = shaFile;
            this.fileCount = fileCount;
        }
    }

    /**
     * Compiles the pack for the given entry config.
     *
     * @param entryJson    the agent entry {@code *.json} inside {@code agentRoot}
     * @param version      the pack version (semver string)
     * @param sourceCommit the git commit of the source tree (may be {@code "unknown"})
     * @param outDir       where to write the zip / manifest / sha256
     * @return the produced artifacts
     * @throws IOException if a referenced file is missing or writing fails
     */
    public PackResult compile(File entryJson, String version, String sourceCommit, File outDir) throws IOException {
        String agentName = stripJsonExtension(entryJson.getName());
        logger.info("Compiling agent pack: {} v{}", agentName, version);

        // Map of pack-relative path -> absolute source file, sorted for determinism.
        Map<String, File> closure = new TreeMap<>();
        Set<String> visitedConfigs = new LinkedHashSet<>();
        Set<String> visitedJs = new LinkedHashSet<>();

        collectConfig(entryJson.toPath(), closure, visitedConfigs, visitedJs);

        // Always include top-level docs when present.
        includeIfExists(closure, "AGENTS.md");
        includeIfExists(closure, "LICENSE");

        // Embed the (normalized) entry config itself at the pack root.
        closure.put(entryJson.getName(), entryJson);

        if (closure.isEmpty()) {
            throw new IOException("Empty closure for " + entryJson.getName() + " — nothing to pack");
        }

        // Build manifest with per-file sha256 + mode.
        JSONObject manifest = buildManifest(agentName, version, sourceCommit, entryJson.getName(), closure);

        // Write artifacts.
        Files.createDirectories(outDir.toPath());
        String base = agentName + "-" + version;
        File zipFile = new File(outDir, base + ".zip");
        File manifestFile = new File(outDir, "manifest.json");
        File shaFile = new File(outDir, base + ".zip.sha256");

        writeDeterministicZip(closure, manifest, zipFile);
        Files.write(manifestFile.toPath(), manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.write(shaFile.toPath(), (sha256Hex(zipFile) + "  " + zipFile.getName() + "\n").getBytes(StandardCharsets.UTF_8));

        logger.info("Pack built: {} ({} files)", zipFile.getName(), closure.size());
        return new PackResult(zipFile, manifestFile, shaFile, closure.size());
    }

    // ------------------------------------------------------------------
    // Closure walking
    // ------------------------------------------------------------------

    /** Recursively collects an entry config and its {@code parent.path} chain. */
    private void collectConfig(Path configPath, Map<String, File> closure,
                               Set<String> visitedConfigs, Set<String> visitedJs) throws IOException {
        Path normalized = configPath.toAbsolutePath().normalize();
        if (!visitedConfigs.add(normalized.toString())) {
            return; // cycle safety
        }
        File configFile = normalized.toFile();
        if (!configFile.isFile()) {
            throw new IOException("Config not found: " + normalized);
        }
        String content = Files.readString(normalized, StandardCharsets.UTF_8);
        JSONObject config;
        try {
            config = new JSONObject(content);
        } catch (Exception e) {
            throw new IOException("Invalid JSON in " + normalized + ": " + e.getMessage(), e);
        }

        collectPathsFromJson(config, closure, visitedJs);

        // Follow parent.path (override inheritance must keep working — the parent's
        // referenced files AND the parent config itself are part of the runtime closure).
        JSONObject parent = config.optJSONObject("parent");
        if (parent != null) {
            String parentPath = parent.optString("path", null);
            if (parentPath != null && !parentPath.isEmpty()) {
                Path resolved = resolveReference(parentPath);
                if (resolved == null) {
                    throw new IOException("parent.path not found: " + parentPath + " (referenced from " + normalized + ")");
                }
                String packRelative = agentRoot.relativize(resolved).toString().replace(File.separatorChar, '/');
                closure.putIfAbsent(packRelative, resolved.toFile());
                collectConfig(resolved, closure, visitedConfigs, visitedJs);
            }
        }
    }

    /** Walks the config JSON, collecting referenced files from known fields. */
    private void collectPathsFromJson(JSONObject config, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        // Single-path keys can appear at top level or under params/metadata/customParams.
        collectSinglePathKeys(config, closure, visitedJs);

        // cliPrompts: mixed array of literal role strings and file paths.
        JSONArray cliPrompts = findArray(config, "cliPrompts");
        if (cliPrompts != null) {
            for (int i = 0; i < cliPrompts.length(); i++) {
                Object item = cliPrompts.get(i);
                if (item instanceof String) {
                    maybeAddPathReference((String) item, closure, visitedJs);
                }
            }
        }

        // cliCommands: command strings; any scripts/** reference pulls the whole subtree.
        JSONArray cliCommands = findArray(config, "cliCommands");
        if (cliCommands != null) {
            boolean referencesScripts = false;
            for (int i = 0; i < cliCommands.length(); i++) {
                Object item = cliCommands.get(i);
                if (item instanceof String) {
                    String cmd = (String) item;
                    if (normalizeReference(cmd) != null && normalizeReference(cmd).startsWith("scripts/")) {
                        referencesScripts = true;
                    }
                    maybeAddCommandPath(cmd, closure, visitedJs);
                }
            }
            if (referencesScripts) {
                includeScriptsSubtree(closure);
            }
        }
    }

    /** Adds the file for each known single-path key found in the object tree. */
    private void collectSinglePathKeys(JSONObject obj, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        for (String key : SINGLE_PATH_KEYS) {
            String value = deepFindString(obj, key);
            if (value != null) {
                addPathReference(value, closure, visitedJs);
            }
        }
        // metadata.descriptionPath
        JSONObject metadata = obj.optJSONObject("params") != null
                ? obj.optJSONObject("params").optJSONObject("metadata")
                : obj.optJSONObject("metadata");
        if (metadata != null) {
            String descriptionPath = metadata.optString("descriptionPath", null);
            if (descriptionPath != null && !descriptionPath.isEmpty()) {
                addPathReference(descriptionPath, closure, visitedJs);
            }
        }
    }

    /** Adds a cliPrompts/cliCommands item only when it looks like a file path. */
    private void maybeAddPathReference(String value, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        String trimmed = value.trim();
        if (PATH_LIKE.matcher(trimmed).matches()) {
            addPathReference(trimmed, closure, visitedJs);
        }
    }

    /** Extracts a repo path from a shell command token (e.g. {@code ./agents/scripts/x.sh}). */
    private void maybeAddCommandPath(String command, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        for (String token : command.split("\\s+")) {
            String cleaned = token.replaceAll("^['\"]+|['\"]+$", "");
            if (PATH_LIKE.matcher(cleaned).matches()) {
                addPathReference(cleaned, closure, visitedJs);
            }
        }
    }

    /**
     * Resolves a config/JS reference to a source file and records it in the closure
     * under its normalized pack-relative path. JS files are transitively scanned.
     */
    private void addPathReference(String rawReference, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        String packRelative = normalizeReference(rawReference);
        if (packRelative == null) {
            return; // not a repo-file reference (URL, classpath, literal)
        }
        Path source = agentRoot.resolve(packRelative).normalize();
        if (!source.startsWith(agentRoot)) {
            throw new IOException("Reference escapes agent root: " + rawReference);
        }
        File file = source.toFile();
        if (!file.isFile()) {
            throw new IOException("Referenced file missing: " + rawReference + " (resolved to " + source + ")");
        }
        if (closure.put(packRelative, file) == null && packRelative.endsWith(".js")) {
            scanJsTransitive(file, closure, visitedJs);
        }
    }

    /** Transitively scans a JS file for {@code require(...)}/{@code loadModule(...)}. */
    private void scanJsTransitive(File jsFile, Map<String, File> closure, Set<String> visitedJs) throws IOException {
        Path normalized = jsFile.toPath().toAbsolutePath().normalize();
        if (!visitedJs.add(normalized.toString())) {
            return; // cycle safety
        }
        String content = stripJsComments(Files.readString(normalized, StandardCharsets.UTF_8));
        Matcher matcher = JS_MODULE_REF.matcher(content);
        Path parentDir = normalized.getParent();
        while (matcher.find()) {
            String ref = matcher.group(1);
            // Only relative module refs resolve inside the repo; bare names are runtime-provided.
            if (!ref.startsWith("./") && !ref.startsWith("../")) {
                continue;
            }
            String refWithExt = ref.endsWith(".js") ? ref : ref + ".js";
            Path resolved = parentDir.resolve(refWithExt).normalize();
            if (!resolved.startsWith(agentRoot)) {
                continue; // escapes the agent root — runtime/global module
            }
            File refFile = resolved.toFile();
            if (!refFile.isFile()) {
                throw new IOException("JS module not found: " + ref + " (required from " + normalized + ")");
            }
            String packRelative = agentRoot.relativize(resolved).toString().replace(File.separatorChar, '/');
            if (closure.put(packRelative, refFile) == null) {
                scanJsTransitive(refFile, closure, visitedJs);
            }
        }
    }

    /** Embeds the whole {@code scripts/} subtree (scripts self-locate via SCRIPT_DIR). */
    private void includeScriptsSubtree(Map<String, File> closure) throws IOException {
        Path scriptsDir = agentRoot.resolve("scripts");
        if (!Files.isDirectory(scriptsDir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(scriptsDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = agentRoot.relativize(p).toString().replace(File.separatorChar, '/');
                closure.put(rel, p.toFile());
            });
        }
    }

    /** Adds a top-level file (AGENTS.md / LICENSE) when it exists. */
    private void includeIfExists(Map<String, File> closure, String name) {
        File file = agentRoot.resolve(name).toFile();
        if (file.isFile()) {
            closure.put(name, file);
        }
    }

    // ------------------------------------------------------------------
    // Path normalization (agents/js/x.js -> js/x.js)
    // ------------------------------------------------------------------

    /**
     * Normalizes a reference to its pack-relative path, or returns {@code null} when
     * the reference is not a repo file (URL, {@code classpath:}, or a bare literal).
     */
    private String normalizeReference(String raw) {
        if (raw == null) {
            return null;
        }
        String ref = raw.trim();
        if (ref.isEmpty() || ref.startsWith("http://") || ref.startsWith("https://")
                || ref.startsWith("classpath:")) {
            return null;
        }
        // Strip leading ./ segments.
        while (ref.startsWith("./")) {
            ref = ref.substring(2);
        }
        // Strip the submodule prefix: agents/js/... -> js/...
        if (ref.startsWith("agents/")) {
            ref = ref.substring("agents/".length());
        }
        return ref;
    }

    /**
     * Resolves a config/JS reference to an absolute source path, or {@code null} when
     * the reference is not a repo file or does not exist. Used for {@code parent.path}.
     */
    private Path resolveReference(String raw) {
        String packRelative = normalizeReference(raw);
        if (packRelative == null) {
            return null;
        }
        Path resolved = agentRoot.resolve(packRelative).normalize();
        if (!resolved.startsWith(agentRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    // ------------------------------------------------------------------
    // Manifest + deterministic zip
    // ------------------------------------------------------------------

    private JSONObject buildManifest(String agentName, String version, String sourceCommit,
                                     String defaultEntry, Map<String, File> closure) throws IOException {
        JSONObject manifest = new JSONObject();
        manifest.put("agent", agentName);
        manifest.put("version", version);
        manifest.put("sourceCommit", sourceCommit == null ? "unknown" : sourceCommit);
        manifest.put("defaultEntry", defaultEntry);
        manifest.put("minDmtoolsVersion", readDmtoolsVersion());

        JSONArray files = new JSONArray();
        for (Map.Entry<String, File> entry : closure.entrySet()) {
            JSONObject fileEntry = new JSONObject();
            fileEntry.put("path", entry.getKey());
            fileEntry.put("sha256", sha256Hex(entry.getValue()));
            fileEntry.put("mode", entry.getKey().endsWith(".sh") ? "0755" : "0644");
            files.put(fileEntry);
        }
        manifest.put("files", files);
        return manifest;
    }

    private void writeDeterministicZip(Map<String, File> closure, JSONObject manifest, File zipFile) throws IOException {
        // Combine the closure files with the manifest under sorted entry order.
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("manifest.json", manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("manifest.json", "0644");
        for (Map.Entry<String, File> entry : closure.entrySet()) {
            entries.put(entry.getKey(), Files.readAllBytes(entry.getValue().toPath()));
            modes.put(entry.getKey(), entry.getKey().endsWith(".sh") ? "0755" : "0644");
        }

        try (OutputStream fos = Files.newOutputStream(zipFile.toPath());
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(fos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.getKey());
                zipEntry.setTime(FIXED_TIMESTAMP);
                zipEntry.setUnixMode(Integer.parseInt(modes.get(entry.getKey()), 8));
                zos.putArchiveEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeArchiveEntry();
            }
        }
    }

    // ------------------------------------------------------------------
    // Small utilities
    // ------------------------------------------------------------------

    /** Finds an array by key, looking under {@code params} when not at top level. */
    private JSONArray findArray(JSONObject config, String key) {
        if (config.has(key)) {
            return config.optJSONArray(key);
        }
        JSONObject params = config.optJSONObject("params");
        return params != null ? params.optJSONArray(key) : null;
    }

    /** Depth-first search for a string value by key anywhere in the object tree. */
    private String deepFindString(JSONObject obj, String key) {
        if (obj.has(key) && obj.opt(key) instanceof String) {
            return obj.optString(key);
        }
        for (String k : obj.keySet()) {
            Object child = obj.opt(k);
            if (child instanceof JSONObject) {
                String found = deepFindString((JSONObject) child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String sha256Hex(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private String readDmtoolsVersion() {
        // Prefer the package implementation version (set in the built jar manifest).
        try {
            Package pkg = AgentPackCompiler.class.getPackage();
            String version = pkg != null ? pkg.getImplementationVersion() : null;
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            // fall through
        }
        return "unknown";
    }

    private String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }

    /**
     * Strips JS line ({@code //}) and block ({@code /* ... *\/}) comments so the
     * dependency scanner does not match {@code require(...)} inside documentation
     * or commented-out code. String literals (single, double, backtick) are
     * preserved verbatim, so a {@code //} or {@code /*} inside a string (e.g. a
     * URL) is not treated as a comment start.
     */
    static String stripJsComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inSingle = false, inDouble = false, inTemplate = false;
        boolean inLineComment = false, inBlockComment = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++; // consume the '/'
                }
                continue;
            }
            boolean inString = inSingle || inDouble || inTemplate;
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < src.length()) {
                        out.append(src.charAt(++i)); // keep escaped char
                    }
                } else if (inSingle && c == '\'') {
                    inSingle = false;
                } else if (inDouble && c == '"') {
                    inDouble = false;
                } else if (inTemplate && c == '`') {
                    inTemplate = false;
                }
                continue;
            }
            // Not in a string or comment.
            if (c == '/' && next == '/') {
                inLineComment = true;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++; // consume the '*'
                continue;
            }
            if (c == '\'') {
                inSingle = true;
            } else if (c == '"') {
                inDouble = true;
            } else if (c == '`') {
                inTemplate = true;
            }
            out.append(c);
        }
        return out.toString();
    }
}
