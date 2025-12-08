package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Utility class for resizing and converting images to JPEG format.
 * Ensures images meet size constraints for AI model processing.
 */
public class ImageResizer {
    
    private static final Logger logger = LogManager.getLogger(ImageResizer.class);
    
    private final int maxDimension;
    private final float jpegQuality;
    
    /**
     * Create ImageResizer with default settings from PropertyReader
     */
    public ImageResizer() {
        PropertyReader propertyReader = new PropertyReader();
        this.maxDimension = propertyReader.getImageMaxDimension();
        this.jpegQuality = propertyReader.getImageJpegQuality();
        logger.debug("ImageResizer initialized with maxDimension={}, jpegQuality={}", maxDimension, jpegQuality);
    }
    
    /**
     * Create ImageResizer with custom settings
     * @param maxDimension maximum width or height in pixels (e.g., 8000)
     * @param jpegQuality JPEG compression quality (0.0 to 1.0, where 1.0 is best quality)
     */
    public ImageResizer(int maxDimension, float jpegQuality) {
        this.maxDimension = maxDimension;
        this.jpegQuality = Math.max(0.0f, Math.min(1.0f, jpegQuality));
        logger.debug("ImageResizer initialized with maxDimension={}, jpegQuality={}", maxDimension, this.jpegQuality);
    }
    
    /**
     * Process an image file: resize if needed and convert to JPEG.
     * If the image already meets constraints, it may still be converted to JPEG for consistency.
     * 
     * @param inputFile the input image file (PNG, JPEG, GIF, etc.)
     * @return the processed image file (JPEG format, possibly resized)
     * @throws IOException if image processing fails
     */
    public File processImage(File inputFile) throws IOException {
        if (inputFile == null || !inputFile.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }
        
        logger.debug("Processing image: {}", inputFile.getName());
        
        // Read the image
        BufferedImage image = ImageIO.read(inputFile);
        if (image == null) {
            throw new IOException("Failed to read image: " + inputFile.getName());
        }
        
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        logger.debug("Original image dimensions: {}x{}", originalWidth, originalHeight);
        
        // Check if resizing is needed
        boolean needsResize = originalWidth > maxDimension || originalHeight > maxDimension;
        
        BufferedImage processedImage = image;
        if (needsResize) {
            processedImage = resizeImage(image, maxDimension);
            logger.info("Resized image from {}x{} to {}x{}", 
                originalWidth, originalHeight, 
                processedImage.getWidth(), processedImage.getHeight());
        } else {
            logger.debug("Image dimensions within limits, no resize needed");
        }
        
        // Create output file (JPEG with .jpeg extension for API compatibility)
        File outputFile = createOutputFile(inputFile);
        
        // Convert to JPEG with quality setting
        convertToJpeg(processedImage, outputFile, jpegQuality);
        
        logger.debug("Processed image saved to: {}", outputFile.getName());
        return outputFile;
    }
    
    /**
     * Resize an image to fit within maxDimension while maintaining aspect ratio
     */
    private BufferedImage resizeImage(BufferedImage original, int maxDimension) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        // Calculate new dimensions maintaining aspect ratio
        int newWidth, newHeight;
        if (originalWidth > originalHeight) {
            newWidth = maxDimension;
            newHeight = (int) ((double) originalHeight / originalWidth * maxDimension);
        } else {
            newHeight = maxDimension;
            newWidth = (int) ((double) originalWidth / originalHeight * maxDimension);
        }
        
        // Create resized image with high quality
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Use high quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Convert image to JPEG format with specified quality
     */
    private void convertToJpeg(BufferedImage image, File outputFile, float quality) throws IOException {
        // Get JPEG writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        
        ImageWriter writer = writers.next();
        
        // Set compression quality
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        
        // Write the image
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            
            // Convert to RGB if needed (JPEG doesn't support alpha channel)
            BufferedImage rgbImage = image;
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }
            
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    
    /**
     * Create output file name (replace extension with .jpeg)
     */
    private File createOutputFile(File inputFile) {
        String inputName = inputFile.getName();
        String baseName = inputName.substring(0, inputName.lastIndexOf('.'));
        String outputName = baseName + "_processed.jpeg";
        
        File outputFile = new File(inputFile.getParent(), outputName);
        
        // If file exists, add counter to make unique
        int counter = 1;
        while (outputFile.exists()) {
            outputName = baseName + "_processed_" + counter + ".jpeg";
            outputFile = new File(inputFile.getParent(), outputName);
            counter++;
        }
        
        return outputFile;
    }
    
    /**
     * Check if an image file needs processing (exceeds dimensions)
     */
    public boolean needsProcessing(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            return false;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        return width > maxDimension || height > maxDimension;
    }
}



