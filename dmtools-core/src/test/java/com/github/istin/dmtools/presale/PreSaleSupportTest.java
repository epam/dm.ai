// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presale;

import junit.framework.TestCase;

public class PreSaleSupportTest extends TestCase {

    private PreSaleSupport preSaleSupport;

    public void setUp() throws Exception {
        super.setUp();
        preSaleSupport = new PreSaleSupport();
    }

    public void testGetName() {
        String name = preSaleSupport.getName();
        assertEquals("PreSaleSupport", name);
    }

    public void testGetParamsClass() {
        Class<PreSaleSupportParams> paramsClass = preSaleSupport.getParamsClass();
        assertEquals(PreSaleSupportParams.class, paramsClass);
    }

    public void testRunJob() {

    }
}