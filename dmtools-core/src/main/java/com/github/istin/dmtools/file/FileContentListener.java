// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.file;

public interface FileContentListener {
    void onFileRead(String folderPath, String packageName, String fileName, String fileContent) throws Exception;
}
