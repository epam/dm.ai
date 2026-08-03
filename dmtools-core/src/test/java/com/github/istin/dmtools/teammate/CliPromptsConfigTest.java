// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliPromptsConfigTest {

    private final Gson gson = new Gson();

    @Test
    void testDeserializesPlainStringArray() {
        String json = "[\"a\", \"b\"]";
        CliPromptsConfig config = gson.fromJson(json, CliPromptsConfig.class);
        assertArrayEquals(new String[]{"a", "b"}, config.toStringArray());
    }

    @Test
    void testDeserializesMixedArray() {
        String json = "[\"base\", {\"id\": \"input\", \"prompts\": [\"in1\", \"in2\"]}, \"tail\"]";
        CliPromptsConfig config = gson.fromJson(json, CliPromptsConfig.class);
        assertArrayEquals(new String[]{"base", "in1", "in2", "tail"}, config.toStringArray());
        assertEquals(3, config.getItems().size());
        assertTrue(config.getItems().get(0).isUnnamed());
        assertFalse(config.getItems().get(1).isUnnamed());
        assertTrue(config.getItems().get(2).isUnnamed());
    }

    @Test
    void testSerializesBackToSameFormat() {
        String json = "[\"base\", {\"id\": \"input\", \"prompts\": [\"in1\"]}, \"tail\"]";
        CliPromptsConfig config = gson.fromJson(json, CliPromptsConfig.class);
        String serialized = gson.toJson(config);
        assertEquals("[\"base\",{\"id\":\"input\",\"prompts\":[\"in1\"]},\"tail\"]", serialized);
    }

    @Test
    void testRejectsObjectWithoutId() {
        String json = "[{\"prompts\": [\"a\"]}]";
        assertThrows(JsonParseException.class, () -> gson.fromJson(json, CliPromptsConfig.class));
    }

    @Test
    void testMergeAppendsSameIdSections() {
        CliPromptsConfig base = gson.fromJson(
                "[\"base1\", {\"id\": \"input\", \"prompts\": [\"in1\"]}, \"base2\"]",
                CliPromptsConfig.class);
        CliPromptsConfig override = gson.fromJson(
                "[{\"id\": \"input\", \"prompts\": [\"in2\"]}, \"child1\"]",
                CliPromptsConfig.class);

        CliPromptsConfig merged = base.merge(override);
        assertArrayEquals(new String[]{"base1", "in1", "in2", "base2", "child1"}, merged.toStringArray());
    }

    @Test
    void testMergePrependsWithPrependStrategy() {
        CliPromptsConfig base = gson.fromJson(
                "[{\"id\": \"input\", \"prompts\": [\"in1\"]}]",
                CliPromptsConfig.class);
        CliPromptsConfig override = gson.fromJson(
                "[{\"id\": \"input\", \"prompts\": [\"in2\"], \"mergeStrategy\": \"prepend\"}]",
                CliPromptsConfig.class);

        CliPromptsConfig merged = base.merge(override);
        assertArrayEquals(new String[]{"in2", "in1"}, merged.toStringArray());
    }

    @Test
    void testMergeReplacesWithReplaceStrategy() {
        CliPromptsConfig base = gson.fromJson(
                "[{\"id\": \"input\", \"prompts\": [\"in1\"]}]",
                CliPromptsConfig.class);
        CliPromptsConfig override = gson.fromJson(
                "[{\"id\": \"input\", \"prompts\": [\"in2\"], \"mergeStrategy\": \"replace\"}]",
                CliPromptsConfig.class);

        CliPromptsConfig merged = base.merge(override);
        assertArrayEquals(new String[]{"in2"}, merged.toStringArray());
    }

    @Test
    void testMergeAddsNewSectionsAtEnd() {
        CliPromptsConfig base = gson.fromJson(
                "[\"a\", {\"id\": \"input\", \"prompts\": [\"in1\"]}]",
                CliPromptsConfig.class);
        CliPromptsConfig override = gson.fromJson(
                "[{\"id\": \"output\", \"prompts\": [\"out1\"]}, \"b\"]",
                CliPromptsConfig.class);

        CliPromptsConfig merged = base.merge(override);
        assertArrayEquals(new String[]{"a", "in1", "out1", "b"}, merged.toStringArray());
    }

    @Test
    void testMergePreservesUnnamedPositions() {
        CliPromptsConfig base = gson.fromJson(
                "[\"a\", {\"id\": \"x\", \"prompts\": [\"x1\"]}, \"b\", {\"id\": \"y\", \"prompts\": [\"y1\"]}, \"c\"]",
                CliPromptsConfig.class);
        CliPromptsConfig override = gson.fromJson(
                "[{\"id\": \"x\", \"prompts\": [\"x2\"]}, \"d\"]",
                CliPromptsConfig.class);

        CliPromptsConfig merged = base.merge(override);
        assertArrayEquals(new String[]{"a", "x1", "x2", "b", "y1", "c", "d"}, merged.toStringArray());
    }

    @Test
    void testFromStringsCreatesUnnamedItems() {
        CliPromptsConfig config = CliPromptsConfig.fromStrings(new String[]{"a", "b"});
        assertEquals(2, config.getItems().size());
        assertTrue(config.getItems().get(0).isUnnamed());
        assertTrue(config.getItems().get(1).isUnnamed());
        assertArrayEquals(new String[]{"a", "b"}, config.toStringArray());
    }

    @Test
    void testNullAndEmptyHandling() {
        assertEquals(0, CliPromptsConfig.fromStrings(null).getItems().size());
        assertEquals(0, CliPromptsConfig.fromStrings(new String[0]).getItems().size());
        assertEquals(0, gson.fromJson("[]", CliPromptsConfig.class).getItems().size());
        assertNull(gson.fromJson("null", CliPromptsConfig.class));
    }
}
