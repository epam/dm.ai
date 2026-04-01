// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf.model;

import com.github.istin.dmtools.common.model.IAttachment;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class PdfPageAsTicketTest {

    private PdfPageAsTicket pdfPageAsTicket;

    @Before
    public void setUp() {
        pdfPageAsTicket = new PdfPageAsTicket();
    }

    @Test
    public void testSetAndGetKey() {
        String key = "TEST-123";
        pdfPageAsTicket.setKey(key);
        assertEquals(key, pdfPageAsTicket.getTicketKey());
    }

    @Test
    public void testSetAndGetDescription() {
        String description = "This is a test description.";
        pdfPageAsTicket.setDescription(description);
        assertEquals(description, pdfPageAsTicket.getDescription());
    }

    @Test
    public void testGetIssueType() throws IOException {
        assertEquals("page", pdfPageAsTicket.getIssueType());
    }

    @Test
    public void testGetTicketLink() {
        String key = "TEST-123";
        pdfPageAsTicket.setKey(key);
        assertEquals(key, pdfPageAsTicket.getTicketLink());
    }

    @Test
    public void testSetAndGetLabels() {
        JSONArray labels = new JSONArray();
        labels.put("label1");
        labels.put("label2");
        pdfPageAsTicket.setLabels(labels);
        assertEquals(labels, pdfPageAsTicket.getTicketLabels());
    }

    @Test
    public void testGetTicketDescriptionWithSnapshot() {
        String description = "This is a test description.";
        String snapshotDescription = "Snapshot description.";
        pdfPageAsTicket.setDescription(description);
        pdfPageAsTicket.setSnapshotDescription(snapshotDescription);
        assertEquals(description + "\n" + snapshotDescription, pdfPageAsTicket.getTicketDescription());
    }

    @Test
    public void testGetTicketDescriptionWithoutSnapshot() {
        String description = "This is a test description.";
        pdfPageAsTicket.setDescription(description);
        assertEquals(description, pdfPageAsTicket.getTicketDescription());
    }

    @Test
    public void testAddAndGetAttachments() {
        File file = Mockito.mock(File.class);
        Mockito.when(file.getName()).thenReturn("test.png");
        Mockito.when(file.getAbsolutePath()).thenReturn("/path/to/test.png");

        pdfPageAsTicket.addAttachment(file);
        List<? extends IAttachment> attachments = pdfPageAsTicket.getAttachments();

        assertEquals(1, attachments.size());
        assertEquals("test.png", attachments.get(0).getName());
        assertEquals("/path/to/test.png", attachments.get(0).getUrl());
        assertEquals("image/png", attachments.get(0).getContentType());
    }

    @Test
    public void testSetAndGetPageSnapshot() {
        File file = Mockito.mock(File.class);
        pdfPageAsTicket.setPageSnapshot(file);
        assertEquals(file, pdfPageAsTicket.getPageSnapshot());
    }

    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getStatus());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getStatusModel());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getPriority());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getTicketDependenciesDescription());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getCreated());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getFieldsAsJSON());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getUpdatedAsMillis());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getCreator());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getResolution());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getFields());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getProgress());
        assertThrows(UnsupportedOperationException.class, () -> pdfPageAsTicket.getWeight());
    }

    @Test
    public void testGetPriorityAsEnum() {
        assertNull(pdfPageAsTicket.getPriorityAsEnum());
    }

}