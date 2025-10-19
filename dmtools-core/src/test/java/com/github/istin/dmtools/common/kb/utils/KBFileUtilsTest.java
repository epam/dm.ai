package com.github.istin.dmtools.common.kb.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KBFileUtilsTest {

    private KBFileUtils fileUtils;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileUtils = new KBFileUtils();
    }

    @Test
    void testCountFilesWithFilter() throws IOException {
        Path file1 = Files.writeString(tempDir.resolve("a.md"), "a");
        Path file2 = Files.writeString(tempDir.resolve("b.txt"), "b");
        Path file3 = Files.writeString(tempDir.resolve("c.md"), "c");

        int count = fileUtils.countFiles(tempDir, p -> p.getFileName().toString().endsWith(".md"));
        assertEquals(2, count);
    }

    @Test
    void testCountFilesDirectoryMissing() throws IOException {
        Path missing = tempDir.resolve("missing");
        assertEquals(0, fileUtils.countFiles(missing, null));
    }

    @Test
    void testCountDirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("dir1"));
        Files.createDirectories(tempDir.resolve("dir2"));
        Files.createDirectories(tempDir.resolve("dir3"));
        Files.writeString(tempDir.resolve("file.txt"), "file");

        assertEquals(3, fileUtils.countDirectories(tempDir));
    }

    @Test
    void testCountDirectoriesMissing() throws IOException {
        Path missing = tempDir.resolve("missing");
        assertEquals(0, fileUtils.countDirectories(missing));
    }
}
