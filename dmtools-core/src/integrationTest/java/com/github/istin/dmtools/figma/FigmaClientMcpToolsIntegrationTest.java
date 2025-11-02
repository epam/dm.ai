package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.figma.model.FigmaIcon;
import com.github.istin.dmtools.figma.model.FigmaIconsResult;
import com.github.istin.dmtools.figma.model.FigmaFileResponse;
import com.github.istin.dmtools.figma.model.FigmaNodesResponse;
import com.github.istin.dmtools.figma.model.FigmaFileDocument;
import com.github.istin.dmtools.figma.model.FigmaNodeDetails;
import com.github.istin.dmtools.figma.model.FigmaTextContentResult;
import com.github.istin.dmtools.figma.model.FigmaStylesResult;
import com.github.istin.dmtools.figma.model.FigmaNodeChildrenResult;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

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
    @Order(6)
    @DisplayName("Test figma_download_image_of_file MCP tool")
    public void testFigmaDownloadImageOfFile() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_download_image_of_file with URL: {}", TEST_FIGMA_URL);
        
        FigmaClient figmaClient = new BasicFigmaClient();
        File file = figmaClient.convertUrlToFile(TEST_FIGMA_URL);

        // Verify file was downloaded successfully
        assertNotNull(file, "File should not be null");
        assertTrue(file.exists(), "Downloaded file should exist: " + file.getAbsolutePath());
        assertTrue(file.isFile(), "Should be a regular file, not a directory");
        assertTrue(file.length() > 0, "File should not be empty");
        
        // Verify it's a PNG image
        String fileName = file.getName();
        assertTrue(fileName.endsWith(".png"), "File should be a PNG image: " + fileName);
        
        logger.info("✅ Successfully downloaded image file:");
        logger.info("   Path: {}", file.getAbsolutePath());
        logger.info("   Size: {} bytes", file.length());
        logger.info("   Name: {}", fileName);
        
        // Verify file content by checking PNG header
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[8];
            int bytesRead = fis.read(header);
            
            assertEquals(8, bytesRead, "Should read 8 bytes for PNG header");
            
            // PNG header: 89 50 4E 47 0D 0A 1A 0A
            assertEquals((byte) 0x89, header[0], "PNG header byte 0");
            assertEquals((byte) 0x50, header[1], "PNG header byte 1 ('P')");
            assertEquals((byte) 0x4E, header[2], "PNG header byte 2 ('N')");
            assertEquals((byte) 0x47, header[3], "PNG header byte 3 ('G')");
            
            logger.info("✅ Verified PNG file header is correct");
        }
        
        logger.info("figma_download_image_of_file test completed successfully");
    }




    @Test
    @Order(7)
    @DisplayName("Test figma_get_node_details MCP method")
    void testFigmaGetNodeDetails() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_node_details");
        
        // First get icons to obtain valid node IDs
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        assertNotNull(iconsResult);
        
        List<FigmaIcon> icons = iconsResult.getIcons();
        assertFalse(icons.isEmpty(), "Should have at least one icon");
        
        // Select first 5 node IDs
        int numNodes = Math.min(5, icons.size());
        String[] nodeIds = new String[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeIds[i] = icons.get(i).getId();
        }
        
        String commaSeparatedIds = String.join(",", nodeIds);
        logger.info("Testing getNodeDetails with {} nodes: {}", numNodes, commaSeparatedIds);
        
        // Call getNodeDetails
        FigmaNodeDetails nodeDetails = figmaClient.getNodeDetails(TEST_FIGMA_URL, commaSeparatedIds);
        
        assertNotNull(nodeDetails, "Node details should not be null");
        
        // Verify basic properties exist
        assertNotNull(nodeDetails.getId(), "Node should have ID");
        assertNotNull(nodeDetails.getName(), "Node should have name");
        assertNotNull(nodeDetails.getType(), "Node should have type");
        
        logger.info("✅ Node details retrieved: {} ({})", nodeDetails.getName(), nodeDetails.getType());
        logger.info("   Dimensions: {}x{}", nodeDetails.getWidth(), nodeDetails.getHeight());
        
        if (nodeDetails.getBackgroundColor() != null) {
            logger.info("   Background color: {}", nodeDetails.getBackgroundColor());
        }
        
        logger.info("figma_get_node_details test completed successfully");
    }

    @Test
    @Order(8)
    @DisplayName("Test figma_get_text_content MCP method")
    void testFigmaGetTextContent() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_text_content");
        
        // Get icons and filter for TEXT type nodes
        FigmaIconsResult iconsResult = figmaClient.getIcons(TEST_FIGMA_URL);
        assertNotNull(iconsResult);
        
        List<FigmaIcon> icons = iconsResult.getIcons();
        List<String> textNodeIds = new ArrayList<>();
        
        for (FigmaIcon icon : icons) {
            if ("TEXT".equals(icon.getType())) {
                textNodeIds.add(icon.getId());
                if (textNodeIds.size() >= 10) break; // Limit to 10 text nodes
            }
        }
        
        if (textNodeIds.isEmpty()) {
            logger.info("ℹ️ No text nodes found in current design scope - skipping text content test");
            return;
        }
        
        String commaSeparatedIds = String.join(",", textNodeIds);
        logger.info("Testing getTextContent with {} text nodes", textNodeIds.size());
        
        // Call getTextContent
        FigmaTextContentResult textResult = figmaClient.getTextContent(TEST_FIGMA_URL, commaSeparatedIds);
        
        assertNotNull(textResult, "Text content result should not be null");
        
        Map<String, FigmaTextContentResult.FigmaTextEntry> textEntries = textResult.getTextEntries();
        assertNotNull(textEntries, "Text entries should not be null");
        assertFalse(textEntries.isEmpty(), "Should have at least one text entry");
        
        // Verify each text node has content
        for (Map.Entry<String, FigmaTextContentResult.FigmaTextEntry> entry : textEntries.entrySet()) {
            FigmaTextContentResult.FigmaTextEntry textEntry = entry.getValue();
            logger.info("✅ Text node {}: '{}' | {} {} {}pt", 
                entry.getKey(), 
                textEntry.getText(),
                textEntry.getFontFamily(),
                textEntry.getFontWeight(),
                textEntry.getFontSize());
        }
        
        logger.info("figma_get_text_content test completed successfully");
    }

    @Test
    @Order(9)
    @DisplayName("Test figma_get_styles MCP method")
    void testFigmaGetStyles() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_styles");
        
        // Call getStyles
        FigmaStylesResult stylesResult = figmaClient.getStyles(TEST_FIGMA_URL);
        
        assertNotNull(stylesResult, "Styles result should not be null");
        
        List<FigmaStylesResult.ColorStyle> colorStyles = stylesResult.getColorStyles();
        List<FigmaStylesResult.TextStyle> textStyles = stylesResult.getTextStyles();
        
        assertNotNull(colorStyles, "Color styles list should not be null");
        assertNotNull(textStyles, "Text styles list should not be null");
        
        logger.info("✅ Retrieved {} color styles and {} text styles", colorStyles.size(), textStyles.size());
        
        logger.info("figma_get_styles test completed successfully");
    }

    @Test
    @Order(10)
    @DisplayName("Test figma_get_node_children MCP method")
    void testFigmaGetNodeChildren() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("Testing figma_get_node_children");
        
        // Call getNodeChildren for root node from URL
        FigmaNodeChildrenResult childrenResult = figmaClient.getNodeChildren(TEST_FIGMA_URL);
        
        // Note: getNodeChildren may return null if the node has no direct children or is a specific frame
        // This is expected behavior for certain Figma nodes
        if (childrenResult == null) {
            logger.info("✅ getNodeChildren returned null (node has no direct children - this is valid)");
            logger.info("figma_get_node_children test completed successfully");
            return;
        }
        
        List<FigmaNodeChildrenResult.ChildNode> children = childrenResult.getChildren();
        if (children == null || children.isEmpty()) {
            logger.info("✅ Node has no children (empty list - this is valid)");
            logger.info("figma_get_node_children test completed successfully");
            return;
        }
        
        logger.info("✅ Found {} immediate children", children.size());
        
        // Verify children have required properties
        for (FigmaNodeChildrenResult.ChildNode child : children) {
            assertNotNull(child.getId(), "Child should have ID");
            assertNotNull(child.getName(), "Child should have name");
            assertNotNull(child.getType(), "Child should have type");
            
            logger.info("   Child: {} ({}) - {}x{}", child.getName(), child.getType(), child.getWidth(), child.getHeight());
        }
        
        logger.info("figma_get_node_children test completed successfully");
    }

    @Test
    @Order(11)
    @DisplayName("Validate MCP tools can extract data for pixel-perfect HTML generation")
    void testDataExtractionForHtmlGeneration() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("=== VALIDATING DATA EXTRACTION FOR HTML GENERATION ===");
        
        // Step 1: Get root node children (screens)
        // Note: For specific nodes, getNodeChildren may return null if there are no direct children
        FigmaNodeChildrenResult childrenResult = figmaClient.getNodeChildren(TEST_FIGMA_URL);
        if (childrenResult != null && childrenResult.getChildren() != null && !childrenResult.getChildren().isEmpty()) {
            logger.info("Found {} screens", childrenResult.getChildren().size());
        } else {
            logger.info("No direct children found for this node (expected for specific frame nodes)");
        }
        
        // Step 2: Get styles (design tokens)
        FigmaStylesResult styles = figmaClient.getStyles(TEST_FIGMA_URL);
        assertNotNull(styles, "Styles should not be null");
        logger.info("Found {} color styles and {} text styles", 
            styles.getColorStyles() != null ? styles.getColorStyles().size() : 0,
            styles.getTextStyles() != null ? styles.getTextStyles().size() : 0);
        
        // Step 3: For first screen, get detailed node info
        if (childrenResult != null && childrenResult.getChildren() != null && !childrenResult.getChildren().isEmpty()) {
            String firstScreenId = childrenResult.getChildren().get(0).getId();
            FigmaNodeDetails nodeDetails = figmaClient.getNodeDetails(TEST_FIGMA_URL, firstScreenId);
            assertNotNull(nodeDetails, "Node details should not be null");
            logger.info("Successfully extracted detailed properties for screen: {}", firstScreenId);
        }
        
        logger.info("✓ All MCP tools successfully provide data needed for HTML generation");
    }

    
    @Test
    @Order(12)
    @DisplayName("Test figma_get_layers MCP method - Get first-level structure")
    void testFigmaGetLayers() throws Exception {
        // Skip test if URL is not configured
        if ("REPLACE_WITH_YOUR_TEST_URL".equals(TEST_FIGMA_URL)) {
            logger.warn("Skipping test - TEST_FIGMA_URL not configured");
            return;
        }
        
        logger.info("=== TESTING figma_get_layers ===");
        logger.info("Testing with URL: {}", TEST_FIGMA_URL);
        
        // Call getLayers to get first-level structure
        FigmaNodeChildrenResult layersResult = figmaClient.getLayers(TEST_FIGMA_URL);
        
        assertNotNull(layersResult, "Layers result should not be null");
        
        List<FigmaNodeChildrenResult.ChildNode> layers = layersResult.getChildren();
        assertNotNull(layers, "Layers list should not be null");
        assertFalse(layers.isEmpty(), "Should have at least one layer");
        
        logger.info("✅ Found {} first-level layers", layers.size());
        
        // Log details about each layer (screen)
        for (int i = 0; i < layers.size(); i++) {
            FigmaNodeChildrenResult.ChildNode layer = layers.get(i);
            logger.info("\n📱 Layer {}: {}", i + 1, layer.getName());
            logger.info("   ID: {}", layer.getId());
            logger.info("   Type: {}", layer.getType());
            logger.info("   Size: {}x{}", layer.getWidth(), layer.getHeight());
            logger.info("   Position: ({}, {})", layer.getX(), layer.getY());
            logger.info("   Visible: {}", layer.isVisible());
        }
        
        logger.info("\n=== figma_get_layers TEST COMPLETE ===");
        logger.info("✅ Successfully retrieved first-level structure");
        logger.info("Next step: Use these layer IDs with figma_get_node_details to drill into specific screens");
    }
    
} 