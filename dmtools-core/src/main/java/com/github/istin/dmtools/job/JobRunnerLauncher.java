// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

public class JobRunnerLauncher {

    public static void main(String[] args) throws Exception {
        JobRunner.main(new String[] {"run", "agents/xray_test_cases_generator.json"});
    }

}

