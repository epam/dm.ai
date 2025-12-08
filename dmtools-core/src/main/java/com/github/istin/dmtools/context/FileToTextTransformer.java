package com.github.istin.dmtools.context;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.context.converter.DocxToImagesConverter;
import com.github.istin.dmtools.context.converter.FileConverter;
import com.github.istin.dmtools.context.converter.PptxToImagesConverter;
import com.github.istin.dmtools.pdf.PdfAsTrackerClient;
import com.github.istin.dmtools.pdf.model.PdfPageAsTicket;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileToTextTransformer {

    private static final Logger logger = LogManager.getLogger(FileToTextTransformer.class);

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "pdf", "csv", "doc", "docx", "xls", "xlsx",
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp"
    );
    
    // Office document extensions that can be converted to images
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "pptx");
    
    // Map of file converters by extension
    private static final Map<String, FileConverter> CONVERTERS = new HashMap<>();
    
    static {
        DocxToImagesConverter docxConverter = new DocxToImagesConverter();
        PptxToImagesConverter pptxConverter = new PptxToImagesConverter();
        
        CONVERTERS.put("docx", docxConverter);
        CONVERTERS.put("pptx", pptxConverter);
    }

    public record TransformationResult(String text, List<File> files) {
    }

    public static List<TransformationResult> transform(File file) throws Exception {
        if (file == null || !file.exists()) {
            return null;
        }

        String fileName = file.getName().toLowerCase();

        // Check if it's an Office document that can be converted to images
        String extension = getExtension(fileName);
        if (OFFICE_EXTENSIONS.contains(extension)) {
            return transformOfficeDocument(file, extension);
        }

        // Special handling for PDF files
        if (fileName.endsWith(".pdf")) {
            return transformPdf(file);
        }

        // Return null for binary files
        if (BINARY_EXTENSIONS.stream().anyMatch(ext -> fileName.endsWith("." + ext))) {
            return null;
        }
        // Read text files
        return List.of(new TransformationResult(file.getName() + "\n" + FileUtils.readFileToString(file, "UTF-8"), null));
    }
    
    /**
     * Transform Office documents (DOCX, PPTX) to images.
     */
    private static List<TransformationResult> transformOfficeDocument(File file, String extension) throws Exception {
        logger.info("Transforming Office document to images: {}", file.getName());
        
        FileConverter converter = CONVERTERS.get(extension);
        if (converter == null) {
            logger.warn("No converter found for extension: {}", extension);
            return null;
        }
        
        try {
            List<File> imageFiles = converter.convert(file);
            
            if (imageFiles == null || imageFiles.isEmpty()) {
                logger.warn("No images generated from Office document: {}", file.getName());
                return null;
            }
            
            // Return a single result with description and all image files
            String description = String.format("Office document (%s): %s - converted to %d image(s)", 
                extension.toUpperCase(), file.getName(), imageFiles.size());
            
            logger.info("Successfully converted {} to {} images", file.getName(), imageFiles.size());
            
            return List.of(new TransformationResult(description, imageFiles));
            
        } catch (Exception e) {
            logger.error("Failed to convert Office document {}: {}", file.getName(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Extract file extension from filename.
     */
    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private static List<TransformationResult> transformPdf(File pdfFile) throws Exception {
        // Create a folder for PDF processing in the same directory
        String folderName = pdfFile.getName().replace(".pdf", "");
        File pdfFolder = new File(pdfFile.getParent(), folderName);
        pdfFolder.mkdirs();

        // Copy PDF file to the new folder
        File pdfCopy = new File(pdfFolder, pdfFile.getName());
        FileUtils.copyFile(pdfFile, pdfCopy);

        // Initialize PdfAsTrackerClient with the new folder
        PdfAsTrackerClient trackerClient = new PdfAsTrackerClient(pdfFolder.getAbsolutePath());

        // Get all PDF pages as tickets
        List<PdfPageAsTicket> pdfPages = trackerClient.searchAndPerform(null, null);

        // Convert PdfPageAsTicket objects to TransformationResults
        List<TransformationResult> results = new ArrayList<>();

        for (PdfPageAsTicket ticket : pdfPages) {
            List<File> files = new ArrayList<>();

            // Add all attachments
            List<? extends IAttachment> attachments = ticket.getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                if (ticket.getPageSnapshot() != null) {
                    files.add(ticket.getPageSnapshot());
                }
                //TODO think about reading full page screenshot or attachments
                //files.addAll(ticket.getAttachmentsAsFiles());
            }

            // Create TransformationResult with ticket description and files
            results.add(new TransformationResult(
                    ticket.getTicketDescription(),
                    files
            ));
        }

        return results;
    }

}
