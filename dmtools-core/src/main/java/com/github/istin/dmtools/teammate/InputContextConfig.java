// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

/**
 * Minimal configuration contract for building a ticket-based CLI input folder.
 * Implemented by job params that want to reuse {@link TicketInputContextBuilder}.
 */
public interface InputContextConfig {

    /**
     * Whether to automatically resolve URLs found in the ticket text (Confluence, Figma, etc.).
     */
    boolean isSmart();

    /**
     * Whitelist of sources to resolve when {@link #isSmart()} is true.
     * Examples: "confluence", "figma".
     * Null or empty means "all available sources".
     */
    String[] getSources();

    /**
     * Depth of linked-ticket traversal. Values &gt; 0 mean extra tickets referenced
     * in the ticket text should be fetched into the context.
     */
    int getDepth();

    /**
     * Whether to fetch and write the ticket's comments to {@code comments.md}.
     */
    boolean isIncludeComments();

    /**
     * Whether to download ticket attachments into the input folder.
     */
    boolean isIncludeAttachments();

    /**
     * Whether to fetch extra tickets referenced in the ticket text.
     * Usually derived from {@link #getDepth()}.
     */
    boolean isIncludeLinkedTickets();

    /**
     * Whether to skip video attachments when {@link #isIncludeAttachments()} is true.
     */
    boolean isSkipVideoAttachments();

    /**
     * Whether to skip all ticket attachments.
     */
    boolean isSkipAllAttachments();

    /**
     * Whether to ignore tickets linked via "is cloned by" / "clones" relationships
     * when fetching linked tickets.
     */
    boolean isIgnoreClonedByRelationship();

    /**
     * Whether to expand agent parameters into separate files in the input folder.
     * Used by {@link Teammate} when {@code writeAgentParamsToFiles=true}.
     */
    default boolean isWriteAgentParamsToFiles() {
        return false;
    }
}
