// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.qa.InstructionsGenerator;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for InstructionsGenerator job dependency injection.
 */
@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class, AIAgentsModule.class,
        TrackerModule.class, ConfluenceModule.class})
public interface InstructionsGeneratorComponent {
    /**
     * Injects dependencies into InstructionsGenerator
     *
     * @param instructionsGenerator The job instance to inject dependencies into
     */
    void inject(InstructionsGenerator instructionsGenerator);
}