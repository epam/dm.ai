// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

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

    private final ConfigurationMerger configurationMerger;

    public ParentConfigResolver() {
        this.configurationMerger = new ConfigurationMerger();
    }

    ParentConfigResolver(ConfigurationMerger configurationMerger) {
        this.configurationMerger = configurationMerger;
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

        // Resolve parent path relative to child file's directory
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

        // Parse and recursively resolve the parent's own inheritance
        JSONObject parentConfig = new JSONObject(parentJson);
        parentConfig = resolve(parentConfig, parentPath);

        // Capture original child values BEFORE merge (needed for override/merge processing)
        JSONObject originalChild = new JSONObject(childConfig.toString());
        originalChild.remove(PARENT);

        // Read override and merge path lists
        JSONArray overridePaths = parentBlock.optJSONArray(PARENT_OVERRIDE);
        JSONArray mergePaths    = parentBlock.optJSONArray(PARENT_MERGE);

        // Base merge: parent ← child (child wins for scalars and arrays by default)
        JSONObject merged = configurationMerger.deepMerge(parentConfig, originalChild);

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
}
