// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.github.istin.dmtools.mcp.cli.OutputFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
