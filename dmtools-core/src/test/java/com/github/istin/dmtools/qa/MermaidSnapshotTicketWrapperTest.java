// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.index.mermaid.MermaidSnapshotResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MermaidSnapshotTicketWrapperTest {

    @Test
    void returnsSnapshotTextWhenSnapshotAvailable() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("PROJ-123");
        when(ticket.toText()).thenReturn("full ticket text");

        MermaidSnapshotResolver resolver = mock(MermaidSnapshotResolver.class);
        when(resolver.resolveSnapshot("PROJ-123")).thenReturn(Optional.of("flowchart TD\nA --> B"));

        MermaidSnapshotTicketWrapper wrapper = new MermaidSnapshotTicketWrapper(ticket, resolver);
        String text = wrapper.toText();

        assertTrue(text.contains("PROJ-123"));
        assertTrue(text.contains("Mermaid snapshot:"));
        assertTrue(text.contains("flowchart TD"));
        assertFalse(text.contains("full ticket text"));
        verify(ticket, never()).toText();
    }

    @Test
    void fallsBackToTicketTextWhenSnapshotMissing() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("PROJ-456");
        when(ticket.toText()).thenReturn("full ticket text");

        MermaidSnapshotResolver resolver = mock(MermaidSnapshotResolver.class);
        when(resolver.resolveSnapshot("PROJ-456")).thenReturn(Optional.empty());

        MermaidSnapshotTicketWrapper wrapper = new MermaidSnapshotTicketWrapper(ticket, resolver);
        String text = wrapper.toText();

        assertEquals("full ticket text", text);
        verify(ticket).toText();
    }

    @Test
    void delegatesOtherMethodsToWrappedTicket() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("PROJ-789");
        when(ticket.getTicketTitle()).thenReturn("Login test");

        MermaidSnapshotResolver resolver = mock(MermaidSnapshotResolver.class);
        when(resolver.resolveSnapshot("PROJ-789")).thenReturn(Optional.empty());

        MermaidSnapshotTicketWrapper wrapper = new MermaidSnapshotTicketWrapper(ticket, resolver);

        assertEquals("PROJ-789", wrapper.getTicketKey());
        assertEquals("Login test", wrapper.getTicketTitle());
    }
}
