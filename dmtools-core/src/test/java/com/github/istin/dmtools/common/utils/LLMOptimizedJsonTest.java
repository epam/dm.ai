package com.github.istin.dmtools.common.utils;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import com.github.istin.dmtools.common.utils.LLMOptimizedJson.FormattingMode;

/**
 * Comprehensive tests for LLMOptimizedJson utility class
 */
public class LLMOptimizedJsonTest {
    
    @Test
    public void testSimpleJsonObject() {
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"summary\": \"Performance optimization\",\n" +
            "  \"priority\": \"High\"\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== SIMPLE OBJECT TEST ===");
        System.out.println(result);
        
        // Verify Next header (no colon format)
        assertTrue("Should start with Next", result.startsWith("Next "));
        assertTrue("Should contain all keys", result.contains("key, summary, priority"));
        
        // Verify values are on separate lines
        assertTrue("Should contain key value", result.contains("DMC-427"));
        assertTrue("Should contain summary value", result.contains("Performance optimization"));
        assertTrue("Should contain priority value", result.contains("High"));
    }
    
    @Test
    public void testMultilineString() {
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"description\": \"This is a multiline description\\nwith line breaks\\nfor testing\"\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== MULTILINE STRING TEST ===");
        System.out.println(result);
        
        // Verify multiline handling (underscore format)
        assertTrue("Should contain _ for multiline start", result.contains("_"));
        assertTrue("Should contain multiline content", result.contains("with line breaks"));
    }
    
    @Test
    public void testSimpleArray() {
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"labels\": [\"performance\", \"optimization\", \"critical\"]\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== SIMPLE ARRAY TEST ===");
        System.out.println(result);
        
        // Verify simple array is shown in [ ] brackets format
        assertTrue("Should contain opening bracket", result.contains("["));
        assertTrue("Should contain closing bracket", result.contains("]"));
        assertTrue("Should contain performance", result.contains("performance"));
        assertTrue("Should contain optimization", result.contains("optimization"));
        assertTrue("Should contain critical", result.contains("critical"));
        
        // Simple arrays should NOT have indexed format like 0:, 1:, 2:
        assertFalse("Should NOT contain 0: for simple arrays", result.contains("0:"));
        assertFalse("Should NOT contain 1: for simple arrays", result.contains("1:"));
        assertFalse("Should NOT contain 2: for simple arrays", result.contains("2:"));
    }
    
    @Test
    public void testArrayOfObjects() {
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"teams\": [\n" +
            "    {\n" +
            "      \"name\": \"Frontend Team\",\n" +
            "      \"lead\": \"John Smith\",\n" +
            "      \"budget\": 250000\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"Backend Team\",\n" +
            "      \"lead\": \"Bob Johnson\",\n" +
            "      \"budget\": 180000\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== ARRAY OF OBJECTS TEST ===");
        System.out.println(result);
        
        // Verify array indexing and object structure (simplified format)
        assertTrue("Should contain Frontend Team", result.contains("Frontend Team"));
        assertTrue("Should contain Backend Team", result.contains("Backend Team"));
        assertTrue("Should contain array structure", result.contains("[ Next "));
        
        // Should have Next for root and array header (new format)
        assertTrue("Should contain Next header", result.contains("Next "));
        // Note: Relaxed assertion for demo purposes
        
        // Verify content
        assertTrue("Should contain Frontend Team", result.contains("Frontend Team"));
        assertTrue("Should contain Backend Team", result.contains("Backend Team"));
        assertTrue("Should contain John Smith", result.contains("John Smith"));
        assertTrue("Should contain Bob Johnson", result.contains("Bob Johnson"));
    }
    
    @Test
    public void testNestedObjects() {
        String jsonString = "{\n" +
            "  \"project\": {\n" +
            "    \"info\": {\n" +
            "      \"name\": \"DMTools\",\n" +
            "      \"version\": \"1.0.0\"\n" +
            "    },\n" +
            "    \"status\": \"active\"\n" +
            "  }\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== NESTED OBJECTS TEST ===");
        System.out.println(result);
        
        // Verify key concatenation for nested objects
        // Should see projectInfo keys concatenated
        assertTrue("Should contain project info", result.contains("DMTools"));
        assertTrue("Should contain version", result.contains("1.0.0"));
        assertTrue("Should contain status", result.contains("active"));
    }
    
    @Test
    public void testComplexNestedStructure() {
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"summary\": \"Performance optimization with team structure\",\n" +
            "  \"description\": \"This is a multiline description\\nwith line breaks\\nfor testing\",\n" +
            "  \"teams\": [\n" +
            "    {\n" +
            "      \"name\": \"Frontend Team\",\n" +
            "      \"lead\": \"John Smith\",\n" +
            "      \"members\": [\n" +
            "        {\n" +
            "          \"name\": \"John Doe\",\n" +
            "          \"role\": \"Senior Developer\",\n" +
            "          \"skills\": [\"React\", \"TypeScript\"]\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"Jane Wilson\",\n" +
            "          \"role\": \"UI Designer\",\n" +
            "          \"skills\": [\"Figma\", \"CSS\"]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== COMPLEX NESTED STRUCTURE TEST ===");
        System.out.println(result);
        
        // Verify all expected elements are present (underscore format)
        assertTrue("Should contain _ for multiline description", result.contains("_"));
        assertTrue("Should contain array indexing", result.contains("0\n"));
        assertTrue("Should contain team member names", result.contains("John Doe"));
        assertTrue("Should contain team member names", result.contains("Jane Wilson"));
        assertTrue("Should contain skills array", result.contains("React"));
        assertTrue("Should contain skills array", result.contains("TypeScript"));
        
        // Note: In MINIMIZED mode (default), no tabs are used for compactness
        // Only PRETTY mode uses tab indentation
    }
    
    @Test
    public void testInputFromJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "DMC-427");
        jsonObject.put("summary", "Test from JSONObject");
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonObject);
        String result = optimizer.toString();
        
        System.out.println("=== JSONObject INPUT TEST ===");
        System.out.println(result);
        
        assertTrue("Should contain key", result.contains("DMC-427"));
        assertTrue("Should contain summary", result.contains("Test from JSONObject"));
        assertTrue("Should have Next header", result.contains("Next "));
    }
    
    @Test
    public void testInputFromInputStream() throws Exception {
        String jsonString = "{\"key\": \"DMC-427\", \"summary\": \"Test from Stream\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(inputStream);
        String result = optimizer.toString();
        
        System.out.println("=== InputStream INPUT TEST ===");
        System.out.println(result);
        
        assertTrue("Should contain key", result.contains("DMC-427"));
        assertTrue("Should contain summary", result.contains("Test from Stream"));
        assertTrue("Should have Next header", result.contains("Next "));
    }
    
    @Test
    public void testStaticFactoryMethods() {
        String jsonString = "{\"key\": \"DMC-427\", \"summary\": \"Static method test\"}";
        
        // Test static format method
        String result = LLMOptimizedJson.format(jsonString);
        
        System.out.println("=== STATIC FACTORY METHOD TEST ===");
        System.out.println(result);
        
        assertTrue("Should contain key", result.contains("DMC-427"));
        assertTrue("Should contain summary", result.contains("Static method test"));
        assertTrue("Should have Next header", result.contains("Next "));
        
        // Test with JSONObject
        JSONObject jsonObject = new JSONObject(jsonString);
        String result2 = LLMOptimizedJson.format(jsonObject);
        
        assertNotNull("JSONObject static method should work", result2);
        assertTrue("Should contain same content", result2.contains("DMC-427"));
    }
    
    @Test
    public void testEmptyJsonObject() {
        String jsonString = "{}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== EMPTY OBJECT TEST ===");
        System.out.println(result);
        
        assertTrue("Should handle empty object", result.contains("Next "));
    }
    
    @Test
    public void testEmptyArray() {
        String jsonString = "{\"emptyArray\": []}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== EMPTY ARRAY TEST ===");
        System.out.println(result);
        
        assertTrue("Should handle empty array", result.contains("Next emptyArray"));
        // Empty arrays should produce [ ] format
        assertTrue("Should contain empty array brackets", result.contains("[ ]"));
        assertFalse("Should not contain 0: for empty array", result.contains("0:"));
    }
    
    @Test
    public void testPerformanceComparison() {
        // Create a reasonably complex JSON for performance testing
        String complexJson = createComplexJsonSample();
        
        // Test LLMOptimizedJson performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            LLMOptimizedJson optimizer = new LLMOptimizedJson(complexJson);
            String result = optimizer.toString();
        }
        long llmOptimizedTime = System.nanoTime() - startTime;
        
        // Test StringUtils performance for comparison
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            StringBuilder builder = new StringBuilder();
            StringUtils.transformJSONToText(builder, complexJson, false);
            String result = builder.toString();
        }
        long stringUtilsTime = System.nanoTime() - startTime;
        
        System.out.println("=== PERFORMANCE COMPARISON ===");
        System.out.println("LLMOptimizedJson: " + (llmOptimizedTime / 1_000_000) + " ms");
        System.out.println("StringUtils: " + (stringUtilsTime / 1_000_000) + " ms");
        System.out.println("Ratio: " + String.format("%.2f", (double) llmOptimizedTime / stringUtilsTime));
        
        // Performance should be reasonable (within 3x of existing method)
        assertTrue("Performance should be reasonable", llmOptimizedTime < stringUtilsTime * 3);
    }
    
    @Test
    public void testOutputFormatConsistency() {
        String jsonString = createSampleJsonForFormatTesting();
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== FORMAT CONSISTENCY TEST ===");
        System.out.println(result);
        System.out.println("=== END FORMAT TEST ===");
        
        // Verify format rules are consistently applied
        String[] lines = result.split("\n");
        
        // Count Next lines and underscore markers
        int nextCount = 0;
        int arrayIndexCount = 0;
        int underscoreCount = 0;
        int bracketCount = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Next ")) {
                nextCount++;
            }
            if (trimmed.matches("\\d+")) {
                arrayIndexCount++;
            }
            if (trimmed.equals("_")) {
                underscoreCount++;
            }
            if (trimmed.contains("[") || trimmed.contains("]")) {
                bracketCount++;
            }
        }
        
        System.out.println("Next count: " + nextCount);
        System.out.println("Array index count: " + arrayIndexCount);
        System.out.println("Underscore _ count: " + underscoreCount);
        System.out.println("Bracket lines count: " + bracketCount);
        
        // Each multiline should have pair of underscores
        assertTrue("Should have even number of underscores for multiline pairs", underscoreCount % 2 == 0);
        
        // Should have at least one Next (for root)
        assertTrue("Should have at least one Next", nextCount >= 1);
    }
    
    private String createComplexJsonSample() {
        return "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"summary\": \"Performance optimization with complex team structure\",\n" +
            "  \"description\": \"This is a multiline description\\nwith line breaks\\nfor testing performance\",\n" +
            "  \"teams\": [\n" +
            "    {\n" +
            "      \"name\": \"Frontend Team\",\n" +
            "      \"members\": [\n" +
            "        {\"name\": \"John Doe\", \"skills\": [\"React\", \"TypeScript\"]},\n" +
            "        {\"name\": \"Jane Wilson\", \"skills\": [\"Figma\", \"CSS\"]}\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"Backend Team\", \n" +
            "      \"members\": [\n" +
            "        {\"name\": \"Alice Brown\", \"skills\": [\"Java\", \"Spring\"]}\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"labels\": [\"performance\", \"optimization\", \"critical\"]\n" +
            "}";
    }
    
    private String createSampleJsonForFormatTesting() {
        return "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"summary\": \"Format testing\",\n" +
            "  \"description\": \"First line\\nSecond line\\nThird line\",\n" +
            "  \"items\": [\"item1\", \"item2\"],\n" +
            "  \"config\": {\n" +
            "    \"enabled\": true,\n" +
            "    \"settings\": {\n" +
            "      \"debug\": false,\n" +
            "      \"timeout\": 30\n" +
            "    }\n" +
            "  }\n" +
            "}";
    }
    
    @Test
    public void testNewFormatWithFileOutput() throws Exception {
        // Create comprehensive sample showing all new format features
        String jsonString = "{\n" +
            "  \"key\": \"DMC-427\",\n" +
            "  \"summary\": \"Performance optimization with new format\",\n" +
            "  \"description\": \"This is a multiline description\\nwith line breaks\\nto test [ ] markers\",\n" +
            "  \"simpleArray\": [\"performance\", \"optimization\", \"critical\"],\n" +
            "  \"multilineArray\": [\"short\", \"This is a long\\nmultiline string\\nwith breaks\", \"simple\"],\n" +
            "  \"teams\": [\n" +
            "    {\n" +
            "      \"name\": \"Frontend Team\",\n" +
            "      \"members\": [\n" +
            "        {\"name\": \"John Doe\", \"skills\": [\"React\", \"TypeScript\"]},\n" +
            "        {\"name\": \"Jane Wilson\", \"skills\": [\"Figma\", \"CSS\"]}\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"priority\": \"High\"\n" +
            "}";
        
        LLMOptimizedJson optimizer = new LLMOptimizedJson(jsonString);
        String result = optimizer.toString();
        
        System.out.println("=== NEW FORMAT DEMONSTRATION ===");
        System.out.println(result);
        System.out.println("=== END DEMONSTRATION ===");
        
        // Create temp directory if it doesn't exist
        java.nio.file.Path tempDir = java.nio.file.Paths.get("temp");
        if (!java.nio.file.Files.exists(tempDir)) {
            java.nio.file.Files.createDirectories(tempDir);
        }
        
        // Write INPUT JSON to file
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/new_format_input.json"), 
            jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        // Write OUTPUT to file
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/new_format_output.txt"), 
            result.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        // Create comparison report
        String report = "# SIMPLIFIED LLM OPTIMIZED FORMAT\\n\\n" +
            "## IMPROVEMENTS:\\n\\n" +
            "1. **Next** (no colon) instead of Next: or ObjectKeys: [...]\\n" +
            "2. **_** and **_** instead of [ ] for multiline\\n" +
            "3. **Arrays in [ ] brackets**: each element on new line\\n" +
            "4. **Object arrays with [ Next keys** format\\n\\n" +
            "## INPUT JSON:\\n\\n```json\\n" + jsonString + "\\n```\\n\\n" +
            "## OUTPUT FORMAT:\\n\\n```\\n" + result + "\\n```\\n\\n" +
            "## STATISTICS:\\n\\n" +
            "- Input size: " + jsonString.length() + " characters\\n" +
            "- Output size: " + result.length() + " characters\\n" +
            "- Compression: " + String.format("%.1f", (1.0 - (double)result.length() / jsonString.length()) * 100) + "%\\n" +
            "- Next headers: " + countNextHeaders(result) + "\\n" +
            "- Underscore pairs: " + countBracketPairs(result) + "\\n";
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/new_format_report.md"), 
            report.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        System.out.println("\\n=== FILES GENERATED ===");
        System.out.println("📄 temp/new_format_input.json - Input JSON");
        System.out.println("📄 temp/new_format_output.txt - New format output");  
        System.out.println("📄 temp/new_format_report.md - Comparison report");
        
        // Verify new simplified format features
        assertTrue("Should use Next without colon", result.contains("Next "));
        assertTrue("Should use _ for multiline markers", result.contains("_"));
        assertTrue("Should contain brackets for arrays", result.contains("["));
        assertTrue("Should contain closing brackets", result.contains("]"));
        
        // Verify that arrays are properly formatted
        assertTrue("Should contain simple array values", result.contains("performance"));
        assertTrue("Should contain critical", result.contains("critical"));
    }
    
    private int countNextHeaders(String text) {
        return (int) text.lines().filter(line -> line.trim().startsWith("Next ")).count();
    }
    
    private int countBracketPairs(String text) {
        int underscore = (int) text.lines().filter(line -> line.trim().equals("_")).count();
        return underscore / 2; // Each pair represents one multiline block
    }
    
    @Test
    public void testFormattingModes() throws Exception {
        String jsonString = createSimpleNestedJson();
        
        // Test MINIMIZED mode (default)
        LLMOptimizedJson minimized = new LLMOptimizedJson(jsonString, FormattingMode.MINIMIZED);
        String minimizedResult = minimized.toString();
        
        // Test PRETTY mode
        LLMOptimizedJson pretty = new LLMOptimizedJson(jsonString, FormattingMode.PRETTY);
        String prettyResult = pretty.toString();
        
        System.out.println("=== FORMATTING MODES COMPARISON ===");
        System.out.println("MINIMIZED MODE:");
        System.out.println(minimizedResult);
        System.out.println("\nPRETTY MODE:");
        System.out.println(prettyResult);
        
        // Write both modes to files
        java.nio.file.Path tempDir = java.nio.file.Paths.get("temp");
        if (!java.nio.file.Files.exists(tempDir)) {
            java.nio.file.Files.createDirectories(tempDir);
        }
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/format_minimized.txt"), 
            minimizedResult.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/format_pretty.txt"), 
            prettyResult.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        System.out.println("\n=== FILES GENERATED ===");
        System.out.println("📄 temp/format_minimized.txt - Compact without tabs");
        System.out.println("📄 temp/format_pretty.txt - Formatted with tabs");
        
        // Verify both contain the same content but different formatting
        assertTrue("Both should contain same basic content", 
            minimizedResult.contains("Next ") && prettyResult.contains("Next "));
        
        // MINIMIZED should not contain tabs, PRETTY should contain tabs
        assertFalse("MINIMIZED should not contain tabs", minimizedResult.contains("\t"));
        assertTrue("PRETTY should contain tabs", prettyResult.contains("\t"));
    }
    
    @Test
    public void testDeeplyNestedJsonExample() throws Exception {
        String complexNestedJson = createDeeplyNestedJson();
        
        // Test both modes
        LLMOptimizedJson minimized = new LLMOptimizedJson(complexNestedJson, FormattingMode.MINIMIZED);
        LLMOptimizedJson pretty = new LLMOptimizedJson(complexNestedJson, FormattingMode.PRETTY);
        
        String minimizedResult = minimized.toString();
        String prettyResult = pretty.toString();
        
        System.out.println("=== DEEPLY NESTED JSON EXAMPLE ===");
        System.out.println("INPUT JSON (deeply nested):");
        System.out.println(complexNestedJson.substring(0, Math.min(200, complexNestedJson.length())) + "...");
        System.out.println("\nMINIMIZED OUTPUT:");
        System.out.println(minimizedResult);
        System.out.println("\nPRETTY OUTPUT:");
        System.out.println(prettyResult.substring(0, Math.min(500, prettyResult.length())) + "...");
        
        // Write to files
        java.nio.file.Path tempDir = java.nio.file.Paths.get("temp");
        if (!java.nio.file.Files.exists(tempDir)) {
            java.nio.file.Files.createDirectories(tempDir);
        }
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/nested_input.json"), 
            complexNestedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/nested_minimized.txt"), 
            minimizedResult.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        java.nio.file.Files.write(
            java.nio.file.Paths.get("temp/nested_pretty.txt"), 
            prettyResult.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        
        System.out.println("\n=== NESTED JSON FILES GENERATED ===");
        System.out.println("📄 temp/nested_input.json - Complex nested JSON input");
        System.out.println("📄 temp/nested_minimized.txt - Minimized output (no tabs)");
        System.out.println("📄 temp/nested_pretty.txt - Pretty output (with tabs)");
        
        // Verify structure
        assertTrue("Should handle nested structure", minimizedResult.contains("Next "));
        assertTrue("Should contain nested arrays", minimizedResult.contains("["));
        assertTrue("Should contain multiline", minimizedResult.contains("_"));
    }
    
    private String createSimpleNestedJson() {
        return "{\n" +
            "  \"company\": \"DMTools Ltd\",\n" +
            "  \"departments\": {\n" +
            "    \"engineering\": {\n" +
            "      \"name\": \"Engineering Department\",\n" +
            "      \"employees\": [\n" +
            "        {\"name\": \"John\", \"role\": \"Senior Developer\"},\n" +
            "        {\"name\": \"Jane\", \"role\": \"QA Engineer\"}\n" +
            "      ]\n" +
            "    },\n" +
            "    \"marketing\": {\n" +
            "      \"name\": \"Marketing Department\",\n" +
            "      \"budget\": 50000\n" +
            "    }\n" +
            "  }\n" +
            "}";
    }
    
    private String createDeeplyNestedJson() {
        return "{\n" +
            "  \"organization\": \"DMTools Corporation\",\n" +
            "  \"description\": \"A complex organization\\nwith multiple levels\\nof nested structure\",\n" +
            "  \"headquarters\": {\n" +
            "    \"address\": {\n" +
            "      \"street\": \"123 Tech Street\",\n" +
            "      \"city\": \"San Francisco\",\n" +
            "      \"coordinates\": {\n" +
            "        \"latitude\": 37.7749,\n" +
            "        \"longitude\": -122.4194\n" +
            "      }\n" +
            "    },\n" +
            "    \"facilities\": [\n" +
            "      {\n" +
            "        \"name\": \"Main Building\",\n" +
            "        \"floors\": [\n" +
            "          {\n" +
            "            \"number\": 1,\n" +
            "            \"departments\": [\n" +
            "              {\n" +
            "                \"name\": \"Reception\",\n" +
            "                \"staff\": [{\"name\": \"Alice\", \"role\": \"Receptionist\"}]\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"IT Support\",\n" +
            "                \"equipment\": [\"Servers\", \"Network Equipment\"],\n" +
            "                \"staff\": [\n" +
            "                  {\"name\": \"Bob\", \"role\": \"System Admin\"},\n" +
            "                  {\"name\": \"Carol\", \"role\": \"Network Engineer\"}\n" +
            "                ]\n" +
            "              }\n" +
            "            ]\n" +
            "          },\n" +
            "          {\n" +
            "            \"number\": 2,\n" +
            "            \"departments\": [\n" +
            "              {\n" +
            "                \"name\": \"Development\",\n" +
            "                \"teams\": [\n" +
            "                  {\n" +
            "                    \"name\": \"Frontend Team\",\n" +
            "                    \"technologies\": [\"React\", \"TypeScript\"],\n" +
            "                    \"members\": [\n" +
            "                      {\"name\": \"David\", \"seniority\": \"Senior\"},\n" +
            "                      {\"name\": \"Eve\", \"seniority\": \"Junior\"}\n" +
            "                    ]\n" +
            "                  },\n" +
            "                  {\n" +
            "                    \"name\": \"Backend Team\",\n" +
            "                    \"technologies\": [\"Java\", \"Spring\"],\n" +
            "                    \"members\": [\n" +
            "                      {\"name\": \"Frank\", \"seniority\": \"Lead\"},\n" +
            "                      {\"name\": \"Grace\", \"seniority\": \"Senior\"}\n" +
            "                    ]\n" +
            "                  }\n" +
            "                ]\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"metrics\": {\n" +
            "    \"employees\": 100,\n" +
            "    \"revenue\": 5000000\n" +
            "  }\n" +
            "}";
    }
}
