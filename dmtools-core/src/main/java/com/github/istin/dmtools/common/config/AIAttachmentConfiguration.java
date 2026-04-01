// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

import java.util.Set;

/**
 * Configuration interface for AI attachment (file) filtering.
 * Controls which files are passed to AI clients based on size and extension.
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code AI_ATTACHMENT_MAX_SIZE_MB} — Maximum file size in MB. Files larger than this
 *       are skipped before being sent to any AI client. Set to {@code 0} (default) to disable
 *       the size limit.</li>
 *   <li>{@code AI_ATTACHMENT_ALLOWED_EXTENSIONS} — Comma-separated whitelist of allowed file
 *       extensions (without leading dot, case-insensitive). E.g. {@code jpg,png,pdf,gif,webp}.
 *       Leave empty (default) to allow all extensions.</li>
 * </ul>
 */
public interface AIAttachmentConfiguration {

    /**
     * Returns the maximum allowed attachment size in bytes for AI clients.
     * @return max size in bytes; {@code 0} means no limit
     */
    long getAIAttachmentMaxSizeBytes();

    /**
     * Returns the set of allowed file extensions (lowercase, without dot).
     * @return allowed extensions; empty set means all extensions are allowed
     */
    Set<String> getAIAttachmentAllowedExtensions();
}
