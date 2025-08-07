package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;

import java.util.List;

public class PullRequestReview extends TicketBasedPrompt {

    public PullRequestReview(String basePath, String role, TicketContext ticketContext) {
        super(basePath, ticketContext);
        this.role = role;
    }

    private String role;

    private String diff;

    private List<? extends ITicket> testCases;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }

    public List<? extends ITicket> getExistingTickets() {
        return testCases;
    }

    public void setExistingTickets(List<? extends ITicket> existingTickets) {
        this.testCases = existingTickets;
    }
}
