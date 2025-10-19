package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for reading files with automatic encoding detection and line ending normalization
 */
public class KBFileReader {
    
    private static final Logger logger = LogManager.getLogger(KBFileReader.class);
    
    /**
     * Read file with automatic encoding detection and line ending normalization.
     * Handles files with mixed line endings (CRLF, LF, NEL) and various encodings.
     * 
     * @param filePath Path to the file to read
     * @return File content with normalized line endings
     * @throws IOException if file cannot be read
     */
    public String readAndNormalize(Path filePath) throws IOException {
        // Try UTF-8 first (most common)
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            // Check if UTF-8 decoding was successful (no replacement characters in suspicious places)
            if (!content.contains("\uFFFD") || content.indexOf("\uFFFD") > 100) {
                return normalizeLineEndings(content);
            }
        } catch (Exception e) {
            logger.debug("Failed to read as UTF-8, trying other encodings: {}", e.getMessage());
        }
        
        // Try ISO-8859-1 (Latin-1) as fallback
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.ISO_8859_1);
            return normalizeLineEndings(content);
        } catch (Exception e) {
            logger.warn("Failed to read with ISO-8859-1, using default encoding: {}", e.getMessage());
        }
        
        // Last resort: use default charset
        return normalizeLineEndings(Files.readString(filePath));
    }
    
    /**
     * Normalize all line endings to LF (\n).
     * Handles CRLF (\r\n), CR (\r), and NEL (U+0085) line endings.
     * 
     * @param content Content to normalize
     * @return Content with normalized line endings
     */
    public String normalizeLineEndings(String content) {
        if (content == null) {
            return null;
        }
        
        // Replace NEL (Next Line, U+0085) with LF
        content = content.replace("\u0085", "\n");
        // Replace CRLF with LF (must be before CR replacement)
        content = content.replace("\r\n", "\n");
        // Replace remaining CR with LF
        content = content.replace("\r", "\n");
        
        return content;
    }
}

