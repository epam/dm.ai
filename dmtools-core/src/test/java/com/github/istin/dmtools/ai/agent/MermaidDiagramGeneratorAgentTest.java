package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MermaidDiagramGeneratorAgent
 */
class MermaidDiagramGeneratorAgentTest {

    @Mock
    private AI mockAI;

    @Mock
    private IPromptTemplateReader mockPromptTemplateReader;

    private MermaidDiagramGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent = new MermaidDiagramGeneratorAgent();
        agent.ai = mockAI;
        agent.promptTemplateReader = mockPromptTemplateReader;
    }

    @Test
    void testGenerateDiagram() throws Exception {
        // Given
        String content = "The user login process: 1. Enter credentials, 2. Validate, 3. Redirect";
        String expectedDiagram = "flowchart TD\nA[Enter credentials] --> B[Validate]\nB --> C[Redirect]";
        String prompt = "Generate mermaid diagram for: " + content;

        when(mockPromptTemplateReader.read(eq("agents/mermaid_diagram_generator"), any()))
                .thenReturn(prompt);
        when(mockAI.chat(eq(prompt))).thenReturn(expectedDiagram);

        MermaidDiagramGeneratorAgent.Params params = new MermaidDiagramGeneratorAgent.Params(content);

        // When
        String result = agent.run(params);

        // Then
        assertNotNull(result);
        assertEquals(expectedDiagram, result);
        verify(mockPromptTemplateReader).read(eq("agents/mermaid_diagram_generator"), any());
        verify(mockAI).chat(eq(prompt));
    }

    @Test
    void testGetParamsClass() {
        // When
        Class<?> paramsClass = agent.getParamsClass();

        // Then
        assertEquals(MermaidDiagramGeneratorAgent.Params.class, paramsClass);
    }
}
