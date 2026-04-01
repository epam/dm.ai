// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import java.util.List;

public interface IChangelog {

    List<? extends IHistory> getHistories();

}
