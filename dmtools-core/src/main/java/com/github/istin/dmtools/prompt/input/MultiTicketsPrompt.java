package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.utils.HtmlCleaner;

public class MultiTicketsPrompt extends TicketBasedPrompt {

    private String role;
    private String projectSpecifics;

    private String existingContent = "";

    public String getExistingContent() {
        return existingContent;
    }

    public MultiTicketsPrompt(String basePath, String role, String projectSpecifics, TicketContext ticketContext, String existingContent) {
        super(basePath, ticketContext);
        this.role = role;
        this.projectSpecifics = projectSpecifics;
        this.existingContent = HtmlCleaner.cleanAllHtmlTags(basePath, existingContent);
    }

    public ITicket getContent() {
        return content;
    }

    public void setContent(ITicket content) {
        this.content = content;
    }

    private ITicket content;

    public MultiTicketsPrompt(String basePath, String role, String projectSpecifics, TicketContext ticketContext) {
        super(basePath, ticketContext);
        this.role = role;
        this.projectSpecifics = projectSpecifics;
    }

    public String getRole() {
        return role;
    }

    public String getProjectSpecifics() {
        return projectSpecifics;
    }

}
