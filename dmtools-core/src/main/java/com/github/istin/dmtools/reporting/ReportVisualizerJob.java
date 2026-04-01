// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.job.AbstractJob;
import com.google.gson.annotations.SerializedName;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import java.io.File;

/**
 * Job to visualize JSON reports as interactive HTML.
 * Uses TrackerClient abstraction for base URL - works with Jira, ADO, Rally, etc.
 */
public class ReportVisualizerJob extends AbstractJob<ReportVisualizerJob.Params, File> {

    private static final Logger logger = LogManager.getLogger(ReportVisualizerJob.class);

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Params extends com.github.istin.dmtools.job.Params {
        @SerializedName("jsonReportPath")
        private String jsonReportPath;

        @SerializedName("outputHtmlPath")
        private String outputHtmlPath;  // Optional - if not provided, will use .json -> .html
    }

    @Override
    protected void initializeStandalone() {
        DaggerReportGeneratorComponent.create().inject(this);
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        DaggerReportGeneratorComponent.create().inject(this);
    }

    @Override
    public File runJob(Params params) throws Exception {
        logger.info("Starting report visualization job");
        logger.info("Input JSON: {}", params.getJsonReportPath());

        if (params.getJsonReportPath() == null || params.getJsonReportPath().isEmpty()) {
            throw new IllegalArgumentException("jsonReportPath is required");
        }

        // Use TrackerClient abstraction for base URL
        ReportVisualizer visualizer = new ReportVisualizer(trackerClient);

        File htmlFile;
        if (params.getOutputHtmlPath() != null && !params.getOutputHtmlPath().isEmpty()) {
            htmlFile = visualizer.visualize(params.getJsonReportPath(), params.getOutputHtmlPath());
        } else {
            htmlFile = visualizer.visualize(params.getJsonReportPath());
        }

        logger.info("Visualization generated: {}", htmlFile.getAbsolutePath());
        logger.info("Open in browser: file://" + htmlFile.getAbsolutePath());

        return htmlFile;
    }
}
