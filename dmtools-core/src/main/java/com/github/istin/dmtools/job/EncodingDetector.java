package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Helper class for detecting and decoding encoded parameters.
 * Supports auto-detection of base64 and URL encoding formats.
 */
public class EncodingDetector {
    
    private static final Logger logger = LogManager.getLogger(EncodingDetector.class);
    
    /**
     * Auto-detects encoding format and decodes the input string.
     * Attempts base64 decoding first, falls back to URL decoding if base64 fails.
     * 
     * @param encoded The encoded string to decode
     * @return The decoded string
     * @throws IllegalArgumentException if neither base64 nor URL decoding succeeds
     */
    public String autoDetectAndDecode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("Encoded parameter cannot be null or empty");
        }
        
        try {
            // Try base64 decoding first
            String decoded = decodeBase64(encoded);
            logger.info("Successfully decoded parameter using base64 encoding");
            return decoded;
        } catch (Exception e) {
            logger.debug("Base64 decoding failed, attempting URL decoding: {}", e.getMessage());
            
            try {
                // Fallback to URL decoding
                String decoded = decodeUrl(encoded);
                logger.info("Successfully decoded parameter using URL encoding");
                return decoded;
            } catch (Exception urlException) {
                logger.error("Both base64 and URL decoding failed for input parameter");
                throw new IllegalArgumentException(
                    "Unable to decode parameter - neither base64 nor URL encoding format detected. " +
                    "Base64 error: " + e.getMessage() + ". URL error: " + urlException.getMessage()
                );
            }
        }
    }
    
    /**
     * Decodes a base64-encoded string.
     * Implemented directly to avoid dependency on JobRunner static initialization.
     * 
     * @param input The base64-encoded string
     * @return The decoded string
     * @throws IllegalArgumentException if base64 decoding fails
     */
    public String decodeBase64(String input) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(input);
            return new String(decodedBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid base64 encoding: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decodes a URL-encoded string.
     * 
     * @param input The URL-encoded string
     * @return The decoded string
     * @throws IllegalArgumentException if URL decoding fails
     */
    public String decodeUrl(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL encoding: " + e.getMessage(), e);
        }
    }
}
