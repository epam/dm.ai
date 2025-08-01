package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.List;

public class FigmaIcon extends JSONModel {

    public FigmaIcon() {
        super();
    }

    public FigmaIcon(String json) {
        super(json);
    }

    public FigmaIcon(JSONObject json) {
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

    public double getWidth() {
        return getDouble("width") != null ? getDouble("width") : 0.0;
    }

    public double getHeight() {
        return getDouble("height") != null ? getDouble("height") : 0.0;
    }

    public String[] getSupportedFormats() {
        return getStringArray("supportedFormats");
    }

    public boolean isVectorBased() {
        return getBoolean("isVectorBased");
    }

    public String getCategory() {
        return getString("category");
    }

    // Static factory method to create FigmaIcon from node data
    public static FigmaIcon fromNode(String nodeId, String nodeName, String nodeType, double width, double height, List<String> supportedFormats, boolean isVectorBased, String category) {
        FigmaIcon icon = new FigmaIcon();
        icon.set("id", nodeId);
        icon.set("name", nodeName);
        icon.set("type", nodeType);
        icon.set("width", width);
        icon.set("height", height);
        icon.setArray("supportedFormats", supportedFormats.toArray(new String[0]));
        icon.set("isVectorBased", isVectorBased);
        icon.set("category", category);
        return icon;
    }
} 