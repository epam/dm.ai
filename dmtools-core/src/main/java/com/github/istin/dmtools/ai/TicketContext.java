package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.prompt.input.TicketBasedPrompt;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TicketContext implements ToText {

    @Getter
    private List<IComment> comments;

    public static interface OnTicketDetailsRequest {
        ITicket getTicketDetails(String key) throws IOException;
    }

    private final TrackerClient<? extends ITicket> trackerClient;
    @Getter
    private final ITicket ticket;
    @Setter
    @Getter
    private OnTicketDetailsRequest onTicketDetailsRequest;

    @Setter
    @Getter
    private List<ITicket> extraTickets = new ArrayList<>();

    public TicketContext(TrackerClient<? extends ITicket> trackerClient, ITicket ticket) {
        this.trackerClient = trackerClient;
        this.ticket = ticket;
    }

    public void prepareContext() throws IOException {
        prepareContext(false);
    }
    public void prepareContext(boolean withComments) throws IOException {
        Set<String> keys = IssuesIDsParser.extractAllJiraIDs(ticket.toText());
        extraTickets = new ArrayList<>();
        if (!keys.isEmpty()) {
            for (String key : keys) {
                if (key.equalsIgnoreCase(ticket.getKey())) {
                    continue;
                }

                try {
                    ITicket e = null;
                    if (onTicketDetailsRequest != null) {
                        e = onTicketDetailsRequest.getTicketDetails(key);
                    }
                    if (e == null) {
                        e = trackerClient.performTicket(key, trackerClient.getExtendedQueryFields());
                    }
                    extraTickets.add(e);
                } catch (AtlassianRestClient.RestClientException e) {

                }
            }
        }
        if (withComments) {
            comments = (List<IComment>) trackerClient.getComments(ticket.getKey(), ticket);
        }
    }

    @Override
    public String toText() throws IOException {
        StringBuilder text = new StringBuilder(new TicketBasedPrompt.TicketWrapper(trackerClient.getBasePath(), ticket).toText());
        if (comments != null) {
            text.append("<previous_discussion>").append("\n");
            for (IComment comment : comments) {
                text.append("\n").append(comment);
            }
            text.append("</previous_discussion>").append("\n");
        }
        for (ITicket extraTicket : extraTickets) {
            text.append("\n").append(new TicketBasedPrompt.TicketWrapper(trackerClient.getBasePath(), extraTicket).toText());
        }
        return text.toString();
    }

}
