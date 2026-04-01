// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

public class SimpleReport {

    private String name;

    private String filterUrl;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilterUrl() {
        return filterUrl;
    }

    public void setFilter(String filterUrl) {
        this.filterUrl = filterUrl;
    }
}
