// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.context;

import java.util.Set;

public interface UriToObject {

    Set<String> parseUris(String object) throws Exception;

    Object uriToObject(String uri) throws Exception;

}
