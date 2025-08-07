package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.prompt.input.TextInputPrompt;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class TextInputPromptTest {

    private TextInputPrompt textInputPrompt;
    private ToText mockToText;
    private String basePath = "base/path";

    @Before
    public void setUp() {
        mockToText = mock(ToText.class);
        textInputPrompt = new TextInputPrompt(basePath, mockToText);
    }

    @Test
    public void testGetInput() {
        ToText input = textInputPrompt.getInput();
        assertNotNull(input);
    }

    @Test
    public void testGetExtraTickets() {
        List<ITicket> tickets = textInputPrompt.getExtraTickets();
        assertNotNull(tickets);
        assertEquals(0, tickets.size());
    }

    @Test
    public void testSetExtraTickets() {
        List<ITicket> tickets = new ArrayList<>();
        textInputPrompt.setExtraTickets(tickets);
        assertEquals(tickets, textInputPrompt.getExtraTickets());
    }

}