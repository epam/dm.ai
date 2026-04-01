// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.scopestatus;

import com.github.istin.dmtools.common.model.Key;

import java.util.Collection;

public class SummaryItem {

    private String label;

    private Object data;

    public SummaryItem(String label, Object data) {
        this.label = label;
        this.data = data;
    }

    public String getLabel() {
        return label;
    }

    public Object getData() {
        return data;
    }

    public Object getNumericValue() {
        if (data instanceof Integer || data instanceof String) {
            return data;
        } else {
            return ((Collection<? extends Key>) data).size();
        }
    }
}
