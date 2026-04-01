// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PropertyReader thread-local override mechanism (envVariables per-job support).
 */
class PropertyReaderOverridesTest {

    @AfterEach
    void cleanup() {
        PropertyReader.clearOverrides();
    }

    @Test
    @DisplayName("Thread-local override takes priority over system env and config files")
    void testOverrideTakesPriority() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("OPENAI_MODEL", "gpt-4o-override");

        PropertyReader.setOverrides(overrides);

        PropertyReader reader = new PropertyReader();
        assertEquals("gpt-4o-override", reader.getValue("OPENAI_MODEL"));
    }

    @Test
    @DisplayName("clearOverrides removes the override so normal lookup resumes")
    void testClearOverridesResumesNormalLookup() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("SOME_TEST_KEY_UNIQUE_12345", "overridden-value");

        PropertyReader.setOverrides(overrides);
        assertEquals("overridden-value", new PropertyReader().getValue("SOME_TEST_KEY_UNIQUE_12345"));

        PropertyReader.clearOverrides();

        // After clear, the key is no longer overridden — should return null (not in env either)
        assertNull(new PropertyReader().getValue("SOME_TEST_KEY_UNIQUE_12345"));
    }

    @Test
    @DisplayName("Key not in overrides falls through to normal lookup")
    void testKeyNotInOverridesFallsThrough() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("OTHER_KEY", "some-value");

        PropertyReader.setOverrides(overrides);

        // SOME_MISSING_KEY is not in overrides — should return null (not in env/config)
        assertNull(new PropertyReader().getValue("SOME_MISSING_KEY_9876"));
    }

    @Test
    @DisplayName("null overrides map does not cause NPE")
    void testNullOverridesAreHandledGracefully() {
        PropertyReader.setOverrides(null);
        assertDoesNotThrow(() -> new PropertyReader().getValue("ANY_KEY"));
    }

    @Test
    @DisplayName("Multiple keys in overrides are all resolved correctly")
    void testMultipleOverrideKeys() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("ANTHROPIC_MODEL", "claude-3-5-sonnet");
        overrides.put("OPENAI_MODEL", "gpt-4o");

        PropertyReader.setOverrides(overrides);
        PropertyReader reader = new PropertyReader();

        assertEquals("claude-3-5-sonnet", reader.getValue("ANTHROPIC_MODEL"));
        assertEquals("gpt-4o", reader.getValue("OPENAI_MODEL"));
    }
}
