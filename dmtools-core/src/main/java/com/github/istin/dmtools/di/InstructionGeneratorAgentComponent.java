// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.agent.InstructionGeneratorAgent;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for InstructionGeneratorAgent dependency injection.
 */
@Singleton
@Component(modules = {ConfigurationModule.class, AIComponentsModule.class})
public interface InstructionGeneratorAgentComponent {
    /**
     * Injects dependencies into InstructionGeneratorAgent
     *
     * @param agent The agent instance to inject dependencies into
     */
    void inject(InstructionGeneratorAgent agent);
}