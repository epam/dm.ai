// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import com.github.istin.dmtools.common.utils.FileConfig;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormulaReader {
    private static volatile FormulaReader instance;
    private final FileConfig fileConfig;
    private final Map<String, Configuration> configCache;
    private final Map<String, String> templateContentCache;

    private FormulaReader() {
        this.fileConfig = new FileConfig();
        this.configCache = new ConcurrentHashMap<>();
        this.templateContentCache = new ConcurrentHashMap<>();
    }

    public static FormulaReader getInstance() {
        if (instance == null) {
            synchronized (FormulaReader.class) {
                if (instance == null) {
                    instance = new FormulaReader();
                }
            }
        }
        return instance;
    }

    private Configuration createConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setLocalizedLookup(false);
        return cfg;
    }

    private String normalizeTemplateName(String template) {
        // Remove leading slash if present
        return template.startsWith("/") ? template.substring(1) : template;
    }

    private Configuration getOrCreateConfig(String template, String templateContent) {
        return configCache.computeIfAbsent(template, k -> {
            Configuration cfg = createConfiguration();
            StringTemplateLoader stringLoader = new StringTemplateLoader();
            // Use normalized name for the template
            String normalizedName = normalizeTemplateName(template);
            stringLoader.putTemplate(normalizedName, templateContent);
            cfg.setTemplateLoader(stringLoader);
            return cfg;
        });
    }

    private Configuration getOrCreateDirectoryConfig(String templateDir) throws IOException {
        return configCache.computeIfAbsent(templateDir, k -> {
            Configuration cfg = createConfiguration();
            try {
                cfg.setTemplateLoader(new FileTemplateLoader(new File(templateDir)));
            } catch (IOException e) {
                throw new RuntimeException("Failed to create template loader for directory: " + templateDir, e);
            }
            return cfg;
        });
    }

    /**
     * Read and process a template from either file system or resources
     *
     * @param params   Parameters to be processed in the template
     * @param template Template name or path
     * @return Processed template as String
     */
    public String readFormula(HashMap<String, String> params, String template) {
        try {
            // Get template content from cache or read it
            String templateContent = templateContentCache.computeIfAbsent(template, k -> {
                String content = fileConfig.readFile(k);
                if (content == null) {
                    throw new RuntimeException("Template not found: " + k);
                }
                return content;
            });

            // Get or create configuration for this template
            Configuration cfg = getOrCreateConfig(template, templateContent);

            // Use normalized template name
            String normalizedTemplate = normalizeTemplateName(template);

            // Process template
            Template temp = cfg.getTemplate(normalizedTemplate);
            Writer out = new StringWriter();
            temp.process(params, out);
            return out.toString();

        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to process template: " + template, e);
        }
    }

    /**
     * Read and process a template from a specific directory
     *
     * @param params      Parameters to be processed in the template
     * @param template    Template name or path
     * @param templateDir Directory containing templates
     * @return Processed template as String
     */
    public String readFormula(HashMap<String, String> params, String template, String templateDir) {
        try {
            if (templateDir != null) {
                File dir = new File(templateDir);
                if (dir.exists() && dir.isDirectory()) {
                    Configuration cfg = getOrCreateDirectoryConfig(templateDir);
                    try {
                        // Use normalized template name
                        String normalizedTemplate = normalizeTemplateName(template);
                        Template temp = cfg.getTemplate(normalizedTemplate);
                        Writer out = new StringWriter();
                        temp.process(params, out);
                        return out.toString();
                    } catch (IOException e) {
                        // Fall back to default behavior
                    }
                }
            }
            return readFormula(params, template);

        } catch (IOException | TemplateException e) {
            throw new RuntimeException("Failed to process template: " + template, e);
        }
    }

    /**
     * Check if template exists in either file system or resources
     *
     * @param template Template name or path
     * @return true if template exists
     */
    public boolean templateExists(String template) {
        return fileConfig.readFile(template) != null;
    }

    /**
     * Clear the configuration and template content caches
     */
    public void clearCache() {
        configCache.clear();
        templateContentCache.clear();
    }

    /**
     * Remove specific template from cache
     *
     * @param template Template name or path
     */
    public void removeFromCache(String template) {
        configCache.remove(template);
        templateContentCache.remove(template);
    }
}