package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.prompt.input.MultiTicketsPrompt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.HtmlCleaner;

public class MultiTicketsPromptTest {

    private MultiTicketsPrompt multiTicketsPrompt;
    private TicketContext mockTicketContext;
    private ITicket mockTicket;

    @Before
    public void setUp() {
        mockTicketContext = Mockito.mock(TicketContext.class);
        mockTicket = Mockito.mock(ITicket.class);
        multiTicketsPrompt = new MultiTicketsPrompt("basePath", "role", "projectSpecifics", mockTicketContext, "existingContent");
    }

    @Test
    public void testGetExistingContent() {
        String expectedContent = HtmlCleaner.cleanAllHtmlTags("basePath", "existingContent");
        assertEquals(expectedContent, multiTicketsPrompt.getExistingContent());
    }

    @Test
    public void testGetRole() {
        assertEquals("role", multiTicketsPrompt.getRole());
    }

    @Test
    public void testGetProjectSpecifics() {
        assertEquals("projectSpecifics", multiTicketsPrompt.getProjectSpecifics());
    }

    @Test
    public void testSetContent() {
        multiTicketsPrompt.setContent(mockTicket);
        assertEquals(mockTicket, multiTicketsPrompt.getContent());
    }

    @Test
    public void testConstructorWithoutExistingContent() {
        MultiTicketsPrompt promptWithoutContent = new MultiTicketsPrompt("basePath", "role", "projectSpecifics", mockTicketContext);
        assertEquals("role", promptWithoutContent.getRole());
        assertEquals("projectSpecifics", promptWithoutContent.getProjectSpecifics());
        assertEquals("", promptWithoutContent.getExistingContent());
    }
}