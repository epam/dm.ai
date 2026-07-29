// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Helper for uploading and managing Confluence page attachments.
 *
 * <p>This class encapsulates attachment-related logic (single upload, bulk upload,
 * idempotency, referenced-attachment upload) so that {@link Confluence} remains a
 * thin MCP facade.</p>
 */
public class ConfluenceAttachmentHelper {

    private static final Logger logger = LogManager.getLogger(ConfluenceAttachmentHelper.class);

    private final Function<String, List<Attachment>> attachmentLister;
    private final AttachmentUploader uploader;

    /**
     * Functional interface for the low-level HTTP upload. Production code passes a
     * lambda that signs and executes the request; tests can pass a stub.
     */
    @FunctionalInterface
    public interface AttachmentUploader {
        String upload(String contentId, File file, String contentType) throws IOException;
    }

    public ConfluenceAttachmentHelper(Function<String, List<Attachment>> attachmentLister,
                                       AttachmentUploader uploader) {
        this.attachmentLister = attachmentLister;
        this.uploader = uploader;
    }

    /**
     * Builds a default HTTP uploader backed by the provided OkHttp client.
     */
    @FunctionalInterface
    public interface RequestSigner {
        Request.Builder sign(Request.Builder builder);
    }

    public static AttachmentUploader defaultUploader(String basePath,
                                                     RequestSigner signer,
                                                     OkHttpClient client) {
        return (contentId, file, contentType) -> {
            String normalizedBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
            String url = normalizedBase + "/rest/api/content/" + contentId + "/child/attachment";
            RequestBody fileBody = RequestBody.create(file, MediaType.parse(contentType));
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(), fileBody)
                    .build();
            Request request = signer.sign(new Request.Builder().url(url))
                    .post(requestBody)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new IOException("Attachment upload failed for " + file.getName() + ": " + response.code() + " " + body);
                }
                return body;
            }
        };
    }

    public List<Attachment> getAttachments(String contentId) {
        return attachmentLister.apply(contentId);
    }

    /**
     * Uploads a single file as an attachment on a Confluence page. If an attachment
     * with the same filename already exists and {@code updateIfExists} is false,
     * the existing attachment is returned without re-uploading.
     */
    public Attachment uploadAttachment(String contentId, File file, boolean updateIfExists) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("Attachment file does not exist or is not a regular file: " + (file != null ? file.getAbsolutePath() : "null"));
        }

        List<Attachment> existingAttachments = getAttachments(contentId);
        Attachment existing = findAttachmentByName(existingAttachments, file.getName());
        if (existing != null && !updateIfExists) {
            logger.info("Attachment '{}' already exists on page {}, skipping upload", file.getName(), contentId);
            return existing;
        }

        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        String responseBody = uploader.upload(contentId, file, contentType);
        logger.info("Uploaded attachment '{}' to page {}", file.getName(), contentId);
        return new Attachment(responseBody);
    }

    /**
     * Uploads all files from a local directory as attachments to a Confluence page.
     * Existing attachments are skipped by default. Returns a JSON summary.
     */
    public String uploadAttachments(String contentId, File dir, Boolean updateIfExists) throws IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir.getAbsolutePath());
        }
        File[] files = dir.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return "No files found in " + dir.getAbsolutePath();
        }

        boolean update = updateIfExists != null && updateIfExists;
        List<String> uploaded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (File file : files) {
            try {
                Attachment result = uploadAttachment(contentId, file, update);
                if (result != null && file.getName().equalsIgnoreCase(result.getTitle())) {
                    uploaded.add(file.getName());
                } else {
                    skipped.add(file.getName());
                }
            } catch (IOException e) {
                logger.warn("Failed to upload attachment {}: {}", file.getName(), e.getMessage());
                failed.add(file.getName() + " (" + e.getMessage() + ")");
            }
        }

        JSONObject summary = new JSONObject();
        summary.put("uploaded", new JSONArray(uploaded));
        summary.put("skipped", new JSONArray(skipped));
        summary.put("failed", new JSONArray(failed));
        return summary.toString();
    }

    /**
     * Uploads attachments referenced by a Markdown page. Returns the filenames of
     * attachments that are present on the page after this call (uploaded or already
     * existing), so callers can link them explicitly when they are not referenced in
     * the body itself.
     */
    public List<String> uploadReferencedAttachments(String contentId, String markdown, File baseDir,
                                                     boolean updateIfExists) {
        Set<String> references = MarkdownToConfluenceStorage.extractAttachmentReferencePaths(markdown);
        List<String> presentAttachmentNames = new ArrayList<>();
        if (references.isEmpty()) {
            return presentAttachmentNames;
        }
        List<Attachment> existingAttachments = getAttachments(contentId);
        for (String rawRef : references) {
            String filename = MarkdownToConfluenceStorage.attachmentFileName(rawRef);
            if (filename.isBlank()) {
                continue;
            }
            Attachment existing = findAttachmentByName(existingAttachments, filename);
            if (existing != null && !updateIfExists) {
                logger.info("Attachment '{}' already exists on page {}, skipping", filename, contentId);
                presentAttachmentNames.add(filename);
                continue;
            }
            File file = resolveAttachmentFile(baseDir, rawRef, filename);
            if (!file.exists()) {
                logger.warn("Referenced attachment '{}' not found in {}, skipping upload", filename, baseDir.getAbsolutePath());
                continue;
            }
            try {
                uploadAttachment(contentId, file, updateIfExists);
                presentAttachmentNames.add(filename);
            } catch (IOException e) {
                logger.warn("Failed to upload referenced attachment '{}': {}", filename, e.getMessage());
            }
        }
        return presentAttachmentNames;
    }

    public static Attachment findAttachmentByName(List<Attachment> attachments, String fileName) {
        if (attachments == null || fileName == null) {
            return null;
        }
        for (Attachment attachment : attachments) {
            if (fileName.equalsIgnoreCase(attachment.getTitle())) {
                return attachment;
            }
        }
        return null;
    }

    private static File resolveAttachmentFile(File baseDir, String rawRef, String filename) {
        try {
            File relative = new File(baseDir, rawRef).getCanonicalFile();
            if (relative.exists()) {
                return relative;
            }
        } catch (IOException e) {
            // Fall back to flat lookup below.
        }
        return new File(baseDir, filename);
    }
}
