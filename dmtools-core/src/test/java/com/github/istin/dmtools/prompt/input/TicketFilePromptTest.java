package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.prompt.input.TicketFilePrompt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IFile;

public class TicketFilePromptTest {

    private TicketFilePrompt ticketFilePrompt;
    private TicketContext mockTicketContext;
    private IFile mockFile;

    @Before
    public void setUp() {
        mockTicketContext = Mockito.mock(TicketContext.class);
        mockFile = Mockito.mock(IFile.class);
        ticketFilePrompt = new TicketFilePrompt("basePath", "role", mockTicketContext, mockFile);
    }

    @Test
    public void testGetFile() {
        IFile file = ticketFilePrompt.getFile();
        assertSame(mockFile, file);
    }

    @Test
    public void testSetFile() {
        IFile newFile = Mockito.mock(IFile.class);
        ticketFilePrompt.setFile(newFile);
        assertSame(newFile, ticketFilePrompt.getFile());
    }

    @Test
    public void testGetRole() {
        String role = ticketFilePrompt.getRole();
        assertEquals("role", role);
    }
}