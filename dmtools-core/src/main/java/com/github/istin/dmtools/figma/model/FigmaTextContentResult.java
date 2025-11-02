package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Model for text content extraction results from Figma API.
 * Contains a map of nodeId to text properties (characters, font, size, weight, etc.)
 */
public class FigmaTextContentResult extends JSONModel {

    public FigmaTextContentResult() {
        super();
    }

    public FigmaTextContentResult(String json) {
        super(json);
    }

    public FigmaTextContentResult(JSONObject json) {
        super(json);
    }

    /**
     * Get all text entries as a map of nodeId -> FigmaTextEntry
     */
    public Map<String, FigmaTextEntry> getTextEntries() {
        Map<String, FigmaTextEntry> entries = new HashMap<>();
        JSONObject textNodes = getJSONObject("textNodes");
        
        if (textNodes != null) {
            Iterator<String> keys = textNodes.keys();
            while (keys.hasNext()) {
                String nodeId = keys.next();
                JSONObject textData = textNodes.optJSONObject(nodeId);
                if (textData != null) {
                    entries.put(nodeId, new FigmaTextEntry(textData));
                }
            }
        }
        
        return entries;
    }

    /**
     * Get text entry for a specific node ID
     */
    public FigmaTextEntry getTextEntry(String nodeId) {
        JSONObject textNodes = getJSONObject("textNodes");
        if (textNodes != null && textNodes.has(nodeId)) {
            return new FigmaTextEntry(textNodes.getJSONObject(nodeId));
        }
        return null;
    }

    /**
     * Inner class representing a single text entry
     */
    public static class FigmaTextEntry extends JSONModel {
        
        public FigmaTextEntry() {
            super();
        }

        public FigmaTextEntry(JSONObject json) {
            super(json);
        }

        public String getText() {
            return getString("text");
        }

        public String getFontFamily() {
            return getString("fontFamily");
        }

        public Double getFontSize() {
            return getDouble("fontSize");
        }

        public Integer getFontWeight() {
            return getInt("fontWeight");
        }

        public Double getLineHeight() {
            return getDouble("lineHeight");
        }

        public Double getLetterSpacing() {
            return getDouble("letterSpacing");
        }

        public String getColor() {
            return getString("color");
        }

        public String getTextAlign() {
            return getString("textAlign");
        }

        public String getTextDecoration() {
            return getString("textDecoration");
        }

        public String getTextTransform() {
            return getString("textTransform");
        }
        
        /**
         * Get character-level style overrides array.
         * Used for mixed-style text where different characters have different styles.
         * Example: "$100.99" where ".99" might be smaller
         */
        public int[] getCharacterStyleOverrides() {
            org.json.JSONArray array = getJSONArray("characterStyleOverrides");
            if (array == null) return null;
            
            int[] overrides = new int[array.length()];
            for (int i = 0; i < array.length(); i++) {
                overrides[i] = array.optInt(i, 0);
            }
            return overrides;
        }
        
        /**
         * Get style override table.
         * Maps override indices to actual style properties.
         * Example: {"1": {"fontSize": 14, "fontWeight": 400}}
         */
        public org.json.JSONObject getStyleOverrideTable() {
            return getJSONObject("styleOverrideTable");
        }
        
        /**
         * Check if this text has mixed character-level styling
         */
        public boolean hasMixedStyling() {
            return getCharacterStyleOverrides() != null && getCharacterStyleOverrides().length > 0;
        }
    }

    /**
     * Static factory method to create result from nodes response
     */
    public static FigmaTextContentResult create(Map<String, FigmaTextEntry> textEntries) {
        FigmaTextContentResult result = new FigmaTextContentResult();
        JSONObject textNodes = new JSONObject();
        
        for (Map.Entry<String, FigmaTextEntry> entry : textEntries.entrySet()) {
            textNodes.put(entry.getKey(), entry.getValue().getJSONObject());
        }
        
        result.set("textNodes", textNodes);
        return result;
    }
}

