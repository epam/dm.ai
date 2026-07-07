// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job.doctor;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.ConfigDoctor;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.mcp.cli.McpCliHandler;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the \`dmtools doctor\` command.
 * Loads configuration from the current directory, checks integration readiness,
 * and runs connectivity tests via the corresponding \`*_test\` MCP tools.
 */
public class DoctorCommand {

    /**
     * Runs the doctor check and prints results to stdout.
     */
    public void run() {
        ApplicationConfiguration config = new PropertyReaderConfiguration();
        List<ConfigDoctor.CheckResult> results = ConfigDoctor.diagnose(config);

        long ready = results.stream().filter(ConfigDoctor.CheckResult::isReady).count();
        System.out.println("DMTools Configuration Check");
        System.out.println("==========================");
        System.out.println("Integrations ready: " + ready + " / " + results.size());
        System.out.println();

        McpCliHandler handler = new McpCliHandler();
        Map<String, Object> clientInstances = handler.createClientInstancesForDoctor();

        for (ConfigDoctor.CheckResult r : results) {
            String symbol = r.isReady() ? "✓" : "✗";
            String status = r.getStatus();
            List<String> warnings = new ArrayList<>(r.getWarnings());
            List<String> missing = new ArrayList<>(r.getMissing());

            if (r.isReady() && !"defaults".equals(r.getName()) && !"ai".equals(r.getName())) {
                String testTool = resolveTestToolName(r.getName());
                try {
                    Object testResult = MCPToolExecutor.executeTool(testTool, new HashMap<>(), clientInstances);
                    if (testResult instanceof Map) {
                        Map<String, Object> tr = (Map<String, Object>) testResult;
                        Object success = tr.get("success");
                        if (Boolean.TRUE.equals(success)) {
                            status = status + " (connectivity OK)";
                        } else {
                            status = status + " (connectivity failed)";
                            Object message = tr.get("message");
                            warnings.add("connectivity: " + (message != null ? message : "unknown error"));
                        }
                    }
                } catch (Exception e) {
                    status = status + " (connectivity failed)";
                    warnings.add("connectivity: " + e.getMessage());
                }
            }

            System.out.println(symbol + " " + r.getName() + " - " + status);
            for (String m : missing) {
                System.out.println("    missing: " + m);
            }
            for (String w : warnings) {
                System.out.println("    warning: " + w);
            }
        }
        System.out.println();
        System.out.println("Note: doctor checks configuration presence and, when configured, basic connectivity.");
    }

    private static String resolveTestToolName(String integrationName) {
        if ("xray".equals(integrationName) || "jira_xray".equals(integrationName)) {
            return "jira_xray_test";
        }
        return integrationName + "_test";
    }
}
