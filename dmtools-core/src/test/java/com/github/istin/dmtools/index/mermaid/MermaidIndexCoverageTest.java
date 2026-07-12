// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.index.mermaid;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.atlassian.jira.JiraClient;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.testrail.model.TestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional coverage tests for {@link MermaidIndex} focusing on the TrackerClient
 * constructor (jira / jira_xray / testrail), attachment processing branches,
 * child page paths, chunk preparation and error handling.
 */
class MermaidIndexCoverageTest {

    @Mock
    private Confluence mockConfluence;

    @Mock
    private MermaidDiagramGeneratorAgent mockDiagramGenerator;

    @Mock
    private TrackerClient<ITicket> mockTrackerClient;

    @Mock
    private TrackerClient<TestCase> mockTestRailClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ------------------------------------------------------------------
    // TrackerClient constructor validation
    // ------------------------------------------------------------------

    @Test
    void testTrackerConstructorRejectsNullStoragePath() {
        assertThrows(IllegalArgumentException.class, () ->
                new MermaidIndex("jira", null, List.of("project=PROJ"), new ArrayList<>(),
                        mockTrackerClient, null, false, mockDiagramGenerator));
    }

    @Test
    void testTrackerConstructorRejectsBlankStoragePath() {
        assertThrows(IllegalArgumentException.class, () ->
                new MermaidIndex("jira", "   ", List.of("project=PROJ"), new ArrayList<>(),
                        mockTrackerClient, null, false, mockDiagramGenerator));
    }

    @Test
    void testTrackerConstructorRejectsNullDiagramGenerator() {
        assertThrows(IllegalArgumentException.class, () ->
                new MermaidIndex("jira", tempDir.toString(), List.of("project=PROJ"), new ArrayList<>(),
                        mockTrackerClient, null, false, null));
    }

    @Test
    void testTrackerConstructorRejectsNullTrackerClient() {
        assertThrows(IllegalArgumentException.class, () ->
                new MermaidIndex("jira", tempDir.toString(), List.of("project=PROJ"), new ArrayList<>(),
                        null, null, false, mockDiagramGenerator));
    }

    @Test
    void testTrackerConstructorRejectsUnsupportedIntegration() {
        assertThrows(IllegalArgumentException.class, () ->
                new MermaidIndex("bitbucket", tempDir.toString(), List.of("project=PROJ"), new ArrayList<>(),
                        mockTrackerClient, null, false, mockDiagramGenerator));
    }

    // ------------------------------------------------------------------
    // Jira integration
    // ------------------------------------------------------------------

    @Test
    void testJiraIndexGeneratesDiagramForTicket() throws Exception {
        // Given
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketKey()).thenReturn("PROJ-1");
        when(ticket.getTicketTitle()).thenReturn("Ticket Title");
        when(ticket.getTicketDescription()).thenReturn("Description text");
        when(ticket.getFieldValueAsString("Acceptance Criteria")).thenReturn("AC text");
        when(ticket.getFields()).thenReturn(null);
        when(ticket.getAttachments()).thenReturn(Collections.emptyList());
        when(ticket.getUpdatedAsMillis()).thenReturn(System.currentTimeMillis());
        when(ticket.getIssueType()).thenReturn("Story");

        IComment comment = mock(IComment.class);
        when(comment.getAuthor()).thenReturn(null);
        when(comment.getCreated()).thenReturn(new Date());
        when(comment.getBody()).thenReturn("A useful comment");
        List<? extends IComment> comments = List.of(comment);
        doReturn(comments).when(mockTrackerClient).getComments(eq("PROJ-1"), any());

        when(mockTrackerClient.getDefaultQueryFields()).thenReturn(new String[]{"summary"});
        doAnswer(invocation -> {
            JiraClient.Performer<ITicket> performer = invocation.getArgument(0);
            performer.perform(ticket);
            return null;
        }).when(mockTrackerClient).searchAndPerform(any(), anyString(), any(String[].class));

        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nA --> B");

        MermaidIndex index = new MermaidIndex("jira", tempDir.toString(),
                List.of("project = PROJ"), new ArrayList<>(),
                mockTrackerClient, new String[]{"Acceptance Criteria"}, true, mockDiagramGenerator);

        // When
        index.index();

        // Then
        verify(mockDiagramGenerator, times(1)).run(any());
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator).run(paramsCaptor.capture());
        String sentContent = paramsCaptor.getValue().getContent();
        assertTrue(sentContent.contains("Description text"), "Content should contain the description");
        assertTrue(sentContent.contains("AC text"), "Content should contain the custom field value");
        assertTrue(sentContent.contains("A useful comment"), "Content should contain comments");

        List<Path> diagrams = findMmdFiles();
        assertEquals(1, diagrams.size(), "One diagram file should be created");
        Path diagram = diagrams.get(0);
        assertTrue(diagram.toString().contains("jira"), "Diagram should be stored under jira dir");
        assertTrue(diagram.toString().contains("PROJ"), "Diagram path should contain project key");
        assertTrue(diagram.toString().contains("PROJ-1"), "Diagram path should contain ticket key");
        assertEquals("flowchart TD\nA --> B", Files.readString(diagram, StandardCharsets.UTF_8));
    }

    @Test
    void testJiraXrayIndexWithNoResults() throws Exception {
        // Given - searchAndPerform does not invoke the performer (no tickets found)
        when(mockTrackerClient.getDefaultQueryFields()).thenReturn(new String[]{"summary"});

        MermaidIndex index = new MermaidIndex("jira_xray", tempDir.toString(),
                List.of("project = XR"), new ArrayList<>(),
                mockTrackerClient, null, false, mockDiagramGenerator);

        // When
        index.index();

        // Then
        verify(mockTrackerClient).searchAndPerform(any(), anyString(), any(String[].class));
        verify(mockDiagramGenerator, never()).run(any());
        assertTrue(findMmdFiles().isEmpty(), "No diagrams should be created");
    }

    // ------------------------------------------------------------------
    // TestRail integration
    // ------------------------------------------------------------------

    @Test
    void testTestRailIndexGeneratesDiagramForCase() throws Exception {
        // Given
        TestCase testCase = mock(TestCase.class);
        when(testCase.getTicketKey()).thenReturn("5/123");
        when(testCase.getTicketTitle()).thenReturn("Case Title");
        when(testCase.toText()).thenReturn("test steps content");
        when(testCase.getIssueType()).thenReturn("TestCase");
        when(testCase.getUpdatedAsMillis()).thenReturn(System.currentTimeMillis());

        when(mockTestRailClient.getDefaultQueryFields()).thenReturn(new String[]{"title"});
        doAnswer(invocation -> {
            JiraClient.Performer<TestCase> performer = invocation.getArgument(0);
            performer.perform(testCase);
            return null;
        }).when(mockTestRailClient).searchAndPerform(any(), anyString(), any(String[].class));

        when(mockDiagramGenerator.run(any())).thenReturn("sequenceDiagram\nA->>B: hi");

        MermaidIndex index = new MermaidIndex("testrail", tempDir.toString(),
                List.of("project_id=5&suite_id=3"), new ArrayList<>(),
                mockTestRailClient, null, false, mockDiagramGenerator);

        // When
        index.index();

        // Then
        verify(mockDiagramGenerator, times(1)).run(any());
        List<Path> diagrams = findMmdFiles();
        assertEquals(1, diagrams.size());
        assertTrue(diagrams.get(0).toString().contains("testrail"));
    }

    @Test
    void testTestRailCaseKeyWithoutSlashIsSkipped() throws Exception {
        // Given - case key without "/" produces an invalid pathOrId (parts.length < 2)
        TestCase testCase = mock(TestCase.class);
        when(testCase.getTicketKey()).thenReturn("C123");
        when(testCase.getTicketTitle()).thenReturn("Case Title");
        when(testCase.toText()).thenReturn("test steps content");
        when(testCase.getIssueType()).thenReturn(null);
        when(testCase.getUpdatedAsMillis()).thenReturn(null);

        when(mockTestRailClient.getDefaultQueryFields()).thenReturn(new String[]{"title"});
        doAnswer(invocation -> {
            JiraClient.Performer<TestCase> performer = invocation.getArgument(0);
            performer.perform(testCase);
            return null;
        }).when(mockTestRailClient).searchAndPerform(any(), anyString(), any(String[].class));

        MermaidIndex index = new MermaidIndex("testrail", tempDir.toString(),
                List.of("project_id=5"), new ArrayList<>(),
                mockTestRailClient, null, false, mockDiagramGenerator);

        // When
        index.index();

        // Then - invalid pathOrId is skipped with a warning, nothing is generated
        verify(mockDiagramGenerator, never()).run(any());
        assertTrue(findMmdFiles().isEmpty());
    }

    // ------------------------------------------------------------------
    // Confluence child page path (pathOrId with more than 2 parts)
    // ------------------------------------------------------------------

    @Test
    void testIndexWithChildPageAncestors() throws Exception {
        // Given
        Content ancestor = mock(Content.class);
        when(ancestor.getId()).thenReturn("100");
        when(ancestor.getTitle()).thenReturn("Parent Page");

        Content child = createMockContent("200", "Child Page", "TEST", "<p>child content</p>");
        when(child.getModels(Content.class, "ancestors")).thenReturn(List.of(ancestor));

        when(mockConfluence.contentById(eq("200"))).thenReturn(child);
        when(mockConfluence.getContentAttachments(eq("200"))).thenReturn(new ArrayList<>());
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nC --> D");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/200/Child+Page"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - diagram stored under spaceKey/ancestorId/ancestorName/pageId
        verify(mockDiagramGenerator, times(1)).run(any());
        List<Path> diagrams = findMmdFiles();
        assertEquals(1, diagrams.size());
        String path = diagrams.get(0).toString();
        assertTrue(path.contains("TEST"), "Path should contain space key");
        assertTrue(path.contains("100"), "Path should contain ancestor id");
        assertTrue(path.contains("200"), "Path should contain page id");
        assertTrue(diagrams.get(0).getFileName().toString().endsWith(".mmd"));
    }

    // ------------------------------------------------------------------
    // Attachment processing
    // ------------------------------------------------------------------

    @Test
    void testIndexWithTextAttachment() throws Exception {
        // Given - text attachment with embedded base64 strings that must be filtered
        StringBuilder attachmentText = new StringBuilder("hello world data:image/png;base64,");
        attachmentText.append("a".repeat(150));
        attachmentText.append(" tail ");
        attachmentText.append("b".repeat(600));
        File textAttachment = createAttachmentFile("notes.txt", attachmentText.toString().getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("321", "AttachPage", "TEST", "<p>page content</p>");
        stubAttachments("321", content, List.of(textAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nT --> U");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/321/AttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - main diagram + one attachment diagram
        verify(mockDiagramGenerator, times(2)).run(any());
        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("321")
                .resolve("attachments").resolve("notes.mmd");
        assertTrue(Files.exists(attachmentDiagram), "Attachment diagram should be created");
        assertEquals("flowchart TD\nT --> U", Files.readString(attachmentDiagram, StandardCharsets.UTF_8));

        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        String attachmentContent = paramsCaptor.getAllValues().get(1).getContent();
        assertTrue(attachmentContent.contains("[Base64 image data removed]"),
                "Inline base64 image data should be filtered out");
        assertTrue(attachmentContent.contains("[Large base64 string removed]"),
                "Large base64 strings should be filtered out");
    }

    @Test
    void testIndexWithImageAttachment() throws Exception {
        // Given
        File imageAttachment = createPngAttachment("pic.png");

        Content content = createMockContent("322", "ImagePage", "TEST", "<p>page content</p>");
        stubAttachments("322", content, List.of(imageAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nI --> M");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/322/ImagePage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then
        verify(mockDiagramGenerator, times(2)).run(any());
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        MermaidDiagramGeneratorAgent.Params attachmentParams = paramsCaptor.getAllValues().get(1);
        assertNotNull(attachmentParams.getFiles(), "Image attachment should pass image files to the agent");
        assertFalse(attachmentParams.getFiles().isEmpty());
        assertTrue(attachmentParams.getContent().contains("pic.png"),
                "Image attachment content should reference the filename");

        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("322")
                .resolve("attachments").resolve("pic.mmd");
        assertTrue(Files.exists(attachmentDiagram));
    }

    @Test
    void testIndexWithBinaryAttachment() throws Exception {
        // Given - known binary extension, processed with a placeholder description
        File binaryAttachment = createAttachmentFile("archive.zip", new byte[]{0x50, 0x4B, 0x03, 0x04});

        Content content = createMockContent("323", "BinaryPage", "TEST", "<p>page content</p>");
        stubAttachments("323", content, List.of(binaryAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nB --> N");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/323/BinaryPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then
        verify(mockDiagramGenerator, times(2)).run(any());
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        String attachmentContent = paramsCaptor.getAllValues().get(1).getContent();
        assertTrue(attachmentContent.contains("binary format - not processed"),
                "Binary attachments should use a placeholder description");

        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("323")
                .resolve("attachments").resolve("archive.mmd");
        assertTrue(Files.exists(attachmentDiagram));
    }

    @Test
    void testIndexWithPdfAttachment() throws Exception {
        // Given - an invalid PDF triggers the multi-page processing error handling
        File pdfAttachment = createAttachmentFile("doc.pdf", "%PDF-1.4\ngarbage".getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("324", "PdfPage", "TEST", "<p>page content</p>");
        stubAttachments("324", content, List.of(pdfAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nP --> Q");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/324/PdfPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - only the main diagram is generated; the PDF failure is handled gracefully
        verify(mockDiagramGenerator, times(1)).run(any());
        Path mainDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("324").resolve("PdfPage.mmd");
        assertTrue(Files.exists(mainDiagram));
        Path documentFolder = tempDir.resolve("confluence").resolve("TEST").resolve("324")
                .resolve("attachments").resolve("doc");
        assertTrue(Files.isDirectory(documentFolder), "Multi-page document folder should be created");
    }

    @Test
    void testIndexWritesPlaceholderWhenAttachmentDiagramIsEmpty() throws Exception {
        // Given - diagram generator returns an empty result for the attachment
        File textAttachment = createAttachmentFile("empty.txt", "some text".getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("325", "EmptyDiagramPage", "TEST", "<p>page content</p>");
        stubAttachments("325", content, List.of(textAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nE --> F", "");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/325/EmptyDiagramPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - a placeholder file is written for the attachment
        verify(mockDiagramGenerator, times(2)).run(any());
        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("325")
                .resolve("attachments").resolve("empty.mmd");
        assertTrue(Files.exists(attachmentDiagram));
        String placeholder = Files.readString(attachmentDiagram, StandardCharsets.UTF_8);
        assertTrue(placeholder.contains("Diagram generation returned empty result"),
                "Empty diagram result should produce a placeholder file");
    }

    @Test
    void testIndexSkipsUpToDateAttachment() throws Exception {
        // Given - attachment diagram already exists and is newer than the content
        File textAttachment = createAttachmentFile("stale.txt", "some text".getBytes(StandardCharsets.UTF_8));
        Path attachmentsDir = tempDir.resolve("confluence").resolve("TEST").resolve("326").resolve("attachments");
        Files.createDirectories(attachmentsDir);
        Files.writeString(attachmentsDir.resolve("stale.mmd"), "existing diagram");

        Content content = createMockContent("326", "StaleAttachPage", "TEST", "<p>page content</p>");
        Date oldDate = createDateDaysOffset(-2);
        when(content.getLastModifiedDate()).thenReturn(oldDate);
        stubAttachments("326", content, List.of(textAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nS --> T");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/326/StaleAttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - only the main diagram is generated, the up-to-date attachment is skipped
        verify(mockDiagramGenerator, times(1)).run(any());
        assertEquals("existing diagram",
                Files.readString(attachmentsDir.resolve("stale.mmd"), StandardCharsets.UTF_8),
                "Up-to-date attachment diagram should not be overwritten");
    }

    @Test
    void testIndexIgnoresNullAndMissingAttachments() throws Exception {
        // Given - attachments list contains null and a file that does not exist
        File missingAttachment = new File(tempDir.toFile(), "does-not-exist.txt");
        List<File> attachments = new ArrayList<>();
        attachments.add(null);
        attachments.add(missingAttachment);

        Content content = createMockContent("327", "NullAttachPage", "TEST", "<p>page content</p>");
        stubAttachments("327", content, attachments);
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nN --> M");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/327/NullAttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - only the main diagram is generated
        verify(mockDiagramGenerator, times(1)).run(any());
        List<Path> diagrams = findMmdFiles();
        assertEquals(1, diagrams.size());
    }

    @Test
    void testIndexWithLargeTextAttachmentUsesChunks() throws Exception {
        // Given - attachment content exceeds the token limit and must be chunked
        File largeAttachment = createAttachmentFile("large.txt",
                "lorem ipsum ".repeat(50000).getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("328", "LargeAttachPage", "TEST", "<p>page content</p>");
        stubAttachments("328", content, List.of(largeAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nL --> G");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/328/LargeAttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - attachment params should carry prepared chunks
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        MermaidDiagramGeneratorAgent.Params attachmentParams = paramsCaptor.getAllValues().get(1);
        assertNotNull(attachmentParams.getChunks(), "Large attachment content should be chunked");
        assertTrue(attachmentParams.getChunks().size() > 1, "Content should be split into multiple chunks");
    }

    // ------------------------------------------------------------------
    // Error handling and chunking of main content
    // ------------------------------------------------------------------

    @Test
    void testIndexContinuesWhenDiagramGenerationFails() throws Exception {
        // Given - diagram generator fails; the error must be swallowed by index()
        Content content = createMockContent("401", "FailPage", "TEST", "<p>page content</p>");
        when(mockConfluence.contentById(eq("401"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("401"))).thenReturn(new ArrayList<>());
        when(mockDiagramGenerator.run(any())).thenThrow(new RuntimeException("AI service down"));

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/401/FailPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When / Then - index() must not propagate the failure
        assertDoesNotThrow(index::index);
        assertTrue(findMmdFiles().isEmpty(), "No diagram file should be written on failure");
    }

    @Test
    void testIndexWithLargeContentUsesChunks() throws Exception {
        // Given - content larger than the token limit (roughly 1 token per 4 chars)
        String largeContent = "lorem ipsum ".repeat(50000);
        Content content = createMockContent("402", "LargePage", "TEST", largeContent);
        when(mockConfluence.contentById(eq("402"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("402"))).thenReturn(new ArrayList<>());
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nX --> Y");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/402/LargePage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - params should carry prepared chunks
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(1)).run(paramsCaptor.capture());
        MermaidDiagramGeneratorAgent.Params params = paramsCaptor.getValue();
        assertNotNull(params.getChunks(), "Large content should be chunked");
        assertTrue(params.getChunks().size() > 1, "Content should be split into multiple chunks");

        List<Path> diagrams = findMmdFiles();
        assertEquals(1, diagrams.size());
    }

    @Test
    void testIndexWithValidPdfAttachmentGeneratesPageDiagrams() throws Exception {
        // Given - a valid PDF is transformed into page snapshots, each diagrammed separately
        File pdfAttachment = createValidPdf("manual.pdf");

        Content content = createMockContent("329", "ValidPdfPage", "TEST", "<p>page content</p>");
        stubAttachments("329", content, List.of(pdfAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nM --> N", "flowchart TD\nP1 --> P2");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/329/ValidPdfPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - main diagram plus one diagram per extracted PDF page
        verify(mockDiagramGenerator, atLeast(2)).run(any());
        Path documentFolder = tempDir.resolve("confluence").resolve("TEST").resolve("329")
                .resolve("attachments").resolve("manual");
        assertTrue(Files.isDirectory(documentFolder), "Multi-page document folder should be created");
        Path pageDiagram = documentFolder.resolve("page_001.mmd");
        assertTrue(Files.exists(pageDiagram), "A diagram should be generated for the first PDF page");
        assertEquals("flowchart TD\nP1 --> P2", Files.readString(pageDiagram, StandardCharsets.UTF_8));
    }

    @Test
    void testIndexWithDocxAttachmentHandlesTransformFailure() throws Exception {
        // Given - an invalid DOCX makes the document transformer throw; it must be handled
        File docxAttachment = createAttachmentFile("spec.docx", "not a real docx".getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("334", "DocxPage", "TEST", "<p>page content</p>");
        stubAttachments("334", content, List.of(docxAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nD --> C");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/334/DocxPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - only the main diagram is generated; the transform failure is handled gracefully
        verify(mockDiagramGenerator, times(1)).run(any());
        Path mainDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("334").resolve("DocxPage.mmd");
        assertTrue(Files.exists(mainDiagram));
    }

    @Test
    void testIndexWithPdfSkipsUpToDatePageDiagram() throws Exception {
        // Given - page diagram already exists and is newer than the content
        File pdfAttachment = createValidPdf("skip.pdf");
        Path documentFolder = tempDir.resolve("confluence").resolve("TEST").resolve("335")
                .resolve("attachments").resolve("skip");
        Files.createDirectories(documentFolder);
        Files.writeString(documentFolder.resolve("page_001.mmd"), "existing page diagram");

        Content content = createMockContent("335", "PdfSkipPage", "TEST", "<p>page content</p>");
        Date oldDate = createDateDaysOffset(-2);
        when(content.getLastModifiedDate()).thenReturn(oldDate);
        stubAttachments("335", content, List.of(pdfAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nM --> N");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/335/PdfSkipPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - only the main diagram is generated; the up-to-date page diagram is skipped
        verify(mockDiagramGenerator, times(1)).run(any());
        assertEquals("existing page diagram",
                Files.readString(documentFolder.resolve("page_001.mmd"), StandardCharsets.UTF_8),
                "Up-to-date page diagram should not be overwritten");
    }

    @Test
    void testIndexWithPdfRegeneratesOutdatedPageDiagram() throws Exception {
        // Given - page diagram exists but is older than the content
        File pdfAttachment = createValidPdf("regen.pdf");
        Path documentFolder = tempDir.resolve("confluence").resolve("TEST").resolve("336")
                .resolve("attachments").resolve("regen");
        Files.createDirectories(documentFolder);
        Path stalePage = documentFolder.resolve("page_001.mmd");
        Files.writeString(stalePage, "outdated page diagram");
        Files.setLastModifiedTime(stalePage,
                java.nio.file.attribute.FileTime.fromMillis(createDateDaysOffset(-3).getTime()));

        Content content = createMockContent("336", "PdfRegenPage", "TEST", "<p>page content</p>");
        stubAttachments("336", content, List.of(pdfAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nM --> N", "flowchart TD\nP1 --> P2");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/336/PdfRegenPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - the outdated page diagram is regenerated
        verify(mockDiagramGenerator, times(2)).run(any());
        assertEquals("flowchart TD\nP1 --> P2", Files.readString(stalePage, StandardCharsets.UTF_8),
                "Outdated page diagram should be regenerated");
    }

    @Test
    void testIndexWithPdfHandlesEmptyAndFailedPageDiagrams() throws Exception {
        // Given - a two-page PDF: first page diagram is empty, second page generation fails
        File pdfAttachment = createValidPdf("twopages.pdf", 2);

        Content content = createMockContent("337", "PdfFailPage", "TEST", "<p>page content</p>");
        stubAttachments("337", content, List.of(pdfAttachment));
        when(mockDiagramGenerator.run(any()))
                .thenReturn("flowchart TD\nM --> N")
                .thenReturn("")
                .thenThrow(new RuntimeException("AI service down"));

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/337/PdfFailPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - main diagram exists; empty/failed page diagrams are skipped without failing the run
        Path mainDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("337").resolve("PdfFailPage.mmd");
        assertTrue(Files.exists(mainDiagram));
        Path documentFolder = tempDir.resolve("confluence").resolve("TEST").resolve("337")
                .resolve("attachments").resolve("twopages");
        assertTrue(Files.isDirectory(documentFolder));
        assertFalse(Files.exists(documentFolder.resolve("page_001.mmd")),
                "Empty page diagram should not be written");
        assertFalse(Files.exists(documentFolder.resolve("page_002.mmd")),
                "Failed page diagram should not be written");
    }

    @Test
    void testIndexRegeneratesOutdatedAttachment() throws Exception {
        // Given - attachment diagram exists but is older than the content
        File textAttachment = createAttachmentFile("old.txt", "updated text".getBytes(StandardCharsets.UTF_8));
        Path attachmentsDir = tempDir.resolve("confluence").resolve("TEST").resolve("330").resolve("attachments");
        Files.createDirectories(attachmentsDir);
        Path staleDiagram = attachmentsDir.resolve("old.mmd");
        Files.writeString(staleDiagram, "outdated diagram");
        Files.setLastModifiedTime(staleDiagram,
                java.nio.file.attribute.FileTime.fromMillis(createDateDaysOffset(-3).getTime()));

        Content content = createMockContent("330", "OutdatedAttachPage", "TEST", "<p>page content</p>");
        stubAttachments("330", content, List.of(textAttachment));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nR --> G");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/330/OutdatedAttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - both main and outdated attachment diagrams are regenerated
        verify(mockDiagramGenerator, times(2)).run(any());
        assertEquals("flowchart TD\nR --> G",
                Files.readString(staleDiagram, StandardCharsets.UTF_8),
                "Outdated attachment diagram should be regenerated");
    }

    @Test
    void testIndexWithInvalidImageAttachmentFallsBackToOriginal() throws Exception {
        // Given - image processing fails for a corrupt image, original file is used
        File corruptImage = createAttachmentFile("broken.png", "not an image".getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("331", "BrokenImagePage", "TEST", "<p>page content</p>");
        stubAttachments("331", content, List.of(corruptImage));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nK --> L");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/331/BrokenImagePage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - the diagram is still generated using the original attachment file
        verify(mockDiagramGenerator, times(2)).run(any());
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        MermaidDiagramGeneratorAgent.Params attachmentParams = paramsCaptor.getAllValues().get(1);
        assertNotNull(attachmentParams.getFiles());
        assertEquals(1, attachmentParams.getFiles().size());
        assertEquals("broken.png", attachmentParams.getFiles().get(0).getName(),
                "Original image should be used as fallback");

        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("331")
                .resolve("attachments").resolve("broken.mmd");
        assertTrue(Files.exists(attachmentDiagram));
    }

    @Test
    void testIndexWithUnreadableTextAttachment() throws Exception {
        // Given - an attachment that exists but cannot be read as text (a directory)
        File unreadable = tempDir.resolve("attachments-source").resolve("weird.txt").toFile();
        Files.createDirectories(unreadable.toPath());

        Content content = createMockContent("332", "UnreadablePage", "TEST", "<p>page content</p>");
        stubAttachments("332", content, List.of(unreadable));
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nU --> V");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/332/UnreadablePage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - fallback description is used for the unreadable attachment
        verify(mockDiagramGenerator, times(2)).run(any());
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(2)).run(paramsCaptor.capture());
        String attachmentContent = paramsCaptor.getAllValues().get(1).getContent();
        assertTrue(attachmentContent.contains("binary or unsupported format"),
                "Unreadable attachment should use the fallback description");
    }

    @Test
    void testIndexSwallowsAttachmentDiagramGenerationFailure() throws Exception {
        // Given - attachment diagram generation fails; the failure must be swallowed
        File textAttachment = createAttachmentFile("failing.txt", "some text".getBytes(StandardCharsets.UTF_8));

        Content content = createMockContent("333", "FailingAttachPage", "TEST", "<p>page content</p>");
        stubAttachments("333", content, List.of(textAttachment));
        when(mockDiagramGenerator.run(any()))
                .thenReturn("flowchart TD\nW --> X")
                .thenThrow(new RuntimeException("AI service down"));

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/333/FailingAttachPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - main diagram is still written, no attachment diagram is created
        verify(mockDiagramGenerator, times(2)).run(any());
        Path mainDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("333")
                .resolve("FailingAttachPage.mmd");
        assertTrue(Files.exists(mainDiagram));
        Path attachmentDiagram = tempDir.resolve("confluence").resolve("TEST").resolve("333")
                .resolve("attachments").resolve("failing.mmd");
        assertFalse(Files.exists(attachmentDiagram), "Failed attachment should not produce a diagram file");
    }

    @Test
    void testIndexWithLargeSingleWordContentFitsInOneChunk() throws Exception {
        // Given - content large in characters but a single token, so chunking yields one chunk
        String largeContent = "a".repeat(410_000);
        Content content = createMockContent("403", "SingleTokenPage", "TEST", largeContent);
        when(mockConfluence.contentById(eq("403"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("403"))).thenReturn(new ArrayList<>());
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nZ --> Y");

        MermaidIndex index = new MermaidIndex("confluence", tempDir.toString(),
                List.of("TEST/pages/403/SingleTokenPage"), new ArrayList<>(),
                mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - single chunk means no chunked processing
        ArgumentCaptor<MermaidDiagramGeneratorAgent.Params> paramsCaptor =
                ArgumentCaptor.forClass(MermaidDiagramGeneratorAgent.Params.class);
        verify(mockDiagramGenerator, times(1)).run(paramsCaptor.capture());
        assertNull(paramsCaptor.getValue().getChunks(), "Single-chunk content should not use chunked params");
        assertEquals(1, findMmdFiles().size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Content createMockContent(String id, String title, String spaceKey, String body) {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(id);
        when(content.getTitle()).thenReturn(title);
        when(content.getLastModifiedDate()).thenReturn(new Date());
        when(content.getParentId()).thenReturn(null);

        JSONObject jsonObject = new JSONObject();
        JSONObject expandable = new JSONObject();
        expandable.put("space", "/rest/api/space/" + spaceKey);
        jsonObject.put("_expandable", expandable);
        jsonObject.put("id", id);
        jsonObject.put("title", title);

        JSONObject storage = new JSONObject();
        storage.put("value", body);
        JSONObject bodyObj = new JSONObject();
        bodyObj.put("storage", storage);
        jsonObject.put("body", bodyObj);

        JSONObject version = new JSONObject();
        version.put("when", "2023-01-01T00:00:00.000Z");
        jsonObject.put("version", version);

        when(content.getJSONObject()).thenReturn(jsonObject);

        Storage storageObj = mock(Storage.class);
        when(storageObj.getValue()).thenReturn(body);
        when(content.getStorage()).thenReturn(storageObj);

        return content;
    }

    private void stubAttachments(String contentId, Content content, List<File> attachmentFiles) throws Exception {
        when(mockConfluence.contentById(eq(contentId))).thenReturn(content);
        Attachment attachment = mock(Attachment.class);
        when(mockConfluence.getContentAttachments(eq(contentId))).thenReturn(List.of(attachment));
        when(mockConfluence.downloadPageAttachments(eq(contentId), any(File.class))).thenReturn(attachmentFiles);
    }

    private File createAttachmentFile(String name, byte[] data) throws Exception {
        Path file = tempDir.resolve("attachments-source").resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        return file.toFile();
    }

    private File createPngAttachment(String name) throws Exception {
        Path file = tempDir.resolve("attachments-source").resolve(name);
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", file.toFile());
        return file.toFile();
    }

    private File createValidPdf(String name) throws Exception {
        return createValidPdf(name, 1);
    }

    private File createValidPdf(String name, int pages) throws Exception {
        Path file = tempDir.resolve("attachments-source").resolve(name);
        Files.createDirectories(file.getParent());
        try (PDDocument document = new PDDocument()) {
            PDImageXObject pdImage = LosslessFactory.createFromImage(document,
                    new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(100, 700);
                    contentStream.showText("Sample PDF page " + (i + 1));
                    contentStream.endText();
                    contentStream.drawImage(pdImage, 100, 500, 50, 50);
                }
            }
            document.save(file.toFile());
        }
        return file.toFile();
    }

    private List<Path> findMmdFiles() throws Exception {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".mmd"))
                    .collect(Collectors.toList());
        }
    }

    private Date createDateDaysOffset(int days) {
        return new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days));
    }
}
