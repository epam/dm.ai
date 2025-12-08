package com.github.istin.dmtools.atlassian.confluence.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class Attachment extends JSONModel {

    public static final String TITLE = "title";
    public static final String LINKS = "_links";
    public static final String DOWNLOAD = "download";

    public Attachment() {
    }

    public Attachment(String json) throws JSONException {
        super(json);
    }

    public Attachment(JSONObject json) {
        super(json);
    }


    public String getTitle() {
        return getString(TITLE);
    }

    /**
     * Gets the download link for this attachment.
     * @return the download path (relative to base URL) or null if not available
     */
    public String getDownloadLink() {
        JSONObject links = getJSONObject().optJSONObject(LINKS);
        if (links != null) {
            return links.optString(DOWNLOAD, null);
        }
        return null;
    }

    /**
     * Gets the media type (MIME type) from metadata.
     * @return the media type (e.g., "image/jpeg", "image/png") or null if not available
     */
    public String getMediaType() {
        JSONObject metadata = getJSONObject().optJSONObject("metadata");
        if (metadata != null) {
            return metadata.optString("mediaType", null);
        }
        return null;
    }

}