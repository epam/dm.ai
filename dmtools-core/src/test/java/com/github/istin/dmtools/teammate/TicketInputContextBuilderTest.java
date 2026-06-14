// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.cliagent.InputParams;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.figma.FigmaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketInputContextBuilderTest {

    @TempDir
    Path tempDir;

    private TrackerClient<ITicket> trackerClient = mock(TrackerClient.class);
    private Confluence confluence = mock(Confluence.class);
    private FigmaClient figmaClient = mock(FigmaClient.class);

    private TicketInputContextBuilder builder = new TicketInputContextBuilder(null);

    @Test
    void testBuildFromTicketKeyCreatesRequestFile() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "Description");
        when(trackerClient.performTicket(eq("PROJ-123"), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Summary\nDescription");

        InputParams input = new InputParams("PROJ-123");
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        assertEquals("PROJ-123", result.getTicket().getTicketKey());
        Path expected = tempDir.resolve("input/PROJ-123");
        assertEquals(expected.toAbsolutePath(), result.getPath().toAbsolutePath());
        assertTrue(Files.exists(expected.resolve("request.md")));
        String request = Files.readString(expected.resolve("request.md"));
        assertTrue(request.contains("Summary"), "request.md should contain ticket summary");
    }

    @Test
    void testBuildFromJqlUsesFirstTicket() throws Exception {
        ITicket first = mockTicket("PROJ-111", "First", "Desc");
        ITicket second = mockTicket("PROJ-222", "Second", "Desc");
        when(trackerClient.searchAndPerform(eq("project = PROJ"), any()))
                .thenReturn(List.of(first, second));
        when(trackerClient.getTextFieldsOnly(any())).thenReturn("text");

        InputParams input = new InputParams();
        input.setJql("project = PROJ");
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        assertEquals("PROJ-111", result.getTicket().getTicketKey());
    }

    @Test
    void testSmartConfluenceWritesPages() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "See https://wiki/display/SPACE/Page");
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("See https://wiki/display/SPACE/Page");

        Content page = mock(Content.class);
        when(page.getTitle()).thenReturn("Page");
        when(page.getId()).thenReturn("12345");
        Storage storage = mock(Storage.class);
        when(storage.getValue()).thenReturn("<p>Page body</p>");
        when(page.getStorage()).thenReturn(storage);

        when(confluence.parseUris(anyString())).thenReturn(Set.of("https://wiki/display/SPACE/Page"));
        when(confluence.contentByUrl("https://wiki/display/SPACE/Page")).thenReturn(page);
        when(confluence.downloadPageAttachments(anyString(), any(File.class))).thenReturn(List.of());

        InputParams input = new InputParams("PROJ-123");
        input.setSources(new String[] { "confluence" });

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        Path confluenceFolder = result.getPath().resolve("confluence");
        assertTrue(Files.exists(confluenceFolder), "Confluence folder should be created");
        assertTrue(Files.list(confluenceFolder).anyMatch(p -> p.toString().endsWith(".md")),
                "Confluence page markdown should be written");
    }

    @Test
    void testSmartFigmaWritesImages() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "See https://figma.com/file/abc");
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("See https://figma.com/file/abc");

        File imageFile = tempDir.resolve("source.png").toFile();
        Files.writeString(imageFile.toPath(), "image-content");
        when(figmaClient.parseUris(anyString())).thenReturn(Set.of("https://figma.com/file/abc"));
        when(figmaClient.uriToObject("https://figma.com/file/abc")).thenReturn(imageFile);

        InputParams input = new InputParams("PROJ-123");
        input.setSources(new String[] { "figma" });

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        Path figmaFolder = result.getPath().resolve("figma");
        assertTrue(Files.exists(figmaFolder), "Figma folder should be created");
        assertTrue(Files.list(figmaFolder).anyMatch(p -> p.toString().endsWith(".png")),
                "Figma image should be written");
    }

    @Test
    void testSmartDisabledSkipsSources() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "See https://wiki/display/SPACE/Page");
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("See https://wiki/display/SPACE/Page");

        InputParams input = new InputParams("PROJ-123");
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        verify(confluence, never()).parseUris(anyString());
        verify(figmaClient, never()).parseUris(anyString());
        assertFalse(Files.exists(result.getPath().resolve("confluence")));
        assertFalse(Files.exists(result.getPath().resolve("figma")));
    }

    @Test
    void testSourcesWhitelistFiltersFigma() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "Confluence and figma links");
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Confluence and figma links");
        when(confluence.parseUris(anyString())).thenReturn(Set.of("https://wiki/page"));

        InputParams input = new InputParams("PROJ-123");
        input.setSources(new String[] { "confluence" });

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        verify(confluence).parseUris(anyString());
        verify(figmaClient, never()).parseUris(anyString());
        assertFalse(Files.exists(result.getPath().resolve("figma")));
    }

    @Test
    void testDepthZeroSkipsLinkedTickets() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "Relates to PROJ-999");
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Relates to PROJ-999");
        ITicket linkedTicket = mockTicket("PROJ-999", "Linked", "Linked desc");
        when(trackerClient.performTicket(eq("PROJ-999"), any()))
                .thenReturn(linkedTicket);

        InputParams input = new InputParams("PROJ-123");
        input.setDepth(0);
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        verify(trackerClient, never()).performTicket(eq("PROJ-999"), any());
        // The original ticket text still mentions PROJ-999, but the linked ticket itself was not fetched.
        assertEquals("PROJ-123", result.getTicket().getTicketKey());
    }

    @Test
    void testDepthGreaterThanZeroIncludesLinkedTickets() throws Exception {
        ITicket ticket = mockTicket("PROJ-123", "Summary", "Relates to PROJ-999");
        when(trackerClient.performTicket(eq("PROJ-123"), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Relates to PROJ-999");
        ITicket linkedTicket = mockTicket("PROJ-999", "Linked", "Linked desc");
        when(trackerClient.performTicket(eq("PROJ-999"), any()))
                .thenReturn(linkedTicket);

        InputParams input = new InputParams("PROJ-123");
        input.setDepth(1);
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        verify(trackerClient).performTicket(eq("PROJ-999"), any());
        String request = Files.readString(result.getPath().resolve("request.md"));
        assertTrue(request.contains("Linked"), "Linked ticket title should appear when depth>0");
    }

    @Test
    void testSkipAllAttachmentsDoesNotDownload() throws Exception {
        IAttachment attachment = mock(IAttachment.class);
        when(attachment.getName()).thenReturn("doc.pdf");
        when(attachment.getUrl()).thenReturn("https://example.com/doc.pdf");
        ITicket ticket = mockTicket("PROJ-123", "Summary", "Desc");
        List<? extends IAttachment> attachments = Collections.singletonList(attachment);
        doReturn(attachments).when(ticket).getAttachments();
        when(trackerClient.performTicket(anyString(), any())).thenReturn(ticket);
        when(trackerClient.getTextFieldsOnly(ticket)).thenReturn("Summary\nDesc");

        InputParams input = new InputParams("PROJ-123");
        input.setSkipAllAttachments(true);
        input.setSmart(false);

        TicketInputContextBuilder.Result result = builder.build(input, tempDir, trackerClient, confluence, figmaClient);

        verify(trackerClient, never()).convertUrlToFile(anyString());
        assertFalse(Files.exists(result.getPath().resolve("doc.pdf")));
    }

    private ITicket mockTicket(String key, String title, String description) throws Exception {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn(key);
        when(ticket.getTicketTitle()).thenReturn(title);
        when(ticket.getTicketDescription()).thenReturn(description);
        doReturn(Collections.emptyList()).when(ticket).getAttachments();
        when(ticket.toText()).thenReturn(title + "\n" + description);
        return ticket;
    }
}
