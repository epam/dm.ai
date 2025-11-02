package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.List;

/**
 * Model for node children information from Figma API.
 * Contains basic information about immediate children (non-recursive).
 */
public class FigmaNodeChildrenResult extends JSONModel {

    public FigmaNodeChildrenResult() {
        super();
    }

    public FigmaNodeChildrenResult(String json) {
        super(json);
    }

    public FigmaNodeChildrenResult(JSONObject json) {
        super(json);
    }

    /**
     * Get the parent node ID
     */
    public String getParentNodeId() {
        return getString("parentNodeId");
    }

    /**
     * Get list of children
     */
    public List<ChildNode> getChildren() {
        return getModels(ChildNode.class, "children");
    }

    /**
     * Get total count of children
     */
    public Integer getChildCount() {
        List<ChildNode> children = getChildren();
        return children != null ? children.size() : 0;
    }

    /**
     * Inner class representing a child node with basic information
     */
    public static class ChildNode extends JSONModel {
        
        public ChildNode() {
            super();
        }

        public ChildNode(JSONObject json) {
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

        public Double getWidth() {
            return getDouble("width");
        }

        public Double getHeight() {
            return getDouble("height");
        }

        public Double getX() {
            return getDouble("x");
        }

        public Double getY() {
            return getDouble("y");
        }

        public Boolean isVisible() {
            return getBoolean("visible");
        }
    }

    /**
     * Static factory method to create result from parent node and children
     */
    public static FigmaNodeChildrenResult create(String parentNodeId, List<ChildNode> children) {
        FigmaNodeChildrenResult result = new FigmaNodeChildrenResult();
        result.set("parentNodeId", parentNodeId);
        
        org.json.JSONArray childrenArray = new org.json.JSONArray();
        for (ChildNode child : children) {
            childrenArray.put(child.getJSONObject());
        }
        result.set("children", childrenArray);
        
        return result;
    }
}

