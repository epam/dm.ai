package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreCommitHookTest {

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve(".githooks/pre-commit"))) {
            return cwd;
        }
        if (cwd.getParent() != null && Files.exists(cwd.getParent().resolve(".githooks/pre-commit"))) {
            return cwd.getParent();
        }
        throw new IllegalStateException("Cannot locate .githooks/pre-commit from " + cwd);
    }

    private static void initSandboxWithHook(Path tempDir) throws Exception {
        Path hookSrc = resolveRepoRoot().resolve(".githooks/pre-commit");
        runCmd(tempDir, Map.of(), "git", "init", "-q");
        runCmd(tempDir, Map.of(), "git", "config", "user.email", "test@example.com");
        runCmd(tempDir, Map.of(), "git", "config", "user.name", "Test");
        Path hooksDir = tempDir.resolve(".githooks");
        Files.createDirectories(hooksDir);
        Path hookDest = hooksDir.resolve("pre-commit");
        Files.copy(hookSrc, hookDest);
        hookDest.toFile().setExecutable(true);
        runCmd(tempDir, Map.of(), "git", "config", "core.hooksPath", ".githooks");
    }

    @Test
    void hookFileExistsAndContainsQualityGates() throws Exception {
        Path hook = resolveRepoRoot().resolve(".githooks/pre-commit");
        assertTrue(Files.exists(hook), ".githooks/pre-commit must exist");
        assertTrue(Files.isExecutable(hook), ".githooks/pre-commit must be executable");
        String content = Files.readString(hook, StandardCharsets.UTF_8);
        assertTrue(content.contains("2800"), "Hook must enforce the 2800-line limit");
        assertTrue(content.contains("--min-lines 10 --min-tokens 50 --threshold 2 --format java"),
                "Hook must enforce the jscpd duplication gate");
    }

    @Test
    void hookRejectsStagedJavaFileOverLineLimit(@TempDir Path tempDir) throws Exception {
        initSandboxWithHook(tempDir);
        StringBuilder sb = new StringBuilder("package sample;\npublic class TooLong {\n");
        for (int i = 0; i < 65; i++) {
            sb.append("    public int line").append(i).append("() { return ").append(i).append("; }\n");
        }
        sb.append("}\n");
        Files.writeString(tempDir.resolve("TooLong.java"), sb.toString(), StandardCharsets.UTF_8);
        runCmd(tempDir, Map.of(), "git", "add", "TooLong.java");

        ProcessResult res = runCmdAllowFail(
                tempDir,
                Map.of("DMAI_MAX_JAVA_LINES", "60"),
                "git", "commit", "-m", "should fail"
        );
        assertNotEquals(0, res.exitCode, "Commit with file exceeding line limit must be rejected");
        assertTrue(res.output.contains("FAIL: TooLong.java"), "Output should name the failing file: " + res.output);
    }

    @Test
    void hookAllowsNonJavaCommit(@TempDir Path tempDir) throws Exception {
        initSandboxWithHook(tempDir);
        Files.writeString(tempDir.resolve("README.md"), "# hello\n", StandardCharsets.UTF_8);
        runCmd(tempDir, Map.of(), "git", "add", "README.md");

        ProcessResult res = runCmdAllowFail(tempDir, Map.of(), "git", "commit", "-m", "docs commit");
        assertEquals(0, res.exitCode, "Non-Java commit must pass: " + res.output);
    }

    private static void runCmd(Path dir, Map<String, String> env, String... cmd) throws Exception {
        ProcessResult r = runCmdAllowFail(dir, env, cmd);
        assertEquals(0, r.exitCode, "Command failed (" + String.join(" ", cmd) + "): " + r.output);
    }

    private static ProcessResult runCmdAllowFail(Path dir, Map<String, String> env, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(List.of(cmd));
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        return new ProcessResult(code, out);
    }

    private static final class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
