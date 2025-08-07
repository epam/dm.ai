package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.IFile;

public class TicketFilePrompt extends TicketBasedPrompt {

    private final String role;
    private IFile file;

    public TicketFilePrompt(String basePath, String role, TicketContext ticketContext, IFile file) {
        super(basePath, ticketContext);
        this.role = role;
        this.file = file;
    }

    public IFile getFile() {
        return file;
    }

    public void setFile(IFile file) {
        this.file = file;
    }

    public String getRole() {
        return role;
    }
}
