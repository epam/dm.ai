// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.job.Job;
import com.github.istin.dmtools.job.JobRunner;
import com.github.istin.dmtools.mcp.cli.interactive.CommandItem.Kind;
import com.github.istin.dmtools.mcp.generated.MCPSchemaGenerator;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads selectable commands from MCP tools, built-in jobs, JavaScript files,
 * and JSON config files.
 *
 * <p>The loader intentionally avoids creating integration clients so that
 * the interactive picker starts quickly.  Tool metadata is read from the
 * generated schema; runnable files are discovered by scanning the working tree.</p>
 *
 * <p>{@code EXCLUDED_DIR_NAMES} lists directories that are skipped while
 * scanning for runnable {@code .js} and {@code .json} files.  It is intentionally
 * conservative: build outputs, dependency folders, cache directories and VCS
 * metadata are ignored to keep the picker fast and avoid surfacing generated
 * or non-runnable files.</p>
 */
public class CommandSource {

    private static final Set<String> EXCLUDED_DIR_NAMES = new HashSet<>(Arrays.asList(
            ".git", ".github", ".gradle", ".idea", ".pytest_cache", ".worktrees",
            ".claude", ".cursor", ".dmtools", ".agents", "node_modules", "__pycache__",
            "build", "temp", "reports", "bin", "out", "target", "dist"
    ));

    private final Set<String> integrations;

    public CommandSource() {
        this(null);
    }

    public CommandSource(Set<String> integrations) {
        this.integrations = integrations == null || integrations.isEmpty()
                ? MCPToolRegistry.getAvailableIntegrations()
                : new HashSet<>(integrations);
    }

    /**
     * Loads and sorts all selectable commands.
     */
    public List<CommandItem> loadCommands() {
        List<CommandItem> commands = new ArrayList<>();
        commands.addAll(loadMcpTools());
        commands.addAll(loadJobs());
        commands.addAll(loadJsFiles());
        commands.addAll(loadJsonConfigs());
        commands.sort(Comparator
                .comparing((CommandItem c) -> c.getKind().name())
                .thenComparing(CommandItem::getIntegration)
                .thenComparing(CommandItem::getName));
        return commands;
    }

    /**
     * Loads MCP tools from the generated schema.
     */
    public List<CommandItem> loadMcpTools() {
        try {
            Map<String, Object> response = MCPSchemaGenerator.generateToolsListResponse(integrations);
            Object toolsObj = response.get("tools");
            if (toolsObj == null) {
                return Collections.emptyList();
            }

            List<CommandItem> items = new ArrayList<>();
            if (toolsObj instanceof List) {
                for (Object o : (List<?>) toolsObj) {
                    if (o instanceof Map) {
                        items.add(toCommandItem((Map<?, ?>) o));
                    }
                }
            } else if (toolsObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) toolsObj;
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof JSONObject) {
                        items.add(toCommandItem(((JSONObject) o).toMap()));
                    } else if (o instanceof Map) {
                        items.add(toCommandItem((Map<?, ?>) o));
                    }
                }
            }
            return items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private CommandItem toCommandItem(Map<?, ?> tool) {
        String name = stringValue(tool.get("name"));
        String integration = stringValue(tool.get("integration"));
        String description = stringValue(tool.get("description"));
        if (integration.isEmpty()) {
            integration = "-";
        }
        return new CommandItem(Kind.MCP, integration, name, description);
    }

    /**
     * Loads built-in job names from {@link JobRunner#getJobs()}.
     */
    public List<CommandItem> loadJobs() {
        List<CommandItem> items = new ArrayList<>();
        try {
            for (Job job : JobRunner.getJobs()) {
                items.add(new CommandItem(Kind.JOB, "job", job.getName(), "Built-in job"));
            }
        } catch (Exception e) {
            // Fallback to static list if JobRunner listing fails for any reason
            items.addAll(loadFallbackJobs());
        }
        return items;
    }

    private List<CommandItem> loadFallbackJobs() {
        List<String> names = Arrays.asList(
                "PreSaleSupport",
                "DocumentationGenerator",
                "RequirementsCollector",
                "TestCasesGenerator",
                "InstructionsGenerator",
                "SolutionArchitectureCreator",
                "DiagramsCreator",
                "CodeGeneratorCompatibilityJob",
                "DevProductivityReport",
                "BAProductivityReport",
                "BusinessAnalyticDORGeneration",
                "QAProductivityReport",
                "ReportGeneratorJob",
                "ReportVisualizerJob",
                "Expert",
                "Teammate",
                "SourceCodeTrackerSyncJob",
                "SourceCodeCommitTrackerSyncJob",
                "UserStoryGenerator",
                "UnitTestsGenerator",
                "JSRunner",
                "KBProcessingJob"
        );
        List<CommandItem> items = new ArrayList<>();
        for (String name : names) {
            items.add(new CommandItem(Kind.JOB, "job", name, "Built-in job"));
        }
        return items;
    }

    /**
     * Discovers runnable JavaScript files in the working tree.
     */
    public List<CommandItem> loadJsFiles() {
        List<Path> roots = Arrays.asList(
                Path.of("."),
                Path.of("agents"),
                Path.of("scripts"),
                Path.of("jobs")
        );
        return scanFiles(roots, ".js", 2, this::isRunnableJs);
    }

    /**
     * Discovers runnable JSON config files in the working tree.
     */
    public List<CommandItem> loadJsonConfigs() {
        List<Path> roots = Arrays.asList(
                Path.of("."),
                Path.of("agents"),
                Path.of("scripts"),
                Path.of("jobs"),
                Path.of("input")
        );
        return scanFiles(roots, ".json", 2, this::isRunnableJson);
    }

    private List<CommandItem> scanFiles(List<Path> roots, String extension, int maxDepth, java.util.function.Predicate<String> filter) {
        List<CommandItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Kind kind = extension.equals(".js") ? Kind.JS : Kind.JSON;
        String label = extension.equals(".js") ? "JavaScript file" : "JSON config";

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                Files.walkFileTree(root, Collections.emptySet(), maxDepth, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (isExcludedDir(dir.getFileName().toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String path = file.toString().replace(File.separator, "/");
                        if (!path.endsWith(extension) || !filter.test(path)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!seen.add(path)) {
                            return FileVisitResult.CONTINUE;
                        }
                        items.add(new CommandItem(kind, kind.getId(), path, label));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // ignore unreadable directories
            }
        }
        return items;
    }

    private boolean isRunnableJs(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("/common/") || lower.contains("/unit-tests/")
                || lower.contains("/integration-tests/") || lower.contains("/branchnaming/")) {
            return false;
        }
        String basename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        return !basename.startsWith("config");
    }

    private boolean isRunnableJson(String path) {
        String lower = path.toLowerCase();
        return !lower.contains("/unit-tests/") && !lower.contains("/integration-tests/");
    }

    private boolean isExcludedDir(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (EXCLUDED_DIR_NAMES.contains(name)) {
            return true;
        }
        return name.startsWith(".") || name.startsWith("cache");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Returns the default set of integration names used when no explicit set is provided.
     *
     * @deprecated Use {@link MCPToolRegistry#getAvailableIntegrations()} directly. Kept for compatibility.
     */
    @Deprecated
    public static Set<String> defaultIntegrations() {
        return MCPToolRegistry.getAvailableIntegrations();
    }

    private static Set<String> defaultIntegrationsInternal() {
        return defaultIntegrations();
    }
}
