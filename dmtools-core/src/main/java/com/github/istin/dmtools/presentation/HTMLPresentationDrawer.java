// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import com.github.istin.dmtools.report.ReportUtils;
import freemarker.template.TemplateException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HTMLPresentationDrawer {

    private static final Logger logger = LogManager.getLogger(HTMLPresentationDrawer.class);

    public File printPresentation(String topic, JSONObject presentation) throws TemplateException, IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("presentationData", presentation);
        File file = new ReportUtils().write(topic + "_presentation", "presentation", map);
        logger.info("=== PRESENTATION FILE ===");
        logger.info(file.getAbsolutePath());
        return file;
    }

}
