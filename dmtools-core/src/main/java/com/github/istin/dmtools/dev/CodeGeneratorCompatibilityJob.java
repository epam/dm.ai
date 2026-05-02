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

public class CodeGeneratorCompatibilityJob extends AbstractJob<BaseJobParams, List<ResultItem>> {

    public static final String JOB_NAME = "CodeGenerator";
    public static final String REMOVAL_VERSION = "v1.8.0";

    private final Logger logger;

    public CodeGeneratorCompatibilityJob() {
        this(LogManager.getLogger(CodeGeneratorCompatibilityJob.class));
    }

    CodeGeneratorCompatibilityJob(Logger logger) {
        this.logger = logger;
    }

    @Override
    public List<ResultItem> runJob(BaseJobParams params) {
        logger.warn(getDeprecationMessage());
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
