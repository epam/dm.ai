// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Regression coverage for {@link MarkdownConfluenceSync#syncDirectory}, in particular the
 * root-node "own parent" bug: updating an existing target root page must preserve its real
 * current ancestor instead of falling back to the page's own ID.
 */
public class MarkdownConfluenceSyncTest {

    /**
     * Minimal in-memory fake of {@link MarkdownConfluenceSync.PageOperations} that records
     * every updatePage/createPage call and lets a test pre-seed an existing page's ancestor.
     */
    private static class FakePageOperations implements MarkdownConfluenceSync.PageOperations {
        final Map<String, String> parentsById = new HashMap<>();
        final List<String[]> updateCalls = new ArrayList<>(); // {contentId, parentId, body}
        int nextId = 1000;

        @Override
        public Content createPage(String title, String parentId, String body, String space) {
            String id = String.valueOf(nextId++);
            parentsById.put(id, parentId);
            return contentFor(id, title, parentId);
        }

        @Override
        public Content updatePage(String contentId, String title, String parentId, String body, String space, String historyComment) {
            updateCalls.add(new String[]{contentId, parentId, body});
            parentsById.put(contentId, parentId);
            return contentFor(contentId, title, parentId);
        }

        @Override
        public List<Content> getChildren(String contentId) {
            return new ArrayList<>();
        }

        @Override
        public String deletePage(String contentId) {
            return "deleted";
        }

        @Override
        public Content getContent(String contentId) throws IOException {
            String parentId = parentsById.get(contentId);
            return contentFor(contentId, "existing", parentId);
        }

        private Content contentFor(String id, String title, String parentId) {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("title", title);
            if (parentId != null) {
                JSONObject ancestor = new JSONObject().put("id", parentId);
                json.put("ancestors", new org.json.JSONArray().put(ancestor));
            }
            return new Content(json);
        }
    }

    private static class NoopAttachmentHelper extends ConfluenceAttachmentHelper {
        NoopAttachmentHelper() {
            super(contentId -> new ArrayList<Attachment>(), (contentId, file, updateIfExists) -> null);
        }
    }

    @Test
    public void syncDirectory_updatesRootPage_preservingItsRealExistingParent() throws IOException {
        File tempDir = File.createTempFile("md-sync-test", "");
        tempDir.delete();
        tempDir.mkdirs();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            // The root page ("555") already exists in Confluence under a real grandparent
            // ("999") — e.g. our discovery agent's ticket page nested under a project's
            // "Discovery" parent page. Before the fix, syncDirectory never looked this up,
            // so the update call below would have sent parentId="555" (itself).
            pageOps.parentsById.put("555", "999");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals("exactly one updatePage call for the root page", 1, pageOps.updateCalls.size());
            String[] call = pageOps.updateCalls.get(0);
            assertEquals("555", call[0]);
            assertEquals("update must preserve the real existing parent (999), not self-reference (555)",
                    "999", call[1]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_rootPageWithNoExistingParent_fallsBackGracefully() throws IOException {
        File tempDir = File.createTempFile("md-sync-test", "");
        tempDir.delete();
        tempDir.mkdirs();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            // Root page "555" has no known ancestor (e.g. a Confluence space's homepage) —
            // getParentId() legitimately returns null here; the sync must still complete
            // without throwing, falling back to the pre-fix self-reference behavior.
            pageOps.parentsById.put("555", null);

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals(1, pageOps.updateCalls.size());
            assertEquals("555", pageOps.updateCalls.get(0)[0]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_parentLookupFailure_isNonFatal() throws IOException {
        File tempDir = File.createTempFile("md-sync-test", "");
        tempDir.delete();
        tempDir.mkdirs();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations() {
                @Override
                public Content getContent(String contentId) throws IOException {
                    throw new IOException("Confluence unavailable");
                }
            };

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            // Must not throw — a failed parent lookup should not abort the whole sync.
            String summary = sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertNotNull(summary);
            assertEquals(1, pageOps.updateCalls.size());
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_childFile_becomesChildPageOfRoot() throws IOException {
        File tempDir = File.createTempFile("md-sync-test", "");
        tempDir.delete();
        tempDir.mkdirs();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");
            FileUtils.write(new File(tempDir, "prd.md"), "# PRD\ncontent", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.parentsById.put("555", "999");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            // 1 update for the root page body + 1 createPage + 1 updatePage for prd.md's
            // placeholder-then-real-content flow.
            assertEquals(2, pageOps.updateCalls.size());
            String[] rootUpdate = pageOps.updateCalls.get(0);
            assertEquals("555", rootUpdate[0]);
            assertEquals("999", rootUpdate[1]);

            String[] childUpdate = pageOps.updateCalls.get(1);
            assertEquals("child page's parent must be the root page (555), not the grandparent",
                    "555", childUpdate[1]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }
}
