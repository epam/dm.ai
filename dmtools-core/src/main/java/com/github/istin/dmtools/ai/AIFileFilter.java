// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Filters files before they are sent to AI clients.
 *
 * <p>Two filters are applied (both configurable via env vars / dmtools.env):
 * <ul>
 *   <li><b>Size limit</b> — {@code AI_ATTACHMENT_MAX_SIZE_MB}: files exceeding this size are
 *       dropped. Set to {@code 0} (default) to disable.</li>
 *   <li><b>Extension whitelist</b> — {@code AI_ATTACHMENT_ALLOWED_EXTENSIONS}: comma-separated
 *       list of permitted extensions without leading dot (e.g. {@code jpg,png,pdf}). When empty
 *       (default) all extensions are permitted.</li>
 * </ul>
 */
public class AIFileFilter {

    private static final Logger logger = LogManager.getLogger(AIFileFilter.class);

    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;

    public AIFileFilter() {
        PropertyReader reader = new PropertyReader();
        this.maxSizeBytes = reader.getAIAttachmentMaxSizeBytes();
        this.allowedExtensions = reader.getAIAttachmentAllowedExtensions();
    }

    AIFileFilter(long maxSizeBytes, Set<String> allowedExtensions) {
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = allowedExtensions;
    }

    /**
     * Returns {@code true} if the file may be sent to an AI client.
     */
    public boolean shouldInclude(File file) {
        if (file == null) {
            logger.warn("⏭️ AI attachment skipped: null file reference");
            return false;
        }
        if (!file.exists()) {
            logger.warn("⏭️ AI attachment skipped (does not exist): {}", file.getName());
            return false;
        }

        if (maxSizeBytes > 0 && file.length() > maxSizeBytes) {
            logger.info("⏭️ AI attachment skipped (size {} MB > limit {} MB): {}",
                    String.format("%.1f", file.length() / (1024.0 * 1024.0)),
                    String.format("%.1f", maxSizeBytes / (1024.0 * 1024.0)),
                    file.getName());
            return false;
        }

        if (!allowedExtensions.isEmpty()) {
            String ext = getExtension(file.getName());
            if (!allowedExtensions.contains(ext)) {
                logger.info("⏭️ AI attachment skipped (extension '{}' not in allowed list {}): {}",
                        ext, allowedExtensions, file.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a new list containing only the files that pass the filter.
     */
    public List<File> filter(List<File> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>(files.size());
        for (File f : files) {
            if (shouldInclude(f)) {
                result.add(f);
            }
        }
        return result;
    }

    private static String getExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }
}
