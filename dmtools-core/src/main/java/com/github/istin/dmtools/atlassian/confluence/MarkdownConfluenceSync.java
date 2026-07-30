// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates synchronizing a local Markdown directory tree with a Confluence page subtree.
 *
 * <p>The class is stateless apart from the collaborators it receives, making it easy to
 * unit-test without a live Confluence instance.</p>
 */
public class MarkdownConfluenceSync {

    private static final Logger logger = LogManager.getLogger(MarkdownConfluenceSync.class);

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]*)\\]\\(([^)]+)\\)", Pattern.DOTALL);
    private static final Pattern IMAGE_ATTACHMENT_PATTERN = Pattern.compile(
            "<ac:image[^>]*>.*?<ri:attachment[^>]*ri:filename=\"([^\"]+)\".*?>.*?</ac:image>", Pattern.DOTALL);
    private static final Pattern LINK_ATTACHMENT_PATTERN = Pattern.compile(
            "<ac:link[^>]*>.*?<ri:attachment[^>]*ri:filename=\"([^\"]+)\".*?>.*?</ac:link>", Pattern.DOTALL);
    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    private final ConfluenceAttachmentHelper attachmentHelper;
    private final PageOperations pageOps;

    /**
     * Callbacks for Confluence page CRUD operations.
     */
    public interface PageOperations {
        Content createPage(String title, String parentId, String body, String space) throws IOException;
        Content updatePage(String contentId, String title, String parentId, String body, String space, String historyComment) throws IOException;
        List<Content> getChildren(String contentId) throws IOException;
        String deletePage(String contentId) throws IOException;
        /**
         * Fetches a page's current metadata, including its ancestors — used only to look up
         * the ROOT target page's existing real parent before updating it (see
         * {@link #syncDirectory}). Not needed for any other node in the tree, since every
         * other node's parentId is set explicitly as it is created during the sync walk.
         */
        Content getContent(String contentId) throws IOException;
    }

    public MarkdownConfluenceSync(ConfluenceAttachmentHelper attachmentHelper, PageOperations pageOps) {
        this.attachmentHelper = attachmentHelper;
        this.pageOps = pageOps;
    }

    /**
     * Synchronizes a local directory tree to a Confluence page subtree.
     */
    public String syncDirectory(File rootDir, String parentId, String space,
                                boolean deleteOrphans, String attachmentsDir) throws IOException {
        DirNode root = buildDirTree(rootDir);
        Map<String, String> pathToTitle = buildPathToTitleMap(rootDir, root);
        Set<String> expectedTitles = new HashSet<>(pathToTitle.values());

        root.contentId = parentId;
        // The root node's own page (parentId) is an EXISTING page being updated in place
        // (its body becomes rootDir's index.md) — unlike every other node in the tree,
        // it was never "created" during this sync walk, so nothing ever set its
        // parentId. Without this lookup, syncDirNode's fallback (node.parentId != null
        // ? node.parentId : node.contentId) would pass the page's OWN id as its ancestor
        // on update, which Confluence rejects with "Can not set page as its own parent."
        // Fetch its actual current parent so the update preserves it unchanged.
        try {
            root.parentId = pageOps.getContent(parentId).getParentId();
        } catch (IOException e) {
            logger.warn("syncDirectory: failed to look up existing parent of root page {} — proceeding without it: {}", parentId, e.getMessage());
        }
        syncDirNode(root, rootDir, pathToTitle, space, attachmentsDir);

        List<String> deleted = new ArrayList<>();
        if (deleteOrphans) {
            deleted = deleteOrphanPages(parentId, expectedTitles);
        }

        JSONObject summary = new JSONObject();
        summary.put("rootDirectory", rootDir.getAbsolutePath());
        summary.put("parentId", parentId);
        summary.put("expectedPages", expectedTitles.size());
        summary.put("syncedPages", new JSONArray(expectedTitles.stream().sorted().toList()));
        summary.put("deleted", new JSONArray(deleted));
        return summary.toString();
    }

    private void syncDirNode(DirNode node, File rootDir, Map<String, String> pathToTitle,
                             String space, String attachmentsDir) throws IOException {
        String folderBody;
        Set<String> referencedAttachmentNames = new LinkedHashSet<>();
        File baseDir;
        if (node.indexFile != null) {
            String markdown = readFile(node.indexFile);
            String processed = processMarkdownLinks(markdown, rootDir, node.indexFile, pathToTitle);
            folderBody = MarkdownToConfluenceStorage.toStorage(processed);
            referencedAttachmentNames.addAll(MarkdownToConfluenceStorage.extractAttachmentReferences(processed));
            baseDir = resolveAttachmentsDir(node.indexFile, attachmentsDir);
            attachmentHelper.uploadReferencedAttachments(node.contentId, processed, baseDir, false);
        } else {
            folderBody = "<p>" + MarkdownToConfluenceStorage.escapeXml(node.title) + "</p>";
        }

        List<String> uploadedAttachmentNames = uploadDirectoryAttachments(node, referencedAttachmentNames);
        folderBody = appendAttachmentLinks(folderBody, uploadedAttachmentNames);
        pageOps.updatePage(node.contentId, node.title,
                node.parentId != null ? node.parentId : node.contentId,
                folderBody, space, "");

        for (FileNode fileNode : node.files) {
            String markdown = readFile(fileNode.file);
            String processed = processMarkdownLinks(markdown, rootDir, fileNode.file, pathToTitle);
            Set<String> fileReferenced = MarkdownToConfluenceStorage.extractAttachmentReferences(processed);
            String storage = MarkdownToConfluenceStorage.toStorage(processed);

            Content page = findOrCreateChildPage(fileNode.title, node.contentId, space,
                    MarkdownToConfluenceStorage.toStorage("<p>Placeholder</p>"));

            baseDir = resolveAttachmentsDir(fileNode.file, attachmentsDir);
            List<String> fileUploadedAttachmentNames = attachmentHelper.uploadReferencedAttachments(
                    page.getId(), processed, baseDir, false);
            List<String> extraAttachmentNames = new ArrayList<>();
            for (String name : fileUploadedAttachmentNames) {
                if (!fileReferenced.contains(name)) {
                    extraAttachmentNames.add(name);
                }
            }
            storage = appendAttachmentLinks(storage, extraAttachmentNames);
            pageOps.updatePage(page.getId(), fileNode.title, node.contentId, storage, space, "");
            fileNode.contentId = page.getId();
        }

        for (DirNode subdir : node.subdirs) {
            Content folderPage = findOrCreateChildPage(subdir.title, node.contentId, space,
                    MarkdownToConfluenceStorage.toStorage("<p>" + MarkdownToConfluenceStorage.escapeXml(subdir.title) + "</p>"));
            subdir.contentId = folderPage.getId();
            subdir.parentId = node.contentId;
            syncDirNode(subdir, rootDir, pathToTitle, space, attachmentsDir);
        }
    }

    private List<String> uploadDirectoryAttachments(DirNode node, Set<String> referencedAttachmentNames) {
        List<String> uploadedAttachmentNames = new ArrayList<>();
        List<Attachment> existingAttachments = attachmentHelper.getAttachments(node.contentId);
        for (File attachment : node.attachments) {
            Attachment existing = ConfluenceAttachmentHelper.findAttachmentByName(existingAttachments, attachment.getName());
            if (existing != null) {
                logger.info("Attachment '{}' already exists on page {}, skipping", attachment.getName(), node.contentId);
                if (!referencedAttachmentNames.contains(attachment.getName())) {
                    uploadedAttachmentNames.add(attachment.getName());
                }
                continue;
            }
            try {
                attachmentHelper.uploadAttachment(node.contentId, attachment, false);
                if (!referencedAttachmentNames.contains(attachment.getName())) {
                    uploadedAttachmentNames.add(attachment.getName());
                }
            } catch (IOException e) {
                logger.warn("Failed to upload attachment '{}': {}", attachment.getName(), e.getMessage());
            }
        }
        return uploadedAttachmentNames;
    }

    private Content findOrCreateChildPage(String title, String parentId, String space, String placeholderBody) throws IOException {
        List<Content> children = pageOps.getChildren(parentId);
        for (Content child : children) {
            if (title.equals(child.getTitle())) {
                return child;
            }
        }
        return pageOps.createPage(title, parentId, placeholderBody, space);
    }

    private List<String> deleteOrphanPages(String parentId, Set<String> expectedTitles) throws IOException {
        List<String> deleted = new ArrayList<>();
        for (Content child : pageOps.getChildren(parentId)) {
            if (!expectedTitles.contains(child.getTitle())) {
                deleteRecursively(child, deleted);
            }
        }
        return deleted;
    }

    private void deleteRecursively(Content content, List<String> deleted) throws IOException {
        for (Content child : pageOps.getChildren(content.getId())) {
            deleteRecursively(child, deleted);
        }
        pageOps.deletePage(content.getId());
        deleted.add(content.getTitle());
        logger.info("Deleted orphan page: {} ({})", content.getTitle(), content.getId());
    }

    private static DirNode buildDirTree(File dir) {
        DirNode node = new DirNode(dir, dir.getName());
        File[] children = dir.listFiles();
        if (children == null) {
            return node;
        }
        Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                node.subdirs.add(buildDirTree(child));
            } else if (isMarkdownFile(child)) {
                if (isIndexFile(child)) {
                    node.indexFile = child;
                } else {
                    node.files.add(new FileNode(child));
                }
            } else {
                node.attachments.add(child);
            }
        }
        return node;
    }

    private static Map<String, String> buildPathToTitleMap(File rootDir, DirNode node) throws IOException {
        Map<String, String> map = new HashMap<>();
        buildPathToTitleMapRecursive(rootDir, node, map);
        return map;
    }

    private static void buildPathToTitleMapRecursive(File rootDir, DirNode node,
                                                     Map<String, String> map) throws IOException {
        String dirRel = relativePath(rootDir, node.dir);
        if (!dirRel.isEmpty()) {
            map.put(dirRel, node.title);
        }
        if (node.indexFile != null) {
            map.put(relativePath(rootDir, node.indexFile), node.title);
        }
        for (FileNode fileNode : node.files) {
            fileNode.title = extractTitle(readFile(fileNode.file), fileNode.file);
            map.put(relativePath(rootDir, fileNode.file), fileNode.title);
        }
        for (DirNode subdir : node.subdirs) {
            buildPathToTitleMapRecursive(rootDir, subdir, map);
        }
    }

    private static String readFile(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static String processMarkdownLinks(String markdown, File rootDir, File currentFile,
                                               Map<String, String> pathToTitle) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        Matcher matcher = LINK_PATTERN.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String text = matcher.group(1);
            String url = matcher.group(2).trim();
            if (url.isBlank() || MarkdownToConfluenceStorage.isExternalUrlStatic(url) || url.startsWith("#")) {
                continue;
            }
            if (!url.toLowerCase().endsWith(".md")) {
                continue;
            }
            File target = resolveRelativePath(currentFile, url);
            if (target == null) {
                continue;
            }
            String rel = relativePath(rootDir, target);
            String title = pathToTitle.get(rel);
            if (title == null) {
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    "[" + text + "](" + title + ")"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static File resolveRelativePath(File baseFile, String relativePath) {
        File parent = baseFile.getParentFile();
        if (parent == null) {
            return null;
        }
        try {
            return new File(parent, relativePath).getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }

    private static String relativePath(File rootDir, File file) {
        try {
            String root = rootDir.getCanonicalPath();
            String abs = file.getCanonicalPath();
            if (abs.equals(root)) {
                return "";
            }
            if (abs.startsWith(root + File.separator)) {
                return abs.substring(root.length() + File.separator.length()).replace(File.separator, "/");
            }
            return abs.replace(File.separator, "/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String extractTitle(String markdown, File file) {
        if (markdown != null) {
            Matcher matcher = H1_PATTERN.matcher(markdown);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        String name = file.getName();
        if (name.toLowerCase().endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private static File resolveAttachmentsDir(File markdownFile, String attachmentsDir) {
        if (attachmentsDir != null && !attachmentsDir.isBlank()) {
            return new File(attachmentsDir);
        }
        File parent = markdownFile.getParentFile();
        return parent != null ? parent : new File(".");
    }

    private static String appendAttachmentLinks(String storageBody, List<String> attachmentNames) {
        if (attachmentNames == null || attachmentNames.isEmpty()) {
            return storageBody;
        }
        Set<String> alreadyLinked = extractReferencedAttachmentNames(storageBody);
        List<String> forceLinkNames = new ArrayList<>();
        for (String name : attachmentNames) {
            if (!alreadyLinked.contains(name)) {
                forceLinkNames.add(name);
            }
        }
        if (forceLinkNames.isEmpty()) {
            return storageBody;
        }
        return storageBody + buildAttachmentLinks(forceLinkNames);
    }

    private static String buildAttachmentLinks(List<String> attachmentNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>Attachments</h2><ul>");
        for (String name : attachmentNames) {
            sb.append("<li><ac:link><ri:attachment ri:filename=\"")
                    .append(MarkdownToConfluenceStorage.escapeXml(name))
                    .append("\" />");
            sb.append("<ac:link-body>")
                    .append(MarkdownToConfluenceStorage.escapeXml(name))
                    .append("</ac:link-body></ac:link></li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private static Set<String> extractReferencedAttachmentNames(String storageBody) {
        Set<String> result = new LinkedHashSet<>();
        if (storageBody == null || storageBody.isBlank()) {
            return result;
        }
        Matcher imageMatcher = IMAGE_ATTACHMENT_PATTERN.matcher(storageBody);
        while (imageMatcher.find()) {
            result.add(imageMatcher.group(1));
        }
        Matcher linkMatcher = LINK_ATTACHMENT_PATTERN.matcher(storageBody);
        while (linkMatcher.find()) {
            result.add(linkMatcher.group(1));
        }
        return result;
    }

    private static boolean isMarkdownFile(File file) {
        return file.getName().toLowerCase().endsWith(".md");
    }

    private static boolean isIndexFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.equals("index.md") || lower.equals("readme.md");
    }

    private static final class DirNode {
        final File dir;
        final String title;
        File indexFile;
        final List<FileNode> files = new ArrayList<>();
        final List<DirNode> subdirs = new ArrayList<>();
        final List<File> attachments = new ArrayList<>();
        String contentId;
        String parentId;

        DirNode(File dir, String title) {
            this.dir = dir;
            this.title = title;
        }
    }

    private static final class FileNode {
        final File file;
        String title;
        String contentId;

        FileNode(File file) {
            this.file = file;
        }
    }
}
