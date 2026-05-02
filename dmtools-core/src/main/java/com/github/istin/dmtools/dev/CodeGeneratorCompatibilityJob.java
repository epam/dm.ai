// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.job.BaseJobParams;
import com.github.istin.dmtools.job.ResultItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class CodeGeneratorCompatibilityJob extends AbstractJob<BaseJobParams, List<ResultItem>> {

    public static final String JOB_NAME = "CodeGenerator";
    public static final String REMOVAL_VERSION = "v1.8.0";

    private final Logger logger;
    private final Consumer<String> warningSink;

    public CodeGeneratorCompatibilityJob() {
        this(LogManager.getLogger(CodeGeneratorCompatibilityJob.class), System.err::println);
    }

    CodeGeneratorCompatibilityJob(Logger logger) {
        this(logger, System.err::println);
    }

    CodeGeneratorCompatibilityJob(Logger logger, Consumer<String> warningSink) {
        this.logger = logger;
        this.warningSink = warningSink;
    }

    @Override
    public List<ResultItem> runJob(BaseJobParams params) {
        String deprecationMessage = getDeprecationMessage();
        logger.warn(deprecationMessage);
        warningSink.accept(deprecationMessage);
        return Collections.singletonList(new ResultItem(getName(), getCompatibilityResponse()));
    }

    @Override
    public AI getAi() {
        return null;
    }

    @Override
    public String getName() {
        return JOB_NAME;
    }

    public static String getDeprecationMessage() {
        return "CodeGenerator is deprecated and now runs as a compatibility shim. "
                + "No code will be generated. Migrate to supported workflows before " + REMOVAL_VERSION + ".";
    }

    public static String getCompatibilityResponse() {
        return "CodeGenerator compatibility shim executed successfully. "
                + "No action was taken and no code artifacts were produced.";
    }
}
