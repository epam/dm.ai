// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.bitbucket.BasicBitbucket;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.gitlab.BasicGitLab;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SourceCodeFactory {

    public SourceCode createSourceCodes(String sourceType) {
        // Assuming a method is available to configure SourceCode with a certain sourceType
        try {
            return SourceCode.Impl.getConfiguredSourceCodes(new JSONArray().put(sourceType)).get(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SourceCode> createSourceCodesOrDefault(SourceCodeConfig... sourceCodeConfigs) throws IOException {
        List<SourceCode> sourceCodes;
        if (sourceCodeConfigs == null) {
            sourceCodes = SourceCode.Impl.getConfiguredSourceCodes(new JSONArray());
        } else {
            sourceCodes = createSourceCodes(sourceCodeConfigs);
        }
        return sourceCodes;
    }

    public List<SourceCode> createSourceCodes(SourceCodeConfig... sourceCodeConfigs) throws IOException {
        List<SourceCode> result = new ArrayList<>();
        for (SourceCodeConfig sourceCodeConfig : sourceCodeConfigs) {
            SourceCodeConfig.Type type = sourceCodeConfig.getType();
            switch (type) {
                case GITHUB:
                    result.add(new BasicGithub(sourceCodeConfig));
                    break;
                case BITBUCKET:
                    result.add(new BasicBitbucket(sourceCodeConfig));
                    break;
                case GITLAB:
                    result.add(new BasicGitLab(sourceCodeConfig));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported source code type: " + type);
            }
        }
        return result;
    }
}