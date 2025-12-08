package com.github.istin.dmtools.context.converter;

import java.io.File;
import java.util.List;

/**
 * Interface for file format converters.
 * Allows extensible conversion of various file types to other formats.
 */
public interface FileConverter {
    
    /**
     * Converts a file to target format.
     * 
     * @param inputFile Source file to convert
     * @return List of converted files (e.g., one image per page/slide)
     * @throws Exception if conversion fails
     */
    List<File> convert(File inputFile) throws Exception;
    
    /**
     * Check if this converter supports the given file extension.
     * 
     * @param extension File extension (without dot, e.g., "docx", "pptx")
     * @return true if this converter supports the extension
     */
    boolean supports(String extension);
}




