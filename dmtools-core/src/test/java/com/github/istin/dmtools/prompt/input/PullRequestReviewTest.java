package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.prompt.input.PullRequestReview;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PullRequestReviewTest {

    private PullRequestReview pullRequestReview;
    private TicketContext mockTicketContext;

    @Before
    public void setUp() {
        mockTicketContext = mock(TicketContext.class);
        pullRequestReview = new PullRequestReview("basePath", "developer", mockTicketContext);
    }

    @Test
    public void testGetRole() {
        assertEquals("developer", pullRequestReview.getRole());
    }

    @Test
    public void testSetRole() {
        pullRequestReview.setRole("reviewer");
        assertEquals("reviewer", pullRequestReview.getRole());
    }

    @Test
    public void testGetDiff() {
        pullRequestReview.setDiff("diff content");
        assertEquals("diff content", pullRequestReview.getDiff());
    }

    @Test
    public void testSetDiff() {
        pullRequestReview.setDiff("new diff content");
        assertEquals("new diff content", pullRequestReview.getDiff());
    }

    @Test
    public void testGetExistingTickets() {
        List<ITicket> mockTickets = mock(List.class);
        pullRequestReview.setExistingTickets(mockTickets);
        assertEquals(mockTickets, pullRequestReview.getExistingTickets());
    }

    @Test
    public void testSetExistingTickets() {
        List<ITicket> mockTickets = mock(List.class);
        pullRequestReview.setExistingTickets(mockTickets);
        assertEquals(mockTickets, pullRequestReview.getExistingTickets());
    }
}