// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import org.json.JSONObject;

public interface PresentationMaker {

    /**
     * Gets the name of the presentation maker implementation.
     * @return The name.
     */
    String getName();

    /**
     * Creates a presentation based on the provided parameters, processed by the configured JavaScript.
     * The specific JavaScript implementation will define how paramsForJs is interpreted.
     *
     * @param paramsForJs A string (typically JSON) containing parameters for the JavaScript logic.
     * @return A JSONObject representing the generated presentation.
     * @throws Exception if there's an error during JavaScript execution or presentation generation.
     */
    JSONObject createPresentation(String paramsForJs) throws Exception;

    /**
     * A convenience method that might serialize Params to JSON and call the string-based createPresentation,
     * or directly interact with an orchestrator if the JS client is bypassed for this specific call.
     * How this is implemented depends on the client's capabilities.
     *
     * @param orchestratorParams Parameters for the PresentationMakerOrchestrator.
     * @return A JSONObject representing the generated presentation.
     * @throws Exception if there's an error during presentation generation.
     */
    JSONObject createPresentation(PresentationMakerOrchestrator.Params orchestratorParams) throws Exception;

} 