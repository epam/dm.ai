package com.github.istin.dmtools.context.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DocxToImagesConverter
 */
class DocxToImagesConverterTest {

    private DocxToImagesConverter converter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        converter = new DocxToImagesConverter();
    }

    @Test
    void testSupportsValidExtension() {
        assertTrue(converter.supports("docx"));
        assertTrue(converter.supports("DOCX"));
    }

    @Test
    void testDoesNotSupportInvalidExtension() {
        assertFalse(converter.supports("pptx"));
        assertFalse(converter.supports("pdf"));
        assertFalse(converter.supports("txt"));
        assertFalse(converter.supports("doc"));
    }

    @Test
    void testConvertWithNonExistentFile() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.docx");
        
        Exception exception = assertThrows(Exception.class, () -> {
            converter.convert(nonExistentFile);
        });
        
        assertNotNull(exception);
    }

    @Test
    void testConvertWithInvalidDocxFile() throws Exception {
        // Create a file with .docx extension but invalid content
        File invalidFile = tempDir.resolve("invalid.docx").toFile();
        java.nio.file.Files.write(invalidFile.toPath(), "not a real docx file".getBytes());
        
        Exception exception = assertThrows(Exception.class, () -> {
            converter.convert(invalidFile);
        });
        
        assertNotNull(exception);
    }

    // Note: Full integration test with real DOCX file would require:
    // - Creating a real DOCX file using Apache POI
    // - Or bundling a test DOCX resource
    // For now, we test the core logic (supports, error handling)
}



