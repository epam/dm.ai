// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileToTextTransformerTest {

    @TempDir
    Path tempDir;

    @Test
    void testTransformNullFile() throws Exception {
        List<FileToTextTransformer.TransformationResult> result = FileToTextTransformer.transform(null);
        assertNull(result);
    }

    @Test
    void testTransformNonExistentFile() throws Exception {
        File nonExistent = new File(tempDir.toFile(), "nonexistent.txt");
        List<FileToTextTransformer.TransformationResult> result = FileToTextTransformer.transform(nonExistent);
        assertNull(result);
    }

    @Test
    void testTransformTextFile() throws Exception {
        Path textFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.writeString(textFile, content);

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(textFile.toFile());
        assertNotNull(results);
        assertEquals(1, results.size());
        
        FileToTextTransformer.TransformationResult result = results.get(0);
        assertTrue(result.text().contains("test.txt"));
        assertTrue(result.text().contains(content));
        assertNull(result.files());
    }

    @Test
    void testTransformJavaFile() throws Exception {
        Path javaFile = tempDir.resolve("Test.java");
        String code = "public class Test { }";
        Files.writeString(javaFile, code);

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(javaFile.toFile());
        assertNotNull(results);
        assertEquals(1, results.size());
        
        FileToTextTransformer.TransformationResult result = results.get(0);
        assertTrue(result.text().contains("Test.java"));
        assertTrue(result.text().contains(code));
    }

    @Test
    void testTransformBinaryImageFile() throws Exception {
        Path pngFile = tempDir.resolve("image.png");
        Files.write(pngFile, new byte[]{1, 2, 3, 4}); // Dummy binary content

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(pngFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryJpgFile() throws Exception {
        Path jpgFile = tempDir.resolve("photo.jpg");
        Files.write(jpgFile, new byte[]{1, 2, 3, 4});

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(jpgFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryDocFile() throws Exception {
        Path docFile = tempDir.resolve("document.doc");
        Files.write(docFile, new byte[]{1, 2, 3, 4});

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(docFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryXlsFile() throws Exception {
        Path xlsFile = tempDir.resolve("spreadsheet.xls");
        Files.write(xlsFile, new byte[]{1, 2, 3, 4});

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(xlsFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryVideoFile() throws Exception {
        Path mp4File = tempDir.resolve("recording.mp4");
        Files.write(mp4File, new byte[]{1, 2, 3, 4}); // Dummy binary content

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(mp4File.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryVideoMovFile() throws Exception {
        Path movFile = tempDir.resolve("recording.mov");
        Files.write(movFile, new byte[]{1, 2, 3, 4});

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(movFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformBinaryArchiveFile() throws Exception {
        Path zipFile = tempDir.resolve("archive.zip");
        Files.write(zipFile, new byte[]{1, 2, 3, 4});

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(zipFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformLargeTextFileReturnsNull() throws Exception {
        // OOM guard: files larger than FILE_TO_TEXT_MAX_FILE_SIZE_MB (default 4MB)
        // must not be read into memory as text - null means "pass as file reference"
        Path largeFile = tempDir.resolve("large.txt");
        byte[] chunk = new byte[1024 * 1024]; // 1MB
        Arrays.fill(chunk, (byte) 'a');
        try (OutputStream os = Files.newOutputStream(largeFile)) {
            for (int i = 0; i < 5; i++) { // 5MB total > default 4MB limit
                os.write(chunk);
            }
        }

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(largeFile.toFile());
        assertNull(results);
    }

    @Test
    void testTransformSmallTextFileStillReadAsText() throws Exception {
        Path textFile = tempDir.resolve("notes.log");
        String content = "some log output";
        Files.writeString(textFile, content);

        List<FileToTextTransformer.TransformationResult> results = FileToTextTransformer.transform(textFile.toFile());
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).text().contains(content));
    }

    @Test
    void testTransformationResultRecord() {
        String text = "Test text";
        List<File> files = List.of(new File("test.txt"));
        
        FileToTextTransformer.TransformationResult result = new FileToTextTransformer.TransformationResult(text, files);
        
        assertEquals(text, result.text());
        assertEquals(files, result.files());
    }

    @Test
    void testTransformationResultWithNullFiles() {
        String text = "Test text";
        
        FileToTextTransformer.TransformationResult result = new FileToTextTransformer.TransformationResult(text, null);
        
        assertEquals(text, result.text());
        assertNull(result.files());
    }
}