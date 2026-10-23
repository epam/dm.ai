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

    @Test
    void testDeserializesSinglePromptAndCondition() {
        String json = "[\"base\", {\"id\": \"codegraph\", \"prompt\": \"./agents/prompts/codegraph_tools.md\", \"condition\": \"agents/js/conditions/isCodeGraphAvailable.js\"}, \"tail\"]";
        CliPromptsConfig config = gson.fromJson(json, CliPromptsConfig.class);
        assertArrayEquals(new String[]{"base", "./agents/prompts/codegraph_tools.md", "tail"}, config.toStringArray());
        assertEquals("agents/js/conditions/isCodeGraphAvailable.js", config.getItems().get(1).getSection().getCondition());
    }

    @Test
    void testToStringArrayWithConditionEvaluator() {
        String json = "[\"base\", " +
                "{\"id\": \"codegraph\", \"prompt\": \"cg.md\", \"condition\": \"cond_true.js\"}, " +
                "{\"id\": \"optional\", \"prompts\": [\"opt1.md\", \"opt2.md\"], \"condition\": \"cond_false.js\"}, " +
                "\"tail\"]";
        CliPromptsConfig config = gson.fromJson(json, CliPromptsConfig.class);

        assertArrayEquals(
                new String[]{"base", "cg.md", "tail"},
                config.toStringArray(cond -> "cond_true.js".equals(cond)));
        assertArrayEquals(
                new String[]{"base", "opt1.md", "opt2.md", "tail"},
                config.toStringArray(cond -> "cond_false.js".equals(cond)));
    }

    @Test
    void testMergePreservesOrOverridesCondition() {
        CliPromptsConfig base = gson.fromJson(
                "[{\"id\": \"codegraph\", \"prompt\": \"cg1.md\", \"condition\": \"base_cond.js\"}]",
                CliPromptsConfig.class);
        CliPromptsConfig overrideKeep = gson.fromJson(
                "[{\"id\": \"codegraph\", \"prompt\": \"cg2.md\"}]",
                CliPromptsConfig.class);
        CliPromptsConfig mergedKeep = base.merge(overrideKeep);
        assertEquals("base_cond.js", mergedKeep.getItems().get(0).getSection().getCondition());
        assertArrayEquals(new String[]{"cg1.md", "cg2.md"}, mergedKeep.toStringArray());

        CliPromptsConfig overrideReplace = gson.fromJson(
                "[{\"id\": \"codegraph\", \"prompt\": \"cg2.md\", \"condition\": \"child_cond.js\"}]",
                CliPromptsConfig.class);
        CliPromptsConfig mergedReplace = base.merge(overrideReplace);
        assertEquals("child_cond.js", mergedReplace.getItems().get(0).getSection().getCondition());
    }

    @Test
    void testIsConditionMet() {
        assertTrue(CliPromptsConfig.isConditionMet(Boolean.TRUE));
        assertTrue(CliPromptsConfig.isConditionMet("true"));
        assertTrue(CliPromptsConfig.isConditionMet(" TRUE "));
        assertTrue(CliPromptsConfig.isConditionMet(new org.json.JSONObject().put("result", true)));
        assertFalse(CliPromptsConfig.isConditionMet(Boolean.FALSE));
        assertFalse(CliPromptsConfig.isConditionMet("false"));
        assertFalse(CliPromptsConfig.isConditionMet(null));
        assertFalse(CliPromptsConfig.isConditionMet(new org.json.JSONObject().put("result", true).put("uncaughtException", true)));
    }
}
