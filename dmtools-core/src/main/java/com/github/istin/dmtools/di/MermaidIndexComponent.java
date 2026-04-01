// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.di;

import com.github.istin.dmtools.index.mermaid.tool.MermaidIndexTools;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for Mermaid Index dependencies
 */
@Singleton
@Component(modules = {MermaidIndexModule.class, AIAgentsModule.class, ConfigurationModule.class})
public interface MermaidIndexComponent {
    MermaidIndexTools mermaidIndexTools();
}
