package com.github.istin.dmtools.index.mermaid;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.index.ConfluenceMermaidIndexIntegration;
import com.github.istin.dmtools.common.utils.ImageResizer;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.context.FileToTextTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Core orchestration class for Mermaid indexing.
 * Manages file system operations, diagram generation, and coordinates with integrations.
 */
public class MermaidIndex {
    
    private static final Logger logger = LogManager.getLogger(MermaidIndex.class);
    
    // Maximum number of images per request to avoid 413 errors (6MB limit)
    // Each base64-encoded image can be large, so limit to 5 images per request
    
    private final String integrationName;
    private final String storagePath;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final MermaidIndexIntegration integration;
    private final MermaidDiagramGeneratorAgent diagramGenerator;
    private final ImageResizer imageResizer;
    
    /**
     * Creates a new MermaidIndex instance.
     * 
     * @param integrationName Name of the integration (e.g., "confluence")
     * @param storagePath Base path for storing generated diagrams
     * @param includePatterns List of patterns to include
     * @param excludePatterns List of patterns to exclude
     * @param confluence Confluence instance (required for confluence integration)
     * @param diagramGenerator Diagram generator agent
     * @throws IllegalArgumentException if required parameters are null
     */
    public MermaidIndex(String integrationName, String storagePath, 
                       List<String> includePatterns, List<String> excludePatterns,
                       Confluence confluence, MermaidDiagramGeneratorAgent diagramGenerator) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage path is required");
        }
        if (diagramGenerator == null) {
            throw new IllegalArgumentException("Diagram generator is required");
        }
        
        this.integrationName = integrationName;
        this.storagePath = storagePath;
        this.includePatterns = includePatterns;
        this.excludePatterns = excludePatterns;
        this.diagramGenerator = diagramGenerator;
        this.imageResizer = new ImageResizer();
        
        // Create integration instance based on name
        if ("confluence".equalsIgnoreCase(integrationName)) {
            if (confluence == null) {
                throw new IllegalArgumentException("Confluence instance is required for confluence integration");
            }
            this.integration = new ConfluenceMermaidIndexIntegration(confluence);
        } else {
            throw new IllegalArgumentException("Unsupported integration: " + integrationName);
        }
    }
    
    /**
     * Executes the indexing process.
     * Retrieves content from the integration and generates diagrams for matching items.
     */
    public void index() throws Exception {
        logger.info("Starting Mermaid indexing for integration: {}", integrationName);
        
        integration.getContentForIndex(includePatterns, excludePatterns, (pathOrId, contentName, content, metadata, attachments, lastModified) -> {
            try {
                processContent(pathOrId, contentName, content, metadata, attachments, lastModified);
            } catch (Exception e) {
                logger.error("Error processing content {}: {}", pathOrId, e.getMessage(), e);
            }
        });
        
        logger.info("Mermaid indexing completed");
    }
    
    /**
     * Processes a single content item.
     * Checks if diagram needs to be generated/updated and creates it if necessary.
     * Processes attachments: images are attached directly, PDFs are transformed first.
     */
    private void processContent(String pathOrId, String contentName, String content, 
                               List<String> metadata, List<File> attachments, Date lastModified) throws Exception {
        // Parse pathOrId to extract spaceKey and path components
        // Format for root pages: spaceKey/pageId
        // Format for child pages: spaceKey/parentPageId/parentPageName/childPageId
        String[] parts = pathOrId.split("/");
        if (parts.length < 2) {
            logger.warn("Invalid pathOrId format: {}, expected at least spaceKey/pageId", pathOrId);
            return;
        }
        
        String spaceKey = sanitizePath(parts[0]);
        String sanitizedContentName = sanitizeFileName(contentName);
        
        // Build file path based on path structure
        // For root pages: storagePath/integrationName/spaceKey/pageId/pageTitle.mmd
        // For child pages: storagePath/integrationName/spaceKey/parentPageId/parentPageName/childPageId/pageTitle.mmd
        Path baseDir;
        if (parts.length == 2) {
            // Root page: spaceKey/pageId
            String pageId = sanitizePath(parts[1]);
            baseDir = Paths.get(storagePath, integrationName, spaceKey, pageId);
        } else {
            // Child page: spaceKey/parentPageId/parentPageName/childPageId
            // Build path with all components
            String[] pathComponents = new String[parts.length - 1];
            for (int i = 1; i < parts.length; i++) {
                pathComponents[i - 1] = sanitizePath(parts[i]);
            }
            baseDir = Paths.get(storagePath, integrationName, spaceKey);
            for (String component : pathComponents) {
                baseDir = baseDir.resolve(component);
            }
        }
        
        Path diagramPath = baseDir.resolve(sanitizedContentName + ".mmd");
        
        // Check if file exists and compare modification time
        if (Files.exists(diagramPath)) {
            try {
                long fileModTime = Files.getLastModifiedTime(diagramPath).toMillis();
                long contentModTime = lastModified.getTime();
                
                // If file was created after content was last modified, skip
                if (fileModTime >= contentModTime) {
                    logger.info("✓ SKIP: {} - diagram is up to date (file: {}, content: {})", 
                        diagramPath.getFileName(), 
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(fileModTime)),
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(contentModTime)));
                    return;
                }
                logger.info("↻ REGENERATE: {} - content was modified (file: {}, content: {})", 
                    diagramPath.getFileName(),
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(fileModTime)),
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(contentModTime)));
            } catch (Exception e) {
                logger.warn("Failed to check modification time for {}: {}", diagramPath, e.getMessage());
            }
        } else {
            logger.info("→ NEW: {} - creating diagram for the first time", diagramPath.getFileName());
        }
        
        // Main diagram processes ONLY the page content itself
        // ALL attachments (any type) will be processed separately via generateAttachmentDiagram()
        String contentString = content;
        logger.info("Generating main diagram for: {} (content length: {}, {} attachments will be processed separately)", 
            diagramPath, contentString.length(), attachments != null ? attachments.size() : 0);
        
        // Check if content is empty or whitespace-only
        String diagram;
        if (contentString == null || contentString.trim().isEmpty()) {
            logger.info("No content for diagram, using placeholder");
            diagram = "no diagram";
        } else {
            MermaidDiagramGeneratorAgent.Params params;
            List<ChunkPreparation.Chunk> chunks = prepareChunksIfNeeded(contentString);
            
            if (chunks != null && !chunks.isEmpty()) {
                logger.info("Content is too large, using {} chunks for processing", chunks.size());
                params = new MermaidDiagramGeneratorAgent.Params(
                    contentString,
                    null, // No images in main diagram - all processed separately
                    chunks
                );
                    } else {
                params = new MermaidDiagramGeneratorAgent.Params(
                    contentString, 
                    null // No images in main diagram - all processed separately
                );
            }
            diagram = diagramGenerator.run(params);
        }
        
        // Create directory if it doesn't exist
        Files.createDirectories(baseDir);
        
        // Write diagram to file
        Files.write(diagramPath, diagram.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Set file modification time to match content last modified date
        try {
            Files.setLastModifiedTime(diagramPath, java.nio.file.attribute.FileTime.fromMillis(lastModified.getTime()));
        } catch (Exception e) {
            logger.warn("Failed to set modification time for {}: {}", diagramPath, e.getMessage());
        }
        
        // Generate separate diagrams for each attachment in attachments subdirectory
        if (attachments != null && !attachments.isEmpty()) {
            logger.info("Generating separate diagrams for {} attachments", attachments.size());
            // Create attachments subdirectory
            Path attachmentsDir = baseDir.resolve("attachments");
            for (File attachment : attachments) {
                if (attachment == null || !attachment.exists()) {
                    continue;
                }
                
                try {
                    generateAttachmentDiagram(attachment, attachmentsDir, lastModified);
                } catch (Exception e) {
                    logger.warn("Failed to generate diagram for attachment {}: {}", attachment.getName(), e.getMessage());
                }
            }
        }
        
        logger.debug("Processed diagram for: {} with {} attachments", diagramPath, attachments != null ? attachments.size() : 0);
    }
    
    /**
     * Generates a separate Mermaid diagram for a single attachment.
     * 
     * @param attachment the attachment file to process
     * @param attachmentsDir the attachments directory where attachment diagrams are stored (e.g., baseDir/attachments)
     * @param lastModified the last modified date for the attachment
     * @throws Exception if diagram generation fails
     */
    private void generateAttachmentDiagram(File attachment, Path attachmentsDir, Date lastModified) throws Exception {
        String attachmentName = attachment.getName();
        String fileName = attachmentName.toLowerCase();
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot + 1);
        }
        
        // Remove extension for diagram filename
        String baseName = lastDot > 0 ? attachmentName.substring(0, lastDot) : attachmentName;
        String sanitizedAttachmentName = sanitizeFileName(baseName);
        // Create attachments directory if it doesn't exist
        Files.createDirectories(attachmentsDir);
        Path attachmentDiagramPath = attachmentsDir.resolve(sanitizedAttachmentName + ".mmd");
        
        // Check if file exists and compare modification time
        if (Files.exists(attachmentDiagramPath)) {
            try {
                long fileModTime = Files.getLastModifiedTime(attachmentDiagramPath).toMillis();
                long attachmentModTime = lastModified.getTime();
                
                // If file was created after attachment was last modified, skip
                if (fileModTime >= attachmentModTime) {
                    logger.info("  ✓ SKIP attachment: {} - diagram is up to date", attachmentName);
                    return;
                }
                logger.info("  ↻ REGENERATE attachment: {} - was modified", attachmentName);
            } catch (Exception e) {
                logger.warn("Failed to check modification time for {}: {}", attachmentDiagramPath, e.getMessage());
            }
        } else {
            logger.info("  → NEW attachment: {} - creating diagram", attachmentName);
        }
        
        // Supported image extensions (only: gif, jpeg, png, webp)
        Set<String> imageExtensions = Set.of("gif", "jpeg", "png", "webp");
        
        String attachmentContent = "";
        List<File> attachmentImageFiles = new ArrayList<>();
        
        // Process attachment based on type
        if (imageExtensions.contains(extension)) {
            // For images, create content description and attach the image
            // The AI will analyze the image and generate a diagram based on its content
            attachmentContent = "Analyze the attached image and create a Mermaid diagram that visualizes its content, structure, relationships, or information flow. " +
                    "If the image contains a diagram, flowchart, architecture, or any structured information, convert it to Mermaid syntax. " +
                    "If the image shows a process or workflow, create a flowchart. " +
                    "If the image shows relationships between entities, create an appropriate relationship diagram. " +
                    "If the image contains text or data, extract and organize it into a diagram. " +
                    "Image filename: " + attachmentName;
            
            // Process image (resize if needed, convert to JPEG)
            try {
                File processedImage = imageResizer.processImage(attachment);
                attachmentImageFiles.add(processedImage);
                logger.debug("Processing image attachment: {} (processed to: {})", 
                    attachmentName, processedImage.getName());
            } catch (Exception e) {
                logger.warn("Failed to process image {}, using original: {}", attachmentName, e.getMessage());
                attachmentImageFiles.add(attachment);
            }
        } else if (extension.equals("pdf") || extension.equals("docx") || extension.equals("pptx")) {
            // For PDFs and Office documents: Create subfolder and process each image separately
            logger.info("Processing multi-page document attachment: {} ({})", attachmentName, extension.toUpperCase());
            processMultiPageAttachment(attachment, attachmentsDir, baseName, lastModified);
            return; // Multi-page processing complete, return early
        } else {
            // Known binary formats that shouldn't be processed as text
            Set<String> binaryExtensions = Set.of(
                "exe", "dll", "so", "dylib", "bin", "dat", "class", "jar", "war", "ear",
                "zip", "tar", "gz", "bz2", "7z", "rar", "iso", 
                "mp4", "avi", "mov", "mkv", "flv", "wmv", 
                "mp3", "wav", "flac", "aac", "ogg", 
                "bmp", "tiff", "ico", "svg",
                "xls", "xlsx", "xlsm", "doc", "ppt", "pptm"
            );
            
            if (binaryExtensions.contains(extension)) {
                logger.debug("Skipping binary attachment {}: known binary format", attachmentName);
                attachmentContent = "Attachment: " + attachmentName + " (binary format - not processed)";
        } else {
            // For other formats, try to read as text
            try {
                logger.debug("Reading text from attachment: {}", attachmentName);
                String textContent = new String(Files.readAllBytes(attachment.toPath()), StandardCharsets.UTF_8);
                // Filter out base64 strings that might be in the text
                textContent = textContent.replaceAll("data:image/[^;]+;base64,[a-zA-Z0-9+/=]{100,}", "[Base64 image data removed]");
                textContent = textContent.replaceAll("[a-zA-Z0-9+/=]{500,}", "[Large base64 string removed]");
                attachmentContent = "Attachment: " + attachmentName + "\n\n" + textContent;
            } catch (Exception e) {
                logger.debug("Could not read attachment {} as text: {}", attachmentName, e.getMessage());
                attachmentContent = "Attachment: " + attachmentName + " (binary or unsupported format)";
                }
            }
        }
        
        // Generate diagram for attachment
        logger.info("Generating diagram for attachment: {} (content length: {}, with {} images)", 
            attachmentDiagramPath, attachmentContent.length(), attachmentImageFiles.size());
        logger.debug("Attachment content preview: {}", 
            attachmentContent.length() > 200 ? attachmentContent.substring(0, 200) + "..." : attachmentContent);
        
        // Check if there's any meaningful content to process
        String attachmentDiagram;
        if ((attachmentContent == null || attachmentContent.trim().isEmpty()) && 
            (attachmentImageFiles == null || attachmentImageFiles.isEmpty())) {
            logger.info("No content or images for attachment diagram, using placeholder");
            attachmentDiagram = "no diagram";
        } else {
            MermaidDiagramGeneratorAgent.Params attachmentParams;
            List<ChunkPreparation.Chunk> attachmentChunks = prepareChunksIfNeeded(attachmentContent);
            
            if (attachmentChunks != null && !attachmentChunks.isEmpty()) {
                logger.info("Attachment content is too large, using {} chunks for processing", attachmentChunks.size());
                attachmentParams = new MermaidDiagramGeneratorAgent.Params(
                    attachmentContent,
                    attachmentImageFiles.isEmpty() ? null : attachmentImageFiles,
                    attachmentChunks
                );
            } else {
                attachmentParams = new MermaidDiagramGeneratorAgent.Params(
            attachmentContent,
            attachmentImageFiles.isEmpty() ? null : attachmentImageFiles
        );
            }
        
        try {
            attachmentDiagram = diagramGenerator.run(attachmentParams);
            logger.debug("Diagram generation completed for attachment {}, result length: {}", 
                attachmentName, attachmentDiagram != null ? attachmentDiagram.length() : 0);
        } catch (Exception e) {
            logger.error("Failed to generate diagram for attachment {}: {}", attachmentName, e.getMessage(), e);
            throw e;
            }
        }
        
        // Check if diagram is empty or null
        if (attachmentDiagram == null || attachmentDiagram.trim().isEmpty()) {
            logger.warn("Generated diagram for attachment {} is empty (result: '{}'), skipping file write", 
                attachmentName, attachmentDiagram);
            // Still create an empty file or a placeholder to indicate processing happened
            String placeholder = "%% Diagram generation returned empty result for attachment: " + attachmentName + "\n%% This may indicate the image could not be analyzed or converted to a diagram.";
            Files.write(attachmentDiagramPath, placeholder.getBytes(StandardCharsets.UTF_8), 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }
        
        // Write diagram to file
        Files.write(attachmentDiagramPath, attachmentDiagram.getBytes(StandardCharsets.UTF_8), 
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Set file modification time to match attachment last modified date
        try {
            Files.setLastModifiedTime(attachmentDiagramPath, java.nio.file.attribute.FileTime.fromMillis(lastModified.getTime()));
        } catch (Exception e) {
            logger.warn("Failed to set modification time for {}: {}", attachmentDiagramPath, e.getMessage());
        }
        
        logger.info("Generated diagram for attachment: {} ({} bytes)", attachmentDiagramPath, attachmentDiagram.length());
    }
    
    /**
     * Sanitizes a path component to be filesystem-safe.
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "unknown";
        }
        // Replace invalid filesystem characters
        return path.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Sanitizes a filename to be filesystem-safe.
     * Uses shared utility with 200 character limit.
     */
    private String sanitizeFileName(String fileName) {
        return StringUtils.sanitizeFileName(fileName, "untitled", 200);
    }
    
    /**
     * Process multi-page documents (PDF, DOCX, PPTX) by creating a subfolder
     * and generating separate Mermaid diagrams for each extracted image/page.
     * 
     * @param attachment the document file to process
     * @param attachmentsDir the attachments directory
     * @param baseName base name for the document (without extension)
     * @param lastModified last modified date
     * @throws Exception if processing fails
     */
    private void processMultiPageAttachment(File attachment, Path attachmentsDir, String baseName, Date lastModified) throws Exception {
        String attachmentName = attachment.getName();
        String sanitizedBaseName = sanitizeFileName(baseName);
        
        // Create subfolder for this document's pages
        Path documentFolder = attachmentsDir.resolve(sanitizedBaseName);
        Files.createDirectories(documentFolder);
        
        logger.info("Processing multi-page document {} into folder {}", attachmentName, documentFolder);
        
        // Transform document to extract images (PDFs → pages, DOCX/PPTX → slides/pages)
        List<FileToTextTransformer.TransformationResult> results;
        try {
            results = FileToTextTransformer.transform(attachment);
        } catch (Exception e) {
            logger.error("Failed to transform attachment {}: {}", attachmentName, e.getMessage(), e);
            return;
        }
        
        if (results == null || results.isEmpty()) {
            logger.warn("No content extracted from attachment {}", attachmentName);
            return;
        }
        
        // Supported image extensions
        Set<String> imageExtensions = Set.of("gif", "jpeg", "jpg", "png", "webp");
        
        // Process each extracted image separately
        int imageCount = 0;
        for (FileToTextTransformer.TransformationResult result : results) {
            if (result.files() != null) {
                for (File extractedImage : result.files()) {
                    String extractedFileName = extractedImage.getName().toLowerCase();
                    String extractedExt = "";
                    int dotIndex = extractedFileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extractedExt = extractedFileName.substring(dotIndex + 1);
                        // Normalize "jpg" to "jpeg"
                        if (extractedExt.equals("jpg")) {
                            extractedExt = "jpeg";
                        }
                    }
                    
                    if (imageExtensions.contains(extractedExt)) {
                        imageCount++;
                        String pageName = String.format("page_%03d", imageCount);
                        Path pageDiagramPath = documentFolder.resolve(pageName + ".mmd");
                        
                        // Check if diagram already exists and is up to date
                        if (Files.exists(pageDiagramPath)) {
                            try {
                                long fileModTime = Files.getLastModifiedTime(pageDiagramPath).toMillis();
                                long attachmentModTime = lastModified.getTime();
                                
                                if (fileModTime >= attachmentModTime) {
                                    logger.info("    ✓ SKIP page {}: diagram is up to date", imageCount);
                                    continue;
                                }
                                logger.info("    ↻ REGENERATE page {}: was modified", imageCount);
                            } catch (Exception e) {
                                logger.warn("Failed to check modification time for {}: {}", pageDiagramPath, e.getMessage());
                            }
                        } else {
                            logger.info("    → NEW page {}: creating diagram", imageCount);
                        }
                        
                        // Generate diagram for this single image
                        logger.info("Generating diagram for page {} of {}", imageCount, attachmentName);
                        
                        String pageContent = String.format(
                            "Analyze page %d from document '%s' and create a Mermaid diagram. " +
                            "Extract structure, relationships, or information flow. " +
                            "If the page contains a diagram, flowchart, or architecture, convert it to Mermaid syntax. " +
                            "If it shows a process or workflow, create a flowchart. " +
                            "If it shows relationships between entities, create an appropriate diagram.",
                            imageCount, attachmentName
                        );
                        
                        // Process image (resize if needed, convert to JPEG)
                        File imageToProcess = extractedImage;
                        try {
                            imageToProcess = imageResizer.processImage(extractedImage);
                            logger.debug("Processed page {} image to: {}", imageCount, imageToProcess.getName());
                        } catch (Exception e) {
                            logger.warn("Failed to process page {} image, using original: {}", imageCount, e.getMessage());
                        }
                        
                        List<File> singleImageList = Collections.singletonList(imageToProcess);
                        
                        MermaidDiagramGeneratorAgent.Params pageParams = new MermaidDiagramGeneratorAgent.Params(
                            pageContent,
                            singleImageList
                        );
                        
                        try {
                            String pageDiagram = diagramGenerator.run(pageParams);
                            
                            if (pageDiagram != null && !pageDiagram.trim().isEmpty()) {
                                Files.write(pageDiagramPath, pageDiagram.getBytes(StandardCharsets.UTF_8), 
                                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                                
                                // Set modification time
                                try {
                                    Files.setLastModifiedTime(pageDiagramPath, 
                                        java.nio.file.attribute.FileTime.fromMillis(lastModified.getTime()));
                                } catch (Exception e) {
                                    logger.warn("Failed to set modification time for {}: {}", pageDiagramPath, e.getMessage());
                                }
                                
                                logger.info("Successfully generated diagram for page {} → {}", imageCount, pageDiagramPath);
                            } else {
                                logger.warn("Generated diagram for page {} is empty, skipping", imageCount);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to generate diagram for page {} of {}: {}", 
                                imageCount, attachmentName, e.getMessage(), e);
                        }
                    }
                }
            }
        }
        
        logger.info("Completed processing multi-page document {}: {} diagrams generated in {}", 
            attachmentName, imageCount, documentFolder);
    }
    
    /**
     * Checks if content exceeds token limit and prepares chunks if needed.
     * Returns null if content fits within token limit, otherwise returns list of chunks.
     * 
     * @param content The content to check and potentially chunk
     * @return List of chunks if content exceeds limit, null otherwise
     */
    private List<ChunkPreparation.Chunk> prepareChunksIfNeeded(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int tokenLimit = chunkPreparation.getTokenLimit();
        
        // Estimate tokens - rough estimate is 1 token per 4 characters
        int estimatedTokens = content.length() / 4;
        
        if (estimatedTokens <= tokenLimit) {
            logger.debug("Content fits within token limit ({} estimated tokens <= {} limit)", estimatedTokens, tokenLimit);
            return null;
        }
        
        logger.info("Content exceeds token limit ({} estimated tokens > {} limit), preparing chunks", estimatedTokens, tokenLimit);
        
        try {
            List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(Collections.singletonList(content));
            if (chunks.size() <= 1) {
                // Single chunk means content fits
                return null;
            }
            return chunks;
        } catch (IOException e) {
            logger.warn("Failed to prepare chunks: {}", e.getMessage());
            return null;
        }
    }
}
