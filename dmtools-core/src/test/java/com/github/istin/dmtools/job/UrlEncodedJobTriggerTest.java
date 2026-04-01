// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import junit.framework.TestCase;

import java.net.URLEncoder;

public class UrlEncodedJobTriggerTest extends TestCase {

    public void testMain() throws Exception {
        String encodedString = URLEncoder.encode("{\n" +
                "  \"name\" : \"Expert\",\n" +
                "  \"params\" : {\n" +
                "    \"inputJql\" : \"key = KEY-2267\",\n" +
                "    \"initiator\" : \"123123123123123230\",\n" +
                "    \"request\" : \"description\"" +
                ",\n" +
                "    \"projectContext\" : \"Context.\"\n" +
                "  }\n" +
                "}", "UTF-8").replace("+", "%20");
        //UrlEncodedJobTrigger.main(new String[] {encodedString});
    }
}