// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

public interface CliPostComment {
    Boolean getAlwaysPostComments();

    TrackerParams.OutputType getOutputType();
}
