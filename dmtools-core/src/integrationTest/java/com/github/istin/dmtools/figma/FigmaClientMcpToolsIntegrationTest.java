package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.figma.model.FigmaIcon;
import com.github.istin.dmtools.figma.model.FigmaIconsResult;
import com.github.istin.dmtools.figma.model.FigmaFileResponse;
import com.github.istin.dmtools.figma.model.FigmaNodesResponse;
import com.github.istin.dmtools.figma.model.FigmaFileDocument;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.io.File;

/**
 * Integration tests for FigmaClient MCP tools using clean models (no raw JSON processing)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FigmaClientMcpToolsIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FigmaClientMcpToolsIntegrationTest.class);
    
    // Replace with your test URL for local testing (DO NOT COMMIT)
    private static final String TEST_FIGMA_URL = "REPLACE_WITH_YOUR_TEST_URL";
    
    private BasicFigmaClient figmaClient;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize BasicFigmaClient for testing
        figmaClient = new BasicFigmaClient();
        logger.info("BasicFigmaClient initialized");
        logger.info("Using Figma base path: {}", figmaClient.getBasePath());
        logger.info("Using Figma API key: [SENSITIVE]");
    }

    @AfterEach 
    void tearDown() {
        logger.info("Integration tests completed for BasicFigmaClient MCP tools");
    }

    @Test
    @Order(1)
    @DisplayName("Test figma_get_icons using clean models")
    void testFigmaGetIcons() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_icons with URL: {}", TEST_FIGMA_URL);
        
        // Call the figma_get_icons method - now returns clean models!
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        
        assertNotNull(iconsResult, "Icons result should not be null");

        // Get the structured result using the models
        String fileId = iconsResult.getFileId();
        int totalIcons = iconsResult.getTotalIcons();
        List<FigmaIcon> icons = iconsResult.getIcons();
        
                logger.info("Found {} visual elements in file {}", totalIcons, fileId);
        assertEquals(totalIcons, icons.size(), "totalIcons should match icons list size");
        
        // Test each found visual element using smart categorization
        for (int i = 0; i < Math.min(icons.size(), 20); i++) { // Limit to first 20 for readability
            FigmaIcon icon = icons.get(i);

            // Verify visual element data using model methods
            assertNotNull(icon.getId(), "Visual element should have id");
            assertNotNull(icon.getName(), "Visual element should have name");
            assertNotNull(icon.getType(), "Visual element should have type");

            String iconId = icon.getId();
            String iconName = icon.getName();
            String iconType = icon.getType();
            double width = icon.getWidth();
            double height = icon.getHeight();
            String[] supportedFormats = icon.getSupportedFormats();
            boolean isVectorBased = icon.isVectorBased();
            String category = icon.getCategory();

            logger.info("Element {}: {} | {} | {}x{} | Vector: {} | Category: {} | Formats: {}",
                i + 1, iconName, iconType, width, height, isVectorBased, category,
                supportedFormats != null ? Arrays.toString(supportedFormats) : "none");
            
                                   // Verify dimensions are positive (components can be any size - icons, illustrations, UI elements)
                       assertTrue(width > 0, "Component width should be positive");
                       assertTrue(height > 0, "Component height should be positive");
                       // Note: Components can be large (illustrations, screens, etc.) so no upper limit check
            
            // Verify supported formats exist (but don't require any specific count)
            assertNotNull(supportedFormats, "Component should have supported formats array");
            
                                   // Log component information (no validation calls as per user guidance)
                       if (supportedFormats.length > 0) {
                           logger.info("  Supported formats: {}", Arrays.toString(supportedFormats));
                       } else {
                           logger.info("  No export formats defined for this component");
                       }
        }
        
                // Success message
        if (totalIcons > 0) {
            logger.info("✅ Successfully found and processed {} visual elements using smart categorization", totalIcons);
        } else {
            logger.info("ℹ️ No visual elements found in the current design scope");
        }

        logger.info("figma_get_icons test completed successfully");
    }

    @Test
    @Order(2)
    @DisplayName("Test figma_get_file_structure using clean models")
    void testGetFileStructure() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        // Test the figma_get_file_structure MCP method with clean models
        FigmaFileResponse fileResponse = figmaClient.getFileStructure(TEST_FIGMA_URL);
        
        assertNotNull(fileResponse);
        
        // Verify it's properly structured using the model
        if (fileResponse.hasNodesResponse()) {
            logger.info("Got specific node structure (nodes object found)");
            FigmaNodesResponse nodesResponse = fileResponse.getNodesResponse();
            assertNotNull(nodesResponse, "Nodes response should not be null");
            
            List<String> nodeIds = nodesResponse.getNodeIds();
            assertFalse(nodeIds.isEmpty(), "Should have at least one node");
            
            String firstNodeId = nodeIds.get(0);
            logger.info("Node ID: {}", firstNodeId);
            
            FigmaFileDocument document = nodesResponse.getNodeDocument(firstNodeId);
            if (document != null) {
                logger.info("Node name: {}", document.getName() != null ? document.getName() : "Unknown");
                logger.info("Node type: {}", document.getType() != null ? document.getType() : "Unknown");
            }
            
        } else if (fileResponse.hasDocument()) {
            logger.info("Got full file structure (document object found)");
            // This would be the full file structure
            assertNotNull(fileResponse.getName(), "File structure should contain name");
            logger.info("File name: {}", fileResponse.getName());
            
            FigmaFileDocument document = fileResponse.getDocument();
            assertNotNull(document, "Document should not be null");
            logger.info("Document type: {}", document.getType() != null ? document.getType() : "Unknown");
            
            if (document.hasChildren()) {
                List<FigmaFileDocument> children = document.getChildren();
                logger.info("Document has {} top-level children", children.size());
            }
        } else {
            fail("Response should contain either nodes or document structure");
        }
        
        logger.info("File structure retrieved successfully using clean models");
        
        logger.info("File structure test completed successfully");
    }

    @Test
    @Order(3)
    @DisplayName("Test figma_get_image_by_id")
    void testGetImageById() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_image_by_id");
        
        // First get icons to get a valid node ID
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        assertNotNull(iconsResult);
        
        List<FigmaIcon> icons = iconsResult.getIcons();
        if (!icons.isEmpty()) {
            FigmaIcon firstIcon = icons.get(0);
            String nodeId = firstIcon.getId();
            String[] formats = firstIcon.getSupportedFormats();
            
            if (formats != null && formats.length > 0) {
                String format = formats[0];
                
                String imageUrl = figmaClient.getImageById(TEST_FIGMA_URL, nodeId, format);
                
                if (imageUrl != null) {
                    assertTrue(imageUrl.startsWith("https://"), "Image URL should be a valid HTTPS URL");
                    logger.info("✅ Successfully got {} image URL: {}", format.toUpperCase(), imageUrl);
                } else {
                    logger.warn("Image URL not available for node {} in format {}", nodeId, format);
                }
            }
        }
        
        logger.info("figma_get_image_by_id test completed");
    }

    @Test
    @Order(4)
    @DisplayName("Test downloading first PNG icon as file")
    void testDownloadFirstPngIcon() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing download of first PNG icon as file");
        
        // Get icons to find one that supports PNG
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        assertNotNull(iconsResult);
        
        List<FigmaIcon> icons = iconsResult.getIcons();
        assertFalse(icons.isEmpty(), "Should have at least one icon");
        
        // Find first icon that supports PNG
        FigmaIcon pngIcon = null;
        for (FigmaIcon icon : icons) {
            String[] formats = icon.getSupportedFormats();
            if (formats != null) {
                for (String format : formats) {
                    if ("png".equals(format)) {
                        pngIcon = icon;
                        break;
                    }
                }
                if (pngIcon != null) break;
            }
        }
        
        assertNotNull(pngIcon, "Should find at least one icon that supports PNG format");
        
        String nodeId = pngIcon.getId();
        String iconName = pngIcon.getName();
        logger.info("Testing PNG download for icon: {} ({})", iconName, nodeId);
        
        // Download the PNG file - this MUST work since the icon was in our verified list
        File pngFile = figmaClient.downloadIconFile(TEST_FIGMA_URL, nodeId, "png");
        
        assertNotNull(pngFile, 
            String.format("PNG download returned null for icon '%s' (%s)! " +
                         "This icon was in our verified exportable list, so download must work.", 
                         iconName, nodeId));
        assertTrue(pngFile.exists(), 
            String.format("Downloaded PNG file should exist for icon '%s' (%s)", iconName, nodeId));
        assertTrue(pngFile.length() > 0, 
            String.format("Downloaded PNG file should not be empty for icon '%s' (%s)", iconName, nodeId));
        
        logger.info("✅ Successfully downloaded PNG file: {} ({} bytes)", pngFile.getName(), pngFile.length());
        
        // Clean up test file
        if (pngFile.exists()) {
            pngFile.delete();
            logger.info("Cleaned up test PNG file");
        }
        
        logger.info("PNG icon download test completed");
    }

    @Test
    @Order(5)
    @DisplayName("Test getting first SVG icon content as text")
    void testGetFirstSvgContent() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing retrieval of first SVG icon content as text");
        
        // Get icons to find one that supports SVG (vector-based)
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        assertNotNull(iconsResult);
        
        List<FigmaIcon> icons = iconsResult.getIcons();
        assertFalse(icons.isEmpty(), "Should have at least one icon");
        
        // Find first icon that supports SVG
        FigmaIcon svgIcon = null;
        for (FigmaIcon icon : icons) {
            String[] formats = icon.getSupportedFormats();
            if (formats != null) {
                for (String format : formats) {
                    if ("svg".equals(format)) {
                        svgIcon = icon;
                        break;
                    }
                }
                if (svgIcon != null) break;
            }
        }
        
        if (svgIcon == null) {
            logger.info("ℹ️ No SVG-compatible icons found in current design scope - skipping SVG test");
            return;
        }
        
        String nodeId = svgIcon.getId();
        String iconName = svgIcon.getName();
        logger.info("Testing SVG content retrieval for icon: {} ({})", iconName, nodeId);
        
        // Get the SVG content - this MUST work since the icon was in our verified list
        String svgContent = figmaClient.getSvgContent(TEST_FIGMA_URL, nodeId);
        
        assertNotNull(svgContent, 
            String.format("SVG content returned null for icon '%s' (%s)! " +
                         "This icon was in our verified exportable list and supports SVG, so this must work.", 
                         iconName, nodeId));
        assertFalse(svgContent.isEmpty(), 
            String.format("SVG content is empty for icon '%s' (%s)", iconName, nodeId));
        assertTrue(svgContent.contains("<svg"), 
            String.format("SVG content should contain <svg tag for icon '%s' (%s). Got: %s", 
                         iconName, nodeId, svgContent.substring(0, Math.min(100, svgContent.length()))));
        assertTrue(svgContent.length() > 50, 
            String.format("SVG content should be substantial for icon '%s' (%s), but got only %d characters", 
                         iconName, nodeId, svgContent.length()));
        
        logger.info("✅ Successfully retrieved SVG content ({} characters)", svgContent.length());
        logger.info("SVG preview: {}", svgContent.substring(0, Math.min(200, svgContent.length())) + "...");
        
        logger.info("SVG content retrieval test completed");
    }

    @Test
    @DisplayName("Explore JSON structure to find actual icons")
    public void testExploreJsonStructure() throws Exception {
        System.out.println("=== EXPLORING FIGMA JSON STRUCTURE ===");
        
        FigmaClient figmaClient = new BasicFigmaClient();
        FigmaFileResponse fileResponse = figmaClient.getFileStructure(TEST_FIGMA_URL);
        
        assertNotNull(fileResponse, "Should get file response");
        
        if (fileResponse.hasNodesResponse()) {
            FigmaNodesResponse nodesResponse = fileResponse.getNodesResponse();
            List<FigmaFileDocument> nodeDocuments = nodesResponse.getAllNodeDocuments();
            
            System.out.println("Found " + nodeDocuments.size() + " top-level node documents");
            
            for (FigmaFileDocument document : nodeDocuments) {
                System.out.println("=== EXPLORING NODE: " + document.getId() + " ===");
                exploreNodeStructure(document, 0);
            }
        } else {
            System.out.println("No nodes response found!");
        }
        
        // Force assertion to ensure we found something
        assertTrue(fileResponse.hasNodesResponse(), "Should have nodes response");
    }
    
    private void exploreNodeStructure(FigmaFileDocument node, int depth) {
        String indent = "  ".repeat(depth);
        String nodeId = node.getId();
        String nodeName = node.getName();
        String nodeType = node.getType();
        double width = node.getWidth();
        double height = node.getHeight();
        boolean isVectorBased = node.isVectorBased();
        
        // Log this node
        System.out.println(indent + "📁 " + nodeName + " | " + nodeType + " | " + width + "x" + height + " | Vector: " + isVectorBased + " | ID: " + nodeId);
        
        // Look for potential icons based on different criteria
        if (isLikelyActualIcon(node)) {
            System.out.println(indent + "🎯 *** POTENTIAL ICON FOUND *** 🎯");
        }
        
        // Recursively explore children, but limit depth to avoid too much output
        if (depth < 4 && node.hasChildren()) {
            List<FigmaFileDocument> children = node.getChildren();
            for (FigmaFileDocument child : children) {
                exploreNodeStructure(child, depth + 1);
            }
        } else if (node.hasChildren()) {
            System.out.println(indent + "... " + node.getChildren().size() + " more children (depth limit reached)");
        }
    }
    
    private boolean isLikelyActualIcon(FigmaFileDocument node) {
        String name = node.getName();
        String type = node.getType();
        double width = node.getWidth();
        double height = node.getHeight();
        
        // Look for small elements that could be icons
        boolean isSmall = width > 0 && height > 0 && width <= 64 && height <= 64;
        
        // Look for vector elements (common for icons)
        boolean isVector = "VECTOR".equals(type);
        
        // Look for icon-like names
        boolean hasIconName = name != null && (
            name.toLowerCase().contains("icon") ||
            name.toLowerCase().contains("chevron") ||
            name.toLowerCase().contains("arrow") ||
            name.toLowerCase().contains("home") ||
            name.toLowerCase().contains("contact") ||
            name.toLowerCase().contains("account") ||
            name.toLowerCase().contains("settings") ||
            name.toLowerCase().contains("exit") ||
            name.contains("🏠") || name.contains("📦") || name.contains("💬") || 
            name.contains("👤") || name.contains("⚙️") || name.contains("🔒")
        );
        
        // Look for nodes that have a simple ID structure (not complex nested)
        String nodeId = node.getId();
        boolean hasSimpleId = nodeId != null && !nodeId.contains(";") && nodeId.split(":").length <= 2;
        
        return (isSmall || isVector || hasIconName) && hasSimpleId;
    }
} 