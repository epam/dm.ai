package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerMermaidDiagramGeneratorAgentComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent for generating Mermaid diagrams from content text.
 * Follows the AbstractSimpleAgent pattern similar to TestCaseVisualizerAgent.
 */
public class MermaidDiagramGeneratorAgent extends AbstractSimpleAgent<MermaidDiagramGeneratorAgent.Params, String> {

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String content;
    }

    public MermaidDiagramGeneratorAgent() {
        super("agents/mermaid_diagram_generator");
        DaggerMermaidDiagramGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        return response;
    }
}
