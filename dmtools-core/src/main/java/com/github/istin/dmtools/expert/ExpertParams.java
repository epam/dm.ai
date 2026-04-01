// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.expert;

import com.github.istin.dmtools.job.Params;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
@AllArgsConstructor
public class ExpertParams extends Params {

    public static final String PROJECT_CONTEXT = "projectContext";
    public static final String REQUEST = "request";
    public static final String SYSTEM_REQUEST = "systemRequest";
    public static final String SYSTEM_REQUEST_COMMENT_ALIAS = "systemRequestCommentAlias";
    public static final String KEYWORDS_BLACKLIST = "keywordsBlacklist";

    @SerializedName(PROJECT_CONTEXT)
    private String projectContext;

    @SerializedName(REQUEST)
    private String request;

    @SerializedName(SYSTEM_REQUEST)
    private String systemRequest;

    @SerializedName(KEYWORDS_BLACKLIST)
    private String keywordsBlacklist;

    @SerializedName(SYSTEM_REQUEST_COMMENT_ALIAS)
    private String systemRequestCommentAlias;

    @SerializedName(REQUEST_DECOMPOSITION_CHUNK_PROCESSING)
    private Boolean requestDecompositionChunkProcessing = false;

}