package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IFile;

import java.util.ArrayList;
import java.util.List;

public class CodeGeneration extends TicketBasedPrompt {

    private List<ICommit> commits;

    public CodeGeneration(String basePath, String role, TicketContext ticketContext) {
        super(basePath, ticketContext);
        this.role = role;
    }

    private String role;


    private List<IFile> files = new ArrayList<>();

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<IFile> getFiles() {
        return files;
    }

    public void setFiles(List<IFile> files) {
        this.files = files;
    }

    public void setCommits(List<ICommit> commits) {
        this.commits = commits;
    }

    public List<ICommit> getCommits() {
        return commits;
    }
}
