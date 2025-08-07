package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IFile;
import com.github.istin.dmtools.prompt.input.CodeGeneration;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class CodeGenerationTest {

    private CodeGeneration codeGeneration;
    private TicketContext ticketContext;

    @Before
    public void setUp() {
        ticketContext = mock(TicketContext.class);
        codeGeneration = new CodeGeneration("basePath", "developer", ticketContext);
    }

    @Test
    public void testGetRole() {
        assertEquals("developer", codeGeneration.getRole());
    }

    @Test
    public void testSetRole() {
        codeGeneration.setRole("tester");
        assertEquals("tester", codeGeneration.getRole());
    }

    @Test
    public void testGetFiles() {
        List<IFile> files = codeGeneration.getFiles();
        assertNotNull(files);
        assertEquals(0, files.size());
    }

    @Test
    public void testSetFiles() {
        List<IFile> files = new ArrayList<>();
        codeGeneration.setFiles(files);
        assertEquals(files, codeGeneration.getFiles());
    }

    @Test
    public void testGetCommits() {
        List<ICommit> commits = codeGeneration.getCommits();
        assertEquals(null, commits);
    }

    @Test
    public void testSetCommits() {
        List<ICommit> commits = new ArrayList<>();
        codeGeneration.setCommits(commits);
        assertEquals(commits, codeGeneration.getCommits());
    }
}