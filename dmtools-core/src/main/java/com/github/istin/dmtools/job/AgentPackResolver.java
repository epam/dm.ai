// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a versioned agent pack (a local {@code .zip} file or an HTTPS URL)
 * to an unpacked, verified, cached directory that {@code dmtools run} can
 * execute from. Implements the runtime side of dm.ai #579; the pack format is
 * produced by {@link AgentPackCompiler} (dm.ai #595).
 *
 * <p>Pipeline: detect pack → download (URL only; {@code SOURCE_GITHUB_TOKEN}
 * for github.com) → verify SHA-256 against the sibling {@code .sha256} asset →
 * unpack into {@code ~/.dmtools/packs/<agent>-<version>/} with zip-slip
 * protection (no absolute paths, no {@code ..} escapes, no symlink entries,
 * per-file and total size caps) → verify every unpacked file against the
 * manifest's per-file sha256 inventory → cache-hit short-circuit → return the
 * entry config inside the cache.</p>
 *
 * <p>Nothing executes from inside the zip. A corrupt download or a
 * manifest/file mismatch deletes the partial cache and fails before any
 * execution. Encrypted zips are rejected with a dedicated error.</p>
 */
public class AgentPackResolver {

    private static final Logger logger = LogManager.getLogger(AgentPackResolver.class);

    /** Cache root for unpacked packs (overridable for tests). */
    private final Path packsRoot;

    /** Per-file unpack size cap (64 MiB). */
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    /** Total unpack size cap (512 MiB). */
    static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    /** The outcome of resolving a pack: the unpacked root and the entry config. */
    public static class ResolvedPack {
        public final Path packRoot;
        public final File entryFile;
        public final String agent;
        public final String version;

        ResolvedPack(Path packRoot, File entryFile, String agent, String version) {
            this.packRoot = packRoot;
            this.entryFile = entryFile;
            this.agent = agent;
            this.version = version;
        }
    }

    /** Default resolver: cache under {@code ~/.dmtools/packs}. */
    public AgentPackResolver() {
        this(defaultPacksRoot());
    }

    /** Test seam: explicit packs root. */
    public AgentPackResolver(Path packsRoot) {
        this.packsRoot = packsRoot;
    }

    static Path defaultPacksRoot() {
        return Path.of(System.getProperty("user.home"), ".dmtools", "packs");
    }

    /**
     * Rewrites repo-relative path references in [config] to absolute paths inside
     * [packRoot] (dm.ai #579 §3). Applies the prefix alias: {@code agents/js/x.js}
     * and {@code js/x.js} both resolve to {@code <packRoot>/js/x.js}. Only strings
     * that look like repo-file paths are rewritten; URLs, {@code classpath:} refs,
     * absolute paths, and literal role strings are left untouched.
     *
     * <p>This is how a packed agent runs without the repo checkout: JS entry points,
     * instructions, prompts, and scripts resolve inside the unpacked cache instead of
     * the process working directory.</p>
     */
    public void rewritePathsToPackRoot(JSONObject config, Path packRoot) {
        rewriteValue(config, packRoot, new java.util.LinkedHashSet<>());
    }

    /** Known config keys whose string values are single path references. */
    private static final String[] PATH_KEYS = {
        "jsPath", "preJSAction", "preCliJSAction", "postJSAction",
        "timerJSAction", "preprocessJSAction", "preAction", "postAction", "descriptionPath"
    };

    /** Array-valued config keys whose string items may be path references. */
    private static final String[] PATH_ARRAY_KEYS = {"cliPrompts", "cliCommands"};

    private void rewriteValue(JSONObject obj, Path packRoot, java.util.Set<String> visited) {
        for (String key : obj.keySet()) {
            Object value = obj.opt(key);
            if (value instanceof JSONObject) {
                rewriteValue((JSONObject) value, packRoot, visited);
            } else if (value instanceof JSONArray) {
                rewriteArray((JSONArray) value, packRoot);
            } else if (value instanceof String && isPathKey(key)) {
                String rewritten = toAbsolutePackPath((String) value, packRoot);
                if (rewritten != null) {
                    obj.put(key, rewritten);
                }
            }
        }
    }

    private void rewriteArray(JSONArray array, Path packRoot) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (item instanceof String) {
                String rewritten = toAbsolutePackPath((String) item, packRoot);
                if (rewritten != null) {
                    array.put(i, rewritten);
                }
            } else if (item instanceof JSONObject) {
                rewriteValue((JSONObject) item, packRoot, new java.util.LinkedHashSet<>());
            } else if (item instanceof JSONArray) {
                rewriteArray((JSONArray) item, packRoot);
            }
        }
    }

    private boolean isPathKey(String key) {
        for (String k : PATH_KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps a config string to an absolute pack-root path when it references a repo
     * file that exists in the pack; returns {@code null} for non-path strings.
     */
    private String toAbsolutePackPath(String value, Path packRoot) {
        String ref = value.trim();
        if (ref.isEmpty() || ref.startsWith("http://") || ref.startsWith("https://")
                || ref.startsWith("classpath:") || new File(ref).isAbsolute()) {
            return null;
        }
        // Strip leading ./ segments and the agents/ submodule prefix (path duality).
        while (ref.startsWith("./")) {
            ref = ref.substring(2);
        }
        if (ref.startsWith("agents/")) {
            ref = ref.substring("agents/".length());
        }
        // Only rewrite when the file actually exists in the pack (else leave as-is —
        // it may be a literal string like a cliPrompts role name).
        Path candidate = packRoot.resolve(ref).normalize();
        if (!candidate.startsWith(packRoot) || !candidate.toFile().isFile()) {
            return null;
        }
        return candidate.toAbsolutePath().toString();
    }

    /** cliPrompts / cliCommands handling is done in rewriteArray; kept for clarity. */
    static String[] pathArrayKeys() {
        return PATH_ARRAY_KEYS;
    }

    /**
     * True when [runArg] names a pack: a {@code .zip} path that exists, or an
     * {@code http(s)://…​.zip} URL, with an optional {@code #entry.json} suffix.
     */
    public boolean isPack(String runArg) {
        if (runArg == null) {
            return false;
        }
        String base = stripEntry(runArg);
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return base.toLowerCase().endsWith(".zip");
        }
        return base.toLowerCase().endsWith(".zip") && new File(base).isFile();
    }

    /**
     * Resolves [runArg] to a cached, verified pack. Returns {@code null} when
     * [runArg] is not a pack (caller falls back to the filesystem flow).
     *
     * @throws IOException on download/verification/unpack/manifest failures
     */
    public ResolvedPack resolve(String runArg) throws IOException {
        return resolve(runArg, null);
    }

    /** Test seam: a token provider for private GitHub release downloads. */
    public ResolvedPack resolve(String runArg, String githubToken) throws IOException {
        String entryOverride = entryOverride(runArg);
        String base = stripEntry(runArg);

        // 1. Obtain the zip bytes (download for URLs, read for local files).
        File zipFile = obtainZip(base, githubToken);

        // 2. Read + validate the manifest from inside the zip.
        JSONObject manifest = readManifest(zipFile);
        String agent = manifest.getString("agent");
        String version = manifest.getString("version");

        // 3. Cache: unpack into ~/.dmtools/packs/<agent>-<version>/ (atomic).
        Path packRoot = packsRoot.resolve(agent + "-" + version).normalize();
        unpackAndVerify(zipFile, manifest, packRoot);

        // 4. Resolve the entry config inside the unpacked pack.
        String entryName = entryOverride != null ? entryOverride : manifest.getString("defaultEntry");
        File entryFile = packRoot.resolve(entryName).toFile();
        if (!entryFile.isFile()) {
            throw new IOException("Entry '" + entryName + "' not found in pack " + agent + "-" + version
                    + ". manifest.json defaultEntry: " + manifest.getString("defaultEntry"));
        }
        logger.info("Resolved agent pack {}-{} -> {}", agent, version, packRoot);
        return new ResolvedPack(packRoot, entryFile, agent, version);
    }

    // ------------------------------------------------------------------
    // Argument parsing
    // ------------------------------------------------------------------

    /** Splits off an optional {@code #entry.json} override. */
    static String stripEntry(String runArg) {
        int hash = runArg.indexOf('#');
        return hash >= 0 ? runArg.substring(0, hash) : runArg;
    }

    /** The {@code #entry.json} override, or {@code null}. */
    static String entryOverride(String runArg) {
        int hash = runArg.indexOf('#');
        return hash >= 0 ? runArg.substring(hash + 1) : null;
    }

    // ------------------------------------------------------------------
    // Download (URL) / read (file)
    // ------------------------------------------------------------------

    private File obtainZip(String base, String githubToken) throws IOException {
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return download(base, githubToken);
        }
        File file = new File(base);
        if (!file.isFile()) {
            throw new IOException("Pack file not found: " + base);
        }
        return file;
    }

    private File download(String urlString, String githubToken) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Private GitHub release assets need a token (E1 in #579).
        if (githubToken != null && "github.com".equalsIgnoreCase(url.getHost())) {
            conn.setRequestProperty("Authorization", "Bearer " + githubToken);
            conn.setRequestProperty("Accept", "application/octet-stream");
        }
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download pack: HTTP " + status + " for " + urlString);
        }
        File tmp = Files.createTempFile("dmtools-pack-", ".zip").toFile();
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(tmp.toPath())) {
            in.transferTo(out);
        }
        // E2: verify the downloaded bytes against the sibling .sha256 asset.
        verifyDownloadedSha256(tmp, urlString, githubToken);
        return tmp;
    }

    /** Verifies the downloaded zip against the sibling {@code .sha256} asset (E2). */
    private void verifyDownloadedSha256(File zipFile, String zipUrl, String githubToken) throws IOException {
        String sha256Url = zipUrl + ".sha256";
        String expected;
        try {
            URL url = new URL(sha256Url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (githubToken != null && "github.com".equalsIgnoreCase(url.getHost())) {
                conn.setRequestProperty("Authorization", "Bearer " + githubToken);
                conn.setRequestProperty("Accept", "application/octet-stream");
            }
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.warn("No sibling .sha256 asset (HTTP {}) — skipping checksum verification", conn.getResponseCode());
                return;
            }
            expected = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().split("\\s+")[0];
        } catch (IOException e) {
            logger.warn("Could not fetch sibling .sha256 ({}); skipping checksum verification", e.getMessage());
            return;
        }
        String actual = sha256Hex(Files.readAllBytes(zipFile.toPath()));
        if (!expected.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(zipFile.toPath());
            throw new IOException("SHA-256 mismatch for " + zipUrl + " (expected " + expected + ", got " + actual + ")");
        }
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    private JSONObject readManifest(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile.Builder().setFile(zipFile).get()) {
            rejectIfEncrypted(zip, zipFile);
            ZipArchiveEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("Pack has no manifest.json: " + zipFile.getName());
            }
            String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            JSONObject manifest = new JSONObject(json);
            if (!manifest.has("agent") || !manifest.has("version") || !manifest.has("defaultEntry")) {
                throw new IOException("manifest.json missing required keys (agent/version/defaultEntry)");
            }
            return manifest;
        }
    }

    /**
     * AC6: encrypted zips are rejected with a dedicated error — {@code java.util.zip}
     * cannot read AES zips and weak ZipCrypto is deliberately not offered (the zip4j
     * decision is a future hook, not v1).
     */
    private void rejectIfEncrypted(ZipFile zip, File zipFile) throws IOException {
        java.util.Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (entry.getGeneralPurposeBit().usesEncryption() || entry.getGeneralPurposeBit().usesStrongEncryption()) {
                throw new IOException("Encrypted agent packs are not supported yet: " + zipFile.getName()
                        + " (encryption support is a planned future hook)");
            }
        }
    }

    // ------------------------------------------------------------------
    // Unpack + verify + cache
    // ------------------------------------------------------------------

    private void unpackAndVerify(File zipFile, JSONObject manifest, Path packRoot) throws IOException {
        // Cache hit: an existing manifest.json with a matching version short-circuits.
        if (isCacheHit(packRoot, manifest)) {
            logger.info("Pack cache hit: {}", packRoot);
            return;
        }

        Path tmp = Files.createTempDirectory(packsRoot.toAbsolutePath().getParent() != null
                ? packsRoot.toAbsolutePath().getParent() : Files.createTempDirectory("dmtools").toAbsolutePath(),
                "pack-unpack-");
        try {
            Map<String, String> manifestHashes = manifestHashes(manifest);
            unzipSafe(zipFile, tmp, manifestHashes);
            // Atomic publish: temp dir -> cache dir.
            Files.createDirectories(packRoot.getParent());
            if (Files.exists(packRoot)) {
                deleteRecursively(packRoot);
            }
            Files.move(tmp, packRoot, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tmp); // never leave a partial cache
            throw e instanceof IOException ? (IOException) e : new IOException("Pack unpack failed: " + e.getMessage(), e);
        }
    }

    private boolean isCacheHit(Path packRoot, JSONObject manifest) throws IOException {
        File cachedManifest = packRoot.resolve("manifest.json").toFile();
        if (!cachedManifest.isFile()) {
            return false;
        }
        try {
            JSONObject cached = new JSONObject(Files.readString(cachedManifest.toPath(), StandardCharsets.UTF_8));
            String zipManifestHash = sha256Hex(manifest.toString().getBytes(StandardCharsets.UTF_8));
            String cachedHash = sha256Hex(cached.toString().getBytes(StandardCharsets.UTF_8));
            return zipManifestHash.equals(cachedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> manifestHashes(JSONObject manifest) {
        Map<String, String> hashes = new HashMap<>();
        JSONArray files = manifest.optJSONArray("files");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                hashes.put(f.getString("path"), f.getString("sha256"));
            }
        }
        return hashes;
    }

    /** Unpacks with zip-slip protection, per-file sha256 verification, and exec bits. */
    private void unzipSafe(File zipFile, Path targetDir, Map<String, String> manifestHashes) throws IOException {
        String targetRoot = targetDir.toAbsolutePath().normalize().toString();
        long totalBytes = 0;
        try (ZipFile zip = new ZipFile.Builder().setFile(zipFile).get()) {
            java.util.Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                totalBytes = unpackEntry(zip, entry, targetDir, targetRoot, manifestHashes, totalBytes);
            }
        }
    }

    private long unpackEntry(ZipFile zip, ZipArchiveEntry entry, Path targetDir, String targetRoot,
                             Map<String, String> manifestHashes, long totalBytes) throws IOException {
        String name = entry.getName();
        // AC4: reject absolute paths, .. escapes, and symlink entries.
        if (name.startsWith("/") || name.contains("..") || new File(name).isAbsolute()) {
            throw new IOException("Zip-slip entry rejected: " + name);
        }
        if (entry.isUnixSymlink()) {
            throw new IOException("Symlink entry rejected: " + name);
        }
        Path target = targetDir.resolve(name).normalize();
        if (!target.toAbsolutePath().normalize().toString().startsWith(targetRoot)) {
            throw new IOException("Entry escapes target dir: " + name);
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return totalBytes;
        }
        Files.createDirectories(target.getParent());
        byte[] data = zip.getInputStream(entry).readAllBytes();
        if (data.length > MAX_FILE_BYTES) {
            throw new IOException("Entry too large: " + name);
        }
        totalBytes += data.length;
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new IOException("Pack exceeds total size cap");
        }
        // Verify against the manifest inventory (E3).
        String expectedHash = manifestHashes.get(name);
        if (expectedHash != null && !expectedHash.equalsIgnoreCase(sha256Hex(data))) {
            throw new IOException("Manifest sha256 mismatch: " + name);
        }
        Files.write(target, data);
        restoreExecBit(name, entry, target);
        return totalBytes;
    }

    /** Restores {@code 0755} on {@code *.sh} (recorded by the pack producer). */
    private void restoreExecBit(String name, ZipArchiveEntry entry, Path target) {
        try {
            int mode = entry.getUnixMode();
            boolean executable = name.endsWith(".sh") || (mode & 0b001_000_000) != 0;
            if (executable) {
                target.toFile().setExecutable(true, false);
            }
        } catch (Exception e) {
            logger.debug("Could not restore exec bit on {}: {}", name, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Small utilities
    // ------------------------------------------------------------------

    static String sha256Hex(byte[] data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
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

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
