package com.github.istin.dmtools.atlassian.confluence.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {

    @Test
    void testDefaultConstructor() {
        Storage storage = new Storage();
        assertNotNull(storage);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"value\":\"<p>Content body</p>\"}";
        Storage storage = new Storage(json);
        
        assertNotNull(storage);
        assertEquals("<p>Content body</p>", storage.getValue());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("value", "<h1>Title</h1><p>Paragraph</p>");
        
        Storage storage = new Storage(json);
        
        assertNotNull(storage);
        assertEquals("<h1>Title</h1><p>Paragraph</p>", storage.getValue());
    }

    @Test
    void testGetValue() {
        JSONObject json = new JSONObject();
        json.put("value", "Plain text content");
        
        Storage storage = new Storage(json);
        
        assertEquals("Plain text content", storage.getValue());
    }

    @Test
    void testGetValue_Null() {
        Storage storage = new Storage();
        assertNull(storage.getValue());
    }

    @Test
    void testValueConstant() {
        assertEquals("value", Storage.VALUE);
    }

    @Test
    void testGetValue_EmptyString() {
        JSONObject json = new JSONObject();
        json.put("value", "");
        
        Storage storage = new Storage(json);
        
        assertEquals("", storage.getValue());
    }

    @Test
    void testGetValue_ComplexHtml() {
        String complexHtml = "<div><h2>Header</h2><ul><li>Item 1</li><li>Item 2</li></ul></div>";
        JSONObject json = new JSONObject();
        json.put("value", complexHtml);
        
        Storage storage = new Storage(json);
        
        assertEquals(complexHtml, storage.getValue());
    }
}