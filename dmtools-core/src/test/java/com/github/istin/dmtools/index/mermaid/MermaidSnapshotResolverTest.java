// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.index.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MermaidSnapshotResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesMainSnapshotForJiraTicket() throws IOException {
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-123");
        Files.createDirectories(ticketDir);
        Files.write(ticketDir.resolve("Login test.mmd"), "flowchart TD\nA --> B".getBytes(StandardCharsets.UTF_8));

        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira");
        Optional<String> snapshot = resolver.resolveSnapshot("PROJ-123");

        assertTrue(snapshot.isPresent());
        assertEquals("flowchart TD\nA --> B", snapshot.get());
    }

    @Test
    void ignoresAttachmentDiagrams() throws IOException {
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-456");
        Path attachmentsDir = ticketDir.resolve("attachments");
        Files.createDirectories(attachmentsDir);
        Files.write(ticketDir.resolve("Main test.mmd"), "flowchart TD\nMain".getBytes(StandardCharsets.UTF_8));
        Files.write(attachmentsDir.resolve("image.mmd"), "sequenceDiagram\nA->>B".getBytes(StandardCharsets.UTF_8));

        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira");
        Optional<String> snapshot = resolver.resolveSnapshot("PROJ-456");

        assertTrue(snapshot.isPresent());
        assertEquals("flowchart TD\nMain", snapshot.get());
    }

    @Test
    void returnsEmptyWhenSnapshotMissing() {
        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira");
        Optional<String> snapshot = resolver.resolveSnapshot("PROJ-999");

        assertFalse(snapshot.isPresent());
    }

    @Test
    void supportsJiraXrayIntegration() throws IOException {
        Path ticketDir = tempDir.resolve("jira_xray").resolve("XRAY").resolve("XRAY-1");
        Files.createDirectories(ticketDir);
        Files.write(ticketDir.resolve("Xray test.mmd"), "graph LR\nX-->Y".getBytes(StandardCharsets.UTF_8));

        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira_xray");
        Optional<String> snapshot = resolver.resolveSnapshot("XRAY-1");

        assertTrue(snapshot.isPresent());
        assertEquals("graph LR\nX-->Y", snapshot.get());
    }

    @Test
    void cachesResultForSameTicketKey() throws IOException {
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-789");
        Files.createDirectories(ticketDir);
        Path snapshotFile = ticketDir.resolve("Cached test.mmd");
        Files.write(snapshotFile, "flowchart TD\nA".getBytes(StandardCharsets.UTF_8));

        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira");
        Optional<String> first = resolver.resolveSnapshot("PROJ-789");
        Optional<String> second = resolver.resolveSnapshot("PROJ-789");

        assertTrue(first.isPresent());
        assertSame(first, second);
    }

    @Test
    void picksMostRecentlyModifiedWhenMultipleMainDiagrams() throws IOException, InterruptedException {
        Path ticketDir = tempDir.resolve("jira").resolve("PROJ").resolve("PROJ-111");
        Files.createDirectories(ticketDir);
        Path older = ticketDir.resolve("Older.mmd");
        Path newer = ticketDir.resolve("Newer.mmd");
        Files.write(older, "OLDER".getBytes(StandardCharsets.UTF_8));
        // Ensure modification times differ across filesystems
        Thread.sleep(20);
        Files.write(newer, "NEWER".getBytes(StandardCharsets.UTF_8));

        MermaidSnapshotResolver resolver = new MermaidSnapshotResolver(tempDir.toString(), "jira");
        Optional<String> snapshot = resolver.resolveSnapshot("PROJ-111");

        assertTrue(snapshot.isPresent());
        assertEquals("NEWER", snapshot.get());
    }
}
