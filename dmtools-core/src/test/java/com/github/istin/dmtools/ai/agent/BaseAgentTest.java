package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Base test class for all agents extending AbstractSimpleAgent.
 * This class provides common test infrastructure for:
 * 1. Verifying prompt template rendering with parameters
 * 2. Testing custom response transformation
 * 3. Mocking AI to avoid actual API calls
 * 
 * @param <P> Params type
 * @param <R> Result type
 * @param <A> Agent type
 */
public abstract class BaseAgentTest<P, R, A extends AbstractSimpleAgent<P, R>> {

    @Mock
    protected AI mockAI;

    @Mock
    protected IPromptTemplateReader mockPromptTemplateReader;

    protected A agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent = createAgent();
        injectMocks(agent);
    }

    /**
     * Create the agent instance to test
     */
    protected abstract A createAgent();

    /**
     * Inject mocks into the agent
     */
    protected void injectMocks(A agent) {
        agent.ai = mockAI;
        agent.promptTemplateReader = mockPromptTemplateReader;
    }

    /**
     * Get the expected prompt name for this agent
     */
    protected abstract String getExpectedPromptName();

    /**
     * Create test parameters for the agent
     */
    protected abstract P createTestParams();

    /**
     * Get the mock AI response for testing
     */
    protected abstract String getMockAIResponse();

    /**
     * Verify the result after agent execution
     */
    protected abstract void verifyResult(R result);

    @Test
    void testConstructor() {
        A newAgent = createAgent();
        assertNotNull(newAgent);
        assertEquals(getExpectedPromptName(), newAgent.getPromptName());
    }

    @Test
    void testGetPromptName() {
        assertEquals(getExpectedPromptName(), agent.getPromptName());
    }

    @Test
    void testPromptTemplateRendering() throws Exception {
        // Arrange
        P params = createTestParams();
        String renderedPrompt = "Rendered prompt with params";
        String mockResponse = getMockAIResponse();

        when(mockPromptTemplateReader.read(eq(getExpectedPromptName()), any(PromptContext.class)))
            .thenReturn(renderedPrompt);
        // Mock all possible AI.chat() variants
        when(mockAI.chat(anyString())).thenReturn(mockResponse);
        when(mockAI.chat(any(), anyString())).thenReturn(mockResponse);
        when(mockAI.chat(any(), anyString(), any(java.io.File.class))).thenReturn(mockResponse);
        when(mockAI.chat(any(), anyString(), anyList())).thenReturn(mockResponse);

        // Act
        R result = agent.run(params);

        // Assert - Verify prompt template was called with correct context
        ArgumentCaptor<PromptContext> contextCaptor = ArgumentCaptor.forClass(PromptContext.class);
        verify(mockPromptTemplateReader).read(eq(getExpectedPromptName()), contextCaptor.capture());
        
        PromptContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext, "Prompt context should not be null");

        // Verify AI was called
        verify(mockAI, atLeastOnce()).chat(anyString());

        // Verify result
        assertNotNull(result, "Result should not be null");
        verifyResult(result);
    }

    @Test
    void testGetParamsClass() {
        Class<P> paramsClass = agent.getParamsClass();
        assertNotNull(paramsClass);
    }

    /**
     * Optional: Test transform AI response if agent overrides it
     */
    protected void testTransformAIResponse(P params, String response, R expectedResult) throws Exception {
        R result = agent.transformAIResponse(params, response);
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }
}
