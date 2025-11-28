package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.Claude35TokenCounter;
import com.github.istin.dmtools.ai.agent.TestCaseGeneratorAgent;
import com.github.istin.dmtools.atlassian.jira.model.Relationship;
import com.github.istin.dmtools.common.model.ToText;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestCasesGeneratorTest {

    private TestCasesGenerator generator;
    private Method resolveNewMethod;
    private Method resolveExistingMethod;

    @Before
    public void setUp() throws Exception {
        generator = new TestCasesGenerator();
        resolveNewMethod = TestCasesGenerator.class.getDeclaredMethod("resolveRelationshipForNew", TestCasesGeneratorParams.class);
        resolveNewMethod.setAccessible(true);
        resolveExistingMethod = TestCasesGenerator.class.getDeclaredMethod("resolveRelationshipForExisting", TestCasesGeneratorParams.class);
        resolveExistingMethod.setAccessible(true);
    }

    @Test
    public void testToTextImplementation() {
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
                "High",
                "User can login",
                "Test login functionality"
        );

        String text = testCase.toText();

        assertTrue(text.contains("Priority: High"));
        assertTrue(text.contains("Summary: User can login"));
        assertTrue(text.contains("Description: Test login functionality"));
    }

    @Test
    public void testToTextUtilsWithMultipleTestCases() throws Exception {
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();
        testCases.add(new TestCaseGeneratorAgent.TestCase("High", "Test 1", "Desc 1"));
        testCases.add(new TestCaseGeneratorAgent.TestCase("Medium", "Test 2", "Desc 2"));

        String result = ToText.Utils.toText(testCases);

        assertTrue(result.contains("Test 1"));
        assertTrue(result.contains("Test 2"));
        assertTrue(result.contains("-"));
    }

    @Test
    public void testChunkPreparationWithTestCases() throws Exception {
        List<TestCaseGeneratorAgent.TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testCases.add(new TestCaseGeneratorAgent.TestCase(
                    "High",
                    "Test case " + i,
                    "Description for test case " + i
            ));
        }

        ChunkPreparation chunkPreparation = new ChunkPreparation();
        int tokenLimit = 1000;

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(testCases, tokenLimit);

        // Should create at least one chunk
        assertTrue(chunks.size() >= 1);

        // Each chunk should have content
        for (ChunkPreparation.Chunk chunk : chunks) {
            assertNotNull(chunk.getText());
            assertFalse(chunk.getText().isEmpty());
        }
    }

    @Test
    public void testTokenCountingForTestCase() {
        Claude35TokenCounter tokenCounter = new Claude35TokenCounter();
        TestCaseGeneratorAgent.TestCase testCase = new TestCaseGeneratorAgent.TestCase(
                "High",
                "User can login",
                "Test login functionality"
        );

        String text = testCase.toText();
        int tokens = tokenCounter.countTokens(text);

        // Should have a reasonable token count
        assertTrue(tokens > 0);
        assertTrue(tokens < 1000); // Shouldn't be excessive for a simple test case
    }

    @Test
    public void resolveRelationshipForNewPrefersOverride() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationship("legacy");
        params.setTestCaseLinkRelationshipForNew("tests");

        String relationship = (String) resolveNewMethod.invoke(generator, params);

        assertEquals("tests", relationship);
    }

    @Test
    public void resolveRelationshipForExistingFallsBackToLegacy() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationship("legacy");

        String relationship = (String) resolveExistingMethod.invoke(generator, params);

        assertEquals("legacy", relationship);
    }

    @Test
    public void resolveRelationshipForExistingDefaultsWhenBlank() throws Exception {
        TestCasesGeneratorParams params = new TestCasesGeneratorParams();
        params.setTestCaseLinkRelationshipForExisting("   ");
        params.setTestCaseLinkRelationship(null);

        String relationship = (String) resolveExistingMethod.invoke(generator, params);

        assertEquals(Relationship.RELATES_TO, relationship);
    }
}