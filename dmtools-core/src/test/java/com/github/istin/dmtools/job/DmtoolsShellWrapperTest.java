// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the {@code dmtools.sh} wrapper contract for epam/dm.ai#670: {@code dmtools --help} (and
 * {@code -h} / {@code help}) must print the usage text. JobRunner started with NO arguments on a
 * TTY opens the interactive command picker, so the wrapper has to pass an explicit {@code --help}.
 */
class DmtoolsShellWrapperTest {

    private static String wrapper() throws IOException {
        Path script = Paths.get("").toAbsolutePath().getParent().resolve("dmtools.sh");
        assertTrue(Files.exists(script), "dmtools.sh not found at " + script);
        return new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
    }

    private static String usageFunction(String source) {
        Matcher m = Pattern.compile("(?s)\\nusage\\(\\) \\{.*?\\n\\}\\n").matcher(source);
        assertTrue(m.find(), "usage() function not found in dmtools.sh");
        return m.group();
    }

    @Test
    void usageInvokesJobRunnerWithAnExplicitHelpFlag() throws IOException {
        String usage = usageFunction(wrapper());
        assertTrue(usage.contains("com.github.istin.dmtools.job.JobRunner --help"),
                "usage() must run JobRunner with --help (no args + TTY = interactive picker, #670):\n" + usage);
    }

    @Test
    void helpCommandsRouteToUsage() throws IOException {
        String source = wrapper();
        assertTrue(Pattern.compile("\"help\"\\|\"-h\"\\|\"--help\"\\)\\s*\\n\\s*usage").matcher(source).find(),
                "help / -h / --help must all call usage()");
    }

    @Test
    void noArgumentInteractiveModeIsKept() throws IOException {
        // backward compatibility: plain `dmtools` on a TTY still opens the picker, non-TTY prints usage
        String source = wrapper();
        assertTrue(source.contains("if [ -t 0 ] && [ -t 1 ]; then"), "TTY check for the no-argument mode must stay");
        assertTrue(source.contains("JobRunner interactive"), "interactive picker launch must stay");
    }
}
