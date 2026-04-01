// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import com.github.istin.dmtools.common.utils.PropertyReader;

import java.io.IOException;

public class BasicFigmaClient extends FigmaClient {

    public static final String BASE_PATH;
    public static final String API_KEY;
    private static BasicFigmaClient instance;


    static {
        PropertyReader propertyReader = new PropertyReader();
        BASE_PATH = propertyReader.getFigmaBasePath();
        API_KEY = propertyReader.getFigmaApiKey();
    }

    public BasicFigmaClient() throws IOException {
        super(BASE_PATH, API_KEY);
    }

    public static synchronized FigmaClient getInstance() throws IOException {
        if (instance == null) {
            if (BASE_PATH == null || BASE_PATH.isEmpty()) {
                return null;
            }
            instance = new BasicFigmaClient();
        }
        return instance;
    }
}
