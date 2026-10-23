// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.index.mermaid.MermaidSnapshotResolver;

import java.io.IOException;
import java.util.Optional;

/**
 * Wraps an {@link ITicket} so that its {@link #toText()} returns a pre-built Mermaid
 * snapshot when one is available. All other ticket properties delegate unchanged to
 * the wrapped ticket.
 * <p>
 * This is used by {@link TestCasesGenerator} to feed compact diagram summaries into
 * the relevant-test-case search instead of full ticket text, saving tokens.
 */
public class MermaidSnapshotTicketWrapper extends ITicket.Wrapper {

    private final MermaidSnapshotResolver resolver;

    public MermaidSnapshotTicketWrapper(ITicket ticket, MermaidSnapshotResolver resolver) {
        super(ticket);
        this.resolver = resolver;
    }

    @Override
    public String toText() throws IOException {
        String ticketKey = getTicketKey();
        if (resolver != null && ticketKey != null && !ticketKey.isEmpty()) {
            Optional<String> snapshot = resolver.resolveSnapshot(ticketKey);
            if (snapshot.isPresent()) {
                return "Key: " + ticketKey + "\nMermaid snapshot:\n" + snapshot.get();
            }
        }
        return super.toText();
    }
}
