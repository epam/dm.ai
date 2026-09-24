// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.kb;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBaseConfig {

    public static final String _KEY = "knowledge_base_config";

    public static final String TYPE = "type";
    public static final String AUTH = "auth";
    public static final String PATH = "path";
    public static final String GRAPHQL = "graphql_path";
    public static final String WORKSPACE = "workspace";
    public static final String API_VERSION = "api_version";

    public enum Type {
        CONFLUENCE
    }

    @SerializedName(WORKSPACE)
    private String workspace;

    @SerializedName(TYPE)
    private Type type;

    @SerializedName(AUTH)
    private String auth;

    @SerializedName(PATH)
    private String path;

    @SerializedName(GRAPHQL)
    private String graphQLPath;

    /**
     * Confluence REST API version for content reads ("v1" default or "v2").
     * Sourced from {@code CONFLUENCE_API_VERSION}; v2 is required when using
     * Atlassian granular/scoped API tokens.
     */
    @SerializedName(API_VERSION)
    private String apiVersion;

    public boolean isConfigured() {
        return path != null || auth != null || type != null ;
    }

}
