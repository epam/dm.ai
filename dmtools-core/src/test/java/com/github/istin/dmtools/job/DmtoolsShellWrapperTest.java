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

    // ---- dmtools upgrade / update (epam/dm.ai#684) ----

    private static String upgradeBlock(String source) {
        int start = source.indexOf("read_installed_dmtools_version()");
        int end = source.indexOf("# Get Java command or error");
        assertTrue(start > 0 && end > start, "upgrade block not found in dmtools.sh");
        return source.substring(start, end);
    }

    @Test
    void upgradeRunsBeforeTheJavaCheckSoItWorksOnABrokenInstall() throws IOException {
        String source = wrapper();
        int upgradeCase = source.indexOf("upgrade|update)");
        int javaCheck = source.indexOf("JAVA_CMD=$(find_java_command");
        assertTrue(upgradeCase > 0, "upgrade|update subcommand missing");
        assertTrue(upgradeCase < javaCheck, "upgrade must be handled before the Java/JAR checks");
    }

    @Test
    void upgradeUsesTheReleaseInstallerAndKeepsTheOldInstallOnFailure() throws IOException {
        String block = upgradeBlock(wrapper());
        assertTrue(block.contains("releases/latest/download/install.sh"));
        assertTrue(block.contains("releases/download/v${requested#v}/install.sh"), "a specific version pins that release's installer");
        assertTrue(block.contains("DMTOOLS_VERSION=\"v${requested#v}\""), "the requested version is passed to install.sh");
        assertTrue(block.contains("DMTOOLS_UPGRADE_DRY_RUN"), "dry run switch");
        assertTrue(block.contains("the previous installation was left as it was"), "a failed installer is reported, not swallowed");
        assertTrue(block.contains("rm -f \"$tmp\""), "the downloaded installer is cleaned up");
    }

    @Test
    void upgradeReportsOldAndNewVersion() throws IOException {
        String block = upgradeBlock(wrapper());
        assertTrue(block.contains("installed version ${before}"));
        assertTrue(block.contains("${before} -> $(read_installed_dmtools_version)"));
    }

    @Test
    void existingCommandsAreStillRoutedAfterTheNewCase() throws IOException {
        String source = wrapper();
        // the new case must not swallow anything else: it matches exactly upgrade|update
        assertTrue(source.contains("case \"${1:-}\" in\n    upgrade|update)"));
        assertTrue(source.contains("\"help\"|\"-h\"|\"--help\")"));
        assertTrue(source.contains("\"interactive\"|\"i\")"));
    }

    @Test
    void scriptStaysExecutableWithABashShebang() throws IOException {
        Path script = Paths.get("").toAbsolutePath().getParent().resolve("dmtools.sh");
        assertTrue(Files.isExecutable(script), "dmtools.sh must stay executable");
        assertTrue(wrapper().startsWith("#!/bin/bash"));
    }

    // ---- GraalVM TruffleAttach warning (epam/dm.ai#697) ----

    @Test
    void everyJavaInvocationSilencesTheHarmlessTruffleAttachWarning() throws IOException {
        String source = wrapper();
        long javaCalls = source.lines().filter(l -> l.contains("\"$JAVA_CMD\"") && l.contains("-Dlog4j2.configurationFile")).count();
        long silenced = source.lines().filter(l -> l.contains("\"$JAVA_CMD\"") && l.contains("-Dpolyglotimpl.AttachLibraryFailureAction=ignore")).count();
        assertTrue(javaCalls >= 8, "expected the wrapper's java invocations, saw " + javaCalls);
        assertEquals(javaCalls, silenced, "every java invocation must carry the TruffleAttach flag");
    }

    @Test
    void theTruffleFlagSitsNextToTheInterpreterOnlyFlagItComplements() throws IOException {
        for (String line : wrapper().split("\n")) {
            if (line.contains("-Dpolyglotimpl.AttachLibraryFailureAction=ignore")) {
                assertTrue(line.contains("-Dpolyglot.engine.WarnInterpreterOnly=false"),
                        "the two polyglot warning switches belong together: " + line);
            }
        }
    }

    @Test
    void theFlagIsAnIgnoreNotAThrow() throws IOException {
        // 'throw' would turn the harmless fallback into a crash; only 'ignore' is acceptable
        assertFalse(wrapper().contains("AttachLibraryFailureAction=throw"));
        assertFalse(wrapper().contains("AttachLibraryFailureAction=diagnose"));
    }

    // ---- install.sh: java version detection (colleague report: "Picked up _JAVA_OPTIONS" was read as the version) ----

    private static String installer() throws IOException {
        Path script = Paths.get("").toAbsolutePath().getParent().resolve("install.sh");
        assertTrue(Files.exists(script), "install.sh not found at " + script);
        return new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
    }

    @Test
    void installerReadsTheJavaVersionThroughOneHelperNotTheFirstLineOfJavaVersion() throws IOException {
        String source = installer();
        assertTrue(source.contains("java_version_of() {"), "version helper missing");
        assertFalse(Pattern.compile("-version 2>&1 \\| head -n 1 \\| cut -d'\"' -f2").matcher(source).find(),
                "the first-line parse returns 'Picked up _JAVA_OPTIONS...' when that variable is set; use java_version_of");
        long uses = source.lines().filter(l -> l.contains("$(java_version_of \"$java_cmd\")")).count();
        assertEquals(4, uses, "all four places that read the version must use the helper");
    }

    @Test
    void javaVersionHelperSkipsPickedUpLinesAndHandlesRealOutputShapes() throws Exception {
        String source = installer();
        java.util.regex.Matcher m = Pattern.compile("(?s)\\njava_version_of\\(\\) \\{.*?\\n\\}\\n").matcher(source);
        assertTrue(m.find(), "helper body not found");
        Path dir = Files.createTempDirectory("jvo");
        Path lib = dir.resolve("lib.sh");
        Files.writeString(lib, m.group());
        String[][] cases = {
                {"Picked up _JAVA_OPTIONS: -Duser.language=en\nopenjdk version \"17.0.13\" 2024-10-15\nOpenJDK Runtime", "17.0.13"},
                {"Picked up JAVA_TOOL_OPTIONS: -Dfoo=bar\nPicked up _JAVA_OPTIONS: -Dx=y\nopenjdk version \"21.0.2\" 2024-01-16\n", "21.0.2"},
                {"openjdk version \"17.0.13\" 2024-10-15\nOpenJDK Runtime", "17.0.13"},
                {"java version \"1.8.0_391\"\nJava(TM) SE", "1.8.0_391"},
                {"java version \"11\"\n", "11"},
                {"Picked up _JAVA_OPTIONS: -Dx=y\nno version here\n", ""},
                {"", ""},
        };
        for (String[] c : cases) {
            Path fake = dir.resolve("java");
            StringBuilder sh = new StringBuilder("#!/bin/sh\n");
            for (String line : c[0].split("\n", -1)) {
                if (!line.isEmpty()) sh.append("echo '").append(line).append("' >&2\n");
            }
            Files.writeString(fake, sh.toString());
            fake.toFile().setExecutable(true);
            Process p = new ProcessBuilder("bash", "-c", ". " + lib + "; java_version_of " + fake).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor());
            assertEquals(c[1], out, "java -version output was: " + c[0].replace("\n", " | "));
        }
    }

    @Test
    void installerStillPassesBashSyntaxCheck() throws Exception {
        Process p = new ProcessBuilder("bash", "-n", Paths.get("").toAbsolutePath().getParent().resolve("install.sh").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
    }

    // ---- dmtools.sh & install.sh launcher corruption prevention (epam/dm.ai#746) ----

    @Test
    void wrapperPassesBashSyntaxCheckAndEndsCleanly() throws Exception {
        Path script = Paths.get("").toAbsolutePath().getParent().resolve("dmtools.sh");
        Process p = new ProcessBuilder("bash", "-n", script.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);

        String trimmed = wrapper().stripTrailing();
        assertTrue(trimmed.endsWith("\nfi"), "dmtools.sh must end after a single if/else/fi launch block");
        assertFalse(trimmed.contains("\n.JobRunner"), "dmtools.sh must not contain stray .JobRunner fragments");
    }

    @Test
    void legacyInstallScriptPassesBashSyntaxCheck() throws Exception {
        Path script = Paths.get("").toAbsolutePath().getParent().resolve("install");
        Process p = new ProcessBuilder("bash", "-n", script.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
    }

    @Test
    void installerOverwritesShellScriptsInsteadOfResumingAndValidatesBashSyntax() throws Exception {
        String source = installer();
        assertTrue(source.contains("bash -n \"$file\""), "validate_not_html must validate shell script syntax with bash -n");
        assertTrue(source.contains("bash -n \"$SCRIPT_PATH\""), "installer_managed_script_present must verify bash -n on SCRIPT_PATH");
        assertTrue(source.contains("curl_resume_args=(-C -)"), "curl resume (-C -) must be restricted to JAR downloads");

        // Simulate the exact #746 corruption scenario: an older 19747-byte script is on disk,
        // and a 20188-byte script is downloaded over it via download_file.
        Path dir = Files.createTempDirectory("installer-746");
        Path oldScript = dir.resolve("dmtools");
        Path remoteScript = Paths.get("").toAbsolutePath().getParent().resolve("dmtools.sh");
        String validWrapper = wrapper();
        // Pre-populate target with a shorter script that has a broken tail if appended to
        String shorterOld = validWrapper.substring(0, validWrapper.length() - 441);
        Files.writeString(oldScript, shorterOld);

        Path testHarness = dir.resolve("run_test.sh");
        Path installSh = Paths.get("").toAbsolutePath().getParent().resolve("install.sh");
        Files.writeString(testHarness,
                "#!/bin/bash\n" +
                "set -e\n" +
                "DMTOOLS_INSTALLER_TEST_MODE=true source \"" + installSh + "\"\n" +
                "download_file \"file://" + remoteScript + "\" \"" + oldScript + "\" \"DMTools shell script\" \"true\"\n" +
                "bash -n \"" + oldScript + "\"\n");
        testHarness.toFile().setExecutable(true);
        Process p = new ProcessBuilder("bash", testHarness.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
        assertEquals(validWrapper, Files.readString(oldScript),
                "Downloaded dmtools script must match the remote dmtools.sh byte-for-byte without appended tail (#746)");
    }
}

