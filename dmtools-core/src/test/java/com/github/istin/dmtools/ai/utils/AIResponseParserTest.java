package com.github.istin.dmtools.ai.utils;

import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class AIResponseParserTest extends TestCase {

    public void testParseBooleanResponse() {
        try {
            assertTrue(AIResponseParser.parseBooleanResponse("... true ..."));
            assertFalse(AIResponseParser.parseBooleanResponse("... false ..."));

            try {
                AIResponseParser.parseBooleanResponse("... invalid ...");
                fail("Expected IllegalArgumentException for invalid boolean");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    public void testParseCodeResponseVariations() {
        // Test without language identifier
        String response1 = "```\nsome code\n```";
        assertEquals("some code", AIResponseParser.parseCodeResponse(response1));

        // Test single line
        String response2 = "```javascript\nsome code```";
        assertEquals("some code", AIResponseParser.parseCodeResponse(response2));

        // Test without code markers
        String response3 = "some code";
        assertEquals("some code", AIResponseParser.parseCodeResponse(response3));

        // Test empty response
        try {
            AIResponseParser.parseCodeResponse("");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test null response
        try {
            AIResponseParser.parseCodeResponse(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testParseResponseAsJSONArray() {
        try {
            String response = "... [\"item1\", \"item2\"] ...";
            JSONArray jsonArray = AIResponseParser.parseResponseAsJSONArray(response);
            assertEquals(2, jsonArray.length());
            assertEquals("item1", jsonArray.getString(0));
            assertEquals("item2", jsonArray.getString(1));

            try {
                AIResponseParser.parseResponseAsJSONArray("invalid response");
                fail("Expected JSONException for invalid JSON array");
            } catch (JSONException e) {
                // Expected exception
            }
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    public void testParseResponseAsJSONObject() {
        try {
            String response = "... {\"key\": \"value\"} ...";
            JSONObject jsonObject = AIResponseParser.parseResponseAsJSONObject(response);
            assertEquals("value", jsonObject.getString("key"));

            try {
                AIResponseParser.parseResponseAsJSONObject("invalid response");
                fail("Expected JSONException for invalid JSON object");
            } catch (JSONException e) {
                // Expected exception
            }
        } catch (Exception e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    public void testParseCodeExamples() {
        try {
            String response = "Example 1:\n```\nCode block 1\n```\nExample 2:\n```\nCode block 2\n```";
            List<String> codeExamples = AIResponseParser.parseCodeExamples(response, "```", "```");
            assertEquals(2, codeExamples.size());
            assertEquals("Code block 1", codeExamples.get(0));
            assertEquals("Code block 2", codeExamples.get(1));

            // Test with no delimiters
            try {
                AIResponseParser.parseCodeExamples("No code blocks here", "```", "```");
                fail("Expected IllegalArgumentException for missing code blocks");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

            // Test with empty response
            try {
                AIResponseParser.parseCodeExamples("", "```", "```");
                fail("Expected IllegalArgumentException for empty response");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

        } catch (Exception e) {
            fail("Exception should not be thrown during valid code extraction: " + e.getMessage());
        }
    }

    public void testParseCodeExamplesInSpecificLanguage() {
        try {
            String response = "```java\njava code example 3\n```";
            List<String> codeExamples = AIResponseParser.parseCodeExamples(response, "```", "```");
            assertEquals(1, codeExamples.size());
            assertEquals("java code example 3", codeExamples.get(0));

            // Edge case with multiple single line examples
            String multiLineResponse = "```java\nSystem.out.println(\"Hello\");\n```\n```java\nint x = 5;\n```";
            codeExamples = AIResponseParser.parseCodeExamples(multiLineResponse, "```", "```");
            assertEquals(2, codeExamples.size());
            assertEquals("System.out.println(\"Hello\");", codeExamples.get(0));
            assertEquals("int x = 5;", codeExamples.get(1));

            // Test with only delimiters
            try {
                AIResponseParser.parseCodeExamples("``````", "```", "```");
                fail("Expected IllegalArgumentException for empty code blocks with delimiters only");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

        } catch (Exception e) {
            fail("Exception should not be thrown during valid code extraction: " + e.getMessage());
        }
    }

    public void testParseCodeExamplesWithDefaultDelimiters() {
        try {
            // Test with single code block
            String singleBlockResponse = "Some text @jai_generated_code\nSystem.out.println(\"Hello, World!\");\n@jai_generated_code More text";
            List<String> codeExamples = AIResponseParser.parseCodeExamples(singleBlockResponse);
            assertEquals(1, codeExamples.size());
            assertEquals("System.out.println(\"Hello, World!\");", codeExamples.get(0));

            // Test with multiple code blocks
            String multipleBlocksResponse = "@jai_generated_code\nint x = 5;\n@jai_generated_code\nSome text in between\n@jai_generated_code\nString s = \"test\";\n@jai_generated_code";
            codeExamples = AIResponseParser.parseCodeExamples(multipleBlocksResponse);
            assertEquals(2, codeExamples.size());
            assertEquals("int x = 5;", codeExamples.get(0));
            assertEquals("String s = \"test\";", codeExamples.get(1));

            // Test with no code blocks
            try {
                AIResponseParser.parseCodeExamples("No code blocks here");
                fail("Expected IllegalArgumentException for missing code blocks");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

            // Test with empty response
            try {
                AIResponseParser.parseCodeExamples("");
                fail("Expected IllegalArgumentException for empty response");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

            // Test with only delimiters
            try {
                AIResponseParser.parseCodeExamples("@jai_generated_code@jai_generated_code");
                fail("Expected IllegalArgumentException for empty code blocks with delimiters only");
            } catch (IllegalArgumentException e) {
                // Expected exception
            }

            // Test with code block containing newlines
            String multilineCodeBlock = "@jai_generated_code\nif (condition) {\n    doSomething();\n} else {\n    doSomethingElse();\n}\n@jai_generated_code";
            codeExamples = AIResponseParser.parseCodeExamples(multilineCodeBlock);
            assertEquals(1, codeExamples.size());
            assertEquals("if (condition) {\n    doSomething();\n} else {\n    doSomethingElse();\n}", codeExamples.get(0));

        } catch (Exception e) {
            fail("Exception should not be thrown during valid code extraction: " + e.getMessage());
        }
    }

    public void testParseCodeExamplesWithMarkdowns() {
        try {
            // Test with markdown in the code block
            String markdownResponse = "@jai_generated_code\n```java\npublic class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}\n```\n@jai_generated_code";
            List<String> codeExamples = AIResponseParser.parseCodeExamples(markdownResponse);
            assertEquals(1, codeExamples.size());
            assertEquals("public class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}", codeExamples.get(0));

            // Test with multiple code blocks and different language markdowns
            String multipleMarkdownResponse = "@jai_generated_code\n```python\nprint('Hello, World!')\n```\n@jai_generated_code\nSome text in between\n@jai_generated_code\n```javascript\nconsole.log('Hello, World!');\n```\n@jai_generated_code";
            codeExamples = AIResponseParser.parseCodeExamples(multipleMarkdownResponse);
            assertEquals(2, codeExamples.size());
            assertEquals("print('Hello, World!')", codeExamples.get(0));
            assertEquals("console.log('Hello, World!');", codeExamples.get(1));

            // Test with no markdown in the code block
            String noMarkdownResponse = "@jai_generated_code\nSystem.out.println(\"Hello, World!\");\n@jai_generated_code";
            codeExamples = AIResponseParser.parseCodeExamples(noMarkdownResponse);
            assertEquals(1, codeExamples.size());
            assertEquals("System.out.println(\"Hello, World!\");", codeExamples.get(0));

        } catch (Exception e) {
            fail("Exception should not be thrown during valid code extraction: " + e.getMessage());
        }
    }

}