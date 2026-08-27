// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import junit.framework.TestCase;

public class HtmlCleanerTest extends TestCase {
    String basePath = "http://example.com/";
    public void testCleanUselessHTMLTags() {
        String taggedInput = "<html><body><p style=\"color:red;\" class=\"myClass\">Hello, world!</p><img src=\"image.jpg\"/></body></html>";
        String expectedOutput = "<p>Hello, world!</p><img src=\"http://example.com/image.jpg\">";
        assertEquals(expectedOutput, HtmlCleaner.cleanUselessHTMLTagsAndAdjustImageUrls(basePath, taggedInput));
    }

    public void testCleanAllHtmlTags() {
        String html = "<html><body><p>Hello, world!</p></body></html>";
        String expectedOutput = "Hello, world!";
        assertEquals(expectedOutput, HtmlCleaner.cleanAllHtmlTags(basePath, html));
    }

    //<p><strong>As a business user I want to be notified on the Dashboard that some of my Cases require actions so that I can proceed with them.</strong></p><p><strong>AC:</strong></p><ol><li>Dashboard contains a component to notify that there's action required within cases (if user doesn't do that the case cannot move on).</li><li>If there is 1 case required for action, then "Naar Cases" link takes the User to the case details page.</li><li>If there are multiple cases required for action, then "Naar Cases" link takes the User to case overview page.</li><li>If there is no required actions for any cases, this component is not displayed on the Dashboard.</li><li>Tagplan.</li></ol><p>API:<a href="https://postnl.atlassian.net/wiki/spaces/SC/pages/3586129921/Case+Experience+API+Overview"> https://postnl.atlassian.net/wiki/spaces/SC/pages/3586129921/Case+Experience+API+Overview</a></p><p><strong>Figma </strong>- <a href="https://www.figma.com/file/cJ4l63XhXiH6ZVYTNC6qpK/PostNL_Zakelijk-App-Discovery-prototype?type=design&amp;node-id=1059-21626&amp;mode=design&amp;t=RXiONuaamxnsJwCQ-0">https://www.figma.com/file/cJ4l63XhXiH6ZVYTNC6qpK/PostNL_Zakelijk-App-Discovery-prototype?type=design&amp;node-id=1059-21626&amp;mode=design&amp;t=RXiONuaamxnsJwCQ-0</a></p><p><strong>Design</strong></p><figure><img src="https://rally1.rallydev.com//slm/attachment/728992815663/Dashboard – shipments to drop + delayed + Actions needed.png"></figure><p><strong>Order of components on Dashboard:</strong></p><figure><img src="https://rally1.rallydev.com//slm/attachment/728549952337/Order of components on Dashboard.png"></figure>
    public void testConvertLinksUrlsToConfluenceFormat() {
        String body = "<html><body><a href=\"http://example.com?param1=value1&param2=value2\">Link</a></body></html>";
        String expectedOutput = "<a href=\"http://example.com?param1=value1&amp;param2=value2\">Link</a>";
        String convertedOutput = HtmlCleaner.convertLinksUrlsToConfluenceFormat(body);
        assertEquals(expectedOutput, convertedOutput);
    }


    public void testPreservingCdata_keepsCodeMacroContent() {
        String body = "<h1>Title</h1>"
                + "<ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\">"
                + "<ac:parameter ac:name=\"language\">mermaid</ac:parameter>"
                + "<ac:plain-text-body><![CDATA[flowchart TD\nA --> B]]></ac:plain-text-body>"
                + "</ac:structured-macro>"
                + "<p>after <a href=\"https://example.com\">link</a></p>";
        String result = HtmlCleaner.convertLinksUrlsToConfluenceFormatPreservingCdata(body);
        assertTrue("CDATA content must survive",
                result.contains("<![CDATA[flowchart TD\nA --> B]]>"));
        assertTrue("link must still be processed",
                result.contains("https://example.com"));
        assertFalse("masking token must not leak", result.contains("DMTOOLS_MACRO_"));
    }

    public void testPreservingCdata_noMacros_behavesLikePlainConversion() {
        String body = "<p>hello <a href=\"https://example.com\">link</a></p>";
        String result = HtmlCleaner.convertLinksUrlsToConfluenceFormatPreservingCdata(body);
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("https://example.com"));
    }

    public void testPlainConversion_destroysCdata_regressionDocumentation() {
        // Documents WHY the preserving variant exists: Jsoup HTML parsing converts the
        // CDATA section into a bogus comment, so the macro body is no longer a CDATA
        // section — Confluence then stores the macro as EMPTY.
        String body = "<ac:structured-macro ac:name=\"code\">"
                + "<ac:plain-text-body><![CDATA[graph TD\nA --> B]]></ac:plain-text-body>"
                + "</ac:structured-macro>";
        String result = HtmlCleaner.convertLinksUrlsToConfluenceFormat(body);
        assertFalse("real CDATA markers must be gone after plain conversion",
                result.contains("<![CDATA["));
        assertTrue("text degrades into a bogus comment",
                result.contains("<!--[CDATA["));
    }
}
