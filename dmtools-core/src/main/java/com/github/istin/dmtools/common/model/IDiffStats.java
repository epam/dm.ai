// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import java.util.Collections;
import java.util.List;

public interface IDiffStats {
    IStats getStats();
    List<IChange> getChanges();

    class Empty implements IDiffStats {
            @Override
            public IStats getStats() {
                return new IStats() {
                    @Override
                    public int getTotal() {
                        return 0;
                    }

                    @Override
                    public int getAdditions() {
                        return 0;
                    }

                    @Override
                    public int getDeletions() {
                        return 0;
                    }
                };
            }

            @Override
            public List<IChange> getChanges() {
                return Collections.emptyList();
            }
    }

}
