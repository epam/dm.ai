// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfluenceExcerptInlinerTest {

    private static final String TABLE = "<table><tbody><tr><th>Key</th><th>Value</th></tr><tr><td>alpha</td><td>1</td></tr></tbody></table>";

    private static Content page(String id, String space, String storage) {
        Storage s = mock(Storage.class);
        when(s.getValue()).thenReturn(storage);
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(id);
        when(content.getSpaceKey()).thenReturn(space);
        when(content.getStorage()).thenReturn(s);
        return content;
    }

    private static String excerpt(String macro, String name, String body) {
        return "<ac:structured-macro ac:name=\"" + macro + "\"><ac:parameter ac:name=\"name\">" + name
                + "</ac:parameter><ac:rich-text-body>" + body + "</ac:rich-text-body></ac:structured-macro>";
    }

    private static String include(String macro, String title, String spaceKey, String name) {
        return "<ac:structured-macro ac:name=\"" + macro + "\" ac:schema-version=\"1\">"
                + "<ac:parameter ac:name=\"page\"><ac:link><ri:page ri:content-title=\"" + title + "\""
                + (spaceKey == null ? "" : " ri:space-key=\"" + spaceKey + "\"") + " /></ac:link></ac:parameter>"
                + "<ac:parameter ac:name=\"name\">" + name + "</ac:parameter></ac:structured-macro>";
    }

    @Test
    public void inlinesNamedTableExcerptFromTargetPageInSourceSpace() throws Exception {
        Confluence confluence = mock(Confluence.class);
        String target = "<p>intro</p>" + excerpt("table-excerpt", "Rules", TABLE)
                + excerpt("table-excerpt", "Other", "<p>other</p>");
        Content stub1 = page("2", "SPACE", target);
        when(confluence.findContent("Target", "SPACE")).thenReturn(stub1);

        String result = new ConfluenceExcerptInliner(confluence)
                .inline("<p>before</p>" + include("table-excerpt-include", "Target", null, "Rules") + "<p>after</p>", "SPACE");

        assertTrue(result.contains(TABLE));
        assertFalse(result.contains("other"));
        assertFalse(result.contains("table-excerpt-include"));
        assertTrue(result.startsWith("<p>before</p>"));
        assertTrue(result.endsWith("<p>after</p>"));
    }

    @Test
    public void nameMatchingIgnoresCaseWhitespaceAndNbsp() throws Exception {
        Confluence confluence = mock(Confluence.class);
        String target = excerpt("excerpt", "&nbsp;Flow  Positions ", "<p>content</p>");
        Content stub2 = page("2", "SPACE", target);
        when(confluence.findContent("Target", "SPACE")).thenReturn(stub2);

        String result = new ConfluenceExcerptInliner(confluence)
                .inline(include("excerpt-include", "Target", "SPACE", "flow positions"), "SPACE");

        assertEquals("<p>content</p>", result);
    }

    @Test
    public void blankNameUsesUnnamedExcerptElseAllOfKind() throws Exception {
        Confluence confluence = mock(Confluence.class);
        String withUnnamed = excerpt("excerpt", "Named", "<p>named</p>") + excerpt("excerpt", "", "<p>unnamed</p>");
        String onlyNamed = excerpt("table-excerpt", "A", "<p>a</p>") + excerpt("table-excerpt", "B", "<p>b</p>");
        Content stub3 = page("1", "SPACE", withUnnamed);
        when(confluence.findContent("P1", "SPACE")).thenReturn(stub3);
        Content stub4 = page("2", "SPACE", onlyNamed);
        when(confluence.findContent("P2", "SPACE")).thenReturn(stub4);
        ConfluenceExcerptInliner inliner = new ConfluenceExcerptInliner(confluence);

        assertEquals("<p>unnamed</p>", inliner.inline(include("excerpt-include", "P1", null, ""), "SPACE"));
        assertEquals("<p>a</p><p>b</p>", inliner.inline(include("table-excerpt-include", "P2", null, ""), "SPACE"));
    }

    @Test
    public void unresolvableIncludesAreLeftUntouched() throws Exception {
        Confluence confluence = mock(Confluence.class);
        when(confluence.findContent("Missing", "SPACE")).thenReturn(null);
        when(confluence.findContent("Missing")).thenReturn(null);
        Content stub5 = page("2", "SPACE", excerpt("excerpt", "X", "<p>x</p>"));
        when(confluence.findContent("Present", "SPACE")).thenReturn(stub5);
        ConfluenceExcerptInliner inliner = new ConfluenceExcerptInliner(confluence);

        String missingPage = include("excerpt-include", "Missing", null, "X");
        String missingExcerpt = include("excerpt-include", "Present", null, "Nope");
        assertEquals(missingPage, inliner.inline(missingPage, "SPACE"));
        assertEquals(missingExcerpt, inliner.inline(missingExcerpt, "SPACE"));
    }

    @Test
    public void fallsBackToDefaultLookupWhenSpaceUnknown() throws Exception {
        Confluence confluence = mock(Confluence.class);
        Content stub6 = page("2", null, excerpt("excerpt", "X", "<p>x</p>"));
        when(confluence.findContent("Target")).thenReturn(stub6);

        String result = new ConfluenceExcerptInliner(confluence)
                .inline(include("excerpt-include", "Target", null, "X"), null);

        assertEquals("<p>x</p>", result);
    }

    @Test
    public void nestedIncludesAreResolvedWithTargetPageSpace() throws Exception {
        Confluence confluence = mock(Confluence.class);
        String outer = excerpt("excerpt", "Outer", include("excerpt-include", "Inner", null, "Leaf"));
        Content stub7 = page("1", "S2", outer);
        when(confluence.findContent("Outer", "S1")).thenReturn(stub7);
        Content stub8 = page("2", "S2", excerpt("excerpt", "Leaf", "<p>leaf</p>"));
        when(confluence.findContent("Inner", "S2")).thenReturn(stub8);

        String result = new ConfluenceExcerptInliner(confluence)
                .inline(include("excerpt-include", "Outer", null, "Outer"), "S1");

        assertEquals("<p>leaf</p>", result);
    }

    @Test
    public void includeCyclesTerminateAndKeepTheInnerMacro() throws Exception {
        Confluence confluence = mock(Confluence.class);
        String self = excerpt("excerpt", "Loop", include("excerpt-include", "Loop", null, "Loop"));
        Content stub9 = page("1", "SPACE", self);
        when(confluence.findContent("Loop", "SPACE")).thenReturn(stub9);

        String result = new ConfluenceExcerptInliner(confluence)
                .inline(include("excerpt-include", "Loop", null, "Loop"), "SPACE");

        assertTrue(result.contains("excerpt-include"));
        assertTrue(result.length() < 2000);
    }

    @Test
    public void pagesWithoutIncludesAreReturnedUnchanged() {
        Confluence confluence = mock(Confluence.class);
        String html = "<p>plain</p>";
        assertSame(html, new ConfluenceExcerptInliner(confluence).inline(html, "SPACE"));
        verifyNoInteractions(confluence);
    }

    @Test
    public void getSpaceKeyReadsExpandedSpaceOrExpandableLink() {
        assertEquals("ABC", new Content("{\"id\":\"1\",\"space\":{\"key\":\"ABC\"}}").getSpaceKey());
        assertEquals("XYZ", new Content("{\"id\":\"1\",\"_expandable\":{\"space\":\"/rest/api/space/XYZ\"}}").getSpaceKey());
        assertNull(new Content("{\"id\":\"1\"}").getSpaceKey());
    }
}
