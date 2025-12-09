package com.github.istin.dmtools.microsoft.ado.model;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an Azure DevOps work item attachment.
 * 
 * Attachments can come from:
 * 1. relations array with rel="AttachedFile"
 * 2. Embedded images in description HTML
 */
public class Attachment extends JSONModel implements IAttachment {

    private String url;
    private String name;

    public Attachment() {
    }

    public Attachment(String json) throws JSONException {
        super(json);
    }

    public Attachment(JSONObject json) {
        super(json);
    }

    /**
     * Create an attachment from URL and name (for embedded images).
     */
    public Attachment(String url, String name) {
        this.url = url;
        this.name = name;
    }

    /**
     * Create attachment from a relation object.
     * Expected format:
     * {
     *   "rel": "AttachedFile",
     *   "url": "https://dev.azure.com/{org}/{project}/_apis/wit/attachments/{id}",
     *   "attributes": {
     *     "name": "filename.ext"
     *   }
     * }
     */
    public static Attachment fromRelation(JSONObject relation) {
        if (relation == null) {
            return null;
        }

        String rel = relation.optString("rel");
        if (!"AttachedFile".equals(rel)) {
            return null; // Not an attachment relation
        }

        String url = relation.optString("url");
        JSONObject attributes = relation.optJSONObject("attributes");
        String name = attributes != null ? attributes.optString("name") : null;

        if (url == null || url.isEmpty()) {
            return null;
        }

        // Extract filename from URL if name is not provided
        if (name == null || name.isEmpty()) {
            name = extractFileNameFromUrl(url);
        }

        Attachment attachment = new Attachment();
        attachment.url = url;
        attachment.name = name;
        return attachment;
    }

    /**
     * Extract filename from attachment URL.
     * ADO attachment URLs typically contain fileName parameter.
     */
    private static String extractFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "attachment";
        }

        // Try to extract from fileName parameter
        if (url.contains("fileName=")) {
            int start = url.indexOf("fileName=") + 9;
            int end = url.indexOf('&', start);
            if (end == -1) {
                end = url.length();
            }
            return url.substring(start, end);
        }

        // Fallback: use last part of URL path
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < url.length() - 1) {
            String fileName = url.substring(lastSlash + 1);
            // Remove query parameters
            int questionMark = fileName.indexOf('?');
            if (questionMark != -1) {
                fileName = fileName.substring(0, questionMark);
            }
            return fileName;
        }

        return "attachment";
    }

    @Override
    public String getName() {
        if (name != null) {
            return name;
        }
        return getString("name");
    }

    @Override
    public String getUrl() {
        if (url != null) {
            return url;
        }
        return getString("url");
    }

    @Override
    public String getContentType() {
        // ADO doesn't provide content type in the relation
        // Infer from filename extension
        String fileName = getName();
        if (fileName == null) {
            return "application/octet-stream";
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerName.endsWith(".json")) {
            return "application/json";
        } else if (lowerName.endsWith(".xml")) {
            return "application/xml";
        } else {
            return "application/octet-stream";
        }
    }
}

