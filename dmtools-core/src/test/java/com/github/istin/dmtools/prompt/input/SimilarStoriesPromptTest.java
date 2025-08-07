package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.prompt.input.SimilarStoriesPrompt;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class SimilarStoriesPromptTest {

    private SimilarStoriesPrompt similarStoriesPrompt;
    private TicketContext mockTicketContext;
    private List<? extends ITicket> mockStories;
    private ITicket mockSimilarTicket;

    @Before
    public void setUp() {
        mockTicketContext = mock(TicketContext.class);
        mockStories = mock(List.class);
        mockSimilarTicket = mock(ITicket.class);
    }

    @Test
    public void testConstructorWithStories() {
        similarStoriesPrompt = new SimilarStoriesPrompt("basePath", mockTicketContext, mockStories);
        assertEquals(mockStories, similarStoriesPrompt.getStories());
        assertNull(similarStoriesPrompt.getSimilarTicket());
        assertNull(similarStoriesPrompt.getRole());
    }

    @Test
    public void testConstructorWithSimilarTicket() {
        similarStoriesPrompt = new SimilarStoriesPrompt("basePath", "role", mockTicketContext, mockSimilarTicket);
        assertEquals(mockSimilarTicket, similarStoriesPrompt.getSimilarTicket());
        assertEquals("role", similarStoriesPrompt.getRole());
        assertNull(similarStoriesPrompt.getStories());
    }

    @Test
    public void testSetSimilarTicket() {
        similarStoriesPrompt = new SimilarStoriesPrompt("basePath", "role", mockTicketContext, null);
        similarStoriesPrompt.setSimilarTicket(mockSimilarTicket);
        assertEquals(mockSimilarTicket, similarStoriesPrompt.getSimilarTicket());
    }

    @Test
    public void testSetRole() {
        similarStoriesPrompt = new SimilarStoriesPrompt("basePath", "role", mockTicketContext, mockSimilarTicket);
        similarStoriesPrompt.setRole("newRole");
        assertEquals("newRole", similarStoriesPrompt.getRole());
    }
}