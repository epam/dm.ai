// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.di.ConfigurationModule;
import com.github.istin.dmtools.di.TrackerModule;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {ConfigurationModule.class, TrackerModule.class})
public interface ReportGeneratorComponent {
    void inject(ReportGeneratorJob reportGeneratorJob);
    void inject(ReportVisualizerJob reportVisualizerJob);
}
