package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FigmaNodesResponse extends JSONModel {

    public FigmaNodesResponse() {
        super();
    }

    public FigmaNodesResponse(String json) {
        super(json);
    }

    public FigmaNodesResponse(JSONObject json) {
        super(json);
    }

    /**
     * Get all node documents from the nodes response
     * Each node in the response has its own document structure
     */
    public List<FigmaFileDocument> getAllNodeDocuments() {
        List<FigmaFileDocument> documents = new ArrayList<>();
        
        JSONObject jo = getJSONObject();
        if (jo != null) {
            Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                String nodeId = keys.next();
                JSONObject nodeData = jo.optJSONObject(nodeId);
                
                if (nodeData != null && nodeData.has("document")) {
                    // Create a temporary model wrapper to use getModel() properly
                    JSONModel nodeWrapper = new JSONModel(nodeData);
                    FigmaFileDocument document = nodeWrapper.getModel(FigmaFileDocument.class, "document");
                    if (document != null) {
                        documents.add(document);
                    }
                }
            }
        }
        
        return documents;
    }

    /**
     * Get a specific node document by node ID
     */
    public FigmaFileDocument getNodeDocument(String nodeId) {
        JSONObject nodeData = getJSONObject(nodeId);
        if (nodeData != null && nodeData.has("document")) {
            // Create a temporary model wrapper to use getModel() properly
            JSONModel nodeWrapper = new JSONModel(nodeData);
            return nodeWrapper.getModel(FigmaFileDocument.class, "document");
        }
        return null;
    }

    /**
     * Get all node IDs in this response
     */
    public List<String> getNodeIds() {
        List<String> nodeIds = new ArrayList<>();
        
        JSONObject jo = getJSONObject();
        if (jo != null) {
            Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                nodeIds.add(keys.next());
            }
        }
        
        return nodeIds;
    }
} 