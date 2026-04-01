// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import com.github.istin.dmtools.bridge.DMToolsBridge;
import com.google.gson.Gson;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONObject;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class JSPresentationMakerBridge implements PresentationMaker {

    private static final Logger logger = LogManager.getLogger(JSPresentationMakerBridge.class);
    private final ScriptEngine scriptEngine;
    private final String scriptName;
    private final Gson gson = new Gson();
    private final DMToolsBridge dmToolsBridge;

    public JSPresentationMakerBridge(JSONObject configJson) throws IOException, ScriptException, TemplateException {
        this.scriptName = configJson.optString("clientName", "JSPresentationMakerBridge");
        
        // Create a bridge with presentation-specific permissions
        this.dmToolsBridge = DMToolsBridge.withPermissions(
            this.scriptName,
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.LOGGING_WARN,
            DMToolsBridge.Permission.LOGGING_ERROR,
            DMToolsBridge.Permission.PRESENTATION_HTML_GENERATION,
            DMToolsBridge.Permission.PRESENTATION_ORCHESTRATOR,
            DMToolsBridge.Permission.PRESENTATION_REQUEST_DATA,
            DMToolsBridge.Permission.REPORT_CUSTOM_PROJECT,
            DMToolsBridge.Permission.REPORT_PROJECT_TIMELINE,
            DMToolsBridge.Permission.REPORT_PROJECT_BUG,
            DMToolsBridge.Permission.REPORT_BUGS_WITH_TYPES,
            DMToolsBridge.Permission.TRACKER_CLIENT_ACCESS
        );

        String jsTemplate = loadJsScript(configJson);
        // Pass the whole configJson to Freemarker if JS needs general config values from it.
        // Or, selectively pass parts of configJson if preferred.
        String processedJsCode = processJsTemplateWithFreemarker(jsTemplate, configJson.optJSONObject("templateData"), this.scriptName);

        this.scriptEngine = createGraalJSEngine();
        logger.info("Evaluating processed JavaScript for: {}", this.scriptName);
        this.scriptEngine.eval(processedJsCode);
    }

    /**
     * Creates a GraalJS script engine with proper configuration and fallback support.
     * 
     * @return a configured ScriptEngine instance
     * @throws ScriptException if no suitable JavaScript engine can be found
     */
    private ScriptEngine createGraalJSEngine() throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        
        // First, try to get the GraalJS engine
        ScriptEngine engine = manager.getEngineByName("graal.js");
        if (engine != null) {
            logger.info("Successfully initialized GraalJS engine for: {}", this.scriptName);
            return engine;
        }
        
        // Fallback: try alternative GraalJS engine names
        String[] alternativeNames = {"Graal.js", "js", "JavaScript"};
        for (String name : alternativeNames) {
            engine = manager.getEngineByName(name);
            if (engine != null) {
                logger.warn("GraalJS engine not found by name 'graal.js', using fallback engine: {} for: {}", 
                           name, this.scriptName);
                return engine;
            }
        }
        
        // List available engines for debugging
        StringBuilder availableEngines = new StringBuilder("Available script engines: ");
        for (ScriptEngineFactory factory : manager.getEngineFactories()) {
            availableEngines.append(factory.getEngineName())
                           .append(" (")
                           .append(String.join(", ", factory.getNames()))
                           .append("), ");
        }
        logger.error("No JavaScript engine found. {}", availableEngines.toString());
        
        throw new ScriptException("Graal.js script engine not found. Ensure GraalVM's JS dependencies are correctly configured. " + 
                                  availableEngines.toString());
    }

    private static String loadJsScript(JSONObject configJson) throws IOException {
        if (configJson.has("jsScript") && !configJson.isNull("jsScript")) {
            return configJson.getString("jsScript");
        }
        if (configJson.has("jsScriptPath") && !configJson.isNull("jsScriptPath")) {
            String path = configJson.getString("jsScriptPath");
            String classpathPath = path.startsWith("/") ? path.substring(1) : path;
            try (java.io.InputStream inputStream = JSPresentationMakerBridge.class.getClassLoader().getResourceAsStream(classpathPath)) {
                if (inputStream != null) {
                    return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                }
                logger.warn("Could not load jsScriptPath {} from classpath, trying filesystem.", path);
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.warn("Error loading jsScriptPath {} from classpath/filesystem.", path, e);
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("JSPresentationMakerBridge config JSON must contain either 'jsScript' (string) or 'jsScriptPath' (path to file).");
    }

    private String processJsTemplateWithFreemarker(String jsTemplate, JSONObject templateData, String templateName) throws IOException, TemplateException {
        if (jsTemplate == null || jsTemplate.isEmpty()) {
            return "";
        }

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        // If templateData is not provided, it's highly likely that jsTemplate is raw JavaScript
        // and not an FTL template. In this case, change FreeMarker's interpolation syntax
        // to square brackets to avoid clashes with JavaScript's ${...} template literals.
        if (templateData == null || templateData.keySet().isEmpty()) {
            cfg.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        }

        Map<String, Object> dataModel = new HashMap<>();
        if (templateData != null) {
            for (String key : templateData.keySet()) {
                dataModel.put(key, templateData.get(key));
            }
        } else {
            // Provide an empty map if no templateData is given, so templates can safely access it.
            dataModel.put("templateData", Collections.emptyMap());
        }

        Template template = new Template(templateName, jsTemplate, cfg);
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }

    @Override
    public String getName() {
        return "JSPresentationMakerBridge:" + scriptName;
    }

    @Override
    public JSONObject createPresentation(String paramsForJs) throws Exception {
        if (!(this.scriptEngine instanceof Invocable)) {
            throw new ScriptException("JavaScript engine does not support function invocation (Invocable interface not available).");
        }
        Invocable invocable = (Invocable) this.scriptEngine;
        logger.info("Invoking JS function 'generatePresentationJs' for {}", scriptName);
        Object result = invocable.invokeFunction("generatePresentationJs", paramsForJs, dmToolsBridge);

        if (result == null) {
            logger.warn("JavaScript generatePresentationJs function in {} returned null.", scriptName);
            return new JSONObject();
        }
        return new JSONObject(gson.toJson(result));
    }

    @Override
    public JSONObject createPresentation(PresentationMakerOrchestrator.Params orchestratorParams) throws Exception {
        String paramsJson = gson.toJson(orchestratorParams);
        return createPresentation(paramsJson);
    }

    /**
     * Provides access to the DMToolsBridge for JavaScript code
     */
    @HostAccess.Export
    public DMToolsBridge getBridge() {
        return dmToolsBridge;
    }

    /**
     * Creates a presentation and automatically generates an HTML file, returning the File object.
     * This is a convenience method that combines JSON generation and HTML file creation.
     */
    public File createPresentationFile(String paramsForJs) throws Exception {
        return createPresentationFile(paramsForJs, null);
    }

    /**
     * Creates a presentation and automatically generates an HTML file with a custom topic, returning the File object.
     * This is a convenience method that combines JSON generation and HTML file creation.
     */
    public File createPresentationFile(String paramsForJs, String customTopic) throws Exception {
        if (!(this.scriptEngine instanceof Invocable)) {
            throw new ScriptException("JavaScript engine does not support function invocation (Invocable interface not available).");
        }
        Invocable invocable = (Invocable) this.scriptEngine;
        logger.info("Invoking JS function 'generatePresentationJs' for {} (File mode)", scriptName);
        Object result = invocable.invokeFunction("generatePresentationJs", paramsForJs, dmToolsBridge);

        if (result == null) {
            logger.warn("JavaScript generatePresentationJs function in {} returned null.", scriptName);
            throw new Exception("JavaScript function returned null - cannot generate presentation");
        }

        String presentationJsonString = gson.toJson(result);
        JSONObject presentationJo = new JSONObject(presentationJsonString);

        // Check for "generatedSlides" and rename to "slides" if "slides" isn't already present
        if (presentationJo.has("generatedSlides") && !presentationJo.has("slides")) {
            org.json.JSONArray slidesArray = presentationJo.getJSONArray("generatedSlides");
            presentationJo.remove("generatedSlides");
            presentationJo.put("slides", slidesArray);
            dmToolsBridge.jsLogInfo("Adjusted presentation JSON: moved 'generatedSlides' to 'slides'");
        }

        // Determine topic for file generation
        String topic;
        if (customTopic != null && !customTopic.trim().isEmpty()) {
            topic = customTopic;
        } else {
            // Try to extract topic from input params or presentation data
            try {
                JSONObject params = new JSONObject(paramsForJs);
                topic = params.optString("topic", "Generated_Presentation");
            } catch (Exception e) {
                // If params parsing fails, try to get title from presentation
                topic = presentationJo.optString("title", "Generated_Presentation");
            }
        }

        // Generate HTML file
        HTMLPresentationDrawer drawer = new HTMLPresentationDrawer();
        File presentationFile = drawer.printPresentation(topic, presentationJo);
        dmToolsBridge.jsLogInfo("HTML presentation file generated: " + presentationFile.getAbsolutePath());
        
        return presentationFile;
    }

    /**
     * Creates a presentation and automatically generates an HTML file for orchestrator params, returning the File object.
     */
    public File createPresentationFile(PresentationMakerOrchestrator.Params orchestratorParams) throws Exception {
        String paramsJson = gson.toJson(orchestratorParams);
        return createPresentationFile(paramsJson, "Orchestrator-" + orchestratorParams.getTopic());
    }

} 