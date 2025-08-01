package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FigmaFileDocument extends JSONModel {

    public FigmaFileDocument() {
        super();
    }

    public FigmaFileDocument(String json) {
        super(json);
    }

    public FigmaFileDocument(JSONObject json) {
        super(json);
    }

    public String getId() {
        return getString("id");
    }

    public String getName() {
        return getString("name");
    }

    public String getType() {
        return getString("type");
    }

    public boolean isVisible() {
        return getBoolean("visible");
    }
    
    public double getOpacity() {
        return getDouble("opacity") != null ? getDouble("opacity") : 1.0; // Default to fully opaque
    }
    
    public boolean hasExportSettings() {
        JSONArray exportSettings = getJSONArray("exportSettings");
        return exportSettings != null && exportSettings.length() > 0;
    }

    public JSONObject getAbsoluteBoundingBox() {
        return getJSONObject("absoluteBoundingBox");
    }

    public double getWidth() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("width", 0) : 0;
    }

    public double getHeight() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("height", 0) : 0;
    }

    public boolean hasFills() {
        return getJSONArray("fills") != null;
    }

    public boolean hasStrokes() {
        return getJSONArray("strokes") != null;
    }

    public boolean hasChildren() {
        JSONArray children = getJSONArray("children");
        return children != null && children.length() > 0;
    }

    public List<FigmaFileDocument> getChildren() {
        return getModels(FigmaFileDocument.class, "children");
    }

        /**
     * Check if this node is an exportable visual element.
     * Following the Figma API documentation: nodes that are invisible or have 0% opacity cannot be rendered.
     * Focuses on actual visual elements (VECTOR, RECTANGLE, etc.) to avoid complex nested component IDs.
     */
    public boolean isExportableVisualElement() {
        String nodeType = getType();
        
        // Focus on actual visual elements that can be directly exported
        // Include COMPONENT/INSTANCE for actual icons, but complex nested IDs are filtered above
        boolean isExportableType = false;
        switch (nodeType) {
            case "VECTOR":
            case "BOOLEAN_OPERATION":
            case "RECTANGLE":
            case "ELLIPSE":
            case "POLYGON":
            case "STAR":
            case "LINE":
            case "FRAME":
            case "GROUP":
            case "COMPONENT":
            case "INSTANCE":
            case "COMPONENT_SET":
            case "TEXT":
                isExportableType = true;
                break;
            default:
                return false;
        }
        
        if (!isExportableType) {
            return false;
        }
        
        // Filter out only the EXTREMELY complex nested IDs with multiple semicolons
        // Real icons have IDs like "I23275:360348;21179:261321" (2 parts) which should be kept
        // Only filter IDs with 3+ semicolon-separated parts like "I23275:360346;4006:42414;378:5885;38:2108"
        String nodeId = getId();
        if (nodeId != null && nodeId.contains(";")) {
            String[] parts = nodeId.split(";");
            if (parts.length >= 4) { // 4+ parts = too complex
                return false;
            }
        }
        
        // Check if node is actually renderable (as per Figma API docs)
        // Note: visible property might not be present in response, defaulting to true
        // Only filter out if explicitly set to false
        if (getJSONObject().has("visible") && !isVisible()) {
            return false;
        }
        
        // Check opacity - 0% opacity nodes cannot be rendered  
        // Only filter out if explicitly set to 0 or very low value
        if (getJSONObject().has("opacity")) {
            double opacity = getOpacity();
            if (opacity < 0.01) { // Nearly invisible
                return false;
            }
        }
        
        // Check if node has actual visual content (bounds)
        // Only require positive dimensions, no maximum limit
        if (getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        
        // Focus on actual icon-sized elements based on the JSON structure analysis
        // Real icons from the structure are 16x16, 24x24, 32x32
        // Filter out very large containers (>200px) that are clearly not individual icons
        double width = getWidth();
        double height = getHeight();
        
        // Skip very large container frames but keep medium-sized elements
        if (width > 200 && height > 200 && ("FRAME".equals(nodeType) || "GROUP".equals(nodeType))) {
            return false; // Skip large UI containers
        }
        
        return true;
    }
    


    /**
     * Get supported export formats for this node type
     */
    public List<String> getSupportedFormats() {
        List<String> formats = new ArrayList<>();
        String nodeType = getType();
        
        // All visible nodes support PNG and JPG
        formats.add("png");
        formats.add("jpg");
        
        // Vector-based nodes also support SVG
        if (isVectorBased()) {
            formats.add("svg");
        }
        
        // Frames and components can be exported as PDF
        if ("FRAME".equals(nodeType) || "COMPONENT".equals(nodeType) || "COMPONENT_SET".equals(nodeType)) {
            formats.add("pdf");
        }
        
        return formats;
    }

    /**
     * Check if this node type is vector-based (supports SVG export)
     */
    public boolean isVectorBased() {
        String nodeType = getType();
        return "VECTOR".equals(nodeType) || "BOOLEAN_OPERATION".equals(nodeType) || 
               "RECTANGLE".equals(nodeType) || "ELLIPSE".equals(nodeType) || 
               "POLYGON".equals(nodeType) || "STAR".equals(nodeType) || 
               "LINE".equals(nodeType);
    }

        /**
     * Recursively find exportable visual elements in this node and its children.
     * Focuses on actual visual elements rather than complex component references.
     */
    public void findComponentsRecursively(String nodeId, List<FigmaIcon> components) {
        // Check if this node is an exportable visual element
        if (isExportableVisualElement()) {
            String actualId = getId() != null ? getId() : nodeId;
            String nodeName = getName() != null ? getName() : "";
            String nodeType = getType() != null ? getType() : "unknown";
            double width = getWidth();
            double height = getHeight();
            List<String> supportedFormats = getSupportedFormats();
            boolean isVectorBased = isVectorBased();

            String category = getElementCategory();
            FigmaIcon component = FigmaIcon.fromNode(actualId, nodeName, nodeType, width, height, supportedFormats, isVectorBased, category);
            components.add(component);
        }

        // Recursively process children
        if (hasChildren()) {
            List<FigmaFileDocument> children = getChildren();
            for (int i = 0; i < children.size(); i++) {
                FigmaFileDocument child = children.get(i);
                String childId = child.getId() != null ? child.getId() : nodeId + "_child_" + i;
                child.findComponentsRecursively(childId, components);
            }
        }
    }
    
    /**
     * Determine if this element is likely a small icon based on name, type, and size.
     * Uses patterns visible in Figma's hierarchy like "Icon", "Chevron", etc.
     */
    public boolean isLikelyIcon() {
        String name = getName();
        String type = getType();
        double width = getWidth();
        double height = getHeight();
        
        // Check name patterns that suggest icons
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("icon") || lowerName.contains("chevron") || 
                lowerName.contains("arrow") || lowerName.contains("button") ||
                lowerName.contains("exit") || lowerName.contains("badge") ||
                lowerName.matches(".*[♣♠♥♦🏠📦💬👤⚙️🔒😊🤝ℹ️].*")) { // Emoji/symbol patterns
                return true;
            }
        }
        
        // COMPONENT/INSTANCE types that are icon-sized are likely icons 
        // Based on JSON analysis: real icons are 16x16, 24x24, 32x32
        if (("COMPONENT".equals(type) || "INSTANCE".equals(type)) && 
            width > 0 && height > 0 && width <= 48 && height <= 48) {
            return true;
        }
        
        // VECTOR types are often the actual icon content (seen inside INSTANCE containers)
        // Based on JSON analysis: vectors can be 13x13, 20x20, etc.
        if ("VECTOR".equals(type) && width > 0 && height > 0 && width <= 64 && height <= 64) {
            return true;
        }
        
        // Small geometric shapes are likely icons
        if (("RECTANGLE".equals(type) || "ELLIPSE".equals(type)) && 
            width > 0 && height > 0 && width <= 50 && height <= 50) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Determine if this element is likely an illustration based on name, type, and size.
     * Uses patterns visible in Figma's hierarchy.
     */
    public boolean isLikelyIllustration() {
        String name = getName();
        String type = getType();
        double width = getWidth();
        double height = getHeight();
        
        // Check name patterns that suggest illustrations or containers
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("illustration") || lowerName.contains("graphic") ||
                lowerName.contains("image") || lowerName.contains("master") ||
                lowerName.contains("header") || lowerName.contains("section") ||
                lowerName.contains("tab") && lowerName.contains("services")) {
                return true;
            }
        }
        
        // Large FRAME elements are typically containers/illustrations (like full screens or sections)
        if ("FRAME".equals(type) && width > 200 && height > 100) {
            return true;
        }
        
        // Large GROUP elements are likely illustrations
        if ("GROUP".equals(type) && width > 100 && height > 100) {
            return true;
        }
        
        // Large vectors are likely illustrations
        if ("VECTOR".equals(type) && width > 100 && height > 100) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a category for this visual element based on its characteristics.
     */
    public String getElementCategory() {
        if (isLikelyIcon()) {
            return "icon";
        } else if (isLikelyIllustration()) {
            return "illustration";
        } else if ("TEXT".equals(getType())) {
            return "text";
        } else {
            return "graphic"; // Generic visual element
        }
    }
} 