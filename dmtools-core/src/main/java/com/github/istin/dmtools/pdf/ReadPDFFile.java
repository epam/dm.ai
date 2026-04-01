// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ReadPDFFile {
    private static final Logger logger = LogManager.getLogger(ReadPDFFile.class);

    public static void parsePdfFilesToTickets(String folderPath) {
        logger.info(folderPath);
        File[] listOfFiles = new File(folderPath).listFiles();

        if (listOfFiles == null) return;

        for (int i = 0; i < listOfFiles.length; i++) {
            File file = listOfFiles[i];
            if (file.isFile() && file.getName().endsWith(".pdf")) {
                breakFileToTickets(folderPath, file);
            }
        }
    }

    private static void breakFileToTickets(String folderPath, File file) {
        PDDocument document = null;
        try {
            String cacheFolder = folderPath + "/cache/" + file.getName().split("\\.")[0];
            File fileCacheFolder = new File(cacheFolder);
            if (fileCacheFolder.exists()) {

//                return;
            }

            fileCacheFolder.mkdirs();
            document = Loader.loadPDF(file);

            PDFTextStripper pdfStripper = new PDFTextStripper();

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageNum = 0;

            for (PDPage page : document.getPages()) {
                pageNum++;
                File currentPageCache = new File(cacheFolder + "/" + pageNum);
                currentPageCache.mkdirs();
                // Extract text
                pdfStripper.setStartPage(pageNum);
                pdfStripper.setEndPage(pageNum);
                String text = pdfStripper.getText(document);

                logger.info("Page {} Text:", pageNum);
                logger.info(text);

                // Extract links and replace in text
                StringBuilder htmlContent = new StringBuilder();
                htmlContent.append("<html><body>");
                List<PDAnnotation> annotations = page.getAnnotations();
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                int linksCounter = 1;
                for (PDAnnotation annotation : annotations) {
                    if (annotation instanceof PDAnnotationLink) {
                        PDAnnotationLink link = (PDAnnotationLink) annotation;
                        if (link.getAction() instanceof PDActionURI) {
                            PDActionURI uriAction = (PDActionURI) link.getAction();
                            String uri = uriAction.getURI();
                            String annotationText = link.getContents();

                            if (annotationText == null || annotationText.isEmpty()) {
                                PDRectangle linkRect = link.getRectangle();

                                // Convert PDRectangle to Java AWT Rectangle for the text stripper
                                Rectangle awtRect = new Rectangle(
                                        (int) linkRect.getLowerLeftX(),
                                        (int) linkRect.getLowerLeftY(),
                                        (int) linkRect.getWidth(),
                                        (int) linkRect.getHeight()
                                );

                                stripper.addRegion("linkRegion", awtRect);
                                stripper.extractRegions(page);

                                annotationText = stripper.getTextForRegion("linkRegion").trim();
                                if (annotationText.isEmpty()) {
                                    annotationText = "Link" + linksCounter;  // Default text for unidentified links
                                }
                            }

                            // Create the HTML representation with the extracted text
                            String linkHtml = "<a href=\"" + uri + "\">" + annotationText + "</a>";
                            if (text.contains(annotationText)) {
                                text = text.replace(annotationText, linkHtml);
                            } else {
                                logger.log(Level.DEBUG, "Warning: Couldn't find exact text, consider another matching strategy.");
                                text += linkHtml;
                            }
                            linksCounter++;
                        }
                    }
                }
                htmlContent.append("<p>").append(text).append("</p>");
                htmlContent.append("</body></html>");

                // Save text and links as HTML
                FileUtils.write(new File(currentPageCache, "description.html"), htmlContent.toString(), "UTF-8");

                FileUtils.write(new File(currentPageCache, "description.txt"), text);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(pageNum-1, 220); // 300 is the dpi (dots per inch), change it as needed

                // Save the image to a file
                ImageIO.write(bim, "png", new File(currentPageCache, "page_snapshot.png"));


                // Extract images
                PDResources resources = page.getResources();
                int imageNum = 0;

                for (COSName name : resources.getXObjectNames()) {
                    if (resources.isImageXObject(name)) {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                        BufferedImage bufferedImage = image.getImage();

                        // Get page rotation
                        int pageRotation = page.getRotation();

                        // Rotate image if needed
                        if (pageRotation != 0) {
                            bufferedImage = rotateImage(bufferedImage, pageRotation);
                        }

                        // Check if image needs to be rotated based on dimensions
                        if (shouldRotateBasedOnDimensions(bufferedImage)) {
                            bufferedImage = rotateImage(bufferedImage, 90);
                        }

                        // Save the image to a file
                        ImageIO.write(bufferedImage, "png", new File(currentPageCache, "attachment_" + (++imageNum) + ".png"));
                    }
                }
            }

            document.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static boolean shouldRotateBasedOnDimensions(BufferedImage image) {
        // You might want to adjust these thresholds based on your specific needs
        return image.getWidth() > image.getHeight() * 1.25; // Rotate if width is significantly larger than height
    }

    private static BufferedImage rotateImage(BufferedImage image, int degrees) {
        double rads = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(rads));
        double cos = Math.abs(Math.cos(rads));

        int width = image.getWidth();
        int height = image.getHeight();

        // New width and height for the rotated image
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();

        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Rotate around the center point
        AffineTransform at = new AffineTransform();
        at.translate((newWidth - width) / 2, (newHeight - height) / 2);
        at.rotate(rads, width / 2, height / 2);
        g2d.setTransform(at);

        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }
}
