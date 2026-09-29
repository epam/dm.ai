// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pack;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves a versioned agent pack (a local {@code .zip} file, an HTTPS URL, or a
 * registry ref {@code <agent>@<version|latest>}) to an unpacked, verified, cached
 * directory that {@code dmtools run} can execute from.
 *
 * <p>Java backport of the Dart {@code AgentPackResolver}
 * (dmtools-dart {@code lib/src/pack/agent_pack_resolver.dart}, dm.ai #579); the
 * pack format is produced by {@code AgentPackCompiler} (dm.ai #595). Behavior
 * and error-message wording mirror the Dart implementation so CI logs compare
 * 1:1.
 *
 * <p>Pipeline: detect pack ({@link #isPack}) → resolve registry ref to a concrete
 * zip URL via {@code <registry>/catalog.json} (env {@code DMTOOLS_PACK_REGISTRY})
 * → download (URL only; {@code SOURCE_GITHUB_TOKEN} for github.com) → verify
 * SHA-256 against the sibling {@code .sha256} asset (a missing sidecar skips
 * verification with a loud warning) → validate the manifest {@code agent}/
 * {@code version} (strict charset — they build the cache path) → unpack into a
 * staging dir on the cache's own filesystem and atomically rename into
 * {@code ~/.dmtools/packs/<agent>-<version>/} with zip-slip protection → verify
 * every unpacked file against the manifest's per-file sha256 inventory →
 * cache-hit short-circuit → return the entry config inside the cache (the entry
 * name is containment-checked against the pack root). Encrypted zips are
 * rejected with a dedicated error.
 */
public class AgentPackResolver {

    private static final Logger logger = LogManager.getLogger(AgentPackResolver.class);

    /** Per-file unpack size cap (64 MiB). */
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    /** Total unpack size cap (512 MiB). */
    public static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    /** Env var holding the registry base URL (a release download dir). */
    public static final String ENV_PACK_REGISTRY = "DMTOOLS_PACK_REGISTRY";

    /**
     * Matches {@code <agent>@<version|latest>} (never a path, URL, or {@code .json}
     * file).
     */
    private static final Pattern REGISTRY_REF =
            Pattern.compile("^([A-Za-z0-9_-]+)@(latest|[A-Za-z0-9][A-Za-z0-9._-]*)$");

    /** Allowed charset for manifest {@code agent}/{@code version} / catalog versions. */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");

    /** A segment made only of dots ({@code .} / {@code ..} / {@code ...}) is a traversal vector. */
    private static final Pattern DOTS_ONLY = Pattern.compile("^\\.+$");

    /** Unix mode mask for symlink entries. */
    private static final int UNIX_SYMLINK_MODE = 0120000;

    /** Owner-exec bit (0o100): packed non-.sh executables keep +x. */
    private static final int UNIX_OWNER_EXEC = 0100;

    /** Repo-relative path references rewritten to absolute paths inside the pack. */
    private static final Set<String> PATH_KEYS = new HashSet<>();
    static {
        PATH_KEYS.add("jsPath");
        PATH_KEYS.add("preJSAction");
        PATH_KEYS.add("preCliJSAction");
        PATH_KEYS.add("postJSAction");
        PATH_KEYS.add("timerJSAction");
        PATH_KEYS.add("preprocessJSAction");
        PATH_KEYS.add("preAction");
        PATH_KEYS.add("postAction");
        PATH_KEYS.add("descriptionPath");
    }

    private final Path packsRoot;
    private final OkHttpClient httpClient;

    /**
     * The configured registry base URL (env {@code DMTOOLS_PACK_REGISTRY}), lazily
     * resolved and cached; {@code null} when no registry is configured.
     */
    private String registryBaseUrl;
    private boolean registryLookedUp;

    /**
     * Creates a resolver; the packs root defaults to {@code ~/.dmtools/packs} and
     * the registry base URL to the {@code DMTOOLS_PACK_REGISTRY} env var (read
     * lazily on first use).
     */
    public AgentPackResolver() {
        this(null, null);
    }

    /**
     * Creates a resolver with explicit test seams.
     *
     * @param packsRoot       the pack cache root, or {@code null} for
     *                        {@code ~/.dmtools/packs}
     * @param registryBaseUrl pin the registry explicitly (tests); {@code null}
     *                        reads the env var lazily; an empty string disables
     *                        the registry hermetically
     */
    public AgentPackResolver(Path packsRoot, String registryBaseUrl) {
        this.packsRoot = (packsRoot != null
                ? packsRoot
                : Paths.get(System.getProperty("user.home"), ".dmtools", "packs"))
                .toAbsolutePath().normalize();
        this.registryBaseUrl = registryBaseUrl;
        this.registryLookedUp = registryBaseUrl != null;
        // Redirects are followed on every fetch (GitHub-Releases registry bases
        // 302 to a signed CDN URL). Budgets mirror the Dart sync client.
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /**
     * True when {@code runArg} names a pack: a {@code .zip} path that exists, an
     * {@code http(s)://….zip} URL, or a registry ref {@code <agent>@<version|latest>}
     * (when a registry is configured), with an optional {@code #entry.json} suffix.
     */
    public boolean isPack(String runArg) {
        if (runArg == null) {
            return false;
        }
        String base = stripEntry(runArg);
        if (isRegistryRef(base)) {
            return true;
        }
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return base.toLowerCase().endsWith(".zip");
        }
        return base.toLowerCase().endsWith(".zip") && Files.exists(Paths.get(base));
    }

    /**
     * True when {@code base} (entry-stripped) is a registry ref
     * {@code <agent>@<version|latest>} and a registry is configured. The {@code @}
     * form is required so bare agent/job names and plain {@code .json} files are
     * never mistaken for packs.
     */
    public boolean isRegistryRef(String base) {
        return registry() != null && REGISTRY_REF.matcher(base).matches();
    }

    /** Splits off an optional {@code #entry.json} override. */
    public static String stripEntry(String runArg) {
        int hash = runArg.indexOf('#');
        return hash >= 0 ? runArg.substring(0, hash) : runArg;
    }

    /** The {@code #entry.json} override, or {@code null}. */
    public static String entryOverrideOf(String runArg) {
        int hash = runArg.indexOf('#');
        return hash >= 0 ? runArg.substring(hash + 1) : null;
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    /**
     * Resolves {@code runArg} to a cached, verified pack.
     *
     * @param runArg      pack zip path, zip URL, or registry ref (with optional
     *                    {@code #entry.json} suffix)
     * @param githubToken authorizes private GitHub release downloads (the
     *                    {@code SOURCE_GITHUB_TOKEN} chain)
     * @return the unpacked pack root and entry config
     * @throws AgentPackException on download/verification/unpack failures
     */
    public ResolvedPack resolve(String runArg, String githubToken) throws IOException {
        String entryOverride = entryOverrideOf(runArg);
        String base = stripEntry(runArg);

        // Registry ref (<agent>@<version|latest>) → concrete zip URL via the registry.
        if (isRegistryRef(base)) {
            String originalRef = base;
            base = resolveRegistryZipUrl(base);
            System.err.println("Resolved registry ref '" + originalRef + "' -> " + base);
        }

        Path zipFile = obtainZip(base, githubToken);
        boolean downloaded = base.startsWith("http");
        try {
            JSONObject manifest = readManifest(zipFile);
            String agent = manifestString(manifest, "agent");
            String version = manifestString(manifest, "version");
            validateManifestIdentity(agent, version);

            Path packRoot = packsRoot.resolve(agent + "-" + version);
            unpackAndVerify(zipFile, manifest, packRoot);

            String entryName = entryOverride != null ? entryOverride : manifestString(manifest, "defaultEntry");
            validateEntryName(entryName, packRoot);
            Path entryFile = packRoot.resolve(entryName);
            if (!Files.exists(entryFile)) {
                throw new AgentPackException(
                        "Entry '" + entryName + "' not found in pack " + agent + "-" + version
                                + ". manifest.json defaultEntry: " + manifest.opt("defaultEntry"));
            }
            return new ResolvedPack(packRoot, entryFile, agent, version);
        } finally {
            // Clean up a downloaded temp zip (local files are left in place).
            if (downloaded) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Registry refs: <agent>@<version|latest> (env DMTOOLS_PACK_REGISTRY)
    // ------------------------------------------------------------------

    /**
     * The configured registry base URL with any trailing slash stripped, or
     * {@code null} when no registry is configured.
     */
    private String registry() {
        if (!registryLookedUp) {
            registryLookedUp = true;
            registryBaseUrl = System.getenv(ENV_PACK_REGISTRY);
        }
        String url = registryBaseUrl == null ? null : registryBaseUrl.trim();
        if (url == null || url.isEmpty()) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Maps a registry ref to the concrete pack zip URL. For {@code @latest} the
     * version is looked up in the registry's {@code catalog.json}; an explicit
     * {@code @version} is used as-is. Layout: {@code <registry>/<agent>-<version>.zip}
     * (with the sibling {@code .sha256} verified later by the normal download path).
     */
    String resolveRegistryZipUrl(String ref) {
        Matcher m = REGISTRY_REF.matcher(ref);
        if (!m.matches()) {
            throw new AgentPackException("Not a registry ref: " + ref);
        }
        String agent = m.group(1);
        String versionToken = m.group(2);
        String registry = registry();
        String version = "latest".equals(versionToken)
                ? fetchLatestVersion(registry, agent)
                : versionToken;
        return registry + "/" + agent + "-" + version + ".zip";
    }

    /**
     * Reads the latest published version of {@code agent} from the registry's
     * {@code catalog.json}. Accepts either a flat map {@code {"agent": "version"}}
     * or a nested {@code {"agents": {"agent": "version"}}} shape.
     */
    private String fetchLatestVersion(String registry, String agent) {
        String catalogUrl = registry + "/catalog.json";
        JSONObject catalog = fetchCatalog(catalogUrl);
        String version = catalogVersion(catalog, agent, catalogUrl);
        assertSafeCatalogVersion(version, agent);
        return version;
    }

    /** GETs and JSON-decodes the registry catalog. */
    private JSONObject fetchCatalog(String catalogUrl) {
        // Redirects are followed: GitHub-Releases registry bases 302 to a signed
        // CDN URL (Java parity: the resolver follows redirects on every fetch).
        HttpResult response = httpGet(catalogUrl, null);
        if (response.statusCode != 200) {
            throw new AgentPackException("Failed to read registry catalog: HTTP "
                    + response.statusCode + " for " + catalogUrl);
        }
        try {
            return new JSONObject(new String(response.body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AgentPackException("Malformed registry catalog " + catalogUrl + ": " + e.getMessage());
        }
    }

    /** Extracts {@code agent}'s version from the decoded catalog (flat or nested). */
    private String catalogVersion(JSONObject catalog, String agent, String catalogUrl) {
        String version = versionFromCatalog(catalog, agent);
        if (version == null || version.isEmpty()) {
            throw new AgentPackException("Agent '" + agent + "' not found in registry catalog "
                    + catalogUrl + ". Available agents: " + availableAgentNames(catalog));
        }
        return version;
    }

    /** Flat {@code {"agent": "version"}} first, then nested {@code {"agents": …}}. */
    private String versionFromCatalog(JSONObject catalog, String agent) {
        String direct = catalog.optString(agent, null);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        JSONObject agents = catalog.optJSONObject("agents");
        if (agents == null) {
            return null;
        }
        String nested = agents.optString(agent, null);
        return nested != null && !nested.isEmpty() ? nested : null;
    }

    private String availableAgentNames(JSONObject catalog) {
        Set<String> names = new LinkedHashSet<>();
        for (String key : catalog.keySet()) {
            if (!"agents".equals(key)) {
                names.add(key);
            }
        }
        JSONObject agents = catalog.optJSONObject("agents");
        if (agents != null) {
            names.addAll(agents.keySet());
        }
        return names.stream().sorted().collect(Collectors.joining(", "));
    }

    /**
     * The catalog is an external input interpolated into the download URL — pin it
     * to the same strict charset the manifest version is held to.
     */
    private void assertSafeCatalogVersion(String version, String agent) {
        if (!SAFE_SEGMENT.matcher(version).matches() || DOTS_ONLY.matcher(version).matches()) {
            throw new AgentPackException("Unsafe catalog version for " + agent + ": \"" + version
                    + "\" (allowed: [A-Za-z0-9._-], no dot-only segments)");
        }
    }

    // ------------------------------------------------------------------
    // Download (URL) / read (file)
    // ------------------------------------------------------------------

    private Path obtainZip(String base, String githubToken) throws IOException {
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return download(base, githubToken);
        }
        Path file = Paths.get(base);
        if (!Files.exists(file)) {
            throw new AgentPackException("Pack file not found: " + base);
        }
        return file;
    }

    private Path download(String url, String githubToken) throws IOException {
        HttpResult response = httpGet(url, githubToken);
        if (response.statusCode != 200) {
            throw new AgentPackException("Failed to download pack: HTTP "
                    + response.statusCode + " for " + url);
        }
        Path tmp = Files.createTempFile("dmtools-pack-", ".zip");
        Files.write(tmp, response.body);
        verifyDownloadedSha256(tmp, url, githubToken);
        return tmp;
    }

    /** Verifies the downloaded zip against the sibling {@code .sha256} asset. */
    private void verifyDownloadedSha256(Path zipFile, String zipUrl, String githubToken) throws IOException {
        HttpResult response = httpGet(zipUrl + ".sha256", githubToken);
        if (response.statusCode != 200) {
            // A missing sidecar means "no checksum published" — verification is
            // skipped, not failed, but the skip is announced loudly so it shows
            // up in run logs.
            System.err.println("WARNING: no .sha256 checksum asset for " + zipUrl
                    + " (HTTP " + response.statusCode + ") — skipping download integrity check");
            return;
        }
        String expected = new String(response.body, StandardCharsets.UTF_8).trim().split("\\s+")[0];
        String actual = sha256Hex(Files.readAllBytes(zipFile));
        if (!expected.equalsIgnoreCase(actual)) {
            Files.delete(zipFile);
            throw new AgentPackException("SHA-256 mismatch for " + zipUrl
                    + " (expected " + expected + ", got " + actual + ")");
        }
    }

    /** A minimal HTTP GET result (status 0 marks a transport failure, curl parity). */
    private static final class HttpResult {
        final int statusCode;
        final byte[] body;

        HttpResult(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private HttpResult httpGet(String url, String githubToken) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream");
        if (githubToken != null && "github.com".equals(hostOf(url))) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            return new HttpResult(response.code(), body != null ? body.bytes() : new byte[0]);
        } catch (IOException e) {
            logger.debug("HTTP GET failed for {}: {}", url, e.getMessage());
            return new HttpResult(0, new byte[0]);
        }
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    private JSONObject readManifest(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            // AC6 parity: encrypted zips are rejected with a dedicated error,
            // checked from the central-directory general-purpose flag before
            // any content is decoded.
            ZipArchiveEntry manifestEntry = null;
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    throw new AgentPackException("Encrypted agent packs are not supported yet: "
                            + zipFile.getFileName()
                            + " (encryption support is a planned future hook)");
                }
                if ("manifest.json".equals(entry.getName())) {
                    manifestEntry = entry;
                }
            }
            if (manifestEntry == null) {
                throw new AgentPackException("Pack has no manifest.json: " + zipFile.getFileName());
            }
            JSONObject manifest = new JSONObject(new String(
                    zip.getInputStream(manifestEntry).readAllBytes(), StandardCharsets.UTF_8));
            if (!manifest.has("agent") || !manifest.has("version") || !manifest.has("defaultEntry")) {
                throw new AgentPackException(
                        "manifest.json missing required keys (agent/version/defaultEntry)");
            }
            return manifest;
        } catch (IOException e) {
            // A corrupt zip fails here with its own decoder error.
            throw new AgentPackException(e.getMessage(), e);
        }
    }

    /**
     * Reads a manifest string key, failing with a clean {@link AgentPackException}
     * (not a bare coercion error) when the value is missing or not a string.
     */
    static String manifestString(JSONObject manifest, String key) {
        Object value = manifest.opt(key);
        if (!(value instanceof String)) {
            throw new AgentPackException("manifest.json \"" + key + "\" must be a string (got "
                    + (value == null ? "null" : value.getClass().getSimpleName()) + ")");
        }
        return (String) value;
    }

    /**
     * Rejects unsafe manifest {@code agent}/{@code version} values BEFORE any cache
     * path is built from them (cache-path traversal guard; also shields the
     * recursive delete).
     */
    private void validateManifestIdentity(String agent, String version) {
        validateSafeSegment("agent", agent);
        validateSafeSegment("version", version);
    }

    private void validateSafeSegment(String key, String value) {
        if (value.isEmpty() || !SAFE_SEGMENT.matcher(value).matches() || DOTS_ONLY.matcher(value).matches()) {
            throw new AgentPackException("Unsafe manifest " + key + ": \"" + value
                    + "\" (allowed: [A-Za-z0-9._-], no dot-only segments)");
        }
    }

    /**
     * Containment-checks an entry-config name (a {@code #entry} CLI override or the
     * manifest {@code defaultEntry}) against {@code packRoot}, so a hostile or
     * careless value cannot point the run at a file outside the unpacked pack.
     */
    private void validateEntryName(String entryName, Path packRoot) {
        Path root = packRoot.toAbsolutePath().normalize();
        Path target = root.resolve(entryName).normalize();
        if (Paths.get(entryName).isAbsolute() || !target.startsWith(root)) {
            throw new AgentPackException("Entry escapes the pack root: " + entryName);
        }
    }

    // ------------------------------------------------------------------
    // Unpack + verify + cache
    // ------------------------------------------------------------------

    private void unpackAndVerify(Path zipFile, JSONObject manifest, Path packRoot) throws IOException {
        if (isCacheHit(packRoot, manifest)) {
            logger.info("Pack cache hit: {} — reusing verified unpack", packRoot);
            return;
        }

        // Stage the unpack next to the cache (same filesystem) so the publish
        // rename stays atomic — a system-temp staging dir fails with EXDEV
        // whenever /tmp and $HOME are different mounts.
        Path stagingParent = packsRoot.getParent();
        Files.createDirectories(stagingParent);
        Path tmp = Files.createTempDirectory(stagingParent, ".pack-unpack-");
        try {
            Map<String, String> hashes = manifestHashes(manifest);
            unzipSafe(zipFile, tmp, hashes);
            Files.createDirectories(packRoot.getParent());
            deleteWithinPacksRoot(packRoot);
            try {
                Files.move(tmp, packRoot, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, packRoot);
            }
        } catch (Exception e) {
            deleteRecursivelySilently(tmp);
            throw e;
        }
    }

    /**
     * Recursive-delete guard: refuses to delete anything that is not inside the
     * pack cache root, so the re-unpack cleanup can never be aimed at an existing
     * directory outside it.
     */
    private void deleteWithinPacksRoot(Path dir) throws IOException {
        Path root = packsRoot.toAbsolutePath().normalize();
        Path target = dir.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new AgentPackException(
                    "Refusing to delete outside the pack cache root: " + dir);
        }
        deleteRecursivelySilently(dir);
    }

    private static void deleteRecursivelySilently(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private boolean isCacheHit(Path packRoot, JSONObject manifest) {
        Path cachedManifest = packRoot.resolve("manifest.json");
        if (!Files.exists(cachedManifest)) {
            return false;
        }
        try {
            JSONObject cached = new JSONObject(Files.readString(cachedManifest, StandardCharsets.UTF_8));
            if (!stableHash(cached).equals(stableHash(manifest))) {
                return false;
            }
            return cachedFilesIntact(packRoot, manifest);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Re-verifies every manifest-listed cached file (existence + sha256). A
     * manifest match alone would execute whatever tampered or corrupted bytes are
     * on disk, so a mismatch falls back to a fresh unpack.
     */
    private boolean cachedFilesIntact(Path packRoot, JSONObject manifest) throws IOException {
        for (Map.Entry<String, String> entry : manifestHashes(manifest).entrySet()) {
            Path file = packRoot.resolve(entry.getKey());
            if (!Files.exists(file)) {
                return false;
            }
            if (!entry.getValue().equalsIgnoreCase(sha256Hex(Files.readAllBytes(file)))) {
                return false;
            }
        }
        return true;
    }

    private String stableHash(JSONObject manifest) {
        return sha256Hex(manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> manifestHashes(JSONObject manifest) {
        Map<String, String> hashes = new HashMap<>();
        JSONArray files = manifest.optJSONArray("files");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f != null) {
                    String path = f.optString("path", null);
                    String sha = f.optString("sha256", null);
                    if (path != null && sha != null) {
                        hashes.put(path, sha);
                    }
                }
            }
        }
        return hashes;
    }

    private void unzipSafe(Path zipFile, Path targetDir, Map<String, String> hashes) throws IOException {
        Path targetRoot = targetDir.toAbsolutePath().normalize();
        Set<String> seen = new HashSet<>();
        long totalBytes = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path target = validateEntryTarget(entry, targetDir, targetRoot);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                seen.add(entry.getName());
                byte[] data = readEntryBounded(zip, entry);
                totalBytes = verifyEntryData(entry.getName(), data, hashes, totalBytes);
                Files.createDirectories(target.getParent());
                Files.write(target, data);
                // restoreExecBit parity: .sh entries OR any entry whose unix mode
                // carries the owner-exec bit, so packed non-.sh executables keep +x.
                if (entry.getName().endsWith(".sh") || (entry.getUnixMode() & UNIX_OWNER_EXEC) != 0) {
                    makeExecutable(target);
                }
            }
        } catch (IOException e) {
            throw new AgentPackException(e.getMessage(), e);
        }
        // The manifest is the source of truth in both directions: every listed
        // file must actually be present in the zip.
        String missing = hashes.keySet().stream()
                .filter(name -> !seen.contains(name))
                .sorted()
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new AgentPackException("Manifest files missing from the zip: " + missing);
        }
    }

    private byte[] readEntryBounded(ZipFile zip, ZipArchiveEntry entry) throws IOException {
        if (entry.getSize() > MAX_FILE_BYTES) {
            throw new AgentPackException("Entry too large: " + entry.getName());
        }
        byte[] data = zip.getInputStream(entry).readAllBytes();
        if (data.length > MAX_FILE_BYTES) {
            throw new AgentPackException("Entry too large: " + entry.getName());
        }
        return data;
    }

    /**
     * Validates a zip entry path (AC4) and returns its safe target path.
     */
    private Path validateEntryTarget(ZipArchiveEntry entry, Path targetDir, Path targetRoot) {
        String name = entry.getName();
        if (name.startsWith("/") || name.contains("..") || Paths.get(name).isAbsolute()) {
            throw new AgentPackException("Zip-slip entry rejected: " + name);
        }
        if (entry.isUnixSymlink() || (entry.getUnixMode() & UNIX_SYMLINK_MODE) == UNIX_SYMLINK_MODE) {
            throw new AgentPackException("Symlink entry rejected: " + name);
        }
        Path target = targetDir.resolve(name).normalize();
        if (!target.startsWith(targetRoot) && !target.equals(targetRoot)) {
            throw new AgentPackException("Entry escapes target dir: " + name);
        }
        return target;
    }

    /**
     * Verifies entry size caps and the manifest sha256; returns the new total.
     */
    private long verifyEntryData(String name, byte[] data, Map<String, String> hashes, long totalBytes) {
        if (data.length > MAX_FILE_BYTES) {
            throw new AgentPackException("Entry too large: " + name);
        }
        totalBytes += data.length;
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new AgentPackException("Pack exceeds total size cap");
        }
        // The manifest inventory is the source of truth: a file entry it does not
        // list is rejected (fail loudly) rather than extracted unverified.
        // manifest.json is exempt — it IS the inventory.
        if (!"manifest.json".equals(name) && !hashes.containsKey(name)) {
            throw new AgentPackException("Zip entry not listed in manifest inventory: " + name);
        }
        String expectedHash = hashes.get(name);
        if (expectedHash != null && !expectedHash.equalsIgnoreCase(sha256Hex(data))) {
            throw new AgentPackException("Manifest sha256 mismatch: " + name);
        }
        return totalBytes;
    }

    private void makeExecutable(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (Exception e) {
            // best-effort on non-POSIX hosts
        }
    }

    static String sha256Hex(byte[] data) {
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
            throw new AgentPackException("SHA-256 not available", e);
        }
    }

    // ------------------------------------------------------------------
    // Path rewriting (path duality, #579 §3)
    // ------------------------------------------------------------------

    /**
     * Rewrites repo-relative path references in {@code config} to absolute paths
     * inside {@code packRoot}. Applies the prefix alias: {@code agents/js/x.js} and
     * {@code js/x.js} both resolve to {@code <packRoot>/js/x.js}. Only strings that
     * look like repo-file paths present in the pack are rewritten; URLs,
     * {@code classpath:} refs, absolute paths, and literal role strings are left
     * untouched.
     */
    public void rewritePathsToPackRoot(JSONObject config, Path packRoot) {
        rewriteObject(config, packRoot);
    }

    private void rewriteObject(JSONObject obj, Path packRoot) {
        for (String key : new LinkedHashSet<>(obj.keySet())) {
            Object value = obj.opt(key);
            if (value instanceof JSONObject) {
                rewriteObject((JSONObject) value, packRoot);
            } else if (value instanceof JSONArray) {
                rewriteList((JSONArray) value, packRoot);
            } else if (value instanceof String && PATH_KEYS.contains(key)) {
                String rewritten = toAbsolutePackPath((String) value, packRoot);
                if (rewritten != null) {
                    obj.put(key, rewritten);
                }
            }
        }
    }

    private void rewriteList(JSONArray list, Path packRoot) {
        for (int i = 0; i < list.length(); i++) {
            Object item = list.opt(i);
            if (item instanceof String) {
                String rewritten = toAbsolutePackPath((String) item, packRoot);
                if (rewritten != null) {
                    list.put(i, rewritten);
                }
            } else if (item instanceof JSONObject) {
                rewriteObject((JSONObject) item, packRoot);
            } else if (item instanceof JSONArray) {
                rewriteList((JSONArray) item, packRoot);
            }
        }
    }

    /**
     * Maps a config string to an absolute pack-root path when it references a repo
     * file present in the pack; returns {@code null} for non-path strings.
     */
    private String toAbsolutePackPath(String value, Path packRoot) {
        String ref = value.trim();
        if (isNonPathRef(ref)) {
            return null;
        }
        while (ref.startsWith("./")) {
            ref = ref.substring(2);
        }
        if (ref.startsWith("agents/")) {
            ref = ref.substring("agents/".length());
        }
        Path root = packRoot.toAbsolutePath().normalize();
        Path candidate = root.resolve(ref).normalize();
        if (!candidate.startsWith(root) || !Files.exists(candidate)) {
            return null;
        }
        return candidate.toString();
    }

    /**
     * True when {@code ref} is not a repo-relative path (empty, an absolute or
     * {@code http(s)}/{@code classpath:} reference) and must be left untouched.
     */
    private static boolean isNonPathRef(String ref) {
        return ref.isEmpty()
                || ref.startsWith("http://")
                || ref.startsWith("https://")
                || ref.startsWith("classpath:")
                || Paths.get(ref).isAbsolute();
    }
}
