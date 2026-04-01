// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for envVariables field in TrackerParams — JSON deserialization and job-level override support.
 */
class TrackerParamsEnvVariablesTest {

    @Test
    @DisplayName("envVariables field is deserialized from JSON correctly")
    void testEnvVariablesDeserializedFromJson() {
        String json = "{"
                + "\"inputJql\": \"project = TEST\","
                + "\"envVariables\": {"
                + "  \"OPENAI_MODEL\": \"gpt-4o\","
                + "  \"ANTHROPIC_MODEL\": \"claude-3-5-sonnet-20241022\""
                + "}"
                + "}";

        TrackerParams params = new Gson().fromJson(json, TrackerParams.class);

        assertNotNull(params.getEnvVariables());
        assertEquals(2, params.getEnvVariables().size());
        assertEquals("gpt-4o", params.getEnvVariables().get("OPENAI_MODEL"));
        assertEquals("claude-3-5-sonnet-20241022", params.getEnvVariables().get("ANTHROPIC_MODEL"));
    }

    @Test
    @DisplayName("envVariables is null when not present in JSON")
    void testEnvVariablesNullWhenNotInJson() {
        String json = "{\"inputJql\": \"project = TEST\"}";

        TrackerParams params = new Gson().fromJson(json, TrackerParams.class);

        assertNull(params.getEnvVariables());
    }

    @Test
    @DisplayName("envVariables can be set and retrieved via getter/setter")
    void testEnvVariablesSetterGetter() {
        TrackerParams params = new TrackerParams();
        assertNull(params.getEnvVariables());

        params.setEnvVariables(Map.of("DIAL_MODEL", "gpt-4-turbo"));
        assertEquals("gpt-4-turbo", params.getEnvVariables().get("DIAL_MODEL"));
    }

    @Test
    @DisplayName("ExpertParams (Params subclass) also has envVariables via inheritance")
    void testExpertParamsInheritsEnvVariables() {
        String json = "{"
                + "\"request\": \"analyze code\","
                + "\"envVariables\": {\"OPENAI_MODEL\": \"gpt-4o\"}"
                + "}";

        // ExpertParams extends Params extends TrackerParams
        com.github.istin.dmtools.expert.ExpertParams params =
                new Gson().fromJson(json, com.github.istin.dmtools.expert.ExpertParams.class);

        assertNotNull(params.getEnvVariables());
        assertEquals("gpt-4o", params.getEnvVariables().get("OPENAI_MODEL"));
    }
}
