package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class IOUtils {

    private static final Logger logger = LogManager.getLogger(IOUtils.class);

    /**
     * Extract a specific file from a ZIP byte array
     * @param zipData The ZIP file as byte array
     * @param fileName The file name to extract (e.g., "response.md")
     * @return The file content as String, or null if not found
     * @throws IOException if extraction fails
     */
    public static String extractFileFromZip(byte[] zipData, String fileName) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(zipData);
             ZipInputStream zis = new ZipInputStream(bis)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // Look for the specified file (can be in subdirectories)
                if (entryName.endsWith(fileName) || entryName.equals(fileName)) {
                    logger.info("Found file in ZIP: {}", entryName);
                    
                    // Read the file content
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    
                    String content = baos.toString("UTF-8");
                    logger.info("Extracted file content: {} bytes", content.length());
                    return content;
                }
            }
            
            logger.warn("File '{}' not found in ZIP archive", fileName);
            return null;
            
        } catch (Exception e) {
            logger.error("Error extracting file '{}' from ZIP: {}", fileName, e.getMessage());
            throw new IOException("Failed to extract file from ZIP", e);
        }
    }

    public static void deleteFiles(List<File> files) {
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.exists()) {
                if (!file.delete()) {
                    System.err.println("Failed to delete temporary file: " + file.getAbsolutePath());
                }
            }
        }
    }
}