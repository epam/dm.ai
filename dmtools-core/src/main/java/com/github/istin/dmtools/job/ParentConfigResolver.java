// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.pack.AgentPackException;
import com.github.istin.dmtools.teammate.CliPromptsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Resolves {@code parent} config inheritance for job configuration files.
 *
 * <p>When a job config JSON contains a {@code "parent"} block, this resolver:
 * <ol>
 *   <li>Loads the parent config file (relative to the current config's directory).</li>
 *   <li>Recursively resolves any {@code parent} block the parent itself may have.</li>
 *   <li>Deep-merges the child config on top of the parent (child scalars/arrays win by default).</li>
 *   <li>Applies {@code override} paths: fields listed here are taken from the child as-is
 *       (no recursive merge even for objects).</li>
 *   <li>Applies {@code merge} paths: array fields listed here are formed by prepending
 *       the parent's array items before the child's array items.</li>
 *   <li>Strips the {@code parent} block from the final result before returning.</li>
 * </ol>
 *
 * <p>Config shape:
 * <pre>{@code
 * {
 *   "name": "Teammate",
 *   "parent": {
 *     "path": "agents/base-teammate.json",
 *     "override": ["params.agentParams"],
 *     "merge":    ["params.agentParams.instructions"]
 *   },
 *   "params": {
 *     "inputJql": "key = SPECIFIC-123",
 *     "agentParams": { "aiRole": "QA Engineer", "instructions": ["check perf"] }
 *   }
 * }
 * }</pre>
 */
public class ParentConfigResolver {

    private static final Logger logger = LogManager.getLogger(ParentConfigResolver.class);

    public static final String PARENT      = "parent";
    public static final String PARENT_PATH     = "path";
    public static final String PARENT_OVERRIDE = "override";
    public static final String PARENT_MERGE    = "merge";

    /**
     * Path to the structured {@code cliPrompts} array inside the job config.
     * When present in both parent and child, it is merged by section id
     * instead of the default array-replacement behavior.
     */
    private static final String CLI_PROMPTS_PATH = "params.cliPrompts";

    private final ConfigurationMerger configurationMerger;

    /**
     * Resolves {@code parent.path} values that reference a versioned agent pack
     * (a {@code .zip} file, an {@code http(s)://…​.zip} URL, or a registry ref
     * {@code <agent>@<version|latest>}). Plain relative/absolute filesystem paths
     * keep the existing behaviour.
     */
    private AgentPackResolver packResolver = new AgentPackResolver();

    public ParentConfigResolver() {
        this.configurationMerger = new ConfigurationMerger();
    }

    ParentConfigResolver(ConfigurationMerger configurationMerger) {
        this.configurationMerger = configurationMerger;
    }

    /** Test seam / wiring: inject the pack resolver used for pack parents. */
    public void setPackResolver(AgentPackResolver packResolver) {
        this.packResolver = packResolver;
    }

    /**
     * Resolves parent inheritance for the given child config.
     *
     * @param childConfig   The parsed child JSON config (may contain a {@code "parent"} block)
     * @param childFilePath Absolute or relative path to the file that contains {@code childConfig};
     *                      used to resolve the {@code parent.path} relative to the same directory
     * @return The fully merged config with the {@code "parent"} block removed
     * @throws IllegalArgumentException if the parent file cannot be loaded or is invalid JSON
     */
    public JSONObject resolve(JSONObject childConfig, Path childFilePath) {
        if (!childConfig.has(PARENT)) {
            return childConfig;
        }

        JSONObject parentBlock = childConfig.getJSONObject(PARENT);
        String parentPathStr = parentBlock.optString(PARENT_PATH, null);
        if (parentPathStr == null || parentPathStr.trim().isEmpty()) {
            logger.warn("'parent' block present but 'path' is missing — ignoring inheritance");
            JSONObject result = new JSONObject(childConfig.toString());
            result.remove(PARENT);
            return result;
        }

        // Load the parent config (filesystem path or agent pack ref), recursively
        // resolving the parent's own inheritance. A pack parent additionally yields
        // its unpacked root so the child's `pack:` references can resolve against it.
        LoadedParent loaded = loadParentConfig(parentPathStr, childFilePath);

        // Capture original child values BEFORE merge (needed for override/merge processing)
        JSONObject originalChild = new JSONObject(childConfig.toString());
        originalChild.remove(PARENT);

        if (loaded.packRoot != null) {
            // The parent is an agent pack: the child's `pack:` references point
            // inside it (zip flow — no agents checkout mounted, dmtools-dart parity).
            rewritePackRefs(originalChild, loaded.packRoot);
        } else if (containsPackRef(originalChild)) {
            throw new IllegalArgumentException(
                    "child config uses pack: references but its parent is not an agent pack");
        }
        JSONObject parentConfig = loaded.config;

        // Read override and merge path lists
        JSONArray overridePaths = parentBlock.optJSONArray(PARENT_OVERRIDE);
        JSONArray mergePaths    = parentBlock.optJSONArray(PARENT_MERGE);

        // Base merge: parent ← child (child wins for scalars and arrays by default)
        JSONObject merged = configurationMerger.deepMerge(parentConfig, originalChild);

        // Apply structured cliPrompts merge: sections with the same id are merged
        // according to mergeStrategy, unnamed prompts keep their positions.
        applyStructuredCliPromptsMerge(merged, parentConfig, originalChild);

        // Apply override: at each listed path, replace merged value with original child value (no deep merge)
        if (overridePaths != null) {
            for (int i = 0; i < overridePaths.length(); i++) {
                String dotPath = overridePaths.getString(i).trim();
                Object childValue = getValueAtPath(originalChild, dotPath);
                if (childValue != null) {
                    setValueAtPath(merged, dotPath, childValue);
                    logger.debug("Override applied at '{}': {}", dotPath, childValue);
                }
            }
        }

        // Apply merge: at each listed array path, prepend parent items before child items
        if (mergePaths != null) {
            for (int i = 0; i < mergePaths.length(); i++) {
                String dotPath = mergePaths.getString(i).trim();
                prependArrayAtPath(merged, parentConfig, dotPath, originalChild);
                logger.debug("Array merge applied at '{}'", dotPath);
            }
        }

        return merged;
    }

    /**
     * Loads the parent config referenced by {@code parentPathStr} and recursively
     * resolves its own inheritance. Two forms are supported:
     * <ul>
     *   <li><b>Filesystem path</b> (existing): resolved relative to the child file's
     *       directory; the parent's own parent chain resolves from that directory.</li>
     *   <li><b>Agent pack ref</b>: a {@code .zip} file, an {@code http(s)://…​.zip} URL,
     *       or a registry ref {@code <agent>@<version|latest>}. The pack is resolved to
     *       its unpacked cache via {@link AgentPackResolver}; the parent's entry config is
     *       loaded from there, its own parent chain resolves inside the pack, and its
     *       repo-relative paths are rewritten to absolute paths in the pack cache so they
     *       survive the merge with the child (which may live in a different pack or repo).</li>
     * </ul>
     *
     * <p>Returns the resolved config plus the pack root when the parent came from an
     * agent pack ({@code null} for filesystem parents), so the caller can resolve the
     * child's {@code pack:} references against it.
     */
    private LoadedParent loadParentConfig(String parentPathStr, Path childFilePath) {
        if (packResolver.isPack(parentPathStr)) {
            return loadPackParent(parentPathStr);
        }
        if (packResolver.isRegistryRefShaped(parentPathStr)
                && !packResolver.hasRegistry()) {
            // `<agent>@<version|latest>` shape with no registry configured: a
            // bare "file not found" here would send every machine leg debugging
            // the wrong layer — say what is actually missing.
            throw new IllegalArgumentException(
                "parent.path '" + parentPathStr + "' is an agent-pack registry ref "
                + "(<agent>@<version|latest>) but no pack registry is configured — "
                + "set the DMTOOLS_PACK_REGISTRY env var to the release-registry "
                + "base URL");
        }

        // Filesystem flow (existing behaviour).
        Path childDir   = (childFilePath == null) ? Path.of("") : childFilePath.toAbsolutePath().getParent();
        Path parentPath = (childDir == null ? Path.of("") : childDir).resolve(parentPathStr).normalize();

        logger.info("Resolving parent config: {} → {}", parentPathStr, parentPath);

        String parentJson;
        try {
            if (!Files.exists(parentPath)) {
                throw new IllegalArgumentException("Parent config file does not exist: " + parentPath);
            }
            parentJson = Files.readString(parentPath);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read parent config file '" + parentPath + "': " + e.getMessage(), e);
        }

        JSONObject parentConfig = new JSONObject(parentJson);
        return new LoadedParent(resolve(parentConfig, parentPath), null);
    }

    /**
     * Resolves a pack-referenced parent to its fully-resolved entry config with
     * pack-relative paths made absolute (into the parent pack's cache).
     */
    private LoadedParent loadPackParent(String parentPathStr) {
        AgentPackResolver.ResolvedPack pack;
        try {
            pack = packResolver.resolve(parentPathStr, new PropertyReader().getGithubToken());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve parent agent pack '" + parentPathStr + "': " + e.getMessage(), e);
        }
        logger.info("Resolving parent config from agent pack: {} → {}-{} (entry {})",
                parentPathStr, pack.agent, pack.version, pack.entryFile);

        String parentJson;
        try {
            parentJson = Files.readString(pack.entryFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read parent pack entry '" + pack.entryFile + "': " + e.getMessage(), e);
        }

        // Recurse: the parent's own parent chain resolves relative to the pack root.
        JSONObject parentConfig = resolve(new JSONObject(parentJson), pack.entryFile.toPath());
        // Make the parent's pack-relative paths absolute so they keep working after the
        // merge, regardless of where the child lives.
        packResolver.rewritePathsToPackRoot(parentConfig, pack.packRoot);
        // `pack:` references inside the entry config resolve against this same pack.
        rewritePackRefs(parentConfig, pack.packRoot);
        return new LoadedParent(parentConfig, pack.packRoot);
    }

    /**
     * The resolved parent config plus the pack root when the parent came from an
     * agent pack ({@code null} for filesystem parents), so the caller can resolve
     * the child's {@code pack:} references against it.
     */
    private static final class LoadedParent {
        final JSONObject config;
        final Path packRoot;

        LoadedParent(JSONObject config, Path packRoot) {
            this.config = config;
            this.packRoot = packRoot;
        }
    }

    /**
     * Scheme prefix marking a config string as a reference into the resolved
     * parent agent pack ({@code pack:instructions/foo.md}). Only meaningful when
     * the config's parent is a pack.
     */
    private static final String PACK_SCHEME = "pack:";

    /**
     * Rewrites every {@code pack:} string under {@code node} to an absolute path inside
     * {@code packRoot}; throws {@link AgentPackException} on root escapes or missing
     * files. Non-string leaves and scheme-less strings pass through.
     */
    private void rewritePackRefs(Object node, Path packRoot) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String key : new ArrayList<>(obj.keySet())) {
                Object value = obj.opt(key);
                if (value instanceof String) {
                    String resolved = resolvePackRef((String) value, packRoot);
                    if (resolved != null) {
                        obj.put(key, resolved);
                    }
                } else if (value instanceof JSONObject || value instanceof JSONArray) {
                    rewritePackRefs(value, packRoot);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Object value = arr.get(i);
                if (value instanceof String) {
                    String resolved = resolvePackRef((String) value, packRoot);
                    if (resolved != null) {
                        arr.put(i, resolved);
                    }
                } else if (value instanceof JSONObject || value instanceof JSONArray) {
                    rewritePackRefs(value, packRoot);
                }
            }
        }
    }

    /** True when any string under {@code node} carries the {@code pack:} scheme. */
    private boolean containsPackRef(Object node) {
        if (node instanceof String) {
            return ((String) node).trim().startsWith(PACK_SCHEME);
        }
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String key : obj.keySet()) {
                if (containsPackRef(obj.opt(key))) {
                    return true;
                }
            }
        }
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                if (containsPackRef(arr.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Maps a {@code pack:}-prefixed string to an existing absolute path inside
     * {@code packRoot}; returns {@code null} for strings without the scheme.
     */
    private String resolvePackRef(String value, Path packRoot) {
        String ref = value.trim();
        if (!ref.startsWith(PACK_SCHEME)) {
            return null;
        }
        ref = ref.substring(PACK_SCHEME.length());
        while (ref.startsWith("/")) {
            ref = ref.substring(1);
        }
        Path candidate = packRoot.resolve(ref).normalize();
        if (!candidate.startsWith(packRoot) || !Files.exists(candidate)) {
            throw new AgentPackException(
                    "pack: reference '" + value + "' not found in pack '" + packRoot + "'");
        }
        return candidate.toString();
    }

    /**
     * Navigates {@code obj} following dot-notation {@code dotPath} and returns the leaf value,
     * or {@code null} if any segment along the path is absent.
     */
    Object getValueAtPath(JSONObject obj, String dotPath) {
        if (obj == null || dotPath == null || dotPath.isEmpty()) return null;

        String[] segments = dotPath.split("\\.", -1);
        JSONObject current = obj;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.opt(segments[i]);
            if (!(next instanceof JSONObject)) return null;
            current = (JSONObject) next;
        }
        String leaf = segments[segments.length - 1];
        return current.opt(leaf);
    }

    /**
     * Navigates {@code obj} following dot-notation {@code dotPath}, creating intermediate
     * {@link JSONObject}s as needed, and sets the leaf to {@code value}.
     */
    void setValueAtPath(JSONObject obj, String dotPath, Object value) {
        if (obj == null || dotPath == null || dotPath.isEmpty()) return;

        String[] segments = dotPath.split("\\.", -1);
        JSONObject current = obj;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.opt(segments[i]);
            if (next instanceof JSONObject) {
                current = (JSONObject) next;
            } else {
                JSONObject newNode = new JSONObject();
                current.put(segments[i], newNode);
                current = newNode;
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    /**
     * At {@code dotPath} in {@code result}, forms the merged array as
     * {@code parentItems + childItems} (parent items come first).
     *
     * <p>If the parent has no array at that path the child array is kept as-is.
     * If the child has no array at that path the parent array is used as-is.
     * If neither has the array the path is left unchanged.</p>
     */
    void prependArrayAtPath(JSONObject result, JSONObject parentConfig, String dotPath, JSONObject originalChild) {
        Object parentValue = getValueAtPath(parentConfig, dotPath);
        Object childValue  = getValueAtPath(originalChild,  dotPath);

        JSONArray parentArr = (parentValue instanceof JSONArray) ? (JSONArray) parentValue : null;
        JSONArray childArr  = (childValue  instanceof JSONArray) ? (JSONArray) childValue  : null;

        if (parentArr == null && childArr == null) return;

        JSONArray combined = new JSONArray();
        if (parentArr != null) {
            for (int i = 0; i < parentArr.length(); i++) combined.put(parentArr.get(i));
        }
        if (childArr != null) {
            for (int i = 0; i < childArr.length(); i++) combined.put(childArr.get(i));
        }
        setValueAtPath(result, dotPath, combined);
    }

    /**
     * Merges structured {@code cliPrompts} from parent and child configs using section ids.
     * If either side has no {@code cliPrompts}, the other's value (or none) is kept.
     */
    private void applyStructuredCliPromptsMerge(JSONObject merged, JSONObject parentConfig, JSONObject originalChild) {
        Object parentValue = getValueAtPath(parentConfig, CLI_PROMPTS_PATH);
        Object childValue  = getValueAtPath(originalChild, CLI_PROMPTS_PATH);

        if (!containsStructuredCliPrompts(parentValue) && !containsStructuredCliPrompts(childValue)) {
            return;
        }

        CliPromptsConfig parentPrompts = parentValue instanceof JSONArray
                ? CliPromptsConfig.fromJsonArray((JSONArray) parentValue)
                : new CliPromptsConfig();
        CliPromptsConfig childPrompts = childValue instanceof JSONArray
                ? CliPromptsConfig.fromJsonArray((JSONArray) childValue)
                : new CliPromptsConfig();

        CliPromptsConfig mergedPrompts = parentPrompts.merge(childPrompts);
        setValueAtPath(merged, CLI_PROMPTS_PATH, CliPromptsConfig.toJsonArray(mergedPrompts));
        logger.debug("Structured cliPrompts merge applied at '{}'", CLI_PROMPTS_PATH);
    }

    private boolean containsStructuredCliPrompts(Object value) {
        if (!(value instanceof JSONArray)) {
            return false;
        }
        JSONArray prompts = (JSONArray) value;
        for (int i = 0; i < prompts.length(); i++) {
            if (prompts.get(i) instanceof JSONObject) {
                return true;
            }
        }
        return false;
    }
}
