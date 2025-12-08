package com.github.istin.dmtools.context.converter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xwpf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Converter for Word (DOCX) files to images.
 * Uses Apache POI to read DOCX and renders content to PNG images.
 * Note: This provides a simplified rendering - complex formatting may not be preserved.
 */
public class DocxToImagesConverter implements FileConverter {
    
    private static final Logger logger = LogManager.getLogger(DocxToImagesConverter.class);
    
    // Page dimensions (A4 size at 72 DPI scaled up for better quality)
    private static final int PAGE_WIDTH = 1200;
    private static final int PAGE_HEIGHT = 1600;
    private static final int MARGIN = 80;
    private static final int LINE_HEIGHT = 24;
    private static final int FONT_SIZE = 16;
    
    @Override
    public List<File> convert(File inputFile) throws Exception {
        logger.info("Converting DOCX to images: {}", inputFile.getName());
        
        List<File> imageFiles = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(inputFile);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            List<String> textLines = new ArrayList<>();
            
            // Extract text from paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    // Handle long paragraphs by wrapping
                    textLines.addAll(wrapText(text, PAGE_WIDTH - 2 * MARGIN, FONT_SIZE));
                } else {
                    // Empty line for spacing
                    textLines.add("");
                }
            }
            
            // Extract text from tables
            for (XWPFTable table : document.getTables()) {
                textLines.add("--- Table ---");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            if (!rowText.isEmpty()) {
                                rowText.append(" | ");
                            }
                            rowText.append(cellText.trim());
                        }
                    }
                    if (!rowText.isEmpty()) {
                        textLines.addAll(wrapText(rowText.toString(), PAGE_WIDTH - 2 * MARGIN, FONT_SIZE));
                    }
                }
                textLines.add("--- End Table ---");
                textLines.add("");
            }
            
            if (textLines.isEmpty()) {
                logger.warn("No text content found in DOCX");
                return imageFiles;
            }
            
            logger.info("Extracted {} lines of text from DOCX", textLines.size());
            
            // Render text to images (paginate)
            int pageNumber = 1;
            int lineIndex = 0;
            
            while (lineIndex < textLines.size()) {
                BufferedImage img = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = img.createGraphics();
                
                // Set rendering hints
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                
                // Fill with white background
                graphics.setPaint(Color.WHITE);
                graphics.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                
                // Set text color and font
                graphics.setPaint(Color.BLACK);
                graphics.setFont(new Font("Arial", Font.PLAIN, FONT_SIZE));
                
                // Draw text lines
                int y = MARGIN + LINE_HEIGHT;
                int linesOnPage = 0;
                int maxLinesPerPage = (PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT;
                
                while (lineIndex < textLines.size() && linesOnPage < maxLinesPerPage) {
                    String line = textLines.get(lineIndex);
                    graphics.drawString(line, MARGIN, y);
                    y += LINE_HEIGHT;
                    lineIndex++;
                    linesOnPage++;
                }
                
                graphics.dispose();
                
                // Save to temporary file
                File tempFile = File.createTempFile(
                    getBaseName(inputFile.getName()) + "_page" + pageNumber + "_",
                    ".png"
                );
                tempFile.deleteOnExit();
                
                ImageIO.write(img, "png", tempFile);
                imageFiles.add(tempFile);
                
                logger.debug("Created page {} with {} lines", pageNumber, linesOnPage);
                pageNumber++;
            }
            
            logger.info("Successfully converted DOCX to {} images (all content processed)", imageFiles.size());
        }
        
        return imageFiles;
    }
    
    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }
    
    /**
     * Wrap text to fit within specified width.
     * Simple word-wrapping algorithm.
     */
    private List<String> wrapText(String text, int maxWidth, int fontSize) {
        List<String> lines = new ArrayList<>();
        
        // Rough estimate: each character is about fontSize/2 pixels wide
        int charsPerLine = maxWidth / (fontSize / 2);
        
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > charsPerLine) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                // Handle very long words
                if (word.length() > charsPerLine) {
                    lines.add(word.substring(0, charsPerLine));
                    currentLine.append(word.substring(charsPerLine));
                } else {
                    currentLine.append(word);
                }
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        
        return lines.isEmpty() ? List.of(text) : lines;
    }
    
    /**
     * Extract base name from filename (without extension)
     */
    private String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}

