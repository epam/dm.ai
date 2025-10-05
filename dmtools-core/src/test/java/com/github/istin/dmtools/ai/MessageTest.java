package com.github.istin.dmtools.ai;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testConstructor() {
        List<File> files = new ArrayList<>();
        Message message = new Message("user", "Test message", files);
        
        assertNotNull(message);
        assertEquals("user", message.getRole());
        assertEquals("Test message", message.getText());
        assertEquals(files, message.getFiles());
    }

    @Test
    void testGettersAndSetters() {
        Message message = new Message("user", "Initial text", null);
        
        assertEquals("user", message.getRole());
        assertEquals("Initial text", message.getText());
        assertNull(message.getFiles());
        
        message.setRole("assistant");
        message.setText("Updated text");
        List<File> files = Arrays.asList(new File("test.txt"));
        message.setFiles(files);
        
        assertEquals("assistant", message.getRole());
        assertEquals("Updated text", message.getText());
        assertEquals(files, message.getFiles());
    }

    @Test
    void testWithNullText() {
        Message message = new Message("user", null, null);
        
        assertNotNull(message);
        assertEquals("user", message.getRole());
        assertNull(message.getText());
        assertNull(message.getFiles());
    }

    @Test
    void testWithEmptyText() {
        Message message = new Message("assistant", "", new ArrayList<>());
        
        assertNotNull(message);
        assertEquals("assistant", message.getRole());
        assertEquals("", message.getText());
        assertNotNull(message.getFiles());
        assertTrue(message.getFiles().isEmpty());
    }

    @Test
    void testWithMultipleFiles() {
        List<File> files = Arrays.asList(
            new File("file1.txt"),
            new File("file2.txt"),
            new File("file3.txt")
        );
        Message message = new Message("user", "Message with files", files);
        
        assertEquals(3, message.getFiles().size());
        assertEquals("file1.txt", message.getFiles().get(0).getName());
        assertEquals("file2.txt", message.getFiles().get(1).getName());
        assertEquals("file3.txt", message.getFiles().get(2).getName());
    }
}
