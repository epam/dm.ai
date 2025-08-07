package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;

import java.util.List;

public class SimilarStoriesPrompt extends TicketBasedPrompt {

    private List<? extends ITicket> stories;

    private ITicket similarTicket;

    private String role;

    public SimilarStoriesPrompt(String basePath, TicketContext ticketContext, List<? extends ITicket> stories) {
        super(basePath, ticketContext);
        this.stories = stories;
    }

    public SimilarStoriesPrompt(String basePath, String role, TicketContext ticketContext, ITicket similarTicket) {
        super(basePath, ticketContext);
        this.similarTicket = similarTicket;
        this.role = role;
    }

    public List<? extends ITicket> getStories() {
        return stories;
    }

    public ITicket getSimilarTicket() {
        return similarTicket;
    }

    public void setSimilarTicket(ITicket similarTicket) {
        this.similarTicket = similarTicket;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
