package com.github.istin.dmtools.teammate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: verifies InstructionProcessor can fetch content from public GitHub URLs.
 * Uses the real BasicGithub client (SOURCE_GITHUB_TOKEN env var is optional for public repos).
 */
class InstructionProcessorGithubIntegrationTest {

    private static final String BLOB_URL =
            "https://github.com/IstiN/dmtools/blob/main/CLAUDE.md";

    private static final String RAW_URL =
            "https://raw.githubusercontent.com/IstiN/dmtools/main/CLAUDE.md";

    @TempDir
    Path tempDir;

    @Test
    void testFetchPublicGithubBlobUrl() throws IOException {
        InstructionProcessor processor = new InstructionProcessor(null, tempDir.toString());

        String[] result = processor.extractIfNeeded(BLOB_URL);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNotEquals(BLOB_URL, result[0],
                "Content should be fetched, not the original URL returned as fallback");
        assertTrue(result[0].length() > 100,
                "Fetched content should be non-trivial (CLAUDE.md is a large file)");

        System.out.println("=== Blob URL: fetched content (first 300 chars) ===");
        System.out.println(result[0].substring(0, Math.min(300, result[0].length())));
        System.out.println("Total length: " + result[0].length() + " chars");
    }

    @Test
    void testFetchPublicGithubRawUrl() throws IOException {
        InstructionProcessor processor = new InstructionProcessor(null, tempDir.toString());

        String[] result = processor.extractIfNeeded(RAW_URL);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNotEquals(RAW_URL, result[0],
                "Content should be fetched, not the original URL returned as fallback");
        assertTrue(result[0].length() > 100,
                "Fetched content should be non-trivial (CLAUDE.md is a large file)");

        System.out.println("=== Raw URL: fetched content (first 300 chars) ===");
        System.out.println(result[0].substring(0, Math.min(300, result[0].length())));
        System.out.println("Total length: " + result[0].length() + " chars");
    }

    @Test
    void testBlobAndRawUrlReturnSameContent() throws IOException {
        InstructionProcessor processor = new InstructionProcessor(null, tempDir.toString());

        String[] blobResult = processor.extractIfNeeded(BLOB_URL);
        String[] rawResult = processor.extractIfNeeded(RAW_URL);

        assertEquals(blobResult[0], rawResult[0],
                "Blob URL and raw URL should return identical file content");
    }
}
