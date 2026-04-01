// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.prompt;

public interface IPromptTemplateReader {

    String read(String promptName, PromptContext context) throws Exception;

}
