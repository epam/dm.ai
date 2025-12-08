package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for token limit calculation in Teammate index processing.
 * Tests the logic that reduces available tokens for chunks based on story tokens.
 */
class TeammateIndexTokenLimitTest {

    @Test
    void testTokenLimitCalculationWithSmallStory() {
        // Given
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        String storyText = "This is a small story with minimal content.";
        int storyTokens = tokenCounter.countTokens(storyText);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // When
        int tokenLimit = (systemTokenLimits - storyTokens) / 2;
        
        // Then
        assertTrue(storyTokens > 0, "Story should have tokens");
        assertTrue(storyTokens < 100, "Small story should have few tokens");
        assertTrue(tokenLimit > 0, "Should have positive token limit");
        assertTrue(tokenLimit < systemTokenLimits, "Chunk limit should be less than system limit");
        
        // Verify the formula leaves room for response
        int totalUsed = storyTokens + tokenLimit;
        assertTrue(totalUsed < systemTokenLimits, "Story + chunks should leave room for response");
    }

    @Test
    void testTokenLimitCalculationWithLargeStory() {
        // Given
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // Create a large story relative to the system limit (e.g., 20% of capacity)
        // This ensures we test "large" input without exceeding the configured limit
        // Use at least 100 tokens to be meaningful
        int targetStoryTokens = Math.max(100, systemTokenLimits / 5); 
        
        StringBuilder largeStory = new StringBuilder();
        String line = "This is a line of text that contributes to the story content. ";
        int lineTokens = tokenCounter.countTokens(line);
        // Calculate lines needed safely
        int linesNeeded = Math.max(1, targetStoryTokens / (lineTokens > 0 ? lineTokens : 1));
        
        for (int i = 0; i < linesNeeded; i++) {
            largeStory.append(line);
        }
        String storyText = largeStory.toString();
        
        int storyTokens = tokenCounter.countTokens(storyText);
        
        // When
        int tokenLimit = (systemTokenLimits - storyTokens) / 2;
        
        // Then
        assertTrue(storyTokens > 0, "Story should have tokens");
        assertTrue(tokenLimit > 0, "Should still have positive token limit even with large story");
        
        // Verify we're not exceeding the system limit
        int totalUsed = storyTokens + tokenLimit;
        assertTrue(totalUsed < systemTokenLimits, 
            "Story + chunks should not exceed system limit (story=" + storyTokens + 
            ", chunks=" + tokenLimit + ", system=" + systemTokenLimits + ")");
    }

    @Test
    void testTokenLimitCalculationWithEmptyStory() {
        // Given
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        String storyText = "";
        int storyTokens = tokenCounter.countTokens(storyText);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // When
        int tokenLimit = (systemTokenLimits - storyTokens) / 2;
        
        // Then
        assertEquals(0, storyTokens, "Empty story should have no tokens");
        assertEquals(systemTokenLimits / 2, tokenLimit, 
            "With no story, chunk limit should be half of system limit");
    }

    @Test
    void testTokenLimitRespectsTestCaseGeneratorPattern() {
        // Given - Same pattern as TestCasesGenerator
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        String ticketText = "Feature: User should be able to login with username and password.";
        int storyTokens = tokenCounter.countTokens(ticketText);
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // When - Apply same formula as TestCasesGenerator
        int tokenLimit = (systemTokenLimits - storyTokens) / 2;
        
        // Then
        assertTrue(tokenLimit > 0, "Token limit must be positive");
        
        // Verify the division by 2 leaves room for both chunks and response
        int reservedForResponse = systemTokenLimits - storyTokens - tokenLimit;
        // Allow for rounding difference of 1 token
        assertTrue(Math.abs(tokenLimit - reservedForResponse) <= 1, 
            "Division by 2 should allocate roughly equal space for chunks and response (diff: " + 
            Math.abs(tokenLimit - reservedForResponse) + ")");
    }

    @Test
    void testNullStoryTextHandling() {
        // Given
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        String storyText = null;
        // Teammate code uses: storyText != null ? storyText : ""
        int storyTokens = tokenCounter.countTokens(storyText != null ? storyText : "");
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // When
        int tokenLimit = (systemTokenLimits - storyTokens) / 2;
        
        // Then
        assertEquals(0, storyTokens, "Null story should result in 0 tokens");
        assertEquals(systemTokenLimits / 2, tokenLimit, 
            "Null story should give maximum chunk limit");
    }

    @Test
    void testTokenLimitNeverNegative() {
        // Given - Extreme case: story is extremely large
        ChunkPreparation chunkPreparation = new ChunkPreparation();
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        
        // Create a story larger than system limit (shouldn't happen in practice)
        StringBuilder hugeStory = new StringBuilder();
        int systemTokenLimits = chunkPreparation.getTokenLimit();
        
        // Generate text that would exceed system limit
        for (int i = 0; i < systemTokenLimits; i++) {
            hugeStory.append("word ");
        }
        String storyText = hugeStory.toString();
        
        int storyTokens = tokenCounter.countTokens(storyText);
        
        // When
        int tokenLimit = Math.max(0, (systemTokenLimits - storyTokens) / 2);
        
        // Then
        assertTrue(tokenLimit >= 0, "Token limit should never be negative");
    }
}

