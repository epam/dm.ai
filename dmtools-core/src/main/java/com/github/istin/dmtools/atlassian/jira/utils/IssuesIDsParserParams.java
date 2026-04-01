// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssuesIDsParserParams {

    public static final String _KEY = "issues_ids_parser_params";

    public enum Transformation {
        NONE,
        UPPERCASE,
        LOWERCASE
    }

    @Builder.Default
    private Transformation transformation = Transformation.NONE;

    @SerializedName("replace_characters")
    private String[] replaceCharacters;
    @SerializedName("replace_values")
    private String[] replaceValues;

}
