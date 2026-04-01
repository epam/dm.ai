// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import java.io.IOException;
import java.util.List;

public interface ToText {

    String toText() throws IOException;

    interface Utils {
        static String toText(List<? extends ToText> list) throws IOException {
            StringBuilder buffer = new StringBuilder();
            for (ToText toText : list) {
                buffer.append("-").append("\n");
                buffer.append(toText.toText());
                buffer.append("-").append("\n");
            }
            return buffer.toString();
        }
    }
}
