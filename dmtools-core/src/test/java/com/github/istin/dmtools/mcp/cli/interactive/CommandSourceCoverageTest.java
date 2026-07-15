// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import com.github.istin.dmtools.mcp.cli.interactive.CommandItem.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandSourceCoverageTest {

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = CommandSource.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private static List<CommandItem> scan(CommandSource source, List<Path> roots, String extension, int maxDepth,
                                          java.util.function.Predicate<String> filter) throws Exception {
        return (List<CommandItem>) invoke(source, "scanFiles",
                new Class<?>[]{List.class, String.class, int.class, java.util.function.Predicate.class},
                roots, extension, maxDepth, filter);
    }

    @Test
    void defaultConstructorLoadsToolsFromRegistry() {
        CommandSource source = new CommandSource();
        List<CommandItem> tools = source.loadMcpTools();
        assertFalse(tools.isEmpty(), "registry integrations should yield MCP tools");
        for (CommandItem item : tools) {
            assertEquals(Kind.MCP, item.getKind());
            assertFalse(item.getName().isEmpty());
            assertFalse(item.getIntegration().isEmpty());
        }
    }

    @Test
    void nullAndEmptyIntegrationsFallBackToRegistry() {
        assertFalse(new CommandSource(null).loadMcpTools().isEmpty());
        assertFalse(new CommandSource(Collections.emptySet()).loadMcpTools().isEmpty());
    }

    @Test
    void explicitIntegrationLimitsLoadedTools() {
        CommandSource source = new CommandSource(Set.of("github"));
        List<CommandItem> tools = source.loadMcpTools();
        assertFalse(tools.isEmpty());
        for (CommandItem item : tools) {
            assertEquals(Kind.MCP, item.getKind());
            assertTrue(item.getName().startsWith("github_"), "unexpected tool: " + item.getName());
        }
    }

    @Test
    void unknownIntegrationYieldsNoTools() {
        CommandSource source = new CommandSource(Set.of("no_such_integration_xyz"));
        assertTrue(source.loadMcpTools().isEmpty());
    }

    @Test
    void toolWithoutIntegrationFieldGetsDashIntegration() throws Exception {
        Method m = CommandSource.class.getDeclaredMethod("toCommandItem", Map.class);
        m.setAccessible(true);
        CommandSource source = new CommandSource(Set.of("github"));
        CommandItem noIntegration = (CommandItem) m.invoke(source,
                java.util.Map.of("name", "tool_a", "description", "desc"));
        assertEquals("-", noIntegration.getIntegration());
        assertEquals("tool_a", noIntegration.getName());
        assertEquals("desc", noIntegration.getDescription());
        assertEquals(Kind.MCP, noIntegration.getKind());
        CommandItem withIntegration = (CommandItem) m.invoke(source,
                java.util.Map.of("name", "tool_b", "integration", "jira"));
        assertEquals("jira", withIntegration.getIntegration());
        CommandItem nullFields = (CommandItem) m.invoke(source, java.util.Map.of());
        assertEquals("", nullFields.getName());
        assertEquals("-", nullFields.getIntegration());
        assertEquals("", nullFields.getDescription());
    }

    @Test
    void loadJobsReturnsBuiltInJobs() {
        List<CommandItem> jobs = new CommandSource(Set.of("github")).loadJobs();
        assertFalse(jobs.isEmpty());
        for (CommandItem item : jobs) {
            assertEquals(Kind.JOB, item.getKind());
            assertEquals("job", item.getIntegration());
            assertEquals("Built-in job", item.getDescription());
            assertFalse(item.getName().isEmpty());
        }
    }

    @Test
    void fallbackJobListContainsExpectedJobs() throws Exception {
        @SuppressWarnings("unchecked")
        List<CommandItem> jobs = (List<CommandItem>) invoke(new CommandSource(Set.of("github")),
                "loadFallbackJobs", new Class<?>[0]);
        assertEquals(22, jobs.size());
        assertTrue(jobs.stream().anyMatch(j -> "Expert".equals(j.getName())));
        assertTrue(jobs.stream().anyMatch(j -> "JSRunner".equals(j.getName())));
        for (CommandItem item : jobs) {
            assertEquals(Kind.JOB, item.getKind());
        }
    }

    @Test
    void loadCommandsCombinesAndSortsAllSources() {
        List<CommandItem> commands = new CommandSource(Set.of("github")).loadCommands();
        assertFalse(commands.isEmpty());
        assertTrue(commands.stream().anyMatch(c -> c.getKind() == Kind.MCP));
        assertTrue(commands.stream().anyMatch(c -> c.getKind() == Kind.JOB));
        for (int i = 1; i < commands.size(); i++) {
            CommandItem prev = commands.get(i - 1);
            CommandItem cur = commands.get(i);
            int byKind = prev.getKind().name().compareTo(cur.getKind().name());
            assertTrue(byKind <= 0, "not sorted by kind at index " + i);
            if (byKind == 0) {
                int byIntegration = prev.getIntegration().compareTo(cur.getIntegration());
                assertTrue(byIntegration <= 0, "not sorted by integration at index " + i);
                if (byIntegration == 0) {
                    assertTrue(prev.getName().compareTo(cur.getName()) <= 0,
                            "not sorted by name at index " + i);
                }
            }
        }
    }

    @Test
    void loadJsFilesReturnsOnlyRunnableJavaScript() {
        List<CommandItem> files = new CommandSource(Set.of("github")).loadJsFiles();
        for (CommandItem item : files) {
            assertEquals(Kind.JS, item.getKind());
            assertEquals("js", item.getIntegration());
            assertEquals("JavaScript file", item.getDescription());
            String path = item.getName();
            assertTrue(path.endsWith(".js"), path);
            String lower = path.toLowerCase();
            assertFalse(lower.contains("/common/"), path);
            assertFalse(lower.contains("/unit-tests/"), path);
            assertFalse(lower.contains("/integration-tests/"), path);
            assertFalse(lower.contains("/branchnaming/"), path);
            String basename = lower.substring(lower.lastIndexOf('/') + 1);
            assertFalse(basename.startsWith("config"), path);
        }
    }

    @Test
    void loadJsonConfigsExcludesTestFolders() {
        List<CommandItem> files = new CommandSource(Set.of("github")).loadJsonConfigs();
        for (CommandItem item : files) {
            assertEquals(Kind.JSON, item.getKind());
            assertEquals("json", item.getIntegration());
            assertEquals("JSON config", item.getDescription());
            String path = item.getName();
            assertTrue(path.endsWith(".json"), path);
            String lower = path.toLowerCase();
            assertFalse(lower.contains("/unit-tests/"), path);
            assertFalse(lower.contains("/integration-tests/"), path);
        }
    }

    @Test
    void scanFilesWalksTreeRespectsDepthExclusionsAndDedups(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("run.js"), "// runnable");
        Files.writeString(temp.resolve("config1.js"), "// filtered out by basename");
        Files.writeString(temp.resolve("data.json"), "{}");
        Files.writeString(temp.resolve("notes.txt"), "not a script");
        Path sub = Files.createDirectories(temp.resolve("sub"));
        Files.writeString(sub.resolve("nested.js"), "// depth 2");
        Path build = Files.createDirectories(temp.resolve("build"));
        Files.writeString(build.resolve("ignored.js"), "// excluded dir");
        Path hidden = Files.createDirectories(temp.resolve(".hidden"));
        Files.writeString(hidden.resolve("ignored2.js"), "// hidden dir");
        Path cacheDir = Files.createDirectories(temp.resolve("cacheStuff"));
        Files.writeString(cacheDir.resolve("ignored3.js"), "// cache dir");
        Path deep = Files.createDirectories(temp.resolve("a/b"));
        Files.writeString(deep.resolve("tooDeep.js"), "// beyond maxDepth");

        CommandSource source = new CommandSource(Set.of("github"));
        java.util.function.Predicate<String> filter = path -> {
            String basename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
            return !basename.startsWith("config");
        };
        // pass the same root twice to exercise the seen-set dedup, plus a missing root
        List<CommandItem> items = scan(source,
                Arrays.asList(temp, temp, temp.resolve("does-not-exist")), ".js", 2, filter);

        Set<String> names = new HashSet<>();
        for (CommandItem item : items) {
            assertEquals(Kind.JS, item.getKind());
            assertEquals("js", item.getIntegration());
            assertEquals("JavaScript file", item.getDescription());
            assertTrue(names.add(item.getName()), "duplicate entry: " + item.getName());
        }
        assertTrue(names.stream().anyMatch(n -> n.endsWith("run.js")), names.toString());
        assertTrue(names.stream().anyMatch(n -> n.endsWith("sub/nested.js")), names.toString());
        assertEquals(2, items.size(), "expected exactly run.js and sub/nested.js: " + names);
    }

    @Test
    void scanFilesUsesJsonKindAndLabel(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("job.json"), "{}");
        CommandSource source = new CommandSource(Set.of("github"));
        List<CommandItem> items = scan(source, List.of(temp), ".json", 2, path -> true);
        assertEquals(1, items.size());
        CommandItem item = items.get(0);
        assertEquals(Kind.JSON, item.getKind());
        assertEquals("json", item.getIntegration());
        assertEquals("JSON config", item.getDescription());
    }

    @Test
    void isRunnableJsFiltersHelpersAndConfigs() throws Exception {
        CommandSource source = new CommandSource(Set.of("github"));
        Method m = CommandSource.class.getDeclaredMethod("isRunnableJs", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(source, "agents/common/utils.js"));
        assertFalse((boolean) m.invoke(source, "agents/unit-tests/foo.js"));
        assertFalse((boolean) m.invoke(source, "agents/integration-tests/foo.js"));
        assertFalse((boolean) m.invoke(source, "agents/branchNaming/foo.js"));
        assertFalse((boolean) m.invoke(source, "scripts/config.js"));
        assertFalse((boolean) m.invoke(source, "scripts/CONFIG_PROD.js"));
        assertTrue((boolean) m.invoke(source, "scripts/run_job.js"));
    }

    @Test
    void isRunnableJsonOnlyExcludesTestFolders() throws Exception {
        CommandSource source = new CommandSource(Set.of("github"));
        Method m = CommandSource.class.getDeclaredMethod("isRunnableJson", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(source, "input/unit-tests/foo.json"));
        assertFalse((boolean) m.invoke(source, "input/integration-tests/foo.json"));
        assertTrue((boolean) m.invoke(source, "input/common/config.json"));
    }

    @Test
    void isExcludedDirCoversAllRules() throws Exception {
        CommandSource source = new CommandSource(Set.of("github"));
        Method m = CommandSource.class.getDeclaredMethod("isExcludedDir", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(source, new Object[]{null}));
        assertTrue((boolean) m.invoke(source, ""));
        assertTrue((boolean) m.invoke(source, ".git"));
        assertTrue((boolean) m.invoke(source, "build"));
        assertTrue((boolean) m.invoke(source, "node_modules"));
        assertTrue((boolean) m.invoke(source, ".anything"));
        assertTrue((boolean) m.invoke(source, "cacheBasicJiraClient"));
        assertFalse((boolean) m.invoke(source, "scripts"));
        assertFalse((boolean) m.invoke(source, "agents"));
    }

    @Test
    void stringValueHandlesNullAndObjects() throws Exception {
        Method m = CommandSource.class.getDeclaredMethod("stringValue", Object.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(null, new Object[]{null}));
        assertEquals("42", m.invoke(null, 42));
        assertEquals("text", m.invoke(null, "text"));
    }

    @Test
    void defaultIntegrationsDelegatesToRegistry() {
        Set<String> integrations = CommandSource.defaultIntegrations();
        assertNotNull(integrations);
        assertFalse(integrations.isEmpty());
    }

    @Test
    void defaultIntegrationsInternalDelegates() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> integrations = (Set<String>) invoke(null, "defaultIntegrationsInternal", new Class<?>[0]);
        assertEquals(CommandSource.defaultIntegrations(), integrations);
    }
}
