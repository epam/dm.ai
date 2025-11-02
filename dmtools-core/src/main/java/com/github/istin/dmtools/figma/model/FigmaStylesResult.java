package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.List;

/**
 * Model for design tokens (color and text styles) from Figma API.
 */
public class FigmaStylesResult extends JSONModel {

    public FigmaStylesResult() {
        super();
    }

    public FigmaStylesResult(String json) {
        super(json);
    }

    public FigmaStylesResult(JSONObject json) {
        super(json);
    }

    /**
     * Get all color styles
     */
    public List<ColorStyle> getColorStyles() {
        return getModels(ColorStyle.class, "colorStyles");
    }

    /**
     * Get all text styles
     */
    public List<TextStyle> getTextStyles() {
        return getModels(TextStyle.class, "textStyles");
    }

    /**
     * Inner class representing a color style
     */
    public static class ColorStyle extends JSONModel {
        
        public ColorStyle() {
            super();
        }

        public ColorStyle(JSONObject json) {
            super(json);
        }

        public String getName() {
            return getString("name");
        }

        public String getColor() {
            return getString("color");
        }

        public String getDescription() {
            return getString("description");
        }

        public String getStyleType() {
            return getString("styleType");
        }
    }

    /**
     * Inner class representing a text style
     */
    public static class TextStyle extends JSONModel {
        
        public TextStyle() {
            super();
        }

        public TextStyle(JSONObject json) {
            super(json);
        }

        public String getName() {
            return getString("name");
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

        public String getDescription() {
            return getString("description");
        }

        public String getStyleType() {
            return getString("styleType");
        }
    }
}

