// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.context.converter;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

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

    private File createDocx(String fileName, Consumer<XWPFDocument> contentBuilder) throws Exception {
        File docxFile = tempDir.resolve(fileName).toFile();
        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile)) {
            contentBuilder.accept(document);
            document.write(out);
        }
        return docxFile;
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    @Test
    void testConvertSimpleDocxProducesImage() throws Exception {
        File docxFile = createDocx("simple.docx", document ->
                addParagraph(document, "Hello world, this is a test paragraph."));

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        File image = images.get(0);
        assertTrue(image.exists());
        assertTrue(image.getName().startsWith("simple_page1_"));
        assertTrue(image.getName().endsWith(".png"));
        assertNotNull(ImageIO.read(image));
    }

    @Test
    void testConvertEmptyDocxReturnsEmptyList() throws Exception {
        File docxFile = createDocx("empty.docx", document -> {
            // no content at all
        });

        List<File> images = converter.convert(docxFile);

        assertTrue(images.isEmpty());
    }

    @Test
    void testConvertDocxWithEmptyParagraphs() throws Exception {
        File docxFile = createDocx("spacing.docx", document -> {
            addParagraph(document, "First line");
            document.createParagraph(); // empty paragraph -> spacing line
            addParagraph(document, "Second line");
        });

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        assertTrue(images.get(0).exists());
    }

    @Test
    void testConvertDocxWithTable() throws Exception {
        File docxFile = createDocx("table.docx", document -> {
            XWPFTable table = document.createTable(2, 3);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Col1");
            headerRow.getCell(1).setText("Col2");
            headerRow.getCell(2).setText("Col3");
            XWPFTableRow dataRow = table.getRow(1);
            dataRow.getCell(0).setText("Value1");
            // leave middle cell empty to exercise the empty-cell branch
            dataRow.getCell(2).setText("Value3");
            // add a fully empty row to exercise the empty-row branch
            table.createRow();
        });

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        assertTrue(images.get(0).exists());
    }

    @Test
    void testConvertLongParagraphWrapsText() throws Exception {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longText.append("word").append(i).append(" ");
        }
        File docxFile = createDocx("long.docx", document ->
                addParagraph(document, longText.toString()));

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        assertTrue(images.get(0).exists());
    }

    @Test
    void testConvertVeryLongWordWrapsText() throws Exception {
        StringBuilder longWord = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longWord.append("a");
        }
        File docxFile = createDocx("longword.docx", document ->
                addParagraph(document, "prefix " + longWord + " suffix"));

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        assertTrue(images.get(0).exists());
    }

    @Test
    void testConvertManyLinesProducesMultiplePages() throws Exception {
        File docxFile = createDocx("multipage.docx", document -> {
            for (int i = 0; i < 130; i++) {
                addParagraph(document, "Line number " + i + " of the multipage document");
            }
        });

        List<File> images = converter.convert(docxFile);

        // (1600 - 2*80) / 24 = 60 lines per page -> 130 lines = 3 pages
        assertEquals(3, images.size());
        assertTrue(images.get(0).getName().contains("_page1_"));
        assertTrue(images.get(1).getName().contains("_page2_"));
        assertTrue(images.get(2).getName().contains("_page3_"));
        for (File image : images) {
            assertTrue(image.exists());
            assertNotNull(ImageIO.read(image));
        }
    }

    @Test
    void testConvertFileNameWithoutExtension() throws Exception {
        File docxFile = createDocx("noextension", document ->
                addParagraph(document, "Content without file extension"));

        List<File> images = converter.convert(docxFile);

        assertEquals(1, images.size());
        assertTrue(images.get(0).getName().startsWith("noextension_page1_"));
    }
}