package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Model for detailed node properties from Figma API.
 * Contains colors, fonts, text, dimensions, and styling information.
 */
public class FigmaNodeDetails extends JSONModel {

    public FigmaNodeDetails() {
        super();
    }

    public FigmaNodeDetails(String json) {
        super(json);
    }

    public FigmaNodeDetails(JSONObject json) {
        super(json);
    }

    // Basic properties
    public String getId() {
        return getString("id");
    }

    public String getName() {
        return getString("name");
    }

    public String getType() {
        return getString("type");
    }

    // Dimensions and position
    public Double getWidth() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("width", 0) : 0.0;
    }

    public Double getHeight() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("height", 0) : 0.0;
    }

    public Double getX() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("x", 0) : 0.0;
    }

    public Double getY() {
        JSONObject bounds = getAbsoluteBoundingBox();
        return bounds != null ? bounds.optDouble("y", 0) : 0.0;
    }

    public JSONObject getAbsoluteBoundingBox() {
        return getJSONObject("absoluteBoundingBox");
    }

    // Colors and fills
    public JSONArray getFills() {
        return getJSONArray("fills");
    }

    public String getBackgroundColor() {
        JSONArray fills = getFills();
        if (fills != null && fills.length() > 0) {
            JSONObject firstFill = fills.optJSONObject(0);
            if (firstFill != null && "SOLID".equals(firstFill.optString("type"))) {
                JSONObject color = firstFill.optJSONObject("color");
                if (color != null) {
                    return rgbaToHex(
                        color.optDouble("r", 0),
                        color.optDouble("g", 0),
                        color.optDouble("b", 0),
                        color.optDouble("a", 1)
                    );
                }
            }
        }
        return null;
    }

    public JSONArray getStrokes() {
        return getJSONArray("strokes");
    }

    public String getStrokeColor() {
        JSONArray strokes = getStrokes();
        if (strokes != null && strokes.length() > 0) {
            JSONObject firstStroke = strokes.optJSONObject(0);
            if (firstStroke != null && "SOLID".equals(firstStroke.optString("type"))) {
                JSONObject color = firstStroke.optJSONObject("color");
                if (color != null) {
                    return rgbaToHex(
                        color.optDouble("r", 0),
                        color.optDouble("g", 0),
                        color.optDouble("b", 0),
                        color.optDouble("a", 1)
                    );
                }
            }
        }
        return null;
    }

    // Effects (shadows, etc.)
    public JSONArray getEffects() {
        return getJSONArray("effects");
    }

    // Text properties
    public String getCharacters() {
        return getString("characters");
    }

    public JSONObject getStyle() {
        return getJSONObject("style");
    }

    public String getFontFamily() {
        JSONObject style = getStyle();
        return style != null ? style.optString("fontFamily", null) : null;
    }

    public Double getFontSize() {
        JSONObject style = getStyle();
        return style != null ? style.optDouble("fontSize", 0) : 0.0;
    }

    public Integer getFontWeight() {
        JSONObject style = getStyle();
        return style != null ? style.optInt("fontWeight", 400) : 400;
    }

    public Double getLineHeightPx() {
        JSONObject style = getStyle();
        if (style != null && style.has("lineHeightPx")) {
            return style.optDouble("lineHeightPx", 0);
        }
        return null;
    }

    public Double getLetterSpacing() {
        JSONObject style = getStyle();
        return style != null ? style.optDouble("letterSpacing", 0) : 0.0;
    }

    public String getTextAlignHorizontal() {
        JSONObject style = getStyle();
        return style != null ? style.optString("textAlignHorizontal", "LEFT") : "LEFT";
    }

    // Border/Corner properties
    public Double getCornerRadius() {
        return getDouble("cornerRadius");
    }

    public Double getStrokeWeight() {
        return getDouble("strokeWeight");
    }

    // Opacity
    public Double getOpacity() {
        Double opacity = getDouble("opacity");
        return opacity != null ? opacity : 1.0;
    }

    // Helper method to convert RGBA to hex color
    private String rgbaToHex(double r, double g, double b, double a) {
        int red = (int) Math.round(r * 255);
        int green = (int) Math.round(g * 255);
        int blue = (int) Math.round(b * 255);
        
        if (a < 1.0) {
            int alpha = (int) Math.round(a * 255);
            return String.format("#%02x%02x%02x%02x", red, green, blue, alpha);
        } else {
            return String.format("#%02x%02x%02x", red, green, blue);
        }
    }

    // Children
    public boolean hasChildren() {
        JSONArray children = getJSONArray("children");
        return children != null && children.length() > 0;
    }

    public List<FigmaNodeDetails> getChildren() {
        return getModels(FigmaNodeDetails.class, "children");
    }
}



