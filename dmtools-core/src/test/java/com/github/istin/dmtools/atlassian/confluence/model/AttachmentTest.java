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

    @Test
    void testGetDownloadLink_WhenLinksAndDownloadPresent() {
        JSONObject links = new JSONObject();
        links.put("download", "/download/attachments/123/file.pdf");
        
        JSONObject json = new JSONObject();
        json.put("title", "file.pdf");
        json.put("_links", links);
        
        Attachment attachment = new Attachment(json);
        
        assertEquals("/download/attachments/123/file.pdf", attachment.getDownloadLink());
    }

    @Test
    void testGetDownloadLink_WhenLinksNull() {
        JSONObject json = new JSONObject();
        json.put("title", "file.pdf");
        
        Attachment attachment = new Attachment(json);
        
        assertNull(attachment.getDownloadLink());
    }

    @Test
    void testGetDownloadLink_WhenLinksExistsButDownloadMissing() {
        JSONObject links = new JSONObject();
        links.put("self", "/rest/api/content/123");
        
        JSONObject json = new JSONObject();
        json.put("title", "file.pdf");
        json.put("_links", links);
        
        Attachment attachment = new Attachment(json);
        
        assertNull(attachment.getDownloadLink());
    }

    @Test
    void testGetDownloadLink_WhenDownloadIsEmptyString() {
        JSONObject links = new JSONObject();
        links.put("download", "");
        
        JSONObject json = new JSONObject();
        json.put("title", "file.pdf");
        json.put("_links", links);
        
        Attachment attachment = new Attachment(json);
        
        // optString returns empty string for empty value, not null
        assertEquals("", attachment.getDownloadLink());
    }

    @Test
    void testLinksConstant() {
        assertEquals("_links", Attachment.LINKS);
    }

    @Test
    void testDownloadConstant() {
        assertEquals("download", Attachment.DOWNLOAD);
    }
}