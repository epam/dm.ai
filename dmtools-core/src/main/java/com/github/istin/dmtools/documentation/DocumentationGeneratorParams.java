// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DocumentationGeneratorParams extends JSONModel {

    public DocumentationGeneratorParams() {
    }

    public DocumentationGeneratorParams(String json) throws JSONException {
        super(json);
    }

    public DocumentationGeneratorParams(JSONObject json) {
        super(json);
    }

    public String getConfluenceRootPage() {
        return getString("confluenceRootPage");
    }

    public String getEachPagePrefix() {
        return getString("eachPagePrefix");
    }

    public String getJQL() {
        return getString("jql");
    }

    public boolean isReadFeatureAreasFromConfluenceRootPage() {
        return getBoolean("isReadFeatureAreasFromConfluenceRootPage");
    }

    public String[] getListOfStatusesToSort() {
        JSONArray listOfStatusesToSort = getJSONArray("listOfStatusesToSort");
        String[] listOfStatuses = new String[listOfStatusesToSort.length()];
        for (int i = 0; i < listOfStatusesToSort.length(); i++) {
            listOfStatuses[i] = listOfStatusesToSort.getString(i);
        }
        return listOfStatuses;
    }
}