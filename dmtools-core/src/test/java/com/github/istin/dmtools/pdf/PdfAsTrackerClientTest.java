// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pdf;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.pdf.model.PdfPageAsTicket;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.*;

public class PdfAsTrackerClientTest {

    private static final Logger logger = LoggerFactory.getLogger(PdfAsTrackerClientTest.class);
    private PdfAsTrackerClient pdfAsTrackerClient;

    @Before
    public void setUp() throws Exception {
        pdfAsTrackerClient = new PdfAsTrackerClient("test_data/test_pdf_sources");
    }

    @Test
    public void testMain() throws Exception {
        List<PdfPageAsTicket> pdfPageAsTickets = pdfAsTrackerClient.searchAndPerform(null, null);
        assertNotNull("PDF page tickets should not be null", pdfPageAsTickets);

        for (PdfPageAsTicket ticket : pdfPageAsTickets) {
            logger.info("Key: {}", ticket.getKey());
            logger.info("Description: {}", ticket.getTicketDescription());
            logger.info("Page snapshot: {}", ticket.getPageSnapshot());
            List<? extends IAttachment> attachments = ticket.getAttachments();
            logger.info("Attachments:");
            for (IAttachment attachment : attachments) {
                logger.info("  {}", attachment.getUrl());
            }
        }

        if (!pdfPageAsTickets.isEmpty()) {
            PdfPageAsTicket pdfPageAsTicket = pdfPageAsTickets.get(0);
            pdfAsTrackerClient.addLabelIfNotExists(pdfPageAsTicket, "test label");
            pdfAsTrackerClient.deleteLabelInTicket(pdfPageAsTicket, "test label");
        }
    }
}