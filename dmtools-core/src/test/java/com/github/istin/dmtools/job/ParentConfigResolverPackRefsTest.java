// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.pack.AgentPackException;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Child {@code pack:} references against a pack parent — Java port of the
 * 'child pack: refs against a pack parent' group in dmtools-dart
 * {@code test/cli/run_command_processor_test.dart}.
 *
 * <p>When a config's {@code parent.path} resolves as an agent pack, the child
 * config may reference files inside that pack with the scheme prefix
 * {@code pack:} (e.g. {@code "pack:instructions/review_rules.md"}). The refs
 * rewrite to absolute paths inside the unpacked pack cache; escapes and missing
 * files fail with {@link AgentPackException}; a {@code pack:} ref with a
 * non-pack filesystem parent fails with {@link IllegalArgumentException}.
 */
class ParentConfigResolverPackRefsTest {

    private static final String AGENT = "parent_agent";
    private static final String VERSION = "1.0.0";

    @TempDir
    Path tempDir;

    /**
     * Runs the child config through the run command exactly like the Dart tests
     * do; the parent resolver gets a pack resolver cached under {@code packsRoot}.
     */
    private RunCommandProcessor processor(Path packsRoot) {
        ParentConfigResolver parentResolver = new ParentConfigResolver();
        parentResolver.setPackResolver(new AgentPackResolver(packsRoot));
        return new RunCommandProcessor(new EncodingDetector(), new ConfigurationMerger(), parentResolver);
    }

    /** Where the resolved pack lands: {@code <packsRoot>/<agent>-<version>}. */
    private Path expectedPackRoot(Path packsRoot) {
        return packsRoot.resolve(AGENT + "-" + VERSION).normalize();
    }

    @Test
    void packRefsInListsAndMapsRewriteIntoPackCache() throws Exception {
        File zip = buildPackWithInstructions();
        Path packsRoot = Files.createDirectories(tempDir.resolve("packs"));

        Path child = tempDir.resolve("child.json");
        Files.writeString(child, """
                {
                  "name":"child",
                  "parent":{"path":"%s"},
                  "params":{
                    "cliPrompts":["pack:instructions/review_rules.md","literal prompt"],
                    "customParams":{"rulesFile":"pack:/instructions/review_rules.md"}
                  }
                }
                """.formatted(zip.getAbsolutePath().replace("\\", "\\\\")));

        JobParams jobParams = processor(packsRoot)
                .processRunCommand(new String[]{"run", child.toString()});

        JSONArray prompts = jobParams.getParams().getJSONArray("cliPrompts");
        assertEquals("literal prompt", prompts.getString(1));
        String rewritten = prompts.getString(0);
        assertTrue(rewritten.startsWith(packsRoot.toAbsolutePath().normalize().toString()),
                "should point into the pack cache: " + rewritten);
        assertTrue(Files.exists(Path.of(rewritten)), "rewritten path should exist: " + rewritten);
        assertEquals(rewritten, jobParams.getParams().getJSONObject("customParams").getString("rulesFile"),
                "pack:/ with a slash normalizes too");
    }

    @Test
    void packRefEscapingThePackRootIsRejected() throws Exception {
        File zip = buildPackWithInstructions();
        Path packsRoot = Files.createDirectories(tempDir.resolve("packs"));

        Path child = tempDir.resolve("child.json");
        Files.writeString(child, """
                {
                  "name":"child",
                  "parent":{"path":"%s"},
                  "params":{"cliPrompts":["pack:../outside.md"]}
                }
                """.formatted(zip.getAbsolutePath().replace("\\", "\\\\")));

        AgentPackException e = assertThrows(AgentPackException.class,
                () -> processor(packsRoot).processRunCommand(new String[]{"run", child.toString()}));
        assertEquals("pack: reference 'pack:../outside.md' not found in pack '"
                + expectedPackRoot(packsRoot) + "'", e.getMessage());
        assertFalse(Files.exists(tempDir.resolve("outside.md")), "outside file must not be created");
    }

    @Test
    void packRefMissingInsideThePackThrowsAgentPackException() throws Exception {
        File zip = buildPackWithInstructions();
        Path packsRoot = Files.createDirectories(tempDir.resolve("packs"));

        Path child = tempDir.resolve("child.json");
        Files.writeString(child, """
                {
                  "name":"child",
                  "parent":{"path":"%s"},
                  "params":{"cliPrompts":["pack:instructions/nope.md"]}
                }
                """.formatted(zip.getAbsolutePath().replace("\\", "\\\\")));

        AgentPackException e = assertThrows(AgentPackException.class,
                () -> processor(packsRoot).processRunCommand(new String[]{"run", child.toString()}));
        assertEquals("pack: reference 'pack:instructions/nope.md' not found in pack '"
                + expectedPackRoot(packsRoot) + "'", e.getMessage());
    }

    @Test
    void packRefsWithANonPackFilesystemParentAreRejected() throws Exception {
        Files.writeString(tempDir.resolve("parent.json"), "{\"params\":{\"k\":\"p\"}}");

        Path child = tempDir.resolve("child.json");
        Files.writeString(child, """
                {
                  "name":"child",
                  "parent":{"path":"parent.json"},
                  "params":{"cliPrompts":["pack:instructions/review_rules.md"]}
                }
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> processor(tempDir.resolve("packs"))
                        .processRunCommand(new String[]{"run", child.toString()}));
        assertTrue(e.getMessage().contains(
                        "child config uses pack: references but its parent is not an agent pack"),
                e.getMessage());
    }

    /**
     * Builds a real pack zip containing {@code instructions/review_rules.md} plus the
     * standard parent-pack fixture content. The entry references the instructions file
     * (via the {@code agents/} path duality) so the compiler's closure embeds it in the zip.
     */
    private File buildPackWithInstructions() throws IOException {
        Path agentRoot = Files.createDirectories(tempDir.resolve("parent_agent_root"));
        Files.createDirectories(agentRoot.resolve("js"));
        Files.writeString(agentRoot.resolve("js/helper.js"), "// helper\n");
        Files.createDirectories(agentRoot.resolve("instructions"));
        Files.writeString(agentRoot.resolve("instructions/review_rules.md"), "# review rules\n");
        Path entry = agentRoot.resolve(AGENT + ".json");
        Files.writeString(entry, """
                {
                  "name":"ParentAgent",
                  "params":{
                    "jsPath":"agents/js/helper.js",
                    "fromParent":"yes",
                    "cliPrompts":["agents/instructions/review_rules.md"]
                  }
                }
                """);
        Path dist = Files.createDirectories(tempDir.resolve("parent_dist"));
        return new AgentPackCompiler(agentRoot.toFile())
                .compile(entry.toFile(), VERSION, "deadbeef", dist.toFile())
                .zipFile;
    }
}
