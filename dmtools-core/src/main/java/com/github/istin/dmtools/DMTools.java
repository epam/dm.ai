// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DMTools {

    private static final Logger logger = LogManager.getLogger(DMTools.class);

    public static void main(String[] args) {
        logger.info("Hello Dear DM.");
        logger.info("Root logger level: " + LogManager.getRootLogger().getLevel());
        logger.debug("This is a debug message");
        logger.info("This is an info message");
        logger.warn("This is a warn message");
        logger.error("This is an error message");
    }

}
