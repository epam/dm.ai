package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FigmaFileResponse extends JSONModel {

    public FigmaFileResponse() {
        super();
    }

    public FigmaFileResponse(String json) {
        super(json);
    }

    public FigmaFileResponse(JSONObject json) {
        super(json);
    }

    public String getRole() {
        return getString("role");
    }

    public String getName() {
        return getString("name");
    }

    public String getLastModified() {
        return getString("lastModified");
    }

    public String getLinkAccess() {
        return getString("linkAccess");
    }

    public String getEditorType() {
        return getString("editorType");
    }

    public String getVersion() {
        return getString("version");
    }

    public String getThumbnailUrl() {
        return getString("thumbnailUrl");
    }

    // For single node responses (when using node-id)
    public boolean hasNodesResponse() {
        return getJSONObject("nodes") != null;
    }

    public FigmaNodesResponse getNodesResponse() {
        return getModel(FigmaNodesResponse.class, "nodes");
    }

    // For full file responses
    public boolean hasDocument() {
        return getJSONObject("document") != null;
    }

    public FigmaFileDocument getDocument() {
        return getModel(FigmaFileDocument.class, "document");
    }

        /**
     * Find all exportable images in this file response, regardless of whether it's a nodes or document response.
     * This includes icons, graphics, components, and other visual elements that can be exported.
     */
    public List<FigmaIcon> findAllComponents() {
        List<FigmaIcon> components = new ArrayList<>();

        if (hasNodesResponse()) {
            // Handle specific node structure
            FigmaNodesResponse nodesResponse = getNodesResponse();
            List<FigmaFileDocument> nodeDocuments = nodesResponse.getAllNodeDocuments();

            for (FigmaFileDocument document : nodeDocuments) {
                String nodeId = document.getId();
                document.findComponentsRecursively(nodeId, components);
            }

        } else if (hasDocument()) {
            // Handle full file structure
            FigmaFileDocument document = getDocument();
            document.findComponentsRecursively("document", components);
        }

        return components;
    }
} 