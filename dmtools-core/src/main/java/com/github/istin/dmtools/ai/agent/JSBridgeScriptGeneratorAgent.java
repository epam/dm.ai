package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.di.DaggerJSBridgeScriptGeneratorAgentComponent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import lombok.Getter;

import java.io.File;
import java.util.List;

public class JSBridgeScriptGeneratorAgent extends AbstractSimpleAgent<JSBridgeScriptGeneratorAgent.Params, String> {

    @Getter
    public static class Params implements GetFiles {
        private String task;
        private String apiDescription;
        private String additionalRequirements;
        private List<File> files;
        private String examples; // Optional additional examples to provide context
        
        // Full constructor with examples
        public Params(String task, String apiDescription, String additionalRequirements, List<File> files, String examples) {
            this.task = task;
            this.apiDescription = apiDescription;
            this.additionalRequirements = additionalRequirements;
            this.files = files;
            this.examples = examples;
        }
        
        // Constructor without examples for backward compatibility
        public Params(String task, String apiDescription, String jsFramework, String outputFormat, String additionalRequirements, List<File> files) {
            this.task = task;
            this.apiDescription = apiDescription;
            this.additionalRequirements = additionalRequirements;
            this.files = files;
            this.examples = null;
        }
    }

    public JSBridgeScriptGeneratorAgent() {
        super("agents/js_bridge_script_generator");
        DaggerJSBridgeScriptGeneratorAgentComponent.create().inject(this);
    }

    @Override
    public String transformAIResponse(Params params, String response) throws Exception {
        return AIResponseParser.parseCodeResponse(response);
    }
} 