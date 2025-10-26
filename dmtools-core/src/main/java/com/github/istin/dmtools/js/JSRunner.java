package com.github.istin.dmtools.js;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.di.DaggerJSRunnerComponent;
import com.github.istin.dmtools.di.ServerManagedIntegrationsModule;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.ExecutionMode;
import com.github.istin.dmtools.job.Params;
import com.google.gson.annotations.SerializedName;
import dagger.Component;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * JavaScript Runner Job - для выполнения JS скриптов
 * 
 * Этот Job позволяет выполнять JavaScript скрипты изолированно от основной логики.
 * На вход принимает:
 * - jsPath - путь к JS файлу (может быть файл, ресурс или GitHub URL)
 * - jobParams - параметры работы (JSONObject)
 * - ticket - тикет (JSONObject) 
 * - response - ответ AI (String)
 * - initiator - инициатор (String)
 * 
 * Использование:
 * java -cp dmtools.jar com.github.istin.dmtools.job.JobRunner \
 *   --job-class com.github.istin.dmtools.js.JSRunner \
 *   --params '{"jsPath": "agents/test.js", "jobParams": {...}, "ticket": {...}, "response": "...", "initiator": "user@example.com"}'
 */
public class JSRunner extends AbstractJob<JSRunner.JSParams, Object> {

    private static final Logger logger = LogManager.getLogger(JSRunner.class);

    @Getter
    @Setter
    public static class JSParams extends Params {
        
        /**
         * Path to JavaScript file for execution
         * Can be:
         * - File path: "agents/test.js"
         * - Resource: "classpath:js/test.js"
         * - GitHub URL: "https://github.com/user/repo/blob/main/test.js"
         * - Inline code: "function action(params) { return {...}; }"
         */
        @SerializedName("jsPath")
        private String jsPath;
        
        /**
         * Job parameters (will be passed as params.jobParams in JS)
         */
        @SerializedName("jobParams")
        private Object jobParams;
        
        /**
         * Ticket (will be passed as params.ticket in JS)
         */
        @SerializedName("ticket")
        private Object ticket;
        
        /**
         * AI response (will be passed as params.response in JS)
         */
        @SerializedName("response")
        private Object response;
        
        // Note: initiator is inherited from TrackerParams
    }

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Inject
    Confluence confluence;

    @Inject
    @Getter
    AI ai;

    @Inject
    List<SourceCode> sourceCodes;
    
    @Inject
    @org.jetbrains.annotations.Nullable
    com.github.istin.dmtools.common.kb.tool.KBTools kbTools;

    private static com.github.istin.dmtools.di.JSRunnerComponent jsRunnerComponent;

    // Commenting out ServerManagedJSRunnerComponent until KB module dependency is resolved
    // This component is not currently used (initializeServerManaged throws UnsupportedOperationException)
    /*
    @Singleton
    @Component(modules = {ServerManagedIntegrationsModule.class})
    public interface ServerManagedJSRunnerComponent {
        void inject(JSRunner jsRunner);
    }
    */

    /**
     * Creates a new JSRunner instance with the default configuration
     */
    public JSRunner() {
        this(null);
    }

    /**
     * Creates a new JSRunner instance with the provided configuration
     */
    public JSRunner(ApplicationConfiguration configuration) {
        // Will be initialized in initializeForMode based on execution mode
    }

    @Override
    protected void initializeStandalone() {
        logger.info("Initializing JSRunner in STANDALONE mode");
        
        if (jsRunnerComponent == null) {
            logger.info("Creating new DaggerJSRunnerComponent for standalone mode");
            jsRunnerComponent = DaggerJSRunnerComponent.create();
        }
        
        logger.info("Injecting dependencies using JSRunnerComponent");
        jsRunnerComponent.inject(this);
        
        logger.info("JSRunner standalone initialization completed - AI type: {}", 
                   (ai != null ? ai.getClass().getSimpleName() : "null"));
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        throw new UnsupportedOperationException("Server-managed execution not yet implemented for JSRunner");
    }

    @Override
    protected Object runJobImpl(JSParams params) throws Exception {
        String jsPath = params.getJsPath();
        if (jsPath == null || jsPath.trim().isEmpty()) {
            throw new IllegalArgumentException("jsPath parameter is required");
        }

        logger.info("Executing JavaScript test with path: {}", jsPath);
        
        // Prepare JavaScript execution
        SourceCode primarySourceCode = sourceCodes != null && !sourceCodes.isEmpty() ? sourceCodes.get(0) : null;
        
        Object result = js(jsPath)
                .mcpWithKB(trackerClient, ai, confluence, primarySourceCode, kbTools)
                .withJobContext(params.getJobParams(), params.getTicket(), params.getResponse())
                .with("initiator", params.getInitiator())
                .execute();

        logger.info("JavaScript execution completed successfully");
        logger.debug("JavaScript result: {}", result);
        
        return result;
    }

    /**
     * Standalone entry point for testing
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: JSRunner <json-params>");
            System.err.println("Example: JSRunner '{\"jsPath\": \"test.js\", \"ticket\": {\"key\": \"TEST-123\"}, \"response\": \"AI response\"}'");
            System.exit(1);
        }

        try {
            // Parse JSON parameters
            JSONObject jsonParams = new JSONObject(args[0]);
            
            // Create and configure JSRunner
            JSRunner runner = new JSRunner();
            runner.initializeForMode(ExecutionMode.STANDALONE, null);
            
            // Create parameters object
            JSParams params = new JSParams();
            params.setJsPath(jsonParams.optString("jsPath"));
            params.setJobParams(jsonParams.opt("jobParams"));
            params.setTicket(jsonParams.opt("ticket"));
            params.setResponse(jsonParams.opt("response"));
            params.setInitiator(jsonParams.optString("initiator"));
            
            // Execute
            Object result = runner.runJobImpl(params);
            
            // Print result
            System.out.println("=== JavaScript Execution Result ===");
            System.out.println(result);
            
        } catch (Exception e) {
            System.err.println("Error executing JavaScript: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
