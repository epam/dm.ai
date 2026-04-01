// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.github.istin.dmtools.common.model.ToText;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CLI output formatter implementations.
 *
 * <p>Covers {@link JsonCliOutputFormatter}, {@link ToonCliOutputFormatter}, and
 * {@link MiniCliOutputFormatter} for result, list, and error output paths.</p>
 */
class CliOutputFormatterTest {

    // -------------------------------------------------------------------------
    // JsonCliOutputFormatter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JSON formatter – null result wraps in success:true")
    void jsonFormatterNullResult() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        String out = fmt.formatResult(null);
        JSONObject json = new JSONObject(out);
        assertTrue(json.getBoolean("success"));
    }

    @Test
    @DisplayName("JSON formatter – JSONObject result passes through without extra wrapping")
    void jsonFormatterJsonObjectResult() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        JSONObject obj = new JSONObject();
        obj.put("key", "value");
        String out = fmt.formatResult(obj);
        JSONObject json = new JSONObject(out);
        assertEquals("value", json.getString("key"));
        assertFalse(json.has("result"));
    }

    @Test
    @DisplayName("JSON formatter – plain string result is wrapped under 'result'")
    void jsonFormatterStringResult() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        String out = fmt.formatResult("hello");
        JSONObject json = new JSONObject(out);
        assertEquals("hello", json.get("result"));
    }

    @Test
    @DisplayName("JSON formatter – error contains error:true and message")
    void jsonFormatterError() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        String out = fmt.formatError("Something went wrong");
        JSONObject json = new JSONObject(out);
        assertTrue(json.getBoolean("error"));
        assertEquals("Something went wrong", json.getString("message"));
    }

    @Test
    @DisplayName("JSON formatter – list output is valid JSON with 'tools' key")
    void jsonFormatterList() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("tools", new org.json.JSONArray());
        String out = fmt.formatList(tools);
        JSONObject json = new JSONObject(out);
        assertTrue(json.has("tools"));
    }

    @Test
    @DisplayName("JSON formatter – output is pretty-printed (contains newlines)")
    void jsonFormatterIsPrettyPrinted() {
        JsonCliOutputFormatter fmt = new JsonCliOutputFormatter();
        JSONObject obj = new JSONObject();
        obj.put("key", "value");
        String out = fmt.formatResult(obj);
        assertTrue(out.contains("\n"), "JSON output should be pretty-printed");
    }

    // -------------------------------------------------------------------------
    // MiniCliOutputFormatter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MINI formatter – null result produces LLMOptimizedJson text containing 'success'")
    void miniFormatterNullResult() {
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        String out = fmt.formatResult(null);
        assertNotNull(out);
        assertFalse(out.trim().isEmpty());
        assertTrue(out.contains("success"), "MINI null result should mention 'success'");
        // LLMOptimizedJson format should not be raw JSON
        assertFalse(out.trim().startsWith("{"), "MINI output should not be raw JSON");
    }

    @Test
    @DisplayName("MINI formatter – JSONObject result is LLMOptimizedJson text (not raw JSON)")
    void miniFormatterJsonObjectNoIndent() {
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        JSONObject obj = new JSONObject();
        obj.put("alpha", "beta");
        String out = fmt.formatResult(obj);
        assertFalse(out.trim().startsWith("{"), "MINI output should not be raw JSON object");
        assertTrue(out.contains("alpha"), "MINI output should contain the key");
    }

    @Test
    @DisplayName("MINI formatter – error is LLMOptimizedJson text containing the message")
    void miniFormatterError() {
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        String out = fmt.formatError("oops");
        assertFalse(out.trim().startsWith("{"), "MINI error should not be raw JSON");
        assertTrue(out.contains("oops"), "MINI error should contain the message text");
        assertTrue(out.contains("error"), "MINI error should mention 'error'");
    }

    @Test
    @DisplayName("MINI formatter – list output is LLMOptimizedJson text")
    void miniFormatterList() {
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("tools", new org.json.JSONArray());
        String out = fmt.formatList(tools);
        assertFalse(out.trim().isEmpty(), "MINI list output should not be empty");
        assertTrue(out.contains("tools"), "MINI list output should mention 'tools'");
    }

    // -------------------------------------------------------------------------
    // ToonCliOutputFormatter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TOON formatter – non-JSON primitive result passes through unchanged")
    void toonFormatterPrimitiveResult() {
        ToonCliOutputFormatter fmt = new ToonCliOutputFormatter();
        String out = fmt.formatResult("plain text");
        assertEquals("plain text", out);
    }

    @Test
    @DisplayName("TOON formatter – null result produces non-JSON success output")
    void toonFormatterNullResult() {
        ToonCliOutputFormatter fmt = new ToonCliOutputFormatter();
        String out = fmt.formatResult(null);
        assertNotNull(out);
        assertFalse(out.trim().isEmpty());
    }

    @Test
    @DisplayName("TOON formatter – JSONObject result does not start with { (TOON format)")
    void toonFormatterJsonObjectIsTransformed() {
        ToonCliOutputFormatter fmt = new ToonCliOutputFormatter();
        JSONObject obj = new JSONObject();
        obj.put("name", "Alice");
        obj.put("age", 30);
        String out = fmt.formatResult(obj);
        // JToon converts to YAML-like "key: value" format, not raw JSON
        assertFalse(out.trim().startsWith("{"),
            "TOON output should not be raw JSON: " + out);
    }

    @Test
    @DisplayName("TOON formatter – error output does not start with {")
    void toonFormatterErrorIsTransformed() {
        ToonCliOutputFormatter fmt = new ToonCliOutputFormatter();
        String out = fmt.formatError("bad thing happened");
        // Should be LLM-optimized text, not raw JSON
        assertFalse(out.trim().startsWith("{"),
            "TOON error should not be raw JSON: " + out);
        assertTrue(out.contains("bad thing happened"),
            "TOON error should contain the original message");
    }

    // -------------------------------------------------------------------------
    // OutputFormat enum
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OutputFormat.fromString – known values resolve correctly")
    void outputFormatKnownValues() {
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("json"));
        assertEquals(OutputFormat.TOON, OutputFormat.fromString("toon"));
        assertEquals(OutputFormat.MINI, OutputFormat.fromString("mini"));
    }

    @Test
    @DisplayName("OutputFormat.fromString – case-insensitive")
    void outputFormatCaseInsensitive() {
        assertEquals(OutputFormat.TOON, OutputFormat.fromString("TOON"));
        assertEquals(OutputFormat.MINI, OutputFormat.fromString("Mini"));
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("JSON"));
    }

    @Test
    @DisplayName("OutputFormat.fromString – null falls back to JSON")
    void outputFormatNullFallback() {
        assertEquals(OutputFormat.JSON, OutputFormat.fromString(null));
    }

    @Test
    @DisplayName("OutputFormat.fromString – empty string falls back to JSON")
    void outputFormatEmptyFallback() {
        assertEquals(OutputFormat.JSON, OutputFormat.fromString(""));
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("  "));
    }

    @Test
    @DisplayName("OutputFormat.fromString – unknown value falls back to JSON")
    void outputFormatUnknownFallback() {
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("xml"));
        assertEquals(OutputFormat.JSON, OutputFormat.fromString("yaml"));
    }

    // -------------------------------------------------------------------------
    // CliOutputFormatterFactory – format resolution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Factory – null CLI flag resolves to JSON (default)")
    void factoryNullFlagUsesDefault() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create(null);
        assertInstanceOf(JsonCliOutputFormatter.class, fmt);
    }

    @Test
    @DisplayName("Factory – empty CLI flag resolves to JSON (default)")
    void factoryEmptyFlagUsesDefault() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create("");
        assertInstanceOf(JsonCliOutputFormatter.class, fmt);
    }

    @Test
    @DisplayName("Factory – explicit 'toon' CLI flag resolves to ToonCliOutputFormatter")
    void factoryToonFlag() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create("toon");
        assertInstanceOf(ToonCliOutputFormatter.class, fmt);
    }

    @Test
    @DisplayName("Factory – explicit 'mini' CLI flag resolves to MiniCliOutputFormatter")
    void factoryMiniFlag() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create("mini");
        assertInstanceOf(MiniCliOutputFormatter.class, fmt);
    }

    @Test
    @DisplayName("Factory – explicit 'json' CLI flag resolves to JsonCliOutputFormatter")
    void factoryJsonFlag() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create("json");
        assertInstanceOf(JsonCliOutputFormatter.class, fmt);
    }

    @Test
    @DisplayName("Factory – unknown format falls back to JSON")
    void factoryUnknownFallback() {
        CliOutputFormatter fmt = CliOutputFormatterFactory.create("xml");
        assertInstanceOf(JsonCliOutputFormatter.class, fmt);
    }

    // -------------------------------------------------------------------------
    // MiniCliOutputFormatter – ToText integration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MINI formatter – single ToText object: uses toText() output directly")
    void miniFormatterUsesToTextForSingleObject() throws IOException {
        ToText item = () -> "custom text output";
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        String out = fmt.formatResult(item);
        assertEquals("custom text output", out);
    }

    @Test
    @DisplayName("MINI formatter – List<ToText> uses ToText.Utils.toText()")
    void miniFormatterUsesToTextForList() throws IOException {
        ToText item1 = () -> "line one\n";
        ToText item2 = () -> "line two\n";
        List<ToText> list = Arrays.asList(item1, item2);

        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        String out = fmt.formatResult(list);

        assertTrue(out.contains("line one"), "Should contain first item output");
        assertTrue(out.contains("line two"), "Should contain second item output");
    }

    @Test
    @DisplayName("MINI formatter – List with non-ToText items falls back to LLMOptimizedJson")
    void miniFormatterNonToTextListFallsBack() {
        List<String> list = Arrays.asList("a", "b", "c");
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        String out = fmt.formatResult(list);
        assertNotNull(out);
        assertFalse(out.trim().isEmpty());
    }

    @Test
    @DisplayName("MINI formatter – ToText throwing IOException falls back to LLMOptimizedJson")
    void miniFormatterToTextExceptionFallsBack() {
        ToText broken = () -> { throw new IOException("disk error"); };
        MiniCliOutputFormatter fmt = new MiniCliOutputFormatter();
        // Should not throw; should fall back gracefully
        assertDoesNotThrow(() -> fmt.formatResult(broken));
    }
}
