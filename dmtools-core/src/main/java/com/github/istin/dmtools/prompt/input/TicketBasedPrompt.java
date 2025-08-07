package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.HtmlCleaner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TicketBasedPrompt {

    private final String basePath;
    private ITicket ticket;
    private String attachmentsDescription;
    public List<ITicket> getExtraTickets() {
        return extraTickets;
    }

    public void setExtraTickets(List<ITicket> extraTickets) {
        this.extraTickets = extraTickets;
    }

    private List<ITicket> extraTickets = new ArrayList<>();
    private List<ITicket> existingTickets = new ArrayList<>();

    public TicketBasedPrompt(String basePath, TicketContext ticketContext) {
        this.basePath = basePath;
        this.ticket = new TicketWrapper(basePath, ticketContext.getTicket());
        for (ITicket extraTicket : ticketContext.getExtraTickets()) {
            this.extraTickets.add(new TicketWrapper(basePath, extraTicket));
        }
    }

    public ITicket getTicket() {
        return ticket;
    }

    public void setTicket(ITicket ticket) {
        this.ticket = new TicketWrapper(basePath, ticket);
    }


    public List<? extends ITicket> getExistingTickets() {
        return existingTickets;
    }

    public void addExistingTicket(ITicket existingTicket) {
        existingTickets.add(new TicketWrapper(basePath, existingTicket));
    }

    public void setExistingTickets(List<? extends ITicket> existingTickets) {
        this.existingTickets.clear();
        for (ITicket ticket : existingTickets) {
            this.existingTickets.add(new TicketWrapper(basePath, ticket));
        }
    }

    public static class TicketWrapper extends ITicket.Wrapper {

        private final String basePath;

        public TicketWrapper(String basePath, ITicket ticket) {
            super(ticket);
            this.basePath = basePath;
        }

        @Override
        public String getTicketDescription() {
            String ticketDescription = getWrapped().getTicketDescription();
            List<? extends IAttachment> attachments = getWrapped().getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                for (IAttachment attachment : attachments) {
                    ticketDescription = ticketDescription + ("\n" + attachment.getName() + " " + attachment.getUrl());
                }
            }
            return HtmlCleaner.cleanAllHtmlTags(basePath, ticketDescription);
        }

        @Override
        public String toText() throws IOException {
            String text = super.toText();
            List<? extends IAttachment> attachments = getWrapped().getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                for (IAttachment attachment : attachments) {
                    text = text + ("\n" + attachment.getName() + " " + attachment.getUrl());
                }
            }
            return HtmlCleaner.cleanAllHtmlTags(basePath, text);
        }
    }

}
