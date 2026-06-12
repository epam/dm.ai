// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.job.TrackerParams;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Parameters for the {@link CliAgent} job.
 * <p>
 * Extends {@link TrackerParams} to inherit common job plumbing (outputType, envVariables,
 * metadata, etc.) but does not require {@code inputJql}. The job is designed to run CLI agents
 * in a lightweight, ticket-agnostic mode.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CliAgentParams extends TrackerParams {

    public static final String CLI_COMMANDS = "cliCommands";
    public static final String CLI_PROMPT = "cliPrompt";
    public static final String CLI_PROMPTS = "cliPrompts";
    public static final String CLI_PROMPTS_BY_TRACKER = "cliPromptsByTracker";
    public static final String SETUP = "setup";
    public static final String CACHE = "cache";
    public static final String RESET = "reset";
    public static final String PRE_CLI_JS_ACTION = "preCliJSAction";
    public static final String CUSTOM_PARAMS = "customParams";
    public static final String CLEANUP_INPUT_FOLDER = "cleanupInputFolder";
    public static final String REQUIRE_CLI_OUTPUT_FILE = "requireCliOutputFile";
    public static final String WORKING_DIRECTORY = "workingDirectory";
    public static final String EXCLUDED_ENV_VARIABLES = "excludedEnvVariables";
    public static final String EXCLUDE_ENV_VARIABLES_BY_REGEX = "excludeEnvVariablesByRegex";
    public static final String TIMER_JS_ACTION = "timerJSAction";
    public static final String TIMER_INTERVAL_SECONDS = "timerIntervalSeconds";
    public static final String CLEANUP_OUTPUTS_FOLDER = "cleanupOutputsFolder";
    public static final String CLI_EXECUTION_ERROR_JS_ACTION = "cliExecutionErrorJSAction";
    public static final String CLI_OUTPUT_LINE_JS_ACTION = "cliOutputLineJSAction";

    @SerializedName(CLI_COMMANDS)
    private String[] cliCommands;

    @SerializedName(CLI_PROMPT)
    private String cliPrompt;

    @SerializedName(CLI_PROMPTS)
    private String[] cliPrompts;

    @SerializedName(CLI_PROMPTS_BY_TRACKER)
    private Map<String, String[]> cliPromptsByTracker;

    @SerializedName(SETUP)
    private String setup;

    @SerializedName(CACHE)
    private String cache;

    @SerializedName(RESET)
    private String reset;

    @SerializedName(PRE_CLI_JS_ACTION)
    private String preCliJSAction;

    @SerializedName(CUSTOM_PARAMS)
    private Map<String, Object> customParams;

    @SerializedName(CLEANUP_INPUT_FOLDER)
    private boolean cleanupInputFolder = true;

    @SerializedName(REQUIRE_CLI_OUTPUT_FILE)
    private boolean requireCliOutputFile = false;

    @SerializedName(WORKING_DIRECTORY)
    private String workingDirectory;

    @SerializedName(EXCLUDED_ENV_VARIABLES)
    private String[] excludedEnvVariables;

    @SerializedName(EXCLUDE_ENV_VARIABLES_BY_REGEX)
    private String[] excludeEnvVariablesByRegex;

    @SerializedName(TIMER_JS_ACTION)
    private String timerJSAction;

    @SerializedName(TIMER_INTERVAL_SECONDS)
    private int timerIntervalSeconds = 60;

    @SerializedName(CLEANUP_OUTPUTS_FOLDER)
    private boolean cleanupOutputsFolder = false;

    @SerializedName(CLI_EXECUTION_ERROR_JS_ACTION)
    private String cliExecutionErrorJSAction;

    @SerializedName(CLI_OUTPUT_LINE_JS_ACTION)
    private String cliOutputLineJSAction;
}
