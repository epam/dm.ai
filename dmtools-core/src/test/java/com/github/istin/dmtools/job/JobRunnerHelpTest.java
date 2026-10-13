// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.mcp.cli.OutputFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards epam/dm.ai#685: --help documents the output formats and stays a superset of the old text. */
class JobRunnerHelpTest {

    private static String help() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer));
            JobRunner.printHelp();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    @Test
    void helpListsEveryOutputFormatTheCodeSupports() {
        String help = help();
        for (OutputFormat format : OutputFormat.values()) {
            assertTrue(help.contains(format.getId()), "--help does not mention the '" + format.getId() + "' output format");
        }
    }

    @Test
    void helpDocumentsTheFlagsAndTheDefaultVariable() {
        String help = help();
        assertTrue(help.contains("--output <format>"));
        assertTrue(help.contains("--toon"));
        assertTrue(help.contains("--mini"));
        assertTrue(help.contains("CLI_OUTPUT"));
    }

    @Test
    void helpKeepsEverySectionItHadBefore() {
        String help = help();
        for (String expected : new String[]{"DMTools CLI Wrapper", "Usage:", "dmtools list", "dmtools doctor", "dmtools run <json-file>",
                "Examples:", "Environment Variables:", "DMTOOLS_INTEGRATIONS", "Environment Files:", "Installation:"}) {
            assertTrue(help.contains(expected), "help lost: " + expected);
        }
    }

    @Test
    void documentedFlagsMatchWhatTheFormatResolverAccepts() {
        assertEquals(OutputFormat.TOON, OutputFormat.fromString("toon"));
        assertEquals(OutputFormat.MINI, OutputFormat.fromString("mini"));
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("json"));
    }

    @Test
    void helpMentionsTheUpgradeCommand() {
        assertTrue(help().contains("dmtools upgrade"));
    }

    // ---- install link in --help (colleague report: a stale link led to the abandoned IstiN/dmtools repo) ----

    @Test
    void helpPointsToTheLiveInstallerAndNeverToTheOldRepository() {
        String help = help();
        assertTrue(help.contains("https://github.com/epam/dm.ai/releases/latest/download/install.sh"),
                "help must point at the live installer");
        assertFalse(help.contains("IstiN/dmtools"), "IstiN/dmtools is the old repository and its install link is a 404");
        assertFalse(help.contains("raw.githubusercontent.com/IstiN"), "no raw links into the old repository");
    }

    @Test
    void everyUrlInHelpPointsAtTheCurrentRepositoryOrAnExternalSite() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(help());
        int seen = 0;
        while (m.find()) {
            seen++;
            String url = m.group();
            if (url.contains("github.com") || url.contains("githubusercontent.com")) {
                assertTrue(url.contains("epam/dm.ai"), "GitHub link outside epam/dm.ai: " + url);
            }
        }
        assertTrue(seen >= 1, "expected at least the install link");
    }
}

