// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Azure DevOps settings.
 */
public interface AdoConfiguration {
    /**
     * Gets the ADO organization
     * @return The ADO organization
     */
    String getAdoOrganization();

    /**
     * Gets the ADO project
     * @return The ADO project
     */
    String getAdoProject();

    /**
     * Gets the ADO personal access token
     * @return The ADO PAT token
     */
    String getAdoPatToken();

    /**
     * Gets the ADO base path URL
     * @return The ADO base path URL
     */
    String getAdoBasePath();
}
