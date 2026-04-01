// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ba;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RequirementsCollectorParamsTest {

    private RequirementsCollectorParams params;

    @Before
    public void setUp() {
        params = new RequirementsCollectorParams();
    }

    @Test
    public void testGetLabelNameToMarkAsReviewed() {
        params = new RequirementsCollectorParams(createJsonWithField(RequirementsCollectorParams.LABEL_NAME_TO_MARK_AS_REVIEWED, "ReviewedLabel"));
        assertEquals("ReviewedLabel", params.getLabelNameToMarkAsReviewed());
    }

    @Test
    public void testGetRoleSpecific() {
        params = new RequirementsCollectorParams(createJsonWithField(RequirementsCollectorParams.ROLE_SPECIFIC, "RoleSpecificValue"));
        assertEquals("RoleSpecificValue", params.getRoleSpecific());
    }

    @Test
    public void testGetProjectSpecific() {
        params = new RequirementsCollectorParams(createJsonWithField(RequirementsCollectorParams.PROJECT_SPECIFIC, "ProjectSpecificValue"));
        assertEquals("ProjectSpecificValue", params.getProjectSpecific());
    }

    @Test
    public void testGetEachPagePrefix() {
        params = new RequirementsCollectorParams(createJsonWithField(RequirementsCollectorParams.EACH_PAGE_PREFIX, "PagePrefix"));
        assertEquals("PagePrefix", params.getEachPagePrefix());
    }

    @Test
    public void testGetExcludeJQL() {
        params = new RequirementsCollectorParams(createJsonWithField("excludeJQL", "ExcludeJQLValue"));
        assertEquals("ExcludeJQLValue", params.getExcludeJQL());
    }

    @Test
    public void testEmptyConstructor() {
        assertNull(params.getLabelNameToMarkAsReviewed());
        assertNull(params.getRoleSpecific());
        assertNull(params.getProjectSpecific());
        assertNull(params.getEachPagePrefix());
        assertNull(params.getExcludeJQL());
    }

    private String createJsonWithField(String key, String value) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }
}