package com.github.istin.dmtools.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncodingDetectorTest {

    private EncodingDetector encodingDetector;

    @BeforeEach
    void setUp() {
        encodingDetector = new EncodingDetector();
    }

    @Test
    void testDecodeBase64_validInput() {
        String input = "Hello World";
        String encoded = Base64.getEncoder().encodeToString(input.getBytes());
        String decoded = encodingDetector.decodeBase64(encoded);
        assertEquals(input, decoded);
    }

    @Test
    void testDecodeBase64_invalidInput() {
        String invalidBase64 = "not-valid-base64!@#";
        assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.decodeBase64(invalidBase64);
        });
    }

    @Test
    void testDecodeUrl_validInput() {
        String input = "Hello World";
        String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
        String decoded = encodingDetector.decodeUrl(encoded);
        assertEquals(input, decoded);
    }

    @Test
    void testDecodeUrl_specialCharacters() {
        String input = "{\"key\": \"value with spaces & symbols!\"}";
        String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
        String decoded = encodingDetector.decodeUrl(encoded);
        assertEquals(input, decoded);
    }

    @Test
    void testDecodeUrl_invalidInput() {
        String invalidUrl = "%ZZ"; // Invalid URL encoding
        assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.decodeUrl(invalidUrl);
        });
    }

    @Test
    void testAutoDetectAndDecode_base64Success() {
        String input = "{\"name\": \"test\", \"value\": 123}";
        String base64Encoded = Base64.getEncoder().encodeToString(input.getBytes());
        
        String decoded = encodingDetector.autoDetectAndDecode(base64Encoded);
        assertEquals(input, decoded);
    }

    @Test
    void testAutoDetectAndDecode_urlEncodingFallback() {
        String input = "{\"name\": \"test\", \"value\": 123}";
        String urlEncoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
        
        String decoded = encodingDetector.autoDetectAndDecode(urlEncoded);
        assertEquals(input, decoded);
    }

    @Test
    void testAutoDetectAndDecode_base64Priority() {
        // Create a string that could potentially be valid in both formats
        String input = "simple";
        String base64Encoded = Base64.getEncoder().encodeToString(input.getBytes());
        
        // Auto-detection should try base64 first
        String decoded = encodingDetector.autoDetectAndDecode(base64Encoded);
        assertEquals(input, decoded);
    }

    @Test
    void testAutoDetectAndDecode_nullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.autoDetectAndDecode(null);
        });
    }

    @Test
    void testAutoDetectAndDecode_emptyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.autoDetectAndDecode("");
        });
    }

    @Test
    void testAutoDetectAndDecode_whitespaceOnlyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.autoDetectAndDecode("   ");
        });
    }

    @Test
    void testAutoDetectAndDecode_bothFormatsInvalid() {
        String invalidInput = "definitely-not-base64-or-url-encoded!@#$%^&*()";
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            encodingDetector.autoDetectAndDecode(invalidInput);
        });
        
        assertTrue(exception.getMessage().contains("Unable to decode parameter"));
        assertTrue(exception.getMessage().contains("Base64 error"));
        assertTrue(exception.getMessage().contains("URL error"));
    }

    @Test
    void testAutoDetectAndDecode_complexJsonBase64() {
        String complexJson = "{\"name\":\"DMTools\",\"version\":\"1.0\",\"config\":{\"timeout\":30,\"retries\":3},\"features\":[\"base64\",\"url\"]}";
        String base64Encoded = Base64.getEncoder().encodeToString(complexJson.getBytes());
        
        String decoded = encodingDetector.autoDetectAndDecode(base64Encoded);
        assertEquals(complexJson, decoded);
    }

    @Test
    void testAutoDetectAndDecode_complexJsonUrl() {
        String complexJson = "{\"name\":\"DMTools\",\"version\":\"1.0\",\"config\":{\"timeout\":30,\"retries\":3},\"features\":[\"base64\",\"url\"]}";
        String urlEncoded = URLEncoder.encode(complexJson, StandardCharsets.UTF_8);
        
        String decoded = encodingDetector.autoDetectAndDecode(urlEncoded);
        assertEquals(complexJson, decoded);
    }
}
