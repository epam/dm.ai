package com.github.istin.dmtools.context.converter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Converter for PowerPoint (PPTX) files to images.
 * Uses Apache POI to read PPTX and renders each slide to a PNG image.
 */
public class PptxToImagesConverter implements FileConverter {
    
    private static final Logger logger = LogManager.getLogger(PptxToImagesConverter.class);
    
    // Image scale factor for better quality (1.0 = slide dimensions, 2.0 = double resolution)
    private static final double SCALE_FACTOR = 2.0;
    
    @Override
    public List<File> convert(File inputFile) throws Exception {
        logger.info("Converting PPTX to images: {}", inputFile.getName());
        
        List<File> imageFiles = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(inputFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            
            List<XSLFSlide> slides = ppt.getSlides();
            int slideCount = slides.size();
            
            logger.info("Converting all {} slides from PPTX", slideCount);
            
            // Get slide dimensions
            Dimension pageSize = ppt.getPageSize();
            int width = (int) (pageSize.width * SCALE_FACTOR);
            int height = (int) (pageSize.height * SCALE_FACTOR);
            
            for (int i = 0; i < slideCount; i++) {
                XSLFSlide slide = slides.get(i);
                
                try {
                    // Create buffered image for this slide
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();
                    
                    // Set rendering hints for better quality
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    
                    // Fill with white background
                    graphics.setPaint(Color.WHITE);
                    graphics.fill(new Rectangle2D.Float(0, 0, width, height));
                    
                    // Scale graphics context
                    graphics.scale(SCALE_FACTOR, SCALE_FACTOR);
                    
                    // Render the slide
                    slide.draw(graphics);
                    
                    graphics.dispose();
                    
                    // Save to temporary file
                    File tempFile = File.createTempFile(
                        getBaseName(inputFile.getName()) + "_slide" + (i + 1) + "_",
                        ".png"
                    );
                    tempFile.deleteOnExit();
                    
                    ImageIO.write(img, "png", tempFile);
                    imageFiles.add(tempFile);
                    
                    logger.debug("Converted slide {} to {}", i + 1, tempFile.getName());
                    
                } catch (Exception e) {
                    logger.warn("Failed to convert slide {}: {}", i + 1, e.getMessage());
                }
            }
            
            logger.info("Successfully converted {} slides to images", imageFiles.size());
        }
        
        return imageFiles;
    }
    
    @Override
    public boolean supports(String extension) {
        return "pptx".equalsIgnoreCase(extension);
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

