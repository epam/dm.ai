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

    public boolean isConfigured() {
        return path != null || auth != null || type != null ;
    }

}
