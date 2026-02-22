package com.github.istin.dmtools.teammate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: verifies InstructionProcessor can fetch content from a public GitHub URL.
 * Uses the real BasicGithub client (SOURCE_GITHUB_TOKEN env var is optional for public repos).
 */
class InstructionProcessorGithubIntegrationTest {

    private static final String PUBLIC_GITHUB_URL =
            "https://github.com/IstiN/dmtools/blob/main/CLAUDE.md";

    @TempDir
    Path tempDir;

    @Test
    void testFetchPublicGithubFileContent() throws IOException {
        InstructionProcessor processor = new InstructionProcessor(null, tempDir.toString());

        String[] result = processor.extractIfNeeded(PUBLIC_GITHUB_URL);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNotEquals(PUBLIC_GITHUB_URL, result[0],
                "Content should be fetched, not the original URL returned as fallback");
        assertTrue(result[0].length() > 100,
                "Fetched content should be non-trivial (CLAUDE.md is a large file)");

        System.out.println("=== Fetched content (first 500 chars) ===");
        System.out.println(result[0].substring(0, Math.min(500, result[0].length())));
        System.out.println("...");
        System.out.println("Total length: " + result[0].length() + " chars");
    }
}
