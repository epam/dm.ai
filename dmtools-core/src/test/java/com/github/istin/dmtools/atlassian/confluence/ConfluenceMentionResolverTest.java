// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConfluenceMentionResolverTest {

    private static String mention(String id) {
        return "<ac:link><ri:user ri:account-id=\"" + id + "\" ri:local-id=\"x\" /></ac:link>";
    }

    @Test
    public void replacesMentionWithDisplayNameAndCachesLookups() throws Exception {
        Confluence confluence = mock(Confluence.class);
        when(confluence.profile("acc-1")).thenReturn("{\"displayName\":\"Jane Roe\"}");

        String result = new ConfluenceMentionResolver(confluence)
                .resolve("<p>" + mention("acc-1") + " and " + mention("acc-1") + "</p>");

        assertEquals("<p>@Jane Roe and @Jane Roe</p>", result);
        verify(confluence, times(1)).profile("acc-1");
    }

    @Test
    public void escapesDisplayName() throws Exception {
        Confluence confluence = mock(Confluence.class);
        when(confluence.profile("acc-1")).thenReturn("{\"displayName\":\"A <b> & C\"}");

        assertEquals("@A &lt;b&gt; &amp; C", new ConfluenceMentionResolver(confluence).resolve(mention("acc-1")));
    }

    @Test
    public void unresolvableMentionIsLeftUntouched() throws Exception {
        Confluence confluence = mock(Confluence.class);
        when(confluence.profile("acc-1")).thenThrow(new java.io.IOException("404"));
        String html = "<p>" + mention("acc-1") + "</p>";

        assertEquals(html, new ConfluenceMentionResolver(confluence).resolve(html));
    }

    @Test
    public void pagesWithoutMentionsAreReturnedUnchanged() {
        Confluence confluence = mock(Confluence.class);
        String html = "<p>plain</p>";

        assertSame(html, new ConfluenceMentionResolver(confluence).resolve(html));
        verifyNoInteractions(confluence);
    }
}
