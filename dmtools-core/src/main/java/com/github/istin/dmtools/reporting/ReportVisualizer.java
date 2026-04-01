// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.common.tracker.TrackerClient;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Visualizes JSON reports as interactive HTML with charts.
 * Uses TrackerClient abstraction for base URL - works with Jira, ADO, Rally, etc.
 * Uses FreeMarker to generate SPA with embedded JSON data.
 */
public class ReportVisualizer {

    private static final Logger logger = LogManager.getLogger(ReportVisualizer.class);

    private final Configuration freemarkerConfig;
    private final String trackerBaseUrl;

    /**
     * Create visualizer with explicit tracker base URL.
     */
    public ReportVisualizer(String trackerBaseUrl) {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_31);
        this.freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/ftl/reports");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
        this.trackerBaseUrl = trackerBaseUrl != null ? trackerBaseUrl : "";
    }

    /**
     * Create visualizer using TrackerClient abstraction to get base URL.
     */
    public ReportVisualizer(TrackerClient trackerClient) {
        this(trackerClient != null ? trackerClient.getBasePath() : "");
    }

    /**
     * Generate HTML visualization from JSON report file
     *
     * @param jsonReportPath Path to JSON report file
     * @param outputHtmlPath Path where to save HTML visualization
     * @return Generated HTML file
     */
    public File visualize(String jsonReportPath, String outputHtmlPath) throws Exception {
        logger.info("Visualizing report: {} -> {}", jsonReportPath, outputHtmlPath);

        // Read JSON report
        File jsonFile = new File(jsonReportPath);
        if (!jsonFile.exists()) {
            throw new IllegalArgumentException("JSON report not found: " + jsonReportPath);
        }

        String jsonContent = FileUtils.readFileToString(jsonFile, StandardCharsets.UTF_8);

        // Prepare template data
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("reportJson", jsonContent);
        templateData.put("trackerBaseUrl", trackerBaseUrl);
        templateData.put("reportFileName", jsonFile.getName());

        // Generate HTML from template
        Template template = freemarkerConfig.getTemplate("report_visualizer.ftl");
        StringWriter writer = new StringWriter();
        template.process(templateData, writer);

        String htmlContent = writer.toString();

        // Write HTML file
        File outputFile = new File(outputHtmlPath);
        FileUtils.writeStringToFile(outputFile, htmlContent, StandardCharsets.UTF_8);

        logger.info("Visualization generated: {} ({} bytes)", outputHtmlPath, outputFile.length());

        return outputFile;
    }

    /**
     * Generate HTML visualization with automatic output path
     *
     * @param jsonReportPath Path to JSON report file
     * @return Generated HTML file
     */
    public File visualize(String jsonReportPath) throws Exception {
        // Auto-generate output path: replace .json with .html
        String outputPath = jsonReportPath.replaceAll("\\.json$", ".html");
        return visualize(jsonReportPath, outputPath);
    }
}
