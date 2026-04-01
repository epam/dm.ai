// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.model;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Metadata {

    public static final String AGENT_ID = "agentId";
    public static final String CONTEXT_ID = "contextId";


    @SerializedName(AGENT_ID)
    private String agentId;

    @SerializedName(CONTEXT_ID)
    private String contextId;

    public void init(Object object) {
        if (agentId == null) {
            agentId = object.getClass().getSimpleName();
        }
    }
}
