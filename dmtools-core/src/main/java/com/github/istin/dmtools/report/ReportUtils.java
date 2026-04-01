// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import java.io.*;

public class ReportUtils {

    public File write(String reportFriendlyName, String root, Object model) throws IOException, TemplateException {
        return write(reportFriendlyName, root, model, null);
    }

    public File write(String reportFriendlyName, String root, Object model, Writer writer) throws IOException, TemplateException {
        return write(reportFriendlyName, root, model, writer, "/ftl");
    }

    public File write(String reportFriendlyName, String root, Object model, Writer writer, String basePath) throws IOException, TemplateException {
        Configurator.initialize(new DefaultConfiguration());

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setLocalizedLookup(false);
        cfg.setTemplateLoader(new ClassTemplateLoader(getClass().getClassLoader(), basePath));

        Template temp = cfg.getTemplate(root + "/index.html");

        try {
            String reportName = getReportFileName(reportFriendlyName);
            File file = new File("reports/" + reportName + ".html");
            if (writer == null) {
                if (file.exists()) {
                    FileUtils.forceDelete(file);
                }
                FileOutputStream fileOutputStream = FileUtils.openOutputStream(file);
                writer = new OutputStreamWriter(fileOutputStream);
            }

            temp.process(model, writer);
            return file;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static String getReportFileName(String reportFriendlyName) {
        return reportFriendlyName.replaceAll(" ", "_");
    }

}
