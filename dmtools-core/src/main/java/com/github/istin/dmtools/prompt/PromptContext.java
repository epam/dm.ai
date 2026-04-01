// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.prompt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class PromptContext {

    private final Object global;

    private Map<String, Object> args = new HashMap<>();

    private List<File> files = new ArrayList<>();

    public PromptContext set(String argName, Object arg) {
        args.put(argName, arg);
        return this;
    }

    public PromptContext addFile(File file) {
        files.add(file);
        return this;
    }

}