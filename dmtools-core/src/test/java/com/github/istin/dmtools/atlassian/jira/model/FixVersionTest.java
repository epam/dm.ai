package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class FixVersionTest {

    @Test
    void testDefaultConstructor() {
        FixVersion fixVersion = new FixVersion();
        assertNotNull(fixVersion);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"id\":\"1000\",\"name\":\"Version 1.0\"}";
        FixVersion fixVersion = new FixVersion(json);
        
        assertNotNull(fixVersion);
        assertEquals("Version 1.0", fixVersion.getName());
        assertEquals("1000", fixVersion.getIdAsString());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("id", "2000");
        json.put("name", "Version 2.0");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertNotNull(fixVersion);
        assertEquals("Version 2.0", fixVersion.getName());
    }

    @Test
    void testGetName() {
        JSONObject json = new JSONObject();
        json.put("name", "Sprint 1");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertEquals("Sprint 1", fixVersion.getName());
    }

    @Test
    void testGetIterationName() {
        JSONObject json = new JSONObject();
        json.put("name", "Q1 2024");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertEquals("Q1 2024", fixVersion.getIterationName());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", "123");
        json.put("name", "Test Version");
        
        FixVersion fixVersion = new FixVersion(json);
        
        // getId() returns hashCode of id string
        assertEquals("123".hashCode(), fixVersion.getId());
    }

    @Test
    void testGetIdAsString() {
        JSONObject json = new JSONObject();
        json.put("id", "456");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertEquals("456", fixVersion.getIdAsString());
    }

    @Test
    void testGetUserReleaseDate() {
        JSONObject json = new JSONObject();
        json.put("userReleaseDate", "05/Nov/24");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertEquals("05/Nov/24", fixVersion.getUserReleaseDate());
    }

    @Test
    void testGetUserStartDate() {
        JSONObject json = new JSONObject();
        json.put("userStartDate", "01/Oct/24");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertEquals("01/Oct/24", fixVersion.getUserStartDate());
    }

    @Test
    void testSetUserReleaseDate() {
        FixVersion fixVersion = new FixVersion();
        fixVersion.setUserReleaseDate("10/Dec/24");
        
        assertEquals("10/Dec/24", fixVersion.getUserReleaseDate());
    }

    @Test
    void testSetUserStartDate() {
        FixVersion fixVersion = new FixVersion();
        fixVersion.setUserStartDate("01/Nov/24");
        
        assertEquals("01/Nov/24", fixVersion.getUserStartDate());
    }

    @Test
    void testSetReleaseDate() {
        FixVersion fixVersion = new FixVersion();
        fixVersion.setReleaseDate("2024-12-31");
        
        assertEquals("2024-12-31", fixVersion.getJSONObject().getString("releaseDate"));
    }

    @Test
    void testSetStartDate() {
        FixVersion fixVersion = new FixVersion();
        fixVersion.setStartDate("2024-01-01");
        
        assertEquals("2024-01-01", fixVersion.getJSONObject().getString("startDate"));
    }

    @Test
    void testGetArchived_True() {
        JSONObject json = new JSONObject();
        json.put("archived", true);
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.getArchived());
    }

    @Test
    void testGetArchived_False() {
        JSONObject json = new JSONObject();
        json.put("archived", false);
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertFalse(fixVersion.getArchived());
    }

    @Test
    void testGetReleased_True() {
        JSONObject json = new JSONObject();
        json.put("released", true);
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.getReleased());
    }

    @Test
    void testGetReleased_False() {
        JSONObject json = new JSONObject();
        json.put("released", false);
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertFalse(fixVersion.getReleased());
    }

    @Test
    void testIsReleased() {
        JSONObject json = new JSONObject();
        json.put("released", true);
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.isReleased());
    }

    @Test
    void testIsNotPlanned_NotPlanned() {
        JSONObject json = new JSONObject();
        json.put("name", "Not Planned");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.isNotPlanned());
    }

    @Test
    void testIsNotPlanned_Unknown() {
        JSONObject json = new JSONObject();
        json.put("name", "unknown");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.isNotPlanned());
    }

    @Test
    void testIsNotPlanned_CaseInsensitive() {
        JSONObject json = new JSONObject();
        json.put("name", "NOT PLANNED");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertTrue(fixVersion.isNotPlanned());
    }

    @Test
    void testIsNotPlanned_False() {
        JSONObject json = new JSONObject();
        json.put("name", "Version 1.0");
        
        FixVersion fixVersion = new FixVersion(json);
        
        assertFalse(fixVersion.isNotPlanned());
    }

    @Test
    void testCompareTo_Less() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "A Version");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "B Version");
        
        assertTrue(version1.compareTo(version2) < 0);
    }

    @Test
    void testCompareTo_Greater() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Z Version");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "A Version");
        
        assertTrue(version1.compareTo(version2) > 0);
    }

    @Test
    void testCompareTo_Equal() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Same Version");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "Same Version");
        
        assertEquals(0, version1.compareTo(version2));
    }

    @Test
    void testEquals_True() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Version 1.0");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "Version 1.0");
        
        assertTrue(version1.equals(version2));
    }

    @Test
    void testEquals_False() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Version 1.0");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "Version 2.0");
        
        assertFalse(version1.equals(version2));
    }

    @Test
    void testHashCode_SameForEqualNames() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Version 1.0");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "Version 1.0");
        
        assertEquals(version1.hashCode(), version2.hashCode());
    }

    @Test
    void testHashCode_DifferentForDifferentNames() {
        FixVersion version1 = new FixVersion();
        version1.getJSONObject().put("name", "Version 1.0");
        
        FixVersion version2 = new FixVersion();
        version2.getJSONObject().put("name", "Version 2.0");
        
        assertNotEquals(version1.hashCode(), version2.hashCode());
    }
}