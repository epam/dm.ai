package com.github.istin.dmtools.job;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationMergerTest {

    private ConfigurationMerger configurationMerger;

    @BeforeEach
    void setUp() {
        configurationMerger = new ConfigurationMerger();
    }

    @Test
    void testMergeConfigurations_basicMerge() {
        String fileJson = "{\"name\":\"test\",\"version\":\"1.0\",\"timeout\":30}";
        String encodedJson = "{\"timeout\":60,\"retries\":3}";

        String result = configurationMerger.mergeConfigurations(fileJson, encodedJson);
        
        JSONObject resultObj = new JSONObject(result);
        assertEquals("test", resultObj.getString("name"));
        assertEquals("1.0", resultObj.getString("version"));
        assertEquals(60, resultObj.getInt("timeout")); // overridden
        assertEquals(3, resultObj.getInt("retries")); // added
    }

    @Test
    void testMergeConfigurations_noEncodedJson() {
        String fileJson = "{\"name\":\"test\",\"version\":\"1.0\"}";
        String encodedJson = null;

        String result = configurationMerger.mergeConfigurations(fileJson, encodedJson);
        
        JSONObject resultObj = new JSONObject(result);
        assertEquals("test", resultObj.getString("name"));
        assertEquals("1.0", resultObj.getString("version"));
    }

    @Test
    void testMergeConfigurations_emptyEncodedJson() {
        String fileJson = "{\"name\":\"test\",\"version\":\"1.0\"}";
        String encodedJson = "";

        String result = configurationMerger.mergeConfigurations(fileJson, encodedJson);
        
        JSONObject resultObj = new JSONObject(result);
        assertEquals("test", resultObj.getString("name"));
        assertEquals("1.0", resultObj.getString("version"));
    }

    @Test
    void testMergeConfigurations_nullFileJson() {
        assertThrows(IllegalArgumentException.class, () -> {
            configurationMerger.mergeConfigurations(null, "{\"test\":\"value\"}");
        });
    }

    @Test
    void testMergeConfigurations_emptyFileJson() {
        assertThrows(IllegalArgumentException.class, () -> {
            configurationMerger.mergeConfigurations("", "{\"test\":\"value\"}");
        });
    }

    @Test
    void testMergeConfigurations_invalidFileJson() {
        String invalidJson = "{invalid json}";
        String encodedJson = "{\"test\":\"value\"}";

        assertThrows(IllegalArgumentException.class, () -> {
            configurationMerger.mergeConfigurations(invalidJson, encodedJson);
        });
    }

    @Test
    void testMergeConfigurations_invalidEncodedJson() {
        String fileJson = "{\"name\":\"test\"}";
        String invalidJson = "{invalid json}";

        assertThrows(IllegalArgumentException.class, () -> {
            configurationMerger.mergeConfigurations(fileJson, invalidJson);
        });
    }

    @Test
    void testDeepMerge_simpleObjects() {
        JSONObject base = new JSONObject("{\"a\":1,\"b\":2}");
        JSONObject override = new JSONObject("{\"b\":3,\"c\":4}");

        JSONObject result = configurationMerger.deepMerge(base, override);

        assertEquals(1, result.getInt("a"));
        assertEquals(3, result.getInt("b")); // overridden
        assertEquals(4, result.getInt("c")); // added
    }

    @Test
    void testDeepMerge_nestedObjects() {
        JSONObject base = new JSONObject("{\"config\":{\"timeout\":30,\"retries\":3},\"name\":\"test\"}");
        JSONObject override = new JSONObject("{\"config\":{\"timeout\":60,\"debug\":true},\"version\":\"2.0\"}");

        JSONObject result = configurationMerger.deepMerge(base, override);

        assertEquals("test", result.getString("name"));
        assertEquals("2.0", result.getString("version"));
        
        JSONObject configResult = result.getJSONObject("config");
        assertEquals(60, configResult.getInt("timeout")); // overridden
        assertEquals(3, configResult.getInt("retries")); // preserved
        assertTrue(configResult.getBoolean("debug")); // added
    }

    @Test
    void testDeepMerge_arrayReplacement() {
        JSONObject base = new JSONObject("{\"features\":[\"a\",\"b\"],\"config\":{\"arr\":[1,2,3]}}");
        JSONObject override = new JSONObject("{\"features\":[\"c\",\"d\"],\"config\":{\"arr\":[4,5]}}");

        JSONObject result = configurationMerger.deepMerge(base, override);

        // Arrays should be completely replaced, not merged
        assertEquals(2, result.getJSONArray("features").length());
        assertEquals("c", result.getJSONArray("features").getString(0));
        assertEquals("d", result.getJSONArray("features").getString(1));
        
        JSONObject configResult = result.getJSONObject("config");
        assertEquals(2, configResult.getJSONArray("arr").length());
        assertEquals(4, configResult.getJSONArray("arr").getInt(0));
        assertEquals(5, configResult.getJSONArray("arr").getInt(1));
    }

    @Test
    void testDeepMerge_deeplyNestedObjects() {
        JSONObject base = new JSONObject("{\"level1\":{\"level2\":{\"level3\":{\"value\":\"old\",\"keep\":\"this\"}}}}");
        JSONObject override = new JSONObject("{\"level1\":{\"level2\":{\"level3\":{\"value\":\"new\",\"add\":\"this\"}}}}");

        JSONObject result = configurationMerger.deepMerge(base, override);

        JSONObject level3 = result.getJSONObject("level1").getJSONObject("level2").getJSONObject("level3");
        assertEquals("new", level3.getString("value")); // overridden
        assertEquals("this", level3.getString("keep")); // preserved
        assertEquals("this", level3.getString("add")); // added
    }

    @Test
    void testDeepMerge_nullObjects() {
        JSONObject result1 = configurationMerger.deepMerge(null, null);
        assertEquals(0, result1.length());

        JSONObject base = new JSONObject("{\"test\":\"value\"}");
        JSONObject result2 = configurationMerger.deepMerge(base, null);
        assertEquals("value", result2.getString("test"));

        JSONObject override = new JSONObject("{\"override\":\"value\"}");
        JSONObject result3 = configurationMerger.deepMerge(null, override);
        assertEquals("value", result3.getString("override"));
    }

    @Test
    void testDeepMerge_mixedDataTypes() {
        JSONObject base = new JSONObject("{\"config\":{\"timeout\":30},\"enabled\":true,\"tags\":[\"old\"]}");
        JSONObject override = new JSONObject("{\"config\":\"simple string\",\"enabled\":false,\"tags\":[\"new\"],\"count\":42}");

        JSONObject result = configurationMerger.deepMerge(base, override);

        // Object replaced with string
        assertEquals("simple string", result.getString("config"));
        // Boolean overridden
        assertFalse(result.getBoolean("enabled"));
        // Array replaced
        assertEquals(1, result.getJSONArray("tags").length());
        assertEquals("new", result.getJSONArray("tags").getString(0));
        // New property added
        assertEquals(42, result.getInt("count"));
    }

    @Test
    void testDeepMerge_preservesOriginalObjects() {
        JSONObject base = new JSONObject("{\"original\":\"value\"}");
        JSONObject override = new JSONObject("{\"new\":\"value\"}");

        JSONObject result = configurationMerger.deepMerge(base, override);
        
        // Original objects should not be modified
        assertFalse(base.has("new"));
        assertFalse(override.has("original"));
        
        // Result should have both
        assertTrue(result.has("original"));
        assertTrue(result.has("new"));
    }

    @Test
    void testDeepMerge_emptyObjects() {
        JSONObject base = new JSONObject("{}");
        JSONObject override = new JSONObject("{\"test\":\"value\"}");

        JSONObject result = configurationMerger.deepMerge(base, override);
        assertEquals("value", result.getString("test"));

        JSONObject base2 = new JSONObject("{\"test\":\"value\"}");
        JSONObject override2 = new JSONObject("{}");

        JSONObject result2 = configurationMerger.deepMerge(base2, override2);
        assertEquals("value", result2.getString("test"));
    }

    @Test
    void testMergeConfigurations_complexScenario() {
        String fileJson = "{" +
            "\"name\":\"DMTools\"," +
            "\"version\":\"1.0\"," +
            "\"config\":{" +
                "\"timeout\":30," +
                "\"retries\":3," +
                "\"features\":[\"logging\",\"metrics\"]" +
            "}," +
            "\"integrations\":{" +
                "\"jira\":{\"enabled\":true,\"url\":\"https://old.jira.com\"}," +
                "\"confluence\":{\"enabled\":false}" +
            "}" +
        "}";

        String encodedJson = "{" +
            "\"version\":\"2.0\"," +
            "\"config\":{" +
                "\"timeout\":60," +
                "\"debug\":true," +
                "\"features\":[\"enhanced-logging\"]" +
            "}," +
            "\"integrations\":{" +
                "\"jira\":{\"url\":\"https://new.jira.com\",\"token\":\"secret\"}," +
                "\"slack\":{\"enabled\":true}" +
            "}," +
            "\"newProperty\":\"added\"" +
        "}";

        String result = configurationMerger.mergeConfigurations(fileJson, encodedJson);
        JSONObject resultObj = new JSONObject(result);

        // Top-level properties
        assertEquals("DMTools", resultObj.getString("name")); // preserved
        assertEquals("2.0", resultObj.getString("version")); // overridden
        assertEquals("added", resultObj.getString("newProperty")); // added

        // Config deep merge
        JSONObject config = resultObj.getJSONObject("config");
        assertEquals(60, config.getInt("timeout")); // overridden
        assertEquals(3, config.getInt("retries")); // preserved
        assertTrue(config.getBoolean("debug")); // added
        assertEquals(1, config.getJSONArray("features").length()); // array replaced
        assertEquals("enhanced-logging", config.getJSONArray("features").getString(0));

        // Integrations deep merge
        JSONObject integrations = resultObj.getJSONObject("integrations");
        
        JSONObject jira = integrations.getJSONObject("jira");
        assertTrue(jira.getBoolean("enabled")); // preserved
        assertEquals("https://new.jira.com", jira.getString("url")); // overridden
        assertEquals("secret", jira.getString("token")); // added

        JSONObject confluence = integrations.getJSONObject("confluence");
        assertFalse(confluence.getBoolean("enabled")); // preserved

        JSONObject slack = integrations.getJSONObject("slack");
        assertTrue(slack.getBoolean("enabled")); // added
    }
}
