package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class FigmaIconsResult extends JSONModel {

    public FigmaIconsResult() {
        super();
    }

    public FigmaIconsResult(String json) {
        super(json);
    }

    public FigmaIconsResult(JSONObject json) {
        super(json);
    }

    public String getFileId() {
        return getString("fileId");
    }

    public int getTotalIcons() {
        return getInt("totalIcons");
    }

    public List<FigmaIcon> getIcons() {
        return getModels(FigmaIcon.class, "icons");
    }

    // Static factory method to create result
    public static FigmaIconsResult create(String fileId, List<FigmaIcon> icons) {
        FigmaIconsResult result = new FigmaIconsResult();
        result.set("fileId", fileId);
        result.set("totalIcons", icons.size());
        
        JSONArray iconArray = new JSONArray();
        for (FigmaIcon icon : icons) {
            iconArray.put(icon.getJSONObject());
        }
        result.set("icons", iconArray);
        
        return result;
    }
} 