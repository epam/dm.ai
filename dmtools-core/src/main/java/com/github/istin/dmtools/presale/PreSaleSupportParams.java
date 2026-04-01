// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presale;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONException;
import org.json.JSONObject;

public class PreSaleSupportParams extends JSONModel {

    public static final String CONFLUENCE_ROOT_PAGE = "confluenceRootPage";
    public static final String FOLDER_WITH_PDF_ASSETS = "folderWithPdfAssets";

    public PreSaleSupportParams() {
    }

    public PreSaleSupportParams(String json) throws JSONException {
        super(json);
    }

    public PreSaleSupportParams(JSONObject json) {
        super(json);
    }

    public String getConfluenceRootPage() {
        return getString(CONFLUENCE_ROOT_PAGE);
    }

    public String getFolderWithPdfAssets() {
        return getString(FOLDER_WITH_PDF_ASSETS);
    }

    public void setFolderWithPdfAssets(String folderWithPdfAssets) {
        set(FOLDER_WITH_PDF_ASSETS, folderWithPdfAssets);
    }

    public void setConfluenceRootPage(String confluenceRootPage) {
        set(CONFLUENCE_ROOT_PAGE, confluenceRootPage);
    }
}