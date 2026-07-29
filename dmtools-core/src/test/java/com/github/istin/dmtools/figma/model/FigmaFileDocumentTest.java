// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FigmaFileDocument} covering constructors, simple JSON
 * accessors and the exportability / categorisation logic
 * ({@code isExportableVisualElement}, {@code getSupportedFormats},
 * {@code isLikelyIcon}, {@code isLikelyIllustration}, {@code getElementCategory}
 * and {@code findComponentsRecursively}).
 */
public class FigmaFileDocumentTest {

    private static JSONObject node(String type, double width, double height) {
        JSONObject json = new JSONObject();
        json.put("id", "1:2");
        json.put("name", "node");
        json.put("type", type);
        JSONObject bounds = new JSONObject();
        bounds.put("width", width);
        bounds.put("height", height);
        json.put("absoluteBoundingBox", bounds);
        return json;
    }

    @Test
    public void testConstructors() {
        FigmaFileDocument empty = new FigmaFileDocument();
        assertNull(empty.getId());
        assertNull(empty.getName());
        assertNull(empty.getType());

        FigmaFileDocument fromString = new FigmaFileDocument("{\"id\":\"1:1\",\"name\":\"root\",\"type\":\"FRAME\"}");
        assertEquals("1:1", fromString.getId());
        assertEquals("root", fromString.getName());
        assertEquals("FRAME", fromString.getType());

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", "2:2");
        FigmaFileDocument fromJsonObject = new FigmaFileDocument(jsonObject);
        assertEquals("2:2", fromJsonObject.getId());
    }

    @Test
    public void testVisibilityAndOpacity() {
        JSONObject json = node("RECTANGLE", 10, 10);
        json.put("visible", false);
        json.put("opacity", 0.5);
        FigmaFileDocument doc = new FigmaFileDocument(json);
        assertFalse(doc.isVisible());
        assertEquals(0.5, doc.getOpacity(), 0.0001);

        FigmaFileDocument defaults = new FigmaFileDocument(node("RECTANGLE", 10, 10));
        // "visible" key absent -> getBoolean returns false
        assertFalse(defaults.isVisible());
        // "opacity" key absent -> defaults to fully opaque
        assertEquals(1.0, defaults.getOpacity(), 0.0001);
    }

    @Test
    public void testExportSettings() {
        FigmaFileDocument none = new FigmaFileDocument(node("RECTANGLE", 10, 10));
        assertFalse(none.hasExportSettings());

        JSONObject emptyJson = node("RECTANGLE", 10, 10);
        emptyJson.put("exportSettings", new JSONArray());
        assertFalse(new FigmaFileDocument(emptyJson).hasExportSettings());

        JSONObject withSettings = node("RECTANGLE", 10, 10);
        withSettings.put("exportSettings", new JSONArray().put(new JSONObject().put("format", "PNG")));
        assertTrue(new FigmaFileDocument(withSettings).hasExportSettings());
    }

    @Test
    public void testBoundsWidthHeight() {
        FigmaFileDocument doc = new FigmaFileDocument(node("RECTANGLE", 24.5, 16));
        assertNotNull(doc.getAbsoluteBoundingBox());
        assertEquals(24.5, doc.getWidth(), 0.0001);
        assertEquals(16, doc.getHeight(), 0.0001);

        FigmaFileDocument noBounds = new FigmaFileDocument(new JSONObject());
        assertNull(noBounds.getAbsoluteBoundingBox());
        assertEquals(0, noBounds.getWidth(), 0.0001);
        assertEquals(0, noBounds.getHeight(), 0.0001);
    }

    @Test
    public void testFillsStrokesChildrenFlags() {
        FigmaFileDocument empty = new FigmaFileDocument(new JSONObject());
        assertFalse(empty.hasFills());
        assertFalse(empty.hasStrokes());
        assertFalse(empty.hasChildren());

        JSONObject json = node("RECTANGLE", 10, 10);
        json.put("fills", new JSONArray());
        json.put("strokes", new JSONArray());
        FigmaFileDocument doc = new FigmaFileDocument(json);
        assertTrue(doc.hasFills());
        assertTrue(doc.hasStrokes());
        assertFalse(doc.hasChildren());

        json.put("children", new JSONArray().put(node("VECTOR", 5, 5)));
        assertTrue(new FigmaFileDocument(json).hasChildren());
    }

    @Test
    public void testGetChildren() {
        JSONObject json = node("FRAME", 100, 100);
        JSONArray children = new JSONArray();
        children.put(node("VECTOR", 5, 5));
        children.put(node("TEXT", 20, 10));
        json.put("children", children);

        FigmaFileDocument doc = new FigmaFileDocument(json);
        List<FigmaFileDocument> result = doc.getChildren();
        assertEquals(2, result.size());
        assertEquals("VECTOR", result.get(0).getType());
        assertEquals("TEXT", result.get(1).getType());

        FigmaFileDocument noChildren = new FigmaFileDocument(new JSONObject());
        assertTrue(noChildren.getChildren().isEmpty());
    }

    @Test
    public void testIsExportableVisualElementAllSupportedTypes() {
        String[] types = {"VECTOR", "BOOLEAN_OPERATION", "RECTANGLE", "ELLIPSE", "POLYGON",
                "STAR", "LINE", "FRAME", "GROUP", "COMPONENT", "INSTANCE", "COMPONENT_SET", "TEXT"};
        for (String type : types) {
            assertTrue("type " + type + " should be exportable",
                    new FigmaFileDocument(node(type, 24, 24)).isExportableVisualElement());
        }
    }

    @Test
    public void testIsExportableVisualElementUnsupportedType() {
        assertFalse(new FigmaFileDocument(node("SLICE", 24, 24)).isExportableVisualElement());
        assertFalse(new FigmaFileDocument(node("DOCUMENT", 24, 24)).isExportableVisualElement());
    }

    @Test
    public void testIsExportableVisualElementComplexIdFiltering() {
        JSONObject simple = node("VECTOR", 24, 24);
        simple.put("id", "I23275:360348;21179:261321"); // 2 parts - kept
        assertTrue(new FigmaFileDocument(simple).isExportableVisualElement());

        JSONObject complex = node("VECTOR", 24, 24);
        complex.put("id", "I23275:360346;4006:42414;378:5885;38:2108"); // 4 parts - filtered
        assertFalse(new FigmaFileDocument(complex).isExportableVisualElement());
    }

    @Test
    public void testIsExportableVisualElementVisibilityAndOpacity() {
        JSONObject invisible = node("RECTANGLE", 24, 24);
        invisible.put("visible", false);
        assertFalse(new FigmaFileDocument(invisible).isExportableVisualElement());

        JSONObject visible = node("RECTANGLE", 24, 24);
        visible.put("visible", true);
        assertTrue(new FigmaFileDocument(visible).isExportableVisualElement());

        JSONObject transparent = node("RECTANGLE", 24, 24);
        transparent.put("opacity", 0.0);
        assertFalse(new FigmaFileDocument(transparent).isExportableVisualElement());

        JSONObject opaque = node("RECTANGLE", 24, 24);
        opaque.put("opacity", 0.8);
        assertTrue(new FigmaFileDocument(opaque).isExportableVisualElement());
    }

    @Test
    public void testIsExportableVisualElementZeroDimensions() {
        assertFalse(new FigmaFileDocument(node("RECTANGLE", 0, 24)).isExportableVisualElement());
        assertFalse(new FigmaFileDocument(node("RECTANGLE", 24, 0)).isExportableVisualElement());
        assertFalse(new FigmaFileDocument(new JSONObject("{\"type\":\"RECTANGLE\"}")).isExportableVisualElement());
    }

    @Test
    public void testIsExportableVisualElementLargeContainers() {
        // Large FRAME/GROUP containers are skipped
        assertFalse(new FigmaFileDocument(node("FRAME", 300, 300)).isExportableVisualElement());
        assertFalse(new FigmaFileDocument(node("GROUP", 300, 300)).isExportableVisualElement());
        // Only one dimension large -> kept
        assertTrue(new FigmaFileDocument(node("FRAME", 300, 50)).isExportableVisualElement());
        // Large non-container types are kept
        assertTrue(new FigmaFileDocument(node("VECTOR", 300, 300)).isExportableVisualElement());
        assertTrue(new FigmaFileDocument(node("RECTANGLE", 300, 300)).isExportableVisualElement());
    }

    @Test
    public void testGetSupportedFormats() {
        List<String> vectorFormats = new FigmaFileDocument(node("VECTOR", 24, 24)).getSupportedFormats();
        assertEquals(List.of("png", "jpg", "svg"), vectorFormats);

        List<String> frameFormats = new FigmaFileDocument(node("FRAME", 24, 24)).getSupportedFormats();
        assertEquals(List.of("png", "jpg", "pdf"), frameFormats);

        List<String> componentFormats = new FigmaFileDocument(node("COMPONENT", 24, 24)).getSupportedFormats();
        assertEquals(List.of("png", "jpg", "pdf"), componentFormats);

        List<String> componentSetFormats = new FigmaFileDocument(node("COMPONENT_SET", 24, 24)).getSupportedFormats();
        assertEquals(List.of("png", "jpg", "pdf"), componentSetFormats);

        List<String> textFormats = new FigmaFileDocument(node("TEXT", 24, 24)).getSupportedFormats();
        assertEquals(List.of("png", "jpg"), textFormats);
    }

    @Test
    public void testIsVectorBased() {
        String[] vectorTypes = {"VECTOR", "BOOLEAN_OPERATION", "RECTANGLE", "ELLIPSE", "POLYGON", "STAR", "LINE"};
        for (String type : vectorTypes) {
            assertTrue("type " + type, new FigmaFileDocument(node(type, 10, 10)).isVectorBased());
        }
        assertFalse(new FigmaFileDocument(node("FRAME", 10, 10)).isVectorBased());
        assertFalse(new FigmaFileDocument(node("TEXT", 10, 10)).isVectorBased());
        assertFalse(new FigmaFileDocument(new JSONObject()).isVectorBased());
    }

    @Test
    public void testIsLikelyIconByName() {
        String[] iconNames = {"App Icon", "chevron-down", "Arrow left", "menu button", "exit", "status badge", "icon ♣"};
        for (String name : iconNames) {
            JSONObject json = node("GROUP", 500, 500);
            json.put("name", name);
            assertTrue("name " + name, new FigmaFileDocument(json).isLikelyIcon());
        }

        JSONObject plain = node("GROUP", 500, 500);
        plain.put("name", "random container");
        assertFalse(new FigmaFileDocument(plain).isLikelyIcon());
    }

    @Test
    public void testIsLikelyIconByTypeAndSize() {
        assertTrue(new FigmaFileDocument(node("COMPONENT", 24, 24)).isLikelyIcon());
        assertTrue(new FigmaFileDocument(node("INSTANCE", 48, 48)).isLikelyIcon());
        assertFalse(new FigmaFileDocument(node("COMPONENT", 100, 100)).isLikelyIcon());
        assertFalse(new FigmaFileDocument(node("COMPONENT", 0, 24)).isLikelyIcon());

        assertTrue(new FigmaFileDocument(node("VECTOR", 20, 20)).isLikelyIcon());
        assertFalse(new FigmaFileDocument(node("VECTOR", 100, 100)).isLikelyIcon());

        assertTrue(new FigmaFileDocument(node("RECTANGLE", 50, 50)).isLikelyIcon());
        assertTrue(new FigmaFileDocument(node("ELLIPSE", 30, 30)).isLikelyIcon());
        assertFalse(new FigmaFileDocument(node("ELLIPSE", 60, 60)).isLikelyIcon());

        assertFalse(new FigmaFileDocument(node("TEXT", 20, 20)).isLikelyIcon());
    }

    @Test
    public void testIsLikelyIllustrationByName() {
        String[] illustrationNames = {"Hero illustration", "main graphic", "profile image",
                "master layout", "page header", "content section", "tab services"};
        for (String name : illustrationNames) {
            JSONObject json = node("RECTANGLE", 10, 10);
            json.put("name", name);
            assertTrue("name " + name, new FigmaFileDocument(json).isLikelyIllustration());
        }

        // "tab" alone without "services" is not an illustration name
        JSONObject tabOnly = node("RECTANGLE", 10, 10);
        tabOnly.put("name", "tab");
        assertFalse(new FigmaFileDocument(tabOnly).isLikelyIllustration());

        JSONObject plain = node("RECTANGLE", 10, 10);
        plain.put("name", "something else");
        assertFalse(new FigmaFileDocument(plain).isLikelyIllustration());
    }

    @Test
    public void testIsLikelyIllustrationByTypeAndSize() {
        assertTrue(new FigmaFileDocument(node("FRAME", 300, 150)).isLikelyIllustration());
        assertFalse(new FigmaFileDocument(node("FRAME", 100, 50)).isLikelyIllustration());

        assertTrue(new FigmaFileDocument(node("GROUP", 150, 150)).isLikelyIllustration());
        assertFalse(new FigmaFileDocument(node("GROUP", 50, 50)).isLikelyIllustration());

        assertTrue(new FigmaFileDocument(node("VECTOR", 150, 150)).isLikelyIllustration());
        assertFalse(new FigmaFileDocument(node("VECTOR", 50, 50)).isLikelyIllustration());

        assertFalse(new FigmaFileDocument(node("TEXT", 500, 500)).isLikelyIllustration());
    }

    @Test
    public void testGetElementCategory() {
        JSONObject icon = node("VECTOR", 20, 20);
        assertEquals("icon", new FigmaFileDocument(icon).getElementCategory());

        assertEquals("illustration", new FigmaFileDocument(node("GROUP", 150, 150)).getElementCategory());

        assertEquals("text", new FigmaFileDocument(node("TEXT", 300, 300)).getElementCategory());

        // Non-icon, non-illustration, non-text -> generic graphic
        assertEquals("graphic", new FigmaFileDocument(node("STAR", 300, 300)).getElementCategory());
    }

    @Test
    public void testFindComponentsRecursively() {
        // Root FRAME (large container, not exportable) with exportable children
        JSONObject root = node("FRAME", 500, 500);
        JSONArray children = new JSONArray();

        JSONObject icon = node("VECTOR", 20, 20);
        icon.put("name", "chevron icon");
        children.put(icon);

        JSONObject invisible = node("RECTANGLE", 20, 20);
        invisible.put("visible", false);
        children.put(invisible);

        // Child without id -> falls back to generated childId
        JSONObject noId = node("TEXT", 30, 10);
        noId.remove("id");
        children.put(noId);

        root.put("children", children);

        List<FigmaIcon> components = new java.util.ArrayList<>();
        new FigmaFileDocument(root).findComponentsRecursively("root", components);

        assertEquals(2, components.size());
        assertEquals("1:2", components.get(0).getString("id"));
        assertEquals("icon", components.get(0).getString("category"));
        assertEquals("root_child_2", components.get(1).getString("id"));
        assertEquals("text", components.get(1).getString("category"));
    }

    @Test
    public void testFindComponentsRecursivelyNodeWithoutIdAndName() {
        // Node without id/name/type -> falls back to the passed nodeId and defaults
        JSONObject json = node("RECTANGLE", 20, 20);
        json.remove("id");
        json.remove("name");
        FigmaFileDocument doc = new FigmaFileDocument(json);

        List<FigmaIcon> components = new java.util.ArrayList<>();
        doc.findComponentsRecursively("fallback", components);

        assertEquals(1, components.size());
        assertEquals("fallback", components.get(0).getString("id"));
        assertEquals("", components.get(0).getString("name"));
        assertEquals("RECTANGLE", components.get(0).getString("type"));
    }

    @Test
    public void testFindComponentsRecursivelyNoChildrenNoMatch() {
        FigmaFileDocument doc = new FigmaFileDocument(node("SLICE", 20, 20));
        List<FigmaIcon> components = new java.util.ArrayList<>();
        doc.findComponentsRecursively("root", components);
        assertTrue(components.isEmpty());
    }
}
