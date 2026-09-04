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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for {@link MarkdownConfluenceSync#syncDirectory}, in particular two
 * root-node bugs found while updating an already-existing target page across repeated syncs:
 * (1) the update sent the page's own ID as its ancestor ("Can not set page as its own
 * parent"), and (2) the update silently renamed the page to the local root directory's
 * basename instead of preserving its real title (which can even fail outright with
 * "A page with this title already exists" if some unrelated page in the space happens to
 * share that literal folder name).
 */
public class MarkdownConfluenceSyncTest {

    /**
     * Minimal in-memory fake of {@link MarkdownConfluenceSync.PageOperations} that records
     * every updatePage/createPage call and lets a test pre-seed an existing page's title
     * and ancestor.
     */
    private static class FakePageOperations implements MarkdownConfluenceSync.PageOperations {
        final Map<String, String> parentsById = new HashMap<>();
        final Map<String, String> titlesById = new HashMap<>();
        final Map<String, String> bodiesById = new HashMap<>();
        final Map<String, List<String>> childrenByParent = new HashMap<>();
        final List<String[]> updateCalls = new ArrayList<>(); // {contentId, title, parentId, body}
        final List<String> createCalls = new ArrayList<>(); // titles, in creation order
        int nextId = 1000;

        @Override
        public Content createPage(String title, String parentId, String body, String space) {
            String id = String.valueOf(nextId++);
            parentsById.put(id, parentId);
            titlesById.put(id, title);
            bodiesById.put(id, body);
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
            createCalls.add(title);
            return contentFor(id, title, parentId, body);
        }

        @Override
        public Content updatePage(String contentId, String title, String parentId, String body, String space, String historyComment) {
            updateCalls.add(new String[]{contentId, title, parentId, body});
            parentsById.put(contentId, parentId);
            titlesById.put(contentId, title);
            bodiesById.put(contentId, body);
            return contentFor(contentId, title, parentId, body);
        }

        @Override
        public List<Content> getChildren(String contentId) {
            List<Content> result = new ArrayList<>();
            for (String childId : childrenByParent.getOrDefault(contentId, new ArrayList<>())) {
                result.add(contentFor(childId, titlesById.get(childId), parentsById.get(childId), bodiesById.get(childId)));
            }
            return result;
        }

        /**
         * Pre-seeds an existing child page (e.g. simulating a page left over from a
         * previous {@code syncDirectory} run) directly, without going through
         * {@link #createPage}.
         */
        void seedChild(String id, String parentId, String title, String body) {
            parentsById.put(id, parentId);
            titlesById.put(id, title);
            bodiesById.put(id, body);
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
        }

        @Override
        public String deletePage(String contentId) {
            return "deleted";
        }

        @Override
        public Content getContent(String contentId) throws IOException {
            String parentId = parentsById.get(contentId);
            String title = titlesById.containsKey(contentId) ? titlesById.get(contentId) : "existing";
            return contentFor(contentId, title, parentId, bodiesById.get(contentId));
        }

        private Content contentFor(String id, String title, String parentId) {
            return contentFor(id, title, parentId, null);
        }

        private Content contentFor(String id, String title, String parentId, String body) {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("title", title);
            if (parentId != null) {
                JSONObject ancestor = new JSONObject().put("id", parentId);
                json.put("ancestors", new org.json.JSONArray().put(ancestor));
            }
            if (body != null) {
                json.put("body", new JSONObject().put("storage",
                        new JSONObject().put("value", body).put("representation", "storage")));
            }
            return new Content(json);
        }
    }

    private static class NoopAttachmentHelper extends ConfluenceAttachmentHelper {
        NoopAttachmentHelper() {
            super(contentId -> new ArrayList<Attachment>(), (contentId, file, updateIfExists) -> null);
        }
    }

    private File newTempDir() throws IOException {
        File tempDir = File.createTempFile("md-sync-test", "");
        tempDir.delete();
        tempDir.mkdirs();
        return tempDir;
    }

    @Test
    public void syncDirectory_updatesRootPage_preservingItsRealExistingParent() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            // The root page ("555") already exists in Confluence under a real grandparent
            // ("999") — e.g. our discovery agent's ticket page nested under a project's
            // "Discovery" parent page. Before the fix, syncDirectory never looked this up,
            // so the update call below would have sent parentId="555" (itself).
            pageOps.parentsById.put("555", "999");
            pageOps.titlesById.put("555", "TICKET-123 Some feature");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals("exactly one updatePage call for the root page", 1, pageOps.updateCalls.size());
            String[] call = pageOps.updateCalls.get(0);
            assertEquals("555", call[0]);
            assertEquals("update must preserve the real existing parent (999), not self-reference (555)",
                    "999", call[2]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_updatesRootPage_preservingItsRealExistingTitle() throws IOException {
        File tempDir = newTempDir();
        try {
            // tempDir's basename (e.g. "md-sync-test1234567890") is NOT the desired page
            // title — the root page's REAL existing title ("TICKET-123 Some feature") must
            // be preserved, not overwritten with the local directory's basename.
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.parentsById.put("555", "999");
            pageOps.titlesById.put("555", "TICKET-123 Some feature");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            String[] call = pageOps.updateCalls.get(0);
            assertEquals("update must preserve the real existing title, not the local directory's basename",
                    "TICKET-123 Some feature", call[1]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_rootPageWithNoExistingParentOrTitle_fallsBackGracefully() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            // Root page "555" has no known ancestor (e.g. a Confluence space's homepage) —
            // getParentId() legitimately returns null here; the sync must still complete
            // without throwing, falling back to the pre-fix self-reference behavior.
            pageOps.parentsById.put("555", null);
            pageOps.titlesById.put("555", "");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals(1, pageOps.updateCalls.size());
            assertEquals("555", pageOps.updateCalls.get(0)[0]);
            // Empty existing title falls back to the directory-basename default (pre-fix
            // behavior) rather than sending an empty title.
            assertEquals(tempDir.getName(), pageOps.updateCalls.get(0)[1]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_parentLookupFailure_isNonFatal() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations() {
                @Override
                public Content getContent(String contentId) throws IOException {
                    throw new IOException("Confluence unavailable");
                }
            };

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            // Must not throw — a failed parent/title lookup should not abort the whole sync.
            String summary = sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertNotNull(summary);
            assertEquals(1, pageOps.updateCalls.size());
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_childFile_becomesChildPageOfRoot() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");
            FileUtils.write(new File(tempDir, "prd.md"), "# PRD\ncontent", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.parentsById.put("555", "999");
            pageOps.titlesById.put("555", "TICKET-123 Some feature");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            // 1 update for the root page body + 1 createPage + 1 updatePage for prd.md's
            // placeholder-then-real-content flow.
            assertEquals(2, pageOps.updateCalls.size());
            String[] rootUpdate = pageOps.updateCalls.get(0);
            assertEquals("555", rootUpdate[0]);
            assertEquals("TICKET-123 Some feature", rootUpdate[1]);
            assertEquals("999", rootUpdate[2]);

            String[] childUpdate = pageOps.updateCalls.get(1);
            assertEquals("child page's parent must be the root page (555), not the grandparent",
                    "555", childUpdate[2]);
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_childFile_rewordedH1OnRerun_updatesSamePageInsteadOfDuplicating() throws IOException {
        File tempDir = newTempDir();
        try {
            // Run 1: recommendations.md has one H1 heading.
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");
            FileUtils.write(new File(tempDir, "recommendations.md"), "# Recommendations\ncontent v1", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.parentsById.put("555", "999");
            pageOps.titlesById.put("555", "TICKET-123 Some feature");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals("exactly one child page created on the first run", 1, pageOps.createCalls.size());
            String childId = pageOps.childrenByParent.get("555").get(0);

            // Run 2: the SAME source file (recommendations.md) now has a reworded H1 — the
            // filename/identity of the document hasn't changed, only its title text. Before
            // the fix, findOrCreateChildPage matched purely by exact title equality, so this
            // would silently create a SECOND "Recommendations & Next Steps" child page
            // instead of updating the existing one.
            FileUtils.write(new File(tempDir, "recommendations.md"), "# Recommendations & Next Steps\ncontent v2", "UTF-8");
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals("re-run with a reworded H1 must NOT create a second child page",
                    1, pageOps.createCalls.size());
            assertEquals("exactly one child page must still exist under the root",
                    1, pageOps.childrenByParent.get("555").size());
            assertEquals("the existing page (by id) must be the one updated, not a new one",
                    childId, pageOps.childrenByParent.get("555").get(0));
            assertEquals("the existing page's title must be renamed to the new heading",
                    "Recommendations & Next Steps", pageOps.titlesById.get(childId));
            assertTrue("the updated body must contain the new content",
                    pageOps.bodiesById.get(childId).contains("content v2"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_childFile_legacyPageWithoutMarker_stillMatchesByTitleOnFirstPostFixSync() throws IOException {
        File tempDir = newTempDir();
        try {
            // Simulates a page created by a pre-fix version of this sync (no source-path
            // marker in its body yet) — the very first sync after upgrading must still find
            // it via the old title-matching fallback (not create a duplicate), and from then
            // on it carries the marker for future reworded-H1 reruns to match reliably.
            FileUtils.write(new File(tempDir, "index.md"), "# Landing page", "UTF-8");
            FileUtils.write(new File(tempDir, "recommendations.md"), "# Recommendations\ncontent v1", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.parentsById.put("555", "999");
            pageOps.titlesById.put("555", "TICKET-123 Some feature");
            pageOps.seedChild("2000", "555", "Recommendations", "<p>legacy body, no marker</p>");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            assertEquals("must update the pre-existing legacy page, not create a new one",
                    0, pageOps.createCalls.size());
            assertEquals("2000", pageOps.updateCalls.get(pageOps.updateCalls.size() - 1)[0]);
            assertTrue("the marker must now be present for future reworded-H1 reruns to match",
                    pageOps.bodiesById.get("2000").contains("dmtools:source-path=recommendations.md"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    private static final String REF_A = "a40ec14d-0d73-4f55-9a69-7026900b6623";

    @Test
    public void syncDirectory_preservesInlineCommentMarker_fromExistingBody() throws IOException {
        File tempDir = newTempDir();
        try {
            // New content still contains the anchored text but no marker (Markdown never
            // carries one) — the sync must re-wrap the text with the original marker.
            FileUtils.write(new File(tempDir, "index.md"), "# Landing\n\n- Item one\n- Item two\n", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.titlesById.put("555", "TICKET-123 Some feature");
            pageOps.bodiesById.put("555",
                    "<ul><li><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Item one</ac:inline-comment-marker></li><li>Item two</li></ul>");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            String updatedBody = pageOps.updateCalls.get(0)[3];
            assertTrue("marker with original ref must be re-applied around the anchor text",
                    updatedBody.contains("<ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Item one</ac:inline-comment-marker>"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_convertsPlaceholderInMarkdown_intoRealMarker() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"),
                    "# Landing\n\n- [[ic:" + REF_A + "]]Item one[[/ic]]\n", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.titlesById.put("555", "TICKET-123 Some feature");
            pageOps.bodiesById.put("555", "<p>old body without markers</p>");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            String updatedBody = pageOps.updateCalls.get(0)[3];
            assertTrue("placeholder must become a real marker",
                    updatedBody.contains("<ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Item one</ac:inline-comment-marker>"));
            assertFalse("no leftover placeholder tags", updatedBody.contains("[[ic:"));
            assertFalse("no leftover placeholder close tags", updatedBody.contains("[[/ic]]"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_preserveInlineCommentsFalse_leavesPlaceholdersUntouched() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"),
                    "# Landing\n\n- [[ic:" + REF_A + "]]Item one[[/ic]]\n", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.titlesById.put("555", "TICKET-123 Some feature");
            pageOps.bodiesById.put("555",
                    "<ul><li><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Item one</ac:inline-comment-marker></li></ul>");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null, false);

            String updatedBody = pageOps.updateCalls.get(0)[3];
            assertFalse("marker must NOT be re-applied when preservation is disabled",
                    updatedBody.contains("ac:inline-comment-marker"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void syncDirectory_anchorTextGone_doesNotFailSync() throws IOException {
        File tempDir = newTempDir();
        try {
            FileUtils.write(new File(tempDir, "index.md"), "# Landing\n\n- completely new content\n", "UTF-8");

            FakePageOperations pageOps = new FakePageOperations();
            pageOps.titlesById.put("555", "TICKET-123 Some feature");
            pageOps.bodiesById.put("555",
                    "<ul><li><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Item one</ac:inline-comment-marker></li></ul>");

            MarkdownConfluenceSync sync = new MarkdownConfluenceSync(new NoopAttachmentHelper(), pageOps);
            sync.syncDirectory(tempDir, "555", "SPACE", false, null);

            String updatedBody = pageOps.updateCalls.get(0)[3];
            assertFalse(updatedBody.contains("ac:inline-comment-marker"));
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }
}
