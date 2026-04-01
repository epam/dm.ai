// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.net.URLDecoder;

public class UrlEncodedJobTrigger {

    private static final Logger logger = LogManager.getLogger(UrlEncodedJobTrigger.class);

    public static void main(String... args) throws Exception {
        logger.info("Root logger level: " + LogManager.getRootLogger().getLevel());
        logger.debug("This is a debug message");
        logger.info("This is an info message");
        logger.warn("This is a warn message");
        logger.error("This is an error message");
        logger.info(args[0]);
        if (args != null) {
            for (String arg : args) {
                logger.info("arg: {}", arg);
            }
            String decodedArgs = URLDecoder.decode(args[0]).replace("\n","");
            logger.info(decodedArgs);
            logger.info(new JSONObject(decodedArgs));
            String base64Encoded = JobRunner.encodeBase64(decodedArgs);
            JobRunner.main(new String[] {base64Encoded});
        } else {
            throw new IllegalStateException("arguments for Job Runner must be JSON urlEncoded.");
        }
    }

}
