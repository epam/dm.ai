package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IComment;

import java.util.List;

public class ExpertPrompt extends TicketBasedPrompt {

    private String projectContext;

    private String request;
    private List<IComment> comments;

    public ExpertPrompt(String basePath, TicketContext ticketContext, String projectContext, String request) {
        super(basePath, ticketContext);
        this.projectContext = projectContext;
        this.request = request;
    }

    public String getProjectContext() {
        return projectContext;
    }

    public String getRequest() {
        return request;
    }

    public void setComments(List<IComment> comments) {
        this.comments = comments;
    }

    public List<IComment> getComments() {
        return comments;
    }
}
