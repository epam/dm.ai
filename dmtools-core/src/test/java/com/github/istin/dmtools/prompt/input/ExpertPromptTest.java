package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.prompt.input.ExpertPrompt;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class ExpertPromptTest {

    private ExpertPrompt expertPrompt;
    private TicketContext ticketContextMock;
    private List<IComment> commentsMock;

    @Before
    public void setUp() {
        ticketContextMock = mock(TicketContext.class);
        commentsMock = mock(List.class);
        expertPrompt = new ExpertPrompt("basePath", ticketContextMock, "projectContext", "request");
    }

    @Test
    public void testGetProjectContext() {
        assertEquals("projectContext", expertPrompt.getProjectContext());
    }

    @Test
    public void testGetRequest() {
        assertEquals("request", expertPrompt.getRequest());
    }

    @Test
    public void testSetAndGetComments() {
        assertNull(expertPrompt.getComments());
        expertPrompt.setComments(commentsMock);
        assertEquals(commentsMock, expertPrompt.getComments());
    }
}