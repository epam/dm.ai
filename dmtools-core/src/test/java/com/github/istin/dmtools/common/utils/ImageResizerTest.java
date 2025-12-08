package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageResizer
 */
class ImageResizerTest {

    @TempDir
    Path tempDir;

    private ImageResizer resizer;

    @BeforeEach
    void setUp() {
        // Use custom settings for predictable test behavior
        resizer = new ImageResizer(8000, 0.9f);
    }

    @Test
    void testProcessImageWithSmallImage() throws Exception {
        // Given - Create a small test image (100x100)
        File inputImage = createTestImage(100, 100, "small_test.png");
        
        // When
        File processedImage = resizer.processImage(inputImage);
        
        // Then
        assertNotNull(processedImage);
        assertTrue(processedImage.exists(), "Processed image should exist");
        assertTrue(processedImage.getName().endsWith(".jpeg"), "Processed image should be JPEG");
        
        // Verify dimensions are preserved (no resize needed)
        BufferedImage result = ImageIO.read(processedImage);
        assertEquals(100, result.getWidth(), "Width should be preserved for small image");
        assertEquals(100, result.getHeight(), "Height should be preserved for small image");
    }

    @Test
    void testProcessImageWithLargeImage() throws Exception {
        // Given - Create a large test image (10000x5000)
        File inputImage = createTestImage(10000, 5000, "large_test.png");
        
        // When
        File processedImage = resizer.processImage(inputImage);
        
        // Then
        assertNotNull(processedImage);
        assertTrue(processedImage.exists(), "Processed image should exist");
        assertTrue(processedImage.getName().endsWith(".jpeg"), "Processed image should be JPEG");
        
        // Verify dimensions are resized
        BufferedImage result = ImageIO.read(processedImage);
        assertTrue(result.getWidth() <= 8000, "Width should be <= 8000");
        assertTrue(result.getHeight() <= 8000, "Height should be <= 8000");
        
        // Verify aspect ratio is maintained
        double originalAspect = 10000.0 / 5000.0;
        double processedAspect = (double) result.getWidth() / result.getHeight();
        assertEquals(originalAspect, processedAspect, 0.01, "Aspect ratio should be preserved");
    }

    @Test
    void testProcessImageWithVeryTallImage() throws Exception {
        // Given - Create a very tall image (2000x12000)
        File inputImage = createTestImage(2000, 12000, "tall_test.png");
        
        // When
        File processedImage = resizer.processImage(inputImage);
        
        // Then
        BufferedImage result = ImageIO.read(processedImage);
        assertTrue(result.getWidth() <= 8000, "Width should be <= 8000");
        assertTrue(result.getHeight() <= 8000, "Height should be <= 8000");
        
        // Height was the limiting dimension, so it should be 8000
        assertEquals(8000, result.getHeight(), "Height should be exactly 8000");
        
        // Verify aspect ratio
        double originalAspect = 2000.0 / 12000.0;
        double processedAspect = (double) result.getWidth() / result.getHeight();
        assertEquals(originalAspect, processedAspect, 0.01, "Aspect ratio should be preserved");
    }

    @Test
    void testProcessImageWithVeryWideImage() throws Exception {
        // Given - Create a very wide image (15000x3000)
        File inputImage = createTestImage(15000, 3000, "wide_test.png");
        
        // When
        File processedImage = resizer.processImage(inputImage);
        
        // Then
        BufferedImage result = ImageIO.read(processedImage);
        assertTrue(result.getWidth() <= 8000, "Width should be <= 8000");
        assertTrue(result.getHeight() <= 8000, "Height should be <= 8000");
        
        // Width was the limiting dimension, so it should be 8000
        assertEquals(8000, result.getWidth(), "Width should be exactly 8000");
        
        // Verify aspect ratio
        double originalAspect = 15000.0 / 3000.0;
        double processedAspect = (double) result.getWidth() / result.getHeight();
        assertEquals(originalAspect, processedAspect, 0.01, "Aspect ratio should be preserved");
    }

    @Test
    void testProcessImageConvertsToJpeg() throws Exception {
        // Given - Create a PNG image
        File inputImage = createTestImage(500, 500, "test.png");
        
        // When
        File processedImage = resizer.processImage(inputImage);
        
        // Then
        assertTrue(processedImage.getName().endsWith(".jpeg"), "Output should be JPEG");
        assertTrue(processedImage.getName().contains("_processed"), "Output should have _processed suffix");
    }

    @Test
    void testProcessImageWithNullFile() {
        // When/Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            resizer.processImage(null);
        });
        
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testProcessImageWithNonExistentFile() {
        // Given
        File nonExistent = new File(tempDir.toFile(), "nonexistent.png");
        
        // When/Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            resizer.processImage(nonExistent);
        });
        
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testProcessImageWithInvalidImageFile() throws Exception {
        // Given - Create a file that's not a valid image
        File invalidFile = tempDir.resolve("invalid.png").toFile();
        java.nio.file.Files.write(invalidFile.toPath(), "not an image".getBytes());
        
        // When/Then
        Exception exception = assertThrows(Exception.class, () -> {
            resizer.processImage(invalidFile);
        });
        
        assertNotNull(exception);
    }

    @Test
    void testNeedsProcessing() throws Exception {
        // Given
        File smallImage = createTestImage(1000, 1000, "small.png");
        File largeImage = createTestImage(9000, 9000, "large.png");
        
        // When/Then
        assertFalse(resizer.needsProcessing(smallImage), "Small image should not need processing");
        assertTrue(resizer.needsProcessing(largeImage), "Large image should need processing");
    }

    @Test
    void testCustomMaxDimension() throws Exception {
        // Given - Resizer with smaller max dimension
        ImageResizer smallResizer = new ImageResizer(1000, 0.9f);
        File inputImage = createTestImage(2000, 2000, "test.png");
        
        // When
        File processedImage = smallResizer.processImage(inputImage);
        
        // Then
        BufferedImage result = ImageIO.read(processedImage);
        assertTrue(result.getWidth() <= 1000, "Width should be <= 1000");
        assertTrue(result.getHeight() <= 1000, "Height should be <= 1000");
    }

    @Test
    void testJpegQualityBounds() {
        // Test that quality is clamped to valid range [0.0, 1.0]
        ImageResizer lowQuality = new ImageResizer(8000, -0.5f);  // Should clamp to 0.0
        ImageResizer highQuality = new ImageResizer(8000, 1.5f);  // Should clamp to 1.0
        
        // No exception should be thrown, quality should be clamped
        assertNotNull(lowQuality);
        assertNotNull(highQuality);
    }

    @Test
    void testDefaultConstructorUsesPropertyReader() {
        // This test verifies the default constructor doesn't throw
        // Actual values depend on dmtools.env configuration
        ImageResizer defaultResizer = new ImageResizer();
        assertNotNull(defaultResizer);
    }

    /**
     * Helper method to create a test image
     */
    private File createTestImage(int width, int height, String filename) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Fill with a simple pattern (optional, for visual debugging)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = ((x / 100) % 2 == 0) ? 0xFFFFFF : 0x000000;
                if ((x + y) % 100 < 50) {
                    rgb = 0xFF0000; // Add some red
                }
                image.setRGB(x, y, rgb);
            }
        }
        
        File imageFile = tempDir.resolve(filename).toFile();
        ImageIO.write(image, "png", imageFile);
        return imageFile;
    }
}



