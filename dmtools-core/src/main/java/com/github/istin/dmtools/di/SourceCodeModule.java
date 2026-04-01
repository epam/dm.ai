// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.dev.CommitsTriageParams;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Module
public class SourceCodeModule {

    @Provides
    @Singleton
    SourceCodeFactory provideSourceCodeFactory() {
        return new SourceCodeFactory();
    }

    @Provides
    List<SourceCode> provideSourceCodes() {
        try {
            return new SourceCodeFactory().createSourceCodesOrDefault((SourceCodeConfig[]) null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    @Provides
    SourceCode provideSourceCode(SourceCodeFactory factory, CommitsTriageParams params) {
        return factory.createSourceCodes(params.getSourceType());
    }
}