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
 * Unit tests for the JavaScript module system in JobJavaScriptBridge
 */
class JobJavaScriptBridgeModuleTest {

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

    @Test
    void testBasicModuleExport() throws Exception {
        // Create a simple module
        Path moduleFile = tempDir.resolve("testModule.js");
        Files.writeString(moduleFile, """
            function greet(name) {
                return "Hello, " + name + "!";
            }
            
            module.exports = {
                greet: greet
            };
            """);

        // Create main script that uses the module
        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const testModule = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    message: testModule.greet("World")
                };
            }
            """, "./" + moduleFile.getFileName()));

        // Execute the main script
        JSONObject params = new JSONObject();
        params.put("test", "value");
        
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        assertEquals("Hello, World!", toJSONObject(result).getString("message"));
    }

    @Test
    void testModuleCaching() throws Exception {
        // Create a module that counts how many times it's loaded
        Path moduleFile = tempDir.resolve("counterModule.js");
        Files.writeString(moduleFile, """
            // This should only execute once due to caching
            var loadCount = (typeof global !== 'undefined' && global.loadCount) ? global.loadCount + 1 : 1;
            if (typeof global !== 'undefined') global.loadCount = loadCount;
            
            module.exports = {
                getLoadCount: function() {
                    return loadCount;
                }
            };
            """);

        // Create main script that requires the module multiple times
        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const counter1 = require('%s');
            const counter2 = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    count1: counter1.getLoadCount(),
                    count2: counter2.getLoadCount(),
                    same: counter1 === counter2
                };
            }
            """, "./" + moduleFile.getFileName(), "./" + moduleFile.getFileName()));

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        // Both should return the same count (1) due to caching
        assertEquals(1, toJSONObject(result).getInt("count1"));
        assertEquals(1, toJSONObject(result).getInt("count2"));
    }

    @Test
    void testRelativePathResolution() throws Exception {
        // Create directory structure: common/utils.js
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir);
        
        Path utilsFile = commonDir.resolve("utils.js");
        Files.writeString(utilsFile, """
            function formatMessage(msg) {
                return "[FORMATTED] " + msg;
            }
            
            module.exports = {
                formatMessage: formatMessage
            };
            """);

        // Create main script in parent directory
        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, """
            const utils = require('./common/utils.js');
            
            function action(params) {
                return {
                    success: true,
                    message: utils.formatMessage("Test message")
                };
            }
            """);

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        assertEquals("[FORMATTED] Test message", toJSONObject(result).getString("message"));
    }

    @Test
    void testNestedModuleRequires() throws Exception {
        // Create a helper module
        Path helperFile = tempDir.resolve("helper.js");
        Files.writeString(helperFile, """
            module.exports = {
                multiply: function(a, b) {
                    return a * b;
                }
            };
            """);

        // Create a math module that requires the helper
        Path mathFile = tempDir.resolve("math.js");
        Files.writeString(mathFile, String.format("""
            const helper = require('%s');
            
            function square(x) {
                return helper.multiply(x, x);
            }
            
            module.exports = {
                square: square,
                cube: function(x) {
                    return helper.multiply(x, square(x));
                }
            };
            """, "./" + helperFile.getFileName()));

        // Create main script
        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const math = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    square: math.square(4),
                    cube: math.cube(3)
                };
            }
            """, "./" + mathFile.getFileName()));

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        assertEquals(16, toJSONObject(result).getInt("square"));
        assertEquals(27, toJSONObject(result).getInt("cube"));
    }

    @Test
    void testModuleNotFound() throws Exception {
        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, """
            const missing = require('./nonexistent.js');
            
            function action(params) {
                return { success: true };
            }
            """);

        JSONObject params = new JSONObject();
        
        assertThrows(RuntimeException.class, () -> {
            bridge.executeJavaScript(mainScript.toString(), params);
        });
    }

    @Test
    void testModuleExportsVariations() throws Exception {
        // Test different export patterns
        
        // Pattern 1: Direct assignment to module.exports
        Path module1 = tempDir.resolve("module1.js");
        Files.writeString(module1, """
            module.exports = function(name) {
                return "Hello " + name;
            };
            """);

        // Pattern 2: Assignment to exports
        Path module2 = tempDir.resolve("module2.js");
        Files.writeString(module2, """
            exports.greet = function(name) {
                return "Hi " + name;
            };
            exports.farewell = function(name) {
                return "Bye " + name;
            };
            """);

        // Pattern 3: Mixed approach
        Path module3 = tempDir.resolve("module3.js");
        Files.writeString(module3, """
            function helper() {
                return "helper";
            }
            
            module.exports = {
                main: function() {
                    return helper() + " main";
                }
            };
            """);

        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const mod1 = require('%s');
            const mod2 = require('%s');
            const mod3 = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    func: mod1("World"),
                    obj1: mod2.greet("Alice"),
                    obj2: mod2.farewell("Bob"),
                    mixed: mod3.main()
                };
            }
            """, "./" + module1.getFileName(), "./" + module2.getFileName(), "./" + module3.getFileName()));

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertEquals("Hello World", jsonResult.getString("func"));
        assertEquals("Hi Alice", jsonResult.getString("obj1"));
        assertEquals("Bye Bob", jsonResult.getString("obj2"));
        assertEquals("helper main", jsonResult.getString("mixed"));
    }

    @Test
    void testModuleWithMCPTools() throws Exception {
        // Create a module that uses MCP tools (mock)
        Path jiraHelperFile = tempDir.resolve("jiraHelper.js");
        Files.writeString(jiraHelperFile, """
            function createMockTicket(project, summary) {
                // In real scenario, this would call jira_create_ticket_basic
                return {
                    key: project + "-123",
                    summary: summary,
                    created: true
                };
            }
            
            module.exports = {
                createTicket: createMockTicket
            };
            """);

        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const jiraHelper = require('%s');
            
            function action(params) {
                const ticket = jiraHelper.createTicket("TEST", "Sample ticket");
                return {
                    success: true,
                    ticket: ticket
                };
            }
            """, "./" + jiraHelperFile.getFileName()));

        JSONObject params = new JSONObject();
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        JSONObject ticket = jsonResult.getJSONObject("ticket");
        assertEquals("TEST-123", ticket.getString("key"));
        assertEquals("Sample ticket", ticket.getString("summary"));
    }

    @Test
    void testCircularDependencyHandling() throws Exception {
        // Create module A that requires B
        Path moduleA = tempDir.resolve("moduleA.js");
        Files.writeString(moduleA, String.format("""
            const b = require('%s');
            
            module.exports = {
                name: "moduleA",
                fromA: function() {
                    return "A works";
                },
                callB: function() {
                    return b.fromB ? b.fromB() : "B not ready";
                }
            };
            """, "./moduleB.js"));

        // Create module B that requires A (circular dependency)
        Path moduleB = tempDir.resolve("moduleB.js");
        Files.writeString(moduleB, String.format("""
            const a = require('%s');
            
            module.exports = {
                name: "moduleB", 
                fromB: function() {
                    return "B works";
                },
                callA: function() {
                    return a.fromA ? a.fromA() : "A not ready";
                }
            };
            """, "./moduleA.js"));

        Path mainScript = tempDir.resolve("main.js");
        Files.writeString(mainScript, String.format("""
            const a = require('%s');
            
            function action(params) {
                return {
                    success: true,
                    aName: a.name,
                    aResult: a.fromA(),
                    bViaA: a.callB()
                };
            }
            """, "./" + moduleA.getFileName()));

        JSONObject params = new JSONObject();
        
        // With proper circular dependency handling, this should work
        Object result = bridge.executeJavaScript(mainScript.toString(), params);
        
        assertNotNull(result);
        JSONObject jsonResult = toJSONObject(result);
        assertTrue(jsonResult.getBoolean("success"));
        assertEquals("moduleA", jsonResult.getString("aName"));
        assertEquals("A works", jsonResult.getString("aResult"));
        // B should be loaded and functional
        assertEquals("B works", jsonResult.getString("bViaA"));
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
}
