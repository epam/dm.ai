// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** epam/dm.ai#701: an install with an OLD wrapper forwards 'upgrade' to the JAR, which must run the installer. */
class UpgradeCommandTest {

    @TempDir
    Path installDir;

    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    private final List<String> downloaded = new ArrayList<>();
    private final List<List<String>> commands = new ArrayList<>();
    private final List<Map<String, String>> overrides = new ArrayList<>();

    private UpgradeCommand command(Map<String, String> env, int exit, boolean downloadFails) {
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        return new UpgradeCommand(env, err,
                (url, target) -> {
                    downloaded.add(url);
                    if (downloadFails) throw new IOException("HTTP 404");
                    Files.writeString(target, "#!/bin/bash\n");
                },
                (cmd, ov) -> {
                    commands.add(cmd);
                    overrides.add(ov);
                    return exit;
                },
                installDir);
    }

    private String err() {
        return errBuffer.toString(StandardCharsets.UTF_8);
    }

    private void writeInstalledVersion(String v) throws IOException {
        Files.writeString(installDir.resolve("installed-skills.json"), "{\n  \"version\": \"" + v + "\"\n}\n");
    }

    @Test
    void handlesExactlyUpgradeAndUpdate() {
        assertTrue(UpgradeCommand.handles("upgrade"));
        assertTrue(UpgradeCommand.handles("update"));
        assertFalse(UpgradeCommand.handles("upgrades"));
        assertFalse(UpgradeCommand.handles("jira_get_ticket"));
        assertFalse(UpgradeCommand.handles(null));
    }

    @Test
    void latestDownloadsTheLatestInstallerAndRunsItWithBash() throws Exception {
        writeInstalledVersion("v1.7.269");
        int rc = command(new HashMap<>(), 0, false).run(new String[]{"upgrade"});

        assertEquals(0, rc);
        assertEquals(List.of(UpgradeCommand.LATEST_URL), downloaded);
        assertEquals("bash", commands.get(0).get(0));
        assertTrue(overrides.get(0).isEmpty(), "latest: no version pinned");
        assertTrue(err().contains("installed version v1.7.269"), err());
    }

    @Test
    void aSpecificVersionPinsThatReleasesInstallerAndPassesDmtoolsVersion() throws Exception {
        int rc = command(new HashMap<>(), 0, false).run(new String[]{"update", "1.7.274"});

        assertEquals(0, rc);
        assertEquals(List.of("https://github.com/epam/dm.ai/releases/download/v1.7.274/install.sh"), downloaded);
        assertEquals("v1.7.274", overrides.get(0).get("DMTOOLS_VERSION"));
    }

    @Test
    void aLeadingVIsAccepted() {
        command(new HashMap<>(), 0, false).run(new String[]{"upgrade", "v1.7.275"});
        assertEquals("https://github.com/epam/dm.ai/releases/download/v1.7.275/install.sh", downloaded.get(0));
        assertEquals("v1.7.275", overrides.get(0).get("DMTOOLS_VERSION"));
    }

    @Test
    void aMalformedVersionIsRejectedBeforeAnythingIsDownloadedOrRun() {
        for (String bad : new String[]{"latest", "1.7", "1.7.x", "../x", "1.7.275; rm -rf /"}) {
            downloaded.clear();
            commands.clear();
            int rc = command(new HashMap<>(), 0, false).run(new String[]{"upgrade", bad});
            assertEquals(2, rc, bad);
            assertTrue(downloaded.isEmpty() && commands.isEmpty(), "nothing may run for '" + bad + "'");
        }
        assertTrue(err().contains("not a valid version"));
    }

    @Test
    void dryRunChangesNothing() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("DMTOOLS_UPGRADE_DRY_RUN", "true");
        int rc = command(env, 0, false).run(new String[]{"upgrade"});

        assertEquals(0, rc);
        assertTrue(downloaded.isEmpty());
        assertTrue(commands.isEmpty());
        assertTrue(err().contains("dry run"), err());
    }

    @Test
    void aFailedDownloadReportsTheWorkaroundAndNeverRunsAnything() {
        int rc = command(new HashMap<>(), 0, true).run(new String[]{"upgrade"});

        assertEquals(1, rc);
        assertTrue(commands.isEmpty(), "no installer was downloaded, so none may run");
        assertTrue(err().contains("could not download the installer"), err());
        assertTrue(err().contains("curl -fsSL " + UpgradeCommand.LATEST_URL), err());
    }

    @Test
    void aFailingInstallerPropagatesItsExitCodeAndSaysTheOldInstallIsKept() {
        int rc = command(new HashMap<>(), 7, false).run(new String[]{"upgrade"});

        assertEquals(7, rc);
        assertTrue(err().contains("the previous installation was left as it was"), err());
    }

    @Test
    void successReportsOldAndNewVersionWhenTheInstallerUpdatedTheRecord() throws Exception {
        writeInstalledVersion("v1.7.269");
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        UpgradeCommand c = new UpgradeCommand(new HashMap<>(), err,
                (url, target) -> Files.writeString(target, "#!/bin/bash\n"),
                (cmd, ov) -> {
                    writeInstalledVersion("v1.7.276");
                    return 0;
                },
                installDir);

        assertEquals(0, c.run(new String[]{"upgrade"}));
        assertTrue(err().contains("v1.7.269 -> v1.7.276"), err());
    }

    @Test
    void installerUrlOverrideWinsAndUnknownVersionIsReported() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("DMTOOLS_INSTALL_SCRIPT_URL", "file:///tmp/mirror/install.sh");
        command(env, 0, false).run(new String[]{"upgrade", "1.7.274"});
        assertEquals(List.of("file:///tmp/mirror/install.sh"), downloaded);
        assertTrue(err().contains("installed version unknown"), err());
    }

    @Test
    void theDownloadedInstallerIsDeletedAfterwards() throws Exception {
        List<Path> scripts = new ArrayList<>();
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        UpgradeCommand c = new UpgradeCommand(new HashMap<>(), err,
                (url, target) -> {
                    scripts.add(target);
                    Files.writeString(target, "x");
                },
                (cmd, ov) -> 0,
                installDir);
        c.run(new String[]{"upgrade"});
        assertEquals(1, scripts.size());
        assertFalse(Files.exists(scripts.get(0)), "temp installer must be removed");
    }

    @Test
    void anOldWrapperSendsMcpUpgradeAndTheJobRunnerRoutesItHere() throws Exception {
        // the pre-v1.7.275 wrapper runs "JobRunner mcp upgrade": the routing must key on args[1]
        String src = Files.readString(java.nio.file.Paths.get("").toAbsolutePath()
                .resolve("src/main/java/com/github/istin/dmtools/job/JobRunner.java"));
        assertTrue(src.contains("\"mcp\".equals(firstArg) && args.length > 1 && UpgradeCommand.handles(args[1])"),
                "JobRunner must route 'mcp upgrade|update' to UpgradeCommand");
        assertTrue(src.indexOf("UpgradeCommand.handles(args[1])") < src.indexOf("McpCliHandler mcpHandler = new McpCliHandler();"),
                "the upgrade routing must come BEFORE the generic MCP handling");
        // the command it forwards drops the leading 'mcp'
        assertTrue(src.contains("java.util.Arrays.copyOfRange(args, 1, args.length)"));
        assertTrue(src.contains("UpgradeCommand(System.getenv(), System.out,"),
                "the old wrapper discards stderr, so the progress must be printed to stdout on this path");
    }
}
