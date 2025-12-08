package com.github.istin.dmtools.context.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PptxToImagesConverter
 */
class PptxToImagesConverterTest {

    private PptxToImagesConverter converter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        converter = new PptxToImagesConverter();
    }

    @Test
    void testSupportsValidExtension() {
        assertTrue(converter.supports("pptx"));
        assertTrue(converter.supports("PPTX"));
    }

    @Test
    void testDoesNotSupportInvalidExtension() {
        assertFalse(converter.supports("docx"));
        assertFalse(converter.supports("pdf"));
        assertFalse(converter.supports("txt"));
        assertFalse(converter.supports("png"));
    }

    @Test
    void testConvertWithNonExistentFile() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.pptx");
        
        Exception exception = assertThrows(Exception.class, () -> {
            converter.convert(nonExistentFile);
        });
        
        assertNotNull(exception);
    }

    @Test
    void testConvertWithInvalidPptxFile() throws Exception {
        // Create a file with .pptx extension but invalid content
        File invalidFile = tempDir.resolve("invalid.pptx").toFile();
        java.nio.file.Files.write(invalidFile.toPath(), "not a real pptx file".getBytes());
        
        Exception exception = assertThrows(Exception.class, () -> {
            converter.convert(invalidFile);
        });
        
        assertNotNull(exception);
    }

    // Note: Full integration test with real PPTX file would require:
    // - Creating a real PPTX file using Apache POI
    // - Or bundling a test PPTX resource
    // For now, we test the core logic (supports, error handling)
}



