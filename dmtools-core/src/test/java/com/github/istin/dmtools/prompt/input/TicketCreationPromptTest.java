package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.prompt.input.TicketCreationPrompt;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import com.github.istin.dmtools.ai.TicketContext;

public class TicketCreationPromptTest {

    private TicketCreationPrompt ticketCreationPrompt;
    private TicketContext mockTicketContext;

    @Before
    public void setUp() {
        mockTicketContext = mock(TicketContext.class);
        ticketCreationPrompt = new TicketCreationPrompt("basePath", mockTicketContext, "High");
    }

    @Test
    public void testGetPriorities() {
        String expectedPriorities = "High";
        String actualPriorities = ticketCreationPrompt.getPriorities();
        assertEquals(expectedPriorities, actualPriorities);
    }

    // TODO: Add more tests if additional methods are added to the class
}