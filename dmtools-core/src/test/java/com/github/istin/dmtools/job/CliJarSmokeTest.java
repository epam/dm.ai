// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Smoke test for the packaged CLI shadow JAR.
 * <p>
 * Verifies that the assembled fat JAR is executable and can run the basic
 * {@code dmtools list} command without a {@link ClassNotFoundException} or
 * a corrupted-archive error. The test is intended to catch packaging issues
 * such as a truncated JAR or a missing main-class manifest.
 */
public class CliJarSmokeTest {

    private static final long PROCESS_TIMEOUT_SECONDS = 120;

    @Test
    public void testShadowJarListCommand() throws Exception {
        String jarPath = System.getProperty("dmtools.shadow.jar.path");
        assertNotNull("System property 'dmtools.shadow.jar.path' must be set by the Gradle task", jarPath);

        File shadowJar = new File(jarPath);
        assertTrue("Shadow JAR does not exist: " + shadowJar, shadowJar.exists());
        assertTrue("Shadow JAR is empty: " + shadowJar, shadowJar.length() > 0);

        List<String> command = Arrays.asList(
                "java", "-jar", shadowJar.getAbsolutePath(), "mcp", "list", "file"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("DMTOOLS_INTEGRATIONS", "file");
        pb.redirectErrorStream(true);

        Process process = pb.start();

        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("dmtools mcp list file did not finish within " + PROCESS_TIMEOUT_SECONDS + " seconds", finished);

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            fail("dmtools mcp list file failed with exit code " + exitCode + ". Output:\n" + String.join("\n", outputLines));
        }

        String output = String.join("\n", outputLines).toLowerCase();
        assertFalse("Output indicates corrupted JAR: " + output,
                output.contains("could not find or load main class")
                        || output.contains("zip end header not found")
                        || output.contains("invalid or corrupt jarfile"));
    }
}
