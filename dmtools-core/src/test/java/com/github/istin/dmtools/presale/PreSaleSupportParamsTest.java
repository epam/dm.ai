// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presale;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PreSaleSupportParamsTest {

    private PreSaleSupportParams preSaleSupportParams;

    @Before
    public void setUp() {
        preSaleSupportParams = new PreSaleSupportParams();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(preSaleSupportParams.getConfluenceRootPage());
        assertNull(preSaleSupportParams.getFolderWithPdfAssets());
    }

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"confluenceRootPage\":\"rootPage\",\"folderWithPdfAssets\":\"pdfAssets\"}";
        PreSaleSupportParams params = new PreSaleSupportParams(jsonString);
        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("pdfAssets", params.getFolderWithPdfAssets());
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("confluenceRootPage", "rootPage");
        jsonObject.put("folderWithPdfAssets", "pdfAssets");
        PreSaleSupportParams params = new PreSaleSupportParams(jsonObject);
        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("pdfAssets", params.getFolderWithPdfAssets());
    }

    @Test
    public void testSetAndGetConfluenceRootPage() {
        preSaleSupportParams.setConfluenceRootPage("newRootPage");
        assertEquals("newRootPage", preSaleSupportParams.getConfluenceRootPage());
    }

    @Test
    public void testSetAndGetFolderWithPdfAssets() {
        preSaleSupportParams.setFolderWithPdfAssets("newPdfAssets");
        assertEquals("newPdfAssets", preSaleSupportParams.getFolderWithPdfAssets());
    }
}