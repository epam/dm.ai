// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.team.IEmployees;

public abstract class CommonSourceCollector implements SourceCollector {

    private final IEmployees employees;

    public CommonSourceCollector(IEmployees employees) {
        this.employees = employees;
    }

    public boolean isNameIgnored(String displayName) {
        if (employees == null) {
            return false;
        }

        if (employees.isBot(displayName)) {
            return true;
        }

        return false;
    }

    public boolean isTeamContainsTheName(String displayName) {
        if (employees != null) {
            if (employees.contains(displayName)) {
                return true;
            }
        }

        return false;
    }

    public IEmployees getEmployees() {
        return employees;
    }

    protected String transformName(String displayName) {
        if (employees == null) {
            return displayName;
        }
        return employees.transformName(displayName);
    }
}
