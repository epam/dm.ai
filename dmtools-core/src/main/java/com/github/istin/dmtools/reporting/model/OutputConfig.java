// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

public class OutputConfig {
    private String mode;
    private boolean saveRawMetadata;
    private String outputPath;  // Path to save reports (default: "reports/")
    private String visualizer;  // "default" or null = run ReportVisualizer, "none" = skip

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isSaveRawMetadata() { return saveRawMetadata; }
    public void setSaveRawMetadata(boolean saveRawMetadata) { this.saveRawMetadata = saveRawMetadata; }
    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
    public String getVisualizer() { return visualizer; }
    public void setVisualizer(String visualizer) { this.visualizer = visualizer; }
}
