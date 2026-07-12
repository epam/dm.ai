// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage tests for {@link LLMOptimizedJson} targeting constructor overloads,
 * well-formed/regular root element branches, deep-nesting indent fallback,
 * empty-array handling, and the remaining static factory methods.
 */
class LLMOptimizedJsonCoverageTest {

    private static final String SIMPLE_JSON = "{\"name\":\"John\",\"age\":30}";

    // -------------------------------------------------------------------------
    // JSONObject constructor overloads
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JSONObject constructor with formatting mode")
    void testJSONObjectConstructorWithMode() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(new JSONObject(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.PRETTY);
        String result = optimizer.toString();
        assertNotNull(result);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("JSONObject constructor with mode, wellFormed and blacklist")
    void testJSONObjectConstructorWithModeWellFormedBlacklist() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("age");
        LLMOptimizedJson optimizer = new LLMOptimizedJson(new JSONObject(SIMPLE_JSON),
                LLMOptimizedJson.FormattingMode.MINIMIZED, true, blacklist);
        String result = optimizer.toString();
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    // -------------------------------------------------------------------------
    // InputStream constructor overloads
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InputStream constructor with formatting mode")
    void testInputStreamConstructorWithMode() {
        InputStream stream = stream(SIMPLE_JSON);
        LLMOptimizedJson optimizer = new LLMOptimizedJson(stream, LLMOptimizedJson.FormattingMode.PRETTY);
        assertTrue(optimizer.toString().contains("John"));
    }

    @Test
    @DisplayName("InputStream constructor with mode and wellFormed")
    void testInputStreamConstructorWithModeWellFormed() {
        InputStream stream = stream(SIMPLE_JSON);
        LLMOptimizedJson optimizer = new LLMOptimizedJson(stream, LLMOptimizedJson.FormattingMode.MINIMIZED, true);
        assertTrue(optimizer.toString().contains("John"));
    }

    @Test
    @DisplayName("InputStream constructor with mode, wellFormed and blacklist")
    void testInputStreamConstructorWithBlacklist() {
        InputStream stream = stream(SIMPLE_JSON);
        LLMOptimizedJson optimizer = new LLMOptimizedJson(stream, LLMOptimizedJson.FormattingMode.MINIMIZED,
                true, Collections.singleton("age"));
        String result = optimizer.toString();
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    // -------------------------------------------------------------------------
    // JsonElement constructor overloads
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JsonElement constructor with default mode")
    void testJsonElementConstructorDefault() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(parse(SIMPLE_JSON));
        assertTrue(optimizer.toString().contains("John"));
    }

    @Test
    @DisplayName("JsonElement constructor with formatting mode")
    void testJsonElementConstructorWithMode() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(parse(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.PRETTY);
        assertTrue(optimizer.toString().contains("John"));
    }

    @Test
    @DisplayName("JsonElement constructor with mode and wellFormed")
    void testJsonElementConstructorWithModeWellFormed() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(parse(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.MINIMIZED, true);
        assertTrue(optimizer.toString().contains("John"));
    }

    @Test
    @DisplayName("JsonElement constructor with mode, wellFormed and blacklist")
    void testJsonElementConstructorWithBlacklist() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(parse(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.MINIMIZED,
                false, Collections.singleton("age"));
        String result = optimizer.toString();
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    @Test
    @DisplayName("JsonElement constructor with all options including skipEmptyValues")
    void testJsonElementConstructorFullOptions() {
        String json = "{\"name\":\"John\",\"empty\":\"   \"}";
        LLMOptimizedJson optimizer = new LLMOptimizedJson(parse(json), LLMOptimizedJson.FormattingMode.MINIMIZED,
                false, new HashSet<>(), true);
        String result = optimizer.toString();
        assertTrue(result.contains("John"));
        assertNotNull(optimizer.getRootElement());
    }

    // -------------------------------------------------------------------------
    // Root element dispatch branches (well-formed and regular)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Well-formed mode formats root array")
    void testWellFormedRootArray() {
        String result = LLMOptimizedJson.formatWellFormed("[\"a\",\"b\"]");
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
    }

    @Test
    @DisplayName("Well-formed mode formats root primitive")
    void testWellFormedRootPrimitive() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson("\"hello\"", LLMOptimizedJson.FormattingMode.MINIMIZED, true);
        assertTrue(optimizer.toString().contains("hello"));
    }

    @Test
    @DisplayName("Regular mode formats root primitive")
    void testRegularRootPrimitive() {
        LLMOptimizedJson optimizer = new LLMOptimizedJson("42");
        assertTrue(optimizer.toString().contains("42"));
    }

    @Test
    @DisplayName("Well-formed mode formats empty array as [ ]")
    void testWellFormedEmptyArray() {
        String result = LLMOptimizedJson.formatWellFormed("[]");
        assertTrue(result.contains("[ ]"));
    }

    @Test
    @DisplayName("Well-formed object array with nested array values")
    void testWellFormedObjectArrayWithNestedArray() {
        String json = "[{\"id\":1,\"tags\":[\"a\",\"b\"]}]";
        String result = LLMOptimizedJson.formatWellFormed(json);
        assertTrue(result.contains("tags"));
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
    }

    @Test
    @DisplayName("Deep nesting beyond indent cache falls back to dynamic indent")
    void testDeepNestingIndentFallback() {
        // Six levels of object-array nesting push indentLevel past the 10-slot cache
        StringBuilder json = new StringBuilder("{\"a\":1}");
        for (int i = 0; i < 6; i++) {
            json.insert(0, "[{\"a\":").append("}]");
        }
        LLMOptimizedJson optimizer = new LLMOptimizedJson(json.toString(), LLMOptimizedJson.FormattingMode.PRETTY, true);
        String result = optimizer.toString();
        assertNotNull(result);
        assertTrue(result.contains("1"));
    }

    // -------------------------------------------------------------------------
    // Blacklisting edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Empty field name is treated as blacklisted")
    void testEmptyFieldNameBlacklisted() {
        String json = "{\"\":\"hidden\",\"keep\":\"visible\"}";
        LLMOptimizedJson optimizer = new LLMOptimizedJson(json);
        String result = optimizer.toString();
        assertTrue(result.contains("visible"));
        assertFalse(result.contains("hidden"));
    }

    // -------------------------------------------------------------------------
    // Static factory methods
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("format(String, FormattingMode)")
    void testFormatWithMode() {
        String result = LLMOptimizedJson.format(SIMPLE_JSON, LLMOptimizedJson.FormattingMode.PRETTY);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format(String, Set) applies blacklist")
    void testFormatWithBlacklistSet() {
        String result = LLMOptimizedJson.format(SIMPLE_JSON, Collections.singleton("age"));
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    @Test
    @DisplayName("format(String, FormattingMode, Set)")
    void testFormatWithModeAndBlacklist() {
        String result = LLMOptimizedJson.format(SIMPLE_JSON, LLMOptimizedJson.FormattingMode.PRETTY,
                Collections.singleton("age"));
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    @Test
    @DisplayName("format(String, FormattingMode, String...) varargs blacklist")
    void testFormatWithModeAndVarargsBlacklist() {
        String result = LLMOptimizedJson.format(SIMPLE_JSON, LLMOptimizedJson.FormattingMode.MINIMIZED, "age");
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    @Test
    @DisplayName("format(JSONObject, FormattingMode)")
    void testFormatJSONObjectWithMode() {
        String result = LLMOptimizedJson.format(new JSONObject(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.PRETTY);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format(JSONObject, FormattingMode, boolean)")
    void testFormatJSONObjectWithModeWellFormed() {
        String result = LLMOptimizedJson.format(new JSONObject(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.MINIMIZED, true);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format(InputStream) default mode")
    void testFormatInputStream() {
        String result = LLMOptimizedJson.format(stream(SIMPLE_JSON));
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format(InputStream, FormattingMode)")
    void testFormatInputStreamWithMode() {
        String result = LLMOptimizedJson.format(stream(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.PRETTY);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format(InputStream, FormattingMode, boolean)")
    void testFormatInputStreamWithModeWellFormed() {
        String result = LLMOptimizedJson.format(stream(SIMPLE_JSON), LLMOptimizedJson.FormattingMode.MINIMIZED, true);
        assertTrue(result.contains("John"));
    }

    @Test
    @DisplayName("format with full control: mode, wellFormed, blacklist, skipEmptyValues")
    void testFormatFullControl() {
        String json = "{\"name\":\"John\",\"age\":30,\"empty\":\"\"}";
        String result = LLMOptimizedJson.format(json, LLMOptimizedJson.FormattingMode.MINIMIZED,
                true, Collections.singleton("age"), true);
        assertTrue(result.contains("John"));
        assertFalse(result.contains("30"));
    }

    // -------------------------------------------------------------------------
    // Private helpers reachable only via reflection (dead code kept in class)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Legacy single-arg isFieldBlacklisted delegates to path-aware version")
    void testLegacyIsFieldBlacklisted() throws Exception {
        LLMOptimizedJson optimizer = new LLMOptimizedJson(SIMPLE_JSON, LLMOptimizedJson.FormattingMode.MINIMIZED,
                false, Collections.singleton("age"));
        Method method = LLMOptimizedJson.class.getDeclaredMethod("isFieldBlacklisted", String.class);
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(optimizer, "age"));
        assertFalse((Boolean) method.invoke(optimizer, "name"));
    }

    @Test
    @DisplayName("printObjectNextHeaderWellFormatted writes compact header")
    void testPrintObjectNextHeaderWellFormatted() throws Exception {
        Method method = LLMOptimizedJson.class.getDeclaredMethod("printObjectNextHeaderWellFormatted",
                StringBuilder.class, String.class, Set.class);
        method.setAccessible(true);
        StringBuilder result = new StringBuilder();
        Set<Map.Entry<String, JsonElement>> entries = parse("{\"a\":1,\"b\":2}").getAsJsonObject().entrySet();
        method.invoke(null, result, "", entries);
        assertEquals("Next a,b\n", result.toString());
    }

    @Test
    @DisplayName("printObjectNextHeaderWellFormattedWithParent prefixes parent key")
    void testPrintObjectNextHeaderWellFormattedWithParent() throws Exception {
        Method method = LLMOptimizedJson.class.getDeclaredMethod("printObjectNextHeaderWellFormattedWithParent",
                StringBuilder.class, String.class, Set.class, String.class);
        method.setAccessible(true);
        Set<Map.Entry<String, JsonElement>> entries = parse("{\"a\":1,\"b\":2}").getAsJsonObject().entrySet();

        StringBuilder withParent = new StringBuilder();
        method.invoke(null, withParent, "", entries, "user");
        assertEquals("user Next a,b\n", withParent.toString());

        StringBuilder withoutParent = new StringBuilder();
        method.invoke(null, withoutParent, "", entries, "");
        assertEquals("Next a,b\n", withoutParent.toString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }
}
