// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.PresentationContentGeneratorAgent;
import com.github.istin.dmtools.ai.agent.PresentationSlideFormatterAgent;
import dagger.Module;
import dagger.Provides;

@Module
public class PresentationAgentsModule {

    @Provides
    PresentationContentGeneratorAgent providePresentationContentGeneratorAgent() {
        return new PresentationContentGeneratorAgent();
    }

    @Provides
    PresentationSlideFormatterAgent providePresentationSlideFormatterAgent() {
        return new PresentationSlideFormatterAgent();
    }
}