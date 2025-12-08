package com.github.istin.dmtools.atlassian.confluence.model;

import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.common.model.TicketLink;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class Content extends JSONModel implements Key, TicketLink, ToText {

    public static final String ID = "id";
    public static final String TITLE = "title";

    public static final String STORAGE = "storage";

    public static final String VERSION = "version";

    public Content() {
    }

    public Content(String json) throws JSONException {
        super(json);
    }

    public Content(JSONObject json) {
        super(json);
    }


    public String getId() {
        return getString(ID);
    }

    public String getTitle() {
        return getString(TITLE);
    }

    public Storage getStorage() {
        JSONObject body = getJSONObject("body");
        if (body == null) {
            return null;
        }
        return new Storage(body.getJSONObject(STORAGE));
    }

    public int getVersionNumber() {
        return getJSONObject(VERSION).getInt("number");
    }

    public Date getLastModifiedDate() {
        JSONObject version = getJSONObject(VERSION);
        if (version == null) {
            return null;
        }
        String when = version.optString("when");
        if (when == null || when.isEmpty()) {
            return null;
        }
        return DateUtils.parseIsoDate2(when);
    }

    public String getViewUrl(String basePath) {
        return basePath + getJSONObject("_links").getString("webui");
    }

    public String getParentId() {
        List<Content> ancestors = getModels(Content.class, "ancestors");
        if (ancestors != null && !ancestors.isEmpty()) {
            return ancestors.get(ancestors.size()-1).getId();
        }
        return null;
    }

    @Override
    public String getTicketLink() {
        return getViewUrl(getJSONObject("_links").getString("base"));
    }

    @Override
    public String toText() throws IOException {
        return getStorage().getValue();
    }

    @Override
    public double getWeight() {
        return getStorage().getValue().length() / 1000d;
    }

    @Override
    public String getKey() {
        String space = getJSONObject().optJSONObject("_expandable").optString("space");
        String[] split = space.split("/");
        return split[split.length - 1] + "-" + getId();
    }
}
