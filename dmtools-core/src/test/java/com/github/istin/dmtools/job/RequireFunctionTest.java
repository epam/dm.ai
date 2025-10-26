package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests specifically for the require() function implementation
 */
class RequireFunctionTest {

    @Mock
    private TrackerClient<?> trackerClient;
    
    @Mock
    private AI ai;
    
    @Mock
    private Confluence confluence;
    
    @Mock
    private SourceCode sourceCode;
    
    private JobJavaScriptBridge bridge;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bridge = new JobJavaScriptBridge(trackerClient, ai, confluence, sourceCode, null);
    }
    
    /**
     * Helper method to convert JavaScript execution result to JSONObject
     */
    private JSONObject toJSONObject(Object result) {
        if (result instanceof String) {
            return new JSONObject((String) result);
        } else if (result instanceof JSONObject) {
            return (JSONObject) result;
        } else {
            throw new IllegalArgumentException("Expected String or JSONObject, got: " + result.getClass());
        }
    }

    @Test
    void testRequireFunctionExists() throws Exception {
        Path testScript = tempDir.resolve("testRequireExists.js");
        Files.writeString(testScript, """
            function action(params) {
                return {
                    success: true,
                    requireExists: typeof require === 'function'
                };
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertTrue(jsonResult.getBoolean("requireExists"));
    }

    @Test
    void testRequireWithInvalidArguments() throws Exception {
        Path testScript = tempDir.resolve("testRequireInvalid.js");
        Files.writeString(testScript, """
            function action(params) {
                try {
                    require(); // No arguments
                    return { success: false, message: "Should have thrown error" };
                } catch (error) {
                    return {
                        success: true,
                        errorMessage: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertTrue(jsonResult.getString("errorMessage").contains("exactly one argument"));
    }

    @Test
    void testRequireWithMultipleArguments() throws Exception {
        Path testScript = tempDir.resolve("testRequireMultiple.js");
        Files.writeString(testScript, """
            function action(params) {
                try {
                    require('module1', 'module2'); // Too many arguments
                    return { success: false, message: "Should have thrown error" };
                } catch (error) {
                    return {
                        success: true,
                        errorMessage: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertTrue(jsonResult.getString("errorMessage").contains("exactly one argument"));
    }

    @Test
    void testRequireNonexistentModule() throws Exception {
        Path testScript = tempDir.resolve("testRequireNonexistent.js");
        Files.writeString(testScript, """
            function action(params) {
                try {
                    require('./nonexistent.js');
                    return { success: false, message: "Should have thrown error" };
                } catch (error) {
                    return {
                        success: true,
                        errorMessage: error.toString()
                    };
                }
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertTrue(jsonResult.getString("errorMessage").contains("require"));
    }

    @Test
    void testCurrentScriptDirectoryResolution() throws Exception {
        // Create nested directory structure
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        
        // Create a module in the subdirectory
        Path moduleInSubDir = subDir.resolve("helper.js");
        Files.writeString(moduleInSubDir, """
            module.exports = {
                getValue: function() {
                    return "from-subdir";
                }
            };
            """);

        // Create main script in subdirectory that requires local module
        Path mainScript = subDir.resolve("main.js");
        Files.writeString(mainScript, """
            const helper = require('./helper.js');
            
            function action(params) {
                return {
                    success: true,
                    value: helper.getValue()
                };
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertEquals("from-subdir", jsonResult.getString("value"));
    }

    @Test
    void testParentDirectoryResolution() throws Exception {
        // Create nested directory structure
        Path subDir = tempDir.resolve("nested");
        Files.createDirectories(subDir);
        
        // Create a module in parent directory
        Path parentModule = tempDir.resolve("parentHelper.js");
        Files.writeString(parentModule, """
            module.exports = {
                getParentValue: function() {
                    return "from-parent";
                }
            };
            """);

        // Create script in subdirectory that requires parent module
        Path nestedScript = subDir.resolve("nested.js");
        Files.writeString(nestedScript, """
            const parent = require('../parentHelper.js');
            
            function action(params) {
                return {
                    success: true,
                    value: parent.getParentValue()
                };
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(nestedScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertEquals("from-parent", jsonResult.getString("value"));
    }

    @Test
    void testModuleCacheIsolation() throws Exception {
        // Create a module that tracks state
        Path statefulModule = tempDir.resolve("stateful.js");
        Files.writeString(statefulModule, """
            var state = { counter: 0 };
            
            module.exports = {
                increment: function() {
                    state.counter++;
                    return state.counter;
                },
                getCounter: function() {
                    return state.counter;
                }
            };
            """);

        // First execution
        Path script1 = tempDir.resolve("script1.js");
        Files.writeString(script1, String.format("""
            const stateful = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    counter: stateful.increment()
                };
            }
            """, "./" + statefulModule.getFileName()));

        JSONObject params1 = new JSONObject();
        Object result1 = bridge.executeJavaScript(script1.toString(), params1);
        
        JSONObject jsonResult1 = toJSONObject(result1);
        assertEquals(1, jsonResult1.getInt("counter"));

        // Second execution - should see cached module state
        Path script2 = tempDir.resolve("script2.js");
        Files.writeString(script2, String.format("""
            const stateful2 = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    counter: stateful2.increment()
                };
            }
            """, "./" + statefulModule.getFileName()));

        JSONObject params2 = new JSONObject();
        Object result2 = bridge.executeJavaScript(script2.toString(), params2);
        
        JSONObject jsonResult2 = toJSONObject(result2);
        // Should be 2 if module cache persists across executions
        assertEquals(2, jsonResult2.getInt("counter"));
    }

    @Test
    void testRequireWithAbsolutePath() throws Exception {
        // Create a module
        Path absoluteModule = tempDir.resolve("absolute.js");
        Files.writeString(absoluteModule, """
            module.exports = {
                getValue: function() {
                    return "absolute-path";
                }
            };
            """);

        // Test script that requires with absolute path
        Path testScript = tempDir.resolve("testAbsolute.js");
        Files.writeString(testScript, String.format("""
            const abs = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    value: abs.getValue()
                };
            }
            """, absoluteModule.toString()));

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(testScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertEquals("absolute-path", jsonResult.getString("value"));
    }
}
