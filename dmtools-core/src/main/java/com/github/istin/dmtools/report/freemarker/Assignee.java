// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import com.github.istin.dmtools.Config;
import com.thedeanda.lorem.LoremIpsum;

public class Assignee {

    private String name;

    private String email;

    public Assignee(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        if (Config.DEMO_PAGE) {
            return LoremIpsum.getInstance().getName();
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
