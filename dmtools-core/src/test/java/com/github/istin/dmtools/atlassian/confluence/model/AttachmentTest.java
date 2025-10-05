package com.github.istin.dmtools.atlassian.confluence.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentTest {

    @Test
    void testDefaultConstructor() {
        Attachment attachment = new Attachment();
        assertNotNull(attachment);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"title\":\"test-attachment.pdf\"}";
        Attachment attachment = new Attachment(json);
        
        assertNotNull(attachment);
        assertEquals("test-attachment.pdf", attachment.getTitle());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("title", "document.docx");
        
        Attachment attachment = new Attachment(json);
        
        assertNotNull(attachment);
        assertEquals("document.docx", attachment.getTitle());
    }

    @Test
    void testGetTitle() {
        JSONObject json = new JSONObject();
        json.put("title", "image.png");
        
        Attachment attachment = new Attachment(json);
        
        assertEquals("image.png", attachment.getTitle());
    }

    @Test
    void testGetTitle_Null() {
        Attachment attachment = new Attachment();
        assertNull(attachment.getTitle());
    }

    @Test
    void testTitleConstant() {
        assertEquals("title", Attachment.TITLE);
    }
}