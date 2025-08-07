package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.prompt.input.TicketBasedPrompt;
import com.github.istin.dmtools.prompt.input.TicketBasedPrompt.TicketWrapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TicketBasedPromptTest {

    private TicketBasedPrompt ticketBasedPrompt;
    private ITicket mockTicket;
    private TicketContext mockTicketContext;
    private String basePath = "base/path";

    @Before
    public void setUp() {
        mockTicket = Mockito.mock(ITicket.class);
        mockTicketContext = Mockito.mock(TicketContext.class);
        when(mockTicketContext.getTicket()).thenReturn(mockTicket);
        when(mockTicketContext.getExtraTickets()).thenReturn(new ArrayList<>());

        ticketBasedPrompt = new TicketBasedPrompt(basePath, mockTicketContext);
    }


    @Test
    public void testAddExistingTicket() {
        ITicket existingTicket = Mockito.mock(ITicket.class);
        ticketBasedPrompt.addExistingTicket(existingTicket);
        assertEquals(1, ticketBasedPrompt.getExistingTickets().size());
    }

    @Test
    public void testSetExistingTickets() {
        List<ITicket> existingTickets = new ArrayList<>();
        ITicket existingTicket1 = Mockito.mock(ITicket.class);
        ITicket existingTicket2 = Mockito.mock(ITicket.class);
        existingTickets.add(existingTicket1);
        existingTickets.add(existingTicket2);

        ticketBasedPrompt.setExistingTickets(existingTickets);
        assertEquals(2, ticketBasedPrompt.getExistingTickets().size());
    }

    @Test
    public void testTicketWrapperGetTicketDescription() {
        // Set up mock attachment
        IAttachment mockAttachment = mock(IAttachment.class);
        when(mockAttachment.getName()).thenReturn("AttachmentName");
        when(mockAttachment.getUrl()).thenReturn("AttachmentUrl");

        // Create a concrete list of attachments
        List<IAttachment> attachments = new ArrayList<>();
        attachments.add(mockAttachment);

        // Set up mock ticket
        when(mockTicket.getTicketDescription()).thenReturn("Ticket Description");
        doReturn(attachments).when(mockTicket).getAttachments();

        // Create TicketWrapper
        TicketWrapper ticketWrapper = new TicketWrapper(basePath, mockTicket);

        // Expected description
        String expectedDescription = "Ticket Description\nAttachmentName AttachmentUrl";
        String cleanedDescription = HtmlCleaner.cleanAllHtmlTags(basePath, expectedDescription);

        // Perform the test
        String result = ticketWrapper.getTicketDescription();

        // Verify the result
        assertEquals("The cleaned description should match the expected", cleanedDescription, result);

        // Verify method calls
        verify(mockTicket).getTicketDescription();
        verify(mockTicket).getAttachments();
        verify(mockAttachment).getName();
        verify(mockAttachment).getUrl();
    }

    @Test
    public void testTicketWrapperToText() throws IOException {
        // Set up mock attachment
        IAttachment mockAttachment = mock(IAttachment.class);
        when(mockAttachment.getName()).thenReturn("AttachmentName");
        when(mockAttachment.getUrl()).thenReturn("AttachmentUrl");

        // Create a concrete list of attachments
        List<IAttachment> attachments = new ArrayList<>();
        attachments.add(mockAttachment);

        // Set up mock ticket
        when(mockTicket.toText()).thenReturn("Ticket Text");
        doReturn(attachments).when(mockTicket).getAttachments();

        // Create TicketWrapper
        TicketWrapper ticketWrapper = new TicketWrapper(basePath, mockTicket);

        // Expected text
        String expectedText = "Ticket Text\nAttachmentName AttachmentUrl";
        String cleanedText = HtmlCleaner.cleanAllHtmlTags(basePath, expectedText);

        // Perform the test
        String result = ticketWrapper.toText();

        // Verify the result
        assertEquals("The cleaned text should match the expected", cleanedText, result);

        // Verify method calls
        verify(mockTicket).toText();
        verify(mockTicket).getAttachments();
        verify(mockAttachment).getName();
        verify(mockAttachment).getUrl();
    }
}