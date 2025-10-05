package com.github.istin.dmtools.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Claude35TokenCounterTest {

    private Claude35TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new Claude35TokenCounter();
    }

    @Test
    void testCountTokens_Null() {
        int result = tokenCounter.countTokens(null);
        assertEquals(0, result);
    }

    @Test
    void testCountTokens_Empty() {
        int result = tokenCounter.countTokens("");
        assertEquals(0, result);
    }

    @Test
    void testCountTokens_SingleCharacter() {
        int result = tokenCounter.countTokens("a");
        assertEquals(8, result); // Base token count for single character
    }

    @Test
    void testCountTokens_SimpleWord() {
        int result = tokenCounter.countTokens("hello");
        assertTrue(result > 8); // Should be more than base tokens
    }

    @Test
    void testCountTokens_WordWithSpaces() {
        int result = tokenCounter.countTokens("hello world");
        assertTrue(result > 8);
        // Should count spaces as tokens
    }

    @Test
    void testCountTokens_UpperCase() {
        int result = tokenCounter.countTokens("Hello");
        int lowerCaseResult = tokenCounter.countTokens("hello");
        // Uppercase letters should add extra tokens
        assertTrue(result > lowerCaseResult);
    }

    @Test
    void testCountTokens_WithSpecialCharacters() {
        int result = tokenCounter.countTokens("hello-world");
        assertTrue(result > 8);
        // Should count the dash as a token
    }

    @Test
    void testCountTokens_WithSlash() {
        int result = tokenCounter.countTokens("path/to/file");
        assertTrue(result > 8);
        // Should count slashes as tokens
    }

    @Test
    void testCountTokens_WithUnderscore() {
        int result = tokenCounter.countTokens("snake_case");
        assertTrue(result > 8);
    }

    @Test
    void testCountTokens_WithDot() {
        int result = tokenCounter.countTokens("file.txt");
        assertTrue(result > 8);
    }

    @Test
    void testCountTokens_JsonStructure() {
        String json = "{\"key\":\"value\"}";
        int result = tokenCounter.countTokens(json);
        assertTrue(result > 8);
        // Should count JSON syntax characters
    }

    @Test
    void testCountTokens_ComplexJson() {
        String json = "{\"name\":\"John\",\"age\":30}";
        int result = tokenCounter.countTokens(json);
        assertTrue(result > 8); // Complex JSON should have more than base tokens
    }

    @Test
    void testCountTokens_WithBackslash() {
        int result = tokenCounter.countTokens("\\n\\t");
        assertTrue(result > 8);
    }

    @Test
    void testCountTokens_CamelCase() {
        int camelCaseResult = tokenCounter.countTokens("camelCaseWord");
        int lowerCaseResult = tokenCounter.countTokens("camelcaseword");
        // CamelCase should have more tokens due to uppercase letters
        assertTrue(camelCaseResult > lowerCaseResult);
    }

    @Test
    void testCountTokens_MultipleSpaces() {
        int result = tokenCounter.countTokens("hello   world");
        assertTrue(result > 8);
        // Multiple spaces should each count as tokens
    }

    @Test
    void testCountTokens_LongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("word ");
        }
        int result = tokenCounter.countTokens(sb.toString());
        assertTrue(result > 100); // Should have many tokens
    }

    @Test
    void testCountTokens_NumbersAndLetters() {
        int result = tokenCounter.countTokens("test123");
        assertTrue(result > 8);
    }

    @Test
    void testCountTokens_AllUpperCase() {
        int result = tokenCounter.countTokens("HELLO");
        assertTrue(result > 8);
        // All uppercase should add extra tokens
    }
}
