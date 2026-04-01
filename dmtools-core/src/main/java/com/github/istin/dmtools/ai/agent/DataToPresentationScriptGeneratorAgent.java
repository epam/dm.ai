// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.common.utils.Resources;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DataToPresentationScriptGeneratorAgent extends JSBridgeScriptGeneratorAgent {
    private static final Logger logger = LogManager.getLogger(DataToPresentationScriptGeneratorAgent.class);
    public static final String EXAMPLES_SEPARATOR = "\n// --- Next Example --- \n";

    @Getter
    public static class DataToPresentationParams extends JSBridgeScriptGeneratorAgent.Params {
        private static final String DEFAULT_ADDITIONAL_REQUIREMENTS = """
            The script's primary goal is to call `javaClient.runPresentationOrchestrator`. To do this, it must:
            ** IF SOME DATA IS REQUIRED BUT YOU DON'T HAVE IT IN CONNECTORS OR PROVIDED BY USER YOU MUST TO GENERATE SLIDE WITH SUGGESTIONS HOW TO GET THE DATA. **
            ** IT'S NOT ALLOWED TO USE DATA FROM JS Scripts EXAMPLES. **
            0. You must to include provided data to generated script to make ability to write function to transform data to nice summarized format for orchestrator, don't expect that the data exists in parameters to function.
            1.  First, call one of the data-gathering methods if required (like `generateCustomProjectReport` or `generateBugsReportWithTypes`) to get a dataset.
            2.  Construct a JSON object for the orchestrator.
            3.  This object must contain a `topic`, an `audience`, and a `requestDataList` array.
            4.  Create at least one object inside the `requestDataList`. This object must have:
                a. `userRequest`: A clear, natural language instruction to the orchestrator telling it how to analyze the data.
                b. `additionalData`: The string result from the data-gathering method called in step 1.
            5.  Convert the orchestrator parameters object to a JSON string.
            6.  Call `javaClient.runPresentationOrchestrator` with the prepared JSON string.
            7.  The script must return the result of the `runPresentationOrchestrator` call after parsing it as JSON.
            8.  All operations must be enclosed in a try-catch block.
            9.  The generated JavaScript code must not contain any comments.
            """;

        public DataToPresentationParams(String userTask, String additionalRequirements, List<File> files) {
            super(userTask, DEFAULT_ADDITIONAL_REQUIREMENTS,
                    additionalRequirements,
                    files, // files - not used by this agent
                    getDefaultDataToPresentationExamplesAsString());
        }
    }

    public static String getDefaultDataToPresentationExamplesAsString() {
        List<String> examplePaths = Arrays.asList(
                "js/examples/csv_data_analysis.js",
                "js/examples/bug_report_analysis.js"
        );

        try {
            return examplePaths.stream()
                    .map(Resources::readSpecificResource)
                    .collect(Collectors.joining(EXAMPLES_SEPARATOR));
        } catch (Exception e) {
            logger.error("Error loading presentation examples from resources", e);
            throw new IllegalStateException("Could not load default presentation examples.", e);
        }
    }

    public DataToPresentationScriptGeneratorAgent() {
        super();
    }
} 