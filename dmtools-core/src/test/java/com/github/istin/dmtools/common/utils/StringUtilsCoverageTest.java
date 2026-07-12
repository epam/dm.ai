// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.common.model.ToText;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive coverage tests for {@link StringUtils} exercising every public
 * method and the reachable private branches (field blacklisting, YAML-style
 * transformation, markdown tables, nested sections, summaries).
 */
class StringUtilsCoverageTest {

    // -------------------------------------------------------------------------
    // Constructor (implicit default) - touch it so JaCoCo counts the class init
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Class can be instantiated")
    void testInstantiation() {
        assertNotNull(new StringUtils());
    }

    // -------------------------------------------------------------------------
    // transformJSONToText(StringBuilder, JSONObject, boolean)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JSONObject transform returns builder unchanged for null and empty input")
    void testTransformJSONObjectNullAndEmpty() {
        StringBuilder builder = new StringBuilder("prefix");
        assertSame(builder, StringUtils.transformJSONToText(builder, (JSONObject) null, false));
        assertEquals("prefix", builder.toString());

        assertSame(builder, StringUtils.transformJSONToText(builder, new JSONObject(), false));
        assertEquals("prefix", builder.toString());
    }

    @Test
    @DisplayName("JSONObject transform skips blacklisted fields")
    void testTransformJSONObjectBlacklistedFields() {
        JSONObject json = new JSONObject();
        json.put("id", "12345");              // exact blacklist
        json.put("customId", "67890");        // ends with "id"
        json.put("avatarUrl", "http://x");    // contains "url"
        json.put("homeUri", "urn:x");         // contains "uri"
        json.put("description", "desc text"); // filtered only when ignoreDescription
        json.put("summary", "keep me");

        StringBuilder result = StringUtils.transformJSONToText(new StringBuilder(), json, false);
        String output = result.toString();
        assertFalse(output.contains("12345"));
        assertFalse(output.contains("67890"));
        assertFalse(output.contains("http://x"));
        assertFalse(output.contains("urn:x"));
        assertTrue(output.contains("desc text"), "description kept when ignoreDescription=false");
        assertTrue(output.contains("summary: keep me"));

        StringBuilder ignored = StringUtils.transformJSONToText(new StringBuilder(), json, true);
        assertFalse(ignored.toString().contains("desc text"), "description dropped when ignoreDescription=true");
    }

    @Test
    @DisplayName("JSONObject transform skips blank values and JSONObject.NULL is rendered")
    void testTransformJSONObjectNullAndBlankValues() {
        JSONObject json = new JSONObject();
        json.put("nullField", JSONObject.NULL);
        json.put("blankField", "   ");
        json.put("realField", "value");

        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("nullField: null"), "JSONObject.NULL renders as 'null'");
        assertFalse(output.contains("blankField"), "blank values are skipped");
        assertTrue(output.contains("realField: value"));
    }

    @Test
    @DisplayName("JSONObject transform handles nested object")
    void testTransformJSONObjectNestedObject() {
        JSONObject nested = new JSONObject();
        nested.put("innerKey", "innerValue");
        JSONObject json = new JSONObject();
        json.put("parent", nested);

        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("parent: {"));
        assertTrue(output.contains("innerKey: innerValue"));
        assertTrue(output.contains("}"));
    }

    @Test
    @DisplayName("JSONObject transform handles arrays of objects and primitives")
    void testTransformJSONObjectArray() {
        JSONArray array = new JSONArray();
        array.put(new JSONObject().put("name", "first"));
        array.put("plain");
        array.put(42);
        JSONObject json = new JSONObject();
        json.put("items", array);

        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("items: ["));
        assertTrue(output.contains("name: first"));
        assertTrue(output.contains("plain"));
        assertTrue(output.contains("42"));
        assertTrue(output.contains("]\n"));
        assertTrue(output.contains(", "), "elements separated by comma");
    }

    // -------------------------------------------------------------------------
    // transformJSONToText(StringBuilder, String, boolean)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JSON string transform returns builder unchanged for null and blank input")
    void testTransformJSONStringNullAndBlank() {
        StringBuilder builder = new StringBuilder("keep");
        assertSame(builder, StringUtils.transformJSONToText(builder, (String) null, false));
        assertSame(builder, StringUtils.transformJSONToText(builder, "   ", false));
        assertEquals("keep", builder.toString());
    }

    @Test
    @DisplayName("JSON string transform appends 'Invalid JSON' for malformed input")
    void testTransformJSONStringInvalid() {
        String output = StringUtils.transformJSONToText(new StringBuilder(), "{not json", false).toString();
        assertEquals("Invalid JSON\n", output);
    }

    @Test
    @DisplayName("JSON string transform renders top-level array of objects as markdown table")
    void testTransformJSONStringTopLevelArrayTable() {
        String json = "[{\"name\":\"Alice\",\"role\":\"dev\"},{\"name\":\"Bob\",\"role\":\"qa\"}]";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("| name |") || output.contains("| role |"), "table header present");
        assertTrue(output.contains("Alice"));
        assertTrue(output.contains("Bob"));
        assertTrue(output.contains("dev"));
        assertTrue(output.contains("qa"));
    }

    @Test
    @DisplayName("Top-level array where all keys are blacklisted produces no table")
    void testTransformJSONStringArrayAllKeysBlacklisted() {
        String json = "[{\"id\":\"1\",\"url\":\"u\"}]";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertEquals("", output);
    }

    @Test
    @DisplayName("Top-level array of primitives is left untouched by yaml transform")
    void testTransformJSONStringTopLevelPrimitiveArray() {
        String output = StringUtils.transformJSONToText(new StringBuilder(), "[\"a\",\"b\"]", false).toString();
        assertEquals("", output);
    }

    @Test
    @DisplayName("JSON string transform renders simple fields and skips blacklisted ones")
    void testTransformJSONStringSimpleFields() {
        String json = "{\"summary\":\"Do the thing\",\"id\":\"X-1\",\"count\":7,\"flag\":true}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("summary: Do the thing"));
        assertTrue(output.contains("count: 7"));
        assertTrue(output.contains("flag: true"));
        assertFalse(output.contains("X-1"));
    }

    @Test
    @DisplayName("Multiline values are wrapped with TextStart/TextEnd markers")
    void testTransformJSONStringMultilineValue() {
        String json = "{\"body\":\"line one\\nline two\"}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("body: TextStart\n"));
        assertTrue(output.contains("line one\nline two"));
        assertTrue(output.contains("\nTextEnd"));
    }

    @Test
    @DisplayName("Empty primitive values render key with empty value")
    void testTransformJSONStringEmptyPrimitive() {
        String json = "{\"empty\":\"  \",\"filled\":\"x\"}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("empty: \n"));
        assertTrue(output.contains("filled: x"));
    }

    @Test
    @DisplayName("Simple arrays render inline with truncation beyond three items")
    void testTransformJSONStringSimpleArray() {
        String json = "{\"tags\":[\"a\",\"b\",\"c\"],\"many\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("tags: [a, b, c]"));
        assertTrue(output.contains("many: [1, 2, 3 and 2 more]"));
    }

    @Test
    @DisplayName("Simple array with non-primitive item renders ellipsis")
    void testTransformJSONStringSimpleArrayNonPrimitive() {
        String json = "{\"mixed\":[{\"k\":\"v\"}]}";
        // array of objects at top-level key -> full table; use nested object to force simple path
        String nestedJson = "{\"outer\":{\"mixed\":[\"x\",{\"k\":\"v\"}]}}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), nestedJson, false).toString();
        assertTrue(output.contains("mixed: [x, ...]"));
    }

    @Test
    @DisplayName("Nested objects recurse with indentation, empty nested objects are skipped")
    void testTransformJSONStringNestedObjects() {
        String json = "{\"outer\":{\"inner\":\"deep\"},\"hollow\":{}}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("outer:\n"));
        assertTrue(output.contains("  inner: deep"));
        assertFalse(output.contains("hollow"));
    }

    @Test
    @DisplayName("Nested array of objects renders summary and separate detail section")
    void testTransformJSONStringNestedArrayOfObjects() {
        String json = "{\"epic\":{\"stories\":[{\"name\":\"Story A\",\"points\":3},"
                + "{\"name\":\"Story B\",\"points\":5}]}}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("stories: [2 items: Story A, ...]"));
        assertTrue(output.contains("epic_stories:"), "nested section title combines path and array name");
        assertTrue(output.contains("Story B"));
    }

    @Test
    @DisplayName("Array summary falls back when first item has no name/title or is not an object")
    void testTransformJSONStringArraySummaryFallbacks() {
        String noName = "{\"epic\":{\"items\":[{\"points\":3},{\"points\":5}]}}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), noName, false).toString();
        assertTrue(output.contains("[2 items]"), "summary without name hint");
        assertTrue(output.contains("epic_items:"));

        String primitiveFirst = "{\"outer\":{\"things\":[\"plain\",{\"name\":\"obj\"}]}}";
        String primitiveOutput = StringUtils.transformJSONToText(new StringBuilder(), primitiveFirst, false).toString();
        assertTrue(primitiveOutput.contains("things: [plain, ...]"));
    }

    @Test
    @DisplayName("Table cells handle multiline, long, missing, nested-array and null values")
    void testTransformJSONStringTableCellVariants() {
        String longValue = "L".repeat(100);
        StringBuilder json = new StringBuilder("{\"rows\":[");
        json.append("{\"name\":\"Row One\",\"note\":\"multi\\nline\",\"big\":\"").append(longValue)
                .append("\",\"subs\":[{\"taskName\":\"Sub A\"}],\"tags\":[\"t1\",\"t2\"],\"nothing\":null},");
        json.append("{\"name\":\"Row Two\",\"tags\":[]}");
        json.append("]}");

        String output = StringUtils.transformJSONToText(new StringBuilder(), json.toString(), false).toString();
        assertTrue(output.contains("[TextStart...TextEnd]"), "multiline cell collapsed");
        assertTrue(output.contains("L".repeat(77) + "..."), "long cell truncated to 80 chars");
        assertTrue(output.contains("[1 items: Sub A]"), "nested object array summarized in cell");
        assertTrue(output.contains("rows_Row_One_subs:"), "nested section from table cell");
        assertTrue(output.contains("Row Two"), "second row rendered");
        assertTrue(output.contains("t1, t2"), "simple array cell");
        assertTrue(output.contains("- |"), "missing key rendered as dash");
        assertTrue(output.contains("..."), "null value rendered as ellipsis");
        assertTrue(output.contains("[]"), "empty simple array cell");
    }

    @Test
    @DisplayName("Array item name falls back to item index when no name/title field")
    void testTransformJSONStringTableNestedArrayItemNameFallback() {
        String json = "{\"rows\":[{\"weight\":9,\"subs\":[{\"points\":1}]}]}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("[1 items]"));
        assertTrue(output.contains("rows_item_0_subs:"), "fallback section name uses index");
    }

    @Test
    @DisplayName("Non-object entries inside arrays are ignored in table rows")
    void testTransformJSONStringTableSkipsNonObjectRows() {
        String json = "[\"stray\",{\"name\":\"Kept\"}]";
        // first element is not an object -> not treated as table; goes through yaml path (no-op)
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertEquals("", output);
    }

    @Test
    @DisplayName("Keys longer than 200 chars are truncated by escapeMarkdown")
    void testTransformJSONStringLongKeyTruncated() {
        String longKey = "k".repeat(250);
        String json = "[{\"" + longKey + "\":\"v\",\"short\":\"s\"}]";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("k".repeat(197) + "..."), "long header key truncated");
        assertFalse(output.contains("k".repeat(250)));
    }

    @Test
    @DisplayName("Simple array summary truncates long values and collapses overflow")
    void testTransformJSONStringSimpleArraySummaryTruncation() {
        String longItem = "v".repeat(30);
        String json = "[{\"name\":\"Row\",\"vals\":[\"" + longItem + "\",\"b\",\"c\",\"d\"]}]";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("v".repeat(12) + "..."), "long simple-array item truncated");
        assertTrue(output.contains("+1"), "overflow count appended");
    }

    @Test
    @DisplayName("Array item name with non-primitive name field falls back to index")
    void testTransformJSONStringNonPrimitiveNameFallback() {
        String json = "{\"rows\":[{\"name\":{\"nested\":true},\"subs\":[{\"x\":1}]}]}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("rows_item_0_subs:"));
    }

    @Test
    @DisplayName("Name fields are sanitized for section titles")
    void testTransformJSONStringSectionTitleSanitized() {
        String json = "{\"rows\":[{\"name\":\"My Row!\",\"subs\":[{\"subName\":\"Sub 1\"}]}]}";
        String output = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(output.contains("rows_My_Row__subs:"));
    }

    @Test
    @DisplayName("Description field honored in string transform when ignoreDescription=true")
    void testTransformJSONStringIgnoreDescription() {
        String json = "{\"description\":\"hidden\",\"summary\":\"shown\"}";
        String kept = StringUtils.transformJSONToText(new StringBuilder(), json, false).toString();
        assertTrue(kept.contains("hidden"));
        String dropped = StringUtils.transformJSONToText(new StringBuilder(), json, true).toString();
        assertFalse(dropped.contains("hidden"));
        assertTrue(dropped.contains("shown"));
    }

    // -------------------------------------------------------------------------
    // isConfluenceYamlFormat / extractYamlContentFromConfluence
    // -------------------------------------------------------------------------

    private static final String VALID_CONFLUENCE =
            "<ac:structured-macro ac:name=\"code\">"
            + "<ac:parameter ac:name=\"language\">yaml</ac:parameter>"
            + "<ac:plain-text-body><![CDATA[key: value]]></ac:plain-text-body>"
            + "</ac:structured-macro>";

    @Test
    @DisplayName("isConfluenceYamlFormat validates structure")
    void testIsConfluenceYamlFormat() {
        assertTrue(StringUtils.isConfluenceYamlFormat(VALID_CONFLUENCE));
        assertFalse(StringUtils.isConfluenceYamlFormat("plain text"), "wrong prefix");
        assertFalse(StringUtils.isConfluenceYamlFormat(
                "<ac:structured-macro>no language</ac:structured-macro>"), "missing language param");
        assertFalse(StringUtils.isConfluenceYamlFormat(
                "<ac:structured-macro><ac:parameter ac:name=\"language\">json</ac:parameter>"
                + "<ac:plain-text-body><![CDATA[x]]></ac:plain-text-body></ac:structured-macro>"),
                "non-yaml language");
        assertFalse(StringUtils.isConfluenceYamlFormat(
                "<ac:structured-macro><ac:parameter ac:name=\"language\">yaml</ac:parameter>"
                + "</ac:structured-macro>"), "missing CDATA body");
    }

    @Test
    @DisplayName("extractYamlContentFromConfluence extracts CDATA or returns input unchanged")
    void testExtractYamlContentFromConfluence() {
        assertEquals("key: value", StringUtils.extractYamlContentFromConfluence(VALID_CONFLUENCE));
        String plain = "not confluence at all";
        assertSame(plain, StringUtils.extractYamlContentFromConfluence(plain));
    }

    // -------------------------------------------------------------------------
    // removeUrls / extractUrls
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("removeUrls handles null, empty and strips http/ftp/www URLs")
    void testRemoveUrls() {
        assertNull(StringUtils.removeUrls(null));
        assertEquals("", StringUtils.removeUrls(""));
        assertEquals("see  and  and ",
                StringUtils.removeUrls("see http://example.com and ftp://files.example.com/a and www.example.org"));
    }

    @Test
    @DisplayName("extractUrls collects all URL matches")
    void testExtractUrls() {
        assertTrue(StringUtils.extractUrls(null).isEmpty());
        assertTrue(StringUtils.extractUrls("").isEmpty());
        List<String> urls = StringUtils.extractUrls("go https://a.example.com/x then www.b.example.org done");
        assertEquals(Arrays.asList("https://a.example.com/x", "www.b.example.org"), urls);
    }

    // -------------------------------------------------------------------------
    // cleanTextForMarkdown / cleanFieldName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cleanTextForMarkdown strips HTML, normalizes whitespace, escapes pipes")
    void testCleanTextForMarkdown() {
        assertEquals("", StringUtils.cleanTextForMarkdown(null));
        assertEquals("bold text a\\|b",
                StringUtils.cleanTextForMarkdown("<b>bold</b>  text\n a|b "));
    }

    @Test
    @DisplayName("cleanFieldName splits camelCase and capitalizes")
    void testCleanFieldName() {
        assertEquals("", StringUtils.cleanFieldName(null));
        assertEquals("", StringUtils.cleanFieldName(""));
        assertEquals("A", StringUtils.cleanFieldName("a"));
        assertEquals("Story points", StringUtils.cleanFieldName("storyPoints"));
        assertEquals("Already spaced", StringUtils.cleanFieldName("Already spaced"));
    }

    // -------------------------------------------------------------------------
    // convertToText
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("convertToText handles null, ToText, failing ToText and plain objects")
    void testConvertToText() {
        assertEquals("", StringUtils.convertToText(null));

        ToText good = () -> "text content";
        assertEquals("text content", StringUtils.convertToText(good));

        ToText failing = () -> { throw new IOException("boom"); };
        assertEquals("Error converting to text: boom", StringUtils.convertToText(failing));

        assertEquals("42", StringUtils.convertToText(42));
        assertEquals("plain", StringUtils.convertToText("plain"));
    }

    // -------------------------------------------------------------------------
    // splitLines / concatenate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("splitLines handles null, empty, LF and CRLF")
    void testSplitLines() {
        assertArrayEquals(new String[0], StringUtils.splitLines(null));
        assertArrayEquals(new String[0], StringUtils.splitLines(""));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtils.splitLines("a\nb\r\nc"));
    }

    @Test
    @DisplayName("concatenate handles arrays")
    void testConcatenateArray() {
        assertEquals("", StringUtils.concatenate(",", (String[]) null));
        assertEquals("", StringUtils.concatenate(",", new String[0]));
        assertEquals("a,b,c", StringUtils.concatenate(",", new String[]{"a", "b", "c"}));
    }

    @Test
    @DisplayName("concatenate handles collections")
    void testConcatenateCollection() {
        assertEquals("", StringUtils.concatenate(",", (List<String>) null));
        assertEquals("", StringUtils.concatenate(",", Collections.emptyList()));
        assertEquals("x|y", StringUtils.concatenate("|", Arrays.asList("x", "y")));
    }

    // -------------------------------------------------------------------------
    // convertToMarkdown
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("convertToMarkdown delegates to Jira markdown converter")
    void testConvertToMarkdown() {
        assertEquals("", StringUtils.convertToMarkdown(null));
        String result = StringUtils.convertToMarkdown("# Heading");
        assertNotNull(result);
        assertTrue(result.contains("Heading"));
    }

    // -------------------------------------------------------------------------
    // sortByTwoStrings
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sortByTwoStrings orders nulls last")
    void testSortByTwoStrings() {
        assertEquals(0, StringUtils.sortByTwoStrings(null, null));
        assertEquals(1, StringUtils.sortByTwoStrings(null, "b"));
        assertEquals(-1, StringUtils.sortByTwoStrings("a", null));
        assertTrue(StringUtils.sortByTwoStrings("a", "b") < 0);
        assertTrue(StringUtils.sortByTwoStrings("b", "a") > 0);
        assertEquals(0, StringUtils.sortByTwoStrings("same", "same"));
    }
}
